package com.fhasix.uitheme;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * FHASIX Partnered Servers (the title-screen "Partnered Servers" button) - UPDATE 41, rebuilt from scratch
 * after the Feather client's partnered-servers window.
 *
 * Sizing works exactly like the Store / Cosmetics screens: every position and size below is a pixel of the
 * reference picture (RW x RH = 1932 x 1080) and k = min(width / RW, height / RH) scales it to the screen,
 * so the window looks the same at every GUI size (the GUI scale itself is locked to 2 by NewGuiScale).
 *
 * Look: Fhasix mark + title, a date/time pill with the Feather edge (NewButtonArt.edge, the same edge the
 * Singleplayer / Multiplayer buttons have), the red SERVER EVENTS button, the red close button, a dark panel
 * with the search field + category dropdown, and a 3-column grid of server cards. A card shows the server's
 * own description (MOTD, in its colours), country flag, icon, live player count and name; hovering it dims
 * the top, lights up the picture and shows the Join Server / Add to Server List buttons.
 *
 * Touch: a first tap on a card reveals its buttons (there is no hover on a phone), a second tap on a button
 * uses it. Swipe the grid to scroll (with a short glide), or drag the scrollbar.
 *
 * Server status (icon, players, description) comes from FhasixServerPinger on its own thread pool.
 */
public class DiscoverScreen extends Screen {
    private final Screen parent;

    // ------------------------------------------------------------------ reference geometry (picture pixels)
    private static final int RW = 1502;
    private static final int RH = 1059;
    private static final String TITLE = "PARTNERED SERVERS";
    private static final int HEAD_CY = 198;                 // header row centre (the close button's centre, like Cosmetics)
    private static final int MARK_CX = 111;
    private static final int MARK_SIZE = 66;
    private static final int TITLE_X = 160;
    private static final int PILL_H = 46;
    private static final int PILL_GAP = 34;
    private static final int[] EVENTS_REF = {1100, 167, 262, 62};
    private static final int[] CLOSE_REF = {1388, 175, 47, 47};      // = Store / Cosmetics
    private static final int[] PANEL_REF = {79, 243, 1355, 745};     // = Store / Cosmetics
    private static final int[] SEARCH_REF = {104, 266, 959, 39};     // = Store / Cosmetics
    private static final int[] DROP_REF = {1088, 266, 321, 39};      // = Cosmetics collection dropdown
    private static final int[] QUIT_REF = {608, 968, 285, 54};
    private static final int VERSION_Y = 1018;
    // card grid
    private static final int VIEW_X = 104;
    private static final int VIEW_Y = 330;
    private static final int VIEW_W = 1305;
    private static final int VIEW_B = 976;
    private static final int COLS = 3;
    private static final int CARD_W = 424;
    private static final int CARD_H = 245;
    private static final float PITCH_X = 440.5f;
    private static final int PITCH_Y = 262;
    private static final int HOVER_TOP_H = 75;
    private static final int SCROLL_X = 1416;
    private static final int SCROLL_W = 6;

    // ------------------------------------------------------------------ colours
    private static final int TXT = 0xFFFFFFFF;
    private static final int TXT_SOFT = 0xFFC9CCD1;
    private static final int TXT_GREY = 0xFF8A8D93;
    private static final int TILE_T = NewUi.rgb(17, 19, 23);
    private static final int TILE_B = NewUi.rgb(14, 16, 19);
    private static final int PANEL_T = NewUi.rgb(15, 17, 20);
    private static final int PANEL_B = NewUi.rgb(14, 16, 19);
    private static final int PANEL_EDGE = NewUi.rgb(23, 26, 31);
    private static final int FIELD = NewUi.rgb(23, 26, 30);
    private static final int FIELD_HOV = NewUi.rgb(30, 34, 39);
    private static final int DOT_ON = 0x3FBF4D;
    private static final int DOT_WAIT = 0x8A8F96;
    private static final int DOT_OFF = 0xE05B5B;
    private static final float CARD_DIM = 0.60f;

    // ------------------------------------------------------------------ textures
    private static final Identifier LOGO_MARK = Identifier.fromNamespaceAndPath("fhasixuitheme", "textures/gui/title/new_fhasix_mark.png");
    private static final int MARK_TEX = 256;
    private static final Identifier FX_MOON = Identifier.fromNamespaceAndPath("fhasixuitheme", "textures/gui/fx/fx_moon.png");
    private static final Identifier FX_CLOSE = Identifier.fromNamespaceAndPath("fhasixuitheme", "textures/gui/fx/fx_close.png");
    private static final int FX_SIZE = 96;
    private static final Identifier ICONS = Identifier.fromNamespaceAndPath("fhasixuitheme", "textures/gui/store/store_icons.png");
    private static final int ICON_CELL = 64;
    private static final int ICONS_W = 512;
    private static final int ICONS_H = 256;
    private static final int I_CHEV = 13;
    private static final Identifier CARD_BG = Identifier.fromNamespaceAndPath("fhasixuitheme", "textures/gui/discover/card_background.png");
    private static final int BG_W = 1200;
    private static final int BG_H = 558;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("yyyy/MM/dd  hh:mm a", Locale.ENGLISH);

    /** "Recommended" = no filter. Every other entry is a tag a server can carry (a server can have several). */
    private static final String[] CATEGORIES = {
            "Recommended", "PvP", "Lifesteal", "BedWars", "Practice", "BoxPvP", "KitPvP",
            "Duels", "FFA", "CrystalPvP", "Survival", "Anarchy", "Economy", "Other"
    };

    // ------------------------------------------------------------------ state
    private final List<CardEntry> servers = new ArrayList<>();
    private boolean pingsStarted = false;
    private EditBox searchField;
    private String keepText = "";
    private String lastQuery = "";
    private int category = 0;
    private boolean dropOpen = false;
    private boolean eventsOpen = false;

    private String clockText = "";
    private long clockAt = 0L;
    private float closeHv = 0f;
    private float eventsHv = 0f;
    private long animLast = System.nanoTime();
    private float animDt = 0f;

    // layout (screen pixels)
    private float k;
    private int ox;
    private int oy;
    private int[] panel;
    private int[] searchR;
    private int[] dropR;
    private int[] closeR;
    private int[] eventsR;
    private int[] viewR;
    private int cardW;
    private int cardH;
    private int pitchY;

    // scrolling
    private float scrollCur = 0f;
    private float scrollTarget = 0f;
    private long lastNanos = 0L;
    private boolean pressing = false;
    private boolean dragScrolling = false;
    private double travel = 0.0;
    private int pressCard = -1;
    private int pressBtn = 0; // 0 none, 1 Join Server, 2 Add to Server List
    private long lastDragNs = 0L;
    private float dragVel = 0f;
    private boolean thumbDragging = false;
    private double thumbGrab = 0.0;

    public DiscoverScreen(Screen parent) {
        super(Component.literal("Partnered Servers"));
        this.parent = parent;
        // addServer(name, address, tags)  -  an optional 4th value sets the flag ("IN", "US", "DE", "FR", "NL", "IT");
        // without it the flag is guessed from the address ending (.in / .de / .us / .fr / .nl / .it), or left out.
        addServer("ALWTO.FUN", "play.alwto.fun", "Other");
        addServer("GamerTee", "gamertee.net", "Other");
        addServer("PlumSMP", "fun.plumsmp.net", "Survival");
        addServer("CrazyMC", "play.crazymc.net", "Other");
        addServer("PvP Paradise", "pvpparadise.net", "PvP");
        addServer("FireMC", "play.firemc.fun", "Other");
        addServer("BlueMC", "play.bluemc.fun", "Other");
        addServer("WaterMC", "play.watermc.in", "Other");

        addServer("AyorMC", "mc.ayormc.net", "Practice, BedWars, PvP");
        addServer("BlockMC", "play.blockmc.fun", "Lifesteal, PvP");
        addServer("KhelMC", "play.khelmc.online", "FFA, Lifesteal");
        addServer("CryzorMC", "play.cryzormc.in", "Practice, BoxPvP, Lifesteal");
        addServer("SkyZenMC", "play.skyzenmc.qzz.io", "Lifesteal, BedWars");
        addServer("RixxyMC", "play.rixxymc.in", "Practice, BedWars, Lifesteal");
        addServer("Pookie SMP", "pookiesmp.in", "Practice, Lifesteal, BedWars");
        addServer("Valocraft", "play.valocraft.fun", "Duels, FFA, PvP");
        addServer("XyrixGames", "play.xyrixgames.fun", "Duels, CrystalPvP, Lifesteal");
        addServer("RuleX MC", "rulexmc.mcsh.io", "Practice, PvP, Lifesteal");
        addServer("Don Network", "play.donnetwork.in", "Practice, BedWars, Lifesteal");
        addServer("TwilightSMP", "play.twilightsmp.in:25567", "Duels, FFA, BoxPvP");
        addServer("StarNetwork", "starnetwork.cbu.net:19136", "PvP, BoxPvP, Lifesteal");
        addServer("ThunderMC", "play.thundermcog.fun:25570", "KitPvP, Lifesteal, PvP");
        addServer("SnifferMC", "play.sniffermc.fun:25567", "Survival, PvP");
        addServer("MCGOLD", "play.mcgold.fun", "Lifesteal, PvP");
        addServer("MomoMC", "momomc.net", "PvP, Economy");
        addServer("Mantra SMP", "play.worldofangara.in", "PvP, BedWars, Survival");
        addServer("Ultimate SMP", "play.ultimateplayz.fun", "PvP, Survival");
        // Aloo SMP (PvP, Survival, Minigames) is intentionally not listed yet: no address was
        // given. Add it here once there is one, and add "Minigames" to CATEGORIES.
        addServer("BharatMC", "play.bharatmc.net", "Lifesteal, PvP");
        addServer("SpaceMC", "play.spacemc.lol", "BoxPvP, KitPvP, Lifesteal");
        addServer("ND Network", "play.ndnetwork.in", "KitPvP, Lifesteal");
        addServer("BlitzMC", "blitzmc.fun", "PvP, Lifesteal, Anarchy");
        addServer("PrimeX Anarchy", "play.primexanarchy.net", "PvP, Anarchy");
        addServer("LOL SMP", "play.shivxtreme.fun", "KitPvP, PvP, Lifesteal");
        addServer("Wsmp", "play.wsmp.fun", "KitPvP, PvP, Lifesteal");
        addServer("CoolMC", "play.coolmc.fun", "BoxPvP");
        addServer("LalaMC", "play.lalamc.fun", "PvP, Lifesteal");
        addServer("MCNP Network", "play.mcnpnetwork.com", "BedWars, Lifesteal");
    }

    private void addServer(String name, String address, String tagsCsv) {
        servers.add(new CardEntry(name, address, tagsCsv, ""));
    }

    // ------------------------------------------------------------------ layout helpers (same as the Store / Cosmetics)
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
        searchR = rect(SEARCH_REF);
        dropR = rect(DROP_REF);
        closeR = rect(CLOSE_REF);
        eventsR = rect(EVENTS_REF);
        viewR = rect(new int[]{VIEW_X, VIEW_Y, VIEW_W, VIEW_B - VIEW_Y});
        cardW = sx(VIEW_X + CARD_W) - sx(VIEW_X);
        cardH = sy(VIEW_Y + CARD_H) - sy(VIEW_Y);
        pitchY = sy(VIEW_Y + PITCH_Y) - sy(VIEW_Y);

        int padX = sl(16);
        String keep = searchField != null ? searchField.getValue() : keepText;
        searchField = new EditBox(font, searchR[0] + padX,
                searchR[1] + (searchR[3] - font.lineHeight) / 2,
                Math.max(10, searchR[2] - padX * 2), font.lineHeight + 2, Component.literal("Search"));
        searchField.setMaxLength(40);
        searchField.setBordered(false);
        searchField.setValue(keep);
        // typing and focus only - this screen draws the text and the caret itself (smooth font)
        this.addRenderableWidget(searchField);

        // Queue the pings once per screen: init() also runs again on a window resize / rotation, which
        // must not ping every server again. pingAsync only queues work on the pinger's own threads.
        if (!pingsStarted) {
            pingsStarted = true;
            for (CardEntry entry : servers) {
                entry.startPing(minecraft);
            }
        }
    }

    @Override
    public void removed() {
        for (CardEntry entry : servers) {
            entry.close();
        }
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
    private List<CardEntry> visible() {
        String q = searchField == null ? "" : searchField.getValue().trim().toLowerCase(Locale.ROOT);
        List<CardEntry> out = new ArrayList<>();
        for (CardEntry e : servers) {
            if (category != 0 && !e.hasTag(CATEGORIES[category])) {
                continue;
            }
            if (!q.isEmpty() && !e.name.toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            out.add(e);
        }
        return out;
    }

    private float contentHeight(int count) {
        int rows = (count + COLS - 1) / COLS;
        return rows == 0 ? 0f : (rows - 1) * pitchY + cardH + sl(12);
    }

    private float maxScroll(int count) {
        return Math.max(0f, contentHeight(count) - viewR[3]);
    }

    private int cardX(int i) {
        return sx(VIEW_X + (i % COLS) * PITCH_X);
    }

    private int cardY(int i) {
        return viewR[1] + (i / COLS) * pitchY - Math.round(scrollCur);
    }

    /** Index (in the visible list) of the card under the point, or -1. */
    private int cardAt(double mx, double my, int count) {
        if (!NewUi.in(mx, my, viewR[0], viewR[1], viewR[2], viewR[3])) {
            return -1;
        }
        for (int i = 0; i < count; i++) {
            if (NewUi.in(mx, my, cardX(i), cardY(i), cardW, cardH)) {
                return i;
            }
        }
        return -1;
    }

    // button rectangles on a hovered card (x, y, w, h)
    private int[] joinRect(int x, int y) {
        int pad = sl(17);
        int bw = (cardW - pad * 2 - sl(25)) / 2;
        return new int[]{x + pad, y + sl(18), bw, sl(40)};
    }

    private int[] addRect(int x, int y) {
        int[] j = joinRect(x, y);
        return new int[]{j[0] + j[2] + sl(25), j[1], j[2], j[3]};
    }

    // ------------------------------------------------------------------ small helpers
    private float approach(float cur, float target, float speed) {
        if (NewPerf.animationsOff()) {
            return target;
        }
        cur += (target - cur) * Math.min(1f, animDt * speed);
        return Math.abs(target - cur) < 0.004f ? target : cur;
    }

    private void text(GuiGraphicsExtractor c, String s, int x, int y, int color, int weight, float size) {
        if (((color >>> 24) & 255) < 6) {
            return; // an almost invisible colour would be drawn fully opaque by the text renderer
        }
        NewFont.drawUi(c, font, s, x, y, color, weight, size);
    }

    private int tw(String s, int weight, float size) {
        return NewFont.widthUi(font, s, weight, size);
    }

    private void tile(GuiGraphicsExtractor c, int[] r, float hv, int rad) {
        NewUi.rrect(c, r[0], r[1], r[2], r[3], rad, NewUi.lerp(TILE_T, NewUi.rgb(54, 62, 74), hv), NewUi.lerp(TILE_B, NewUi.rgb(33, 38, 46), hv));
        NewButtonArt.edge(c, r[0], r[1], r[2], r[3], rad, 0.20f + 0.08f * hv);
    }

    private static int blackAlpha(float a) {
        return (Math.round(255f * Math.max(0f, Math.min(1f, a))) << 24) | 0x08090C;
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

        String query = searchField == null ? "" : searchField.getValue();
        if (!query.equals(lastQuery)) {
            lastQuery = query;
            scrollTarget = 0f;
            scrollCur = 0f;
        }
        List<CardEntry> vis = visible();
        tickScroll(nowN, vis.size());

        boolean blocked = eventsOpen || (dropOpen && dropListContains(mx, my));
        int hx = blocked ? -10000 : mx;
        int hy = blocked ? -10000 : my;

        drawQuitGhost(c);
        NewUi.rrect(c, panel[0], panel[1], panel[2], panel[3], Math.max(3, sl(14)), PANEL_T, PANEL_B);
        NewUi.ring(c, panel[0], panel[1], panel[2], panel[3], Math.max(3, sl(14)), PANEL_EDGE);
        drawHeader(c, hx, hy);
        drawToolbar(c, hx, hy);
        drawGrid(c, hx, hy, vis);
        drawScrollbar(c, vis.size());
        drawDropList(c, mx, my);

        String ver = "Fhasix 1.21.11 (release/1.0.1)";
        float vsz = fs(18f);
        text(c, ver, (width - tw(ver, NewFont.W_MED, vsz)) / 2, sy(VERSION_Y) - sl(9), NewUi.rgb(104, 106, 112), NewFont.W_MED, vsz);

        if (eventsOpen) {
            drawEvents(c, mx, my);
        }
    }

    private void tickScroll(long now, int count) {
        float dt = lastNanos == 0L ? 0f : Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
        lastNanos = now;
        scrollTarget = Math.max(0f, Math.min(maxScroll(count), scrollTarget));
        if ((pressing && dragScrolling) || thumbDragging) {
            scrollCur = scrollTarget;
        } else {
            scrollCur += (scrollTarget - scrollCur) * (1f - (float) Math.exp(-16f * dt));
            if (Math.abs(scrollTarget - scrollCur) < 0.3f) {
                scrollCur = scrollTarget;
            }
        }
    }

    /** The dimmed "QUIT GAME" button of the title screen that peeks out under the panel in the reference. */
    private void drawQuitGhost(GuiGraphicsExtractor c) {
        int[] r = rect(QUIT_REF);
        int rad = Math.max(3, sl(10));
        int fill = NewUi.withAlpha(NewUi.rgb(150, 28, 34), 0.26f);
        NewUi.rrect(c, r[0], r[1], r[2], r[3], rad, fill, fill);
        String s = "QUIT GAME";
        float sz = fs(22f);
        text(c, s, r[0] + (r[2] - tw(s, NewFont.W_BOLD, sz)) / 2, NewFont.centerYUi(sy(995) - sl(14), sl(28), sz), NewUi.rgb(214, 52, 58), NewFont.W_BOLD, sz);
    }

    private void drawHeader(GuiGraphicsExtractor c, int mx, int my) {
        // Fhasix mark + title
        int ms = sl(MARK_SIZE);
        c.blit(RenderPipelines.GUI_TEXTURED, LOGO_MARK, sx(MARK_CX) - ms / 2, sy(HEAD_CY) - ms / 2, 0, 0,
                ms, ms, MARK_TEX, MARK_TEX, MARK_TEX, MARK_TEX);
        float tsz = fs(32f);
        text(c, TITLE, sx(TITLE_X), NewFont.centerYUi(sy(HEAD_CY) - sl(24), sl(48), tsz), TXT, NewFont.W_BOLD, tsz);
        int titleW = tw(TITLE, NewFont.W_BOLD, tsz);

        // date + time pill with the Feather edge
        long ms2 = System.currentTimeMillis();
        if (ms2 - clockAt > 500L || clockText.isEmpty()) {
            clockAt = ms2;
            clockText = LocalDateTime.now().format(CLOCK);
        }
        float dsz = fs(19f);
        int pillX = sx(TITLE_X) + titleW + sl(PILL_GAP);
        int pillW = sl(84) + tw(clockText, NewFont.W_BOLD, dsz) + sl(30);
        int[] pill = {pillX, sy(HEAD_CY - PILL_H / 2), pillW, sy(HEAD_CY + PILL_H / 2) - sy(HEAD_CY - PILL_H / 2)};
        tile(c, pill, 0f, Math.max(3, sl(9)));
        int moon = Math.max(8, sl(40));
        NewUi.tex(c, FX_MOON, pill[0] + sl(24), pill[1] + (pill[3] - moon) / 2, moon, FX_SIZE);
        text(c, clockText, pill[0] + sl(84), NewFont.centerYUi(pill[1], pill[3], dsz), TXT, NewFont.W_BOLD, dsz);

        // SERVER EVENTS (the red button of the title screen)
        boolean eh = NewUi.in(mx, my, eventsR[0], eventsR[1], eventsR[2], eventsR[3]);
        eventsHv = approach(eventsHv, eh ? 1f : 0f, 14f);
        NewButtonArt.red(c, eventsR[0], eventsR[1], eventsR[2], eventsR[3], eventsHv);
        String ev = "SERVER EVENTS";
        float esz = fs(23f);
        text(c, ev, eventsR[0] + (eventsR[2] - tw(ev, NewFont.W_BOLD, esz)) / 2, NewFont.centerYUi(eventsR[1], eventsR[3], esz), TXT, NewFont.W_BOLD, esz);

        // close button (same as the Store / Cosmetics)
        boolean ch = NewUi.in(mx, my, closeR[0], closeR[1], closeR[2], closeR[3]);
        closeHv = approach(closeHv, ch ? 1f : 0f, 14f);
        int crad = Math.max(3, sl(9));
        int fill = NewUi.withAlpha(FhasixTheme.red(), 0.13f + 0.17f * closeHv);
        NewUi.rrect(c, closeR[0], closeR[1], closeR[2], closeR[3], crad, fill, fill);
        NewUi.ring(c, closeR[0], closeR[1], closeR[2], closeR[3], crad, NewUi.lerp(FhasixTheme.ring(), FhasixTheme.redHover(), closeHv));
        int xs = Math.max(8, Math.round(closeR[3] * 0.50f));
        NewUi.tex(c, FX_CLOSE, closeR[0] + (closeR[2] - xs) / 2, closeR[1] + (closeR[3] - xs) / 2, xs, FX_SIZE);
    }

    private void drawToolbar(GuiGraphicsExtractor c, int mx, int my) {
        int rad = Math.max(3, sl(8));
        float ssz = fs(16f);

        boolean sh = NewUi.in(mx, my, searchR[0], searchR[1], searchR[2], searchR[3]) || (searchField != null && searchField.isFocused());
        int sc = sh ? FIELD_HOV : FIELD;
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

        boolean dh = NewUi.in(mx, my, dropR[0], dropR[1], dropR[2], dropR[3]) || dropOpen;
        int dc = dh ? FIELD_HOV : FIELD;
        NewUi.rrect(c, dropR[0], dropR[1], dropR[2], dropR[3], rad, dc, dc);
        text(c, CATEGORIES[category], dropR[0] + sl(15), NewFont.centerYUi(dropR[1], dropR[3], ssz), TXT_GREY, NewFont.W_BOLD, ssz);
        int cs = Math.max(8, sl(13));
        int u = (I_CHEV % 8) * ICON_CELL;
        int v = (I_CHEV / 8) * ICON_CELL;
        c.blit(RenderPipelines.GUI_TEXTURED, ICONS, dropR[0] + dropR[2] - sl(17) - cs, dropR[1] + (dropR[3] - cs) / 2,
                u, v, cs, cs, ICON_CELL, ICON_CELL, ICONS_W, ICONS_H, TXT_GREY);
    }

    // ------------------------------------------------------------------ category dropdown
    private int[] dropListRect() {
        int rh = Math.max(14, sl(36));
        int pad = sl(6);
        return new int[]{dropR[0], dropR[1] + dropR[3] + sl(6), dropR[2], rh * CATEGORIES.length + pad * 2};
    }

    private boolean dropListContains(double mx, double my) {
        int[] r = dropListRect();
        return NewUi.in(mx, my, r[0], r[1], r[2], r[3]);
    }

    private void drawDropList(GuiGraphicsExtractor c, int mx, int my) {
        if (!dropOpen) {
            return;
        }
        int[] r = dropListRect();
        int rad = Math.max(3, sl(8));
        NewUi.rrect(c, r[0], r[1], r[2], r[3], rad, NewUi.rgb(19, 21, 25), NewUi.rgb(17, 19, 22));
        NewButtonArt.edge(c, r[0], r[1], r[2], r[3], rad, 0.26f);
        int rh = Math.max(14, sl(36));
        int pad = sl(6);
        float sz = fs(16f);
        for (int i = 0; i < CATEGORIES.length; i++) {
            int ry = r[1] + pad + i * rh;
            boolean over = NewUi.in(mx, my, r[0] + pad, ry, r[2] - pad * 2, rh);
            if (over) {
                int hc = NewUi.withAlpha(0xFFFFFFFF, 0.06f);
                NewUi.rrect(c, r[0] + pad, ry, r[2] - pad * 2, rh, Math.max(3, sl(6)), hc, hc);
            }
            text(c, CATEGORIES[i], r[0] + sl(14), NewFont.centerYUi(ry, rh, sz),
                    i == category ? TXT : (over ? TXT : TXT_GREY), NewFont.W_BOLD, sz);
        }
    }

    // ------------------------------------------------------------------ card grid
    private void drawGrid(GuiGraphicsExtractor c, int mx, int my, List<CardEntry> vis) {
        if (vis.isEmpty()) {
            String msg = "No servers match that search";
            float sz = fs(18f);
            text(c, msg, viewR[0] + (viewR[2] - tw(msg, NewFont.W_MED, sz)) / 2, viewR[1] + sl(40), TXT_GREY, NewFont.W_MED, sz);
            return;
        }
        int hot = (pressing && dragScrolling) || thumbDragging ? -1 : cardAt(mx, my, vis.size());
        c.enableScissor(viewR[0], viewR[1], viewR[0] + viewR[2], viewR[1] + viewR[3]);
        for (int i = 0; i < vis.size(); i++) {
            int y = cardY(i);
            if (y + cardH < viewR[1] || y > viewR[1] + viewR[3]) {
                continue;
            }
            CardEntry e = vis.get(i);
            e.hover = approach(e.hover, i == hot ? 1f : 0f, 13f);
            drawCard(c, e, cardX(i), y, mx, my);
        }
        c.disableScissor();
    }

    private void drawCard(GuiGraphicsExtractor c, CardEntry e, int x, int y, int mx, int my) {
        int w = cardW;
        int h = cardH;
        float t = e.hover;
        int r = Math.max(3, sl(12));

        // picture (every card crops a different part of the shared picture), dimmed; the top part gets darker and
        // the lower part lights up while the card is hovered
        int regionW = Math.min(BG_W, Math.round(BG_H * (float) w / h));
        int u = Math.round(e.pan * (BG_W - regionW));
        c.blit(RenderPipelines.GUI_TEXTURED, CARD_BG, x, y, u, 0, w, h, regionW, BG_H, BG_W, BG_H);
        int h1 = Math.min(h, sl(HOVER_TOP_H));
        c.fill(x, y, x + w, y + h1, blackAlpha(CARD_DIM + (0.90f - CARD_DIM) * t));
        c.fill(x, y + h1, x + w, y + h, blackAlpha(CARD_DIM * (1f - t) + 0.02f * t));
        int sh = Math.round(h * 0.42f);
        c.fillGradient(x, y + h - sh, x + w, y + h, 0x00000000, 0xA0000000);
        NewUi.cutCorners(c, x, y, w, h, r, PANEL_T);
        NewButtonArt.edge(c, x, y, w, h, r, 0.16f + 0.10f * t);

        int pad = sl(17);

        // description (MOTD) - fades away while the buttons are shown
        float mA = 1f - 0.82f * t;
        float msz = fs(15.5f);
        int flagW = sl(28);
        drawMotd(c, e, x + pad, y, w - pad * 2 - flagW - sl(8), w - pad * 2, msz, mA);

        // country flag, top right
        drawFlag(c, e.country, x + w - pad - flagW, y + sl(18), flagW, sl(20));

        // server icon, bottom left
        int is = sl(50);
        int ix = x + sl(19);
        int iy = y + h - sl(66);
        if (e.faviconTexture != null) {
            c.blit(RenderPipelines.GUI_TEXTURED, e.faviconTexture, ix, iy, 0, 0, is, is, 64, 64, 64, 64);
        } else if (e.isLoading()) {
            drawSpinner(c, ix, iy, is);
        } else {
            // finished without an icon (offline / no icon): the quiet Fhasix mark, no coloured badge
            int m = Math.round(is * 0.8f);
            c.blit(RenderPipelines.GUI_TEXTURED, LOGO_MARK, ix + (is - m) / 2, iy + (is - m) / 2, 0, 0,
                    m, m, MARK_TEX, MARK_TEX, MARK_TEX, MARK_TEX, 0x55FFFFFF);
        }

        // players (green dot + count) and the name, bottom right
        int right = x + w - pad;
        String pc = e.playersLabel();
        float psz = fs(13.5f);
        int pcw = tw(pc, NewFont.W_BOLD, psz);
        int pcy = y + sl(194);
        text(c, pc, right - pcw, NewFont.centerYUi(pcy - sl(11), sl(22), psz), TXT_SOFT, NewFont.W_BOLD, psz);
        int dot = Math.max(3, sl(10));
        int dx = right - pcw - sl(9) - dot;
        int dcol = 0xFF000000 | e.dotColor();
        NewUi.rrect(c, dx, pcy - dot / 2, dot, dot, dot / 2, dcol, dcol);

        float nsz = fs(21f);
        String name = NewFont.fitUi(font, e.name, NewFont.W_BOLD, nsz, w - pad * 2 - is - sl(24));
        text(c, name, right - tw(name, NewFont.W_BOLD, nsz), NewFont.centerYUi(y + sl(217) - sl(16), sl(32), nsz), TXT, NewFont.W_BOLD, nsz);

        // Join Server / Add to Server List
        if (t > 0.03f) {
            drawCardButton(c, joinRect(x, y), "Join Server", t, mx, my);
            drawCardButton(c, addRect(x, y), e.addLabel(), t, mx, my);
        }
    }

    private void drawCardButton(GuiGraphicsExtractor c, int[] r, String label, float t, int mx, int my) {
        boolean over = t > 0.6f && NewUi.in(mx, my, r[0], r[1], r[2], r[3]);
        int top = NewUi.lerp(NewUi.redT(), NewUi.redHT(), over ? 1f : 0f);
        int bot = NewUi.lerp(NewUi.redB(), NewUi.redHB(), over ? 1f : 0f);
        NewUi.rrect(c, r[0], r[1], r[2], r[3], Math.max(3, sl(7)), NewUi.withAlpha(top, t), NewUi.withAlpha(bot, t));
        float sz = fs(15.5f);
        text(c, label, r[0] + (r[2] - tw(label, NewFont.W_BOLD, sz)) / 2, NewFont.centerYUi(r[1], r[3], sz),
                NewUi.withAlpha(TXT, t), NewFont.W_BOLD, sz);
    }

    /** The server's own description in its own colours (2 lines), or its address while nothing has arrived. */
    private void drawMotd(GuiGraphicsExtractor c, CardEntry e, int x, int cardY, int maxW1, int maxW2, float size, float alpha) {
        if (alpha < 0.03f) {
            return;
        }
        List<List<CardEntryMotd>> lines = e.motdLines;
        if (lines == null || lines.isEmpty()) {
            String s = NewFont.fitUi(font, e.address, NewFont.W_BOLD, size, maxW1);
            text(c, s, x, NewFont.centerYUi(cardY + sl(22) - sl(11), sl(22), size), NewUi.withAlpha(TXT_GREY, alpha), NewFont.W_BOLD, size);
            return;
        }
        for (int li = 0; li < Math.min(2, lines.size()); li++) {
            int cy = cardY + sl(22) + li * sl(22);
            int ty = NewFont.centerYUi(cy - sl(11), sl(22), size);
            int maxW = li == 0 ? maxW1 : maxW2;
            int cx = x;
            for (CardEntryMotd seg : lines.get(li)) {
                String s = seg.text;
                int col = NewUi.withAlpha(0xFF000000 | seg.rgb, alpha);
                int sw = tw(s, NewFont.W_BOLD, size);
                if (cx - x + sw > maxW) {
                    int n = s.length();
                    while (n > 0 && cx - x + tw(s.substring(0, n) + "..", NewFont.W_BOLD, size) > maxW) {
                        n--;
                    }
                    text(c, s.substring(0, n) + "..", cx, ty, col, NewFont.W_BOLD, size);
                    break;
                }
                text(c, s, cx, ty, col, NewFont.W_BOLD, size);
                cx += sw;
            }
        }
    }

    /** Loading spinner with no background: a ring of dots, the bright head runs round and the tail fades out. */
    private void drawSpinner(GuiGraphicsExtractor c, int x, int y, int size) {
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

    // ------------------------------------------------------------------ flags (drawn in code, no images)
    private void drawFlag(GuiGraphicsExtractor c, String code, int x, int y, int w, int h) {
        if (code == null || code.isEmpty()) {
            return;
        }
        switch (code) {
            case "IN": {
                int a = h / 3;
                c.fill(x, y, x + w, y + a, 0xFFFF9933);
                c.fill(x, y + a, x + w, y + h - a, 0xFFFFFFFF);
                c.fill(x, y + h - a, x + w, y + h, 0xFF138808);
                int d = Math.max(2, h / 4);
                c.fill(x + w / 2 - d / 2, y + h / 2 - d / 2, x + w / 2 + d / 2 + (d % 2), y + h / 2 + d / 2 + (d % 2), 0xFF000080);
                break;
            }
            case "DE": {
                int a = h / 3;
                c.fill(x, y, x + w, y + a, 0xFF000000);
                c.fill(x, y + a, x + w, y + h - a, 0xFFDD0000);
                c.fill(x, y + h - a, x + w, y + h, 0xFFFFCE00);
                break;
            }
            case "US": {
                for (int i = 0; i < 7; i++) {
                    int y0 = y + h * i / 7;
                    int y1 = y + h * (i + 1) / 7;
                    c.fill(x, y0, x + w, y1, i % 2 == 0 ? 0xFFB22234 : 0xFFFFFFFF);
                }
                c.fill(x, y, x + w * 45 / 100, y + h * 4 / 7, 0xFF3C3B6E);
                break;
            }
            case "FR": {
                int a = w / 3;
                c.fill(x, y, x + a, y + h, 0xFF0055A4);
                c.fill(x + a, y, x + w - a, y + h, 0xFFFFFFFF);
                c.fill(x + w - a, y, x + w, y + h, 0xFFEF4135);
                break;
            }
            case "IT": {
                int a = w / 3;
                c.fill(x, y, x + a, y + h, 0xFF009246);
                c.fill(x + a, y, x + w - a, y + h, 0xFFFFFFFF);
                c.fill(x + w - a, y, x + w, y + h, 0xFFCE2B37);
                break;
            }
            case "NL": {
                int a = h / 3;
                c.fill(x, y, x + w, y + a, 0xFFAE1C28);
                c.fill(x, y + a, x + w, y + h - a, 0xFFFFFFFF);
                c.fill(x, y + h - a, x + w, y + h, 0xFF21468B);
                break;
            }
            default:
                return;
        }
        int edge = 0x40000000;
        c.fill(x, y, x + w, y + 1, edge);
        c.fill(x, y + h - 1, x + w, y + h, edge);
        c.fill(x, y + 1, x + 1, y + h - 1, edge);
        c.fill(x + w - 1, y + 1, x + w, y + h - 1, edge);
    }

    // ------------------------------------------------------------------ scrollbar
    /** {trackX, trackY, trackW, trackH, thumbY, thumbH, maxScroll} */
    private int[] scrollGeo(int count) {
        int tx = sx(SCROLL_X);
        int tw = Math.max(2, sl(SCROLL_W));
        int ty = viewR[1];
        int th = viewR[3];
        float max = maxScroll(count);
        if (max <= 0f) {
            return new int[]{tx, ty, tw, th, ty, th, 0};
        }
        int thumbH = Math.max(sl(30), Math.round(th * (th / (th + max))));
        int thumbY = ty + Math.round((th - thumbH) * (scrollCur / max));
        return new int[]{tx, ty, tw, th, thumbY, thumbH, Math.round(max)};
    }

    private void drawScrollbar(GuiGraphicsExtractor c, int count) {
        if (count == 0) {
            return;
        }
        int[] g = scrollGeo(count);
        NewUi.rrect(c, g[0], g[1], g[2], g[3], g[2] / 2, NewUi.rgb(20, 22, 26), NewUi.rgb(20, 22, 26));
        int thumb = thumbDragging ? NewUi.rgb(120, 124, 131) : NewUi.rgb(84, 88, 95);
        NewUi.rrect(c, g[0], g[4], g[2], g[5], g[2] / 2, thumb, thumb);
    }

    private void dragThumbTo(double my, int count) {
        int[] g = scrollGeo(count);
        if (g[6] <= 0) {
            return;
        }
        double travelPx = g[3] - g[5];
        if (travelPx <= 0) {
            return;
        }
        double frac = Math.max(0.0, Math.min(1.0, ((my - thumbGrab) - g[1]) / travelPx));
        scrollTarget = (float) (frac * g[6]);
        scrollCur = scrollTarget;
    }

    // ------------------------------------------------------------------ server events window
    private int[] eventsBox() {
        return rect(new int[]{476, 440, 550, 280});
    }

    private void drawEvents(GuiGraphicsExtractor c, int mx, int my) {
        c.fill(0, 0, width, height, 0x99000000);
        int[] b = eventsBox();
        int rad = Math.max(3, sl(14));
        NewUi.rrect(c, b[0], b[1], b[2], b[3], rad, NewUi.rgb(19, 21, 25), NewUi.rgb(15, 17, 20));
        NewButtonArt.edge(c, b[0], b[1], b[2], b[3], rad, 0.26f);
        float tsz = fs(27f);
        String t = "SERVER EVENTS";
        text(c, t, b[0] + (b[2] - tw(t, NewFont.W_BOLD, tsz)) / 2, NewFont.centerYUi(b[1] + sl(30), sl(40), tsz), TXT, NewFont.W_BOLD, tsz);
        float msz = fs(18f);
        String m1 = "No server events are live right now.";
        String m2 = "Check back soon.";
        text(c, m1, b[0] + (b[2] - tw(m1, NewFont.W_MED, msz)) / 2, NewFont.centerYUi(b[1] + sl(110), sl(34), msz), TXT_SOFT, NewFont.W_MED, msz);
        text(c, m2, b[0] + (b[2] - tw(m2, NewFont.W_MED, msz)) / 2, NewFont.centerYUi(b[1] + sl(150), sl(34), msz), TXT_GREY, NewFont.W_MED, msz);
        int bw = sl(180);
        int bh = sl(46);
        int bx = b[0] + (b[2] - bw) / 2;
        int by = b[1] + b[3] - bh - sl(28);
        boolean over = NewUi.in(mx, my, bx, by, bw, bh);
        NewUi.rrect(c, bx, by, bw, bh, Math.max(3, sl(8)), NewUi.lerp(NewUi.redT(), NewUi.redHT(), over ? 1f : 0f), NewUi.lerp(NewUi.redB(), NewUi.redHB(), over ? 1f : 0f));
        String ok = "Close";
        float osz = fs(18f);
        text(c, ok, bx + (bw - tw(ok, NewFont.W_BOLD, osz)) / 2, NewFont.centerYUi(by, bh, osz), TXT, NewFont.W_BOLD, osz);
    }

    // ------------------------------------------------------------------ actions
    private void joinServer(CardEntry e) {
        ServerData info = new ServerData(e.name, e.address, ServerData.Type.OTHER);
        ServerAddress address = ServerAddress.parseString(e.address);
        ConnectScreen.startConnecting(parent, minecraft, address, info, false, null);
    }

    /** Adds the server to the normal Multiplayer list (once). */
    private void addToServerList(CardEntry e) {
        try {
            ServerList list = new ServerList(minecraft);
            list.load();
            for (int i = 0; i < list.size(); i++) {
                ServerData existing = list.get(i);
                if (existing != null && existing.ip != null && existing.ip.equalsIgnoreCase(e.address)) {
                    e.addedState = 2;
                    e.addedUntil = System.currentTimeMillis() + 2200L;
                    return;
                }
            }
            list.add(new ServerData(e.name, e.address, ServerData.Type.OTHER), false);
            list.save();
            e.addedState = 1;
        } catch (RuntimeException ex) {
            e.addedState = 3;
        }
        e.addedUntil = System.currentTimeMillis() + 2200L;
    }

    // ------------------------------------------------------------------ input
    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.buttonInfo().button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return true;
        }
        double mx = click.x();
        double my = click.y();

        if (eventsOpen) {
            NewUi.click(minecraft);
            eventsOpen = false;
            return true;
        }
        if (dropOpen) {
            int[] r = dropListRect();
            if (NewUi.in(mx, my, r[0], r[1], r[2], r[3])) {
                int rh = Math.max(14, sl(36));
                int pad = sl(6);
                int i = (int) ((my - r[1] - pad) / rh);
                if (i >= 0 && i < CATEGORIES.length) {
                    NewUi.click(minecraft);
                    category = i;
                    scrollTarget = 0f;
                    scrollCur = 0f;
                }
            }
            dropOpen = false;
            return true;
        }
        if (super.mouseClicked(click, doubled)) {
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
        if (NewUi.in(mx, my, eventsR[0], eventsR[1], eventsR[2], eventsR[3])) {
            NewUi.click(minecraft);
            eventsOpen = true;
            return true;
        }
        if (NewUi.in(mx, my, dropR[0], dropR[1], dropR[2], dropR[3])) {
            NewUi.click(minecraft);
            dropOpen = true;
            return true;
        }

        List<CardEntry> vis = visible();
        int[] g = scrollGeo(vis.size());
        if (g[6] > 0 && NewUi.in(mx, my, g[0] - sl(12), g[1], g[2] + sl(24), g[3])) {
            boolean onThumb = my >= g[4] && my < g[4] + g[5];
            thumbGrab = onThumb ? my - g[4] : g[5] / 2.0;
            thumbDragging = true;
            if (!onThumb) {
                dragThumbTo(my, vis.size());
            }
            return true;
        }
        if (NewUi.in(mx, my, viewR[0], viewR[1], viewR[2], viewR[3])) {
            pressing = true;
            dragScrolling = false;
            travel = 0.0;
            dragVel = 0f;
            lastDragNs = System.nanoTime();
            pressCard = cardAt(mx, my, vis.size());
            pressBtn = buttonAt(vis, pressCard, mx, my);
        }
        return true;
    }

    /** Which button of a card is under the point (only once the card has been revealed): 0 none, 1 join, 2 add. */
    private int buttonAt(List<CardEntry> vis, int idx, double mx, double my) {
        if (idx < 0 || idx >= vis.size() || vis.get(idx).hover < 0.6f) {
            return 0;
        }
        int x = cardX(idx);
        int y = cardY(idx);
        int[] j = joinRect(x, y);
        if (NewUi.in(mx, my, j[0], j[1], j[2], j[3])) {
            return 1;
        }
        int[] a = addRect(x, y);
        return NewUi.in(mx, my, a[0], a[1], a[2], a[3]) ? 2 : 0;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        if (thumbDragging) {
            dragThumbTo(click.y(), visible().size());
            return true;
        }
        if (pressing) {
            travel += Math.abs(offsetX) + Math.abs(offsetY);
            if (!dragScrolling && travel > 6.0) {
                dragScrolling = true;
            }
            if (dragScrolling) {
                scrollTarget -= (float) offsetY;
                long now = System.nanoTime();
                float dt = Math.max(1f / 240f, (now - lastDragNs) / 1_000_000_000f);
                lastDragNs = now;
                dragVel = dragVel * 0.55f + ((float) -offsetY / dt) * 0.45f;
            }
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (thumbDragging) {
            thumbDragging = false;
            return true;
        }
        if (pressing) {
            pressing = false;
            boolean wasDrag = dragScrolling;
            dragScrolling = false;
            int card = pressCard;
            int btn = pressBtn;
            pressCard = -1;
            pressBtn = 0;
            if (wasDrag) {
                // lifted while still moving: let the grid glide a little further
                boolean moving = System.nanoTime() - lastDragNs < 90_000_000L;
                if (moving) {
                    scrollTarget -= Math.max(-3000f, Math.min(3000f, -dragVel)) * 0.16f;
                }
                return true;
            }
            if (btn != 0 && click.buttonInfo().button() == InputConstants.MOUSE_BUTTON_LEFT) {
                List<CardEntry> vis = visible();
                int now = cardAt(click.x(), click.y(), vis.size());
                if (now == card && card >= 0 && card < vis.size() && buttonAt(vis, now, click.x(), click.y()) == btn) {
                    NewUi.click(minecraft);
                    if (btn == 1) {
                        joinServer(vis.get(card));
                    } else {
                        addToServerList(vis.get(card));
                    }
                }
            }
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollTarget -= (float) verticalAmount * Math.max(30f, 90f * k);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (keyEvent.key() == InputConstants.KEY_ESCAPE) {
            if (eventsOpen) {
                eventsOpen = false;
                return true;
            }
            if (dropOpen) {
                dropOpen = false;
                return true;
            }
            closeScreen();
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    // ------------------------------------------------------------------ one coloured piece of a description
    private static final class CardEntryMotd {
        final String text;
        final int rgb;

        CardEntryMotd(String text, int rgb) {
            this.text = text;
            this.rgb = rgb;
        }
    }

    // ------------------------------------------------------------------ one server card
    private static final class CardEntry {
        final String name;
        final String address;
        final String[] tags;
        final String initials;
        final int[] badgeTop;
        final int[] badgeBottom;
        final String country;
        /** 0..1 where each card's picture is cropped from the shared picture, so the cards do not all look alike. */
        final float pan;

        float hover = 0f;

        volatile int ping = -1;
        volatile int players = -1;
        volatile boolean pingFailed = false;
        volatile Identifier faviconTexture;
        volatile List<List<CardEntryMotd>> motdLines;
        private volatile boolean closed = false;

        int addedState = 0; // 1 added, 2 already in the list, 3 failed
        long addedUntil = 0L;

        CardEntry(String name, String address, String tagsCsv, String country) {
            this.name = name;
            this.address = address;
            this.tags = tagsCsv.split("\\s*,\\s*");
            this.initials = initials(name);
            int hue = Math.floorMod(name.hashCode(), 360);
            this.badgeTop = hsvToRgb(hue, 0.55f, 0.5f);
            this.badgeBottom = hsvToRgb(hue, 0.65f, 0.28f);
            this.country = country != null && !country.isEmpty() ? country : countryOf(address);
            this.pan = Math.floorMod(address.hashCode() * 31 + name.hashCode(), 1000) / 999f;
        }

        private static String countryOf(String address) {
            String host = address.toLowerCase(Locale.ROOT);
            int colon = host.indexOf(':');
            if (colon > 0) {
                host = host.substring(0, colon);
            }
            if (host.endsWith(".in")) return "IN";
            if (host.endsWith(".de")) return "DE";
            if (host.endsWith(".us")) return "US";
            if (host.endsWith(".fr")) return "FR";
            if (host.endsWith(".nl")) return "NL";
            if (host.endsWith(".it")) return "IT";
            return "";
        }

        private static String initials(String name) {
            String[] parts = name.replace(".", " ").trim().split("\\s+");
            StringBuilder sb = new StringBuilder();
            for (String part : parts) {
                if (!part.isEmpty() && sb.length() < 2) {
                    sb.append(Character.toUpperCase(part.charAt(0)));
                }
            }
            return sb.length() > 0 ? sb.toString() : "?";
        }

        private static int[] hsvToRgb(int hueDeg, float sat, float val) {
            float c = val * sat;
            float x = c * (1 - Math.abs((hueDeg / 60f) % 2 - 1));
            float m = val - c;
            float r, g, b;
            if (hueDeg < 60) { r = c; g = x; b = 0; }
            else if (hueDeg < 120) { r = x; g = c; b = 0; }
            else if (hueDeg < 180) { r = 0; g = c; b = x; }
            else if (hueDeg < 240) { r = 0; g = x; b = c; }
            else if (hueDeg < 300) { r = x; g = 0; b = c; }
            else { r = c; g = 0; b = x; }
            return new int[]{Math.round((r + m) * 255), Math.round((g + m) * 255), Math.round((b + m) * 255)};
        }

        boolean hasTag(String tag) {
            for (String t : tags) {
                if (t.equalsIgnoreCase(tag)) {
                    return true;
                }
            }
            return false;
        }

        /** True until the ping has finished (success or failure). */
        boolean isLoading() {
            return !pingFailed && players < 0;
        }

        String playersLabel() {
            if (pingFailed) return "offline";
            if (players < 0) return "...";
            return Integer.toString(players);
        }

        int dotColor() {
            if (pingFailed) return DOT_OFF;
            if (players < 0) return DOT_WAIT;
            return DOT_ON;
        }

        /** Label of the second hover button; shows the result for a moment after it was pressed. */
        String addLabel() {
            if (addedState != 0 && System.currentTimeMillis() < addedUntil) {
                return addedState == 1 ? "Added!" : addedState == 2 ? "Already added" : "Could not add";
            }
            addedState = 0;
            return "Add to Server List";
        }

        void startPing(Minecraft minecraft) {
            FhasixServerPinger.pingAsync(address, result -> {
                if (closed) {
                    return;
                }
                if (!result.online) {
                    pingFailed = true;
                    return;
                }
                ping = result.pingMs;
                players = result.playersOnline;
                pingFailed = false;
                motdLines = buildLines(result.motd);
                if (result.favicon != null) {
                    loadFavicon(minecraft, result.favicon);
                }
            });
        }

        /** Cleans the description for the screen font: plain letters / digits / symbols only, at most 2 lines. */
        private static List<List<CardEntryMotd>> buildLines(List<FhasixServerPinger.MotdSeg> segs) {
            List<List<CardEntryMotd>> lines = new ArrayList<>();
            List<CardEntryMotd> cur = new ArrayList<>();
            for (FhasixServerPinger.MotdSeg seg : segs) {
                StringBuilder sb = new StringBuilder();
                int rgb = readable(seg.rgb);
                for (int i = 0; i < seg.text.length(); i++) {
                    char ch = seg.text.charAt(i);
                    if (ch == '\n') {
                        flush(cur, sb, rgb);
                        lines.add(cur);
                        cur = new ArrayList<>();
                    } else if (ch >= 0x20 && ch < 0x7F) {
                        sb.append(ch);
                    }
                }
                flush(cur, sb, rgb);
            }
            lines.add(cur);

            List<List<CardEntryMotd>> out = new ArrayList<>();
            for (List<CardEntryMotd> line : lines) {
                List<CardEntryMotd> clean = new ArrayList<>();
                for (CardEntryMotd s : line) {
                    String t = s.text.replaceAll(" {2,}", " ");
                    if (!t.isEmpty()) {
                        clean.add(new CardEntryMotd(t, s.rgb));
                    }
                }
                if (!clean.isEmpty()) {
                    CardEntryMotd first = clean.get(0);
                    clean.set(0, new CardEntryMotd(stripStart(first.text), first.rgb));
                    CardEntryMotd last = clean.get(clean.size() - 1);
                    clean.set(clean.size() - 1, new CardEntryMotd(stripEnd(last.text), last.rgb));
                    boolean any = false;
                    for (CardEntryMotd s : clean) {
                        if (!s.text.isEmpty()) {
                            any = true;
                        }
                    }
                    if (any) {
                        out.add(Collections.unmodifiableList(clean));
                    }
                }
                if (out.size() >= 2) {
                    break;
                }
            }
            return Collections.unmodifiableList(out);
        }

        private static void flush(List<CardEntryMotd> line, StringBuilder sb, int rgb) {
            if (sb.length() > 0) {
                line.add(new CardEntryMotd(sb.toString(), rgb));
                sb.setLength(0);
            }
        }

        private static String stripStart(String s) {
            int i = 0;
            while (i < s.length() && s.charAt(i) == ' ') {
                i++;
            }
            return s.substring(i);
        }

        private static String stripEnd(String s) {
            int n = s.length();
            while (n > 0 && s.charAt(n - 1) == ' ') {
                n--;
            }
            return s.substring(0, n);
        }

        /** Default colour -> soft white; very dark colours are lifted so they can be read on the dark card. */
        private static int readable(int rgb) {
            if (rgb < 0) {
                return 0xE6E6E6;
            }
            int r = (rgb >> 16) & 255;
            int g = (rgb >> 8) & 255;
            int b = rgb & 255;
            float lum = 0.299f * r + 0.587f * g + 0.114f * b;
            if (lum >= 90f) {
                return rgb;
            }
            float lift = 0.55f;
            return (Math.round(r + (255 - r) * lift) << 16) | (Math.round(g + (255 - g) * lift) << 8) | Math.round(b + (255 - b) * lift);
        }

        private void loadFavicon(Minecraft minecraft, byte[] png) {
            try {
                NativeImage image = NativeImage.read(new ByteArrayInputStream(png));
                String safeName = address.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "_");
                Identifier id = Identifier.fromNamespaceAndPath("fhasixuitheme", "discover_favicon_" + safeName);
                minecraft.execute(() -> {
                    if (closed) {
                        image.close();
                        return;
                    }
                    // 1.21.11: NativeImageBackedTexture takes a name supplier first.
                    minecraft.getTextureManager().register(id,
                            new DynamicTexture(() -> "fhasix_favicon", image));
                    faviconTexture = id;
                });
            } catch (Exception ignored) {
                // Broken or missing icon - the initials badge stays.
            }
        }

        void close() {
            closed = true;
        }
    }
}
