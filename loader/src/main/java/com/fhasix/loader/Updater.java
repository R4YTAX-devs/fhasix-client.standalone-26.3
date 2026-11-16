package com.fhasix.loader;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Checks version.json on GitHub, compares it with what is installed, and replaces the
 * FHASIX Client jar when it is missing or different. Everything is verified with sha256.
 */
public final class Updater {

    public enum State { CHECKING, UP_TO_DATE, NEEDS_UPDATE, DOWNLOADING, DONE, ERROR }

    // version.json is tried on the fastest-updating hosts first; jsDelivr caches longest, so it is last.
    private static final String[] BASES = {
            "https://raw.githubusercontent.com/R4YTAX-devs/fhasix-client.standalone-26.3/main/",
            "https://github.com/R4YTAX-devs/fhasix-client.standalone-26.3/raw/main/",
            "https://rawcdn.githack.com/R4YTAX-devs/fhasix-client.standalone-26.3/main/",
            "https://cdn.jsdelivr.net/gh/R4YTAX-devs/fhasix-client.standalone-26.3@main/"
    };

    private static final Pattern SAFE_JAR_PATH = Pattern.compile("^dist/fhasix-client-[A-Za-z0-9._-]+\\.jar$");
    private static final Pattern SAFE_VERSION = Pattern.compile("^[A-Za-z0-9._-]{1,32}$");
    private static final Pattern SAFE_SHA = Pattern.compile("^[0-9a-fA-F]{64}$");

    public volatile State state = State.CHECKING;
    public volatile String message = "";
    public volatile float progress = 0f;
    public volatile boolean firstInstall = false;
    public volatile String remoteVersion = "";
    /** True when an error happened before anything was deleted, so playing on is safe. */
    public volatile boolean canContinue = false;

    private volatile boolean busy = false;
    private String remoteJar = "";
    private String remoteSha = "";
    private long remoteSize = 0;

    private final Path gameDir = FabricLoader.getInstance().getGameDir();
    private final Path modsDir = gameDir.resolve("mods");
    private final Path stateDir = gameDir.resolve("config").resolve("fhasix-loader");
    private final Path installedFile = stateDir.resolve("installed.json");
    private final Path stagingDir = gameDir.resolve("fhasix-loader-staging");

    public void startCheck() {
        if (busy) return;
        busy = true;
        state = State.CHECKING;
        message = "";
        canContinue = false;
        Thread t = new Thread(() -> {
            try {
                check();
            } catch (Exception e) {
                fail("Could not reach the update server. Check your internet connection.", true);
            } finally {
                busy = false;
            }
        }, "fhasix-loader-check");
        t.setDaemon(true);
        t.start();
    }

    public void startApply() {
        if (busy || state != State.NEEDS_UPDATE) return;
        busy = true;
        state = State.DOWNLOADING;
        progress = 0f;
        message = "";
        canContinue = false;
        Thread t = new Thread(() -> {
            try {
                apply();
            } catch (Exception e) {
                fail("Update failed: " + e.getMessage(), true);
            } finally {
                busy = false;
            }
        }, "fhasix-loader-apply");
        t.setDaemon(true);
        t.start();
    }

    // ------------------------------------------------------------------ check

    private void check() throws IOException {
        JsonObject remote = fetchVersion();
        String version = str(remote, "version");
        String jar = str(remote, "jar");
        String sha = str(remote, "sha256");
        if (!SAFE_VERSION.matcher(version).matches() || !SAFE_JAR_PATH.matcher(jar).matches()
                || jar.contains("..") || !SAFE_SHA.matcher(sha).matches()) {
            throw new IOException("version.json is not valid");
        }
        remoteVersion = version;
        remoteJar = jar;
        remoteSha = sha.toLowerCase();
        remoteSize = remote.has("size") ? remote.get("size").getAsLong() : 0L;

        List<Path> current = listJars("fhasix-client-");
        List<Path> legacy = listJars("fhasix-ui-theme-");
        firstInstall = current.isEmpty() && legacy.isEmpty();

        boolean needs = current.isEmpty()
                || !legacy.isEmpty()
                || current.size() > 1
                || !sha256(current.get(0)).equals(remoteSha);

        if (needs) {
            state = State.NEEDS_UPDATE;
        } else {
            writeInstalled();
            state = State.UP_TO_DATE;
        }
    }

    private JsonObject fetchVersion() throws IOException {
        IOException last = null;
        for (String base : BASES) {
            try {
                String text = httpText(base + "version.json?t=" + System.currentTimeMillis());
                return JsonParser.parseString(text).getAsJsonObject();
            } catch (Exception e) {
                last = new IOException(e.getMessage(), e);
            }
        }
        throw last != null ? last : new IOException("no mirrors");
    }

    // ------------------------------------------------------------------ apply

    private void apply() throws IOException {
        deleteTree(stagingDir);
        Files.createDirectories(stagingDir);
        Files.createDirectories(modsDir);

        String fileName = Paths.get(remoteJar).getFileName().toString();
        Path staged = stagingDir.resolve(fileName);
        message = "Downloading " + remoteVersion + "...";
        download(staged);

        // The new jar is downloaded and verified. Only now touch the old install.
        List<Path> old = new ArrayList<>(listJars("fhasix-client-"));
        old.addAll(listJars("fhasix-ui-theme-"));
        List<Path> stuck = new ArrayList<>();
        for (Path p : old) {
            try {
                Files.deleteIfExists(p);
            } catch (IOException e) {
                stuck.add(p);
            }
        }

        Path target = modsDir.resolve(fileName);
        if (stuck.isEmpty()) {
            Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
            deleteTree(stagingDir);
        } else if (isWindows()) {
            // Windows keeps the loaded jar locked while the game runs: finish the swap after the game closes.
            scheduleWindowsSwap(staged, target, stuck);
        } else {
            throw new IOException("could not remove the old version");
        }

        writeInstalled();
        progress = 1f;
        message = "";
        state = State.DONE;
    }

    private void download(Path out) throws IOException {
        StringBuilder why = new StringBuilder();
        for (String base : BASES) {
          for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                progress = 0f;
                fetchToFile(base + remoteJar + (attempt == 1 ? "" : "?retry=" + System.currentTimeMillis()), out);
                if (remoteSize > 0 && Files.size(out) != remoteSize) {
                    throw new IOException("size mismatch");
                }
                if (!sha256(out).equals(remoteSha)) {
                    throw new IOException("checksum mismatch");
                }
                return;
            } catch (IOException e) {
                // keep every mirror's answer, so a failure says which host refused and why
                String host = base.replaceAll("^https?://([^/]+)/.*$", "$1");
                why.append(why.length() == 0 ? "" : "; ").append(host).append(" try ").append(attempt).append(": ").append(e.getMessage());
                try { Files.deleteIfExists(out); } catch (IOException ignored) { }
                try { Thread.sleep(1500L * attempt); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
          }
        }
        throw new IOException("download failed (" + why + ")");
    }

    private void scheduleWindowsSwap(Path staged, Path target, List<Path> stuck) throws IOException {
        Path bat = stagingDir.resolve("swap.bat");
        StringBuilder sb = new StringBuilder();
        sb.append("@echo off\r\nset n=0\r\n:wait\r\nping -n 3 127.0.0.1 >nul\r\nset /a n+=1\r\n");
        sb.append("if %n% GTR 120 exit /b\r\n");
        for (Path p : stuck) {
            sb.append("del /f /q \"").append(p).append("\" 2>nul\r\n");
            sb.append("if exist \"").append(p).append("\" goto wait\r\n");
        }
        sb.append("move /y \"").append(staged).append("\" \"").append(target).append("\"\r\n");
        sb.append("del /f /q \"%~f0\"\r\n");
        Files.writeString(bat, sb.toString(), StandardCharsets.US_ASCII);
        new ProcessBuilder("cmd", "/c", "start", "\"\"", "/min", bat.toString()).start();
    }

    // ------------------------------------------------------------------ helpers

    private List<Path> listJars(String prefix) throws IOException {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(modsDir)) return out;
        try (Stream<Path> s = Files.list(modsDir)) {
            s.filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith(prefix) && n.endsWith(".jar");
            }).forEach(out::add);
        }
        return out;
    }

    private void writeInstalled() throws IOException {
        Files.createDirectories(stateDir);
        JsonObject o = new JsonObject();
        o.addProperty("version", remoteVersion);
        o.addProperty("sha256", remoteSha);
        Files.writeString(installedFile, o.toString(), StandardCharsets.UTF_8);
    }

    private void fail(String msg, boolean allowContinue) {
        message = msg;
        canContinue = allowContinue;
        state = State.ERROR;
    }

    private static String str(JsonObject o, String key) throws IOException {
        if (!o.has(key)) throw new IOException("missing " + key);
        return o.get(key).getAsString().trim();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static HttpURLConnection open(String url, int readMs) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(5000);
        con.setReadTimeout(readMs);
        con.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) FhasixLoader/1.0");
        con.setRequestProperty("Accept", "*/*");
        con.setRequestProperty("Cache-Control", "no-cache");
        con.setInstanceFollowRedirects(true);
        int code = con.getResponseCode();
        if (code != 200) throw new IOException("HTTP " + code);
        return con;
    }

    private static String httpText(String url) throws IOException {
        HttpURLConnection con = open(url, 6000);
        try (InputStream in = con.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            con.disconnect();
        }
    }

    private void fetchToFile(String url, Path out) throws IOException {
        HttpURLConnection con = open(url, 15000);
        long total = remoteSize > 0 ? remoteSize : con.getContentLengthLong();
        try (InputStream in = con.getInputStream(); OutputStream os = Files.newOutputStream(out)) {
            byte[] buf = new byte[16384];
            long done = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                os.write(buf, 0, n);
                done += n;
                if (total > 0) progress = Math.min(1f, (float) done / total);
            }
        } finally {
            con.disconnect();
        }
    }

    private static String sha256(Path p) throws IOException {
        try (InputStream in = Files.newInputStream(p)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    private static void deleteTree(Path dir) {
        try {
            if (!Files.exists(dir)) return;
            try (Stream<Path> s = Files.walk(dir)) {
                s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) { }
                });
            }
        } catch (IOException ignored) { }
    }
}
