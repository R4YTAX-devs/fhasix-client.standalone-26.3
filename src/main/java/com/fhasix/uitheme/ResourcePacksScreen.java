package com.fhasix.uitheme;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The in-game resource pack downloader (opens from the "Resource Packs" icon of the title-screen row).
 *
 * Same construction as the Mod Menu: every rectangle is measured from the reference picture (1512 x 965) and
 * scaled by ONE factor k. Packs come live from Modrinth (see FhasixPackApi): search, category / feature /
 * resolution filters, sorting, infinite scroll, banners + icons loaded in the background, one click on a card
 * downloads the pack into the resourcepacks folder. While the first results load, skeleton cards are shown.
 */
public final class ResourcePacksScreen extends Screen {

    // ------------------------------------------------------------------ reference geometry (picture pixels)
    private static final int RW = 1512;
    private static final int RH = 965;

    private static final int[] PANEL_REF = {107, 155, 1304, 718};
    private static final int[] LOGO_REF = {110, 66, 62, 62};
    private static final int[] DATE_REF = {440, 77, 257, 40};
    private static final int[] CLOSE_REF = {1352, 74, 47, 47};
    private static final int[] UPD_REF = {131, 178, 289, 40};
    private static final int[] CLR_REF = {131, 234, 289, 40};
    private static final int[] SEARCH_REF = {433, 178, 623, 39};
    private static final int[] SORT_REF = {1073, 178, 320, 39};

    private static final int LEFT_X = 131;
    private static final int LEFT_W = 289;
    private static final int LEFT_TOP = 285;
    private static final int BOX_X = 133;
    private static final int BOX_W = 264;
    private static final int ROW_H = 32;
    private static final int GRID_X = 433;
    private static final int GRID_W = 964;
    private static final int GRID_TOP = 233;
    private static final int[] COL_X = {435, 928};
    private static final int CARD_W = 465;
    private static final int CARD_H = 263;
    private static final int CARD_TOP = 234;
    private static final int CARD_PITCH = 291;
    private static final int BANNER_H = 160;

    private static final int PAGE = 20;
    private static final String[] SORT_LABELS = {"Relevance", "Downloads", "Follows", "Newest", "Updated"};
    private static final String[] SORT_INDEX = {"relevance", "downloads", "follows", "newest", "updated"};

    // ------------------------------------------------------------------ colours
    private static final int TXT = 0xFFFFFFFF;
    private static final int TXT_SOFT = 0xFFE9E9EC;
    private static final int TXT_GREY = 0xFF9A9DA3;
    private static final int TXT_DIM = 0xFF7F8288;
    private static final int PANEL_T = NewUi.rgb(15, 17, 20);
    private static final int PANEL_B = NewUi.rgb(14, 16, 19);
    private static final int PANEL_EDGE = NewUi.rgb(23, 26, 31);
    private static final int CARD_T = NewUi.rgb(16, 18, 21);
    private static final int CARD_B = NewUi.rgb(14, 16, 19);
    private static final int GREEN = NewUi.rgb(58, 170, 76);
    private static final int AMBER = NewUi.rgb(226, 160, 44);
    private static final int ERR = NewUi.rgb(226, 92, 92);

    // ------------------------------------------------------------------ textures
    private static final Identifier ICONS = Identifier.fromNamespaceAndPath("fhasixuitheme", "textures/gui/fhasix_pack_icons.png");
    private static final int ICON_CELL = 64;
    private static final int ICONS_W = 512;
    private static final int ICONS_H = 256;
    private static final int I_DOWNLOAD = 21;
    private static final int I_CHEV_R = 22;
    private static final int I_CHEV_D = 23;
    private static final int I_CHECK = 24;
    private static final int I_X = 25;
    private static final int I_REFRESH = 26;
    private static final int I_ALERT = 27;

    private static final Identifier MARK = Identifier.fromNamespaceAndPath("fhasixuitheme", "textures/gui/title/new_fhasix_mark.png");
    private static final int MARK_TEX = 256;
    private static final Identifier FX_MOON = Identifier.fromNamespaceAndPath("fhasixuitheme", "textures/gui/fx/fx_moon.png");
    private static final Identifier FX_CLOSE = Identifier.fromNamespaceAndPath("fhasixuitheme", "textures/gui/fx/fx_close.png");
    private static final int FX_SIZE = 96;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("yyyy/MM/dd  hh:mm a", Locale.ENGLISH);

    /** tag (category / feature) -> icon cell of the atlas; resolutions and loaders have no icon. */
    private static final Map<String, Integer> TAG_ICON = new HashMap<>();

    static {
        String[] names = {"combat", "cursed", "decoration", "modded", "realistic", "simplistic", "themed", "tweaks",
                "utility", "vanilla-like", "audio", "blocks", "core-shaders", "entities", "environment", "equipment",
                "fonts", "gui", "items", "locale", "models"};
        for (int i = 0; i < names.length; i++) {
            TAG_ICON.put(names[i], i);
        }
    }

    // ------------------------------------------------------------------ background work
    private static final ExecutorService API_EXEC = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "fhasix-packs-api");
        t.setDaemon(true);
        return t;
    });
    private static final ExecutorService IMG_EXEC = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "fhasix-packs-img");
        t.setDaemon(true);
        return t;
    });
    private static final ExecutorService INSTALL_EXEC = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "fhasix-packs-install");
        t.setDaemon(true);
        return t;
    });

    // ------------------------------------------------------------------ small model classes
    private static final class Sec {
        final String title;
        final String[] labels;
        final String[] keys;
        final boolean res;
        boolean open = true;

        Sec(String title, boolean res, String[] labels, String[] keys) {
            this.title = title;
            this.res = res;
            this.labels = labels;
            this.keys = keys;
        }
    }

    private static final class Img {
        volatile int state; // 0 none, 1 loading, 2 ready, 3 failed
        Identifier id;
        int w;
        int h;
        float fade;
    }

    private static final class Inst {
        volatile int state; // 1 downloading, 2 done, 3 failed
        volatile float progress;
        volatile String error = "";
    }

    private static final int HIT_NONE = 0;
    private static final int HIT_HEAD = 1;
    private static final int HIT_ROW = 2;
    private static final int HIT_CARD = 3;
    private static final int HIT_RETRY = 4;

    private static final class Hit {
        final int kind;
        final int a;
        final int b;

        Hit(int kind, int a, int b) {
            this.kind = kind;
            this.a = a;
            this.b = b;
        }
    }

    // ------------------------------------------------------------------ state
    private final Screen parent;
    private final Sec[] secs = {
            new Sec("CATEGORIES", false,
                    new String[]{"Combat", "Cursed", "Decoration", "Modded", "Realistic", "Simplistic", "Themed", "Tweaks", "Utility", "Vanilla-Like"},
                    new String[]{"combat", "cursed", "decoration", "modded", "realistic", "simplistic", "themed", "tweaks", "utility", "vanilla-like"}),
            new Sec("FEATURES", false,
                    new String[]{"Audio", "Blocks", "Core-Shaders", "Entities", "Environment", "Equipment", "Fonts", "GUI", "Items", "Locale", "Models"},
                    new String[]{"audio", "blocks", "core-shaders", "entities", "environment", "equipment", "fonts", "gui", "items", "locale", "models"}),
            new Sec("RESOLUTIONS", true,
                    new String[]{"8x-", "16x", "32x", "48x", "64x", "128x", "256x", "512x+"},
                    new String[]{"8x-", "16x", "32x", "48x", "64x", "128x", "256x", "512x+"})
    };
    private final Set<String> selected = new HashSet<>();

    private EditBox searchField;
    private String keepText = "";
    private String lastQuery = "";
    private long queryChangedAt = 0L;
    private boolean queryPending = false;
    private boolean started = false;
    private int sortIdx = 0;
    private boolean sortOpen = false;

    private final List<FhasixPackApi.Hit> hits = new ArrayList<>();
    private int total = 0;
    private boolean loading = false;
    private String error = null;
    private int searchToken = 0;
    private boolean dead = false;

    private final Map<String, Img> imgs = new HashMap<>();
    private final Map<String, Inst> installs = new HashMap<>();
    private final Map<String, Boolean> installed = new HashMap<>();
    private final Set<String> updates = new HashSet<>();
    private int imgCounter = 0;

    private String updText = null;
    private long updUntil = 0L;
    private boolean updChecking = false;

    private final Map<String, Float> anim = new HashMap<>();
    private final Map<String, long[]> ripples = new HashMap<>();
    private long animLast = System.nanoTime();
    private float animDt = 0f;
    private String clockText = "";
    private long clockAt = 0L;

    // layout (screen pixels)
    private float k;
    private int ox;
    private int oy;
    private int[] panel;
    private int[] logoR;
    private int[] dateR;
    private int[] closeR;
    private int[] updR;
    private int[] clrR;
    private int[] searchR;
    private int[] sortR;
    private int leftL;
    private int leftR;
    private int leftT;
    private int leftB;
    private int gridL;
    private int gridR;
    private int gridT;
    private int gridB;
    private int cardW;
    private int cardH;
    private int bannerH;
    private int pitchY;
    private int[] secHeadY = new int[3];
    private int[] secBoxY = new int[3];
    private int[] secBoxH = new int[3];
    private int leftContentEnd;

    private float leftCur = 0f;
    private float leftTarget = 0f;
    private float gridCur = 0f;
    private float gridTarget = 0f;
    private long lastNanos = 0L;
    private int pressArea = 0; // 1 left, 2 grid
    private boolean dragScrolling = false;
    private double travel = 0.0;
    private Hit pressedHit = null;

    public ResourcePacksScreen(Screen parent) {
        super(Component.literal("RESOURCE PACKS"));
        this.parent = parent;
    }

    // ------------------------------------------------------------------ layout helpers
    private int sx(int v) {
        return ox + Math.round(v * k);
    }

    private int sy(int v) {
        return oy + Math.round(v * k);
    }

    private int sl(int v) {
        return Math.max(1, Math.round(v * k));
    }

    private int[] rect(int[] r) {
        int x0 = sx(r[0]);
        int y0 = sy(r[1]);
        return new int[]{x0, y0, sx(r[0] + r[2]) - x0, sy(r[1] + r[3]) - y0};
    }

    private float fs(float design) {
        return design * k;
    }

    @Override
    protected void init() {
        k = Math.min(width / (float) RW, height / (float) RH);
        ox = (width - Math.round(RW * k)) / 2;
        oy = (height - Math.round(RH * k)) / 2;

        panel = rect(PANEL_REF);
        logoR = rect(LOGO_REF);
        dateR = rect(DATE_REF);
        closeR = rect(CLOSE_REF);
        updR = rect(UPD_REF);
        clrR = rect(CLR_REF);
        searchR = rect(SEARCH_REF);
        sortR = rect(SORT_REF);

        leftL = sx(LEFT_X);
        leftR = sx(LEFT_X + LEFT_W);
        leftT = sy(LEFT_TOP);
        leftB = panel[1] + panel[3] - 1;
        gridL = sx(GRID_X);
        gridR = sx(GRID_X + GRID_W);
        gridT = sy(GRID_TOP);
        gridB = panel[1] + panel[3] - 1;
        cardW = sx(COL_X[0] + CARD_W) - sx(COL_X[0]);
        cardH = sy(CARD_TOP + CARD_H) - sy(CARD_TOP);
        bannerH = sy(CARD_TOP + BANNER_H) - sy(CARD_TOP);
        pitchY = sy(CARD_TOP + CARD_PITCH) - sy(CARD_TOP);

        int padX = sl(18);
        String keep = searchField != null ? searchField.getValue() : keepText;
        searchField = new EditBox(font, searchR[0] + padX,
                searchR[1] + (searchR[3] - font.lineHeight) / 2,
                Math.max(10, searchR[2] - padX * 2 - sl(30)), font.lineHeight + 2, Component.literal("Search"));
        searchField.setMaxLength(48);
        searchField.setBordered(false);
        searchField.setValue(keep);
        // The field only handles typing / focus; its text and caret are drawn by this screen (smooth font).
        this.addRenderableWidget(searchField);

        computeLeft();
        if (!started) {
            started = true;
            startSearch(false);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void removed() {
        dead = true;
        for (Img im : imgs.values()) {
            if (im.id != null) {
                minecraft.getTextureManager().release(im.id);
                im.id = null;
            }
        }
        imgs.clear();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void closeScreen() {
        minecraft.gui.setScreen(parent);
    }

    // ------------------------------------------------------------------ left column model
    private void computeLeft() {
        int y = 312;
        for (int s = 0; s < secs.length; s++) {
            secHeadY[s] = y;
            if (secs[s].open) {
                secBoxY[s] = y + 31;
                secBoxH[s] = secs[s].labels.length * ROW_H + 12;
                y = secBoxY[s] + secBoxH[s] + 27;
            } else {
                secBoxY[s] = y + 31;
                secBoxH[s] = 0;
                y += 46;
            }
        }
        leftContentEnd = y;
    }

    private float maxLeftScroll() {
        return Math.max(0f, sy(leftContentEnd) - leftB);
    }

    private float maxGridScroll() {
        int n = hits.size() + (loading ? (hits.isEmpty() ? 6 : 2) : 0);
        int rows = (n + 1) / 2;
        if (rows == 0) {
            return 0f;
        }
        int content = rows * pitchY - (pitchY - cardH) + sl(20);
        return Math.max(0f, content - (gridB - gridT - sl(1)));
    }

    // ------------------------------------------------------------------ search / data
    private void startSearch(boolean append) {
        final int token = ++searchToken;
        final int offset = append ? hits.size() : 0;
        if (!append) {
            hits.clear();
            total = 0;
            gridCur = 0f;
            gridTarget = 0f;
        }
        loading = true;
        error = null;
        final String q = searchField == null ? "" : searchField.getValue();
        final List<String> cats = new ArrayList<>();
        final List<String> res = new ArrayList<>();
        for (Sec s : secs) {
            for (String key : s.keys) {
                if (selected.contains(key)) {
                    (s.res ? res : cats).add(key);
                }
            }
        }
        final String index = SORT_INDEX[sortIdx];
        API_EXEC.execute(() -> {
            FhasixPackApi.SearchResult r = null;
            String err = null;
            try {
                r = FhasixPackApi.search(q, cats, res, index, offset, PAGE);
            } catch (Throwable t) {
                String m = t.getMessage();
                err = m == null || m.isEmpty() ? "Could not reach Modrinth" : m;
            }
            final FhasixPackApi.SearchResult fr = r;
            final String ferr = err;
            minecraft.execute(() -> {
                if (dead || token != searchToken) {
                    return;
                }
                loading = false;
                if (ferr != null || fr == null) {
                    error = ferr == null ? "Could not reach Modrinth" : ferr;
                    return;
                }
                total = fr.total;
                for (FhasixPackApi.Hit h : fr.hits) {
                    hits.add(h);
                    installed.put(h.id, FhasixPackApi.isInstalled(minecraft, h.id));
                }
            });
        });
    }

    private void tickSearch(long nowNanos) {
        String q = searchField == null ? "" : searchField.getValue();
        if (!q.equals(lastQuery)) {
            lastQuery = q;
            queryChangedAt = nowNanos;
            queryPending = true;
        }
        if (queryPending && nowNanos - queryChangedAt > 380_000_000L) {
            queryPending = false;
            startSearch(false);
        }
    }

    private void checkUpdates() {
        if (updChecking) {
            return;
        }
        updChecking = true;
        updText = "Checking...";
        updUntil = Long.MAX_VALUE;
        API_EXEC.execute(() -> {
            final List<String> found = FhasixPackApi.findUpdates(minecraft);
            minecraft.execute(() -> {
                updChecking = false;
                if (dead) {
                    return;
                }
                updates.addAll(found);
                updText = found.isEmpty() ? "All packs up to date"
                        : found.size() + (found.size() == 1 ? " update available" : " updates available");
                updUntil = System.currentTimeMillis() + 4000L;
            });
        });
    }

    private void startInstall(FhasixPackApi.Hit hit) {
        Inst cur = installs.get(hit.id);
        if (cur != null && cur.state == 1) {
            return;
        }
        final Inst inst = new Inst();
        inst.state = 1;
        installs.put(hit.id, inst);
        INSTALL_EXEC.execute(() -> {
            final FhasixPackApi.InstallResult r = FhasixPackApi.install(minecraft, hit,
                    (done, tot) -> inst.progress = tot > 0 ? Math.min(1f, done / (float) tot) : 0f);
            minecraft.execute(() -> {
                if (r.ok) {
                    inst.state = 2;
                    installed.put(hit.id, true);
                    updates.remove(hit.id);
                } else {
                    inst.state = 3;
                    inst.error = r.error;
                }
            });
        });
    }

    private void requestImage(String url, int maxW) {
        if (url == null || imgs.containsKey(url)) {
            return;
        }
        final Img im = new Img();
        im.state = 1;
        imgs.put(url, im);
        IMG_EXEC.execute(() -> {
            NativeImage ready = null;
            try {
                ready = FhasixPackApi.loadImage(url, maxW);
            } catch (Throwable ignored) {
                ready = null;
            }
            final NativeImage fr = ready;
            minecraft.execute(() -> installImage(im, fr));
        });
    }

    private void installImage(Img im, NativeImage img) {
        if (img == null) {
            im.state = 3;
            return;
        }
        if (dead) {
            img.close();
            return;
        }
        im.w = img.getWidth();
        im.h = img.getHeight();
        Identifier id = Identifier.fromNamespaceAndPath("fhasixuitheme", "packs/img_" + (imgCounter++));
        minecraft.getTextureManager().register(id, new DynamicTexture(() -> "fhasix_pack_image", img));
        im.id = id;
        im.fade = 0f;
        im.state = 2;
    }

    // ------------------------------------------------------------------ animation + drawing helpers
    private float ease(String key, float target, float speed) {
        if (NewPerf.animationsOff()) {
            anim.put(key, target);
            return target;
        }
        float cur = anim.getOrDefault(key, target);
        cur += (target - cur) * Math.min(1f, animDt * speed);
        if (Math.abs(target - cur) < 0.004f) {
            cur = target;
        }
        anim.put(key, cur);
        return cur;
    }

    private static int mix(int a, int b, float t) {
        return NewUi.lerp(a, b, t);
    }

    private void text(GuiGraphicsExtractor c, String s, int x, int y, int color, int weight, float size) {
        NewFont.drawUi(c, font, s, x, y, color, weight, size);
    }

    private int tw(String s, int weight, float size) {
        return NewFont.widthUi(font, s, weight, size);
    }

    /** Cuts text with "..." so it fits maxW. */
    private String fit(String s, int weight, float size, int maxW) {
        if (maxW <= 0) {
            return "";
        }
        if (tw(s, weight, size) <= maxW) {
            return s;
        }
        String cut = s;
        while (cut.length() > 1 && tw(cut + "...", weight, size) > maxW) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "...";
    }

    private void icon(GuiGraphicsExtractor c, int idx, int x, int y, int size, int color) {
        int u = (idx % 8) * ICON_CELL;
        int v = (idx / 8) * ICON_CELL;
        c.blit(RenderPipelines.GUI_TEXTURED, ICONS, x, y, u, v, size, size, ICON_CELL, ICON_CELL, ICONS_W, ICONS_H, color);
    }

    private static int cornerInset(int row, int r) {
        if (row >= r) {
            return 0;
        }
        double dy = r - row - 0.5;
        return (int) Math.round(r - Math.sqrt(Math.max(0.0, r * r - dy * dy)));
    }

    /** A texture drawn "cover" style into x,y,w,h with rounded corners (top only, or all four). */
    private void roundTex(GuiGraphicsExtractor c, Identifier id, int x, int y, int w, int h, int r, boolean topOnly,
                          int tw, int th, int color) {
        if (w <= 0 || h <= 0 || tw <= 0 || th <= 0) {
            return;
        }
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        float sc = Math.max(w / (float) tw, h / (float) th);
        float rwF = w / sc;
        float rhF = h / sc;
        float u0 = (tw - rwF) / 2f;
        float v0 = (th - rhF) / 2f;
        float fx = rwF / w;
        float fy = rhF / h;
        int midTop = y + r;
        int midBot = topOnly ? y + h : y + h - r;
        if (midBot > midTop) {
            int rv = clamp(Math.round(v0 + r * fy), 0, th - 1);
            int rh = clamp(Math.round((midBot - midTop) * fy), 1, th - rv);
            int ru = clamp(Math.round(u0), 0, tw - 1);
            int rwid = clamp(Math.round(rwF), 1, tw - ru);
            c.blit(RenderPipelines.GUI_TEXTURED, id, x, midTop, ru, rv, w, midBot - midTop, rwid, rh, tw, th, color);
        }
        for (int i = 0; i < r; i++) {
            int in = cornerInset(i, r);
            int sw = w - 2 * in;
            if (sw <= 0) {
                continue;
            }
            int ru = clamp(Math.round(u0 + in * fx), 0, tw - 1);
            int rwid = clamp(Math.round(sw * fx), 1, tw - ru);
            int rv = clamp(Math.round(v0 + i * fy), 0, th - 1);
            int rh = clamp(Math.round(fy), 1, th - rv);
            c.blit(RenderPipelines.GUI_TEXTURED, id, x + in, y + i, ru, rv, sw, 1, rwid, rh, tw, th, color);
            if (!topOnly) {
                int rv2 = clamp(Math.round(v0 + (h - 1 - i) * fy), 0, th - 1);
                int rh2 = clamp(Math.round(fy), 1, th - rv2);
                c.blit(RenderPipelines.GUI_TEXTURED, id, x + in, y + h - 1 - i, ru, rv2, sw, 1, rwid, rh2, tw, th, color);
            }
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(Math.max(lo, hi), v));
    }

    /** Solid colour with rounded TOP corners (a banner without a picture). */
    private void roundTopFill(GuiGraphicsExtractor c, int x, int y, int w, int h, int r, int top, int bot) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int i = 0; i < r; i++) {
            int in = cornerInset(i, r);
            c.fill(x + in, y + i, x + w - in, y + i + 1, mix(top, bot, i / (float) Math.max(1, h)));
        }
        c.fillGradient(x, y + r, x + w, y + h, mix(top, bot, r / (float) Math.max(1, h)), bot);
    }

    private void drawRed(GuiGraphicsExtractor c, int[] r, String label, String key, int mx, int my) {
        boolean over = NewUi.in(mx, my, r[0], r[1], r[2], r[3]) && !sortOpen;
        float hv = ease("red:" + key, over ? 1f : 0f, 14f);
        int col = mix(FhasixTheme.red(), FhasixTheme.redHover(), hv);
        int rad = Math.max(3, sl(8));
        NewUi.rrect(c, r[0], r[1], r[2], r[3], rad, col, col);
        long[] rp = ripples.get(key);
        if (rp != null) {
            float p = (System.nanoTime() - rp[0]) / 450_000_000f;
            if (p >= 1f) {
                ripples.remove(key);
            } else {
                float ep = 1f - (1f - p) * (1f - p);
                float far = (float) Math.hypot(Math.max(rp[1] - r[0], r[0] + r[2] - rp[1]), Math.max(rp[2] - r[1], r[1] + r[3] - rp[2]));
                NewUi.ripple(c, r[0], r[1], r[2], r[3], rad, rp[1], rp[2], far * ep, NewUi.withAlpha(0xFFFFFFFF, 0.28f * (1f - p) + 0.02f));
            }
        }
        float sz = fs(15f);
        text(c, label, r[0] + (r[2] - tw(label, NewFont.W_BOLD, sz)) / 2, NewFont.centerYUi(r[1], r[3], sz), TXT, NewFont.W_BOLD, sz);
    }

    // ------------------------------------------------------------------ render
    @Override
    public void extractRenderState(GuiGraphicsExtractor c, int mx, int my, float delta) {
        long nowN = System.nanoTime();
        animDt = Math.min(0.1f, (nowN - animLast) / 1_000_000_000f);
        animLast = nowN;
        if (parent != null) {
            NewModMenuScreen.drawTitleBackdrop(c, width, height);
        } else {
            c.fill(0, 0, width, height, 0xB0000000);
        }
        tickSearch(nowN);
        tickScroll(nowN);
        // infinite scroll: ask for the next page when the end is near
        if (!loading && error == null && !hits.isEmpty() && hits.size() < total
                && gridCur > maxGridScroll() - pitchY) {
            startSearch(true);
        }

        drawHeader(c, mx, my);
        NewUi.frame(c, panel[0], panel[1], panel[2], panel[3], Math.max(3, sl(12)), PANEL_EDGE, PANEL_T, PANEL_B);
        boolean blocked = sortOpen && sortListContains(mx, my);
        int hx = blocked ? -10000 : mx;
        int hy = blocked ? -10000 : my;
        drawLeft(c, hx, hy);
        drawToolbar(c, hx, hy);
        drawGrid(c, hx, hy);
        drawSortList(c, mx, my);
    }

    private void tickScroll(long now) {
        leftTarget = Math.max(0f, Math.min(maxLeftScroll(), leftTarget));
        gridTarget = Math.max(0f, Math.min(maxGridScroll(), gridTarget));
        float dt = lastNanos == 0L ? 0f : Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
        lastNanos = now;
        if (pressArea == 1 && dragScrolling) {
            leftCur = leftTarget;
        } else {
            leftCur += (leftTarget - leftCur) * (1f - (float) Math.exp(-18f * dt));
            if (Math.abs(leftTarget - leftCur) < 0.3f) {
                leftCur = leftTarget;
            }
        }
        if (pressArea == 2 && dragScrolling) {
            gridCur = gridTarget;
        } else {
            gridCur += (gridTarget - gridCur) * (1f - (float) Math.exp(-18f * dt));
            if (Math.abs(gridTarget - gridCur) < 0.3f) {
                gridCur = gridTarget;
            }
        }
    }

    private void drawHeader(GuiGraphicsExtractor c, int mx, int my) {
        c.blit(RenderPipelines.GUI_TEXTURED, MARK, logoR[0], logoR[1], 0, 0, logoR[2], logoR[3],
                MARK_TEX, MARK_TEX, MARK_TEX, MARK_TEX);
        String title = "RESOURCE PACKS";
        float tsz = fs(25f);
        int tx = sx(187);
        text(c, title, tx, NewFont.centerYUi(logoR[1], logoR[3], tsz), TXT, NewFont.W_BOLD, tsz);
        int tWidth = tw(title, NewFont.W_BOLD, tsz);

        long ms = System.currentTimeMillis();
        if (ms - clockAt > 500L || clockText.isEmpty()) {
            clockAt = ms;
            clockText = LocalDateTime.now().format(CLOCK);
        }
        float dsz = fs(17f);
        int moon = Math.max(8, Math.round(dateR[3] * 0.50f));
        int dtw = tw(clockText, NewFont.W_BOLD, dsz);
        int dw = sl(22) + moon + sl(16) + dtw + sl(24);
        int dx = Math.max(dateR[0], tx + tWidth + sl(34));
        int rad = Math.max(3, sl(9));
        NewUi.rrect(c, dx, dateR[1], dw, dateR[3], rad, PANEL_T, PANEL_B);
        NewButtonArt.edge(c, dx, dateR[1], dw, dateR[3], rad, 0.20f);
        NewUi.tex(c, FX_MOON, dx + sl(22), dateR[1] + (dateR[3] - moon) / 2, moon, FX_SIZE);
        text(c, clockText, dx + sl(22) + moon + sl(16), NewFont.centerYUi(dateR[1], dateR[3], dsz), TXT, NewFont.W_BOLD, dsz);

        boolean ch = NewUi.in(mx, my, closeR[0], closeR[1], closeR[2], closeR[3]) && !sortOpen;
        float cv = ease("close", ch ? 1f : 0f, 14f);
        int crad = Math.max(3, sl(9));
        int fill = NewUi.withAlpha(FhasixTheme.red(), 0.13f + 0.17f * cv);
        NewUi.rrect(c, closeR[0], closeR[1], closeR[2], closeR[3], crad, fill, fill);
        NewUi.ring(c, closeR[0], closeR[1], closeR[2], closeR[3], crad, mix(FhasixTheme.ring(), FhasixTheme.redHover(), cv));
        int xs = Math.max(8, Math.round(closeR[3] * 0.50f));
        NewUi.tex(c, FX_CLOSE, closeR[0] + (closeR[2] - xs) / 2, closeR[1] + (closeR[3] - xs) / 2, xs, FX_SIZE);
    }

    // ------------------------------------------------------------------ left column
    private int leftY(int ref) {
        return sy(ref) - Math.round(leftCur);
    }

    private void drawLeft(GuiGraphicsExtractor c, int mx, int my) {
        String upd = updR != null && updText != null && System.currentTimeMillis() < updUntil ? updText : "Check For Updates";
        drawRed(c, updR, upd, "upd", mx, my);
        drawRed(c, clrR, "Clear Filters", "clr", mx, my);

        c.enableScissor(leftL, leftT, leftR, leftB);
        int boxX = sx(BOX_X);
        int boxW = sx(BOX_X + BOX_W) - boxX;
        int rad = Math.max(3, sl(8));
        float hsz = fs(15.5f);
        float lsz = fs(14.5f);
        for (int s = 0; s < secs.length; s++) {
            Sec sec = secs[s];
            int hy = leftY(secHeadY[s]);
            boolean hh = NewUi.in(mx, my, leftL, hy - sl(14), leftR - leftL, sl(28)) && NewUi.in(mx, my, leftL, leftT, leftR - leftL, leftB - leftT);
            float hv = ease("head:" + s, hh ? 1f : 0f, 14f);
            int cs = Math.max(8, sl(13));
            icon(c, sec.open ? I_CHEV_D : I_CHEV_R, sx(LEFT_X) + sl(2), hy - cs / 2, cs, mix(FhasixTheme.red(), FhasixTheme.redHover(), hv));
            text(c, sec.title, sx(148), NewFont.centerYUi(hy - sl(10), sl(20), hsz), TXT, NewFont.W_BOLD, hsz);
            if (!sec.open) {
                continue;
            }
            int by = leftY(secBoxY[s]);
            int bh = sy(secBoxY[s] + secBoxH[s]) - sy(secBoxY[s]);
            if (by + bh < leftT || by > leftB) {
                continue;
            }
            NewUi.rrect(c, boxX, by, boxW, bh, rad, CARD_T, CARD_B);
            NewButtonArt.edge(c, boxX, by, boxW, bh, rad, 0.20f);
            for (int i = 0; i < sec.labels.length; i++) {
                int ry = leftY(secBoxY[s] + 6 + i * ROW_H);
                int rh = sy(ROW_H) - sy(0);
                if (ry + rh < leftT || ry > leftB) {
                    continue;
                }
                boolean sel = selected.contains(sec.keys[i]);
                boolean over = NewUi.in(mx, my, boxX + 1, ry, boxW - 2, rh) && NewUi.in(mx, my, leftL, leftT, leftR - leftL, leftB - leftT);
                float ov = over ? 1f : 0f; // hovering only brightens the name - no highlight box, no fade
                int cy = ry + rh / 2;
                int cb = Math.max(8, sl(16));
                int cbx = sx(165);
                if (sel) {
                    NewUi.rrect(c, cbx, cy - cb / 2, cb, cb, cb / 2, FhasixTheme.red(), FhasixTheme.red());
                    int ck = Math.max(6, Math.round(cb * 0.72f));
                    icon(c, I_CHECK, cbx + (cb - ck) / 2, cy - ck / 2, ck, 0xFFFFFFFF);
                } else {
                    NewUi.frame(c, cbx, cy - cb / 2, cb, cb, cb / 2, NewUi.rgb(30, 33, 39), NewUi.rgb(21, 23, 27), NewUi.rgb(21, 23, 27));
                }
                int is = Math.max(8, sl(16));
                int lcol = sel ? TXT : mix(TXT_GREY, TXT, ov);
                if (!sec.res) {
                    Integer ic = TAG_ICON.get(sec.keys[i]);
                    if (ic != null) {
                        icon(c, ic, sx(185), cy - is / 2, is, lcol);
                    }
                }
                text(c, sec.labels[i], sx(210), NewFont.centerYUi(ry, rh, lsz), lcol, NewFont.W_MED, lsz);
            }
        }
        c.disableScissor();

        float max = maxLeftScroll();
        if (max > 0f) {
            int track = leftB - leftT;
            int th = Math.max(sl(24), Math.round(track * (track / (track + max))));
            int ty = leftT + Math.round((track - th) * (leftCur / max));
            int bw = Math.max(2, sl(4));
            NewUi.rrect(c, sx(413), ty, bw, th, bw / 2, NewUi.rgb(88, 89, 92), NewUi.rgb(80, 81, 84));
        }
    }

    // ------------------------------------------------------------------ toolbar
    private void drawToolbar(GuiGraphicsExtractor c, int mx, int my) {
        boolean sh = NewUi.in(mx, my, searchR[0], searchR[1], searchR[2], searchR[3]);
        int sc = mix(NewUi.rgb(25, 28, 31), NewUi.rgb(33, 37, 42), ease("search", sh ? 1f : 0f, 14f));
        int rad = Math.max(3, sl(8));
        NewUi.rrect(c, searchR[0], searchR[1], searchR[2], searchR[3], rad, sc, sc);
        float ssz = fs(15f);
        int tx = searchR[0] + sl(18);
        int maxW = searchR[2] - sl(18) - sl(46);
        String q = searchField == null ? "" : searchField.getValue();
        int ty = NewFont.centerYUi(searchR[1], searchR[3], ssz);
        int caretX = tx;
        if (q.isEmpty()) {
            text(c, "Search...", tx, ty, 0xFF8A8D93, NewFont.W_MED, ssz);
        } else {
            String shown = q;
            while (shown.length() > 1 && tw(shown, NewFont.W_MED, ssz) > maxW) {
                shown = shown.substring(1);
            }
            text(c, shown, tx, ty, TXT, NewFont.W_MED, ssz);
            caretX = tx + tw(shown, NewFont.W_MED, ssz);
            int xs = Math.max(8, sl(17));
            boolean xh = NewUi.in(mx, my, clearXRect()[0], clearXRect()[1], clearXRect()[2], clearXRect()[3]);
            icon(c, I_X, searchR[0] + searchR[2] - sl(22) - xs, searchR[1] + (searchR[3] - xs) / 2, xs, xh ? TXT : TXT_GREY);
        }
        if (searchField != null && searchField.isFocused() && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            int chh = Math.max(6, Math.round(searchR[3] * 0.5f));
            c.fill(caretX + 1, searchR[1] + (searchR[3] - chh) / 2, caretX + 1 + Math.max(1, sl(2)),
                    searchR[1] + (searchR[3] - chh) / 2 + chh, TXT);
        }

        boolean oh = NewUi.in(mx, my, sortR[0], sortR[1], sortR[2], sortR[3]);
        int oc = mix(NewUi.rgb(25, 28, 31), NewUi.rgb(33, 37, 42), ease("sort", oh || sortOpen ? 1f : 0f, 14f));
        NewUi.rrect(c, sortR[0], sortR[1], sortR[2], sortR[3], rad, oc, oc);
        text(c, SORT_LABELS[sortIdx], sortR[0] + sl(16), NewFont.centerYUi(sortR[1], sortR[3], ssz), 0xFF8A8D93, NewFont.W_MED, ssz);
        int cs = Math.max(8, sl(14));
        icon(c, I_CHEV_D, sortR[0] + sortR[2] - sl(18) - cs, sortR[1] + (sortR[3] - cs) / 2, cs, 0xFF8A8D93);
    }

    private int[] clearXRect() {
        int xs = Math.max(8, sl(17));
        int pad = sl(6);
        return new int[]{searchR[0] + searchR[2] - sl(22) - xs - pad, searchR[1], xs + pad * 2, searchR[3]};
    }

    // ------------------------------------------------------------------ sort dropdown
    private int[] sortListRect() {
        int rh = Math.max(14, sl(36));
        int pad = sl(6);
        return new int[]{sortR[0], sortR[1] + sortR[3] + sl(6), sortR[2], rh * SORT_LABELS.length + pad * 2};
    }

    private boolean sortListContains(double mx, double my) {
        int[] r = sortListRect();
        return NewUi.in(mx, my, r[0], r[1], r[2], r[3]);
    }

    private void drawSortList(GuiGraphicsExtractor c, int mx, int my) {
        if (!sortOpen) {
            return;
        }
        int[] r = sortListRect();
        int rad = Math.max(3, sl(8));
        NewUi.rrect(c, r[0], r[1], r[2], r[3], rad, NewUi.rgb(19, 21, 25), NewUi.rgb(17, 19, 22));
        NewButtonArt.edge(c, r[0], r[1], r[2], r[3], rad, 0.26f);
        int rh = Math.max(14, sl(36));
        int pad = sl(6);
        float sz = fs(15f);
        for (int i = 0; i < SORT_LABELS.length; i++) {
            int ry = r[1] + pad + i * rh;
            boolean over = NewUi.in(mx, my, r[0] + pad, ry, r[2] - pad * 2, rh);
            float hv = ease("sorti:" + i, over ? 1f : 0f, 16f);
            if (hv > 0.02f) {
                int hc = NewUi.withAlpha(0xFFFFFFFF, 0.06f * hv);
                NewUi.rrect(c, r[0] + pad, ry, r[2] - pad * 2, rh, Math.max(3, sl(6)), hc, hc);
            }
            text(c, SORT_LABELS[i], r[0] + sl(16), NewFont.centerYUi(ry, rh, sz), i == sortIdx ? TXT : mix(TXT_GREY, TXT, hv), NewFont.W_MED, sz);
            if (i == sortIdx) {
                int cs = Math.max(8, sl(14));
                icon(c, I_CHECK, r[0] + r[2] - sl(18) - cs, ry + (rh - cs) / 2, cs, FhasixTheme.red());
            }
        }
    }

    // ------------------------------------------------------------------ grid
    private int cardX(int i) {
        return sx(COL_X[i % 2]);
    }

    private int cardY(int i) {
        return sy(CARD_TOP + (i / 2) * CARD_PITCH) - Math.round(gridCur);
    }

    private void drawGrid(GuiGraphicsExtractor c, int mx, int my) {
        c.enableScissor(gridL, gridT, gridR, gridB);
        boolean inGrid = NewUi.in(mx, my, gridL, gridT, gridR - gridL, gridB - gridT);
        if (error != null && hits.isEmpty()) {
            drawError(c, mx, my);
        } else if (hits.isEmpty() && !loading) {
            float sz = fs(17f);
            String msg = "No resource packs found";
            text(c, msg, (gridL + gridR - tw(msg, NewFont.W_MED, sz)) / 2, gridT + sl(120), TXT_GREY, NewFont.W_MED, sz);
        } else {
            int n = hits.size();
            for (int i = 0; i < n; i++) {
                int x = cardX(i);
                int y = cardY(i);
                if (y + cardH < gridT || y > gridB) {
                    continue;
                }
                drawCard(c, hits.get(i), x, y, inGrid && NewUi.in(mx, my, x, y, cardW, cardH));
            }
            if (loading) {
                int skel = n == 0 ? 6 : 2;
                for (int j = 0; j < skel; j++) {
                    int i = n + j;
                    int x = cardX(i);
                    int y = cardY(i);
                    if (y + cardH < gridT || y > gridB) {
                        continue;
                    }
                    drawSkeleton(c, x, y);
                }
            }
        }
        c.disableScissor();

        float max = maxGridScroll();
        if (max > 0f) {
            int track = gridB - gridT;
            int th = Math.max(sl(24), Math.round(track * (track / (track + max))));
            int ty = gridT + Math.round((track - th) * (gridCur / max));
            int bw = Math.max(2, sl(5));
            NewUi.rrect(c, sx(1404), ty, bw, th, bw / 2, NewUi.rgb(88, 89, 92), NewUi.rgb(80, 81, 84));
        }
    }

    private int[] retryRect() {
        int w = sl(180);
        int h = sl(40);
        return new int[]{(gridL + gridR - w) / 2, gridT + sl(150), w, h};
    }

    private void drawError(GuiGraphicsExtractor c, int mx, int my) {
        float sz = fs(17f);
        int ic = Math.max(10, sl(34));
        icon(c, I_ALERT, (gridL + gridR - ic) / 2, gridT + sl(40), ic, ERR);
        String msg = "Couldn't load resource packs";
        text(c, msg, (gridL + gridR - tw(msg, NewFont.W_BOLD, sz)) / 2, gridT + sl(86), TXT, NewFont.W_BOLD, sz);
        float ssz = fs(14.5f);
        String why = fit(error == null ? "" : error, NewFont.W_MED, ssz, gridR - gridL - sl(60));
        text(c, why, (gridL + gridR - tw(why, NewFont.W_MED, ssz)) / 2, gridT + sl(112), TXT_GREY, NewFont.W_MED, ssz);
        drawRed(c, retryRect(), "Retry", "retry", mx, my);
    }

    private void drawSkeleton(GuiGraphicsExtractor c, int x, int y) {
        int cr = Math.max(3, sl(10));
        NewUi.rrect(c, x, y, cardW, cardH, cr, CARD_T, CARD_B);
        double t = System.nanoTime() / 1_000_000_000.0;
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 3.0);
        int bar = mix(NewUi.rgb(50, 52, 55), NewUi.rgb(62, 64, 68), pulse);
        int blk = mix(NewUi.rgb(70, 72, 75), NewUi.rgb(84, 86, 90), pulse);
        int is = sl(72);
        NewUi.rrect(c, x + sl(16), y + sl(75), is, is, Math.max(3, sl(10)), blk, blk);
        int br = Math.max(2, sl(4));
        int cy1 = y + sl(187);
        NewUi.rrect(c, x + sl(16), cy1 - sl(8), sl(160), sl(16), br, bar, bar);
        NewUi.rrect(c, x + cardW - sl(16) - sl(50), cy1 - sl(8), sl(50), sl(16), br, bar, bar);
        NewUi.rrect(c, x + cardW - sl(16) - sl(50) - sl(10) - sl(24), cy1 - sl(12), sl(24), sl(24), br, bar, bar);
        NewUi.rrect(c, x + sl(16), y + sl(214), cardW - sl(32), sl(14), br, bar, bar);
        NewUi.rrect(c, x + sl(16), y + sl(238), sl(172), sl(14), br, bar, bar);
        NewButtonArt.edge(c, x, y, cardW, cardH, cr, 0.20f);
    }

    private void drawCard(GuiGraphicsExtractor c, FhasixPackApi.Hit hit, int x, int y, boolean over) {
        int cr = Math.max(3, sl(10));
        float hv = ease("card:" + hit.id, over ? 1f : 0f, 14f);
        NewUi.rrect(c, x, y, cardW, cardH, cr, CARD_T, CARD_B);

        // banner: colour placeholder, then the picture fades in over it
        int base = hit.color != 0 ? hit.color : NewUi.rgb(38, 42, 50);
        int br = (int) (((base >> 16) & 255) * 0.42f);
        int bg = (int) (((base >> 8) & 255) * 0.42f);
        int bb = (int) ((base & 255) * 0.42f);
        int top = NewUi.rgb(br, bg, bb);
        int bot = NewUi.rgb((int) (br * 0.7f), (int) (bg * 0.7f), (int) (bb * 0.7f));
        roundTopFill(c, x, y, cardW, bannerH, cr, top, bot);
        String bannerUrl = hit.bannerUrl;
        if (bannerUrl != null) {
            requestImage(bannerUrl, 640);
            Img bi = imgs.get(bannerUrl);
            if (bi != null && bi.state == 2 && bi.id != null) {
                bi.fade = Math.min(1f, bi.fade + animDt * 4f);
                int a = Math.round(255f * bi.fade);
                int tint = (a << 24) | 0xFFFFFF;
                roundTex(c, bi.id, x, y, cardW, bannerH, cr, true, bi.w, bi.h, tint);
            }
        }

        // pack icon (rounded, light 2px edge) at the bottom-left of the banner
        int ix = x + sl(16);
        int iy = y + sl(75);
        int is = sl(72);
        int ir = Math.max(3, sl(10));
        int ew = Math.max(1, sl(2));
        int edgeCol = NewUi.withAlpha(0xFFFFFFFF, 0.42f);
        NewUi.rrect(c, ix, iy, is, is, ir, edgeCol, edgeCol);
        NewUi.rrect(c, ix + ew, iy + ew, is - ew * 2, is - ew * 2, Math.max(2, ir - ew), NewUi.rgb(26, 28, 32), NewUi.rgb(26, 28, 32));
        if (hit.iconUrl != null) {
            requestImage(hit.iconUrl, 160);
            Img ii = imgs.get(hit.iconUrl);
            if (ii != null && ii.state == 2 && ii.id != null) {
                ii.fade = Math.min(1f, ii.fade + animDt * 5f);
                int a = Math.round(255f * ii.fade);
                roundTex(c, ii.id, ix + ew, iy + ew, is - ew * 2, is - ew * 2, Math.max(2, ir - ew), false, ii.w, ii.h, (a << 24) | 0xFFFFFF);
            }
        }

        // status badge (top right of the banner)
        Inst inst = installs.get(hit.id);
        boolean isInstalled = Boolean.TRUE.equals(installed.get(hit.id));
        boolean downloading = inst != null && inst.state == 1;
        boolean hasUpdate = updates.contains(hit.id) && isInstalled;
        int bs = Math.max(10, sl(30));
        int bcx = x + cardW - sl(27) - bs / 2;
        int bcy = y + sl(27);
        if (downloading) {
            NewUi.rrect(c, bcx - bs / 2, bcy - bs / 2, bs, bs, bs / 2, NewUi.withAlpha(0xFF0A0B0D, 0.7f), NewUi.withAlpha(0xFF0A0B0D, 0.7f));
            icon(c, I_DOWNLOAD, bcx - bs * 3 / 10, bcy - bs * 3 / 10, bs * 3 / 5, 0xFFFFFFFF);
        } else if (hasUpdate) {
            NewUi.rrect(c, bcx - bs / 2, bcy - bs / 2, bs, bs, bs / 2, AMBER, AMBER);
            icon(c, I_REFRESH, bcx - bs * 3 / 10, bcy - bs * 3 / 10, bs * 3 / 5, 0xFFFFFFFF);
        } else if (isInstalled) {
            NewUi.rrect(c, bcx - bs / 2, bcy - bs / 2, bs, bs, bs / 2, GREEN, GREEN);
            icon(c, I_CHECK, bcx - bs * 3 / 10, bcy - bs * 3 / 10, bs * 3 / 5, 0xFFFFFFFF);
        }
        if (hv > 0.05f && !downloading && (!isInstalled || hasUpdate)) {
            String label = hasUpdate ? "Update" : "Download";
            float psz = fs(14f);
            int pw = sl(24) + tw(label, NewFont.W_BOLD, psz) + sl(30);
            int ph = Math.max(12, sl(32));
            int px = x + cardW - sl(14) - pw;
            int py = y + sl(14);
            int pc = NewUi.withAlpha(hasUpdate ? AMBER : FhasixTheme.red(), 0.95f * hv);
            if (hasUpdate) {
                // the round update badge is replaced by the pill while hovered
                NewUi.rrect(c, bcx - bs / 2 - 1, bcy - bs / 2 - 1, bs + 2, bs + 2, (bs + 2) / 2, mix(top, bot, 0.3f), mix(top, bot, 0.3f));
            }
            NewUi.rrect(c, px, py, pw, ph, ph / 2, pc, pc);
            int di = Math.max(8, sl(15));
            icon(c, I_DOWNLOAD, px + sl(14), py + (ph - di) / 2, di, NewUi.withAlpha(0xFFFFFFFF, hv));
            text(c, label, px + sl(14) + di + sl(6), NewFont.centerYUi(py, ph, psz), NewUi.withAlpha(0xFFFFFFFF, hv), NewFont.W_BOLD, psz);
        }

        int padL = x + sl(16);
        int innerR = x + cardW - sl(16);

        // title row: name, "by author", download count
        float tsz = fs(21f);
        float bsz = fs(15f);
        float dsz = fs(16f);
        String count = String.valueOf(hit.downloads);
        int cw = tw(count, NewFont.W_MED, dsz);
        int dlS = Math.max(8, sl(18));
        int countX = innerR - cw;
        int dlX = countX - sl(10) - dlS;
        int avail = dlX - sl(14) - padL;
        String byText = hit.author.isEmpty() ? "" : " by " + hit.author;
        int byMax = Math.round(avail * 0.45f);
        byText = fit(byText, NewFont.W_REG, bsz, byMax);
        int byW = byText.isEmpty() ? 0 : tw(byText, NewFont.W_REG, bsz);
        String title = fit(hit.title, NewFont.W_BOLD, tsz, Math.max(sl(60), avail - byW));
        int rowCy = y + sl(187);
        int tY = NewFont.centerYUi(rowCy - sl(12), sl(24), tsz);
        text(c, title, padL, tY, TXT, NewFont.W_BOLD, tsz);
        int titleW = tw(title, NewFont.W_BOLD, tsz);
        if (!byText.isEmpty()) {
            text(c, byText, padL + titleW, NewFont.centerYUi(rowCy - sl(12), sl(24), bsz) + 1, TXT_DIM, NewFont.W_REG, bsz);
        }
        icon(c, I_DOWNLOAD, dlX, rowCy - dlS / 2, dlS, TXT_GREY);
        text(c, count, countX, NewFont.centerYUi(rowCy - sl(12), sl(24), dsz), TXT_GREY, NewFont.W_MED, dsz);

        // description
        float xsz = fs(15f);
        String desc = fit(hit.description, NewFont.W_MED, xsz, innerR - padL);
        text(c, desc, padL, NewFont.centerYUi(y + sl(221) - sl(10), sl(20), xsz), 0xFF838790, NewFont.W_MED, xsz);

        // bottom row: tags / progress / error
        int rowY = y + sl(246);
        float gsz = fs(14.5f);
        if (downloading) {
            int pct = Math.round(inst.progress * 100f);
            text(c, "Downloading... " + pct + "%", padL, NewFont.centerYUi(rowY - sl(12), sl(20), gsz), TXT_GREY, NewFont.W_MED, gsz);
            int tw0 = innerR - padL;
            int bh = Math.max(2, sl(5));
            int by = y + cardH - sl(11);
            NewUi.rrect(c, padL, by, tw0, bh, bh / 2, NewUi.rgb(32, 35, 40), NewUi.rgb(32, 35, 40));
            int fw = Math.max(bh, Math.round(tw0 * inst.progress));
            NewUi.rrect(c, padL, by, fw, bh, bh / 2, FhasixTheme.red(), FhasixTheme.red());
        } else if (inst != null && inst.state == 3) {
            String msg = fit("Failed: " + inst.error + " - click to retry", NewFont.W_MED, gsz, innerR - padL);
            text(c, msg, padL, NewFont.centerYUi(rowY - sl(12), sl(24), gsz), ERR, NewFont.W_MED, gsz);
        } else {
            c.enableScissor(padL, y + cardH - sl(34), innerR, y + cardH);
            int tx = padL;
            int is2 = Math.max(8, sl(17));
            for (String tag : hit.tags) {
                if (tx > innerR) {
                    break;
                }
                Integer ic = TAG_ICON.get(tag);
                if (ic != null) {
                    icon(c, ic, tx, rowY - is2 / 2, is2, TXT_GREY);
                    tx += is2 + sl(6);
                }
                text(c, tag, tx, NewFont.centerYUi(rowY - sl(12), sl(24), gsz), TXT_GREY, NewFont.W_MED, gsz);
                tx += tw(tag, NewFont.W_MED, gsz) + sl(14);
            }
            c.disableScissor();
        }

        NewButtonArt.edge(c, x, y, cardW, cardH, cr, 0.20f + 0.08f * hv);
    }

    // ------------------------------------------------------------------ hit testing + input
    private Hit hitAt(double mx, double my) {
        if (NewUi.in(mx, my, leftL, leftT, leftR - leftL, leftB - leftT)) {
            int boxX = sx(BOX_X);
            int boxW = sx(BOX_X + BOX_W) - boxX;
            for (int s = 0; s < secs.length; s++) {
                int hy = leftY(secHeadY[s]);
                if (NewUi.in(mx, my, leftL, hy - sl(14), leftR - leftL, sl(28))) {
                    return new Hit(HIT_HEAD, s, 0);
                }
                if (!secs[s].open) {
                    continue;
                }
                for (int i = 0; i < secs[s].labels.length; i++) {
                    int ry = leftY(secBoxY[s] + 6 + i * ROW_H);
                    int rh = sy(ROW_H) - sy(0);
                    if (NewUi.in(mx, my, boxX + 1, ry, boxW - 2, rh)) {
                        return new Hit(HIT_ROW, s, i);
                    }
                }
            }
            return new Hit(HIT_NONE, 0, 0);
        }
        if (NewUi.in(mx, my, gridL, gridT, gridR - gridL, gridB - gridT)) {
            if (error != null && hits.isEmpty()) {
                int[] r = retryRect();
                return NewUi.in(mx, my, r[0], r[1], r[2], r[3]) ? new Hit(HIT_RETRY, 0, 0) : new Hit(HIT_NONE, 0, 0);
            }
            for (int i = 0; i < hits.size(); i++) {
                if (NewUi.in(mx, my, cardX(i), cardY(i), cardW, cardH)) {
                    return new Hit(HIT_CARD, i, 0);
                }
            }
        }
        return new Hit(HIT_NONE, 0, 0);
    }

    private void ripple(String key, double x, double y) {
        ripples.put(key, new long[]{System.nanoTime(), (long) x, (long) y});
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) {
            return true;
        }
        if (click.buttonInfo().button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return true;
        }
        double mx = click.x();
        double my = click.y();
        if (sortOpen) {
            int[] r = sortListRect();
            if (NewUi.in(mx, my, r[0], r[1], r[2], r[3])) {
                int rh = Math.max(14, sl(36));
                int pad = sl(6);
                int i = (int) ((my - r[1] - pad) / rh);
                if (i >= 0 && i < SORT_LABELS.length) {
                    NewUi.click(minecraft);
                    if (i != sortIdx) {
                        sortIdx = i;
                        queryPending = false;
                        startSearch(false);
                    }
                }
            }
            sortOpen = false;
            return true;
        }
        if (searchField != null && !NewUi.in(mx, my, searchR[0], searchR[1], searchR[2], searchR[3])) {
            this.setFocused(null);
        }
        if (NewUi.in(mx, my, closeR[0], closeR[1], closeR[2], closeR[3])) {
            NewUi.click(minecraft);
            closeScreen();
            return true;
        }
        if (searchField != null && !searchField.getValue().isEmpty()) {
            int[] xr = clearXRect();
            if (NewUi.in(mx, my, xr[0], xr[1], xr[2], xr[3])) {
                NewUi.click(minecraft);
                searchField.setValue("");
                lastQuery = "";
                queryPending = false;
                startSearch(false);
                return true;
            }
        }
        if (NewUi.in(mx, my, sortR[0], sortR[1], sortR[2], sortR[3])) {
            NewUi.click(minecraft);
            sortOpen = true;
            return true;
        }
        if (NewUi.in(mx, my, updR[0], updR[1], updR[2], updR[3])) {
            NewUi.click(minecraft);
            ripple("upd", mx, my);
            checkUpdates();
            return true;
        }
        if (NewUi.in(mx, my, clrR[0], clrR[1], clrR[2], clrR[3])) {
            NewUi.click(minecraft);
            ripple("clr", mx, my);
            if (!selected.isEmpty()) {
                selected.clear();
                queryPending = false;
                startSearch(false);
            }
            return true;
        }
        boolean inLeft = NewUi.in(mx, my, leftL, leftT, leftR - leftL, leftB - leftT);
        boolean inGrid = NewUi.in(mx, my, gridL, gridT, gridR - gridL, gridB - gridT);
        if (inLeft || inGrid) {
            pressArea = inLeft ? 1 : 2;
            dragScrolling = false;
            travel = 0.0;
            pressedHit = hitAt(mx, my);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        if (pressArea != 0) {
            travel += Math.abs(offsetX) + Math.abs(offsetY);
            if (!dragScrolling && travel > 5.0) {
                dragScrolling = true;
            }
            if (dragScrolling) {
                if (pressArea == 1) {
                    leftTarget -= (float) offsetY;
                } else {
                    gridTarget -= (float) offsetY;
                }
            }
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (pressArea != 0) {
            pressArea = 0;
            Hit pressed = pressedHit;
            pressedHit = null;
            boolean wasDrag = dragScrolling;
            dragScrolling = false;
            if (!wasDrag && pressed != null && pressed.kind != HIT_NONE) {
                Hit now = hitAt(click.x(), click.y());
                if (now.kind == pressed.kind && now.a == pressed.a && now.b == pressed.b) {
                    activate(pressed, click.x(), click.y());
                }
            }
            return true;
        }
        return super.mouseReleased(click);
    }

    private void activate(Hit h, double x, double y) {
        if (h.kind == HIT_HEAD) {
            NewUi.click(minecraft);
            secs[h.a].open = !secs[h.a].open;
            computeLeft();
        } else if (h.kind == HIT_ROW) {
            NewUi.click(minecraft);
            String key = secs[h.a].keys[h.b];
            if (!selected.remove(key)) {
                selected.add(key);
            }
            queryPending = false;
            startSearch(false);
        } else if (h.kind == HIT_RETRY) {
            NewUi.click(minecraft);
            ripple("retry", x, y);
            startSearch(false);
        } else if (h.kind == HIT_CARD && h.a >= 0 && h.a < hits.size()) {
            FhasixPackApi.Hit hit = hits.get(h.a);
            Inst inst = installs.get(hit.id);
            boolean isInstalled = Boolean.TRUE.equals(installed.get(hit.id));
            boolean hasUpdate = updates.contains(hit.id);
            if (inst != null && inst.state == 1) {
                return;
            }
            if (!isInstalled || hasUpdate || (inst != null && inst.state == 3)) {
                NewUi.click(minecraft);
                startInstall(hit);
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (NewUi.in(mouseX, mouseY, leftL, leftT, leftR - leftL, leftB - leftT)) {
            leftTarget -= (float) verticalAmount * 28f;
        } else {
            gridTarget -= (float) verticalAmount * 40f;
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (keyEvent.key() == InputConstants.KEY_ESCAPE) {
            if (sortOpen) {
                sortOpen = false;
                return true;
            }
            closeScreen();
            return true;
        }
        return super.keyPressed(keyEvent);
    }
}
