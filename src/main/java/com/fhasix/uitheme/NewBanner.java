package com.fhasix.uitheme;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Title-screen side banners (bottom-right): a "check in" card while today's Fhasix Points are still unclaimed
 * (stacked on top), and a news card below it. Hovering zooms the card's content inside its box; clicking opens the Store.
 * The points card uses the main button look (NewButtonArt.navy) and the Store points gem; the news card is drawn in code.
 */
public class NewBanner extends Button {
    public static final int POINTS = 0;
    public static final int NEWS = 1;

    /** News card text. Edit these two lines for each update. */
    private static final String NEWS_TITLE = "Update 5.8";
    private static final String NEWS_LINE = "Motion Blur - Minimap - Waypoints";

    /** The same points gem the Store and Cosmetics use. */
    private static final Identifier GEM = Identifier.fromNamespaceAndPath("fhasixuitheme", "textures/gui/store/store_gem.png");
    private static final int GEM_TEX = 128;
    private static final int GREEN = NewUi.rgb(62, 200, 82);

    private final int kind;
    private float zoomT = 0f;
    private long lastNs = 0L;

    private NewBanner(int x, int y, int w, int h, int kind, Button.OnPress onPress) {
        super(x, y, w, h, Component.literal(kind == POINTS ? "Daily points" : "News"), onPress, DEFAULT_NARRATION);
        this.kind = kind;
    }

    /** Builds the banners for a screen of the given size. The points card is left out when already claimed today. */
    public static List<NewBanner> create(int sw, int sh, Button.OnPress open) {
        List<NewBanner> out = new ArrayList<>();
        float s = NewUi.scale(sw, sh);
        int w = Math.max(60, Math.round(400 * s));
        int pw = w;                                   // same width as the news card
        int ph = Math.max(18, Math.round(84 * s));
        int nh = Math.max(34, Math.round(215 * s));
        int gap = Math.max(2, Math.round(14 * s));
        int margin = Math.max(4, Math.round(26 * s));
        int x = sw - margin - w;
        int newsY = sh - margin - nh;
        out.add(new NewBanner(x, newsY, w, nh, NEWS, open));
        boolean canClaim;
        try {
            canClaim = FhasixPoints.canClaim();
        } catch (Exception | LinkageError t) {
            canClaim = false;
        }
        if (canClaim) {
            out.add(new NewBanner(x + (w - pw), newsY - gap - ph, pw, ph, POINTS, open));
        }
        return out;
    }

    @Override
    public void extractContents(GuiGraphicsExtractor c, int mouseX, int mouseY, float delta) {
        try {
            long now = System.nanoTime();
            float dt = lastNs == 0L ? 0f : Math.min(0.1f, (now - lastNs) / 1_000_000_000f);
            lastNs = now;
            float target = this.isHovered() ? 1f : 0f;
            zoomT += (target - zoomT) * (1f - (float) Math.exp(-14f * dt));
            if (Math.abs(target - zoomT) < 0.003f) {
                zoomT = target;
            }
            if (kind == POINTS) {
                drawPoints(c, this.getX(), this.getY(), this.getWidth(), this.getHeight());
            } else {
                drawNews(c, this.getX(), this.getY(), this.getWidth(), this.getHeight());
            }
        } catch (Exception | LinkageError t) {
            NewSafe.failed("banner", t);
        }
    }

    private static void ui(GuiGraphicsExtractor c, String s, int x, int y, int color, int weight, float size) {
        NewFont.drawUi(c, Minecraft.getInstance().font, s, x, y, color, weight, size);
    }

    private static int uw(String s, int weight, float size) {
        return NewFont.widthUi(Minecraft.getInstance().font, s, weight, size);
    }

    /** A filled diamond made from one-pixel rows. */
    private static void diamond(GuiGraphicsExtractor c, int cx, int cy, int half, int color) {
        for (int dy = -half; dy <= half; dy++) {
            int hw = half - Math.abs(dy);
            c.fill(cx - hw, cy + dy, cx + hw + 1, cy + dy + 1, color);
        }
    }

    private void drawPoints(GuiGraphicsExtractor c, int x, int y, int w, int h) {
        float z = 1f + 0.08f * zoomT;                 // content zoom on hover (the card itself stays put)
        // same look as the Singleplayer / Multiplayer / Cosmetics buttons
        NewButtonArt.navy(c, x, y, w, h, zoomT);

        int reward;
        try {
            reward = FhasixPoints.nextReward();
        } catch (Exception | LinkageError t) {
            reward = 5;
        }

        // the Fhasix points gem (same texture as the Store)
        int gs = Math.max(8, Math.round(h * 0.62f));
        int gx = x + Math.round(h * 0.22f);
        int gy = y + (h - gs) / 2;
        int gz = Math.max(8, Math.round(gs * z));
        NewUi.tex(c, GEM, gx + (gs - gz) / 2, gy + (gs - gz) / 2, gz, GEM_TEX);
        ui(c, "+" + reward, gx + gs - Math.round(h * 0.04f), y + Math.round(h * 0.07f), GREEN, NewFont.W_BOLD, h * 0.16f);

        // text
        float lineSize = h * 0.23f * z;
        int tx = gx + gs + Math.round(h * 0.34f);
        int ly1 = y + Math.round((h - lineSize * 2.45f) / 2f);
        int ly2 = ly1 + Math.round(lineSize * 1.45f);
        ui(c, "Check in now", tx, ly1, 0xFFB9BBC2, NewFont.W_MED, lineSize);
        int cur = tx;
        ui(c, "for ", cur, ly2, 0xFFB9BBC2, NewFont.W_MED, lineSize);
        cur += uw("for ", NewFont.W_MED, lineSize);
        String pts = reward + " Points";
        ui(c, pts, cur, ly2, 0xFFFFFFFF, NewFont.W_BOLD, lineSize);
        cur += uw(pts, NewFont.W_BOLD, lineSize);
        ui(c, "!", cur, ly2, 0xFFB9BBC2, NewFont.W_MED, lineSize);
    }

    private void drawNews(GuiGraphicsExtractor c, int x, int y, int w, int h) {
        float k = Math.max(0.2f, w / 400f);
        float z = 1f + 0.12f * zoomT;
        int rad = Math.max(3, Math.round(h * 0.06f));
        int lift = Math.round(18 * zoomT);            // the card gets a touch brighter on hover
        int top = NewUi.rgb(255, Math.min(255, 158 + lift / 2), 52);
        int bot = NewUi.rgb(Math.min(255, 224 + lift / 2), 104, 6);
        NewUi.rrect(c, x, y, w, h, rad, top, bot);

        // faint pattern of small diamonds in the background
        int step = Math.max(10, Math.round(h * 0.22f));
        int mark = Math.max(1, step / 6);
        for (int py = y + step / 2; py < y + h - step / 2; py += step) {
            for (int px = x + step / 2 + ((py - y) / step % 2) * (step / 2); px < x + w - step / 2; px += step) {
                diamond(c, px, py, mark, 0x22FFFFFF);
            }
        }

        // title (soft shadow first)
        float tsz = 30f * k * z;
        int tw = uw(NEWS_TITLE, NewFont.W_BOLD, tsz);
        if (tw > w * 0.90f) {                         // long title: shrink to fit the card
            tsz *= (w * 0.90f) / tw;
            tw = uw(NEWS_TITLE, NewFont.W_BOLD, tsz);
        }
        int tx = x + (w - tw) / 2;
        int ty = y + Math.round(h * 0.20f);
        ui(c, NEWS_TITLE, tx + 1, ty + 1, 0x55000000, NewFont.W_BOLD, tsz);
        ui(c, NEWS_TITLE, tx, ty, 0xFFFFFFFF, NewFont.W_BOLD, tsz);
        float ssz = 13f * k * z;
        int sw2 = uw(NEWS_LINE, NewFont.W_MED, ssz);
        ui(c, NEWS_LINE, x + (w - sw2) / 2, ty + Math.round(tsz * 1.15f), 0xDDFFFFFF, NewFont.W_MED, ssz);

        // heart-beat line: dark spikes on the left, fading to a plain white line on the right
        int base = y + Math.round(h * 0.78f);
        int x0 = x + Math.round(w * 0.05f);
        int x1 = x + Math.round(w * 0.95f);
        int split = x0 + Math.round((x1 - x0) * 0.55f);
        c.fill(x0, base, split, base + 1, 0xAA8A3B08);
        for (int px = split; px < x1; px++) {
            int a = 0xB0 - Math.round(0x60 * (px - split) / (float) Math.max(1, x1 - split));
            c.fill(px, base, px + 1, base + 1, (a << 24) | 0xFFFFFF);
        }
        float[][] spikes = {{0.08f, 0.10f}, {0.14f, 0.20f}, {0.17f, 0.12f}, {0.23f, 0.40f}, {0.27f, 0.07f}, {0.33f, 0.26f}, {0.40f, 0.11f}};
        float grow = 1f + 0.45f * zoomT;
        for (float[] sp : spikes) {
            int sx = x0 + Math.round((x1 - x0) * sp[0]);
            int sh2 = Math.round(h * sp[1] * grow);
            c.fill(sx, base - sh2, sx + 1, base + Math.max(1, sh2 / 3), 0xCC8A3B08);
        }
    }
}
