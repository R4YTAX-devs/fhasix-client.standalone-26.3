package com.fhasix.uitheme;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * FHASIX cosmetics catalog that lives on GitHub (UPDATE 46).
 *
 * The game never contains the new cosmetics: it only knows where to look. The repository holds one small
 * {@code cosmetics.json} plus, per cape, an icon (the preview slab shown in the Store / Cosmetics) and the in-game
 * cape texture. Nothing but data is ever read from there - no code is downloaded or run.
 *
 * Flow:
 *  1. A few seconds after the game starts, {@link #init()} quietly asks GitHub for {@code cosmetics.json}. Nothing is
 *     changed by that: if the catalog there is newer than the saved copy, an update is only REMEMBERED.
 *  2. The Store shows "N new cosmetics available" with Update / Cancel. Update stores the new catalog and downloads
 *     only the small icons of the new cosmetics. Cancel hides the banner until the Store is opened the next time.
 *  3. Icons are decoded and uploaded to the GPU lazily (only while a card is visible, at most {@link #MAX_ICON_TEX}
 *     at once). The big cape texture of a cosmetic is downloaded only when the player taps it to download - so
 *     downloading Aurora's texture never touches the other cosmetics.
 *  4. "Free up memory" in the Store unloads every icon texture and deletes the downloaded cape textures again
 *     (the cape you are wearing is kept so it does not vanish from your back).
 *
 * Safety: HTTPS to one fixed repository only, file names are checked, size limits on everything, SHA-256 of every
 * file is verified against the catalog, images are decoded before they are accepted. All work runs on background
 * threads; textures are uploaded on the render thread.
 */
public final class FhasixCatalog {
    /** Where the catalog lives. The files are fetched as raw files from this repository (branch main). */
    public static final String BASE_URL = "https://raw.githubusercontent.com/R4YTAX-devs/fhasix-cosmetics-check/main/";
    /**
     * The same repository through several addresses. raw.githubusercontent.com is the freshest, but some networks
     * (some ISPs) block it, so the game falls through to CDN copies of the SAME repo until one answers. The address
     * that worked last is tried first next time. Every file is still checked against its SHA-256 from the catalog,
     * so a stale or wrong copy from a mirror is simply skipped.
     */
    private static final String[] BASES = {
            BASE_URL,
            "https://cdn.jsdelivr.net/gh/R4YTAX-devs/fhasix-cosmetics-check@main/",
            "https://fastly.jsdelivr.net/gh/R4YTAX-devs/fhasix-cosmetics-check@main/",
            "https://gcore.jsdelivr.net/gh/R4YTAX-devs/fhasix-cosmetics-check@main/",
            "https://rawcdn.githack.com/R4YTAX-devs/fhasix-cosmetics-check/main/",
            "https://cdn.statically.io/gh/R4YTAX-devs/fhasix-cosmetics-check/main/"
    };
    private static volatile int goodBase = 0;

    private static final int CATALOG_MAX = 262_144;
    private static final int ICON_MAX = 2_000_000;
    private static final int TEX_MAX = 6_000_000;
    private static final int MAX_ICON_TEX = 24;
    private static final long RECHECK_MS = 120_000L;

    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("fhasix_cosmetics");
    private static final Path CATALOG_FILE = DIR.resolve("catalog.json");
    private static final Path ICON_DIR = DIR.resolve("icons");
    private static final Path CAPE_DIR = DIR.resolve("capes");

    private static final Pattern ID_OK = Pattern.compile("^[a-z0-9_]{1,40}$");
    private static final Pattern PATH_OK = Pattern.compile("^[a-z0-9][a-z0-9_./-]{0,120}$");
    private static final Pattern SHA_OK = Pattern.compile("^[0-9a-fA-F]{64}$");

    /** The capes that are built into the game; the catalog can never replace them. */
    private static final Set<String> BUILTIN = new HashSet<>();

    static {
        Collections.addAll(BUILTIN, FhasixCosmetics.FIRST_OPTIX, FhasixCosmetics.RED_CHARMS, FhasixCosmetics.CRIMSON_FADE,
                FhasixCosmetics.CYBER_CIRCUIT, FhasixCosmetics.VOID_RIFT, FhasixCosmetics.AURORA, FhasixCosmetics.SOLAR_FLARE);
    }

    // cape texture states
    public static final int CAPE_NONE = 0;
    public static final int CAPE_DOWNLOADING = 1;
    public static final int CAPE_READY = 2;
    public static final int CAPE_FAILED = 3;

    private FhasixCatalog() {
    }

    // ------------------------------------------------------------------ data
    /** One cosmetic of the catalog. */
    public static final class Entry {
        public final String id;
        public final String name;
        public final String storeName;
        public final String collection;
        public final int price;
        final String iconPath;
        final String iconSha;
        final String texPath;
        final String texSha;

        Entry(String id, String name, String storeName, String collection, int price,
              String iconPath, String iconSha, String texPath, String texSha) {
            this.id = id;
            this.name = name;
            this.storeName = storeName;
            this.collection = collection;
            this.price = price;
            this.iconPath = iconPath;
            this.iconSha = iconSha;
            this.texPath = texPath;
            this.texSha = texSha;
        }

        boolean same(Entry o) {
            return o != null && name.equals(o.name) && storeName.equals(o.storeName) && collection.equals(o.collection)
                    && price == o.price && iconPath.equals(o.iconPath) && iconSha.equals(o.iconSha)
                    && texPath.equals(o.texPath) && texSha.equals(o.texSha);
        }
    }

    private static final class Catalog {
        final int version;
        final List<Entry> entries;
        final String raw;

        Catalog(int version, List<Entry> entries, String raw) {
            this.version = version;
            this.entries = entries;
            this.raw = raw;
        }
    }

    private static final class Asset {
        volatile int state;
        volatile Identifier tex;
        volatile float progress;
        volatile long lastUse;
        volatile long failedAt;
        volatile long gpuBytes;
        volatile boolean loadingTex;
    }

    private interface Progress {
        void on(float fraction);
    }

    // ------------------------------------------------------------------ state
    private static final ExecutorService EXEC = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "fhasix-catalog");
        t.setDaemon(true);
        return t;
    });
    private static volatile List<Entry> entries = Collections.emptyList();
    private static volatile int localVersion = 0;
    private static volatile Catalog pending = null;
    private static volatile int checkState = 0; // 0 idle, 1 checking, 2 up to date, 3 update available, 4 failed
    private static volatile long lastCheckMs = 0L;
    private static volatile String lastError = "";
    private static volatile int applyState = 0; // 0 none, 1 updating, 2 done, 3 failed
    private static volatile int applyDone = 0;
    private static volatile int applyTotal = 0;
    private static volatile int appliedNew = 0;
    private static volatile boolean appliedFlag = false;
    private static volatile int generation = 0;
    private static int texCounter = 0;
    private static boolean inited = false;
    private static final Map<String, Asset> ICONS = new ConcurrentHashMap<>();
    private static final Map<String, Asset> CAPES = new ConcurrentHashMap<>();

    // ------------------------------------------------------------------ start up
    /** Loads the saved catalog and quietly checks GitHub for a newer one in the background. */
    public static synchronized void init() {
        if (inited) {
            return;
        }
        inited = true;
        try {
            Files.createDirectories(ICON_DIR);
            Files.createDirectories(CAPE_DIR);
        } catch (IOException ignored) {
            // no folder - everything below just fails quietly
        }
        try {
            if (Files.exists(CATALOG_FILE)) {
                Catalog saved = parse(Files.readString(CATALOG_FILE, StandardCharsets.UTF_8));
                if (saved != null) {
                    entries = saved.entries;
                    localVersion = saved.version;
                }
            }
        } catch (Exception ignored) {
            // a broken saved catalog is simply replaced by the next update
        }
        for (Entry e : entries) {
            capeAsset(e.id).state = Files.exists(capeFile(e.id)) ? CAPE_READY : CAPE_NONE;
        }
        EXEC.execute(() -> {
            try {
                Thread.sleep(5000L); // do not compete with the game while it is starting
            } catch (InterruptedException ignored) {
                return;
            }
            check();
        });
    }

    /** Asks GitHub again when the last check is a while ago (called when the Store is opened). */
    public static void checkIfStale() {
        if (checkState == 1 || applyState == 1) {
            return;
        }
        // never checked yet or the last check failed: try again soon; otherwise only every couple of minutes
        long wait = (checkState == 0 || checkState == 4) ? 8_000L : RECHECK_MS;
        if (System.currentTimeMillis() - lastCheckMs < wait) {
            return;
        }
        lastCheckMs = System.currentTimeMillis();
        EXEC.execute(FhasixCatalog::check);
    }

    /** Asks GitHub right now (the Retry button). */
    public static void recheck() {
        if (checkState == 1 || applyState == 1) {
            return;
        }
        EXEC.execute(FhasixCatalog::check);
    }

    public static boolean checking() {
        return checkState == 1;
    }

    public static boolean checkFailed() {
        return checkState == 4;
    }

    /** Short reason of the last failed check, e.g. "HTTP 404" or "no internet". */
    public static String checkError() {
        return lastError;
    }

    private static String describe(Throwable t) {
        if (t instanceof java.net.UnknownHostException || t instanceof java.net.ConnectException
                || t instanceof java.net.SocketTimeoutException) {
            return "no internet";
        }
        if (t instanceof javax.net.ssl.SSLException) {
            return "secure connection failed";
        }
        String m = t.getMessage();
        if (m != null && m.startsWith("all ")) {
            return m;
        }
        if (m != null && m.startsWith("HTTP ")) {
            return m.equals("HTTP 404") ? "HTTP 404 - file not found" : m;
        }
        if (m == null || m.isEmpty()) {
            return t.getClass().getSimpleName();
        }
        return m.length() > 40 ? m.substring(0, 40) : m;
    }

    private static void check() {
        if (checkState == 1) {
            return;
        }
        checkState = 1;
        lastCheckMs = System.currentTimeMillis();
        try {
            final Catalog[] got = new Catalog[1];
            byte[] data = fetchRepo("cosmetics.json", CATALOG_MAX, null, d -> {
                got[0] = parse(new String(d, StandardCharsets.UTF_8));
                if (got[0] == null) {
                    throw new IOException("not a valid catalog");
                }
            });
            Catalog remote = got[0];
            if (remote == null) {
                lastError = "cosmetics.json is not valid";
                System.out.println("[Fhasix] cosmetics catalog check failed: " + lastError);
                checkState = 4;
                return;
            }
            lastError = "";
            boolean newer = remote.version > localVersion || (entries.isEmpty() && !remote.entries.isEmpty());
            if (newer) {
                pending = remote;
                checkState = 3;
            } else {
                pending = null;
                checkState = 2;
            }
        } catch (Throwable t) {
            lastError = describe(t);
            System.out.println("[Fhasix] cosmetics catalog check failed: " + t);
            checkState = 4;
        }
    }

    private static int countChanges(Catalog remote) {
        int n = 0;
        for (Entry e : remote.entries) {
            if (!e.same(find(e.id))) {
                n++;
            }
        }
        for (Entry old : entries) {
            if (!containsId(remote.entries, old.id)) {
                n++;
            }
        }
        return n;
    }

    private static boolean containsId(List<Entry> list, String id) {
        for (Entry e : list) {
            if (e.id.equals(id)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ catalog access
    /** The cosmetics of the saved catalog (never null). */
    public static List<Entry> entries() {
        return entries;
    }

    public static Entry find(String id) {
        for (Entry e : entries) {
            if (e.id.equals(id)) {
                return e;
            }
        }
        return null;
    }

    public static boolean isBuiltin(String id) {
        return BUILTIN.contains(id);
    }

    public static boolean isRemote(String id) {
        return find(id) != null;
    }

    /** Bumps whenever a downloaded texture appears or goes away (the cape skin cache watches it). */
    public static int generation() {
        return generation;
    }

    // ------------------------------------------------------------------ update banner
    /** A newer catalog was found on GitHub and is waiting for the player to press Update. */
    public static boolean updateAvailable() {
        return pending != null && applyState != 1;
    }

    public static boolean updating() {
        return applyState == 1;
    }

    public static boolean updateFailed() {
        return applyState == 3 && pending != null;
    }

    public static int updateDone() {
        return applyDone;
    }

    public static int updateTotal() {
        return applyTotal;
    }

    /** How many cosmetics are new or changed in the waiting update. */
    public static int pendingCount() {
        Catalog p = pending;
        return p == null ? 0 : countChanges(p);
    }

    /** True once, right after an update finished (the Store shows its "updated" message then). */
    public static boolean consumeApplied() {
        if (appliedFlag) {
            appliedFlag = false;
            return true;
        }
        return false;
    }

    public static int lastAppliedNew() {
        return appliedNew;
    }

    /** Stores the waiting catalog and downloads the (small) icons of the new cosmetics. */
    public static void applyUpdate(Minecraft minecraft) {
        final Catalog p = pending;
        if (p == null || applyState == 1) {
            return;
        }
        applyState = 1;
        applyDone = 0;
        applyTotal = 0;
        EXEC.execute(() -> {
            try {
                List<Entry> todo = new ArrayList<>();
                for (Entry e : p.entries) {
                    if (!e.same(find(e.id))) {
                        todo.add(e);
                    }
                }
                applyTotal = todo.size();
                int fresh = 0;
                for (Entry e : todo) {
                    Entry old = find(e.id);
                    if (old == null) {
                        fresh++;
                    } else {
                        // files changed on GitHub: forget the old copies so the new ones are fetched
                        if (!old.iconSha.equals(e.iconSha) || !old.iconPath.equals(e.iconPath)) {
                            dropIcon(minecraft, e.id, true);
                        }
                        if (!old.texSha.equals(e.texSha) || !old.texPath.equals(e.texPath)) {
                            dropCape(minecraft, e.id, true);
                        }
                    }
                    try {
                        downloadIconFile(e);
                    } catch (Throwable ignored) {
                        // shown later: the icon is fetched again when the card is first drawn
                    }
                    applyDone++;
                }
                for (Entry old : entries) {
                    if (!containsId(p.entries, old.id)) {
                        dropIcon(minecraft, old.id, true);
                        dropCape(minecraft, old.id, true);
                    }
                }
                writeAtomic(CATALOG_FILE, p.raw.getBytes(StandardCharsets.UTF_8));
                for (Entry e : p.entries) {
                    Asset a = capeAsset(e.id);
                    if (a.state != CAPE_DOWNLOADING) {
                        a.state = Files.exists(capeFile(e.id)) ? CAPE_READY : CAPE_NONE;
                    }
                }
                entries = p.entries;
                localVersion = p.version;
                pending = null;
                appliedNew = fresh;
                appliedFlag = true;
                checkState = 2;
                generation++;
                applyState = 2;
            } catch (Throwable t) {
                applyState = 3;
            }
        });
    }

    /** Hides a failed-update message (the update stays available). */
    public static void clearFailure() {
        if (applyState == 3) {
            applyState = 0;
        }
    }

    // ------------------------------------------------------------------ icons (small preview slabs)
    /** The icon texture of an entry, or null while it is still loading (starts loading it when needed). */
    public static Identifier icon(Entry e) {
        Asset a = ICONS.computeIfAbsent(e.id, k -> new Asset());
        a.lastUse = System.nanoTime();
        if (a.state == 2 && a.tex != null) {
            return a.tex;
        }
        if (a.state == 3 && System.currentTimeMillis() - a.failedAt > 20_000L) {
            a.state = 0; // try again
        }
        if (a.state == 0) {
            loadIcon(e, a);
        }
        return null;
    }

    /** True while the icon could not be loaded at all (offline / missing file). */
    public static boolean iconFailed(Entry e) {
        Asset a = ICONS.get(e.id);
        return a != null && a.state == 3;
    }

    private static void loadIcon(Entry e, Asset a) {
        a.state = 1;
        final Minecraft c = Minecraft.getInstance();
        EXEC.execute(() -> {
            NativeImage img = null;
            try {
                Path f = iconFile(e.id);
                if (!Files.exists(f)) {
                    downloadIconFile(e);
                }
                try (InputStream in = Files.newInputStream(f)) {
                    img = NativeImage.read(in);
                }
                if (img.getWidth() > 1024 || img.getHeight() > 1024) {
                    img.close();
                    img = null;
                    Files.deleteIfExists(f);
                }
            } catch (Throwable t) {
                if (img != null) {
                    img.close();
                    img = null;
                }
            }
            final NativeImage ready = img;
            c.execute(() -> installIcon(c, e.id, a, ready));
        });
    }

    private static void installIcon(Minecraft c, String id, Asset a, NativeImage img) {
        if (img == null) {
            a.state = 3;
            a.failedAt = System.currentTimeMillis();
            return;
        }
        if (a.state != 1) { // reset while it was loading (Free up memory / changed on GitHub)
            img.close();
            return;
        }
        Identifier tid = Identifier.fromNamespaceAndPath("fhasixuitheme", "catalog/icon_" + id + "_" + (texCounter++));
        a.gpuBytes = (long) img.getWidth() * img.getHeight() * 4L;
        c.getTextureManager().register(tid, new DynamicTexture(() -> "fhasix_catalog_icon", img));
        a.tex = tid;
        a.state = 2;
        trimIcons(c);
    }

    /** Keeps at most MAX_ICON_TEX icons on the GPU: the longest unused ones are unloaded again. */
    private static void trimIcons(Minecraft c) {
        while (true) {
            int ready = 0;
            String oldestId = null;
            long oldest = Long.MAX_VALUE;
            for (Map.Entry<String, Asset> en : ICONS.entrySet()) {
                Asset a = en.getValue();
                if (a.state == 2 && a.tex != null) {
                    ready++;
                    if (a.lastUse < oldest) {
                        oldest = a.lastUse;
                        oldestId = en.getKey();
                    }
                }
            }
            if (ready <= MAX_ICON_TEX || oldestId == null) {
                return;
            }
            Asset a = ICONS.get(oldestId);
            c.getTextureManager().release(a.tex);
            a.tex = null;
            a.state = 0;
        }
    }

    private static void downloadIconFile(Entry e) throws IOException {
        byte[] data = fetchRepo(e.iconPath, ICON_MAX, null, d -> {
            verify(d, e.iconSha);
            checkPng(d, 1024, false);
        });
        writeAtomic(iconFile(e.id), data);
    }

    // ------------------------------------------------------------------ cape textures (downloaded one by one)
    private static Asset capeAsset(String id) {
        return CAPES.computeIfAbsent(id, k -> new Asset());
    }

    /** CAPE_NONE / CAPE_DOWNLOADING / CAPE_READY (on disk) / CAPE_FAILED. */
    public static int capeState(Entry e) {
        return capeAsset(e.id).state;
    }

    public static float capeProgress(Entry e) {
        return capeAsset(e.id).progress;
    }

    private static final Set<String> AUTO_EQUIP = ConcurrentHashMap.newKeySet();

    public static void downloadCape(Entry e) {
        downloadCape(e, false);
    }

    /**
     * Starts downloading the cape texture of ONE cosmetic. Does nothing if it is already there or coming.
     * With equipWhenReady the cape is put on as soon as the download has finished.
     */
    public static void downloadCape(Entry e, boolean equipWhenReady) {
        final Asset a = capeAsset(e.id);
        if (equipWhenReady) {
            AUTO_EQUIP.add(e.id);
        }
        if (a.state == CAPE_DOWNLOADING || a.state == CAPE_READY) {
            return;
        }
        a.state = CAPE_DOWNLOADING;
        a.progress = 0f;
        EXEC.execute(() -> {
            try {
                byte[] data = fetchRepo(e.texPath, TEX_MAX, f -> a.progress = f, d -> {
                    verify(d, e.texSha);
                    checkPng(d, 2048, true);
                });
                writeAtomic(capeFile(e.id), data);
                a.progress = 1f;
                a.state = CAPE_READY;
                generation++;
                if (AUTO_EQUIP.remove(e.id)) {
                    Minecraft.getInstance().execute(() -> {
                        if (FhasixCosmetics.owns(e.id) && !e.id.equals(FhasixCosmetics.equippedCape())) {
                            FhasixCosmetics.toggleEquip(e.id);
                        }
                    });
                }
            } catch (Throwable t) {
                AUTO_EQUIP.remove(e.id);
                a.state = CAPE_FAILED;
            }
        });
    }

    /** The registered cape texture of a downloaded cape, or null (it is loaded from disk in the background). */
    public static Identifier capeTexture(String id) {
        Asset a = CAPES.get(id);
        if (a == null || a.state != CAPE_READY) {
            return null;
        }
        if (a.tex != null) {
            return a.tex;
        }
        if (!a.loadingTex) {
            loadCapeTexture(id, a);
        }
        return null;
    }

    private static void loadCapeTexture(String id, Asset a) {
        a.loadingTex = true;
        final Minecraft c = Minecraft.getInstance();
        EXEC.execute(() -> {
            NativeImage img = null;
            try (InputStream in = Files.newInputStream(capeFile(id))) {
                img = NativeImage.read(in);
                if (img.getWidth() != img.getHeight() * 2 || img.getWidth() > 2048) {
                    img.close();
                    img = null;
                }
            } catch (Throwable t) {
                if (img != null) {
                    img.close();
                    img = null;
                }
            }
            final NativeImage ready = img;
            c.execute(() -> {
                a.loadingTex = false;
                if (ready == null) {
                    a.state = CAPE_FAILED;
                    return;
                }
                if (a.state != CAPE_READY || a.tex != null) {
                    ready.close();
                    return;
                }
                Identifier tid = Identifier.fromNamespaceAndPath("fhasixuitheme", "catalog/cape_" + id + "_" + (texCounter++));
                a.gpuBytes = (long) ready.getWidth() * ready.getHeight() * 4L;
                c.getTextureManager().register(tid, new DynamicTexture(() -> "fhasix_catalog_cape", ready));
                a.tex = tid;
                generation++;
            });
        });
    }

    // ------------------------------------------------------------------ free up memory
    /**
     * Unloads every icon texture from the GPU and deletes the downloaded cape textures (they download again when
     * needed). The cape that is being worn is kept. Returns roughly how many bytes were freed.
     */
    public static long freeMemory(Minecraft c) {
        String keep = FhasixCosmetics.equippedCape();
        long freed = 0L;
        for (Asset a : ICONS.values()) {
            if (a.tex != null) {
                c.getTextureManager().release(a.tex);
                freed += a.gpuBytes;
                a.tex = null;
            }
            if (a.state == 1 || a.state == 2) {
                a.state = 0;
            }
        }
        for (Map.Entry<String, Asset> en : CAPES.entrySet()) {
            Asset a = en.getValue();
            if (en.getKey().equals(keep) || a.state == CAPE_DOWNLOADING) {
                continue;
            }
            if (a.tex != null) {
                c.getTextureManager().release(a.tex);
                freed += a.gpuBytes;
                a.tex = null;
            }
            try {
                Path f = capeFile(en.getKey());
                if (Files.exists(f)) {
                    freed += Files.size(f);
                    Files.deleteIfExists(f);
                }
            } catch (IOException ignored) {
                // left on disk - harmless
            }
            if (a.state == CAPE_READY) {
                a.state = CAPE_NONE;
            }
            a.progress = 0f;
        }
        generation++;
        return freed;
    }

    private static void dropIcon(Minecraft c, String id, boolean deleteFile) {
        Asset a = ICONS.get(id);
        if (a != null) {
            final Identifier t = a.tex;
            a.tex = null;
            a.state = 0;
            if (t != null) {
                c.execute(() -> c.getTextureManager().release(t));
            }
        }
        if (deleteFile) {
            try {
                Files.deleteIfExists(iconFile(id));
            } catch (IOException ignored) {
                // ignore
            }
        }
    }

    private static void dropCape(Minecraft c, String id, boolean deleteFile) {
        Asset a = CAPES.get(id);
        if (a != null) {
            final Identifier t = a.tex;
            a.tex = null;
            if (a.state != CAPE_DOWNLOADING) {
                a.state = CAPE_NONE;
            }
            if (t != null) {
                c.execute(() -> c.getTextureManager().release(t));
            }
        }
        if (deleteFile) {
            try {
                Files.deleteIfExists(capeFile(id));
            } catch (IOException ignored) {
                // ignore
            }
        }
        generation++;
    }

    // ------------------------------------------------------------------ text for the cards
    /** What a not-yet-downloaded cosmetic shows instead of its price; null when it is ready. */
    public static String downloadLabel(Entry e) {
        Asset a = capeAsset(e.id);
        switch (a.state) {
            case CAPE_DOWNLOADING:
                return "DOWNLOADING " + Math.round(Math.max(0f, Math.min(1f, a.progress)) * 100f) + "%";
            case CAPE_FAILED:
                return "RETRY DOWNLOAD";
            case CAPE_READY:
                return null;
            default:
                return "DOWNLOAD";
        }
    }

    /** Total size of everything downloaded for cosmetics on disk (icons + cape textures), in bytes. */
    public static long diskBytes() {
        long n = 0L;
        for (Path dir : new Path[]{ICON_DIR, CAPE_DIR}) {
            try (java.util.stream.Stream<Path> s = Files.list(dir)) {
                for (Path p : (Iterable<Path>) s::iterator) {
                    n += Files.size(p);
                }
            } catch (Exception ignored) {
                // folder missing
            }
        }
        return n;
    }

    public static String sizeText(long bytes) {
        if (bytes < 1024L * 1024L) {
            return Math.max(1L, bytes / 1024L) + " KB";
        }
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    // ------------------------------------------------------------------ small loading ring (no background)
    public static void spinner(GuiGraphicsExtractor c, int x, int y, int size) {
        int n = 12;
        double cx = x + size / 2.0;
        double cy = y + size / 2.0;
        double rad = size * 0.34;
        int d = Math.max(2, Math.round(size * 0.12f));
        int head = (int) ((System.currentTimeMillis() / 75L) % n);
        for (int i = 0; i < n; i++) {
            int behind = ((head - i) % n + n) % n;
            float a = 1f - behind / (float) n;
            a = 0.12f + 0.88f * a * a;
            double ang = i * 2 * Math.PI / n - Math.PI / 2;
            int px = (int) Math.round(cx + Math.cos(ang) * rad - d / 2.0);
            int py = (int) Math.round(cy + Math.sin(ang) * rad - d / 2.0);
            c.fill(px, py, px + d, py + d, NewUi.withAlpha(0xFFFFFFFF, a));
        }
    }

    // ------------------------------------------------------------------ files + network
    private static Path iconFile(String id) {
        return ICON_DIR.resolve(id + ".png");
    }

    private static Path capeFile(String id) {
        return CAPE_DIR.resolve(id + ".png");
    }

    private static void writeAtomic(Path target, byte[] data) throws IOException {
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(tmp, data);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private interface Validator {
        void test(byte[] data) throws IOException;
    }

    /** Fetches a file of the repository, trying the sources one after another until one gives a valid file. */
    private static byte[] fetchRepo(String path, int maxBytes, Progress progress, Validator check) throws IOException {
        int n = BASES.length;
        String first = null;
        for (int i = 0; i < n; i++) {
            int idx = (goodBase + i) % n;
            String url = BASES[idx] + path;
            try {
                byte[] data = fetch(url, maxBytes, progress);
                if (check != null) {
                    check.test(data);
                }
                goodBase = idx;
                return data;
            } catch (Throwable t) {
                String host = url.replaceFirst("^https://", "").replaceFirst("/.*$", "");
                System.out.println("[Fhasix] cosmetics source failed: " + host + " - " + t);
                if (first == null) {
                    first = describe(t);
                }
                if (progress != null) {
                    progress.on(0f);
                }
            }
        }
        throw new IOException("all " + n + " sources failed - " + (first == null ? "unknown" : first));
    }

    private static byte[] fetch(String url, int maxBytes, Progress progress) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        try {
            con.setConnectTimeout(6000);
            con.setReadTimeout(15000);
            con.setInstanceFollowRedirects(true);
            con.setRequestProperty("User-Agent", "FhasixClient/1.0");
            int code = con.getResponseCode();
            if (code != 200) {
                throw new IOException("HTTP " + code);
            }
            long len = con.getContentLengthLong();
            if (len > maxBytes) {
                throw new IOException("file too big");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream(len > 0 ? (int) len : 16384);
            byte[] buf = new byte[16384];
            try (InputStream in = con.getInputStream()) {
                int r;
                while ((r = in.read(buf)) != -1) {
                    out.write(buf, 0, r);
                    if (out.size() > maxBytes) {
                        throw new IOException("file too big");
                    }
                    if (progress != null && len > 0) {
                        progress.on(out.size() / (float) len);
                    }
                }
            }
            return out.toByteArray();
        } finally {
            con.disconnect();
        }
    }

    private static void verify(byte[] data, String expectedSha) throws IOException {
        if (expectedSha == null || expectedSha.isEmpty()) {
            return;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : h) {
                sb.append(String.format("%02x", b));
            }
            if (!sb.toString().equalsIgnoreCase(expectedSha)) {
                throw new IOException("file does not match the catalog");
            }
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IOException(ex);
        }
    }

    /** Makes sure the bytes really are a PNG of a sane size (and, for capes, 2:1) before they are saved. */
    private static void checkPng(byte[] data, int maxSide, boolean capeShape) throws IOException {
        if (data.length < 8 || (data[0] & 255) != 0x89 || data[1] != 'P' || data[2] != 'N' || data[3] != 'G') {
            throw new IOException("not a PNG");
        }
        try (NativeImage img = NativeImage.read(new ByteArrayInputStream(data))) {
            if (img.getWidth() > maxSide || img.getHeight() > maxSide) {
                throw new IOException("image too large");
            }
            if (capeShape && img.getWidth() != img.getHeight() * 2) {
                throw new IOException("cape must be 2:1");
            }
        }
    }

    // ------------------------------------------------------------------ catalog parsing
    private static String str(JsonObject o, String key) {
        if (o == null || !o.has(key) || !o.get(key).isJsonPrimitive()) {
            return "";
        }
        return o.get(key).getAsString().trim();
    }

    private static Catalog parse(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            int version = root.has("catalogVersion") ? root.get("catalogVersion").getAsInt() : 0;
            JsonArray arr = root.getAsJsonArray("cosmetics");
            List<Entry> list = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            if (arr != null) {
                for (JsonElement el : arr) {
                    if (!el.isJsonObject()) {
                        continue;
                    }
                    JsonObject o = el.getAsJsonObject();
                    String id = str(o, "id");
                    if (!ID_OK.matcher(id).matches() || BUILTIN.contains(id) || !seen.add(id)) {
                        continue;
                    }
                    if (!"cape".equals(str(o, "type"))) {
                        continue; // only capes are supported for now
                    }
                    String name = str(o, "name");
                    if (name.isEmpty() || name.length() > 40) {
                        continue;
                    }
                    String storeName = str(o, "storeName");
                    if (storeName.isEmpty() || storeName.length() > 48) {
                        storeName = name.toUpperCase(Locale.ROOT) + " CLOAK";
                    }
                    String collection = str(o, "collection");
                    int price = o.has("price") && o.get("price").isJsonPrimitive() ? o.get("price").getAsInt() : 925;
                    price = Math.max(0, Math.min(1_000_000, price));
                    JsonObject icon = o.has("icon") && o.get("icon").isJsonObject() ? o.getAsJsonObject("icon") : null;
                    JsonObject tex = o.has("texture") && o.get("texture").isJsonObject() ? o.getAsJsonObject("texture") : null;
                    String iconPath = str(icon, "path");
                    String texPath = str(tex, "path");
                    if (!safePath(iconPath) || !safePath(texPath)) {
                        continue;
                    }
                    String iconSha = str(icon, "sha256").toLowerCase(Locale.ROOT);
                    String texSha = str(tex, "sha256").toLowerCase(Locale.ROOT);
                    if ((!iconSha.isEmpty() && !SHA_OK.matcher(iconSha).matches())
                            || (!texSha.isEmpty() && !SHA_OK.matcher(texSha).matches())) {
                        continue;
                    }
                    list.add(new Entry(id, name, storeName, collection.isEmpty() ? "store_cloak" : collection, price,
                            iconPath, iconSha, texPath, texSha));
                }
            }
            return new Catalog(version, Collections.unmodifiableList(list), json);
        } catch (Exception ex) {
            return null;
        }
    }

    private static boolean safePath(String p) {
        return PATH_OK.matcher(p).matches() && !p.contains("..") && !p.contains("//") && p.endsWith(".png");
    }
}
