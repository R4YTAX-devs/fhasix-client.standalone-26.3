package com.fhasix.uitheme;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.NativeImage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * The resource pack downloader behind the Resource Packs screen. Talks to the public Modrinth API
 * (https://api.modrinth.com/v2 - no key needed, only a descriptive User-Agent), downloads the newest
 * pack that supports this Minecraft version into the game's resourcepacks folder (SHA-1 checked, written
 * to a .part file first so a half download can never be mistaken for a pack) and remembers what it
 * installed in fhasix_packs.properties so cards can show "installed" and updates can be detected.
 *
 * Everything here is blocking network / disk work: call it from a background thread, never the render thread.
 */
public final class FhasixPackApi {

    /** The Minecraft version this build targets (search and downloads are filtered to it). */
    public static final String MC_VERSION = "1.21.11";

    private static final String API = "https://api.modrinth.com/v2";
    private static final String UA = "Fhasix-Client/1.0.1 (resource pack browser)";
    private static final int CONNECT_MS = 8000;
    private static final int READ_MS = 20000;
    private static final long MAX_JSON = 4L * 1024 * 1024;
    private static final long MAX_IMAGE = 8L * 1024 * 1024;
    private static final long MAX_PACK = 600L * 1024 * 1024;

    private FhasixPackApi() {
    }

    // ------------------------------------------------------------------ data

    public static final class Hit {
        public String id = "";
        public String slug = "";
        public String title = "";
        public String author = "";
        public String description = "";
        public String iconUrl;
        public String bannerUrl;
        public long downloads;
        public int color;
        public List<String> tags = new ArrayList<>();
    }

    public static final class SearchResult {
        public final List<Hit> hits = new ArrayList<>();
        public int total;
    }

    public interface Progress {
        void update(long done, long total);
    }

    public static final class InstallResult {
        public boolean ok;
        public String file = "";
        public String versionId = "";
        public String error = "";
    }

    // ------------------------------------------------------------------ search

    /**
     * @param andCats  categories / features that must ALL match
     * @param anyRes   resolutions of which ANY may match (empty = no resolution filter)
     * @param index    relevance | downloads | follows | newest | updated
     */
    public static SearchResult search(String query, List<String> andCats, List<String> anyRes,
                                      String index, int offset, int limit) throws IOException {
        JsonArray facets = new JsonArray();
        facets.add(group("project_type:resourcepack"));
        facets.add(group("versions:" + MC_VERSION));
        for (String c : andCats) {
            facets.add(group("categories:" + c));
        }
        if (!anyRes.isEmpty()) {
            JsonArray any = new JsonArray();
            for (String r : anyRes) {
                any.add("categories:" + r);
            }
            facets.add(any);
        }
        StringBuilder url = new StringBuilder(API).append("/search?");
        if (query != null && !query.trim().isEmpty()) {
            url.append("query=").append(enc(query.trim())).append('&');
        }
        url.append("facets=").append(enc(facets.toString()));
        url.append("&index=").append(enc(index));
        url.append("&limit=").append(limit);
        url.append("&offset=").append(offset);

        JsonObject root = JsonParser.parseString(new String(get(url.toString(), MAX_JSON), StandardCharsets.UTF_8)).getAsJsonObject();
        SearchResult out = new SearchResult();
        out.total = root.has("total_hits") && !root.get("total_hits").isJsonNull() ? root.get("total_hits").getAsInt() : 0;
        JsonArray hits = root.has("hits") && root.get("hits").isJsonArray() ? root.getAsJsonArray("hits") : new JsonArray();
        for (JsonElement el : hits) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject o = el.getAsJsonObject();
            Hit h = new Hit();
            h.id = str(o, "project_id");
            if (h.id.isEmpty()) {
                continue;
            }
            h.slug = str(o, "slug");
            h.title = clean(str(o, "title"));
            h.author = clean(str(o, "author"));
            h.description = clean(str(o, "description"));
            String icon = str(o, "icon_url");
            h.iconUrl = icon.isEmpty() ? null : icon;
            String banner = str(o, "featured_gallery");
            if (banner.isEmpty() && o.has("gallery") && o.get("gallery").isJsonArray() && o.getAsJsonArray("gallery").size() > 0) {
                JsonElement g0 = o.getAsJsonArray("gallery").get(0);
                banner = g0.isJsonNull() ? "" : g0.getAsString();
            }
            h.bannerUrl = banner.isEmpty() ? null : banner;
            h.downloads = o.has("downloads") && !o.get("downloads").isJsonNull() ? o.get("downloads").getAsLong() : 0L;
            h.color = o.has("color") && !o.get("color").isJsonNull() ? o.get("color").getAsInt() : 0;
            if (o.has("categories") && o.get("categories").isJsonArray()) {
                for (JsonElement t : o.getAsJsonArray("categories")) {
                    if (!t.isJsonNull()) {
                        h.tags.add(t.getAsString());
                    }
                }
                Collections.sort(h.tags);
            }
            out.hits.add(h);
        }
        return out;
    }

    private static JsonArray group(String one) {
        JsonArray a = new JsonArray();
        a.add(one);
        return a;
    }

    // ------------------------------------------------------------------ install

    /** Newest release of the project that supports MC_VERSION (falls back to the newest of any type). */
    private static JsonObject pickVersion(String projectId) throws IOException {
        String url = API + "/project/" + enc(projectId) + "/version?game_versions=" + enc("[\"" + MC_VERSION + "\"]");
        JsonArray all = JsonParser.parseString(new String(get(url, MAX_JSON), StandardCharsets.UTF_8)).getAsJsonArray();
        if (all.size() == 0) {
            throw new IOException("No version for Minecraft " + MC_VERSION);
        }
        for (JsonElement el : all) {
            JsonObject v = el.getAsJsonObject();
            if ("release".equals(str(v, "version_type"))) {
                return v;
            }
        }
        return all.get(0).getAsJsonObject();
    }

    public static InstallResult install(Minecraft minecraft, Hit hit, Progress progress) {
        InstallResult res = new InstallResult();
        Path part = null;
        try {
            Path dir = packDir(minecraft);
            Files.createDirectories(dir);
            JsonObject ver = pickVersion(hit.id);
            JsonArray files = ver.has("files") && ver.get("files").isJsonArray() ? ver.getAsJsonArray("files") : new JsonArray();
            if (files.size() == 0) {
                throw new IOException("Version has no files");
            }
            JsonObject file = files.get(0).getAsJsonObject();
            for (JsonElement f : files) {
                JsonObject fo = f.getAsJsonObject();
                if (fo.has("primary") && !fo.get("primary").isJsonNull() && fo.get("primary").getAsBoolean()) {
                    file = fo;
                    break;
                }
            }
            String url = str(file, "url");
            String name = safeName(str(file, "filename"), hit.slug);
            long size = file.has("size") && !file.get("size").isJsonNull() ? file.get("size").getAsLong() : -1L;
            String sha1 = "";
            if (file.has("hashes") && file.get("hashes").isJsonObject()) {
                sha1 = str(file.getAsJsonObject("hashes"), "sha1").toLowerCase(Locale.ROOT);
            }
            if (url.isEmpty()) {
                throw new IOException("Version has no download link");
            }

            part = dir.resolve(name + ".fhasix.part");
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            HttpURLConnection c = open(url);
            long total = c.getContentLengthLong() > 0 ? c.getContentLengthLong() : size;
            long done = 0L;
            try (InputStream in = c.getInputStream(); OutputStream out = Files.newOutputStream(part)) {
                byte[] buf = new byte[32 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    md.update(buf, 0, n);
                    done += n;
                    if (done > MAX_PACK) {
                        throw new IOException("File too large");
                    }
                    if (progress != null) {
                        progress.update(done, total);
                    }
                }
            } finally {
                c.disconnect();
            }
            if (done == 0L) {
                throw new IOException("Empty download");
            }
            if (!sha1.isEmpty()) {
                StringBuilder hex = new StringBuilder();
                for (byte b : md.digest()) {
                    hex.append(String.format("%02x", b));
                }
                if (!hex.toString().equals(sha1)) {
                    throw new IOException("Download was corrupted (checksum mismatch)");
                }
            }

            Path target = dir.resolve(name);
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
            part = null;

            // an update with a different file name: remove the old file of this project
            synchronized (FhasixPackApi.class) {
                ensureLoaded(minecraft);
                String[] old = TRACK.get(hit.id);
                if (old != null && !old[0].equals(name)) {
                    try {
                        Files.deleteIfExists(dir.resolve(old[0]));
                    } catch (IOException ignored) {
                        // leaving the old file behind is harmless
                    }
                }
                TRACK.put(hit.id, new String[]{name, str(ver, "id")});
                save(minecraft);
            }
            res.ok = true;
            res.file = name;
            res.versionId = str(ver, "id");
        } catch (Throwable t) {
            res.ok = false;
            String m = t.getMessage();
            res.error = m == null || m.isEmpty() ? t.getClass().getSimpleName() : m;
        } finally {
            if (part != null) {
                try {
                    Files.deleteIfExists(part);
                } catch (IOException ignored) {
                    // nothing to do
                }
            }
        }
        return res;
    }

    /** Project ids of installed packs that have a newer version on Modrinth. Blocking (one request per pack). */
    public static List<String> findUpdates(Minecraft minecraft) {
        Map<String, String[]> copy;
        synchronized (FhasixPackApi.class) {
            ensureLoaded(minecraft);
            copy = new HashMap<>(TRACK);
        }
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String[]> e : copy.entrySet()) {
            try {
                JsonObject v = pickVersion(e.getKey());
                if (!str(v, "id").equals(e.getValue()[1])) {
                    out.add(e.getKey());
                }
            } catch (Throwable ignored) {
                // one pack failing must not stop the check
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ installed packs (tracking)

    private static final Map<String, String[]> TRACK = new HashMap<>();
    private static boolean loaded = false;

    private static Path packDir(Minecraft c) {
        return c.gameDirectory.toPath().resolve("resourcepacks");
    }

    private static Path trackFile(Minecraft c) {
        return c.gameDirectory.toPath().resolve("fhasix_packs.properties");
    }

    private static void ensureLoaded(Minecraft c) {
        if (loaded) {
            return;
        }
        loaded = true;
        Path f = trackFile(c);
        if (!Files.isRegularFile(f)) {
            return;
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(f)) {
            p.load(in);
        } catch (IOException e) {
            return;
        }
        for (String id : p.stringPropertyNames()) {
            String v = p.getProperty(id);
            int bar = v.lastIndexOf('|');
            if (bar > 0) {
                TRACK.put(id, new String[]{v.substring(0, bar), v.substring(bar + 1)});
            }
        }
    }

    private static void save(Minecraft c) {
        Properties p = new Properties();
        for (Map.Entry<String, String[]> e : TRACK.entrySet()) {
            p.setProperty(e.getKey(), e.getValue()[0] + "|" + e.getValue()[1]);
        }
        try (OutputStream out = Files.newOutputStream(trackFile(c))) {
            p.store(out, "Fhasix Resource Packs: project id = file|version id");
        } catch (IOException ignored) {
            // tracking is a convenience only
        }
    }

    /** True when this project was installed through the browser and its file is still in resourcepacks. */
    public static synchronized boolean isInstalled(Minecraft c, String projectId) {
        ensureLoaded(c);
        String[] v = TRACK.get(projectId);
        return v != null && Files.isRegularFile(packDir(c).resolve(v[0]));
    }

    // ------------------------------------------------------------------ images

    /**
     * Downloads and decodes an image, shrunk to at most maxW pixels wide. PNG/JPEG are decoded directly;
     * WebP (which Modrinth uses a lot and Java cannot read) goes through the wsrv.nl image proxy as PNG.
     * Blocking: call from a background thread. The caller owns (and must close or register) the result.
     */
    public static NativeImage loadImage(String url, int maxW) throws IOException {
        NativeImage full = null;
        if (!url.toLowerCase(Locale.ROOT).contains(".webp")) {
            try {
                full = decode(get(url, MAX_IMAGE));
            } catch (IOException ignored) {
                full = null;
            }
        }
        if (full == null) {
            String bare = url.replaceFirst("^https?://", "");
            String proxied = "https://wsrv.nl/?url=" + enc(bare) + "&output=png&w=" + maxW;
            full = decode(get(proxied, MAX_IMAGE));
        }
        if (full == null) {
            throw new IOException("Unreadable image");
        }
        int fw = full.getWidth();
        int fh = full.getHeight();
        if (fw <= maxW) {
            return full;
        }
        int tw = maxW;
        int th = Math.max(1, Math.round(tw * (fh / (float) fw)));
        NativeImage small = new NativeImage(NativeImage.Format.RGBA, tw, th, false);
        try {
            full.resizeSubRectTo(0, 0, fw, fh, small);
        } catch (Throwable t) {
            small.close();
            throw new IOException("Resize failed");
        } finally {
            full.close();
        }
        return small;
    }

    private static NativeImage decode(byte[] data) {
        try {
            return NativeImage.read(new ByteArrayInputStream(data));
        } catch (Throwable ignored) {
            // not something NativeImage reads - try Java's decoder below
        }
        try {
            byte[] png = AwtPng.toPng(data);
            if (png != null) {
                return NativeImage.read(new ByteArrayInputStream(png));
            }
        } catch (Throwable ignored) {
            // no Java image decoder on this platform
        }
        return null;
    }

    /** Kept in its own class so a runtime without java.desktop never fails to load FhasixPackApi itself. */
    private static final class AwtPng {
        static byte[] toPng(byte[] data) throws IOException {
            java.awt.image.BufferedImage bi = javax.imageio.ImageIO.read(new ByteArrayInputStream(data));
            if (bi == null) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(bi, "png", out);
            return out.toByteArray();
        }
    }

    // ------------------------------------------------------------------ http / json helpers

    private static HttpURLConnection open(String url) throws IOException {
        String cur = url;
        for (int hop = 0; hop < 6; hop++) {
            HttpURLConnection c = (HttpURLConnection) URI.create(cur).toURL().openConnection();
            c.setRequestProperty("User-Agent", UA);
            c.setConnectTimeout(CONNECT_MS);
            c.setReadTimeout(READ_MS);
            c.setInstanceFollowRedirects(false);
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null || loc.isEmpty()) {
                    throw new IOException("Bad redirect");
                }
                cur = URI.create(cur).resolve(loc).toString();
                continue;
            }
            if (code != 200) {
                c.disconnect();
                throw new IOException("HTTP " + code);
            }
            return c;
        }
        throw new IOException("Too many redirects");
    }

    private static byte[] get(String url, long max) throws IOException {
        HttpURLConnection c = open(url);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16 * 1024];
            int n;
            long total = 0L;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > max) {
                    throw new IOException("Response too large");
                }
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } finally {
            c.disconnect();
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String str(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull() || !o.get(key).isJsonPrimitive()) {
            return "";
        }
        return o.get(key).getAsString();
    }

    /** Single line, no control characters. */
    private static String clean(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            b.append(ch < 32 ? ' ' : ch);
        }
        return b.toString().trim();
    }

    private static String safeName(String filename, String fallback) {
        String n = filename == null ? "" : filename.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._+\\- ]", "_").trim();
        if (n.isEmpty() || n.equals(".") || n.equals("..")) {
            n = (fallback == null || fallback.isEmpty() ? "pack" : fallback) + ".zip";
        }
        if (!n.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            n = n + ".zip";
        }
        return n;
    }
}
