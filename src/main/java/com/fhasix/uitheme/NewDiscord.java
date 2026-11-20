package com.fhasix.uitheme;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

/**
 * Discord Rich Presence: shows "Playing Fhasix Client" and where you are (menus, singleplayer, a server) on your
 * Discord profile. It talks to the Discord desktop app on the SAME computer through Discord's local socket, so it
 * works on a PC / Mac / Linux and does nothing on a phone (there is no Discord desktop app to talk to).
 *
 * Needs a Discord "Application ID": create an application at https://discord.com/developers/applications (its name is
 * what people see after "Playing"), copy the Application ID into fhasix_discord.txt in the game folder (the file is
 * created for you the first time the mod is switched on). Without an ID nothing is sent.
 */
public final class NewDiscord {

    static final String ID = "discord_rpc";
    private static final String FILE = "fhasix_discord.txt";

    private static volatile boolean wanted = false;
    private static volatile String details = "In the menus";
    private static volatile String state = "Fhasix Client";
    private static volatile String largeText = "Fhasix Client";
    private static volatile String clientId = "";
    private static long startSeconds = System.currentTimeMillis() / 1000L;
    private static Thread worker;
    private static int tickCounter = 0;
    private static boolean fileChecked = false;

    private NewDiscord() {
    }

    static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(NewSafe.tick("discord", NewDiscord::tick));
    }

    private static void tick(Minecraft c) {
        boolean on = NewModSettings.isEnabled(c, ID);
        wanted = on;
        if (!on) {
            return;
        }
        if (!fileChecked) {
            fileChecked = true;
            readId(c);
        }
        if (++tickCounter % 40 != 1) {
            return;
        }
        if (c.level == null) {
            details = "In the menus";
            state = "Fhasix Client";
        } else if (c.getCurrentServer() != null) {
            details = "Playing multiplayer";
            state = NewModSettings.optBool(c, ID, "server") ? c.getCurrentServer().ip : "On a server";
        } else {
            details = "Playing singleplayer";
            state = "Fhasix Client";
        }
        if (worker == null || !worker.isAlive()) {
            if (!clientId.isEmpty()) {
                worker = new Thread(NewDiscord::loop, "fhasix-discord");
                worker.setDaemon(true);
                worker.start();
            }
        }
    }

    private static void readId(Minecraft c) {
        try {
            Path f = c.gameDirectory.toPath().resolve(FILE);
            if (!Files.exists(f)) {
                Files.write(f, List.of(
                        "# Fhasix Discord Rich Presence",
                        "# 1) Open https://discord.com/developers/applications and create an application (its name is shown after \"Playing\").",
                        "# 2) Copy its Application ID and put it on the next line (digits only), then restart the game.",
                        "# This works with the Discord desktop app on the same PC; it does nothing on a phone.",
                        ""), StandardCharsets.UTF_8);
                return;
            }
            for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                String t = line.trim();
                if (!t.isEmpty() && !t.startsWith("#") && t.chars().allMatch(Character::isDigit)) {
                    clientId = t;
                    return;
                }
            }
        } catch (IOException ignored) {
            // no id = no presence
        }
    }

    // ------------------------------------------------------------------ worker thread

    private static void loop() {
        SocketChannel ch = null;
        String lastSent = "";
        long lastSendMs = 0L;
        while (wanted) {
            try {
                if (ch == null) {
                    ch = connect();
                    if (ch == null) {
                        Thread.sleep(30_000L);
                        continue;
                    }
                    frame(ch, 0, "{\"v\":1,\"client_id\":\"" + clientId + "\"}");
                    readFrame(ch);
                    lastSent = "";
                }
                String presence = details + "|" + state;
                long now = System.currentTimeMillis();
                if (!presence.equals(lastSent) || now - lastSendMs > 15_000L) {
                    frame(ch, 1, activityJson());
                    readFrame(ch);
                    lastSent = presence;
                    lastSendMs = now;
                }
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                close(ch);
                ch = null;
                try {
                    Thread.sleep(30_000L);
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }
        close(ch);
    }

    private static String esc(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (ch == '"' || ch == '\\') {
                b.append('\\').append(ch);
            } else if (ch < 32) {
                b.append(' ');
            } else {
                b.append(ch);
            }
        }
        return b.toString();
    }

    private static String activityJson() {
        long pid = ProcessHandle.current().pid();
        return "{\"cmd\":\"SET_ACTIVITY\",\"nonce\":\"" + System.nanoTime() + "\",\"args\":{\"pid\":" + pid
                + ",\"activity\":{\"details\":\"" + esc(details) + "\",\"state\":\"" + esc(state)
                + "\",\"timestamps\":{\"start\":" + startSeconds + "},\"assets\":{\"large_image\":\"fhasix\",\"large_text\":\""
                + esc(largeText) + "\"}}}}";
    }

    private static SocketChannel connect() {
        String os = System.getProperty("os.name", "").toLowerCase();
        for (int i = 0; i < 10; i++) {
            try {
                if (os.contains("win")) {
                    return null; // the Windows named pipe needs a different transport; not supported in this version
                }
                for (String dir : dirs()) {
                    Path p = Path.of(dir, "discord-ipc-" + i);
                    if (Files.exists(p)) {
                        SocketChannel ch = SocketChannel.open(StandardProtocolFamily.UNIX);
                        ch.connect(UnixDomainSocketAddress.of(p));
                        return ch;
                    }
                }
            } catch (Exception ignored) {
                // try the next one
            }
        }
        return null;
    }

    private static String[] dirs() {
        String xdg = System.getenv("XDG_RUNTIME_DIR");
        String tmp = System.getenv("TMPDIR");
        String base = xdg != null ? xdg : (tmp != null ? tmp : "/tmp");
        return new String[]{base, base + "/app/com.discordapp.Discord", base + "/snap.discord", "/tmp"};
    }

    private static void frame(SocketChannel ch, int op, String json) throws IOException {
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer b = ByteBuffer.allocate(8 + data.length).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(op).putInt(data.length).put(data).flip();
        while (b.hasRemaining()) {
            ch.write(b);
        }
    }

    private static void readFrame(SocketChannel ch) throws IOException {
        ByteBuffer head = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        while (head.hasRemaining()) {
            if (ch.read(head) < 0) {
                throw new IOException("closed");
            }
        }
        head.flip();
        head.getInt();
        int len = Math.max(0, Math.min(head.getInt(), 1 << 20));
        ByteBuffer body = ByteBuffer.allocate(len);
        while (body.hasRemaining()) {
            if (ch.read(body) < 0) {
                throw new IOException("closed");
            }
        }
    }

    private static void close(SocketChannel ch) {
        if (ch != null) {
            try {
                ch.close();
            } catch (IOException ignored) {
                // nothing to do
            }
        }
    }
}
