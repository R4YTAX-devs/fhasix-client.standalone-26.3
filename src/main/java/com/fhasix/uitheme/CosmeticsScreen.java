package com.fhasix.uitheme;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.entity.player.PlayerSkin;
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
 * FHASIX Cosmetics. The frame (title, logo, top-right tile, category row, points, close button, panel, search and
 * dropdown) sits at EXACTLY the same place and size as in the Store; only the content differs: the grid of owned
 * capes and "YOUR PLAYER" on the right. Clicking a cape card equips it (a green frame
 * marks the worn one); clicking it again takes it off. Only one cape is worn at a time.
 */
public class CosmeticsScreen extends Screen {
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
    private static final int[] DATE_REF = {1133, 69, 301, 83};      // the Store's check-in tile
    private static final int TAB_X0 = 79;
    private static final int TAB_Y = 166;
    private static final int TAB_W = 67;
    private static final int TAB_H = 63;
    private static final int TAB_PITCH = 80;
    private static final int[] POINTS_REF = {1253, 166, 121, 63};
    private static final int[] CLOSE_REF = {1388, 175, 47, 47};
    private static final int[] SEARCH_REF = {104, 266, 959, 39};
    private static final int[] COLL_REF = {1088, 266, 321, 39};
    // content (this is what differs from the Store)
    private static final int[] BOX_REF = {104, 330, 832, 640};
    private static final int CARD_X0 = 128;
    private static final int CARD_Y0 = 354;
    private static final int CARD_W = 178;
    private static final int CARD_H = 214;
    private static final int CARD_PITCH_X = 196;
    private static final int CARD_PITCH_Y = 232;
    private static final int CARD_COLS = 4;
    private static final int PLAYER_CX = 1180;
    private static final int PLAYER_LABEL_CY = 360;
    private static final int PLAYER_FEET_Y = 905;

    // ------------------------------------------------------------------ colours
    private static final int TXT = 0xFFFFFFFF;
    private static final int TXT_SOFT = 0xFFC9CCD1;
    private static final int TXT_GREY = 0xFF8A8D93;
    private static final int GREEN = NewUi.rgb(62, 200, 82);
    private static final int TILE_T = NewUi.rgb(15, 17, 20);
    private static final int TILE_B = NewUi.rgb(13, 15, 18);
    private static final int HOV_TL = NewUi.rgb(54, 62, 74);
    private static final int HOV_BR = NewUi.rgb(33, 38, 46);
    private static final int PANEL_T = NewUi.rgb(15, 17, 20);
    private static final int PANEL_B = NewUi.rgb(14, 16, 19);
    private static final int PANEL_EDGE = NewUi.rgb(23, 26, 31);

    // ------------------------------------------------------------------ textures
    private static final Identifier ICONS = Identifier.fromNamespaceAndPath("fhasixuitheme", "textures/gui/store/store_icons.png");
    private static final int ICON_CELL = 64;
    private static final int ICONS_W = 512;
    private static final int ICONS_H = 256;
    private static final int I_CHEV = 13;
    private static final int I_UNLOCK = 23;
    private static final Identifier LOGO = Identifier.fromNamespaceAndPath("fhasixuitheme", "textures/gui/title/fhasix_logo.png");
    private static final int LOGO_TEX_W = 1251;
    private static final int LOGO_TEX_H = 323;
    private static final Identifier GEM = Identifier.fromNamespaceAndPath("fhasixuitheme", "textures/gui/store/store_gem.png");
    private static final int GEM_TEX = 128;
    private static final Identifier FX_MOON = Identifier.fromNamespaceAndPath("fhasixuitheme", "textures/gui/fx/fx_moon.png");
    private static final Identifier FX_CLOSE = Identifier.fromNamespaceAndPath("fhasixuitheme", "textures/gui/fx/fx_close.png");
    private static final int FX_SIZE = 96;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);

    private static Identifier store(String name) {
        return Identifier.fromNamespaceAndPath("fhasixuitheme", "textures/gui/store/" + name + ".png");
    }

    // category tiles: name + atlas icon
    private static final String[] TAB_NAMES = {"Capes", "Wings", "Particles", "Banners", "Backpacks", "Pets", "Hats",
            "Glasses", "Emotes", "Shoes", "Characters"};
    private static final int[] TAB_ICONS = {2, 3, 4, 5, 7, 6, 8, 9, 10, 11, 14};
    private static final String[] COLLECTIONS = {"All Collections", "Store Cloaks", "Collabs & Codes"};

    private static final class Cape {
        final String id;
        final String name;
        final Identifier tex;
        final int tw;
        final int th;
        final boolean slab;
        final int collection; // 1 = store cloak, 2 = collab / code
        /** Not null for a cape that comes from the GitHub catalog (UPDATE 46). */
        final FhasixCatalog.Entry remote;

        Cape(String id, String name, Identifier tex, int tw, int th, boolean slab, int collection) {
            this.id = id;
            this.name = name;
            this.tex = tex;
            this.tw = tw;
            this.th = th;
            this.slab = slab;
            this.collection = collection;
            this.remote = null;
        }

        Cape(FhasixCatalog.Entry e) {
            this.id = e.id;
            this.name = e.name;
            this.tex = null;
            this.tw = 360;
            this.th = 600;
            this.slab = true;
            this.collection = "store_cloak".equals(e.collection) ? 1 : 2;
            this.remote = e;
        }

        /** The picture to draw, or null while a GitHub icon is still loading. */
        Identifier icon() {
            return remote == null ? tex : FhasixCatalog.icon(remote);
        }

        /** False for a GitHub cape whose texture still has to be downloaded to this device. */
        boolean downloaded() {
            return remote == null || FhasixCatalog.capeState(remote) == FhasixCatalog.CAPE_READY;
        }
    }

    private static final Map<FhasixCatalog.Entry, Cape> REMOTE_CAPES = new HashMap<>();

    private static Cape remoteCape(FhasixCatalog.Entry e) {
        return REMOTE_CAPES.computeIfAbsent(e, k -> new Cape(k));
    }

    private static final Cape[] CAPES = {
            new Cape(FhasixCosmetics.FIRST_OPTIX, "First Optix (Collab)", store("first_optix_collab"), 928, 1536, false, 2),
            new Cape(FhasixCosmetics.RED_CHARMS, "Red Charms", store("red_charms"), 1263, 1536, false, 2),
            new Cape(FhasixCosmetics.CRIMSON_FADE, "Crimson Fade", store("cloak_crimson"), 360, 600, true, 1),
            new Cape(FhasixCosmetics.CYBER_CIRCUIT, "Cyber Circuit", store("cloak_neon"), 360, 600, true, 1),
            new Cape(FhasixCosmetics.VOID_RIFT, "Void Rift", store("cloak_void"), 360, 600, true, 1),
            new Cape(FhasixCosmetics.AURORA, "Aurora", store("cloak_aurora"), 360, 600, true, 1),
            new Cape(FhasixCosmetics.SOLAR_FLARE, "Solar Flare", store("cloak_solar"), 360, 600, true, 1)
    };

    // ------------------------------------------------------------------ state
    private static java.util.function.Supplier<PlayerSkin> SKIN_SUPPLIER;
    private PlayerSkin previewSkin;

    private EditBox searchField;
    private String keepText = "";
    private int tab = 0;
    private int collection = 0;
    private boolean collOpen = false;

    private final Map<String, Float> anim = new HashMap<>();
    private long animLast = System.nanoTime();
    private float animDt = 0f;
    private String dateText = "";
    private String timeText = "";
    private long clockAt = 0L;

    // layout (screen pixels)
    private float k;
    private int ox;
    private int oy;
    private int[] panel;
    private int[] dateR;
    private int[] pointsR;
    private int[] closeR;
    private int[] collR;
    private int[] searchR;
    private int[] boxR;
    private int cardW;
    private int cardH;
    private int pitchX;
    private int pitchY;
    private final int[][] tabR = new int[TAB_NAMES.length][];

    private float scrollCur = 0f;
    private float scrollTarget = 0f;
    private long lastNanos = 0L;
    private boolean pressing = false;
    private boolean dragScrolling = false;
    private double travel = 0.0;
    private int pressedCard = -1;

    public CosmeticsScreen(Screen parent) {
        super(Component.literal("Cosmetics"));
        this.parent = parent;
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
        dateR = rect(DATE_REF);
        pointsR = rect(POINTS_REF);
        closeR = rect(CLOSE_REF);
        collR = rect(COLL_REF);
        searchR = rect(SEARCH_REF);
        boxR = rect(BOX_REF);
        for (int i = 0; i < TAB_NAMES.length; i++) {
            tabR[i] = rect(new int[]{TAB_X0 + i * TAB_PITCH, TAB_Y, TAB_W, TAB_H});
        }
        cardW = sx(CARD_X0 + CARD_W) - sx(CARD_X0);
        cardH = sy(CARD_Y0 + CARD_H) - sy(CARD_Y0);
        pitchX = sx(CARD_X0 + CARD_PITCH_X) - sx(CARD_X0);
        pitchY = sy(CARD_Y0 + CARD_PITCH_Y) - sy(CARD_Y0);

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

        // The skin supplier is created ONCE (kept in a static); get() returns the current skin
        // (the default one until the real skin has loaded).
        if (SKIN_SUPPLIER == null) {
            GameProfile profile = new GameProfile(minecraft.getUser().getProfileId(), minecraft.getUser().getName());
            final java.util.concurrent.CompletableFuture<java.util.Optional<PlayerSkin>> skinFuture = minecraft.getSkinManager().get(profile);
            SKIN_SUPPLIER = () -> skinFuture.getNow(java.util.Optional.empty()).orElse(null);
        }
        previewSkin = SKIN_SUPPLIER.get();
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

    // ------------------------------------------------------------------ data
    private List<Cape> visibleCapes() {
        List<Cape> list = new ArrayList<>();
        if (tab != 0) {
            return list;
        }
        String q = searchField == null ? "" : searchField.getValue().trim().toLowerCase(Locale.ROOT);
        for (Cape cape : CAPES) {
            if (!FhasixCosmetics.owns(cape.id)) {
                continue;
            }
            if (collection != 0 && cape.collection != collection) {
                continue;
            }
            if (!q.isEmpty() && !cape.name.toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            list.add(cape);
        }
        for (FhasixCatalog.Entry e : FhasixCatalog.entries()) { // owned capes from GitHub
            if (!FhasixCosmetics.owns(e.id)) {
                continue;
            }
            Cape cape = remoteCape(e);
            if (collection != 0 && cape.collection != collection) {
                continue;
            }
            if (!q.isEmpty() && !cape.name.toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            list.add(cape);
        }
        return list;
    }

    private float maxScroll(int count) {
        int rows = (count + CARD_COLS - 1) / CARD_COLS;
        if (rows == 0) {
            return 0f;
        }
        int content = (rows - 1) * pitchY + cardH + sl(24);
        return Math.max(0f, content - (boxR[3] - (sy(CARD_Y0) - boxR[1])));
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

    private void icon(GuiGraphicsExtractor c, int idx, int x, int y, int size, int color) {
        int u = (idx % 8) * ICON_CELL;
        int v = (idx / 8) * ICON_CELL;
        c.blit(RenderPipelines.GUI_TEXTURED, ICONS, x, y, u, v, size, size, ICON_CELL, ICON_CELL, ICONS_W, ICONS_H, color);
    }

    private void tile(GuiGraphicsExtractor c, int[] r, float hv, int rad) {
        NewUi.rrect(c, r[0], r[1], r[2], r[3], rad, mix(TILE_T, HOV_TL, hv), mix(TILE_B, HOV_BR, hv));
        NewButtonArt.edge(c, r[0], r[1], r[2], r[3], rad, 0.20f + 0.08f * hv);
    }

    // ------------------------------------------------------------------ render
    @Override
    public void extractRenderState(GuiGraphicsExtractor c, int mx, int my, float delta) {
        long nowN = System.nanoTime();
        animDt = Math.min(0.1f, (nowN - animLast) / 1_000_000_000f);
        animLast = nowN;
        if (SKIN_SUPPLIER != null) {
            previewSkin = SKIN_SUPPLIER.get();
        }
        if (parent != null) {
            NewModMenuScreen.drawTitleBackdrop(c, width, height);
        } else {
            c.fill(0, 0, width, height, 0xB4000000);
        }
        tickScroll(nowN);

        boolean blocked = collOpen && collListContains(mx, my);
        int hx = blocked ? -10000 : mx;
        int hy = blocked ? -10000 : my;

        drawHeader(c, hx, hy);
        drawTabs(c, hx, hy);
        int prad = Math.max(3, sl(12));
        NewUi.rrect(c, panel[0], panel[1], panel[2], panel[3], prad, PANEL_T, PANEL_B);
        NewUi.ring(c, panel[0], panel[1], panel[2], panel[3], prad, PANEL_EDGE);
        drawToolbar(c, hx, hy);
        drawGrid(c, hx, hy);
        drawPlayer(c);
        drawCollList(c, mx, my);
        drawTips(c);

        String ver = "Fhasix 1.21.11 (release/1.0.1)";
        float vsz = fs(18f);
        text(c, ver, (width - tw(ver, NewFont.W_MED, vsz)) / 2, sy(1018) - sl(9), NewUi.rgb(104, 106, 112), NewFont.W_MED, vsz);
    }

    private void tickScroll(long now) {
        float dt = lastNanos == 0L ? 0f : Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
        lastNanos = now;
        scrollTarget = Math.max(0f, Math.min(maxScroll(visibleCapes().size()), scrollTarget));
        if (pressing && dragScrolling) {
            scrollCur = scrollTarget;
        } else {
            scrollCur += (scrollTarget - scrollCur) * (1f - (float) Math.exp(-18f * dt));
            if (Math.abs(scrollTarget - scrollCur) < 0.3f) {
                scrollCur = scrollTarget;
            }
        }
    }

    private void gem(GuiGraphicsExtractor c, int x, int y, int size) {
        NewUi.tex(c, GEM, x, y, size, GEM_TEX);
    }

    private void drawHeader(GuiGraphicsExtractor c, int mx, int my) {
        // everything below is placed and sized exactly like in the Store
        float tsz = fs(34f);
        text(c, "COSMETICS", sx(TITLE_X), NewFont.centerYUi(sy(TITLE_CY) - sl(24), sl(48), tsz), TXT, NewFont.W_BOLD, tsz);

        int lw = sl(LOGO_W);
        int lh = Math.round(lw * (float) LOGO_TEX_H / LOGO_TEX_W);
        c.blit(RenderPipelines.GUI_TEXTURED, LOGO, sx(LOGO_CX) - lw / 2, sy(LOGO_CY) - lh / 2, 0, 0,
                lw, lh, LOGO_TEX_W, LOGO_TEX_H, LOGO_TEX_W, LOGO_TEX_H);

        // date + time in the Store's top-right tile
        long ms = System.currentTimeMillis();
        if (ms - clockAt > 500L || dateText.isEmpty()) {
            clockAt = ms;
            LocalDateTime now = LocalDateTime.now();
            dateText = now.format(DATE_FMT);
            timeText = now.format(TIME_FMT);
        }
        tile(c, dateR, 0f, Math.max(3, sl(9)));
        int moon = Math.max(8, sl(40));
        NewUi.tex(c, FX_MOON, dateR[0] + sl(26), dateR[1] + (dateR[3] - moon) / 2, moon, FX_SIZE);
        float dsz = fs(17f);
        int dx = dateR[0] + sl(100);
        text(c, dateText, dx, NewFont.centerYUi(dateR[1] + sl(18), sl(26), dsz), TXT, NewFont.W_BOLD, dsz);
        text(c, timeText, dx, NewFont.centerYUi(dateR[1] + sl(42), sl(26), dsz), TXT_SOFT, NewFont.W_BOLD, dsz);

        // points balance, same tile as in the Store
        int prad = Math.max(3, sl(10));
        String bal = FhasixPoints.compact(FhasixPoints.balance());
        float nsz = fs(25f);
        int pillW = Math.max(pointsR[2], sl(62) + tw(bal, NewFont.W_BOLD, nsz) + sl(26));
        int pillX = pointsR[0] + pointsR[2] - pillW;
        tile(c, new int[]{pillX, pointsR[1], pillW, pointsR[3]}, 0f, prad);
        int pg = Math.max(10, sl(36));
        gem(c, pillX + sl(18), pointsR[1] + (pointsR[3] - pg) / 2, pg);
        text(c, bal, pillX + sl(62), NewFont.centerYUi(pointsR[1], pointsR[3], nsz), TXT, NewFont.W_BOLD, nsz);

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
            icon(c, TAB_ICONS[i], r[0] + (r[2] - s) / 2, r[1] + (r[3] - s) / 2, s, 0xFFFFFFFF);
        }
    }

    /** The small label that fades in above a hovered tile (same as in the Store). */
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

    // ------------------------------------------------------------------ toolbar + collection dropdown
    private void drawToolbar(GuiGraphicsExtractor c, int mx, int my) {
        int rad = Math.max(3, sl(8));
        float ssz = fs(15f);
        boolean oh = NewUi.in(mx, my, collR[0], collR[1], collR[2], collR[3]);
        int oc = mix(NewUi.rgb(23, 26, 30), NewUi.rgb(30, 34, 39), ease("coll", oh || collOpen ? 1f : 0f, 14f));
        NewUi.rrect(c, collR[0], collR[1], collR[2], collR[3], rad, oc, oc);
        text(c, COLLECTIONS[collection], collR[0] + sl(13), NewFont.centerYUi(collR[1], collR[3], ssz), TXT_GREY, NewFont.W_BOLD, ssz);
        int cs = Math.max(8, sl(13));
        icon(c, I_CHEV, collR[0] + collR[2] - sl(16) - cs, collR[1] + (collR[3] - cs) / 2, cs, TXT_GREY);

        boolean sh = NewUi.in(mx, my, searchR[0], searchR[1], searchR[2], searchR[3]);
        int sc = mix(NewUi.rgb(23, 26, 30), NewUi.rgb(30, 34, 39), ease("search", sh ? 1f : 0f, 14f));
        NewUi.rrect(c, searchR[0], searchR[1], searchR[2], searchR[3], rad, sc, sc);
        int tx = searchR[0] + sl(16);
        int ty = NewFont.centerYUi(searchR[1], searchR[3], ssz);
        String q = searchField == null ? "" : searchField.getValue();
        int caretX = tx;
        if (q.isEmpty()) {
            text(c, "Search...", tx, ty, TXT_GREY, NewFont.W_BOLD, ssz);
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
    }

    private int[] collListRect() {
        int rh = Math.max(14, sl(36));
        int pad = sl(6);
        return new int[]{collR[0], collR[1] + collR[3] + sl(6), collR[2], rh * COLLECTIONS.length + pad * 2};
    }

    private boolean collListContains(double mx, double my) {
        int[] r = collListRect();
        return NewUi.in(mx, my, r[0], r[1], r[2], r[3]);
    }

    private void drawCollList(GuiGraphicsExtractor c, int mx, int my) {
        if (!collOpen) {
            return;
        }
        int[] r = collListRect();
        int rad = Math.max(3, sl(8));
        NewUi.rrect(c, r[0], r[1], r[2], r[3], rad, NewUi.rgb(19, 21, 25), NewUi.rgb(17, 19, 22));
        NewButtonArt.edge(c, r[0], r[1], r[2], r[3], rad, 0.26f);
        int rh = Math.max(14, sl(36));
        int pad = sl(6);
        float sz = fs(15f);
        for (int i = 0; i < COLLECTIONS.length; i++) {
            int ry = r[1] + pad + i * rh;
            boolean over = NewUi.in(mx, my, r[0] + pad, ry, r[2] - pad * 2, rh);
            float hv = ease("colli:" + i, over ? 1f : 0f, 16f);
            if (hv > 0.02f) {
                int hc = NewUi.withAlpha(0xFFFFFFFF, 0.06f * hv);
                NewUi.rrect(c, r[0] + pad, ry, r[2] - pad * 2, rh, Math.max(3, sl(6)), hc, hc);
            }
            text(c, COLLECTIONS[i], r[0] + sl(14), NewFont.centerYUi(ry, rh, sz), i == collection ? TXT : mix(TXT_GREY, TXT, hv), NewFont.W_BOLD, sz);
        }
    }

    // ------------------------------------------------------------------ grid of owned capes
    private int cardX(int i) {
        return sx(CARD_X0) + (i % CARD_COLS) * pitchX;
    }

    private int cardY(int i) {
        return sy(CARD_Y0) + (i / CARD_COLS) * pitchY - Math.round(scrollCur);
    }

    private void drawGrid(GuiGraphicsExtractor c, int mx, int my) {
        int brad = Math.max(3, sl(6));
        NewUi.ring(c, boxR[0], boxR[1], boxR[2], boxR[3], brad, NewUi.rgb(26, 29, 35));
        List<Cape> capes = visibleCapes();
        c.enableScissor(boxR[0], boxR[1] + 1, boxR[0] + boxR[2], boxR[1] + boxR[3] - 1);
        if (capes.isEmpty()) {
            float sz = fs(17f);
            String msg = tab != 0 ? "Nothing here yet"
                    : (FhasixCosmetics.owns(FhasixCosmetics.FIRST_OPTIX) || anyOwned() ? "No capes match" : "You don't own any capes yet - get some in the Store");
            text(c, msg, boxR[0] + (boxR[2] - tw(msg, NewFont.W_MED, sz)) / 2, boxR[1] + sl(120), TXT_GREY, NewFont.W_MED, sz);
        }
        boolean inBox = NewUi.in(mx, my, boxR[0], boxR[1], boxR[2], boxR[3]);
        for (int i = 0; i < capes.size(); i++) {
            int x = cardX(i);
            int y = cardY(i);
            if (y + cardH < boxR[1] || y > boxR[1] + boxR[3]) {
                continue;
            }
            drawCard(c, capes.get(i), x, y, inBox && NewUi.in(mx, my, x, y, cardW, cardH));
        }
        c.disableScissor();

        float max = maxScroll(capes.size());
        if (max > 0f) {
            int track = boxR[3] - sl(8);
            int th = Math.max(sl(24), Math.round(track * (track / (track + max))));
            int ty = boxR[1] + sl(4) + Math.round((track - th) * (scrollCur / max));
            int bw = Math.max(2, sl(5));
            NewUi.rrect(c, boxR[0] + boxR[2] - bw - sl(4), ty, bw, th, bw / 2, NewUi.rgb(88, 89, 92), NewUi.rgb(80, 81, 84));
        }
    }

    private boolean anyOwned() {
        for (Cape cape : CAPES) {
            if (FhasixCosmetics.owns(cape.id)) {
                return true;
            }
        }
        for (FhasixCatalog.Entry e : FhasixCatalog.entries()) {
            if (FhasixCosmetics.owns(e.id)) {
                return true;
            }
        }
        return false;
    }

    private void drawCard(GuiGraphicsExtractor c, Cape cape, int x, int y, boolean over) {
        int rad = Math.max(3, sl(7));
        boolean worn = FhasixCosmetics.isEquipped(cape.id);
        float hv = ease("card:" + cape.id, over ? 1f : 0f, 14f);
        int top = NewUi.rgb(18, 21, 26);
        int bot = NewUi.rgb(13, 15, 19);
        if (worn) {
            top = NewUi.rgb(38, 66, 46);
            bot = NewUi.rgb(20, 34, 26);
        }
        NewUi.rrect(c, x, y, cardW, cardH, rad, mix(top, worn ? NewUi.rgb(46, 80, 56) : NewUi.rgb(64, 72, 84), hv),
                mix(bot, worn ? NewUi.rgb(26, 44, 32) : NewUi.rgb(38, 43, 51), hv));

        int cx = x + cardW / 2;
        Identifier ctex = cape.icon();
        if (cape.slab) {
            int ih = Math.round(cardH * 1.02f);
            int iw = Math.round(ih * (float) cape.tw / cape.th);
            if (ctex != null) {
                c.blit(RenderPipelines.GUI_TEXTURED, ctex, cx - iw / 2 - sl(2), y + (cardH - ih) / 2 - Math.round(ih * 0.02f), 0, 0,
                        iw, ih, cape.tw, cape.th, cape.tw, cape.th);
            } else {
                FhasixCatalog.spinner(c, cx - sl(40), y + cardH / 2 - sl(40), sl(80)); // icon still loading
            }
        } else {
            int maxH = Math.round(cardH * 0.80f);
            int maxW = Math.round(cardW * 0.78f);
            int ih = maxH;
            int iw = Math.round(ih * (float) cape.tw / cape.th);
            if (iw > maxW) {
                iw = maxW;
                ih = Math.round(iw * (float) cape.th / cape.tw);
            }
            c.blit(RenderPipelines.GUI_TEXTURED, ctex, cx - iw / 2, y + (cardH - ih) / 2, 0, 0, iw, ih, cape.tw, cape.th, cape.tw, cape.th);
        }

        int ls = Math.max(8, sl(20));
        icon(c, I_UNLOCK, x + cardW - ls - sl(12), y + sl(10), ls, GREEN);

        if (worn) {
            NewUi.ring(c, x, y, cardW, cardH, rad, GREEN);
            NewUi.ring(c, x + 1, y + 1, cardW - 2, cardH - 2, Math.max(2, rad - 1), GREEN);
        } else {
            NewButtonArt.edge(c, x, y, cardW, cardH, rad, 0.20f + 0.08f * hv);
        }
        boolean needDl = cape.remote != null && !cape.downloaded();
        if (needDl) { // GitHub cape whose texture is not on this device yet: tap the card to download it
            String dl = FhasixCatalog.downloadLabel(cape.remote);
            if (dl != null) {
                float dsz = fs(14f);
                int dw = tw(dl, NewFont.W_BOLD, dsz);
                int dph = Math.max(10, sl(28));
                int dpw = dw + sl(26);
                int dpx = x + (cardW - dpw) / 2;
                int dpy = y + cardH - dph - sl(10);
                NewUi.rrect(c, dpx, dpy, dpw, dph, Math.max(3, sl(8)), NewUi.withAlpha(NewUi.rgb(10, 12, 15), 0.92f), NewUi.withAlpha(NewUi.rgb(10, 12, 15), 0.92f));
                text(c, dl, dpx + (dpw - dw) / 2, NewFont.centerYUi(dpy, dph, dsz), 0xFFFFFFFF, NewFont.W_BOLD, dsz);
            }
        }
        if (!needDl && hv > 0.05f) { // the name shows while the pointer is on the card
            float nsz = fs(14f);
            int nw = tw(cape.name, NewFont.W_BOLD, nsz);
            int ph = Math.max(10, sl(26));
            int pw = nw + sl(24);
            int px = x + (cardW - pw) / 2;
            int py = y + cardH - ph - sl(10);
            NewUi.rrect(c, px, py, pw, ph, Math.max(3, sl(7)), NewUi.withAlpha(NewUi.rgb(10, 12, 15), 0.88f * hv), NewUi.withAlpha(NewUi.rgb(10, 12, 15), 0.88f * hv));
            text(c, cape.name, px + (pw - nw) / 2, NewFont.centerYUi(py, ph, nsz), NewUi.withAlpha(0xFFFFFFFF, hv), NewFont.W_BOLD, nsz);
        }
    }

    // ------------------------------------------------------------------ "YOUR PLAYER"
    private void drawPlayer(GuiGraphicsExtractor context) {
        float sz = fs(24f);
        String label = "YOUR PLAYER";
        text(context, label, sx(PLAYER_CX) - tw(label, NewFont.W_BOLD, sz) / 2,
                NewFont.centerYUi(sy(PLAYER_LABEL_CY) - sl(20), sl(40), sz), NewUi.rgb(155, 158, 165), NewFont.W_BOLD, sz);

        Minecraft mc = Minecraft.getInstance();
        int entityX = sx(PLAYER_CX);
        int entityY = sy(PLAYER_FEET_Y);
        if (mc.player != null) {
            // PORTING NOTE: InventoryScreen.drawEntity takes a bounding box (x1,y1,x2,y2) plus a separate
            // scale factor; this frames the player as a box of 2*size height standing on entityY.
            int size = Math.min(140, Math.max(40, Math.round(197f * k)));
            InventoryScreen.extractEntityInInventoryFollowsMouse(context,
                    entityX - size, entityY - size * 2, entityX + size, entityY,
                    size, 1.0f,
                    entityX - 80.0f, entityY - 80.0f, mc.player);
        } else if (previewSkin != null) {
            // The title screen has no world to hold a player entity, so show the account skin instead.
            int skinSize = Math.max(32, sl(160));
            PlayerFaceExtractor.extractRenderState(context, previewSkin, entityX - skinSize / 2, sy(600), skinSize);
        }
    }

    // ------------------------------------------------------------------ input
    private int cardAt(double mx, double my) {
        if (!NewUi.in(mx, my, boxR[0], boxR[1], boxR[2], boxR[3])) {
            return -1;
        }
        int n = visibleCapes().size();
        for (int i = 0; i < n; i++) {
            if (NewUi.in(mx, my, cardX(i), cardY(i), cardW, cardH)) {
                return i;
            }
        }
        return -1;
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
        if (collOpen) {
            int[] r = collListRect();
            if (NewUi.in(mx, my, r[0], r[1], r[2], r[3])) {
                int rh = Math.max(14, sl(36));
                int pad = sl(6);
                int i = (int) ((my - r[1] - pad) / rh);
                if (i >= 0 && i < COLLECTIONS.length) {
                    NewUi.click(minecraft);
                    collection = i;
                    scrollTarget = 0f;
                }
            }
            collOpen = false;
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
        if (NewUi.in(mx, my, collR[0], collR[1], collR[2], collR[3])) {
            NewUi.click(minecraft);
            collOpen = true;
            return true;
        }
        for (int i = 0; i < TAB_NAMES.length; i++) {
            int[] r = tabR[i];
            if (NewUi.in(mx, my, r[0], r[1], r[2], r[3])) {
                if (i != tab) {
                    NewUi.click(minecraft);
                    tab = i;
                    scrollTarget = 0f;
                    scrollCur = 0f;
                }
                return true;
            }
        }
        if (NewUi.in(mx, my, boxR[0], boxR[1], boxR[2], boxR[3])) {
            pressing = true;
            dragScrolling = false;
            travel = 0.0;
            pressedCard = cardAt(mx, my);
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
            int pressed = pressedCard;
            pressedCard = -1;
            if (!wasDrag && pressed >= 0 && cardAt(click.x(), click.y()) == pressed) {
                List<Cape> capes = visibleCapes();
                if (pressed < capes.size()) {
                    NewUi.click(minecraft);
                    Cape tapped = capes.get(pressed);
                    if (tapped.remote != null && !tapped.downloaded()) {
                        // GitHub cape whose texture is not on this device (e.g. after Free up memory):
                        // download ONLY this one, and wear it as soon as it has arrived
                        FhasixCatalog.downloadCape(tapped.remote, true);
                    } else {
                        FhasixCosmetics.toggleEquip(tapped.id);
                    }
                }
            }
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollTarget -= (float) verticalAmount * 40f;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (keyEvent.key() == InputConstants.KEY_ESCAPE) {
            if (collOpen) {
                collOpen = false;
                return true;
            }
            closeScreen();
            return true;
        }
        return super.keyPressed(keyEvent);
    }
}
