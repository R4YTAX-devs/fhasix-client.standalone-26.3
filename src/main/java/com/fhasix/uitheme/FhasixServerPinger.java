package com.fhasix.uitheme;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Hashtable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Self-contained Minecraft "Server List Ping" client used by the Discover cards.
 *
 * Why this exists instead of vanilla's MultiplayerServerListPinger: that class was the one part
 * of Discover that depended on multiplayer-networking internals whose behaviour/signatures moved
 * between versions, and when it failed the card could only say "offline" without any reason.
 * The status protocol itself is tiny and stable, so this speaks it directly over a plain socket:
 *
 *   handshake (next state = status)  ->  status request  ->  status JSON (players, favicon)
 *   then ping(long) -> pong(long)    ->  round-trip time = the number shown on the card
 *
 * No Minecraft classes are touched (only java.net, Gson which Minecraft already ships, and the
 * JDK's JNDI DNS provider for SRV records - the same one vanilla uses, wrapped in try/catch so
 * runtimes without it, e.g. some Android JVMs, simply connect to the typed host directly).
 * Everything runs on a small daemon pool, never on the render thread.
 */
public final class FhasixServerPinger {

    /** One coloured piece of the server's description (MOTD). rgb = 0xRRGGBB, or -1 for "default colour". */
    public static final class MotdSeg {
        public final String text;
        public final int rgb;

        MotdSeg(String text, int rgb) {
            this.text = text;
            this.rgb = rgb;
        }
    }

    /** What one ping attempt found out about a server. */
    public static final class Result {
        public final boolean online;
        public final int pingMs;
        public final int playersOnline;
        public final int playersMax;
        /** Raw PNG bytes of the server icon (64x64), or null. */
        public final byte[] favicon;
        /** The server description as coloured pieces (may contain '\n'); empty if the server sent none. */
        public final List<MotdSeg> motd;

        Result(boolean online, int pingMs, int playersOnline, int playersMax, byte[] favicon, List<MotdSeg> motd) {
            this.online = online;
            this.pingMs = pingMs;
            this.playersOnline = playersOnline;
            this.playersMax = playersMax;
            this.favicon = favicon;
            this.motd = motd == null ? Collections.<MotdSeg>emptyList() : motd;
        }

        static final Result OFFLINE = new Result(false, -1, 0, 0, null, null);
    }

    private static final int DEFAULT_PORT = 25565;
    /** Sent in the handshake; servers answer status pings for any protocol number. */
    private static final int PROTOCOL_VERSION = 774;
    private static final int CONNECT_TIMEOUT_MS = 4000;
    private static final int READ_TIMEOUT_MS = 4000;
    private static final int MAX_STRING_BYTES = 1 << 20;

    private static final ExecutorService POOL = Executors.newFixedThreadPool(6, r -> {
        Thread t = new Thread(r, "fhasix-server-ping");
        t.setDaemon(true);
        return t;
    });

    private FhasixServerPinger() {
    }

    /** Pings on the background pool and hands the result to the callback (on a pool thread). */
    public static void pingAsync(String address, Consumer<Result> callback) {
        POOL.execute(() -> {
            Result result;
            try {
                result = ping(address);
            } catch (Throwable t) {
                result = Result.OFFLINE;
            }
            try {
                callback.accept(result);
            } catch (Throwable ignored) {
                // a failing callback must never kill the pool thread
            }
        });
    }

    /** Blocking ping. Never throws: any failure is reported as an offline Result. */
    public static Result ping(String address) {
        String host = address.trim();
        int port = DEFAULT_PORT;
        int colon = host.lastIndexOf(':');
        if (colon > 0 && host.indexOf(':') == colon && colon < host.length() - 1) {
            try {
                port = Integer.parseInt(host.substring(colon + 1).trim());
                host = host.substring(0, colon).trim();
            } catch (NumberFormatException ignored) {
                // not a port after all - keep the whole string as the host
            }
        }

        String connectHost = host;
        int connectPort = port;
        if (port == DEFAULT_PORT) {
            // Same rule vanilla uses: only look for an SRV record when no custom port was given.
            String[] srv = resolveSrv(host);
            if (srv != null) {
                connectHost = srv[0];
                try {
                    connectPort = Integer.parseInt(srv[1]);
                } catch (NumberFormatException ignored) {
                    connectHost = host;
                }
            }
        }

        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return query(host, port, connectHost, connectPort);
            } catch (SocketTimeoutException e) {
                // slow or briefly busy server - one more try before calling it offline
            } catch (IOException | RuntimeException e) {
                break; // refused, unknown host, protocol error: retrying won't help
            }
        }
        return Result.OFFLINE;
    }

    private static Result query(String host, int port, String connectHost, int connectPort) throws IOException {
        try (Socket socket = new Socket()) {
            socket.setTcpNoDelay(true);
            socket.connect(new InetSocketAddress(connectHost, connectPort), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            // Handshake: id 0x00, protocol, the host exactly as typed, its port, next state 1 (status).
            ByteArrayOutputStream handshake = new ByteArrayOutputStream();
            DataOutputStream h = new DataOutputStream(handshake);
            writeVarInt(h, 0x00);
            writeVarInt(h, PROTOCOL_VERSION);
            writeString(h, host);
            h.writeShort(port);
            writeVarInt(h, 1);
            writePacket(out, handshake.toByteArray());

            // Status request: just packet id 0x00.
            writePacket(out, new byte[]{0x00});
            out.flush();
            long statusSent = System.nanoTime();

            readVarInt(in); // packet length - the fields below are read by structure
            int id = readVarInt(in);
            if (id != 0x00) {
                throw new IOException("Unexpected status packet id " + id);
            }
            String json = readString(in);
            long statusRoundTrip = (System.nanoTime() - statusSent) / 1_000_000L;

            // Ping/pong gives the cleanest latency number; some proxies drop it, so fall back
            // to the status round trip rather than reporting a reachable server as offline.
            long pingMs = -1L;
            try {
                ByteArrayOutputStream ping = new ByteArrayOutputStream();
                DataOutputStream p = new DataOutputStream(ping);
                writeVarInt(p, 0x01);
                p.writeLong(System.nanoTime());
                long sent = System.nanoTime();
                writePacket(out, ping.toByteArray());
                out.flush();
                readVarInt(in);
                if (readVarInt(in) == 0x01) {
                    in.readLong();
                    pingMs = (System.nanoTime() - sent) / 1_000_000L;
                }
            } catch (IOException ignored) {
                // keep the status round trip
            }
            if (pingMs < 0L) {
                pingMs = statusRoundTrip;
            }

            int online = 0;
            int max = 0;
            byte[] favicon = null;
            List<MotdSeg> motd = new ArrayList<>();
            try {
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                try {
                    collectMotd(root.get("description"), -1, motd);
                } catch (RuntimeException ignored) {
                    // odd description JSON - the card just shows no description
                }
                if (root.has("players") && root.get("players").isJsonObject()) {
                    JsonObject players = root.getAsJsonObject("players");
                    online = intOf(players, "online");
                    max = intOf(players, "max");
                }
                if (root.has("favicon") && root.get("favicon").isJsonPrimitive()) {
                    String icon = root.get("favicon").getAsString();
                    int comma = icon.indexOf(',');
                    if (comma >= 0) {
                        icon = icon.substring(comma + 1);
                    }
                    favicon = Base64.getMimeDecoder().decode(icon);
                }
            } catch (RuntimeException ignored) {
                // The server did answer - odd JSON just means no player count / icon.
            }
            return new Result(true, (int) Math.max(1L, pingMs), online, max, favicon, motd);
        }
    }

    // ---- description (MOTD) parsing -------------------------------------------------------

    private static final String[] COLOR_NAMES = {"black", "dark_blue", "dark_green", "dark_aqua", "dark_red",
            "dark_purple", "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"};
    private static final int[] COLOR_RGB = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00,
            0xAAAAAA, 0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};
    private static final int MOTD_MAX_CHARS = 220;

    private static int colorOf(String name) {
        if (name == null) {
            return -1;
        }
        if (name.length() == 7 && name.charAt(0) == '#') {
            try {
                return Integer.parseInt(name.substring(1), 16);
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        for (int i = 0; i < COLOR_NAMES.length; i++) {
            if (COLOR_NAMES[i].equals(name)) {
                return COLOR_RGB[i];
            }
        }
        return -1;
    }

    private static int totalChars(List<MotdSeg> out) {
        int n = 0;
        for (MotdSeg seg : out) {
            n += seg.text.length();
        }
        return n;
    }

    /** Walks a chat-component description (string, object with text/extra/color, or array). */
    private static void collectMotd(JsonElement e, int inherited, List<MotdSeg> out) {
        if (e == null || e.isJsonNull() || totalChars(out) >= MOTD_MAX_CHARS) {
            return;
        }
        if (e.isJsonPrimitive()) {
            addLegacy(e.getAsString(), inherited, out);
        } else if (e.isJsonArray()) {
            for (JsonElement part : e.getAsJsonArray()) {
                collectMotd(part, inherited, out);
            }
        } else if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            int rgb = inherited;
            if (o.has("color") && o.get("color").isJsonPrimitive()) {
                int c = colorOf(o.get("color").getAsString());
                if (c >= 0) {
                    rgb = c;
                }
            }
            if (o.has("text") && o.get("text").isJsonPrimitive()) {
                addLegacy(o.get("text").getAsString(), rgb, out);
            }
            if (o.has("extra") && o.get("extra").isJsonArray()) {
                for (JsonElement part : o.getAsJsonArray("extra")) {
                    collectMotd(part, rgb, out);
                }
            }
        }
    }

    /** Splits text on the legacy section-sign colour codes; other format codes are dropped. */
    private static void addLegacy(String text, int base, List<MotdSeg> out) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int current = base;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\u00a7' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(++i));
                int idx = "0123456789abcdef".indexOf(code);
                if (idx >= 0 || code == 'r') {
                    if (sb.length() > 0) {
                        out.add(new MotdSeg(sb.toString(), current));
                        sb.setLength(0);
                    }
                    current = idx >= 0 ? COLOR_RGB[idx] : base;
                }
                continue;
            }
            sb.append(ch);
        }
        if (sb.length() > 0) {
            out.add(new MotdSeg(sb.toString(), current));
        }
    }

    private static int intOf(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        if (e != null && e.isJsonPrimitive()) {
            try {
                return e.getAsInt();
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    /** Looks up _minecraft._tcp.<host> via the JDK's DNS provider; null if none/unavailable. */
    private static String[] resolveSrv(String host) {
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            env.put("java.naming.provider.url", "dns:");
            env.put("com.sun.jndi.dns.timeout.initial", "1500");
            env.put("com.sun.jndi.dns.timeout.retries", "1");
            InitialDirContext ctx = new InitialDirContext(env);
            Attributes attrs = ctx.getAttributes("_minecraft._tcp." + host, new String[]{"SRV"});
            Attribute srv = attrs.get("srv");
            if (srv != null && srv.size() > 0) {
                String[] parts = srv.get().toString().split(" "); // priority weight port target
                if (parts.length >= 4) {
                    String target = parts[3];
                    if (target.endsWith(".")) {
                        target = target.substring(0, target.length() - 1);
                    }
                    return new String[]{target, parts[2]};
                }
            }
        } catch (Throwable ignored) {
            // no SRV record, no DNS provider, offline resolver... connect to the typed host
        }
        return null;
    }

    // ---- wire format helpers --------------------------------------------------------------

    private static void writePacket(DataOutputStream out, byte[] payload) throws IOException {
        writeVarInt(out, payload.length);
        out.write(payload);
    }

    private static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        int shift = 0;
        int b;
        do {
            b = in.readUnsignedByte();
            value |= (b & 0x7F) << shift;
            shift += 7;
            if (shift > 35) {
                throw new IOException("VarInt too long");
            }
        } while ((b & 0x80) != 0);
        return value;
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = readVarInt(in);
        if (length < 0 || length > MAX_STRING_BYTES) {
            throw new IOException("Bad string length " + length);
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
