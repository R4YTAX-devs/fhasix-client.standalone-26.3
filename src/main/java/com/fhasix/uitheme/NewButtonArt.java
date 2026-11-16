package com.fhasix.uitheme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Draws the five main title-screen buttons purely in code (no background or label images):
 * dark navy buttons with a faint grey-blue edge that is brightest in two corners, and the red Store button.
 * hoverT (0..1) is the smooth hover fade, and ripple() draws the click ripple.
 */
public final class NewButtonArt {
    private static final int R = 4; // corner radius in GUI units

    private NewButtonArt() {}

    public static void navy(GuiGraphicsExtractor g, int x, int y, int w, int h, float hoverT) {
        solid(g, x, y, w, h, argb(255, 17, 19, 23), argb(255, 13, 15, 18));
        if (hoverT > 0.01f) {
            hGradient(g, x, y, w, h, new int[]{62, 70, 81}, new int[]{21, 25, 30}, 0f, 0.07f, false, hoverT);
        }
        edge(g, x, y, w, h, 0.20f + 0.08f * hoverT);
    }

    public static void red(GuiGraphicsExtractor g, int x, int y, int w, int h, float hoverT) {
        int[] a = FhasixTheme.channels(); // the main colour (Mod Menu -> General -> Main Color)
        int[] dark = {(int) (a[0] * 0.15f) + 15, (int) (a[1] * 0.15f) + 12, (int) (a[2] * 0.15f) + 14};
        hGradient(g, x, y, w, h, a, dark, 0.12f, 0f, true, 1f);
        if (hoverT > 0.01f) {
            int[] light = {a[0] + (int) ((255 - a[0]) * 0.18f), a[1] + (int) ((255 - a[1]) * 0.18f), a[2] + (int) ((255 - a[2]) * 0.18f)};
            int[] lightDark = {(int) (a[0] * 0.28f) + 20, (int) (a[1] * 0.28f) + 14, (int) (a[2] * 0.28f) + 16};
            hGradient(g, x, y, w, h, light, lightDark, 0.12f, 0f, true, hoverT);
        }
    }

    /**
     * Click ripple: a lighter translucent circle that grows from the click point (cx, cy, button-local units)
     * until it covers the whole button, clipped to the rounded corners. p = growth progress 0..1.
     */
    public static void ripple(GuiGraphicsExtractor g, int x, int y, int w, int h, float cx, float cy, float p) {
        float ease = 1f - (1f - p) * (1f - p) * (1f - p);
        float far = (float) Math.sqrt(Math.max(cx, w - cx) * Math.max(cx, w - cx) + Math.max(cy, h - cy) * Math.max(cy, h - cy));
        float rad = far * ease;
        float alpha = 0.18f - 0.06f * p;               // settles on a lighter button, like the footage
        int col = argb(Math.round(255 * alpha), 0xFF, 0xFF, 0xFF);
        for (int row = 0; row < h; row++) {
            float dy = row + 0.5f - cy;
            if (Math.abs(dy) > rad) continue;
            float dx = (float) Math.sqrt(rad * rad - dy * dy);
            int in = inset(Math.min(row, h - 1 - row));
            int x1 = Math.max(x + in, Math.round(x + cx - dx));
            int x2 = Math.min(x + w - in, Math.round(x + cx + dx));
            if (x2 > x1) g.fill(x1, y + row, x2, y + row + 1, col);
        }
    }

    /** Icon (small PNG) + label in the title fonts, centred as one group. inter = Inter (Store), else Product Sans. */
    public static void label(GuiGraphicsExtractor g, int x, int y, int w, int h, Identifier icon, String text,
                             boolean upper, boolean inter) {
        Font tr = Minecraft.getInstance().font;
        int iconSize = Math.round(h * 0.34f);
        int gap = Math.round(h * 0.24f);
        int tw = NewFont.widthTitle(tr, text, inter, upper);
        int group = iconSize + gap + tw;
        int gx = x + (w - group) / 2;
        g.blit(RenderPipelines.GUI_TEXTURED, icon, gx, y + (h - iconSize) / 2, 0, 0,
                iconSize, iconSize, 128, 128, 128, 128);
        NewFont.drawTitle(g, tr, text, gx + iconSize + gap, y + (h - tr.lineHeight) / 2 + NewFont.Y_TITLE,
                0xFFEDEDED, inter, upper);
    }

    // ---------------------------------------------------------------- drawing helpers

    private static int inset(int d) {
        // how many units the rounded corner cuts off a column / row that is d units away from the edge
        if (d >= R) return 0;
        return (int) Math.round(R - Math.sqrt(R * R - Math.pow(R - d - 0.5, 2)));
    }

    private static int argb(int a, int r, int gr, int b) {
        return (a << 24) | (r << 16) | (gr << 8) | b;
    }

    /** Flat rounded rectangle with a top-to-bottom gradient. */
    private static void solid(GuiGraphicsExtractor g, int x, int y, int w, int h, int top, int bottom) {
        g.fillGradient(x + R, y, x + w - R, y + h, top, bottom);
        for (int d = 0; d < R; d++) {
            int in = inset(d);
            g.fillGradient(x + d, y + in, x + d + 1, y + h - in, top, bottom);
            g.fillGradient(x + w - 1 - d, y + in, x + w - d, y + h - in, top, bottom);
        }
    }

    /**
     * Rounded rectangle with a left-to-right colour fade (plateau = share of the width that stays solid).
     * border = a continuous 1-unit edge in a slightly stronger red. alpha = overall opacity (hover fade).
     */
    private static void hGradient(GuiGraphicsExtractor g, int x, int y, int w, int h, int[] left, int[] right,
                                  float plateau, float vShade, boolean border, float alpha) {
        int a = Math.round(255 * Math.max(0f, Math.min(1f, alpha)));
        int col = 0;
        while (col < w) {
            int sw = (col < R || col >= w - R) ? 1 : 2;
            if (col + sw > w - R && col < w - R) sw = w - R - col;
            float t = (col + sw / 2f) / w;
            float tt = plateau > 0f ? Math.max(0f, Math.min(1f, (t - plateau) / (1f - plateau))) : t;
            int in = col < R ? inset(col) : (col >= w - R ? inset(w - 1 - col) : 0);
            int cr = Math.round(left[0] + (right[0] - left[0]) * tt);
            int cg = Math.round(left[1] + (right[1] - left[1]) * tt);
            int cb = Math.round(left[2] + (right[2] - left[2]) * tt);
            float k = 1f - vShade;
            int top = argb(a, cr, cg, cb);
            int bottom = argb(a, Math.round(cr * k), Math.round(cg * k), Math.round(cb * k));
            int x1 = x + col, x2 = x + col + sw, y1 = y + in, y2 = y + h - in;
            if (!border) {
                g.fillGradient(x1, y1, x2, y2, top, bottom);
            } else {
                // one steady edge colour that follows the fill, so the line never looks broken
                int br = Math.round(cr + (205 - cr) * 0.40f);
                int bg = Math.round(cg + (56 - cg) * 0.40f);
                int bb = Math.round(cb + (60 - cb) * 0.40f);
                int edge = argb(a, br, bg, bb);
                if (col == 0 || col == w - 1) {
                    g.fill(x1, y1, x2, y2, edge);
                } else {
                    g.fill(x1, y1, x2, y1 + 1, edge);
                    g.fill(x1, y2 - 1, x2, y2, edge);
                    g.fillGradient(x1, y1 + 1, x2, y2 - 1, top, bottom);
                }
            }
            col += sw;
        }
    }

    /**
     * Faint grey-blue edge, measured from the reference: brightest (about +9) in the top-left and bottom-right
     * corners and fading out linearly - about 45% of the width along the top/bottom, about 60% of the height
     * down the sides. The rounded corner itself is drawn too, so no pixels are missing there.
     */
    private static void edge(GuiGraphicsExtractor g, int x, int y, int w, int h, float strength) {
        edge(g, x, y, w, h, R, strength);
    }

    private static final Identifier RAMP_R = Identifier.fromNamespaceAndPath("fhasixuitheme", "textures/gui/edge_ramp_r.png"); // solid left -> clear right
    private static final Identifier RAMP_L = Identifier.fromNamespaceAndPath("fhasixuitheme", "textures/gui/edge_ramp_l.png"); // clear left -> solid right
    private static final int RAMP_W = 256;
    private static final Identifier RR = Identifier.fromNamespaceAndPath("fhasixuitheme", "textures/gui/rr_atlas.png");
    private static final int RR_W = 240;
    private static final int RR_H = 836;

    /**
     * The same edge the title-screen buttons have, for a shape with corner radius r (mod menu cards and tiles).
     * Six draw calls however big the shape is: two faded lines drawn from a tiny gradient texture (top and
     * bottom), two vertical gradients (left and right) and the two smooth corner arcs from the rounded-corner
     * atlas. (The first version drew about 300 single fills per edge, which made the Mod Menu lag.)
     */
    public static void edge(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, float strength) {
        r = Math.max(1, Math.min(r, Math.min(w, h) / 2));
        int peak = argb(Math.round(255 * strength), 0x3C, 0x44, 0x4D);
        int clear = argb(0, 0x3C, 0x44, 0x4D);
        int lenH = Math.max(0, Math.min(Math.round(w * 0.45f), w - 2 * r));
        int lenV = Math.max(0, Math.min(Math.round(h * 0.60f), h - 2 * r));

        // rounded corner arcs (top-left and bottom-right), 1 unit thick
        if (r >= 2) {
            int ra = Math.min(r, 20);
            int q = ra * 2;
            int u0 = 1 * ra * 4;
            int v0 = 2 * ra * (ra - 1) - 4;
            g.blit(RenderPipelines.GUI_TEXTURED, RR, x, y, u0, v0, ra, ra, q, q, RR_W, RR_H, peak);
            g.blit(RenderPipelines.GUI_TEXTURED, RR, x + w - ra, y + h - ra, u0 + q, v0 + q, ra, ra, q, q, RR_W, RR_H, peak);
        } else {
            g.fill(x, y, x + 1, y + 1, peak);
            g.fill(x + w - 1, y + h - 1, x + w, y + h, peak);
        }
        if (lenH > 0) {
            g.blit(RenderPipelines.GUI_TEXTURED, RAMP_R, x + r, y, 0, 0, lenH, 1, RAMP_W, 1, RAMP_W, 1, peak);
            g.blit(RenderPipelines.GUI_TEXTURED, RAMP_L, x + w - r - lenH, y + h - 1, 0, 0, lenH, 1, RAMP_W, 1, RAMP_W, 1, peak);
        }
        if (lenV > 0) {
            g.fillGradient(x, y + r, x + 1, y + r + lenV, peak, clear);
            g.fillGradient(x + w - 1, y + h - r - lenV, x + w, y + h - r, clear, peak);
        }
    }
}
