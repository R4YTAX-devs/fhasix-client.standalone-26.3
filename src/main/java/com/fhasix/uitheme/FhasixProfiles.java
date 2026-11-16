package com.fhasix.uitheme;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Mod profiles: a profile is a name plus a CODE that holds every mod switch, every mod option and every
 * performance setting. The code is plain text ("FHX1-XXXXXX-XXXXXX-..."): share it, paste it somewhere else, and
 * the other player gets exactly the same setup. Changing a single setting changes the code.
 *
 * How the code is made: [format 1][which mods are on, 1 bit each][performance modes][every mod option] is
 * packed into bytes, compressed, a checksum is added, and the result is written in letters and digits (Base32).
 * Importing checks the checksum first, so a mistyped code is refused instead of half-applied.
 */
final class FhasixProfiles {
    static final class Profile {
        String name;
        String code;

        Profile(String name, String code) {
            this.name = name;
            this.code = code;
        }
    }

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("fhasix_profiles.json");
    private static final String PREFIX = "FHX1";
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int GROUP = 6;

    private static final List<Profile> LIST = new ArrayList<>();
    private static String active = "";
    private static boolean loaded = false;

    private FhasixProfiles() {}

    // ------------------------------------------------------------------ stored profiles
    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            if (Files.exists(FILE)) {
                JsonObject root = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
                if (root.has("active")) {
                    active = root.get("active").getAsString();
                }
                if (root.has("profiles") && root.get("profiles").isJsonArray()) {
                    for (JsonElement e : root.getAsJsonArray("profiles")) {
                        JsonObject o = e.getAsJsonObject();
                        if (o.has("name") && o.has("code")) {
                            LIST.add(new Profile(o.get("name").getAsString(), o.get("code").getAsString()));
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            LIST.clear();
            active = "";
        }
    }

    private static void save() {
        JsonObject root = new JsonObject();
        root.addProperty("active", active);
        JsonArray arr = new JsonArray();
        for (Profile p : LIST) {
            JsonObject o = new JsonObject();
            o.addProperty("name", p.name);
            o.addProperty("code", p.code);
            arr.add(o);
        }
        root.add("profiles", arr);
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, root.toString(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // profiles are a convenience; the game keeps running if saving fails
        }
    }

    static List<Profile> all() {
        ensureLoaded();
        return LIST;
    }

    static String active() {
        ensureLoaded();
        return active;
    }

    static void setActive(String name) {
        ensureLoaded();
        active = name;
        save();
    }

    /** A name that is not used yet: "PVP", then "PVP 2", "PVP 3" ... */
    static String uniqueName(String wanted) {
        ensureLoaded();
        String base = wanted.trim();
        if (base.isEmpty()) {
            base = "Profile";
        }
        String name = base;
        int n = 2;
        while (nameTaken(name, -1)) {
            name = base + " " + n++;
        }
        return name;
    }

    private static boolean nameTaken(String name, int ignoreIndex) {
        for (int i = 0; i < LIST.size(); i++) {
            if (i != ignoreIndex && LIST.get(i).name.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    static Profile add(String name, String code) {
        ensureLoaded();
        Profile p = new Profile(uniqueName(name), code);
        LIST.add(p);
        save();
        return p;
    }

    static void remove(int index) {
        ensureLoaded();
        if (index >= 0 && index < LIST.size()) {
            if (LIST.get(index).name.equals(active)) {
                active = "";
            }
            LIST.remove(index);
            save();
        }
    }

    static Profile duplicate(int index) {
        ensureLoaded();
        if (index < 0 || index >= LIST.size()) {
            return null;
        }
        Profile src = LIST.get(index);
        return add(src.name + " Copy", src.code);
    }

    /** Renames; false when the name is empty or already used by another profile. */
    static boolean rename(int index, String name) {
        ensureLoaded();
        String n = name.trim();
        if (index < 0 || index >= LIST.size() || n.isEmpty() || nameTaken(n, index)) {
            return false;
        }
        Profile p = LIST.get(index);
        if (p.name.equals(active)) {
            active = n;
        }
        p.name = n;
        save();
        return true;
    }

    // ------------------------------------------------------------------ code: settings -> text
    private static List<NewModSettings.ModDef> functionalMods() {
        List<NewModSettings.ModDef> l = new ArrayList<>();
        for (NewModSettings.ModDef m : NewModSettings.MODS) {
            if (m.functional) {
                l.add(m);
            }
        }
        return l;
    }

    /** Every registered option of every mod, in a fixed order: {mod id, option}. */
    private static List<Object[]> allOptions() {
        List<Object[]> l = new ArrayList<>();
        for (NewModSettings.ModDef m : NewModSettings.MODS) {
            List<NewModOptions.Opt> opts = NewModOptions.forMod(m.id);
            if (opts == null) {
                continue;
            }
            for (NewModOptions.Opt o : opts) {
                l.add(new Object[]{m.id, o});
            }
        }
        return l;
    }

    private static void varint(ByteArrayOutputStream out, long v) {
        while (v >= 0x80L) {
            out.write((int) ((v & 0x7F) | 0x80));
            v >>>= 7;
        }
        out.write((int) v);
    }

    private static long zigzag(int v) {
        return ((long) ((v << 1) ^ (v >> 31))) & 0xFFFFFFFFL;
    }

    private static int unzigzag(long v) {
        return (int) ((v >>> 1) ^ -(v & 1L));
    }

    /** The code of the settings the game has right now. */
    static String encodeCurrent(Minecraft c) {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        raw.write(1); // format version

        List<NewModSettings.ModDef> mods = functionalMods();
        varint(raw, mods.size());
        int bits = 0;
        int cur = 0;
        for (NewModSettings.ModDef m : mods) {
            if (NewModSettings.isEnabled(c, m.id)) {
                cur |= 1 << bits;
            }
            if (++bits == 8) {
                raw.write(cur);
                bits = 0;
                cur = 0;
            }
        }
        if (bits > 0) {
            raw.write(cur);
        }

        varint(raw, NewPerf.COUNT);
        for (int i = 0; i < NewPerf.COUNT; i++) {
            raw.write((NewPerf.on(c, i) ? 4 : 0) | (NewPerf.level(c, i) & 3));
        }

        List<Object[]> opts = allOptions();
        varint(raw, opts.size());
        for (Object[] e : opts) {
            NewModOptions.Opt o = (NewModOptions.Opt) e[1];
            varint(raw, zigzag(NewModSettings.opt(c, (String) e[0], o.key)));
        }
        return pack(raw.toByteArray());
    }

    private static String pack(byte[] data) {
        Deflater d = new Deflater(Deflater.BEST_COMPRESSION, true);
        d.setInput(data);
        d.finish();
        ByteArrayOutputStream z = new ByteArrayOutputStream();
        byte[] buf = new byte[256];
        while (!d.finished()) {
            int n = d.deflate(buf);
            z.write(buf, 0, n);
        }
        d.end();
        byte[] zipped = z.toByteArray();
        CRC32 crc = new CRC32();
        crc.update(zipped);
        long v = crc.getValue();
        byte[] all = new byte[zipped.length + 4];
        System.arraycopy(zipped, 0, all, 0, zipped.length);
        for (int i = 0; i < 4; i++) {
            all[zipped.length + i] = (byte) (v >>> (24 - 8 * i));
        }
        StringBuilder text = new StringBuilder(base32(all));
        StringBuilder out = new StringBuilder(PREFIX);
        for (int i = 0; i < text.length(); i += GROUP) {
            out.append('-').append(text, i, Math.min(text.length(), i + GROUP));
        }
        return out.toString();
    }

    private static String base32(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                sb.append(ALPHABET.charAt((buffer >> (bitsLeft - 5)) & 31));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            sb.append(ALPHABET.charAt((buffer << (5 - bitsLeft)) & 31));
        }
        return sb.toString();
    }

    private static byte[] unbase32(String s) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bitsLeft = 0;
        for (int i = 0; i < s.length(); i++) {
            int v = ALPHABET.indexOf(s.charAt(i));
            if (v < 0) {
                return null;
            }
            buffer = (buffer << 5) | v;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out.write((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return out.toByteArray();
    }

    // ------------------------------------------------------------------ code: text -> settings
    private static final class Reader {
        final byte[] b;
        int pos = 0;

        Reader(byte[] b) {
            this.b = b;
        }

        int read() throws IOException {
            if (pos >= b.length) {
                throw new IOException("short");
            }
            return b[pos++] & 0xFF;
        }

        long varint() throws IOException {
            long v = 0;
            int shift = 0;
            while (true) {
                int x = read();
                v |= (long) (x & 0x7F) << shift;
                if ((x & 0x80) == 0) {
                    return v;
                }
                shift += 7;
                if (shift > 35) {
                    throw new IOException("long");
                }
            }
        }
    }

    /** Applies a code to the game right now. Returns null when it worked, otherwise a short reason. */
    static String applyCode(Minecraft c, String code) {
        if (code == null) {
            return "Empty code";
        }
        String s = code.toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "");
        if (!s.startsWith(PREFIX)) {
            return "This is not a Fhasix profile code";
        }
        byte[] all = unbase32(s.substring(PREFIX.length()));
        if (all == null || all.length < 6) {
            return "The code has wrong characters";
        }
        byte[] zipped = new byte[all.length - 4];
        System.arraycopy(all, 0, zipped, 0, zipped.length);
        CRC32 crc = new CRC32();
        crc.update(zipped);
        long want = crc.getValue();
        long got = 0;
        for (int i = 0; i < 4; i++) {
            got = (got << 8) | (all[zipped.length + i] & 0xFF);
        }
        if ((want & 0xFFFFFFFFL) != got) {
            return "The code is incomplete or mistyped";
        }
        byte[] raw;
        try {
            Inflater inf = new Inflater(true);
            inf.setInput(zipped);
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] buf = new byte[512];
            while (!inf.finished()) {
                int n = inf.inflate(buf);
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) {
                    break;
                }
                o.write(buf, 0, n);
                if (o.size() > 1 << 20) {
                    inf.end();
                    return "The code is not valid";
                }
            }
            inf.end();
            raw = o.toByteArray();
        } catch (DataFormatException e) {
            return "The code is not valid";
        }
        try {
            return apply(c, new Reader(raw));
        } catch (IOException e) {
            return "The code is cut off";
        }
    }

    private static String apply(Minecraft c, Reader r) throws IOException {
        if (r.read() != 1) {
            return "This code is from a newer Fhasix version";
        }
        // everything is read first, so a broken code never leaves the settings half changed
        long nMods = r.varint();
        if (nMods > 2000) {
            return "The code is not valid";
        }
        boolean[] modOn = new boolean[(int) nMods];
        int cur = 0;
        for (int i = 0; i < nMods; i++) {
            if (i % 8 == 0) {
                cur = r.read();
            }
            modOn[i] = (cur & (1 << (i % 8))) != 0;
        }
        long nPerf = r.varint();
        if (nPerf > 200) {
            return "The code is not valid";
        }
        int[] perf = new int[(int) nPerf];
        for (int i = 0; i < nPerf; i++) {
            perf[i] = r.read();
        }
        long nOpts = r.varint();
        if (nOpts > 5000) {
            return "The code is not valid";
        }
        int[] optVals = new int[(int) nOpts];
        for (int i = 0; i < nOpts; i++) {
            optVals[i] = unzigzag(r.varint());
        }

        List<NewModSettings.ModDef> mods = functionalMods();
        for (int i = 0; i < mods.size() && i < modOn.length; i++) {
            NewModSettings.ModDef m = mods.get(i);
            if (NewModSettings.isEnabled(c, m.id) != modOn[i]) {
                NewModSettings.toggle(c, m);
            }
        }
        for (int i = 0; i < NewPerf.COUNT && i < perf.length; i++) {
            NewPerf.setOn(c, i, (perf[i] & 4) != 0);
            NewPerf.setLevel(c, i, Math.max(0, Math.min(2, perf[i] & 3)));
        }
        List<Object[]> opts = allOptions();
        for (int i = 0; i < opts.size() && i < optVals.length; i++) {
            NewModOptions.Opt o = (NewModOptions.Opt) opts.get(i)[1];
            NewModSettings.setOpt(c, (String) opts.get(i)[0], o.key, clampOption(o, optVals[i]));
        }
        NewModSettings.save(c);
        return null;
    }

    private static int clampOption(NewModOptions.Opt o, int v) {
        if (o.type == NewModOptions.TOGGLE) {
            return v != 0 ? 1 : 0;
        }
        if (o.type == NewModOptions.CHOICE) {
            int top = o.choices == null ? 0 : o.choices.length - 1;
            return Math.max(0, Math.min(top, v));
        }
        if (o.type == NewModOptions.COLOR) {
            return Math.max(0, Math.min(NewModOptions.PALETTE.length - 1, v));
        }
        if (o.type == NewModOptions.PICKER) {
            return v & 0xFFFFFF;
        }
        return Math.max(o.min, Math.min(o.max, v)); // slider
    }
}
