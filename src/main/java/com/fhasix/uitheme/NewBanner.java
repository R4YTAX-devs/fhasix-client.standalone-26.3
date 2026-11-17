package com.fhasix.uitheme;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Title-screen side banners (bottom-right): a "check in" card while today's Fhasix Points are still unclaimed
 * (stacked on top), and a news card below it. Hovering zooms the card's content inside its box; clicking opens the Store.
 * Drawn only with fill()/NewUi/NewFont, so there are no textures to ship.
 */
public class NewBanner extends Button {
    public static final int POINTS = 0;
    public static final int NEWS = 1;

    /** News card text. Edit these two lines for each update. */
    private static final String NEWS_TITLE = "Update 5.4";
    private static final String NEWS_LINE = "More FPS - new banners";

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
        int ph = Math.max(18, Math.round(95 * s));
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
            out.add(new NewBanner(x, newsY - gap - ph, w, ph, POINTS, open));
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
        float k = Math.max(0.2f, w / 400f);          // design pixels -> screen
        float z = 1f + 0.10f * zoomT;                 // content zoom on hover (the card itself stays put)
        int rad = Math.max(3, Math.round(h * 0.14f));
        NewUi.frame(c, x, y, w, h, rad, NewUi.CARD_BORDER, NewUi.CARD_T, NewUi.CARD_B);

        // gem
        int cx = x + Math.round(h * 0.52f);
        int cy = y + h / 2;
        int half = Math.max(3, Math.round(h * 0.27f * z));
        diamond(c, cx, cy, half, 0xFFC96A12);
        diamond(c, cx, cy, Math.max(2, Math.round(half * 0.78f)), 0xFFEE8F25);
        diamond(c, cx, cy, Math.max(1, Math.round(half * 0.42f)), 0xFFFFD27A);
        int reward;
        try {
            reward = FhasixPoints.nextReward();
        } catch (Exception | LinkageError t) {
            reward = 5;
        }
        float small = 13f * k * z;
        ui(c, "+" + reward, cx + half - Math.round(h * 0.02f), y + Math.round(h * 0.06f), 0xFFE8892B, NewFont.W_BOLD, 11f * k);

        // text
        int tx = x + Math.round(h * 1.05f);
        float lineSize = 15f * k * z;
        int ly1 = y + Math.round(h * 0.26f);
        int ly2 = y + Math.round(h * 0.55f);
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
