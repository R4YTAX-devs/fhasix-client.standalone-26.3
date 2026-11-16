package com.fhasix.uitheme;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * NEW (v31 Feather-style redesign). The Mod Menu grid.
 *
 * Layout: every rectangle is measured from the reference (1920x1080) and scaled by ONE factor k, so the
 * menu keeps the reference's proportions on any window size / GUI scale. Design space: x = 0 is the left
 * edge of the menu area, y = 0 is the top of the header (logo / title / date / account / buttons).
 *
 * Look: dark panel, tile tabs with a faint corner-lit edge, cards with the same edge as the title-screen
 * buttons, diagonal slate hover, green Enabled pill with a click ripple, smooth (anti-aliased) corners and
 * smooth icons. Fonts: Product Sans (regular / medium / bold files in assets/fhasixuitheme/font).
 *
 * Opens from the title-screen grid icon (parent = TitleScreen) and from the in-world launcher
 * (parent = null). Working: FPS/CPS pills, hearts, tabs, search, favourites filter, scrolling. (Performance / General / Mod Profiles are NewSettingsScreen pages.)
 * Decorative: header buttons, most tab icons, grid-view button, placeholder mods' pills.
 */
public final class NewModMenuScreen extends Screen {

    private static final int DW = 1394;
    private static final int DH = 958;
    /** The whole header + menu is fitted into this many design pixels (width x height). */
    private static final float FIT_W = 1700f;
    private static final float FIT_H = 985f;
    private static final int COLS = 4;
    private static final String[] TABS = {"All", "HUD", "Hypixel", "PvP"};
    private static final String[] FX = {"fx_gear", "fx_comment", "fx_monitor", "fx_cape", "fx_desktop", "fx_chats", "fx_profile"};
    private static final String[] FX_TIP = {"Performance", "Chat", "Display", "Cosmetics", "General", "Social", "Mod Profiles"};
    private static final Identifier[] FX_TEX = new Identifier[FX.length];
    private static final int FX_SIZE = 96;
    private static final Identifier FX_MOON = fx("fx_moon");
    private static final Identifier FX_GEARS = fx("fx_gears");
    private static final Identifier FX_CHATS = fx("fx_chats");
    private static final Identifier FX_MOVE = fx("fx_move");
    private static final Identifier FX_CLOSE = fx("fx_close");
    private static final Identifier MARK = Identifier.fromNamespaceAndPath("fhasixuitheme", "textures/gui/title/new_fhasix_mark.png");
    private static final int MARK_TEX = 256;
    private static final Identifier TITLE_BG = Identifier.fromNamespaceAndPath("fhasixuitheme", "textures/gui/title/background.png");
    private static final int TITLE_BG_W = 1584;
    private static final int TITLE_BG_H = 672;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("yyyy/MM/dd  hh:mm a", Locale.ENGLISH);

    private static Identifier fx(String name) {
        return Identifier.fromNamespaceAndPath("fhasixuitheme", "textures/gui/fx/" + name + ".png");
    }

    static {
        for (int i = 0; i < FX.length; i++) {
            FX_TEX[i] = fx(FX[i]);
        }
    }

    // colours (ARGB) - sampled from the reference
    private static final int PANEL_T = NewUi.rgb(15, 17, 20);
    private static final int PANEL_B = NewUi.rgb(14, 16, 19);
    private static final int PANEL_BG = NewUi.rgb(15, 17, 20);
    private static final int PANEL_EDGE = NewUi.rgb(23, 26, 31);
    private static final int TILE_EDGE = NewUi.rgb(29, 32, 38);
    private static final int TILE_T = NewUi.rgb(15, 17, 20);
    private static final int TILE_B = NewUi.rgb(13, 15, 18);
    private static final int BTN = NewUi.rgb(25, 27, 30);
    private static final int BTN_HOV = NewUi.rgb(42, 46, 53);
    private static final int CHIP = NewUi.rgb(26, 29, 32);
    private static final int CARD_BORDER = NewUi.rgb(24, 27, 32);
    private static final int CARD_T = NewUi.rgb(15, 17, 20);
    private static final int CARD_B = NewUi.rgb(14, 16, 19);
    private static final int HOV_TL = NewUi.rgb(60, 70, 82);
    private static final int HOV_BR = NewUi.rgb(28, 32, 38);
    private static final int HOV_MID = NewUi.rgb(44, 51, 60);
    private static final int ON = NewUi.rgb(25, 69, 27);
    private static final int ON_H = NewUi.rgb(32, 86, 35);
    private static final int OFF_H = NewUi.rgb(41, 45, 51);
    private static final int TXT = 0xFFFFFFFF;
    private static final int TXT_DIM = 0xFF7A7B7F;
    private static final int TXT_TAB = 0xFF9A9DA3;
    private static final int TXT_ON = 0xFF2E9A2C;

    private static final int HIT_NONE = 0;
    private static final int HIT_HEART = 1;
    private static final int HIT_PILL = 2;
    private static final int HIT_GEAR = 3;
    private static final int HIT_CARD = 4;

    private static final class Hit {
        final int kind;
        final NewModSettings.ModDef mod;
        final int arg;

        Hit(int kind, NewModSettings.ModDef mod) {
            this(kind, mod, 0);
        }

        Hit(int kind, NewModSettings.ModDef mod, int arg) {
            this.kind = kind;
            this.mod = mod;
            this.arg = arg;
        }
    }

    private static final class Geo {
        int x, y, w, h;
        int hx, hy, hs;
        int gx, gy, gs;
        int px, py, pw, ph;
        int ix, iy, is;
        int nx, ny;
    }

    private final Screen parent;
    private EditBox searchField;
    private int tab = 0;
    private boolean favOnly = false;

    /** Every eased 0..1 animation value of the menu (hover glows, Enabled pills, tooltips), by key. */
    private final Map<String, Float> anim = new HashMap<>();
    /** Running click ripples: mod id -> {start nanos, x, y}. */
    private final Map<String, long[]> ripples = new HashMap<>();
    private long animLast = System.nanoTime();
    private float animDt = 0f;
    private String clockText = "";
    private long clockAt = 0L;

    private float k;
    private int ox;
    private int oy;
    private int[] modTab;
    private final int[][] topBtn = new int[FX.length][];
    private int[] closeBtn;
    private int[] panel;
    private int[] logoR;
    private int[] datePill;
    private int[] accountPill;
    private final int[][] hdrBtn = new int[3][];
    private final int[][] tabRect = new int[TABS.length][];
    private int[] searchRect;
    private int[] favBtn;
    private int[] gridBtn;

    // click ripple + a short delay when this menu swaps to another view (a settings page, a mod's settings)
    private long navStart;
    private int[] navRect;
    private int navRad;
    private float navX;
    private float navY;
    private Runnable navAction;
    private static final float NAV_RIPPLE_S = 0.32f;
    private static final float NAV_DELAY_S = 0.20f;

    private void navRipple(int[] rect, int rad, double x, double y, Runnable action) {
        if (navAction != null) {
            return;
        }
        navRect = rect.clone();
        navRad = rad;
        navX = (float) x;
        navY = (float) y;
        navStart = System.nanoTime();
        navAction = action;
    }

    private void drawNavRipple(GuiGraphicsExtractor c) {
        if (navAction == null) {
            return;
        }
        float t = (System.nanoTime() - navStart) / 1_000_000_000f;
        float p = Math.min(1f, t / NAV_RIPPLE_S);
        float ease = 1f - (1f - p) * (1f - p) * (1f - p);
        float lx = navX - navRect[0];
        float ly = navY - navRect[1];
        float far = (float) Math.sqrt(Math.max(lx, navRect[2] - lx) * Math.max(lx, navRect[2] - lx)
                + Math.max(ly, navRect[3] - ly) * Math.max(ly, navRect[3] - ly));
        NewUi.ripple(c, navRect[0], navRect[1], navRect[2], navRect[3], navRad, navX, navY, far * ease,
                NewUi.withAlpha(0xFFFFFFFF, 0.20f - 0.08f * p));
        if (t >= NAV_DELAY_S) {
            Runnable a = navAction;
            navAction = null;
            a.run();
        }
    }

    /** Rectangle of a mod card's gear button (for the click ripple). */
    private int[] gearRect(NewModSettings.ModDef mod) {
        List<NewModSettings.ModDef> list = visible();
        layout(list.size());
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == mod && geos[i] != null) {
                return new int[]{geos[i].gx, geos[i].gy, geos[i].gs, geos[i].gs};
            }
        }
        return null;
    }
    private int clipTop;
    private int clipBottom;
    private int clipL;
    private int clipR;
    private int cardW;
    private int cardH;
    private int pitchX;
    private int pitchY;
    private int firstX;
    private int firstY;
    private final Geo[] geos = new Geo[NewModSettings.MODS.size()];

    private float scrollCur = 0f;
    private float scrollTarget = 0f;
    private long lastNanos = 0L;
    private boolean pressActive = false;
    private boolean dragScrolling = false;
    private double travel = 0.0;
    private Hit pressedHit = null;

    /** parent == null means "opened in a world": nothing to draw behind, closing returns to the game. */
    public NewModMenuScreen(Screen parent) {
        super(Component.literal("MOD MENU"));
        this.parent = parent;
    }

    /** Font size for a design-size value: the whole menu is scaled by k, so the text is too. */
    private float fs(float design) {
        return design * k;
    }

    private int cyUi(int centreY, float size) {
        return NewFont.centerYUi(centreY - 4, 8, size);
    }

    private String[] tabs() {
        return TABS;
    }

    // ---------------------------------------------------------------- layout

    private int sx(int v) {
        return ox + Math.round(v * k);
    }

    private int sy(int v) {
        return oy + Math.round(v * k);
    }

    private int sl(int v) {
        return Math.max(1, Math.round(v * k));
    }

    private int[] rect(int x, int y, int w, int h) {
        int x0 = sx(x);
        int y0 = sy(y);
        return new int[]{x0, y0, sx(x + w) - x0, sy(y + h) - y0};
    }

    @Override
    protected void init() {
        k = Math.min(width / FIT_W, height / FIT_H);
        ox = (width - Math.round(DW * k)) / 2;
        oy = (height - Math.round(DH * k)) / 2;

        // header
        logoR = rect(-118, 8, 62, 62);
        datePill = rect(120, 25, 257, 40);
        accountPill = rect(1134, 8, 170, 63);
        for (int i = 0; i < hdrBtn.length; i++) {
            hdrBtn[i] = rect(1315 + 77 * i, 8, 64, 63);
        }

        // tab row
        modTab = rect(47, 117, 178, 64);
        for (int i = 0; i < FX.length; i++) {
            topBtn[i] = rect(237 + 88 * i, 117, 76, 64);
        }
        closeBtn = rect(1305, 125, 46, 47);

        // panel + toolbar
        panel = rect(47, 197, 1305, 725);
        for (int i = 0; i < TABS.length; i++) {
            tabRect[i] = rect(70 + 120 * i, 220, 108, 39);
        }
        searchRect = rect(913, 220, 310, 39);
        favBtn = rect(1235, 220, 40, 39);
        gridBtn = rect(1288, 220, 40, 39);

        clipTop = sy(267);
        clipBottom = sy(916);
        clipL = sx(47) + 1;
        clipR = sx(1352) - 1;
        cardW = sx(72 + 304) - sx(72);
        cardH = Math.max(40, Math.round(208 * k));
        pitchX = Math.round(317 * k);
        pitchY = Math.round(222 * k);
        firstX = sx(72);
        firstY = sy(275);

        int padX = sl(16);
        searchField = new EditBox(font, searchRect[0] + padX,
                searchRect[1] + (searchRect[3] - font.lineHeight) / 2,
                Math.max(10, searchRect[2] - padX * 2), font.lineHeight + 2, Component.literal("Search"));
        searchField.setMaxLength(32);
        searchField.setBordered(false);
        // The field only handles typing/focus; its text and caret are drawn by this screen (smooth Product Sans).
        this.addRenderableWidget(searchField);
    }

    /** Intentionally empty: the background is drawn from render() so it happens exactly once, in order. */

    /**
     * Vanilla background (panorama / blur / darkening) drawn once. The game allows only ONE blur per
     * frame and throws "Can only blur once per frame" if a launcher or the loading overlay has already
     * drawn one - that is caught here so it can never crash the game.
     */
    private void safeBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        try {
            super.extractBackground(context, mouseX, mouseY, delta);
        } catch (IllegalStateException alreadyBlurred) {
            context.fill(0, 0, width, height, 0xB0000000);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
    }

    /** The screen behind this menu (null in a world). Used by the options screen. */
    Screen backdrop() {
        return parent;
    }

    /** The title-screen background (same one the title screen draws): the moving panorama over a w x h screen. */
    static void drawTitleBackdrop(GuiGraphicsExtractor c, int w, int h) {
        FhasixPanorama.draw(c, w, h); // UPDATE 42: the moving panorama (the pictures are already dark - only a light dim)
        c.fill(0, 0, w, h, 0x1F000000);
    }

    private void closeMenu() {
        minecraft.gui.setScreen(parent);
    }

    private void layout(int count) {
        int scroll = Math.round(scrollCur);
        for (int i = 0; i < count; i++) {
            Geo g = geos[i];
            if (g == null) {
                g = new Geo();
                geos[i] = g;
            }
            g.x = firstX + (i % COLS) * pitchX;
            g.y = firstY + (i / COLS) * pitchY - scroll;
            g.w = cardW;
            g.h = cardH;
            g.hs = Math.max(10, sl(31));
            g.hx = g.x + g.w - sl(27) - g.hs;
            g.hy = g.y + sl(23);
            g.gs = Math.max(11, sl(40));
            g.gx = g.x + sl(24);
            g.gy = g.y + g.h - sl(23) - g.gs;
            g.px = g.x + sl(72);
            g.py = g.gy;
            g.pw = g.w - sl(72) - sl(27);
            g.ph = g.gs;
            g.is = Math.max(14, sl(64));
            g.ix = g.x + (g.w - g.is) / 2;
            g.iy = g.y + sl(97) - g.is / 2;
            g.nx = g.x + sl(24);
            g.ny = g.y + sl(34) - font.lineHeight / 2;
        }
    }

    private float maxScroll(int count) {
        int rows = (count + COLS - 1) / COLS;
        int content = (firstY - clipTop) + rows * pitchY - (pitchY - cardH) + sl(24);
        return Math.max(0, content - (clipBottom - clipTop));
    }

    // ---------------------------------------------------------------- data

    private List<NewModSettings.ModDef> visible() {
        String q = searchField == null ? "" : searchField.getValue().trim().toLowerCase(Locale.ROOT);
        List<NewModSettings.ModDef> out = new ArrayList<>();
        for (NewModSettings.ModDef m : NewModSettings.MODS) {
            if (tab == 1 && (m.tags & NewModSettings.TAG_HUD) == 0) {
                continue;
            }
            if (tab == 2 && (m.tags & NewModSettings.TAG_HYPIXEL) == 0) {
                continue;
            }
            if (tab == 3 && (m.tags & NewModSettings.TAG_PVP) == 0) {
                continue;
            }
            if (favOnly && !NewModSettings.isFavorite(minecraft, m.id)) {
                continue;
            }
            if (!q.isEmpty() && !m.name.toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            out.add(m);
        }
        return out;
    }

    private boolean inClip(double mx, double my) {
        return NewUi.in(mx, my, clipL, clipTop, clipR - clipL, clipBottom - clipTop);
    }

    private Hit hitAt(double mx, double my) {
        if (!inClip(mx, my)) {
            return new Hit(HIT_NONE, null);
        }
        List<NewModSettings.ModDef> list = visible();
        layout(list.size());
        for (int i = 0; i < list.size(); i++) {
            Geo g = geos[i];
            if (!NewUi.in(mx, my, g.x, g.y, g.w, g.h)) {
                continue;
            }
            NewModSettings.ModDef m = list.get(i);
            if (NewUi.in(mx, my, g.hx, g.hy, g.hs, g.hs)) {
                return new Hit(HIT_HEART, m);
            }
            if (NewUi.in(mx, my, g.gx, g.gy, g.gs, g.gs)) {
                return new Hit(HIT_GEAR, m);
            }
            if (NewUi.in(mx, my, g.px, g.py, g.pw, g.ph)) {
                return new Hit(HIT_PILL, m);
            }
            return new Hit(HIT_CARD, m);
        }
        return new Hit(HIT_NONE, null);
    }

    // ---------------------------------------------------------------- animation helpers

    /** Moves a stored 0..1 value towards target (frame-rate independent); a first-seen value snaps (no animation on open). */
    private float ease(String key, float target, float speed) {
        if (NewPerf.animationsOff()) { // HUD Optimizer: no UI animations
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

    private static float smooth(float t) {
        return t * t * (3f - 2f * t);
    }

    private static int mix(int a, int b, float t) {
        return NewUi.lerp(a, b, t);
    }

    /** Greys a white icon by laying the colour that is behind it over it (no box shows, the icon just turns grey). */
    private static void dim(GuiGraphicsExtractor c, int x, int y, int size, int bg, float a) {
        if (a > 0.02f) {
            c.fill(x, y, x + size, y + size, NewUi.withAlpha(bg | 0xFF000000, a));
        }
    }

    // ---------------------------------------------------------------- render

    @Override
    public void extractRenderState(GuiGraphicsExtractor c, int mx, int my, float delta) {
        long animNow = System.nanoTime();
        animDt = Math.min(0.1f, (animNow - animLast) / 1_000_000_000f);
        animLast = animNow;
        if (parent != null) {
            drawTitleBackdrop(c, width, height); // our own backdrop: the title buttons underneath must not show through
        } else {
            safeBackground(c, mx, my, delta); // in a world: vanilla blur + darkening (needs Menu Background Blur > 0)
            c.fill(0, 0, width, height, 0x40000000);
        }

        List<NewModSettings.ModDef> list = visible();
        float max = maxScroll(list.size());
        scrollTarget = Math.max(0f, Math.min(max, scrollTarget));
        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0f : Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
        lastNanos = now;
        if (pressActive && dragScrolling) {
            scrollCur = scrollTarget;
        } else {
            scrollCur += (scrollTarget - scrollCur) * (1f - (float) Math.exp(-18f * dt));
            if (Math.abs(scrollTarget - scrollCur) < 0.3f) {
                scrollCur = scrollTarget;
            }
        }

        drawTabRow(c, mx, my); // (the top header bar - date, account, chat / settings / move buttons - is removed)
        NewUi.rrect(c, panel[0], panel[1], panel[2], panel[3], Math.max(3, sl(12)), PANEL_T, PANEL_B);
        NewUi.ring(c, panel[0], panel[1], panel[2], panel[3], Math.max(3, sl(12)), PANEL_EDGE);
        drawToolbar(c, mx, my);

        layout(list.size());
        c.enableScissor(clipL, clipTop, clipR, clipBottom);
        boolean over = inClip(mx, my);
        for (int i = 0; i < list.size(); i++) {
            Geo g = geos[i];
            if (g.y + g.h < clipTop - 4 || g.y > clipBottom) {
                continue;
            }
            drawCard(c, list.get(i), g, mx, my, over);
        }
        c.disableScissor();

        if (max > 0f) {
            int bw = Math.max(2, sl(6));
            int bx = sx(1342);
            int th = Math.max(12, Math.round((clipBottom - clipTop) * ((clipBottom - clipTop) / (clipBottom - clipTop + max))));
            int ty = clipTop + sl(6) + Math.round((clipBottom - clipTop - sl(12) - th) * (scrollCur / max));
            NewUi.rrect(c, bx, ty, bw, th, bw / 2, NewUi.rgb(88, 89, 92), NewUi.rgb(80, 81, 84));
        }

        String ver = "Fhasix Client";
        float vsz = fs(14f);
        NewFont.drawUi(c, font, ver, panel[0] + (panel[2] - NewFont.widthUi(font, ver, NewFont.W_MED, vsz)) / 2,
                panel[1] + panel[3] + sl(14), NewUi.rgb(96, 98, 104), NewFont.W_MED, vsz);

        drawNavRipple(c);
        drawTips(c);
        // the search field's own drawing is skipped on purpose: drawSearch() paints its text and caret
    }

    // ---------------------------------------------------------------- header

    /** A dark tile with the faint corner-lit edge (tabs and header buttons). hv = hover 0..1. */
    private void tile(GuiGraphicsExtractor c, int[] r, float hv, int rad) {
        NewUi.rrect(c, r[0], r[1], r[2], r[3], rad, mix(TILE_T, HOV_TL, hv), mix(TILE_B, HOV_BR, hv));
        NewButtonArt.edge(c, r[0], r[1], r[2], r[3], rad, 0.20f + 0.08f * hv); // same edge as Singleplayer / Multiplayer
    }

    private static final String[] FACE = {
            "HHHHHHHH", "HHHHHHHH", "HSSSSSSH", "SSSSSSSS", "SWBSSBWS", "SSSNNSSS", "SSNMMNSS", "SSSSSSSS"};

    private static int faceColor(char ch) {
        switch (ch) {
            case 'H': return NewUi.rgb(58, 38, 26);
            case 'S': return NewUi.rgb(178, 128, 100);
            case 'W': return NewUi.rgb(255, 255, 255);
            case 'B': return NewUi.rgb(70, 58, 140);
            case 'N': return NewUi.rgb(150, 100, 76);
            default: return NewUi.rgb(105, 62, 50);
        }
    }

    /** A small pixel-art head for the account pill. */
    private void drawFace(GuiGraphicsExtractor c, int x, int y, int size) {
        int p = Math.max(1, size / 8);
        int x0 = x + (size - p * 8) / 2;
        int y0 = y + (size - p * 8) / 2;
        for (int row = 0; row < 8; row++) {
            int col = 0;
            while (col < 8) {
                char ch = FACE[row].charAt(col);
                int end = col + 1;
                while (end < 8 && FACE[row].charAt(end) == ch) {
                    end++;
                }
                c.fill(x0 + col * p, y0 + row * p, x0 + end * p, y0 + (row + 1) * p, faceColor(ch));
                col = end;
            }
        }
    }

    private void drawHeader(GuiGraphicsExtractor c, int mx, int my) {
        int rad = Math.max(3, sl(10));
        // logo + title
        c.blit(RenderPipelines.GUI_TEXTURED, MARK, logoR[0], logoR[1], 0, 0, logoR[2], logoR[3],
                MARK_TEX, MARK_TEX, MARK_TEX, MARK_TEX);
        String title = "MOD MENU";
        float tsz = fs(27f);
        int tx = sx(-46);
        NewFont.drawUi(c, font, title, tx, NewFont.centerYUi(sy(8), sl(63), tsz), TXT, NewFont.W_BOLD, tsz);
        int tw = NewFont.widthUi(font, title, NewFont.W_BOLD, tsz);

        // date / time pill
        long ms = System.currentTimeMillis();
        if (ms - clockAt > 500L || clockText.isEmpty()) {
            clockAt = ms;
            clockText = LocalDateTime.now().format(CLOCK);
        }
        float dsz = fs(17f);
        int ms2 = Math.max(8, Math.round(datePill[3] * 0.50f));
        int dtw = NewFont.widthUi(font, clockText, NewFont.W_BOLD, dsz);
        int dw = sl(22) + ms2 + sl(16) + dtw + sl(24);
        int dx = Math.max(datePill[0], tx + tw + sl(34));
        int[] dp = {dx, datePill[1], dw, datePill[3]};
        tile(c, dp, 0f, Math.max(3, sl(9)));
        NewUi.tex(c, FX_MOON, dp[0] + sl(22), dp[1] + (dp[3] - ms2) / 2, ms2, FX_SIZE);
        NewFont.drawUi(c, font, clockText, dp[0] + sl(22) + ms2 + sl(16), NewFont.centerYUi(dp[1], dp[3], dsz),
                TXT, NewFont.W_BOLD, dsz);

        // account pill: head + name
        float ah = ease("hdr:acc", NewUi.in(mx, my, accountPill[0], accountPill[1], accountPill[2], accountPill[3]) ? 1f : 0f, 14f);
        tile(c, accountPill, ah, rad);
        int hs = Math.max(8, Math.round(accountPill[3] * 0.52f));
        drawFace(c, accountPill[0] + sl(18), accountPill[1] + (accountPill[3] - hs) / 2, hs);
        String name = minecraft.getUser().getName();
        name = name == null ? "" : name.toUpperCase(Locale.ROOT);
        float nsz = fs(17f);
        int nx = accountPill[0] + sl(18) + hs + sl(14);
        name = NewFont.fitUi(font, name, NewFont.W_BOLD, nsz, accountPill[0] + accountPill[2] - nx - sl(14));
        NewFont.drawUi(c, font, name, nx, NewFont.centerYUi(accountPill[1], accountPill[3], nsz), TXT, NewFont.W_BOLD, nsz);

        // chat / settings / move buttons
        Identifier[] ic = {FX_CHATS, FX_GEARS, FX_MOVE};
        for (int i = 0; i < hdrBtn.length; i++) {
            int[] r = hdrBtn[i];
            float hv = ease("hdr:" + i, NewUi.in(mx, my, r[0], r[1], r[2], r[3]) ? 1f : 0f, 14f);
            tile(c, r, hv, rad);
            int s = Math.max(8, Math.round(r[3] * 0.46f));
            NewUi.tex(c, ic[i], r[0] + (r[2] - s) / 2, r[1] + (r[3] - s) / 2, s, FX_SIZE);
        }
    }

    private void drawTabRow(GuiGraphicsExtractor c, int mx, int my) {
        int rad = Math.max(3, sl(10));
        boolean hov = NewUi.in(mx, my, modTab[0], modTab[1], modTab[2], modTab[3]);
        float mh = ease("tab:mod", hov ? 1f : 0f, 14f);
        int red = mix(FhasixTheme.red(), FhasixTheme.redHover(), mh);
        NewUi.rrect(c, modTab[0], modTab[1], modTab[2], modTab[3], rad, red, red);
        String label = "MOD MENU";
        float lsz = fs(18f);
        int ico = Math.max(9, sl(24));
        int tw = NewFont.widthUi(font, label, NewFont.W_BOLD, lsz);
        boolean showIcon = modTab[2] >= ico + 4 + tw + 6;
        int total = (showIcon ? ico + 8 : 0) + tw;
        int sx0 = modTab[0] + (modTab[2] - total) / 2;
        if (showIcon) {
            NewUi.icon(c, NewUi.ICON_GRID, sx0, modTab[1] + (modTab[3] - ico) / 2, ico);
            sx0 += ico + 8;
        }
        NewFont.drawUi(c, font, label, sx0, NewFont.centerYUi(modTab[1], modTab[3], lsz), TXT, NewFont.W_BOLD, lsz);

        for (int i = 0; i < FX.length; i++) {
            int[] r = topBtn[i];
            boolean h = NewUi.in(mx, my, r[0], r[1], r[2], r[3]);
            float hv = ease("top:" + i, h ? 1f : 0f, 14f);
            tile(c, r, hv, rad);
            int s = Math.max(8, Math.round(r[3] * 0.46f));
            NewUi.tex(c, FX_TEX[i], r[0] + (r[2] - s) / 2, r[1] + (r[3] - s) / 2, s, FX_SIZE);
        }

        // close button: translucent red with a red edge
        boolean ch = NewUi.in(mx, my, closeBtn[0], closeBtn[1], closeBtn[2], closeBtn[3]);
        float cv = ease("close", ch ? 1f : 0f, 14f);
        int crad = Math.max(3, sl(9));
        int fill = NewUi.withAlpha(FhasixTheme.red(), 0.13f + 0.17f * cv);
        NewUi.rrect(c, closeBtn[0], closeBtn[1], closeBtn[2], closeBtn[3], crad, fill, fill);
        NewUi.ring(c, closeBtn[0], closeBtn[1], closeBtn[2], closeBtn[3], crad, mix(FhasixTheme.ring(), FhasixTheme.redHover(), cv));
        int xs = Math.max(8, Math.round(closeBtn[3] * 0.50f));
        NewUi.tex(c, FX_CLOSE, closeBtn[0] + (closeBtn[2] - xs) / 2, closeBtn[1] + (closeBtn[3] - xs) / 2, xs, FX_SIZE);
    }

    /** The small label that fades in above a hovered tab icon (and fades out when the pointer leaves). */
    private void drawTips(GuiGraphicsExtractor c) {
        float sz = fs(14.5f);
        for (int i = 0; i < FX.length; i++) {
            Float a = anim.get("tip:" + i);
            float ta = a == null ? 0f : a;
            if (ta < 0.08f) {
                continue;
            }
            int[] r = topBtn[i];
            String s = FX_TIP[i];
            int w = NewFont.widthUi(font, s, NewFont.W_BOLD, sz);
            NewFont.drawUi(c, font, s, r[0] + (r[2] - w) / 2, r[1] - sl(26), NewUi.withAlpha(0xFFFFFFFF, ta), NewFont.W_BOLD, sz);
        }
    }

    private void drawToolbar(GuiGraphicsExtractor c, int mx, int my) {
        for (int i = 0; i < FX.length; i++) { // tooltips are driven here so they keep fading after the pointer leaves
            int[] r = topBtn[i];
            ease("tip:" + i, NewUi.in(mx, my, r[0], r[1], r[2], r[3]) ? 1f : 0f, 11f);
        }
        String[] tabNames = tabs();
        for (int i = 0; i < tabNames.length; i++) {
            int[] r = tabRect[i];
            boolean active = i == tab;
            boolean h = NewUi.in(mx, my, r[0], r[1], r[2], r[3]);
            float hv = ease("chip:" + i, h ? 1f : 0f, 14f);
            int rad = Math.max(3, sl(8));
            if (active) {
                int rc = mix(FhasixTheme.red(), FhasixTheme.redHover(), hv);
                NewUi.rrect(c, r[0], r[1], r[2], r[3], rad, rc, rc);
            } else {
                int bc = mix(CHIP, OFF_H, hv);
                NewUi.rrect(c, r[0], r[1], r[2], r[3], rad, bc, bc);
            }
            float tsz = fs(15f);
            int wgt = active ? NewFont.W_BOLD : NewFont.W_MED;
            NewFont.drawUi(c, font, tabNames[i], r[0] + (r[2] - NewFont.widthUi(font, tabNames[i], wgt, tsz)) / 2,
                    NewFont.centerYUi(r[1], r[3], tsz), active ? TXT : mix(TXT_TAB, TXT, hv), wgt, tsz);
        }
        drawSearch(c, mx, my);

        boolean fh = NewUi.in(mx, my, favBtn[0], favBtn[1], favBtn[2], favBtn[3]);
        int fc = mix(BTN, BTN_HOV, ease("fav", fh ? 1f : 0f, 14f));
        NewUi.rrect(c, favBtn[0], favBtn[1], favBtn[2], favBtn[3], Math.max(3, sl(8)), fc, fc);
        int hs = Math.max(8, Math.round(favBtn[3] * 0.5f));
        int fxp = favBtn[0] + (favBtn[2] - hs) / 2;
        int fyp = favBtn[1] + (favBtn[3] - hs) / 2;
        NewUi.icon(c, favOnly ? NewUi.ICON_HEART_ON : NewUi.ICON_HEART_OFF, fxp, fyp, hs);
        if (!favOnly) {
            dim(c, fxp, fyp, hs, fc, 0.45f);
        }

        boolean gh = NewUi.in(mx, my, gridBtn[0], gridBtn[1], gridBtn[2], gridBtn[3]);
        int gc = mix(BTN, BTN_HOV, ease("grid", gh ? 1f : 0f, 14f));
        NewUi.rrect(c, gridBtn[0], gridBtn[1], gridBtn[2], gridBtn[3], Math.max(3, sl(8)), gc, gc);
        int gridIcon = NewUi.ICON_GRID;
        int gxp = gridBtn[0] + (gridBtn[2] - hs) / 2;
        int gyp = gridBtn[1] + (gridBtn[3] - hs) / 2;
        NewUi.icon(c, gridIcon, gxp, gyp, hs);
        dim(c, gxp, gyp, hs, gc, 0.45f);
    }

    /** The search box: background, then the typed text (or the "Search" hint) and a blinking caret, all in Product Sans. */
    private void drawSearch(GuiGraphicsExtractor c, int mx, int my) {
        boolean sh = NewUi.in(mx, my, searchRect[0], searchRect[1], searchRect[2], searchRect[3]);
        int sc = mix(NewUi.rgb(25, 28, 31), NewUi.rgb(33, 37, 42), ease("search", sh ? 1f : 0f, 14f));
        NewUi.rrect(c, searchRect[0], searchRect[1], searchRect[2], searchRect[3], Math.max(3, sl(8)), sc, sc);
        float ssz = fs(15f);
        int tx = searchRect[0] + sl(16);
        int maxW = searchRect[2] - sl(32);
        String q = searchField == null ? "" : searchField.getValue();
        int ty = NewFont.centerYUi(searchRect[1], searchRect[3], ssz);
        int caretX = tx;
        if (q.isEmpty()) {
            NewFont.drawUi(c, font, "Search", tx, ty, 0xFF8A8D93, NewFont.W_MED, ssz);
        } else {
            String shown = q;
            while (shown.length() > 1 && NewFont.widthUi(font, shown, NewFont.W_MED, ssz) > maxW) {
                shown = shown.substring(1); // too long: show the end of what was typed
            }
            NewFont.drawUi(c, font, shown, tx, ty, TXT, NewFont.W_MED, ssz);
            caretX = tx + NewFont.widthUi(font, shown, NewFont.W_MED, ssz);
        }
        if (searchField != null && searchField.isFocused() && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            int chh = Math.max(6, Math.round(searchRect[3] * 0.5f));
            c.fill(caretX + 1, searchRect[1] + (searchRect[3] - chh) / 2, caretX + 1 + Math.max(1, sl(2)),
                    searchRect[1] + (searchRect[3] - chh) / 2 + chh, TXT);
        }
    }

    // ---------------------------------------------------------------- cards

    private void drawCard(GuiGraphicsExtractor c, NewModSettings.ModDef m, Geo g, int mx, int my, boolean over) {
        boolean enabled = m.functional && NewModSettings.isEnabled(minecraft, m.id);
        boolean hov = over && NewUi.in(mx, my, g.x, g.y, g.w, g.h);
        float hv = ease(m.id + ":c", hov ? 1f : 0f, 14f);
        float e = smooth(ease(m.id + ":on", enabled ? 1f : 0f, 9f));
        int cr = Math.max(3, sl(10));

        // card body: dark with the corner-lit edge (same as the title-screen buttons); on hover the slate
        // diagonal gradient fades in and the edge fades out
        if (hv <= 0.004f) {
            NewUi.rrect(c, g.x, g.y, g.w, g.h, cr, CARD_T, CARD_B);
            NewButtonArt.edge(c, g.x, g.y, g.w, g.h, cr, 0.20f); // same edge as Singleplayer / Multiplayer
        } else {
            NewUi.rrectDiag(c, g.x, g.y, g.w, g.h, cr, mix(CARD_T, HOV_TL, hv), mix(CARD_B, HOV_BR, hv), PANEL_BG);
            NewButtonArt.edge(c, g.x, g.y, g.w, g.h, cr, 0.20f + 0.08f * hv); // same edge as Singleplayer / Multiplayer
        }

        // title: white bold when the mod is on, grey when off (fades between the two)
        float tsz = fs(18f);
        int wgt = e > 0.5f ? NewFont.W_BOLD : NewFont.W_MED;
        String name = NewFont.fitUi(font, m.name, wgt, tsz, g.hx - g.nx - sl(8));
        NewFont.drawUi(c, font, name, g.nx, cyUi(g.y + sl(34), tsz), mix(TXT_DIM, TXT, e), wgt, tsz);

        // heart
        boolean hh = over && NewUi.in(mx, my, g.hx, g.hy, g.hs, g.hs);
        float hhv = ease(m.id + ":h", hh ? 1f : 0f, 16f);
        int hbg = mix(mix(BTN, NewUi.rgb(47, 53, 61), hv), NewUi.rgb(62, 68, 77), hhv);
        NewUi.rrect(c, g.hx, g.hy, g.hs, g.hs, Math.max(3, sl(7)), hbg, hbg);
        int hi = Math.max(8, Math.round(g.hs * 0.55f));
        boolean fav = NewModSettings.isFavorite(minecraft, m.id);
        int hix = g.hx + (g.hs - hi) / 2;
        int hiy = g.hy + (g.hs - hi) / 2;
        NewUi.icon(c, fav ? NewUi.ICON_HEART_ON : NewUi.ICON_HEART_OFF, hix, hiy, hi);
        if (!fav) {
            dim(c, hix, hiy, hi, hbg, 0.45f);
        }

        // mod icon: white when the mod is on, grey when off (the colour behind it is laid over it)
        NewUi.icon(c, m.icon, g.ix, g.iy, g.is);
        dim(c, g.ix, g.iy, g.is, mix(CARD_T, HOV_MID, hv), 0.50f * (1f - e));

        float psz = fs(14.5f);
        {
            // gear
            boolean gh = over && NewUi.in(mx, my, g.gx, g.gy, g.gs, g.gs);
            float gv = ease(m.id + ":g", gh ? 1f : 0f, 16f);
            int gbg = mix(mix(BTN, NewUi.rgb(47, 53, 61), hv), NewUi.rgb(62, 68, 77), gv);
            NewUi.rrect(c, g.gx, g.gy, g.gs, g.gs, Math.max(3, sl(8)), gbg, gbg);
            int gi = Math.max(8, Math.round(g.gs * 0.6f));
            int gix = g.gx + (g.gs - gi) / 2;
            int giy = g.gy + (g.gs - gi) / 2;
            NewUi.icon(c, NewUi.ICON_GEAR, gix, giy, gi);
            dim(c, gix, giy, gi, gbg, 0.45f);
        }

        // Enabled / Disabled pill: colour crossfade + a ripple that spreads from the click point
        boolean ph = over && m.functional && NewUi.in(mx, my, g.px, g.py, g.pw, g.ph);
        float pv = ease(m.id + ":p", ph ? 1f : 0f, 14f);
        int base = mix(mix(BTN, NewUi.rgb(47, 53, 61), hv * 0.6f), ON, e);
        int hovc = mix(OFF_H, ON_H, e);
        int pc = mix(base, hovc, pv);
        int rad = Math.max(3, sl(8));
        NewUi.rrect(c, g.px, g.py, g.pw, g.ph, rad, pc, pc);
        long[] rp = ripples.get(m.id);
        if (rp != null) {
            float p = (System.nanoTime() - rp[0]) / 450_000_000f;
            if (p >= 1f) {
                ripples.remove(m.id);
            } else {
                float ep = 1f - (1f - p) * (1f - p);
                float far = (float) Math.hypot(Math.max(rp[1] - g.px, g.px + g.pw - rp[1]), Math.max(rp[2] - g.py, g.py + g.ph - rp[2]));
                int rc = NewUi.withAlpha(0xFFFFFFFF, 0.30f * (1f - p) + 0.02f);
                NewUi.ripple(c, g.px, g.py, g.pw, g.ph, rad, rp[1], rp[2], far * ep, rc);
            }
        }
        String label = e >= 0.5f ? "Enabled" : "Disabled";
        NewFont.drawUi(c, font, label, g.px + (g.pw - NewFont.widthUi(font, label, NewFont.W_MED, psz)) / 2,
                NewFont.centerYUi(g.py, g.ph, psz), mix(TXT_DIM, TXT_ON, e), NewFont.W_MED, psz);
    }

    // ---------------------------------------------------------------- input

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
        if (navAction != null) {
            return true;
        }
        if (searchField != null && !NewUi.in(mx, my, searchRect[0], searchRect[1], searchRect[2], searchRect[3])) {
            this.setFocused(null); // clicking anywhere else stops typing into the search box
        }
        if (NewUi.in(mx, my, closeBtn[0], closeBtn[1], closeBtn[2], closeBtn[3])) {
            NewUi.click(minecraft);
            closeMenu();
            return true;
        }
        // top bar: Performance (gear), General (screen) and Mod Profiles open their settings pages (ripple, then swap)
        int[] pageOf = {NewSettingsScreen.PAGE_PERF, 0, 0, 0, NewSettingsScreen.PAGE_GENERAL, 0, NewSettingsScreen.PAGE_PROFILES};
        for (int i = 0; i < FX.length; i++) {
            int[] tb = topBtn[i];
            if (pageOf[i] != 0 && NewUi.in(mx, my, tb[0], tb[1], tb[2], tb[3])) {
                NewUi.click(minecraft);
                final int target = pageOf[i];
                navRipple(tb, Math.max(3, sl(10)), mx, my, () -> minecraft.gui.setScreen(new NewSettingsScreen(parent, target)));
                return true;
            }
        }
        for (int i = 0; i < tabs().length; i++) {
            int[] r = tabRect[i];
            if (NewUi.in(mx, my, r[0], r[1], r[2], r[3])) {
                NewUi.click(minecraft);
                tab = i;
                scrollTarget = 0f;
                return true;
            }
        }
        if (NewUi.in(mx, my, favBtn[0], favBtn[1], favBtn[2], favBtn[3])) {
            NewUi.click(minecraft);
            favOnly = !favOnly;
            scrollTarget = 0f;
            return true;
        }
        if (inClip(mx, my)) {
            // A tap acts on release; a drag scrolls (works with a finger too).
            pressActive = true;
            dragScrolling = false;
            travel = 0.0;
            pressedHit = hitAt(mx, my);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        if (pressActive) {
            travel += Math.abs(offsetX) + Math.abs(offsetY);
            if (!dragScrolling && travel > 5.0) {
                dragScrolling = true;
            }
            if (dragScrolling) {
                scrollTarget -= (float) offsetY;
            }
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (pressActive) {
            pressActive = false;
            Hit pressed = pressedHit;
            pressedHit = null;
            if (!dragScrolling && pressed != null && pressed.kind != HIT_NONE) {
                Hit now = hitAt(click.x(), click.y());
                if (now.kind == pressed.kind && now.mod == pressed.mod && now.arg == pressed.arg) {
                    if (pressed.kind != HIT_CARD) {
                        NewUi.click(minecraft);
                    }
                    if (pressed.kind == HIT_HEART) {
                        NewModSettings.toggleFavorite(minecraft, pressed.mod.id);
                    } else if (pressed.kind == HIT_PILL) {
                        ripples.put(pressed.mod.id, new long[]{System.nanoTime(), (long) click.x(), (long) click.y()});
                        NewModSettings.toggle(minecraft, pressed.mod);
                    }
                    else if (pressed.kind == HIT_GEAR && pressed.mod.functional
                            && !NewModOptions.forMod(pressed.mod.id).isEmpty()) {
                        int[] gr = gearRect(pressed.mod);
                        if (gr != null) {
                            navRipple(gr, Math.max(3, sl(8)), click.x(), click.y(),
                                    () -> minecraft.gui.setScreen(new NewModOptionsScreen(this, pressed.mod)));
                        } else {
                            minecraft.gui.setScreen(new NewModOptionsScreen(this, pressed.mod));
                        }
                    }
                    // HIT_CARD: decorative for now
                }
            }
            dragScrolling = false;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollTarget -= (float) verticalAmount * 28f;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (keyEvent.key() == InputConstants.KEY_ESCAPE) {
            closeMenu();
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
