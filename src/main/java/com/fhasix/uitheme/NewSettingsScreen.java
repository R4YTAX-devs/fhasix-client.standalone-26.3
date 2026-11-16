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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The settings pages of the Mod Menu, in the style of the General page of the reference: a section header with a
 * red arrow, then a bordered box (the same edge as the Singleplayer / Multiplayer buttons) with two columns of
 * rows - the name on the left, a switch (or a Low / Medium / High selector, or a colour) on the right.
 * Nothing here animates or uses pictures, so it stays light on weak devices.
 *
 * Pages: Performance (every FPS mode), General (Main Color), Mod Profiles (saved setups + share / import codes).
 * The frame (top bar, panel) is the Mod Menu's own, at the same place and size, so switching pages feels like
 * one menu.
 */
final class NewSettingsScreen extends Screen {
    static final int PAGE_PERF = 1;
    static final int PAGE_GENERAL = 2;
    static final int PAGE_PROFILES = 3;

    private static final String[] PAGE_TITLES = {"MOD MENU", "PERFORMANCE", "GENERAL", "MOD PROFILES"};
    private static final String[] PAGE_INFO = {"", "Performance", "General", "Mod Profiles"};

    // ------------------------------------------------------------------ design space (identical to the Mod Menu)
    private static final int DW = 1394;
    private static final int DH = 958;
    private static final float FIT_W = 1700f;
    private static final float FIT_H = 985f;
    private static final String[] FX = {"fx_gear", "fx_comment", "fx_monitor", "fx_cape", "fx_desktop", "fx_chats", "fx_profile"};
    private static final String[] FX_TIP = {"Performance", "Chat", "Display", "Cosmetics", "General", "Social", "Mod Profiles"};
    private static final int[] FX_PAGE = {PAGE_PERF, 0, 0, 0, PAGE_GENERAL, 0, PAGE_PROFILES};
    private static final Identifier[] FX_TEX = new Identifier[FX.length];
    private static final int FX_SIZE = 96;
    private static final Identifier FX_CLOSE = fx("fx_close");
    private static final Identifier TITLE_BG = Identifier.fromNamespaceAndPath("fhasixuitheme", "textures/gui/title/background.png");

    private static Identifier fx(String name) {
        return Identifier.fromNamespaceAndPath("fhasixuitheme", "textures/gui/fx/" + name + ".png");
    }

    static {
        for (int i = 0; i < FX.length; i++) {
            FX_TEX[i] = fx(FX[i]);
        }
    }

    // colours (same family as the Mod Menu)
    private static final int PANEL_T = NewUi.rgb(15, 17, 20);
    private static final int PANEL_B = NewUi.rgb(14, 16, 19);
    private static final int PANEL_EDGE = NewUi.rgb(23, 26, 31);
    private static final int TILE_T = NewUi.rgb(15, 17, 20);
    private static final int TILE_B = NewUi.rgb(13, 15, 18);
    private static final int HOV_TL = NewUi.rgb(60, 70, 82);
    private static final int HOV_BR = NewUi.rgb(28, 32, 38);
    private static final int CARD_T = NewUi.rgb(15, 17, 20);
    private static final int CARD_B = NewUi.rgb(14, 16, 19);
    private static final int BTN = NewUi.rgb(25, 27, 30);
    private static final int BTN_HOV = NewUi.rgb(42, 46, 53);
    private static final int ON = NewUi.rgb(25, 69, 27);
    private static final int OFF = NewUi.rgb(31, 33, 38);
    private static final int KNOB_ON = NewUi.rgb(62, 200, 82);
    private static final int KNOB_OFF = NewUi.rgb(110, 112, 118);
    private static final int TXT = 0xFFFFFFFF;
    private static final int TXT_DIM = 0xFF7A7B7F;
    private static final int TXT_LABEL = 0xFF9A9DA3;
    private static final int TXT_LABEL_HOV = 0xFFD2D4D8;

    // ------------------------------------------------------------------ page content model
    private static final int T_PERF = 1;   // switch + Low / Medium / High
    private static final int T_COLOR = 2;  // colour swatch

    private static final class Item {
        final int type;
        final String label;
        final int index; // perf mode index

        Item(int type, String label, int index) {
            this.type = type;
            this.label = label;
            this.index = index;
        }
    }

    private static final class Sec {
        final String title;
        final List<Item> items = new ArrayList<>();
        boolean open = true;

        Sec(String title) {
            this.title = title;
        }
    }

    private static final String[] LEVEL_NAMES = {"Low", "Medium", "High"};

    private final Screen parent;
    private final int page;
    private final List<Sec> secs = new ArrayList<>();
    private final ProfilesPage profiles;

    // ------------------------------------------------------------------ layout (screen pixels)
    private float k;
    private int ox;
    private int oy;
    private int[] modTab;
    private final int[][] topBtn = new int[FX.length][];
    private int[] closeBtn;
    private int[] panel;
    private int contentTop;
    private int contentBottom;

    // scrolling
    private float scrollCur = 0f;
    private float scrollTarget = 0f;
    private long lastNanos = 0L;
    private boolean pressActive = false;
    private boolean dragScrolling = false;
    private double travel = 0.0;
    private long pressedKey = -1L;

    // top bar hover / tip animation (the same fade as the Mod Menu's)
    private final Map<String, Float> anim = new HashMap<>();
    private long animLast = System.nanoTime();
    private float animDt = 0f;

    // click ripple on the top bar, then the page changes (same as the Mod Menu)
    private long navStart;
    private int[] navRect;
    private int navRad;
    private float navX;
    private float navY;
    private Runnable navAction;
    private static final float NAV_RIPPLE_S = 0.32f;
    private static final float NAV_DELAY_S = 0.20f;

    // colour picker popup (General -> Main Color)
    private boolean pickerOpen = false;
    private float[] hsv = {0f, 0.8f, 0.9f};
    private int pickerDrag = 0; // 1 = square, 2 = hue bar
    private int[] pkRect;
    private int[] svRect;
    private int[] hueRect;
    private int[] pkSwatch;
    private int[] pkReset;
    private int[] pkDone;
    private final int[][] presetRect = new int[FhasixTheme.PRESETS.length][];

    NewSettingsScreen(Screen parent, int page) {
        super(Component.literal(PAGE_TITLES[page]));
        this.parent = parent;
        this.page = page;
        buildPage();
        this.profiles = page == PAGE_PROFILES ? new ProfilesPage() : null;
    }

    private void buildPage() {
        if (page == PAGE_PERF) {
            String[] names = {"World", "Video", "System"};
            for (int t = 0; t < names.length; t++) {
                Sec s = new Sec(names[t]);
                for (int i = 0; i < NewPerf.COUNT; i++) {
                    if (NewPerf.TAB[i] == t + 1) {
                        s.items.add(new Item(T_PERF, NewPerf.LABELS[i], i));
                    }
                }
                if (!s.items.isEmpty()) {
                    secs.add(s);
                }
            }
        } else if (page == PAGE_GENERAL) {
            Sec s = new Sec("General Settings");
            s.items.add(new Item(T_COLOR, "Main Color", 0));
            secs.add(s);
        }
    }

    // ------------------------------------------------------------------ geometry helpers
    private int sx(int v) {
        return ox + Math.round(v * k);
    }

    private int sy(int v) {
        return oy + Math.round(v * k);
    }

    private int sl(int v) {
        return Math.max(1, Math.round(v * k));
    }

    private float fs(float design) {
        return design * k;
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
        modTab = rect(47, 117, 178, 64);
        for (int i = 0; i < FX.length; i++) {
            topBtn[i] = rect(237 + 88 * i, 117, 76, 64);
        }
        closeBtn = rect(1305, 125, 46, 47);
        panel = rect(47, 197, 1305, 725);
        contentTop = panel[1] + 1;
        contentBottom = panel[1] + panel[3] - 1;
        if (profiles != null) {
            profiles.init();
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void closeMenu() {
        minecraft.gui.setScreen(parent);
    }

    // ------------------------------------------------------------------ small helpers
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

    private void tile(GuiGraphicsExtractor c, int[] r, float hv, int rad) {
        NewUi.rrect(c, r[0], r[1], r[2], r[3], rad, mix(TILE_T, HOV_TL, hv), mix(TILE_B, HOV_BR, hv));
        NewButtonArt.edge(c, r[0], r[1], r[2], r[3], rad, 0.20f + 0.08f * hv);
    }

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
        float e = 1f - (1f - p) * (1f - p) * (1f - p);
        float lx = navX - navRect[0];
        float ly = navY - navRect[1];
        float far = (float) Math.sqrt(Math.max(lx, navRect[2] - lx) * Math.max(lx, navRect[2] - lx)
                + Math.max(ly, navRect[3] - ly) * Math.max(ly, navRect[3] - ly));
        NewUi.ripple(c, navRect[0], navRect[1], navRect[2], navRect[3], navRad, navX, navY, far * e,
                NewUi.withAlpha(0xFFFFFFFF, 0.20f - 0.08f * p));
        if (t >= NAV_DELAY_S) {
            Runnable a = navAction;
            navAction = null;
            a.run();
        }
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
            c.fill(0, 0, width, height, 0xB4000000);
        }
        // while a popup is open the page behind it does not react to the pointer
        boolean blocked = pickerOpen || (profiles != null && profiles.popup != 0);
        int hx = blocked ? -10000 : mx;
        int hy = blocked ? -10000 : my;

        tickScroll(nowN);
        drawTabRow(c, hx, hy);
        int prad = Math.max(3, sl(12));
        NewUi.rrect(c, panel[0], panel[1], panel[2], panel[3], prad, PANEL_T, PANEL_B);
        NewUi.ring(c, panel[0], panel[1], panel[2], panel[3], prad, PANEL_EDGE);

        drawPage(c, hx, hy);

        String ver = "Fhasix Client";
        float vsz = fs(14f);
        text(c, ver, panel[0] + (panel[2] - tw(ver, NewFont.W_MED, vsz)) / 2, panel[1] + panel[3] + sl(14), NewUi.rgb(96, 98, 104), NewFont.W_MED, vsz);

        drawNavRipple(c);
        drawTips(c);
        if (pickerOpen) {
            drawPicker(c, mx, my);
        }
        if (profiles != null) {
            profiles.drawPopup(c, mx, my);
            profiles.drawToast(c);
        }
    }

    private void tickScroll(long now) {
        float dt = lastNanos == 0L ? 0f : Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
        lastNanos = now;
        scrollTarget = Math.max(0f, Math.min(maxScroll(), scrollTarget));
        if (pressActive && dragScrolling) {
            scrollCur = scrollTarget;
        } else {
            scrollCur += (scrollTarget - scrollCur) * (1f - (float) Math.exp(-18f * dt));
            if (Math.abs(scrollTarget - scrollCur) < 0.3f) {
                scrollCur = scrollTarget;
            }
        }
    }

    private void drawTabRow(GuiGraphicsExtractor c, int mx, int my) {
        int rad = Math.max(3, sl(10));
        boolean hov = NewUi.in(mx, my, modTab[0], modTab[1], modTab[2], modTab[3]);
        float mh = ease("tab:mod", hov ? 1f : 0f, 14f);
        int red = mix(FhasixTheme.red(), FhasixTheme.redHover(), mh);
        NewUi.rrect(c, modTab[0], modTab[1], modTab[2], modTab[3], rad, red, red);
        String label = PAGE_TITLES[page];
        float lsz = fs(18f);
        int ico = Math.max(9, sl(24));
        int w = tw(label, NewFont.W_BOLD, lsz);
        boolean showIcon = modTab[2] >= ico + 4 + w + 6;
        int total = (showIcon ? ico + 8 : 0) + w;
        int x0 = modTab[0] + (modTab[2] - total) / 2;
        if (showIcon) {
            NewUi.icon(c, NewUi.ICON_GRID, x0, modTab[1] + (modTab[3] - ico) / 2, ico);
            x0 += ico + 8;
        }
        text(c, label, x0, NewFont.centerYUi(modTab[1], modTab[3], lsz), TXT, NewFont.W_BOLD, lsz);

        for (int i = 0; i < FX.length; i++) {
            int[] r = topBtn[i];
            boolean h = NewUi.in(mx, my, r[0], r[1], r[2], r[3]);
            float hv = ease("top:" + i, h ? 1f : 0f, 14f);
            ease("tip:" + i, h ? 1f : 0f, 11f);
            if (FX_PAGE[i] == page) {
                int rc = mix(FhasixTheme.red(), FhasixTheme.redHover(), hv);
                NewUi.rrect(c, r[0], r[1], r[2], r[3], rad, rc, rc);
            } else {
                tile(c, r, hv, rad);
            }
            int s = Math.max(8, Math.round(r[3] * 0.46f));
            NewUi.tex(c, FX_TEX[i], r[0] + (r[2] - s) / 2, r[1] + (r[3] - s) / 2, s, FX_SIZE);
        }

        boolean ch = NewUi.in(mx, my, closeBtn[0], closeBtn[1], closeBtn[2], closeBtn[3]);
        float cv = ease("close", ch ? 1f : 0f, 14f);
        int crad = Math.max(3, sl(9));
        int fill = NewUi.withAlpha(FhasixTheme.red(), 0.13f + 0.17f * cv);
        NewUi.rrect(c, closeBtn[0], closeBtn[1], closeBtn[2], closeBtn[3], crad, fill, fill);
        NewUi.ring(c, closeBtn[0], closeBtn[1], closeBtn[2], closeBtn[3], crad, mix(FhasixTheme.ring(), FhasixTheme.redHover(), cv));
        int xs = Math.max(8, Math.round(closeBtn[3] * 0.50f));
        NewUi.tex(c, FX_CLOSE, closeBtn[0] + (closeBtn[2] - xs) / 2, closeBtn[1] + (closeBtn[3] - xs) / 2, xs, FX_SIZE);
    }

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
            int w = tw(s, NewFont.W_BOLD, sz);
            text(c, s, r[0] + (r[2] - w) / 2, r[1] - sl(26), NewUi.withAlpha(0xFFFFFFFF, ta), NewFont.W_BOLD, sz);
        }
    }

    // ------------------------------------------------------------------ page layout (design units, relative to the panel)
    private static final int PAD = 24;           // panel edge -> box edge
    private static final int INFO_CY = 38;       // the "i" + page name row
    private static final int FIRST_HEAD_CY = 104;
    private static final int HEAD_TO_BOX = 24;
    private static final int ROW_PITCH = 36;
    private static final int ROW_PAD = 28;       // box edge -> first row centre / last row centre -> box edge
    private static final int SEC_GAP = 34;
    private static final int COL_PAD = 26;

    private int boxX() {
        return panel[0] + sl(PAD);
    }

    private int boxW() {
        return panel[2] - sl(PAD * 2);
    }

    /** Content-space y (no scroll) of everything, per section. */
    private int[] headCy;
    private int[] boxTop;
    private int[] boxH;
    private int contentEnd;

    private void layoutSections() {
        int n = secs.size();
        if (headCy == null || headCy.length != n) {
            headCy = new int[n];
            boxTop = new int[n];
            boxH = new int[n];
        }
        int y = FIRST_HEAD_CY;
        for (int i = 0; i < n; i++) {
            Sec s = secs.get(i);
            headCy[i] = y;
            boxTop[i] = y + HEAD_TO_BOX;
            if (s.open) {
                int rows = (s.items.size() + 1) / 2;
                boxH[i] = ROW_PAD + (rows - 1) * ROW_PITCH + ROW_PAD;
                y = boxTop[i] + boxH[i] + SEC_GAP;
            } else {
                boxH[i] = 0;
                y = y + 46;
            }
        }
        contentEnd = y;
    }

    private float maxScroll() {
        if (profiles != null) {
            return profiles.maxScroll();
        }
        layoutSections();
        int total = sl(contentEnd) - sl(0);
        int view = contentBottom - contentTop - sl(INFO_CY + 30);
        return Math.max(0f, total - view - sl(60));
    }

    /** Screen y of a content-space design y (relative to the panel top), with the scroll applied. */
    private int py(int design) {
        return panel[1] + sl(design) - Math.round(scrollCur);
    }

    private int[] colRect(int col) {
        int bx = boxX();
        int bw = boxW();
        int half = bw / 2;
        return new int[]{bx + col * half, 0, half, 0};
    }

    /** Row centre (screen y) of item i of section s. */
    private int rowCy(int s, int i) {
        return py(boxTop[s] + ROW_PAD + (i / 2) * ROW_PITCH);
    }

    private int[] rowRect(int s, int i) {
        int[] col = colRect(i % 2);
        int cy = rowCy(s, i);
        int h = sl(ROW_PITCH - 2);
        return new int[]{col[0] + sl(10), cy - h / 2, col[2] - sl(20), h};
    }

    // control rectangles of a row
    private int[] toggleRect(int s, int i) {
        int[] col = colRect(i % 2);
        int w = sl(54);
        int h = sl(26);
        return new int[]{col[0] + col[2] - sl(COL_PAD) - w, rowCy(s, i) - h / 2, w, h};
    }

    private int[] segRect(int s, int i, int level) {
        int[] tg = toggleRect(s, i);
        int bw = sl(76);
        int gap = sl(4);
        int h = sl(30);
        int total = bw * 3 + gap * 2;
        int x0 = tg[0] - sl(20) - total;
        return new int[]{x0 + level * (bw + gap), rowCy(s, i) - h / 2, bw, h};
    }

    private int[] swatchRect(int s, int i) {
        int[] col = colRect(i % 2);
        int w = sl(64);
        int h = sl(30);
        return new int[]{col[0] + col[2] - sl(COL_PAD) - w, rowCy(s, i) - h / 2, w, h};
    }

    // page header buttons
    private int[] btnReset() {
        int w = sl(150);
        int h = sl(38);
        return new int[]{panel[0] + panel[2] - sl(PAD) - w, panel[1] + sl(16), w, h};
    }

    private int[] btnDevice() {
        int[] r = btnReset();
        int w = sl(200);
        return new int[]{r[0] - sl(12) - w, r[1], w, r[3]};
    }

    // ------------------------------------------------------------------ drawing the page
    private void drawPage(GuiGraphicsExtractor c, int mx, int my) {
        if (profiles != null) {
            profiles.draw(c, mx, my);
            return;
        }
        layoutSections();
        // info row: a grey "i" circle + the page name, and the buttons on the right (they stay while scrolling)
        float isz = fs(17f);
        int cd = Math.max(10, sl(24));
        int ix = panel[0] + sl(PAD) + sl(4);
        int iy = panel[1] + sl(INFO_CY) - cd / 2;
        NewUi.rrect(c, ix, iy, cd, cd, cd / 2, NewUi.rgb(120, 123, 130), NewUi.rgb(120, 123, 130));
        float lsz = fs(14f);
        text(c, "i", ix + (cd - tw("i", NewFont.W_BOLD, lsz)) / 2, NewFont.centerYUi(iy, cd, lsz), NewUi.rgb(22, 24, 28), NewFont.W_BOLD, lsz);
        text(c, PAGE_INFO[page], ix + cd + sl(14), NewFont.centerYUi(iy, cd, isz), TXT_LABEL, NewFont.W_MED, isz);
        drawHeaderButtons(c, mx, my);

        int clipTop = panel[1] + sl(INFO_CY + 30);
        c.enableScissor(panel[0] + 1, clipTop, panel[0] + panel[2] - 1, contentBottom);
        for (int s = 0; s < secs.size(); s++) {
            drawSection(c, s, mx, my, clipTop);
        }
        c.disableScissor();

        float max = maxScroll();
        if (max > 0f) {
            int bw = Math.max(2, sl(6));
            int bx = sx(1342);
            int track = contentBottom - clipTop;
            int th = Math.max(12, Math.round(track * (track / (track + max))));
            int ty = clipTop + sl(6) + Math.round((track - sl(12) - th) * (scrollCur / max));
            NewUi.rrect(c, bx, ty, bw, th, bw / 2, NewUi.rgb(88, 89, 92), NewUi.rgb(80, 81, 84));
        }
        if (page == PAGE_PERF) {
            drawPerfHint(c, mx, my, clipTop);
        }
    }

    private void drawButton(GuiGraphicsExtractor c, int[] r, String label, boolean hover) {
        int col = hover ? BTN_HOV : BTN;
        NewUi.rrect(c, r[0], r[1], r[2], r[3], Math.max(3, sl(8)), col, col);
        float sz = fs(14.5f);
        text(c, label, r[0] + (r[2] - tw(label, NewFont.W_MED, sz)) / 2, NewFont.centerYUi(r[1], r[3], sz), hover ? TXT : TXT_LABEL, NewFont.W_MED, sz);
    }

    private void drawHeaderButtons(GuiGraphicsExtractor c, int mx, int my) {
        int[] rr = btnReset();
        drawButton(c, rr, "Reset Settings", NewUi.in(mx, my, rr[0], rr[1], rr[2], rr[3]));
        if (page == PAGE_PERF) {
            int[] dr = btnDevice();
            String label = "Device: " + NewPerf.DEVICE_NAMES[NewPerf.device(minecraft)];
            drawButton(c, dr, label, NewUi.in(mx, my, dr[0], dr[1], dr[2], dr[3]));
        }
    }

    private void drawChevron(GuiGraphicsExtractor c, int x, int y, int size, boolean open, int color) {
        int half = size / 2;
        for (int t = 0; t <= half; t++) {
            if (open) { // pointing down
                c.fill(x + t, y + t, x + t + 2, y + t + 1, color);
                c.fill(x + size - t - 2, y + t, x + size - t, y + t + 1, color);
            } else {    // pointing right
                c.fill(x + t, y + t, x + t + 1, y + t + 2, color);
                c.fill(x + t, y + size - t - 2, x + t + 1, y + size - t, color);
            }
        }
    }

    private void drawSection(GuiGraphicsExtractor c, int s, int mx, int my, int clipTop) {
        Sec sec = secs.get(s);
        int hy = py(headCy[s]);
        if (hy < clipTop - sl(30) || hy > contentBottom + sl(30)) {
            return;
        }
        int cs = Math.max(6, sl(11));
        drawChevron(c, boxX() + sl(14), hy - cs / 2, cs, sec.open, FhasixTheme.red());
        float hsz = fs(17f);
        text(c, sec.title, boxX() + sl(14) + cs + sl(12), NewFont.centerYUi(hy - sl(12), sl(24), hsz), TXT, NewFont.W_BOLD, hsz);
        if (!sec.open) {
            return;
        }
        int by = py(boxTop[s]);
        int bh = sl(boxH[s]);
        int rad = Math.max(3, sl(8));
        NewUi.rrect(c, boxX(), by, boxW(), bh, rad, CARD_T, CARD_B);
        NewButtonArt.edge(c, boxX(), by, boxW(), bh, rad, 0.20f); // same edge as Singleplayer / Multiplayer
        for (int i = 0; i < sec.items.size(); i++) {
            drawItem(c, sec.items.get(i), s, i, mx, my, clipTop);
        }
    }

    private void drawItem(GuiGraphicsExtractor c, Item it, int s, int i, int mx, int my, int clipTop) {
        int[] rr = rowRect(s, i);
        if (rr[1] + rr[3] < clipTop || rr[1] > contentBottom) {
            return;
        }
        boolean inClip = my >= clipTop && my <= contentBottom;
        boolean over = inClip && NewUi.in(mx, my, rr[0], rr[1], rr[2], rr[3]);
        float lsz = fs(15.5f);
        int[] col = colRect(i % 2);
        text(c, it.label, col[0] + sl(COL_PAD), NewFont.centerYUi(rr[1], rr[3], lsz), over ? TXT_LABEL_HOV : TXT_LABEL, NewFont.W_MED, lsz);
        if (it.type == T_PERF) {
            boolean on = NewPerf.on(minecraft, it.index);
            int lvl = NewPerf.level(minecraft, it.index);
            for (int l = 0; l < 3 && !NewPerf.TOGGLE_ONLY[it.index]; l++) {
                int[] r = segRect(s, i, l);
                boolean h = inClip && NewUi.in(mx, my, r[0], r[1], r[2], r[3]);
                int fill;
                if (l == lvl) {
                    fill = on ? (h ? FhasixTheme.redHover() : FhasixTheme.red()) : NewUi.rgb(52, 58, 67);
                } else {
                    fill = h ? BTN_HOV : BTN;
                }
                NewUi.rrect(c, r[0], r[1], r[2], r[3], Math.max(2, sl(7)), fill, fill);
                float ssz = fs(13.5f);
                text(c, LEVEL_NAMES[l], r[0] + (r[2] - tw(LEVEL_NAMES[l], NewFont.W_BOLD, ssz)) / 2, NewFont.centerYUi(r[1], r[3], ssz),
                        l == lvl || h ? TXT : TXT_DIM, NewFont.W_BOLD, ssz);
            }
            drawSwitch(c, toggleRect(s, i), on);
        } else if (it.type == T_COLOR) {
            int[] r = swatchRect(s, i);
            int rad = Math.max(3, sl(7));
            NewUi.rrect(c, r[0], r[1], r[2], r[3], rad, FhasixTheme.red(), FhasixTheme.red());
            NewButtonArt.edge(c, r[0], r[1], r[2], r[3], rad, 0.26f);
        }
    }

    /** An on/off switch: dark track with a knob - green and on the right when on, grey and on the left when off. */
    private void drawSwitch(GuiGraphicsExtractor c, int[] r, boolean on) {
        int track = on ? ON : OFF;
        NewUi.rrect(c, r[0], r[1], r[2], r[3], r[3] / 2, track, track);
        int kd = r[3] - sl(8);
        int kx = on ? r[0] + r[2] - kd - sl(4) : r[0] + sl(4);
        int knob = on ? KNOB_ON : KNOB_OFF;
        NewUi.rrect(c, kx, r[1] + (r[3] - kd) / 2, kd, kd, kd / 2, knob, knob);
    }

    /** Performance page: what the hovered row's mode does at its current value, in a small box next to the pointer. */
    private void drawPerfHint(GuiGraphicsExtractor c, int mx, int my, int clipTop) {
        if (my < clipTop || my > contentBottom) {
            return;
        }
        for (int s = 0; s < secs.size(); s++) {
            Sec sec = secs.get(s);
            if (!sec.open) {
                continue;
            }
            for (int i = 0; i < sec.items.size(); i++) {
                int[] rr = rowRect(s, i);
                int[] first = NewPerf.TOGGLE_ONLY[sec.items.get(i).index] ? toggleRect(s, i) : segRect(s, i, 0);
                // only over the name part of the row, so the box never covers the buttons being used
                if (!NewUi.in(mx, my, rr[0], rr[1], first[0] - rr[0] - sl(8), rr[3])) {
                    continue;
                }
                int idx = sec.items.get(i).index;
                float hsz = fs(14f);
                int w = Math.min(sl(340), width - 12);
                int innerW = w - sl(24);
                List<String> lines = new ArrayList<>();
                String line = "";
                for (String word : NewPerf.DESC[idx][NewPerf.level(minecraft, idx)].split(" ")) {
                    String tryLine = line.isEmpty() ? word : line + " " + word;
                    if (!line.isEmpty() && tw(tryLine, NewFont.W_REG, hsz) > innerW) {
                        lines.add(line);
                        line = word;
                    } else {
                        line = tryLine;
                    }
                }
                if (!line.isEmpty()) {
                    lines.add(line);
                }
                int lh = Math.round(hsz * 1.5f);
                int h = sl(12) + lh + 4 + lines.size() * lh + sl(12);
                int x = Math.max(4, Math.min(width - w - 6, mx + 12));
                int y = Math.max(4, Math.min(height - h - 6, my + 14));
                NewUi.frame(c, x, y, w, h, Math.max(3, sl(8)), PANEL_EDGE, PANEL_T, PANEL_B);
                text(c, NewPerf.LABELS[idx], x + sl(12), y + sl(12), TXT, NewFont.W_BOLD, hsz);
                for (int l = 0; l < lines.size(); l++) {
                    text(c, lines.get(l), x + sl(12), y + sl(12) + lh + 4 + l * lh, TXT_DIM, NewFont.W_REG, hsz);
                }
                return;
            }
        }
    }

    // ------------------------------------------------------------------ colour picker popup
    private static float clamp01(double v) {
        return (float) Math.max(0.0, Math.min(1.0, v));
    }

    private static int hsvToRgb(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        float hh = h * 6f;
        int i = (int) hh;
        float f = hh - i;
        float p = v * (1f - s);
        float q = v * (1f - s * f);
        float t = v * (1f - s * (1f - f));
        float r;
        float g;
        float b;
        switch (i % 6) {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            default: r = v; g = p; b = q; break;
        }
        return 0xFF000000 | (Math.round(r * 255f) << 16) | (Math.round(g * 255f) << 8) | Math.round(b * 255f);
    }

    private static float[] rgbToHsv(int rgb) {
        float r = ((rgb >> 16) & 255) / 255f;
        float g = ((rgb >> 8) & 255) / 255f;
        float b = (rgb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float hue;
        if (d <= 0f) {
            hue = 0f;
        } else if (max == r) {
            hue = ((g - b) / d) % 6f;
        } else if (max == g) {
            hue = (b - r) / d + 2f;
        } else {
            hue = (r - g) / d + 4f;
        }
        hue /= 6f;
        if (hue < 0f) {
            hue += 1f;
        }
        return new float[]{hue, max <= 0f ? 0f : d / max, max};
    }

    private void openPicker() {
        pickerOpen = true;
        hsv = rgbToHsv(FhasixTheme.rgb());
        int w = 600;
        int h = 410;
        int x = (DW - w) / 2;
        int y = 300;
        pkRect = rect(x, y, w, h);
        svRect = rect(x + 28, y + 72, 300, 230);
        hueRect = rect(x + 344, y + 72, 28, 230);
        pkSwatch = rect(x + 400, y + 72, 172, 70);
        int pw = 30;
        int gap = 10;
        for (int i = 0; i < presetRect.length; i++) {
            presetRect[i] = rect(x + 400 + (i % 5) * (pw + gap), y + 168 + (i / 5) * (pw + gap), pw, pw);
        }
        pkReset = rect(x + 28, y + h - 64, 130, 42);
        pkDone = rect(x + w - 28 - 130, y + h - 64, 130, 42);
    }

    private void applyPick() {
        FhasixTheme.set(minecraft, hsvToRgb(hsv[0], hsv[1], hsv[2]) & 0xFFFFFF);
    }

    private void pickSv(double mx, double my) {
        hsv[1] = clamp01((mx - svRect[0]) / Math.max(1, svRect[2] - 1));
        hsv[2] = 1f - clamp01((my - svRect[1]) / Math.max(1, svRect[3] - 1));
        applyPick();
    }

    private void pickHue(double my) {
        hsv[0] = clamp01((my - hueRect[1]) / Math.max(1, hueRect[3] - 1));
        applyPick();
    }

    private void drawPicker(GuiGraphicsExtractor c, int mx, int my) {
        c.fill(0, 0, width, height, 0xB0000000);
        int rad = Math.max(3, sl(12));
        NewUi.rrect(c, pkRect[0], pkRect[1], pkRect[2], pkRect[3], rad, NewUi.rgb(20, 22, 27), NewUi.rgb(15, 17, 20));
        NewButtonArt.edge(c, pkRect[0], pkRect[1], pkRect[2], pkRect[3], rad, 0.28f);
        float tsz = fs(21f);
        text(c, "Main Color", pkRect[0] + sl(28), NewFont.centerYUi(pkRect[1] + sl(18), sl(40), tsz), TXT, NewFont.W_BOLD, tsz);

        int border = 0xFF2A2D34;
        // saturation (left -> right) / brightness (bottom -> top) square, as vertical strips
        c.fill(svRect[0] - 1, svRect[1] - 1, svRect[0] + svRect[2] + 1, svRect[1] + svRect[3] + 1, border);
        int cw = Math.max(1, svRect[2] / 80);
        for (int x = 0; x < svRect[2]; x += cw) {
            float s = Math.min(1f, (x + cw / 2f) / svRect[2]);
            c.fillGradient(svRect[0] + x, svRect[1], svRect[0] + Math.min(svRect[2], x + cw), svRect[1] + svRect[3], hsvToRgb(hsv[0], s, 1f), 0xFF000000);
        }
        int cur = hsvToRgb(hsv[0], hsv[1], hsv[2]);
        int mxp = svRect[0] + Math.round(hsv[1] * (svRect[2] - 1));
        int myp = svRect[1] + Math.round((1f - hsv[2]) * (svRect[3] - 1));
        int ring = Math.max(5, sl(18));
        NewUi.rrect(c, mxp - ring / 2, myp - ring / 2, ring, ring, ring / 2, 0xFFFFFFFF, 0xFFFFFFFF);
        NewUi.rrect(c, mxp - ring / 2 + 2, myp - ring / 2 + 2, ring - 4, ring - 4, Math.max(1, ring / 2 - 2), cur, cur);

        // hue bar: red -> yellow -> green -> cyan -> blue -> magenta -> red
        c.fill(hueRect[0] - 1, hueRect[1] - 1, hueRect[0] + hueRect[2] + 1, hueRect[1] + hueRect[3] + 1, border);
        for (int seg = 0; seg < 6; seg++) {
            int y0 = hueRect[1] + hueRect[3] * seg / 6;
            int y1 = hueRect[1] + hueRect[3] * (seg + 1) / 6;
            c.fillGradient(hueRect[0], y0, hueRect[0] + hueRect[2], y1, hsvToRgb(seg / 6f, 1f, 1f), hsvToRgb((seg + 1) / 6f, 1f, 1f));
        }
        int hy = hueRect[1] + Math.round(hsv[0] * (hueRect[3] - 1));
        int mh = Math.max(3, sl(6));
        c.fill(hueRect[0] - 3, hy - mh / 2 - 1, hueRect[0] + hueRect[2] + 3, hy - mh / 2 + mh + 1, 0xFF000000);
        c.fill(hueRect[0] - 2, hy - mh / 2, hueRect[0] + hueRect[2] + 2, hy - mh / 2 + mh, 0xFFFFFFFF);

        // current colour + hex
        int srad = Math.max(3, sl(8));
        NewUi.rrect(c, pkSwatch[0], pkSwatch[1], pkSwatch[2], pkSwatch[3], srad, cur, cur);
        NewButtonArt.edge(c, pkSwatch[0], pkSwatch[1], pkSwatch[2], pkSwatch[3], srad, 0.30f);
        String hex = String.format(java.util.Locale.ROOT, "#%06X", cur & 0xFFFFFF);
        float hsz = fs(15f);
        text(c, hex, pkSwatch[0], NewFont.centerYUi(pkSwatch[1] + pkSwatch[3] + sl(6), sl(24), hsz), TXT_LABEL, NewFont.W_MED, hsz);

        // presets
        for (int i = 0; i < presetRect.length; i++) {
            int[] r = presetRect[i];
            int pc = 0xFF000000 | FhasixTheme.PRESETS[i];
            boolean selected = (FhasixTheme.rgb() & 0xFFFFFF) == FhasixTheme.PRESETS[i];
            if (selected) {
                NewUi.rrect(c, r[0] - 2, r[1] - 2, r[2] + 4, r[3] + 4, Math.max(3, sl(8)), 0xFFFFFFFF, 0xFFFFFFFF);
            }
            NewUi.rrect(c, r[0], r[1], r[2], r[3], Math.max(3, sl(7)), pc, pc);
        }

        boolean rh = NewUi.in(mx, my, pkReset[0], pkReset[1], pkReset[2], pkReset[3]);
        drawButton(c, pkReset, "Reset", rh);
        boolean dh = NewUi.in(mx, my, pkDone[0], pkDone[1], pkDone[2], pkDone[3]);
        int dc = dh ? FhasixTheme.redHover() : FhasixTheme.red();
        NewUi.rrect(c, pkDone[0], pkDone[1], pkDone[2], pkDone[3], Math.max(3, sl(8)), dc, dc);
        float bsz = fs(15f);
        text(c, "Done", pkDone[0] + (pkDone[2] - tw("Done", NewFont.W_BOLD, bsz)) / 2, NewFont.centerYUi(pkDone[1], pkDone[3], bsz), TXT, NewFont.W_BOLD, bsz);
    }

    // ------------------------------------------------------------------ input
    private static final int K_HEAD = 1;
    private static final int K_TOGGLE = 2;
    private static final int K_SEG = 3;
    private static final int K_SWATCH = 4;
    private static final int K_RESET = 5;
    private static final int K_DEVICE = 6;

    private static long key(int kind, int a, int b) {
        return ((long) kind << 40) | ((long) a << 20) | b;
    }

    /** What is under the pointer inside the page (or -1). */
    private long hitAt(double mx, double my) {
        if (profiles != null) {
            return profiles.hitAt(mx, my);
        }
        int[] rr = btnReset();
        if (NewUi.in(mx, my, rr[0], rr[1], rr[2], rr[3])) {
            return key(K_RESET, 0, 0);
        }
        if (page == PAGE_PERF) {
            int[] dr = btnDevice();
            if (NewUi.in(mx, my, dr[0], dr[1], dr[2], dr[3])) {
                return key(K_DEVICE, 0, 0);
            }
        }
        int clipTop = panel[1] + sl(INFO_CY + 30);
        if (my < clipTop || my > contentBottom || mx < panel[0] || mx > panel[0] + panel[2]) {
            return -1L;
        }
        layoutSections();
        for (int s = 0; s < secs.size(); s++) {
            int hy = py(headCy[s]);
            if (NewUi.in(mx, my, boxX(), hy - sl(16), boxW(), sl(32))) {
                return key(K_HEAD, s, 0);
            }
            Sec sec = secs.get(s);
            if (!sec.open) {
                continue;
            }
            for (int i = 0; i < sec.items.size(); i++) {
                Item it = sec.items.get(i);
                if (it.type == T_PERF) {
                    for (int l = 0; l < 3 && !NewPerf.TOGGLE_ONLY[it.index]; l++) {
                        int[] r = segRect(s, i, l);
                        if (NewUi.in(mx, my, r[0], r[1], r[2], r[3])) {
                            return key(K_SEG, s * 100 + i, l);
                        }
                    }
                    int[] tg = toggleRect(s, i);
                    if (NewUi.in(mx, my, tg[0] - sl(6), tg[1] - sl(6), tg[2] + sl(12), tg[3] + sl(12))) {
                        return key(K_TOGGLE, s * 100 + i, 0);
                    }
                } else if (it.type == T_COLOR) {
                    int[] r = swatchRect(s, i);
                    if (NewUi.in(mx, my, r[0], r[1], r[2], r[3])) {
                        return key(K_SWATCH, s * 100 + i, 0);
                    }
                }
            }
        }
        return -1L;
    }

    private void act(long k0) {
        if (profiles != null) {
            profiles.act(k0);
            return;
        }
        int kind = (int) (k0 >> 40);
        int a = (int) ((k0 >> 20) & 0xFFFFF);
        int b = (int) (k0 & 0xFFFFF);
        if (kind == K_HEAD) {
            NewUi.click(minecraft);
            secs.get(a).open = !secs.get(a).open;
            scrollTarget = Math.min(scrollTarget, maxScroll());
        } else if (kind == K_TOGGLE) {
            NewUi.click(minecraft);
            Item it = secs.get(a / 100).items.get(a % 100);
            NewPerf.setOn(minecraft, it.index, !NewPerf.on(minecraft, it.index));
        } else if (kind == K_SEG) {
            NewUi.click(minecraft);
            Item it = secs.get(a / 100).items.get(a % 100);
            NewPerf.setLevel(minecraft, it.index, b);
        } else if (kind == K_SWATCH) {
            NewUi.click(minecraft);
            openPicker();
        } else if (kind == K_RESET) {
            NewUi.click(minecraft);
            if (page == PAGE_PERF) {
                NewPerf.applyDevice(minecraft, NewPerf.device(minecraft));
            } else if (page == PAGE_GENERAL) {
                FhasixTheme.reset(minecraft);
            }
        } else if (kind == K_DEVICE) {
            NewUi.click(minecraft);
            minecraft.gui.setScreen(new NewDeviceSelectorScreen(this));
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.buttonInfo().button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return true;
        }
        double mx = click.x();
        double my = click.y();
        if (profiles != null && profiles.popup != 0) { // a profile popup swallows every click
            return profiles.clickPopup(click, doubled);
        }
        if (pickerOpen) {
            if (NewUi.in(mx, my, svRect[0], svRect[1], svRect[2], svRect[3])) {
                pickerDrag = 1;
                pickSv(mx, my);
            } else if (NewUi.in(mx, my, hueRect[0] - 4, hueRect[1], hueRect[2] + 8, hueRect[3])) {
                pickerDrag = 2;
                pickHue(my);
            } else if (NewUi.in(mx, my, pkDone[0], pkDone[1], pkDone[2], pkDone[3])
                    || !NewUi.in(mx, my, pkRect[0], pkRect[1], pkRect[2], pkRect[3])) {
                NewUi.click(minecraft);
                FhasixTheme.save(minecraft);
                pickerOpen = false;
            } else if (NewUi.in(mx, my, pkReset[0], pkReset[1], pkReset[2], pkReset[3])) {
                NewUi.click(minecraft);
                FhasixTheme.reset(minecraft);
                hsv = rgbToHsv(FhasixTheme.rgb());
            } else {
                for (int i = 0; i < presetRect.length; i++) {
                    int[] r = presetRect[i];
                    if (NewUi.in(mx, my, r[0], r[1], r[2], r[3])) {
                        NewUi.click(minecraft);
                        FhasixTheme.set(minecraft, FhasixTheme.PRESETS[i]);
                        FhasixTheme.save(minecraft);
                        hsv = rgbToHsv(FhasixTheme.rgb());
                    }
                }
            }
            return true;
        }
        if (NewUi.in(mx, my, closeBtn[0], closeBtn[1], closeBtn[2], closeBtn[3])) {
            NewUi.click(minecraft);
            closeMenu();
            return true;
        }
        if (NewUi.in(mx, my, modTab[0], modTab[1], modTab[2], modTab[3])) {
            NewUi.click(minecraft);
            navRipple(modTab, Math.max(3, sl(10)), mx, my, () -> minecraft.gui.setScreen(new NewModMenuScreen(parent)));
            return true;
        }
        for (int i = 0; i < FX.length; i++) {
            int[] r = topBtn[i];
            if (NewUi.in(mx, my, r[0], r[1], r[2], r[3])) {
                if (FX_PAGE[i] != 0) {
                    NewUi.click(minecraft);
                    final int target = FX_PAGE[i];
                    navRipple(r, Math.max(3, sl(10)), mx, my, () -> minecraft.gui.setScreen(
                            target == page ? new NewModMenuScreen(parent) : new NewSettingsScreen(parent, target)));
                }
                return true;
            }
        }
        if (profiles != null) {
            if (super.mouseClicked(click, doubled)) { // the profile name box takes typing / focus
                return true;
            }
            if (!profiles.overName(mx, my)) {
                this.setFocused(null);
            }
        }
        if (NewUi.in(mx, my, panel[0], panel[1], panel[2], panel[3])) {
            pressActive = true;
            dragScrolling = false;
            travel = 0.0;
            pressedKey = hitAt(mx, my);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        if (pickerOpen && pickerDrag != 0) {
            if (pickerDrag == 1) {
                pickSv(click.x(), click.y());
            } else {
                pickHue(click.y());
            }
            return true;
        }
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
        if (pickerDrag != 0) {
            pickerDrag = 0;
            FhasixTheme.save(minecraft);
            return true;
        }
        if (pressActive) {
            pressActive = false;
            boolean wasDrag = dragScrolling;
            dragScrolling = false;
            long pressed = pressedKey;
            pressedKey = -1L;
            if (!wasDrag && pressed != -1L && hitAt(click.x(), click.y()) == pressed) {
                act(pressed);
            }
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!pickerOpen && (profiles == null || profiles.popup == 0)) {
            scrollTarget -= (float) verticalAmount * 40f;
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (keyEvent.key() == InputConstants.KEY_ESCAPE) {
            if (profiles != null && profiles.popup != 0) {
                profiles.closePopup();
                return true;
            }
            if (pickerOpen) {
                FhasixTheme.save(minecraft);
                pickerOpen = false;
                return true;
            }
            closeMenu();
            return true;
        }
        if (profiles != null && (keyEvent.key() == InputConstants.KEY_RETURN || keyEvent.key() == InputConstants.KEY_NUMPADENTER) && profiles.enterPressed()) {
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    // ================================================================== Mod Profiles page
    private static final Identifier PF_ICONS = Identifier.fromNamespaceAndPath("fhasixuitheme", "textures/gui/profile_icons.png");
    private static final int PF_ICON_SHARE = 0;
    private static final int PF_ICON_COPY = 1;
    private static final int PF_ICON_EDIT = 2;
    private static final int PF_ICON_UPLOAD = 3;
    private static final int PF_X0 = 21;
    private static final int PF_Y0 = 82;
    private static final int PF_PX = 300;
    private static final int PF_PY = 192;
    private static final int PF_CARD_W = 237;
    private static final int PF_CARD_H = 152;
    private static final int K_P_TOPSHARE = 11;
    private static final int K_P_TOPIMPORT = 12;
    private static final int K_P_CREATE = 13;
    private static final int K_P_CARD = 14;
    private static final int K_P_ICON = 15;
    private static final int K_P_DEL = 16;

    private void copyToClipboard(String s) {
        minecraft.keyboardHandler.setClipboard(s);
    }

    private void pfIcon(GuiGraphicsExtractor c, int idx, int x, int y, int size, int color) {
        c.blit(RenderPipelines.GUI_TEXTURED, PF_ICONS, x, y, idx * 64, 0, size, size, 64, 64, 256, 64, color);
    }

    private final class ProfilesPage {
        int popup = 0; // 0 none, 1 share, 2 import
        String shareCode = "";
        String popupError = "";
        String toast = null;
        long toastUntil = 0L;
        EditBox nameField;
        EditBox codeField;
        int[] nameR;
        int[] createR;
        int[] shareBtnR;
        int[] importBtnR;
        int[] pop;
        int[] popField;
        int[] popA;
        int[] popB;
        int[] shareBox;

        void init() {
            nameR = rect(47 + 21, 197 + 636, 1305 - 42 - 135 - 12, 64);
            createR = rect(47 + 1305 - 21 - 135, 197 + 636, 135, 64);
            importBtnR = rect(47 + 1305 - 22 - 156, 197 + 20, 156, 40);
            shareBtnR = rect(47 + 1305 - 22 - 156 - 8 - 150, 197 + 20, 150, 40);

            String keep = nameField != null ? nameField.getValue() : "";
            nameField = new EditBox(font, nameR[0] + sl(18), nameR[1] + (nameR[3] - font.lineHeight) / 2,
                    Math.max(10, nameR[2] - sl(36)), font.lineHeight + 2, Component.literal("Name"));
            nameField.setMaxLength(24);
            nameField.setBordered(false);
            nameField.setValue(keep);
            NewSettingsScreen.this.addRenderableWidget(nameField);

            int pw = 760;
            int ph = 340;
            int px = (DW - pw) / 2;
            int py0 = 290;
            pop = rect(px, py0, pw, ph);
            popField = rect(px + 32, py0 + 128, pw - 64, 52);
            shareBox = rect(px + 32, py0 + 118, pw - 64, 120);
            popA = rect(px + pw - 32 - 180, py0 + ph - 32 - 48, 180, 48);
            popB = rect(px + 32, py0 + ph - 32 - 48, 180, 48);
            String keepCode = codeField != null ? codeField.getValue() : "";
            codeField = new EditBox(font, popField[0] + sl(16), popField[1] + (popField[3] - font.lineHeight) / 2,
                    Math.max(10, popField[2] - sl(32)), font.lineHeight + 2, Component.literal("Code"));
            codeField.setMaxLength(1500);
            codeField.setBordered(false);
            codeField.setValue(keepCode);
            codeField.visible = popup == 2;
            nameField.visible = popup == 0;
            NewSettingsScreen.this.addRenderableWidget(codeField);
        }

        boolean overName(double mx, double my) {
            return NewUi.in(mx, my, nameR[0], nameR[1], nameR[2], nameR[3]);
        }

        // ---------------------------------------------------------- layout
        private int cols() {
            return Math.max(1, (panel[2] - sl(42)) / sl(PF_PX));
        }

        private int[] cardRect(int i) {
            int col = i % cols();
            int row = i / cols();
            int x = panel[0] + sl(PF_X0 + col * PF_PX);
            int y = panel[1] + sl(PF_Y0 + row * PF_PY) - Math.round(scrollCur);
            return new int[]{x, y, sl(PF_CARD_W), sl(PF_CARD_H)};
        }

        private int[] iconRect(int i, int n) {
            int[] c = cardRect(i);
            int s = sl(31);
            return new int[]{c[0] + c[2] + sl(12), c[1] + n * sl(60), s, s};
        }

        private int[] delRect(int i) {
            int[] c = cardRect(i);
            int s = Math.max(10, sl(24));
            return new int[]{c[0] + c[2] - s - sl(8), c[1] + sl(8), s, s};
        }

        private int viewTop() {
            return panel[1] + sl(74);
        }

        private int viewBottom() {
            return panel[1] + sl(622);
        }

        float maxScroll() {
            int n = FhasixProfiles.all().size();
            int rows = (n + cols() - 1) / cols();
            if (rows == 0) {
                return 0f;
            }
            return Math.max(0f, sl(PF_Y0 + rows * PF_PY - 20) - (viewBottom() - panel[1]));
        }

        // ---------------------------------------------------------- drawing
        void draw(GuiGraphicsExtractor c, int mx, int my) {
            // top buttons
            boolean sh = NewUi.in(mx, my, shareBtnR[0], shareBtnR[1], shareBtnR[2], shareBtnR[3]);
            drawIconButton(c, shareBtnR, PF_ICON_SHARE, "Share profile", sh);
            boolean im = NewUi.in(mx, my, importBtnR[0], importBtnR[1], importBtnR[2], importBtnR[3]);
            drawIconButton(c, importBtnR, PF_ICON_UPLOAD, "Import profile", im);

            List<FhasixProfiles.Profile> list = FhasixProfiles.all();
            c.enableScissor(panel[0] + 1, viewTop(), panel[0] + panel[2] - 1, viewBottom());
            boolean inView = my >= viewTop() && my <= viewBottom();
            if (list.isEmpty()) {
                float sz = fs(16f);
                String msg = "No profiles yet - type a name below and press Create Profile";
                text(c, msg, panel[0] + (panel[2] - tw(msg, NewFont.W_MED, sz)) / 2, viewTop() + sl(120), TXT_LABEL, NewFont.W_MED, sz);
            }
            for (int i = 0; i < list.size(); i++) {
                int[] cr = cardRect(i);
                if (cr[1] + cr[3] < viewTop() || cr[1] > viewBottom()) {
                    continue;
                }
                boolean over = inView && NewUi.in(mx, my, cr[0], cr[1], cr[2], cr[3]);
                drawCard(c, list.get(i), cr, over);
                for (int n = 0; n < 3; n++) {
                    int[] ir = iconRect(i, n);
                    boolean ih = inView && NewUi.in(mx, my, ir[0], ir[1], ir[2], ir[3]);
                    int fill = ih ? BTN_HOV : BTN;
                    NewUi.rrect(c, ir[0], ir[1], ir[2], ir[3], Math.max(3, sl(7)), fill, fill);
                    int gs = Math.max(8, Math.round(ir[3] * 0.58f));
                    pfIcon(c, n == 0 ? PF_ICON_SHARE : (n == 1 ? PF_ICON_COPY : PF_ICON_EDIT), ir[0] + (ir[2] - gs) / 2, ir[1] + (ir[3] - gs) / 2, gs, ih ? 0xFFFFFFFF : 0xFFB4B7BD);
                }
                if (over) { // delete cross, only while the pointer is on the card
                    int[] dr = delRect(i);
                    boolean dh = NewUi.in(mx, my, dr[0], dr[1], dr[2], dr[3]);
                    int dc = NewUi.withAlpha(0xFF000000, dh ? 0.75f : 0.5f);
                    NewUi.rrect(c, dr[0], dr[1], dr[2], dr[3], dr[2] / 2, dc, dc);
                    int xs = Math.max(6, Math.round(dr[2] * 0.55f));
                    NewUi.tex(c, FX_CLOSE, dr[0] + (dr[2] - xs) / 2, dr[1] + (dr[3] - xs) / 2, xs, FX_SIZE);
                }
            }
            c.disableScissor();

            float max = maxScroll();
            if (max > 0f) {
                int bw = Math.max(2, sl(6));
                int track = viewBottom() - viewTop();
                int th = Math.max(12, Math.round(track * (track / (track + max))));
                int ty = viewTop() + Math.round((track - th) * (scrollCur / max));
                NewUi.rrect(c, sx(1342), ty, bw, th, bw / 2, NewUi.rgb(88, 89, 92), NewUi.rgb(80, 81, 84));
            }
            drawBottom(c, mx, my);
        }

        private void drawIconButton(GuiGraphicsExtractor c, int[] r, int icon, String label, boolean hover) {
            int col = hover ? BTN_HOV : BTN;
            NewUi.rrect(c, r[0], r[1], r[2], r[3], Math.max(3, sl(8)), col, col);
            int gs = Math.max(8, Math.round(r[3] * 0.48f));
            float sz = fs(14.5f);
            int total = gs + sl(10) + tw(label, NewFont.W_MED, sz);
            int x0 = r[0] + (r[2] - total) / 2;
            pfIcon(c, icon, x0, r[1] + (r[3] - gs) / 2, gs, hover ? 0xFFFFFFFF : 0xFFB4B7BD);
            text(c, label, x0 + gs + sl(10), NewFont.centerYUi(r[1], r[3], sz), hover ? TXT : TXT_LABEL, NewFont.W_MED, sz);
        }

        /** The profile tile: the main colour with a quiet pixel pattern (like the reference), the name in the middle. */
        private void drawCard(GuiGraphicsExtractor c, FhasixProfiles.Profile p, int[] r, boolean hover) {
            int rad = Math.max(3, sl(8));
            int base = hover ? FhasixTheme.redHover() : FhasixTheme.red();
            NewUi.rrect(c, r[0], r[1], r[2], r[3], rad, base, base);
            int cols = 12;
            int rows = 8;
            int seed = p.name.hashCode();
            for (int gy = 0; gy < rows; gy++) {
                for (int gx = 0; gx < cols; gx++) {
                    if ((gx == 0 || gx == cols - 1) && (gy == 0 || gy == rows - 1)) {
                        continue; // keep the rounded corners clean
                    }
                    int h = (gx * 73856093) ^ (gy * 19349663) ^ (seed * 83492791);
                    float v = ((h >>> 8) & 255) / 255f;
                    int shade = v < 0.5f ? 0xFF000000 : 0xFFFFFFFF;
                    float alpha = 0.03f + 0.10f * (((h >>> 16) & 255) / 255f);
                    c.fill(r[0] + r[2] * gx / cols, r[1] + r[3] * gy / rows, r[0] + r[2] * (gx + 1) / cols, r[1] + r[3] * (gy + 1) / rows,
                            NewUi.withAlpha(shade, alpha));
                }
            }
            NewButtonArt.edge(c, r[0], r[1], r[2], r[3], rad, 0.26f);
            if (p.name.equals(FhasixProfiles.active())) {
                NewUi.ring(c, r[0] - 2, r[1] - 2, r[2] + 4, r[3] + 4, rad + 2, 0xFFFFFFFF);
            }
            float nsz = fs(21f);
            String name = p.name;
            while (name.length() > 1 && tw(name, NewFont.W_BOLD, nsz) > r[2] - sl(24)) {
                name = name.substring(0, name.length() - 1);
            }
            if (!name.equals(p.name)) {
                name = name + "..";
            }
            text(c, name, r[0] + (r[2] - tw(name, NewFont.W_BOLD, nsz)) / 2, NewFont.centerYUi(r[1], r[3], nsz), TXT, NewFont.W_BOLD, nsz);
        }

        private void drawBottom(GuiGraphicsExtractor c, int mx, int my) {
            int rad = Math.max(3, sl(8));
            boolean nh = NewUi.in(mx, my, nameR[0], nameR[1], nameR[2], nameR[3]);
            int nc = nh ? NewUi.rgb(26, 29, 33) : NewUi.rgb(21, 23, 27);
            NewUi.rrect(c, nameR[0], nameR[1], nameR[2], nameR[3], rad, nc, nc);
            float sz = fs(15.5f);
            int tx = nameR[0] + sl(18);
            int ty = NewFont.centerYUi(nameR[1], nameR[3], sz);
            String q = nameField.getValue();
            int caretX = tx;
            if (q.isEmpty()) {
                text(c, "Profile name (enter here)", tx, ty, 0xFF868990, NewFont.W_MED, sz);
            } else {
                text(c, q, tx, ty, TXT, NewFont.W_MED, sz);
                caretX = tx + tw(q, NewFont.W_MED, sz);
            }
            if (nameField.isFocused() && popup == 0 && (System.currentTimeMillis() / 500L) % 2L == 0L) {
                int chh = Math.max(6, Math.round(nameR[3] * 0.42f));
                int cy = nameR[1] + (nameR[3] - chh) / 2;
                c.fill(caretX + 1, cy, caretX + 1 + Math.max(1, sl(2)), cy + chh, TXT);
            }
            boolean ch = NewUi.in(mx, my, createR[0], createR[1], createR[2], createR[3]);
            int cc = ch ? FhasixTheme.redHover() : FhasixTheme.red();
            NewUi.rrect(c, createR[0], createR[1], createR[2], createR[3], rad, cc, cc);
            float bsz = fs(16f);
            text(c, "Create Profile", createR[0] + (createR[2] - tw("Create Profile", NewFont.W_BOLD, bsz)) / 2, NewFont.centerYUi(createR[1], createR[3], bsz), TXT, NewFont.W_BOLD, bsz);
        }

        void drawToast(GuiGraphicsExtractor c) {
            long now = System.currentTimeMillis();
            if (toast == null || now >= toastUntil) {
                return;
            }
            float a = Math.min(1f, (toastUntil - now) / 400f);
            float sz = fs(15f);
            int w = tw(toast, NewFont.W_BOLD, sz) + sl(44);
            int h = Math.max(14, sl(38));
            int x = panel[0] + (panel[2] - w) / 2;
            int y = panel[1] + sl(560);
            NewUi.rrect(c, x, y, w, h, h / 2, NewUi.withAlpha(NewUi.rgb(8, 9, 12), 0.94f * a), NewUi.withAlpha(NewUi.rgb(8, 9, 12), 0.94f * a));
            text(c, toast, x + sl(22), NewFont.centerYUi(y, h, sz), NewUi.withAlpha(0xFFFFFFFF, a), NewFont.W_BOLD, sz);
        }

        private void say(String msg) {
            toast = msg;
            toastUntil = System.currentTimeMillis() + 2600L;
        }

        // ---------------------------------------------------------- popups
        void drawPopup(GuiGraphicsExtractor c, int mx, int my) {
            if (popup == 0) {
                return;
            }
            c.fill(0, 0, width, height, 0xB0000000);
            int rad = Math.max(3, sl(12));
            NewUi.rrect(c, pop[0], pop[1], pop[2], pop[3], rad, NewUi.rgb(20, 22, 27), NewUi.rgb(15, 17, 20));
            NewButtonArt.edge(c, pop[0], pop[1], pop[2], pop[3], rad, 0.28f);
            float tsz = fs(22f);
            String title = popup == 1 ? "Share Profile" : "Import Profile";
            text(c, title, pop[0] + sl(32), NewFont.centerYUi(pop[1] + sl(22), sl(44), tsz), TXT, NewFont.W_BOLD, tsz);
            float isz = fs(14.5f);
            String info = popup == 1 ? "Your current mod settings as a code. Send it to a friend - they paste it with Import profile."
                    : "Paste a profile code. It is applied right away and saved as a new profile.";
            List<String> infoLines = wrap(info, NewFont.W_MED, isz, pop[2] - sl(64));
            for (int i = 0; i < infoLines.size(); i++) {
                text(c, infoLines.get(i), pop[0] + sl(32), pop[1] + sl(74) + i * Math.round(isz * 1.55f), TXT_LABEL, NewFont.W_MED, isz);
            }
            int brad = Math.max(3, sl(8));
            if (popup == 1) {
                NewUi.rrect(c, shareBox[0], shareBox[1], shareBox[2], shareBox[3], brad, NewUi.rgb(21, 23, 27), NewUi.rgb(21, 23, 27));
                float csz = fs(14f);
                List<String> lines = wrap(shareCode, NewFont.W_MED, csz, shareBox[2] - sl(28));
                for (int i = 0; i < lines.size() && i < 5; i++) {
                    text(c, lines.get(i), shareBox[0] + sl(14), shareBox[1] + sl(12) + i * Math.round(csz * 1.5f), NewUi.rgb(200, 203, 208), NewFont.W_MED, csz);
                }
            } else {
                int fc = NewUi.rgb(21, 23, 27);
                NewUi.rrect(c, popField[0], popField[1], popField[2], popField[3], brad, fc, fc);
                float csz = fs(15f);
                String q = codeField.getValue();
                int tx = popField[0] + sl(16);
                int ty = NewFont.centerYUi(popField[1], popField[3], csz);
                int caretX = tx;
                if (q.isEmpty()) {
                    text(c, "Paste the code here (Ctrl+V)", tx, ty, 0xFF868990, NewFont.W_MED, csz);
                } else {
                    String shown = q;
                    int maxW = popField[2] - sl(32);
                    while (shown.length() > 1 && tw(shown, NewFont.W_MED, csz) > maxW) {
                        shown = shown.substring(1);
                    }
                    text(c, shown, tx, ty, TXT, NewFont.W_MED, csz);
                    caretX = tx + tw(shown, NewFont.W_MED, csz);
                }
                if (codeField.isFocused() && (System.currentTimeMillis() / 500L) % 2L == 0L) {
                    int chh = Math.max(6, Math.round(popField[3] * 0.5f));
                    int cy = popField[1] + (popField[3] - chh) / 2;
                    c.fill(caretX + 1, cy, caretX + 1 + Math.max(1, sl(2)), cy + chh, TXT);
                }
                if (!popupError.isEmpty()) {
                    text(c, popupError, popField[0], popField[1] + popField[3] + sl(12), NewUi.rgb(226, 92, 92), NewFont.W_BOLD, fs(14.5f));
                }
            }
            boolean bh = NewUi.in(mx, my, popB[0], popB[1], popB[2], popB[3]);
            drawButton(c, popB, popup == 1 ? "Close" : "Cancel", bh);
            boolean ah = NewUi.in(mx, my, popA[0], popA[1], popA[2], popA[3]);
            int ac = ah ? FhasixTheme.redHover() : FhasixTheme.red();
            NewUi.rrect(c, popA[0], popA[1], popA[2], popA[3], brad, ac, ac);
            String yes = popup == 1 ? "Copy Code" : "Apply";
            float bsz = fs(15.5f);
            text(c, yes, popA[0] + (popA[2] - tw(yes, NewFont.W_BOLD, bsz)) / 2, NewFont.centerYUi(popA[1], popA[3], bsz), TXT, NewFont.W_BOLD, bsz);
        }

        private List<String> wrap(String s, int weight, float size, int maxW) {
            List<String> out = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                line.append(s.charAt(i));
                boolean breakHere = tw(line.toString(), weight, size) > maxW && line.length() > 1;
                if (breakHere) {
                    // prefer a break at the last space or dash of the line
                    int cut = Math.max(line.lastIndexOf(" "), line.lastIndexOf("-"));
                    if (cut > 0 && cut < line.length() - 1) {
                        out.add(line.substring(0, cut + 1));
                        line = new StringBuilder(line.substring(cut + 1));
                    } else {
                        out.add(line.substring(0, line.length() - 1));
                        line = new StringBuilder(line.substring(line.length() - 1));
                    }
                }
            }
            if (line.length() > 0) {
                out.add(line.toString());
            }
            return out;
        }

        boolean clickPopup(MouseButtonEvent click, boolean doubled) {
            if (click.buttonInfo().button() != InputConstants.MOUSE_BUTTON_LEFT) {
                return true;
            }
            double mx = click.x();
            double my = click.y();
            if (popup == 2 && super_click(click, doubled)) {
                return true;
            }
            if (NewUi.in(mx, my, popA[0], popA[1], popA[2], popA[3])) {
                NewUi.click(minecraft);
                if (popup == 1) {
                    copyToClipboard(shareCode);
                    say("Code copied");
                } else {
                    applyImport();
                }
            } else if (NewUi.in(mx, my, popB[0], popB[1], popB[2], popB[3])
                    || !NewUi.in(mx, my, pop[0], pop[1], pop[2], pop[3])) {
                NewUi.click(minecraft);
                closePopup();
            }
            return true;
        }

        private boolean super_click(MouseButtonEvent click, boolean doubled) {
            // lets the code box take focus when it is clicked
            return NewSettingsScreen.super.mouseClicked(click, doubled);
        }

        void closePopup() {
            popup = 0;
            codeField.visible = false;
            nameField.visible = true;
            NewSettingsScreen.this.setFocused(null);
        }

        private void openShare() {
            shareCode = FhasixProfiles.encodeCurrent(minecraft);
            copyToClipboard(shareCode);
            popup = 1;
            nameField.visible = false;
            NewSettingsScreen.this.setFocused(null);
            say("Code copied");
        }

        private void openImport() {
            popup = 2;
            popupError = "";
            codeField.setValue("");
            codeField.visible = true;
            nameField.visible = false;
            NewSettingsScreen.this.setFocused(codeField);
        }

        private void applyImport() {
            String err = FhasixProfiles.applyCode(minecraft, codeField.getValue());
            if (err != null) {
                popupError = err;
                return;
            }
            FhasixProfiles.Profile p = FhasixProfiles.add("Imported", FhasixProfiles.encodeCurrent(minecraft));
            FhasixProfiles.setActive(p.name);
            closePopup();
            say("Profile imported and applied");
        }

        private void create() {
            String name = nameField.getValue().trim();
            if (name.isEmpty()) {
                say("Type a profile name first");
                return;
            }
            FhasixProfiles.Profile p = FhasixProfiles.add(name, FhasixProfiles.encodeCurrent(minecraft));
            FhasixProfiles.setActive(p.name);
            nameField.setValue("");
            say("Profile created: " + p.name);
        }

        boolean enterPressed() {
            if (popup == 2) {
                applyImport();
                return true;
            }
            if (popup == 0 && nameField.isFocused()) {
                create();
                return true;
            }
            return false;
        }

        // ---------------------------------------------------------- hit testing / actions
        long hitAt(double mx, double my) {
            if (NewUi.in(mx, my, shareBtnR[0], shareBtnR[1], shareBtnR[2], shareBtnR[3])) {
                return key(K_P_TOPSHARE, 0, 0);
            }
            if (NewUi.in(mx, my, importBtnR[0], importBtnR[1], importBtnR[2], importBtnR[3])) {
                return key(K_P_TOPIMPORT, 0, 0);
            }
            if (NewUi.in(mx, my, createR[0], createR[1], createR[2], createR[3])) {
                return key(K_P_CREATE, 0, 0);
            }
            if (my < viewTop() || my > viewBottom()) {
                return -1L;
            }
            int n = FhasixProfiles.all().size();
            for (int i = 0; i < n; i++) {
                int[] cr = cardRect(i);
                for (int k0 = 0; k0 < 3; k0++) {
                    int[] ir = iconRect(i, k0);
                    if (NewUi.in(mx, my, ir[0], ir[1], ir[2], ir[3])) {
                        return key(K_P_ICON, i, k0);
                    }
                }
                if (NewUi.in(mx, my, cr[0], cr[1], cr[2], cr[3])) {
                    int[] dr = delRect(i);
                    if (NewUi.in(mx, my, dr[0], dr[1], dr[2], dr[3])) {
                        return key(K_P_DEL, i, 0);
                    }
                    return key(K_P_CARD, i, 0);
                }
            }
            return -1L;
        }

        void act(long k0) {
            int kind = (int) (k0 >> 40);
            int a = (int) ((k0 >> 20) & 0xFFFFF);
            int b = (int) (k0 & 0xFFFFF);
            List<FhasixProfiles.Profile> list = FhasixProfiles.all();
            NewUi.click(minecraft);
            if (kind == K_P_TOPSHARE) {
                openShare();
            } else if (kind == K_P_TOPIMPORT) {
                openImport();
            } else if (kind == K_P_CREATE) {
                create();
            } else if (kind == K_P_CARD && a < list.size()) {
                FhasixProfiles.Profile p = list.get(a);
                String err = FhasixProfiles.applyCode(minecraft, p.code);
                if (err == null) {
                    FhasixProfiles.setActive(p.name);
                    say("Profile applied: " + p.name);
                } else {
                    say(err);
                }
            } else if (kind == K_P_ICON && a < list.size()) {
                FhasixProfiles.Profile p = list.get(a);
                if (b == 0) {
                    copyToClipboard(p.code);
                    say("Code copied: " + p.name);
                } else if (b == 1) {
                    FhasixProfiles.Profile d = FhasixProfiles.duplicate(a);
                    say(d == null ? "Could not copy" : "Copied as " + d.name);
                } else {
                    String n = nameField.getValue().trim();
                    if (n.isEmpty()) {
                        say("Type the new name in the box below, then press the pencil");
                    } else if (FhasixProfiles.rename(a, n)) {
                        nameField.setValue("");
                        say("Renamed to " + n);
                    } else {
                        say("That name is already used");
                    }
                }
            } else if (kind == K_P_DEL && a < list.size()) {
                String gone = list.get(a).name;
                FhasixProfiles.remove(a);
                say("Deleted " + gone);
                scrollTarget = Math.min(scrollTarget, maxScroll());
            }
        }
    }
}
