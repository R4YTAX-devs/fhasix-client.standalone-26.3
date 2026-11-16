package com.fhasix.uitheme;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The FHASIX Store, rebuilt to match the reference picture (1502 x 1059): title + logo + daily check-in on top,
 * a row of twelve category tiles, then a panel with search, sorting and a three-column grid of cloak cards.
 * Every rectangle is measured from the picture and scaled by ONE factor, like the Mod Menu. Opening the store
 * or switching a category first shows the grey loading cards, then the real cards fade in.
 *
 * Cloaks tab: five new cloaks. Wings tab: the two existing items (First Optix collab, Red Charms), which keep
 * their old behaviour (Discord invite / four-digit code).
 */
public class StoreScreen extends Screen {
    private final Screen parent;

    // ------------------------------------------------------------------ reference geometry (picture pixels)
    private static final int RW = 1502;
    private static final int RH = 1059;
    private static final int[] PANEL_REF = {79, 243, 1355, 745};
    private static final int TITLE_X = 79;
    private static final int TITLE_CY = 123;
    private static final float LOGO_CX = 756.5f;
    private static final int LOGO_W = 300;
    private static final int LOGO_CY = 89;
    private static final int[] CHECK_REF = {1133, 69, 301, 83};
    private static final int TAB_X0 = 79;
    private static final int TAB_Y = 166;
    private static final int TAB_W = 67;
    private static final int TAB_H = 63;
    private static final int TAB_PITCH = 80;
    private static final int[] POINTS_REF = {1253, 166, 121, 63};
    private static final int[] CLOSE_REF = {1388, 175, 47, 47};
    private static final int[] SEARCH_REF = {104, 266, 783, 39};
    private static final int[] FREE_REF = {899, 266, 164, 39};      // UPDATE 46: Free up memory
    private static final int[] BANNER_REF = {331, 905, 840, 64};     // UPDATE 46: new cosmetics banner
    // Home tab (daily rewards): seven day cards, the streak row, then the closest cosmetics
    private static final int DAY_Y = 266;
    private static final int DAY_H = 144;
    private static final int DAY_W = 169;
    private static final int DAY_PITCH = 189;
    private static final int STREAK_Y = 462;
    private static final int STREAK_H = 65;
    private static final int HOME_ITEMS_Y = 563;
    private static final int[] SORT_REF = {1088, 266, 321, 39};
    private static final int CARD_X0 = 105;
    private static final int CARD_Y0 = 330;
    private static final int CARD_W = 415;
    private static final int CARD_H = 231;
    private static final int PITCH_X = 443;
    private static final int PITCH_Y = 256;
    private static final int COLS = 3;
    private static final int GRID_L = 100;
    private static final int GRID_R = 1412;
    private static final int GRID_T = 322;
    
    // ------------------------------------------------------------------ colours (the same family as the Mod Menu)
    private static final int TXT = 0xFFFFFFFF;
    private static final int TXT_SOFT = 0xFFC9CCD1;
    private static final int TXT_GREY = 0xFF8A8D93;
    private static final int TILE_T = NewUi.rgb(15, 17, 20);
    private static final int TILE_B = NewUi.rgb(13, 15, 18);
    private static final int HOV_TL = NewUi.rgb(54, 62, 74);
    private static final int HOV_BR = NewUi.rgb(33, 38, 46);
    private static final int PANEL_T = NewUi.rgb(15, 17, 20);
    private static final int PANEL_B = NewUi.rgb(14, 16, 19);
    private static final int PANEL_EDGE = NewUi.rgb(23, 26, 31);
    private static final int CARD_T = NewUi.rgb(17, 19, 23);
    private static final int CARD_B = NewUi.rgb(14, 16, 19);
    private static final int GREEN = NewUi.rgb(62, 200, 82);
    private static final int ERR_COLOR = NewUi.rgb(226, 92, 92);

    // kept for the code-entry popup below
    private static final int[] PANEL_TL = {14, 17, 22};
    private static final int[] PANEL_BR = {9, 10, 14};

    // ------------------------------------------------------------------ textures
    private static final String DISCORD_INVITE = "https://discord.com/invite/KdxZAmxnmv";
    private static final Identifier ICONS = Identifier.fromNamespaceAndPath("fhasixuitheme", "textures/gui/store/store_icons.png");
    private static final int ICON_CELL = 64;
    private static final int ICONS_W = 512;
    private static final int ICONS_H = 256;
    private static final int I_PLUS = 12;
    private static final int I_CHEV = 13;
    private static final int I_KEY = 24;
    private static final Identifier GEM = Identifier.fromNamespaceAndPath("fhasixuitheme", "textures/gui/store/store_gem.png");
    private static final int GEM_TEX = 128;
    private static final Identifier WATERMARK = Identifier.fromNamespaceAndPath("fhasixuitheme", "textures/gui/store/store_watermark.png");
    private static final Identifier LOGO = Identifier.fromNamespaceAndPath("fhasixuitheme", "textures/gui/title/fhasix_logo.png");
    private static final int LOGO_TEX_W = 1251;
    private static final int LOGO_TEX_H = 323;
    private static final Identifier FX_CLOSE = Identifier.fromNamespaceAndPath("fhasixuitheme", "textures/gui/fx/fx_close.png");
    private static final int FX_SIZE = 96;

    private static Identifier store(String name) {
        return Identifier.fromNamespaceAndPath("fhasixuitheme", "textures/gui/store/" + name + ".png");
    }

    // ------------------------------------------------------------------ items
    private static final String[] TAB_NAMES = {"Home", "Favorites", "Cloaks", "Wings", "Particles", "Banners",
            "Pets", "Backpacks", "Hats", "Glasses", "Emotes", "Shoes"};
    private static final int TAB_HOME = 0;
    private static final int TAB_CLOAKS = 2;
    private static final int TAB_WINGS = 3;

    private static final int KIND_CLOAK = 0;
    private static final int KIND_OPTIX = 1;
    private static final int KIND_CHARMS = 2;

    private static final class Item {
        final String name;
        final int price;
        final int kind;
        final String capeId;
        final Identifier tex;
        final int tw;
        final int th;
        /** Not null for a cosmetic that comes from the GitHub catalog (UPDATE 46). */
        final FhasixCatalog.Entry remote;

        Item(String name, int price, int kind, String capeId, Identifier tex, int tw, int th) {
            this.name = name;
            this.price = price;
            this.kind = kind;
            this.capeId = capeId;
            this.tex = tex;
            this.tw = tw;
            this.th = th;
            this.remote = null;
        }

        Item(FhasixCatalog.Entry e) {
            this.name = e.storeName;
            this.price = e.price;
            this.kind = KIND_CLOAK;
            this.capeId = e.id;
            this.tex = null;
            this.tw = 360;
            this.th = 600;
            this.remote = e;
        }

        /** The picture to draw, or null while a GitHub icon is still loading. */
        Identifier icon() {
            return remote == null ? tex : FhasixCatalog.icon(remote);
        }

        /** False for a GitHub cosmetic whose cape texture still has to be downloaded to this device. */
        boolean downloaded() {
            return remote == null || FhasixCatalog.capeState(remote) == FhasixCatalog.CAPE_READY;
        }

        boolean owned() {
            return FhasixCosmetics.owns(capeId);
        }
    }

    private static final Map<FhasixCatalog.Entry, Item> REMOTE_ITEMS = new HashMap<>();

    private static Item remoteItem(FhasixCatalog.Entry e) {
        return REMOTE_ITEMS.computeIfAbsent(e, k -> new Item(k));
    }

    private static final Item[] CLOAKS = {
            new Item("CRIMSON FADE CLOAK", 925, KIND_CLOAK, FhasixCosmetics.CRIMSON_FADE, store("cloak_crimson"), 360, 600),
            new Item("CYBER CIRCUIT CLOAK", 925, KIND_CLOAK, FhasixCosmetics.CYBER_CIRCUIT, store("cloak_neon"), 360, 600),
            new Item("VOID RIFT CLOAK", 925, KIND_CLOAK, FhasixCosmetics.VOID_RIFT, store("cloak_void"), 360, 600),
            new Item("AURORA CLOAK", 925, KIND_CLOAK, FhasixCosmetics.AURORA, store("cloak_aurora"), 360, 600),
            new Item("SOLAR FLARE CLOAK", 925, KIND_CLOAK, FhasixCosmetics.SOLAR_FLARE, store("cloak_solar"), 360, 600)
    };
    private static final Item[] WINGS = {
            new Item("FIRST OPTIX (COLLAB) CAPE", 0, KIND_OPTIX, FhasixCosmetics.FIRST_OPTIX, store("first_optix_collab"), 928, 1536),
            new Item("RED CHARMS", 0, KIND_CHARMS, FhasixCosmetics.RED_CHARMS, store("red_charms"), 1263, 1536)
    };
    private static final String[] SORT_LABELS = {"Sorted by relevance", "Price: low to high", "Price: high to low", "Name: A to Z"};

    // ------------------------------------------------------------------ state
    private EditBox searchField;
    private String keepText = "";
    private int tab = TAB_CLOAKS;
    private int sortIdx = 0;
    private boolean sortOpen = false;

    private long loadStart = System.nanoTime();
    private float loadDur = 0.75f;

    private String toast = null;
    private long toastUntil = 0L;
    private Item buying = null; // the buy / sell popup
    private boolean selling = false;
    private int[] codeR;

    private final Map<String, Float> anim = new HashMap<>();
    private long animLast = System.nanoTime();
    private float animDt = 0f;

    // layout (screen pixels)
    private float k;
    private int ox;
    private int oy;
    private int[] panel;
    private int[] checkR;
    private int[] pointsR;
    private int[] closeR;
    private int[] searchR;
    private int[] sortR;
    private int[] freeR;
    private int[] bannerR;
    private boolean bannerDismissed = false; // Cancel hides the banner until the Store is opened again
    private final int[][] tabR = new int[TAB_NAMES.length][];
    private int cardW;
    private int cardH;
    private int pitchX;
    private int pitchY;
    private int gridL;
    private int gridR;
    private int gridT;
    private int gridB;

    private float scrollCur = 0f;
    private float scrollTarget = 0f;
    private long lastNanos = 0L;
    private boolean pressing = false;
    private boolean dragScrolling = false;
    private double travel = 0.0;
    private int pressedItem = -1;

    // click ripple on the category tiles, then the view swaps (same behaviour as the Mod Menu gear tile)
    private long navStart;
    private int[] navRect;
    private int navRad;
    private float navX;
    private float navY;
    private Runnable navAction;
    private static final float NAV_RIPPLE_S = 0.32f;
    private static final float NAV_DELAY_S = 0.20f;

    public StoreScreen(Screen parent) {
        super(Component.literal("Store"));
        this.parent = parent;
        FhasixCatalog.checkIfStale(); // look for new cosmetics on GitHub (throttled)
    }

    // ------------------------------------------------------------------ layout helpers
    private int sx(float v) {
        return ox + Math.round(v * k);
    }

    private int sy(float v) {
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
        checkR = rect(CHECK_REF);
        pointsR = rect(POINTS_REF);
        closeR = rect(CLOSE_REF);
        searchR = rect(SEARCH_REF);
        sortR = rect(SORT_REF);
        freeR = rect(FREE_REF);
        bannerR = rect(BANNER_REF);
        for (int i = 0; i < TAB_NAMES.length; i++) {
            tabR[i] = rect(new int[]{TAB_X0 + i * TAB_PITCH, TAB_Y, TAB_W, TAB_H});
        }
        cardW = sx(CARD_X0 + CARD_W) - sx(CARD_X0);
        cardH = sy(CARD_Y0 + CARD_H) - sy(CARD_Y0);
        pitchX = sx(CARD_X0 + PITCH_X) - sx(CARD_X0);
        pitchY = sy(CARD_Y0 + PITCH_Y) - sy(CARD_Y0);
        gridL = sx(GRID_L);
        gridR = sx(GRID_R);
        gridT = sy(GRID_T);
        gridB = panel[1] + panel[3] - 1;

        int padX = sl(16);
        String keep = searchField != null ? searchField.getValue() : keepText;
        searchField = new EditBox(font, searchR[0] + padX,
                searchR[1] + (searchR[3] - font.lineHeight) / 2,
                Math.max(10, searchR[2] - padX * 2), font.lineHeight + 2, Component.literal("Search"));
        searchField.setMaxLength(40);
        searchField.setBordered(false);
        searchField.setValue(keep);
        // typing and focus only - this screen draws the text and caret itself (smooth font)
        this.addRenderableWidget(searchField);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void closeScreen() {
        minecraft.gui.setScreen(parent);
    }

    // ------------------------------------------------------------------ item list for the current tab
    private List<Item> visibleItems() {
        List<Item> list = new ArrayList<>();
        if (tab == TAB_HOME) {
            return closestItems();
        }
        if (tab == TAB_CLOAKS) {
            for (Item it : CLOAKS) {
                list.add(it);
            }
            for (FhasixCatalog.Entry e : FhasixCatalog.entries()) { // cosmetics from GitHub
                list.add(remoteItem(e));
            }
        } else if (tab == TAB_WINGS) {
            for (Item it : WINGS) {
                list.add(it);
            }
        }
        String q = searchField == null ? "" : searchField.getValue().trim().toLowerCase(Locale.ROOT);
        if (!q.isEmpty()) {
            list.removeIf(it -> !it.name.toLowerCase(Locale.ROOT).contains(q));
        }
        if (sortIdx == 1) {
            list.sort((a, b) -> Integer.compare(a.price, b.price));
        } else if (sortIdx == 2) {
            list.sort((a, b) -> Integer.compare(b.price, a.price));
        } else if (sortIdx == 3) {
            list.sort((a, b) -> a.name.compareTo(b.name));
        }
        return list;
    }

    /** Up to three cosmetics that are closest to being affordable (the ones just above the balance first). */
    private List<Item> closestItems() {
        long bal = FhasixPoints.balance();
        List<Item> pool = new ArrayList<>();
        for (Item it : CLOAKS) {
            if (!it.owned()) {
                pool.add(it);
            }
        }
        pool.sort((a, b) -> Integer.compare(a.price, b.price));
        List<Item> out = new ArrayList<>();
        for (Item it : pool) {
            if (it.price > bal && out.size() < 3) {
                out.add(it);
            }
        }
        for (Item it : pool) {
            if (!out.contains(it) && out.size() < 3) {
                out.add(it);
            }
        }
        return out;
    }

    private float maxScroll(int count) {
        if (tab == TAB_HOME) {
            return 0f;
        }
        int rows = (count + COLS - 1) / COLS;
        if (rows == 0) {
            return 0f;
        }
        int content = (rows - 1) * pitchY + cardH + sl(24);
        return Math.max(0f, content - (gridB - gridT - sl(8)));
    }

    private void restartLoad(float seconds) {
        loadStart = System.nanoTime();
        loadDur = seconds;
        scrollCur = 0f;
        scrollTarget = 0f;
    }

    // ------------------------------------------------------------------ drawing helpers
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

    private String fit(String s, int weight, float size, int maxW) {
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

    private void gem(GuiGraphicsExtractor c, int x, int y, int size) {
        NewUi.tex(c, GEM, x, y, size, GEM_TEX);
    }

    /** A dark tile with the faint corner-lit edge (same as the Mod Menu tiles). */
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
        tickScroll(nowN);

        boolean blocked = (sortOpen && sortListContains(mx, my)) || buying != null;
        int hx = blocked ? -10000 : mx;
        int hy = blocked ? -10000 : my;

        drawHeader(c, hx, hy);
        drawTabs(c, hx, hy);
        NewUi.rrect(c, panel[0], panel[1], panel[2], panel[3], Math.max(3, sl(12)), PANEL_T, PANEL_B);
        NewUi.ring(c, panel[0], panel[1], panel[2], panel[3], Math.max(3, sl(12)), PANEL_EDGE);
        // the Home tab (daily rewards) has no search bar and no sorting
        boolean toolbar = tab != TAB_HOME;
        if (searchField != null) {
            searchField.visible = toolbar;
        }
        if (toolbar) {
            drawToolbar(c, hx, hy);
        } else {
            sortOpen = false;
        }
        drawGrid(c, hx, hy);
        drawSortList(c, mx, my);
        drawTips(c);
        drawBanner(c, hx, hy);
        drawBuyModal(c, mx, my);
        drawToast(c);

        String ver = "Fhasix 1.21.11 (release/1.0.1)";
        float vsz = fs(18f);
        text(c, ver, (width - tw(ver, NewFont.W_MED, vsz)) / 2, sy(1018) - sl(9), NewUi.rgb(104, 106, 112), NewFont.W_MED, vsz);
        drawNavRipple(c);
    }

    private void tickScroll(long now) {
        float dt = lastNanos == 0L ? 0f : Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
        lastNanos = now;
        scrollTarget = Math.max(0f, Math.min(maxScroll(visibleItems().size()), scrollTarget));
        if (pressing && dragScrolling) {
            scrollCur = scrollTarget;
        } else {
            scrollCur += (scrollTarget - scrollCur) * (1f - (float) Math.exp(-18f * dt));
            if (Math.abs(scrollTarget - scrollCur) < 0.3f) {
                scrollCur = scrollTarget;
            }
        }
    }

    private void drawHeader(GuiGraphicsExtractor c, int mx, int my) {
        String title = TAB_NAMES[tab].toUpperCase(Locale.ROOT);
        float tsz = fs(34f);
        text(c, title, sx(TITLE_X), NewFont.centerYUi(sy(TITLE_CY) - sl(24), sl(48), tsz), TXT, NewFont.W_BOLD, tsz);

        int lw = sl(LOGO_W);
        int lh = Math.round(lw * (float) LOGO_TEX_H / LOGO_TEX_W);
        c.blit(RenderPipelines.GUI_TEXTURED, LOGO, sx(LOGO_CX) - lw / 2, sy(LOGO_CY) - lh / 2, 0, 0,
                lw, lh, LOGO_TEX_W, LOGO_TEX_H, LOGO_TEX_W, LOGO_TEX_H);

        // daily reminder: the same dark tile as every other tile, and only while today's reward is still open
        // (click it to open the Home tab with the streak)
        if (FhasixPoints.canClaim()) {
            boolean chk = NewUi.in(mx, my, checkR[0], checkR[1], checkR[2], checkR[3]);
            float chv = ease("checkin", chk ? 1f : 0f, 14f);
            tile(c, checkR, chv, Math.max(3, sl(9)));
            int gs = Math.max(10, sl(44));
            gem(c, checkR[0] + sl(22), checkR[1] + (checkR[3] - gs) / 2 + sl(2), gs);
            float psz = fs(12f);
            text(c, "+" + FhasixPoints.nextReward(), checkR[0] + sl(62), checkR[1] + sl(8), GREEN, NewFont.W_BOLD, psz);
            float tsz2 = fs(17f);
            int txx = checkR[0] + sl(100);
            text(c, "Check in now", txx, NewFont.centerYUi(checkR[1] + sl(18), sl(26), tsz2), TXT_SOFT, NewFont.W_MED, tsz2);
            int y2 = NewFont.centerYUi(checkR[1] + sl(42), sl(26), tsz2);
            String pts = FhasixPoints.nextReward() + " Fhasix Points";
            text(c, "for ", txx, y2, TXT_SOFT, NewFont.W_MED, tsz2);
            int xx = txx + tw("for ", NewFont.W_MED, tsz2);
            text(c, pts, xx, y2, TXT, NewFont.W_BOLD, tsz2);
            xx += tw(pts, NewFont.W_BOLD, tsz2);
            text(c, "!", xx, y2, TXT_SOFT, NewFont.W_MED, tsz2);
        }

        // points balance (grows to fit big numbers) + the small code tile to its left
        int prad = Math.max(3, sl(10));
        String bal = FhasixPoints.compact(FhasixPoints.balance());
        float nsz = fs(25f);
        int pillW = Math.max(pointsR[2], sl(62) + tw(bal, NewFont.W_BOLD, nsz) + sl(26));
        int pillX = pointsR[0] + pointsR[2] - pillW;
        int[] pill = {pillX, pointsR[1], pillW, pointsR[3]};
        tile(c, pill, 0f, prad);
        int pg = Math.max(10, sl(36));
        gem(c, pillX + sl(18), pointsR[1] + (pointsR[3] - pg) / 2, pg);
        text(c, bal, pillX + sl(62), NewFont.centerYUi(pointsR[1], pointsR[3], nsz), TXT, NewFont.W_BOLD, nsz);

        int codeW = sl(104);
        codeR = new int[]{pillX - sl(12) - codeW, pointsR[1], codeW, pointsR[3]};
        boolean cho = NewUi.in(mx, my, codeR[0], codeR[1], codeR[2], codeR[3]);
        float cov = ease("codetile", cho ? 1f : 0f, 14f);
        tile(c, codeR, cov, prad);
        int ks = Math.max(8, sl(24));
        icon(c, I_KEY, codeR[0] + sl(16), codeR[1] + (codeR[3] - ks) / 2, ks, mix(TXT_SOFT, TXT, cov));
        float csz = fs(15f);
        text(c, "CODE", codeR[0] + sl(16) + ks + sl(10), NewFont.centerYUi(codeR[1], codeR[3], csz), mix(TXT_SOFT, TXT, cov), NewFont.W_BOLD, csz);

        // close button: translucent red with a red edge
        boolean ch = NewUi.in(mx, my, closeR[0], closeR[1], closeR[2], closeR[3]);
        float cv = ease("close", ch ? 1f : 0f, 14f);
        int crad = Math.max(3, sl(9));
        int fill = NewUi.withAlpha(FhasixTheme.red(), 0.13f + 0.17f * cv);
        NewUi.rrect(c, closeR[0], closeR[1], closeR[2], closeR[3], crad, fill, fill);
        NewUi.ring(c, closeR[0], closeR[1], closeR[2], closeR[3], crad, mix(FhasixTheme.ring(), FhasixTheme.redHover(), cv));
        int xs = Math.max(8, Math.round(closeR[3] * 0.50f));
        NewUi.tex(c, FX_CLOSE, closeR[0] + (closeR[2] - xs) / 2, closeR[1] + (closeR[3] - xs) / 2, xs, FX_SIZE);
    }

    private void drawTabs(GuiGraphicsExtractor c, int mx, int my) {
        int rad = Math.max(3, sl(10));
        for (int i = 0; i < TAB_NAMES.length; i++) {
            int[] r = tabR[i];
            boolean over = NewUi.in(mx, my, r[0], r[1], r[2], r[3]);
            float hv = ease("tab:" + i, over ? 1f : 0f, 14f);
            ease("tip:" + i, over ? 1f : 0f, 11f);
            if (i == tab) {
                int rc = mix(FhasixTheme.red(), FhasixTheme.redHover(), hv);
                NewUi.rrect(c, r[0], r[1], r[2], r[3], rad, rc, rc);
            } else {
                tile(c, r, hv, rad);
            }
            int s = Math.max(8, Math.round(r[3] * 0.46f));
            icon(c, i, r[0] + (r[2] - s) / 2, r[1] + (r[3] - s) / 2, s, 0xFFFFFFFF);
        }
    }

    /** The small label that fades in above a hovered tile (and fades out when the pointer leaves). */
    private void drawTips(GuiGraphicsExtractor c) {
        float sz = fs(13f);
        for (int i = 0; i < TAB_NAMES.length; i++) {
            Float a = anim.get("tip:" + i);
            float ta = a == null ? 0f : a;
            if (ta < 0.08f) {
                continue;
            }
            int[] r = tabR[i];
            String s = TAB_NAMES[i];
            int w = tw(s, NewFont.W_BOLD, sz);
            int pw = w + sl(22);
            int ph = Math.max(10, sl(24));
            int px = r[0] + (r[2] - pw) / 2;
            int py = r[1] - ph - sl(9);
            NewUi.rrect(c, px, py, pw, ph, Math.max(3, sl(7)), NewUi.withAlpha(NewUi.rgb(12, 14, 17), 0.92f * ta), NewUi.withAlpha(NewUi.rgb(12, 14, 17), 0.92f * ta));
            text(c, s, px + (pw - w) / 2, NewFont.centerYUi(py, ph, sz), NewUi.withAlpha(0xFFFFFFFF, ta), NewFont.W_BOLD, sz);
        }
    }

    // ------------------------------------------------------------------ toolbar + dropdown
    private void drawToolbar(GuiGraphicsExtractor c, int mx, int my) {
        int rad = Math.max(3, sl(8));
        boolean sh = NewUi.in(mx, my, searchR[0], searchR[1], searchR[2], searchR[3]);
        int sc = mix(NewUi.rgb(23, 26, 30), NewUi.rgb(30, 34, 39), ease("search", sh ? 1f : 0f, 14f));
        NewUi.rrect(c, searchR[0], searchR[1], searchR[2], searchR[3], rad, sc, sc);
        float ssz = fs(15f);
        int tx = searchR[0] + sl(16);
        int ty = NewFont.centerYUi(searchR[1], searchR[3], ssz);
        String q = searchField == null ? "" : searchField.getValue();
        int caretX = tx;
        if (q.isEmpty()) {
            text(c, "Search", tx, ty, TXT_GREY, NewFont.W_BOLD, ssz);
        } else {
            String shown = q;
            int maxW = searchR[2] - sl(32);
            while (shown.length() > 1 && tw(shown, NewFont.W_MED, ssz) > maxW) {
                shown = shown.substring(1);
            }
            text(c, shown, tx, ty, TXT, NewFont.W_MED, ssz);
            caretX = tx + tw(shown, NewFont.W_MED, ssz);
        }
        if (searchField != null && searchField.isFocused() && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            int chh = Math.max(6, Math.round(searchR[3] * 0.5f));
            int cy = searchR[1] + (searchR[3] - chh) / 2;
            c.fill(caretX + 1, cy, caretX + 1 + Math.max(1, sl(2)), cy + chh, TXT);
        }

        boolean fh = NewUi.in(mx, my, freeR[0], freeR[1], freeR[2], freeR[3]);
        int fc = mix(NewUi.rgb(23, 26, 30), NewUi.rgb(30, 34, 39), ease("freemem", fh ? 1f : 0f, 14f));
        NewUi.rrect(c, freeR[0], freeR[1], freeR[2], freeR[3], rad, fc, fc);
        String fl = "Free up memory";
        text(c, fl, freeR[0] + (freeR[2] - tw(fl, NewFont.W_BOLD, ssz)) / 2, NewFont.centerYUi(freeR[1], freeR[3], ssz), fh ? TXT : TXT_SOFT, NewFont.W_BOLD, ssz);

        boolean oh = NewUi.in(mx, my, sortR[0], sortR[1], sortR[2], sortR[3]);
        int oc = mix(NewUi.rgb(23, 26, 30), NewUi.rgb(30, 34, 39), ease("sort", oh || sortOpen ? 1f : 0f, 14f));
        NewUi.rrect(c, sortR[0], sortR[1], sortR[2], sortR[3], rad, oc, oc);
        text(c, SORT_LABELS[sortIdx], sortR[0] + sl(13), NewFont.centerYUi(sortR[1], sortR[3], ssz), TXT_GREY, NewFont.W_BOLD, ssz);
        int cs = Math.max(8, sl(13));
        icon(c, I_CHEV, sortR[0] + sortR[2] - sl(16) - cs, sortR[1] + (sortR[3] - cs) / 2, cs, TXT_GREY);
    }

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
            text(c, SORT_LABELS[i], r[0] + sl(14), NewFont.centerYUi(ry, rh, sz), i == sortIdx ? TXT : mix(TXT_GREY, TXT, hv), NewFont.W_BOLD, sz);
        }
    }

    // ------------------------------------------------------------------ grid
    private int cardX(int i) {
        return sx(CARD_X0) + (i % COLS) * pitchX;
    }

    private int cardY(int i) {
        if (tab == TAB_HOME) {
            return sy(HOME_ITEMS_Y);
        }
        return sy(CARD_Y0) + (i / COLS) * pitchY - Math.round(scrollCur);
    }

    private void drawGrid(GuiGraphicsExtractor c, int mx, int my) {
        float since = (System.nanoTime() - loadStart) / 1_000_000_000f;
        boolean skeletonOnly = since < loadDur;
        float reveal = Math.min(1f, Math.max(0f, (since - loadDur) / 0.28f));
        List<Item> items = visibleItems();
        boolean inGrid = NewUi.in(mx, my, gridL, gridT, gridR - gridL, gridB - gridT);

        c.enableScissor(gridL, sy(tab == TAB_HOME ? 250 : GRID_T), gridR, gridB);
        if (skeletonOnly && tab == TAB_HOME) {
            drawHomeSkeleton(c);
        } else if (skeletonOnly) {
            for (int i = 0; i < 6; i++) {
                drawSkeleton(c, cardX(i), cardY(i), 1f);
            }
        } else if (items.isEmpty() && tab != TAB_HOME) {
            float sz = fs(17f);
            String msg = tab == 1 ? "No favorites yet" : (searchField != null && !searchField.getValue().trim().isEmpty() ? "No items match your search" : "Nothing here yet");
            text(c, msg, (gridL + gridR - tw(msg, NewFont.W_MED, sz)) / 2, gridT + sl(110), TXT_GREY, NewFont.W_MED, sz);
        } else {
            if (tab == TAB_HOME) {
                drawHome(c, mx, my);
            }
            for (int i = 0; i < items.size(); i++) {
                int x = cardX(i);
                int y = cardY(i);
                if (y + cardH < gridT || y > gridB) {
                    continue;
                }
                drawCard(c, items.get(i), x, y, inGrid && NewUi.in(mx, my, x, y, cardW, cardH));
            }
            if (reveal < 1f) { // the loading cards fade out over the real ones
                for (int i = 0; i < Math.max(6, items.size()); i++) {
                    drawSkeleton(c, cardX(i), cardY(i), 1f - reveal);
                }
            }
        }
        c.disableScissor();

        float max = maxScroll(items.size());
        if (max > 0f && !skeletonOnly) {
            int track = gridB - gridT;
            int th = Math.max(sl(24), Math.round(track * (track / (track + max))));
            int ty = gridT + Math.round((track - th) * (scrollCur / max));
            int bw = Math.max(2, sl(5));
            NewUi.rrect(c, sx(1424), ty, bw, th, bw / 2, NewUi.rgb(88, 89, 92), NewUi.rgb(80, 81, 84));
        }
    }

    private void drawSkeleton(GuiGraphicsExtractor c, int x, int y, float alpha) {
        int rad = Math.max(3, sl(7));
        double t = System.nanoTime() / 1_000_000_000.0;
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 3.0);
        int body = NewUi.withAlpha(mix(NewUi.rgb(90, 91, 93), NewUi.rgb(100, 101, 103), pulse), alpha);
        int bar = NewUi.withAlpha(mix(NewUi.rgb(124, 125, 127), NewUi.rgb(136, 137, 139), pulse), alpha);
        NewUi.rrect(c, x, y, cardW, cardH, rad, body, body);
        int br = Math.max(2, sl(3));
        int right = x + cardW - sl(15);
        NewUi.rrect(c, right - sl(88), y + sl(177), sl(88), sl(15), br, bar, bar);
        NewUi.rrect(c, right - sl(124), y + sl(197), sl(124), sl(19), br, bar, bar);
    }

    private void drawCard(GuiGraphicsExtractor c, Item it, int x, int y, boolean over) {
        int rad = Math.max(3, sl(7));
        float hv = ease("card:" + it.name, over ? 1f : 0f, 14f);
        NewUi.rrect(c, x, y, cardW, cardH, rad, mix(CARD_T, NewUi.rgb(60, 68, 80), hv), mix(CARD_B, NewUi.rgb(37, 42, 50), hv));

        // big faint F behind the item, cut off by the card
        c.enableScissor(x, y, x + cardW, y + cardH);
        int wm = sl(330);
        c.blit(RenderPipelines.GUI_TEXTURED, WATERMARK, x + sl(58), y - sl(48), 0, 0, wm, wm, 256, 256, 256, 256,
                (Math.round(255f * (0.13f + 0.12f * hv)) << 24) | 0xFFFFFF);
        int ps = Math.max(6, sl(15));
        int pc = (Math.round(255f * 0.22f) << 24) | 0xFFFFFF;
        icon(c, I_PLUS, x + sl(260) - ps / 2, y + sl(105) - ps / 2, ps, pc);
        icon(c, I_PLUS, x + sl(165) - ps / 2, y + sl(160) - ps / 2, ps, pc);
        icon(c, I_PLUS, x + sl(274) - ps / 2, y + sl(154) - ps / 2, ps, pc);
        c.disableScissor();

        // the item picture, centred in the card
        int cx = x + cardW / 2;
        Identifier itex = it.icon();
        if (it.kind == KIND_CLOAK) {
            int ih = sl(224);
            int iw = Math.round(ih * (float) it.tw / it.th);
            if (itex != null) {
                c.blit(RenderPipelines.GUI_TEXTURED, itex, cx - iw / 2 - sl(3), y + sl(11), 0, 0, iw, ih, it.tw, it.th, it.tw, it.th);
            } else {
                FhasixCatalog.spinner(c, cx - sl(44), y + sl(11) + ih / 2 - sl(44), sl(88)); // icon still loading
            }
        } else {
            int maxH = sl(196);
            int maxW = sl(190);
            int ih = maxH;
            int iw = Math.round(ih * (float) it.tw / it.th);
            if (iw > maxW) {
                iw = maxW;
                ih = Math.round(iw * (float) it.th / it.tw);
            }
            if (itex != null) {
                c.blit(RenderPipelines.GUI_TEXTURED, itex, cx - iw / 2, y + sl(112) - ih / 2, 0, 0, iw, ih, it.tw, it.th, it.tw, it.th);
            }
        }

        // price (or how to get it) and name, bottom right
        int right = x + cardW - sl(15);
        float psz = fs(11.5f);
        int priceCy = y + sl(182);
        if (it.owned()) {
            String own = "OWNED";
            int ow = tw(own, NewFont.W_BOLD, psz);
            text(c, own, right - ow, NewFont.centerYUi(priceCy - sl(9), sl(18), psz), GREEN, NewFont.W_BOLD, psz);
        } else if (it.kind == KIND_CLOAK) {
            String price = FhasixPoints.format(it.price);
            int pw = tw(price, NewFont.W_BOLD, psz);
            text(c, price, right - pw, NewFont.centerYUi(priceCy - sl(9), sl(18), psz), TXT_SOFT, NewFont.W_BOLD, psz);
            int gs = Math.max(6, sl(13));
            gem(c, right - pw - sl(5) - gs, priceCy - gs / 2, gs);
            if (tab == TAB_HOME) { // how close the balance is to this cloak
                long bal = FhasixPoints.balance();
                boolean ready = bal >= it.price;
                String hint = ready ? "Ready to buy!" : FhasixPoints.format(it.price - bal) + " points to go";
                text(c, hint, x + sl(15), NewFont.centerYUi(priceCy - sl(9), sl(18), psz), ready ? GREEN : TXT_GREY, NewFont.W_BOLD, psz);
            }
        } else {
            String how = it.kind == KIND_OPTIX ? "JOIN DC" : "ENTER CODE";
            int hw = tw(how, NewFont.W_BOLD, psz);
            text(c, how, right - hw, NewFont.centerYUi(priceCy - sl(9), sl(18), psz), mix(TXT_GREY, TXT, hv), NewFont.W_BOLD, psz);
        }
        float nsz = fs(16.5f);
        String name = fit(it.name, NewFont.W_BOLD, nsz, cardW - sl(36));
        int nw = tw(name, NewFont.W_BOLD, nsz);
        text(c, name, right - nw, NewFont.centerYUi(y + sl(204) - sl(11), sl(22), nsz), TXT, NewFont.W_BOLD, nsz);

        NewButtonArt.edge(c, x, y, cardW, cardH, rad, 0.20f + 0.08f * hv);
    }

    // ------------------------------------------------------------------ Home page (daily rewards)
    private int[] dayRect(int i) {
        int x0 = sx(CARD_X0 + i * DAY_PITCH);
        int y0 = sy(DAY_Y);
        return new int[]{x0, y0, sx(CARD_X0 + i * DAY_PITCH + DAY_W) - x0, sy(DAY_Y + DAY_H) - y0};
    }

    private int[] claimRect() {
        int x0 = sx(565);
        int y0 = sy(STREAK_Y);
        return new int[]{x0, y0, sx(1406) - x0, sy(STREAK_Y + STREAK_H) - y0};
    }

    private void drawHome(GuiGraphicsExtractor c, int mx, int my) {
        int collected = FhasixPoints.collectedInCycle();
        boolean can = FhasixPoints.canClaim();
        int rad = Math.max(3, sl(8));
        for (int i = 0; i < FhasixPoints.REWARDS.length; i++) {
            int[] r = dayRect(i);
            boolean claimed = i < collected;
            boolean active = i == collected && can;
            int top = active ? NewUi.rgb(34, 40, 48) : (claimed ? NewUi.rgb(19, 22, 26) : NewUi.rgb(14, 16, 19));
            int bot = active ? NewUi.rgb(22, 26, 32) : (claimed ? NewUi.rgb(15, 17, 21) : NewUi.rgb(12, 14, 17));
            NewUi.rrect(c, r[0], r[1], r[2], r[3], rad, top, bot);
            NewButtonArt.edge(c, r[0], r[1], r[2], r[3], rad, active ? 0.38f : 0.18f);
            int gs = Math.max(10, sl(78));
            int tint = (claimed || active) ? 0xFFFFFFFF : 0x62FFFFFF;
            c.blit(RenderPipelines.GUI_TEXTURED, GEM, r[0] + (r[2] - gs) / 2, r[1] + sl(24), 0, 0, gs, gs,
                    GEM_TEX, GEM_TEX, GEM_TEX, GEM_TEX, tint);
            float rsz = fs(13.5f);
            String plus = "+" + FhasixPoints.REWARDS[i];
            text(c, plus, r[0] + r[2] - sl(14) - tw(plus, NewFont.W_BOLD, rsz), r[1] + sl(12), (claimed || active) ? GREEN : NewUi.rgb(70, 104, 78), NewFont.W_BOLD, rsz);
            float lsz = fs(15f);
            String day = "Day " + (i + 1);
            text(c, day, r[0] + (r[2] - tw(day, NewFont.W_BOLD, lsz)) / 2, NewFont.centerYUi(r[1] + r[3] - sl(36), sl(26), lsz),
                    active ? TXT : (claimed ? TXT_SOFT : TXT_GREY), NewFont.W_BOLD, lsz);
            if (claimed || active) {
                float ssz = fs(10.5f);
                text(c, claimed ? "CLAIMED" : "TODAY", r[0] + sl(14), r[1] + sl(12), GREEN, NewFont.W_BOLD, ssz);
            }
        }

        // streak + claim button
        int[] cr = claimRect();
        float ssz = fs(21f);
        String best = "Highest Streak: " + FhasixPoints.bestStreak();
        int blockL = sx(CARD_X0);
        int blockR = sx(545);
        text(c, best, blockL + (blockR - blockL - tw(best, NewFont.W_BOLD, ssz)) / 2, NewFont.centerYUi(cr[1], cr[3], ssz), TXT, NewFont.W_BOLD, ssz);
        boolean over = can && NewUi.in(mx, my, cr[0], cr[1], cr[2], cr[3]);
        float hv = ease("claim", over ? 1f : 0f, 14f);
        int fill = can ? mix(FhasixTheme.red(), FhasixTheme.redHover(), hv) : NewUi.rgb(26, 29, 34);
        NewUi.rrect(c, cr[0], cr[1], cr[2], cr[3], rad, fill, fill);
        if (!can) {
            NewButtonArt.edge(c, cr[0], cr[1], cr[2], cr[3], rad, 0.18f);
        }
        float bsz = fs(19f);
        String label = can ? "Claim Daily Reward" : "Come back tomorrow";
        text(c, label, cr[0] + (cr[2] - tw(label, NewFont.W_BOLD, bsz)) / 2, NewFont.centerYUi(cr[1], cr[3], bsz), can ? TXT : TXT_GREY, NewFont.W_BOLD, bsz);
    }

    private void drawHomeSkeleton(GuiGraphicsExtractor c) {
        double t = System.nanoTime() / 1_000_000_000.0;
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 3.0);
        int body = mix(NewUi.rgb(90, 91, 93), NewUi.rgb(100, 101, 103), pulse);
        int rad = Math.max(3, sl(8));
        for (int i = 0; i < FhasixPoints.REWARDS.length; i++) {
            int[] r = dayRect(i);
            NewUi.rrect(c, r[0], r[1], r[2], r[3], rad, body, body);
        }
        int[] cr = claimRect();
        NewUi.rrect(c, cr[0], cr[1], cr[2], cr[3], rad, body, body);
        for (int i = 0; i < 3; i++) {
            drawSkeleton(c, cardX(i), sy(HOME_ITEMS_Y), 1f);
        }
    }

    // ------------------------------------------------------------------ new cosmetics banner (UPDATE 46)
    /** Checking GitHub, or the check failed, while no catalog has ever been loaded (the banner explains it). */
    private boolean catalogProblem() {
        return FhasixCatalog.entries().isEmpty() && !FhasixCatalog.updateAvailable()
                && (FhasixCatalog.checking() || FhasixCatalog.checkFailed());
    }

    /** The banner shows a spinner and no buttons. */
    private boolean bannerBusy() {
        return FhasixCatalog.updating() || (FhasixCatalog.checking() && !FhasixCatalog.updateAvailable());
    }

    private boolean bannerOn() {
        return !bannerDismissed && (FhasixCatalog.updateAvailable() || FhasixCatalog.updating() || catalogProblem());
    }

    private int[] bannerButton(boolean update) {
        int bh = Math.max(14, bannerR[3] - sl(20));
        int bw = sl(update ? 150 : 128);
        int gap = sl(14);
        int right = bannerR[0] + bannerR[2] - sl(14);
        int x = update ? right - bw - gap - sl(128) : right - sl(128);
        return new int[]{x, bannerR[1] + (bannerR[3] - bh) / 2, bw, bh};
    }

    private void drawBanner(GuiGraphicsExtractor c, int mx, int my) {
        if (!bannerOn() || buying != null) {
            return;
        }
        int rad = Math.max(3, sl(10));
        NewUi.rrect(c, bannerR[0], bannerR[1], bannerR[2], bannerR[3], rad, NewUi.rgb(21, 23, 28), NewUi.rgb(15, 17, 20));
        NewButtonArt.edge(c, bannerR[0], bannerR[1], bannerR[2], bannerR[3], rad, 0.30f);
        float tsz = fs(17f);
        int tx = bannerR[0] + sl(22);
        if (bannerBusy()) {
            int ss = Math.max(10, sl(34));
            FhasixCatalog.spinner(c, tx - sl(4), bannerR[1] + (bannerR[3] - ss) / 2, ss);
            String t = FhasixCatalog.updating()
                    ? "Updating store...  " + FhasixCatalog.updateDone() + " / " + Math.max(1, FhasixCatalog.updateTotal())
                    : "Checking for new cosmetics...";
            text(c, t, tx + ss + sl(8), NewFont.centerYUi(bannerR[1], bannerR[3], tsz), TXT, NewFont.W_BOLD, tsz);
            return;
        }
        int n = FhasixCatalog.pendingCount();
        boolean checkFail = catalogProblem() && FhasixCatalog.checkFailed();
        String msg = checkFail ? "Can't reach the cosmetics catalog (" + FhasixCatalog.checkError() + ")"
                : FhasixCatalog.updateFailed() ? "Update failed - check your connection"
                : (n > 0 ? n + (n == 1 ? " new cosmetic available" : " new cosmetics available") : "Store update available");
        int dot = Math.max(5, sl(10));
        NewUi.rrect(c, tx, bannerR[1] + (bannerR[3] - dot) / 2, dot, dot, dot / 2, FhasixTheme.red(), FhasixTheme.red());
        text(c, fit(msg, NewFont.W_BOLD, tsz, bannerButton(true)[0] - tx - dot - sl(30)), tx + dot + sl(12),
                NewFont.centerYUi(bannerR[1], bannerR[3], tsz), TXT, NewFont.W_BOLD, tsz);
        int[] ur = bannerButton(true);
        int[] cr = bannerButton(false);
        float bsz = fs(16f);
        boolean uh = NewUi.in(mx, my, ur[0], ur[1], ur[2], ur[3]);
        int uf = mix(FhasixTheme.red(), FhasixTheme.redHover(), ease("bannerup", uh ? 1f : 0f, 14f));
        NewUi.rrect(c, ur[0], ur[1], ur[2], ur[3], Math.max(3, sl(8)), uf, uf);
        String ul = (checkFail || FhasixCatalog.updateFailed()) ? "Retry" : "Update";
        text(c, ul, ur[0] + (ur[2] - tw(ul, NewFont.W_BOLD, bsz)) / 2, NewFont.centerYUi(ur[1], ur[3], bsz), TXT, NewFont.W_BOLD, bsz);
        boolean ch = NewUi.in(mx, my, cr[0], cr[1], cr[2], cr[3]);
        tile(c, cr, ease("bannercancel", ch ? 1f : 0f, 14f), Math.max(3, sl(8)));
        text(c, "Cancel", cr[0] + (cr[2] - tw("Cancel", NewFont.W_BOLD, bsz)) / 2, NewFont.centerYUi(cr[1], cr[3], bsz), TXT_SOFT, NewFont.W_BOLD, bsz);
    }

    // ------------------------------------------------------------------ purchase popup
    private int[] buyRect() {
        int w = sl(600);
        int h = sl(330);
        return new int[]{(width - w) / 2, (height - h) / 2, w, h};
    }

    private int[] buyButton(boolean buyButton) {
        int[] r = buyRect();
        int bw = sl(240);
        int bh = sl(58);
        int gap = sl(28);
        int x0 = r[0] + (r[2] - (bw * 2 + gap)) / 2;
        return new int[]{x0 + (buyButton ? bw + gap : 0), r[1] + r[3] - bh - sl(32), bw, bh};
    }

    private boolean canAfford(Item it) {
        return FhasixPoints.balance() >= it.price;
    }

    /** Whether the popup's main button can be used right now. */
    private boolean canConfirm() {
        if (buying == null) {
            return false;
        }
        return selling ? FhasixPoints.salesLeft() > 0 : canAfford(buying);
    }

    private void drawBuyModal(GuiGraphicsExtractor c, int mx, int my) {
        if (buying == null) {
            return;
        }
        c.fill(0, 0, width, height, 0xB0000000);
        int[] r = buyRect();
        int rad = Math.max(3, sl(12));
        NewUi.rrect(c, r[0], r[1], r[2], r[3], rad, NewUi.rgb(20, 22, 27), NewUi.rgb(15, 17, 20));
        NewButtonArt.edge(c, r[0], r[1], r[2], r[3], rad, 0.28f);
        int cx = r[0] + r[2] / 2;
        float t1 = fs(26f);
        String head = selling ? "Sell this cloak?" : "Buy this cloak?";
        text(c, head, cx - tw(head, NewFont.W_BOLD, t1) / 2, NewFont.centerYUi(r[1] + sl(22), sl(44), t1), TXT, NewFont.W_BOLD, t1);
        float t2 = fs(19f);
        String name = fit(buying.name, NewFont.W_BOLD, t2, r[2] - sl(60));
        text(c, name, cx - tw(name, NewFont.W_BOLD, t2) / 2, NewFont.centerYUi(r[1] + sl(72), sl(34), t2), TXT_SOFT, NewFont.W_BOLD, t2);
        float t3 = fs(32f);
        long shown = selling ? FhasixPoints.sellValue(buying.price) : buying.price;
        String price = (selling ? "+" : "") + FhasixPoints.format(shown);
        int pw = tw(price, NewFont.W_BOLD, t3);
        int gs = Math.max(10, sl(46));
        int gx = cx - (gs + sl(10) + pw) / 2;
        gem(c, gx, r[1] + sl(122), gs);
        text(c, price, gx + gs + sl(10), NewFont.centerYUi(r[1] + sl(122), gs, t3), selling ? GREEN : TXT, NewFont.W_BOLD, t3);
        float t4 = fs(16.5f);
        boolean ok = canConfirm();
        if (selling) {
            String fee = "Store price " + FhasixPoints.format(buying.price) + " - " + FhasixPoints.SELL_PENALTY + " sell fee";
            text(c, fee, cx - tw(fee, NewFont.W_MED, t4) / 2, NewFont.centerYUi(r[1] + sl(180), sl(26), t4), TXT_GREY, NewFont.W_MED, t4);
            int left = FhasixPoints.salesLeft();
            String info = left > 0 ? "Sales left this week: " + left + " / " + FhasixPoints.SELL_LIMIT
                    : "Weekly sell limit reached - it resets on Monday";
            text(c, info, cx - tw(info, NewFont.W_BOLD, t4) / 2, NewFont.centerYUi(r[1] + sl(208), sl(26), t4), left > 0 ? TXT_SOFT : ERR_COLOR, NewFont.W_BOLD, t4);
        } else {
            String have = "Your points: " + FhasixPoints.format(FhasixPoints.balance());
            text(c, have, cx - tw(have, NewFont.W_MED, t4) / 2, NewFont.centerYUi(r[1] + sl(180), sl(26), t4), TXT_GREY, NewFont.W_MED, t4);
            if (!ok) {
                String need = "You need " + FhasixPoints.format(buying.price - FhasixPoints.balance()) + " more points";
                text(c, need, cx - tw(need, NewFont.W_BOLD, t4) / 2, NewFont.centerYUi(r[1] + sl(208), sl(26), t4), ERR_COLOR, NewFont.W_BOLD, t4);
            }
        }
        int[] cancel = buyButton(false);
        int[] buy = buyButton(true);
        boolean ch = NewUi.in(mx, my, cancel[0], cancel[1], cancel[2], cancel[3]);
        tile(c, cancel, ease("buycancel", ch ? 1f : 0f, 14f), Math.max(3, sl(9)));
        float bsz = fs(18f);
        text(c, "Cancel", cancel[0] + (cancel[2] - tw("Cancel", NewFont.W_BOLD, bsz)) / 2, NewFont.centerYUi(cancel[1], cancel[3], bsz), TXT_SOFT, NewFont.W_BOLD, bsz);
        boolean bh = ok && NewUi.in(mx, my, buy[0], buy[1], buy[2], buy[3]);
        int bf = ok ? mix(FhasixTheme.red(), FhasixTheme.redHover(), ease("buybuy", bh ? 1f : 0f, 14f)) : NewUi.rgb(34, 37, 43);
        NewUi.rrect(c, buy[0], buy[1], buy[2], buy[3], Math.max(3, sl(9)), bf, bf);
        String yes = selling ? "Sell" : "Buy";
        text(c, yes, buy[0] + (buy[2] - tw(yes, NewFont.W_BOLD, bsz)) / 2, NewFont.centerYUi(buy[1], buy[3], bsz), ok ? TXT : TXT_GREY, NewFont.W_BOLD, bsz);
    }

    private void confirmPurchase() {
        Item it = buying;
        boolean sell = selling;
        buying = null;
        selling = false;
        if (it == null) {
            return;
        }
        if (sell) {
            long gain = FhasixPoints.sell(it.price);
            if (gain >= 0L) {
                FhasixCosmetics.revoke(it.capeId);
                toast = "Sold " + it.name + " for " + FhasixPoints.format(gain) + " points";
                toastUntil = System.currentTimeMillis() + 3200L;
            } else {
                toast = "Weekly sell limit reached";
                toastUntil = System.currentTimeMillis() + 2200L;
            }
        } else if (FhasixPoints.spend(it.price)) {
            FhasixCosmetics.unlock(it.capeId);
            if (it.remote != null) {
                FhasixCatalog.downloadCape(it.remote, false); // only the cosmetic that was bought is downloaded
            }
            toast = it.remote != null ? "Unlocked " + it.name + " - downloading it now" : "Unlocked " + it.name + " - equip it in Cosmetics";
            toastUntil = System.currentTimeMillis() + 3200L;
        } else {
            toast = "Not enough points";
            toastUntil = System.currentTimeMillis() + 2200L;
        }
    }

    private void drawToast(GuiGraphicsExtractor c) {
        if (FhasixCatalog.consumeApplied()) {
            int n = FhasixCatalog.lastAppliedNew();
            toast = n > 0 ? "Store updated - " + n + (n == 1 ? " new cosmetic" : " new cosmetics") : "Store updated";
            toastUntil = System.currentTimeMillis() + 3200L;
        }
        long now = System.currentTimeMillis();
        if (toast == null || now >= toastUntil) {
            return;
        }
        float a = Math.min(1f, (toastUntil - now) / 400f);
        float sz = fs(16f);
        int w = tw(toast, NewFont.W_BOLD, sz) + sl(44);
        int h = Math.max(14, sl(40));
        int x = (width - w) / 2;
        int y = panel[1] + panel[3] - h - sl(26) - (bannerOn() ? sl(84) : 0);
        NewUi.rrect(c, x, y, w, h, h / 2, NewUi.withAlpha(NewUi.rgb(10, 12, 15), 0.94f * a), NewUi.withAlpha(NewUi.rgb(10, 12, 15), 0.94f * a));
        text(c, toast, x + sl(22), NewFont.centerYUi(y, h, sz), NewUi.withAlpha(0xFFFFFFFF, a), NewFont.W_BOLD, sz);
    }

    // ------------------------------------------------------------------ input
    private int itemAt(double mx, double my) {
        if (!NewUi.in(mx, my, gridL, gridT, gridR - gridL, gridB - gridT)) {
            return -1;
        }
        if ((System.nanoTime() - loadStart) / 1_000_000_000f < loadDur) {
            return -1;
        }
        int n = visibleItems().size();
        for (int i = 0; i < n; i++) {
            if (NewUi.in(mx, my, cardX(i), cardY(i), cardW, cardH)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (buying != null) { // the purchase popup swallows every click
            if (click.buttonInfo().button() == InputConstants.MOUSE_BUTTON_LEFT) {
                double bx = click.x();
                double by = click.y();
                int[] buy = buyButton(true);
                int[] cancel = buyButton(false);
                int[] box = buyRect();
                if (canConfirm() && NewUi.in(bx, by, buy[0], buy[1], buy[2], buy[3])) {
                    NewUi.click(minecraft);
                    confirmPurchase();
                } else if (NewUi.in(bx, by, cancel[0], cancel[1], cancel[2], cancel[3])
                        || !NewUi.in(bx, by, box[0], box[1], box[2], box[3])) {
                    NewUi.click(minecraft);
                    buying = null;
                }
            }
            return true;
        }
        if (super.mouseClicked(click, doubled)) {
            return true;
        }
        if (click.buttonInfo().button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return true;
        }
        double mx = click.x();
        double my = click.y();
        if (bannerOn() && NewUi.in(mx, my, bannerR[0], bannerR[1], bannerR[2], bannerR[3])) {
            if (!bannerBusy()) {
                if (NewUi.in(mx, my, bannerButton(true)[0], bannerButton(true)[1], bannerButton(true)[2], bannerButton(true)[3])) {
                    NewUi.click(minecraft);
                    if (catalogProblem()) {
                        FhasixCatalog.recheck();
                    } else {
                        FhasixCatalog.clearFailure();
                        FhasixCatalog.applyUpdate(minecraft);
                    }
                } else if (NewUi.in(mx, my, bannerButton(false)[0], bannerButton(false)[1], bannerButton(false)[2], bannerButton(false)[3])) {
                    NewUi.click(minecraft);
                    bannerDismissed = true;
                }
            }
            return true;
        }
        if (tab != TAB_HOME && NewUi.in(mx, my, freeR[0], freeR[1], freeR[2], freeR[3])) {
            NewUi.click(minecraft);
            long freed = FhasixCatalog.freeMemory(minecraft);
            toast = freed > 0L ? "Freed " + FhasixCatalog.sizeText(freed) + " of cosmetic memory" : "Nothing to free";
            toastUntil = System.currentTimeMillis() + 2800L;
            return true;
        }
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
                        restartLoad(0.35f);
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
        if (tab != TAB_HOME && NewUi.in(mx, my, sortR[0], sortR[1], sortR[2], sortR[3])) {
            NewUi.click(minecraft);
            sortOpen = true;
            return true;
        }
        if (FhasixPoints.canClaim() && NewUi.in(mx, my, checkR[0], checkR[1], checkR[2], checkR[3])) {
            NewUi.click(minecraft);
            if (tab != TAB_HOME) {
                tab = TAB_HOME;
                restartLoad(0.45f);
            }
            return true;
        }
        if (codeR != null && NewUi.in(mx, my, codeR[0], codeR[1], codeR[2], codeR[3])) {
            NewUi.click(minecraft);
            minecraft.gui.setScreen(new PointsCodeScreen(this));
            return true;
        }
        if (tab == TAB_HOME && FhasixPoints.canClaim() && (System.nanoTime() - loadStart) / 1_000_000_000f >= loadDur) {
            int[] cr = claimRect();
            if (NewUi.in(mx, my, cr[0], cr[1], cr[2], cr[3])) {
                NewUi.click(minecraft);
                int got = FhasixPoints.claim();
                if (got > 0) {
                    toast = "+" + got + " Fhasix Points claimed!";
                    toastUntil = System.currentTimeMillis() + 2600L;
                }
                return true;
            }
        }
        for (int i = 0; i < TAB_NAMES.length; i++) {
            int[] r = tabR[i];
            if (NewUi.in(mx, my, r[0], r[1], r[2], r[3])) {
                if (i != tab && navAction == null) {
                    NewUi.click(minecraft);
                    final int target = i;
                    navRipple(r, Math.max(3, sl(10)), mx, my, () -> {
                        tab = target;
                        restartLoad(0.55f);
                    });
                }
                return true;
            }
        }
        if (NewUi.in(mx, my, gridL, gridT, gridR - gridL, gridB - gridT)) {
            pressing = true;
            dragScrolling = false;
            travel = 0.0;
            pressedItem = itemAt(mx, my);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        if (pressing) {
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
        if (pressing) {
            pressing = false;
            boolean wasDrag = dragScrolling;
            dragScrolling = false;
            int pressed = pressedItem;
            pressedItem = -1;
            if (!wasDrag && pressed >= 0 && itemAt(click.x(), click.y()) == pressed) {
                List<Item> items = visibleItems();
                if (pressed < items.size()) {
                    activate(items.get(pressed));
                }
            }
            return true;
        }
        return super.mouseReleased(click);
    }

    private void activate(Item it) {
        NewUi.click(minecraft);
        if (it.owned()) {
            if (it.kind == KIND_CLOAK) { // store cloaks can be sold back (see FhasixPoints.SELL_LIMIT)
                buying = it;
                selling = true;
            } else {
                toast = "You own this - equip it in Cosmetics";
                toastUntil = System.currentTimeMillis() + 2400L;
            }
        } else if (it.kind == KIND_OPTIX) {
            // The Optix collaboration is delivered to the Cosmetics inventory after the Store acquisition
            // flow, then the player is taken straight there.
            FhasixCosmetics.unlockFirstOptix();
            OpenUri.open(DISCORD_INVITE);
            minecraft.gui.setScreen(new CosmeticsScreen(parent));
        } else if (it.kind == KIND_CHARMS) {
            minecraft.gui.setScreen(new CodeEntryScreen(this));
        } else {
            buying = it;
            selling = false;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (buying != null) {
            return true;
        }
        scrollTarget -= (float) verticalAmount * 40f;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (keyEvent.key() == InputConstants.KEY_ESCAPE) {
            if (buying != null) {
                buying = null;
                return true;
            }
            if (sortOpen) {
                sortOpen = false;
                return true;
            }
            closeScreen();
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    private static class CodeEntryScreen extends Screen {
        private final StoreScreen parent;
        private net.minecraft.client.gui.components.EditBox codeField;
        private String status = "";

        protected CodeEntryScreen(StoreScreen parent) {
            super(Component.literal("Enter Code"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            int fieldW = Math.max(180, Math.round(width * 0.24f));
            int fieldH = Math.max(24, Math.round(height * 0.045f));
            int fieldX = (width - fieldW) / 2;
            int fieldY = height / 2 - fieldH;
            codeField = new net.minecraft.client.gui.components.EditBox(font, fieldX, fieldY, fieldW, fieldH, Component.literal("Code"));
            codeField.setMaxLength(4);
            codeField.setResponder(value -> {
                if (!value.matches("\\d{0,4}")) {
                    codeField.setValue(value.replaceAll("\\D", ""));
                }
            });
            this.addRenderableWidget(codeField);
            codeField.setFocused(true);

            int buttonW = Math.max(110, Math.round(width * 0.15f));
            int buttonH = Math.max(24, Math.round(height * 0.045f));
            this.addRenderableWidget(Button.builder(Component.literal("UNLOCK"), button -> submit())
                    .bounds((width - buttonW) / 2, fieldY + fieldH + 12, buttonW, buttonH)
                    .build());
        }

        private void submit() {
            if ("6241".equals(codeField.getValue())) {
                FhasixCosmetics.unlockSecondCape();
                minecraft.gui.setScreen(new CosmeticsScreen(parent.parent));
            } else {
                status = "Invalid code";
            }
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            parent.extractRenderState(context, mouseX, mouseY, delta);
            context.fill(0, 0, width, height, 0x88000000);
            int w = Math.round(width * 0.40f);
            int h = Math.round(height * 0.30f);
            int x = (width - w) / 2;
            int y = (height - h) / 2;
            a1.drawRoundedGradientRect(context, x, y, w, h, PANEL_TL, PANEL_BR, 250);
            NewFont.drawCentered(context, font, "ENTER CODE", width / 2, y + 28, 0xFFFFFFFF);
            NewFont.drawCentered(context, font, "Enter your 4-digit code to unlock the cape", width / 2, y + 48, 0xFFBFC2C6);
            if (!status.isEmpty()) {
                NewFont.drawCentered(context, font, status, width / 2, y + h - 26, 0xFFFF6B6B);
            }
            super.extractRenderState(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean keyPressed(KeyEvent keyEvent) {
            if (keyEvent.key() == InputConstants.KEY_ESCAPE) {
                minecraft.gui.setScreen(parent);
                return true;
            }
            if (keyEvent.key() == InputConstants.KEY_RETURN || keyEvent.key() == InputConstants.KEY_NUMPADENTER) {
                submit();
                return true;
            }
            return super.keyPressed(keyEvent);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    /** Asks for the 12-digit points code (see FhasixPoints). */
    private static class PointsCodeScreen extends Screen {
        private final StoreScreen parent;
        private net.minecraft.client.gui.components.EditBox codeField;
        private String status = "";

        protected PointsCodeScreen(StoreScreen parent) {
            super(Component.literal("Enter Code"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            int fieldW = Math.max(200, Math.round(width * 0.26f));
            int fieldH = Math.max(24, Math.round(height * 0.045f));
            int fieldX = (width - fieldW) / 2;
            int fieldY = height / 2 - fieldH;
            codeField = new net.minecraft.client.gui.components.EditBox(font, fieldX, fieldY, fieldW, fieldH, Component.literal("Code"));
            codeField.setMaxLength(12);
            codeField.setResponder(value -> {
                if (!value.matches("\\d{0,12}")) {
                    codeField.setValue(value.replaceAll("\\D", ""));
                }
            });
            this.addRenderableWidget(codeField);
            codeField.setFocused(true);

            int buttonW = Math.max(110, Math.round(width * 0.15f));
            int buttonH = Math.max(24, Math.round(height * 0.045f));
            this.addRenderableWidget(Button.builder(Component.literal("REDEEM"), button -> submit())
                    .bounds((width - buttonW) / 2, fieldY + fieldH + 12, buttonW, buttonH)
                    .build());
        }

        private void submit() {
            int result = FhasixPoints.redeemCode(codeField.getValue());
            if (result == FhasixPoints.CODE_OK) {
                parent.toast = "Code accepted! +" + FhasixPoints.format(FhasixPoints.CODE_POINTS) + " Fhasix Points";
                parent.toastUntil = System.currentTimeMillis() + 4200L;
                minecraft.gui.setScreen(parent);
            } else if (result == FhasixPoints.CODE_USED) {
                status = "This code was already used";
            } else {
                status = "Invalid code";
            }
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            parent.extractRenderState(context, mouseX, mouseY, delta);
            context.fill(0, 0, width, height, 0x88000000);
            int w = Math.round(width * 0.40f);
            int h = Math.round(height * 0.30f);
            int x = (width - w) / 2;
            int y = (height - h) / 2;
            a1.drawRoundedGradientRect(context, x, y, w, h, PANEL_TL, PANEL_BR, 250);
            NewFont.drawCentered(context, font, "ENTER CODE", width / 2, y + 28, 0xFFFFFFFF);
            NewFont.drawCentered(context, font, "Enter your code to redeem Fhasix Points", width / 2, y + 48, 0xFFBFC2C6);
            if (!status.isEmpty()) {
                NewFont.drawCentered(context, font, status, width / 2, y + h - 26, 0xFFFF6B6B);
            }
            super.extractRenderState(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean keyPressed(KeyEvent keyEvent) {
            if (keyEvent.key() == InputConstants.KEY_ESCAPE) {
                minecraft.gui.setScreen(parent);
                return true;
            }
            if (keyEvent.key() == InputConstants.KEY_RETURN || keyEvent.key() == InputConstants.KEY_NUMPADENTER) {
                submit();
                return true;
            }
            return super.keyPressed(keyEvent);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}
