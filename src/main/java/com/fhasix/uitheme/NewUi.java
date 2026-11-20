package com.fhasix.uitheme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.resources.Identifier;

/**
 * NEW (v30 UI rewrite). Cheap drawing helpers for the Mod Menu.
 *
 * Why this exists: a1.drawRoundedGradientRect issues one fill PER PIXEL COLUMN (plus one per
 * corner pixel). That is fine for a few buttons but made the big Mod Menu lag. rrect() here
 * draws a rounded, vertically-graded rectangle with about 2r+1 fills no matter how wide it is.
 */
final class NewUi {

    /** Icon atlas: 5 columns x 10 rows of 128px cells (640x1792). */
    static final Identifier ATLAS = Identifier.fromNamespaceAndPath("fhasixuitheme", "textures/gui/new_mod_icons.png");
    static final int CELL = 128;
    static final int ATLAS_W = 640;
    static final int ATLAS_H = 1792;
    static final int COLS = 5;

    // atlas cell indexes
    static final int ICON_HEART_OFF = 14;
    static final int ICON_HEART_ON = 15;
    static final int ICON_GEAR = 16;
    static final int ICON_GRID = 17;

    private NewUi() {
    }

    /** Menu-wide scale: reference design 1394x847 fitted to the window, then shrunk a little so nothing hugs the edges. */
    static final float MENU_SCALE = 0.88f;

    static float scale(int w, int h) {
        return Math.min(w / 1394f, h / 847f) * MENU_SCALE;
    }

    // shared palette (ARGB)
    static final int PANEL_T = rgb(15, 17, 20);
    static final int PANEL_B = rgb(14, 16, 19);
    static final int BTN_BORDER = rgb(30, 33, 40);
    static final int BTN_T = rgb(25, 27, 30);
    static final int BTN_B = rgb(25, 27, 30);
    static final int HOV_T = rgb(54, 64, 78);
    static final int HOV_B = rgb(23, 26, 33);
    static final int CARD_BORDER = rgb(28, 31, 38);
    static final int CARD_T = rgb(15, 17, 20);
    static final int CARD_B = rgb(14, 16, 19);
    // the main (accent) colour family: follows the colour chosen in Mod Menu -> General -> Main Color
    static int redT() { return FhasixTheme.shade(1.02f, 0f); }
    static int redB() { return FhasixTheme.shade(0.97f, 0f); }
    static int redHT() { return FhasixTheme.shade(1.02f, 0.15f); }
    static int redHB() { return FhasixTheme.shade(0.97f, 0.05f); }
    static final int ON_T = rgb(27, 78, 33);
    static final int ON_B = rgb(20, 60, 25);
    static final int ON_HT = rgb(36, 98, 43);
    static final int ON_HB = rgb(25, 72, 30);
    static final int TXT = 0xFFFFFFFF;
    static final int TXT_DIM = 0xFF8C8F96;
    static final int TXT_ON = 0xFF3FBF4D;

    static int rgb(int r, int g, int b) {
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    static int lerp(int a, int b, float t) {
        if (t <= 0f) {
            return a;
        }
        if (t >= 1f) {
            return b;
        }
        int aa = (int) (((a >>> 24) & 255) + (((b >>> 24) & 255) - ((a >>> 24) & 255)) * t);
        int ar = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int ag = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int ab = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return (aa << 24) | (ar << 16) | (ag << 8) | ab;
    }

    // ------------------------------------------------------------------ smooth rounded shapes
    // Every rounded shape is drawn with anti-aliased corners: the pixels on the arc get a partial alpha
    // instead of being switched fully on/off, so the corners are smooth at every GUI scale (no staircase).

    // Rounded shapes use ONE small texture atlas (rr_atlas.png) for their corners: a solid disc, a 1-unit ring and
    // the "outside the arc" shape, each pre-drawn smooth (anti-aliased) for every radius from 2 to 20 units at the
    // exact size the screen shows them. A rounded rectangle is then 3 fills + 4 corner quads instead of dozens
    // of single-pixel fills - same smooth look, a small fraction of the draw calls (this was the Mod Menu lag).

    private static final Identifier RR = Identifier.fromNamespaceAndPath("fhasixuitheme", "textures/gui/rr_atlas.png");
    private static final int RR_W = 240;
    private static final int RR_H = 836;
    private static final int RR_MIN = 2;
    private static final int RR_MAX = 20;
    private static final Identifier ACC_CARD = Identifier.fromNamespaceAndPath("fhasixuitheme", "textures/gui/accent_card.png");
    private static final Identifier ACC_TILE = Identifier.fromNamespaceAndPath("fhasixuitheme", "textures/gui/accent_tile.png");

    private static int rrY(int r) {
        return 2 * r * (r - 1) - 4;
    }

    /** kind 0 = filled disc, 1 = 1-unit ring, 2 = the corner area outside the arc. */
    private static void corners(GuiGraphicsExtractor c, int x, int y, int w, int h, int r, int kind, int colTop, int colBot) {
        int q = r * 2;
        int u0 = kind * r * 4;
        int v0 = rrY(r);
        c.blit(RenderPipelines.GUI_TEXTURED, RR, x, y, u0, v0, r, r, q, q, RR_W, RR_H, colTop);
        c.blit(RenderPipelines.GUI_TEXTURED, RR, x + w - r, y, u0 + q, v0, r, r, q, q, RR_W, RR_H, colTop);
        c.blit(RenderPipelines.GUI_TEXTURED, RR, x, y + h - r, u0, v0 + q, r, r, q, q, RR_W, RR_H, colBot);
        c.blit(RenderPipelines.GUI_TEXTURED, RR, x + w - r, y + h - r, u0 + q, v0 + q, r, r, q, q, RR_W, RR_H, colBot);
    }

    static int withAlpha(int color, float a) {
        int al = Math.round(((color >>> 24) & 255) * Math.max(0f, Math.min(1f, a)));
        return (al << 24) | (color & 0xFFFFFF);
    }

    /** Rounded rectangle with smooth corners, vertical gradient top -> bottom. */
    static void rrect(GuiGraphicsExtractor c, int x, int y, int w, int h, int r, int top, int bot) {
        if (w <= 0 || h <= 0) {
            return;
        }
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r < RR_MIN) {
            c.fillGradient(x, y, x + w, y + h, top, bot);
            return;
        }
        r = Math.min(r, RR_MAX);
        int midTop = lerp(top, bot, r / (float) h);
        int midBot = lerp(top, bot, (h - r) / (float) h);
        c.fillGradient(x, y + r, x + w, y + h - r, midTop, midBot);
        if (w > 2 * r) {
            c.fillGradient(x + r, y, x + w - r, y + r, top, midTop);
            c.fillGradient(x + r, y + h - r, x + w - r, y + h, midBot, bot);
        }
        corners(c, x, y, w, h, r, 0, lerp(top, bot, r * 0.5f / h), lerp(top, bot, 1f - r * 0.5f / h));
    }

    /** Rounded rectangle with a 1px border. */
    static void frame(GuiGraphicsExtractor c, int x, int y, int w, int h, int r, int border, int top, int bot) {
        rrect(c, x, y, w, h, r, border, border);
        rrect(c, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), top, bot);
    }

    /** Only the 1px outline of a rounded rectangle (the inside stays untouched), smooth corners. */
    static void ring(GuiGraphicsExtractor c, int x, int y, int w, int h, int r, int color) {
        if (w <= 2 || h <= 2) {
            return;
        }
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r < RR_MIN) {
            c.fill(x, y, x + w, y + 1, color);
            c.fill(x, y + h - 1, x + w, y + h, color);
            c.fill(x, y + 1, x + 1, y + h - 1, color);
            c.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
            return;
        }
        r = Math.min(r, RR_MAX);
        c.fill(x + r, y, x + w - r, y + 1, color);
        c.fill(x + r, y + h - 1, x + w - r, y + h, color);
        c.fill(x, y + r, x + 1, y + h - r, color);
        c.fill(x + w - 1, y + r, x + w, y + h - r, color);
        corners(c, x, y, w, h, r, 1, color, color);
    }

    /**
     * Paints the four corner areas OUTSIDE a rounded rectangle with the colour that lies behind it (smooth edge).
     * Used for shapes that are drawn as a plain rectangle first (e.g. the diagonal hover gradient).
     */
    static void cutCorners(GuiGraphicsExtractor c, int x, int y, int w, int h, int r, int bg) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r < RR_MIN) {
            return;
        }
        corners(c, x, y, w, h, Math.min(r, RR_MAX), 2, bg, bg);
    }

    /**
     * Rounded rectangle with a DIAGONAL gradient (top-left colour -> bottom-right colour), like the hovered
     * cards in the reference. {@code bg} is the flat colour behind the shape (used to round the corners).
     */
    static void rrectDiag(GuiGraphicsExtractor c, int x, int y, int w, int h, int r, int tl, int br, int bg) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int n = Math.max(4, Math.min(14, w / 12));
        for (int s = 0; s < n; s++) {
            int x0 = x + w * s / n;
            int x1 = x + w * (s + 1) / n;
            float u = (s + 0.5f) / n;
            c.fillGradient(x0, y, x1, y + h, lerp(tl, br, u * 0.5f), lerp(tl, br, 0.5f + u * 0.5f));
        }
        cutCorners(c, x, y, w, h, r, bg);
    }

    /**
     * A soft circular "ripple" (the click wave that spreads over a button), clipped to the rectangle
     * x,y,w,h (corner arcs of radius r respected). cx,cy = centre, rad = current radius.
     */
    static void ripple(GuiGraphicsExtractor c, int x, int y, int w, int h, int r, float cx, float cy, float rad, int color) {
        if (rad < 1f || w <= 0 || h <= 0) {
            return;
        }
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int row = 0; row < h; row++) {
            float dy = (y + row + 0.5f) - cy;
            if (Math.abs(dy) >= rad) {
                continue;
            }
            float dx = (float) Math.sqrt(rad * rad - dy * dy);
            int inset = 0;
            if (r >= 2) {
                int d = Math.min(row, h - 1 - row);
                if (d < r) {
                    float ddy = r - (d + 0.5f);
                    inset = r - Math.round((float) Math.sqrt(Math.max(0f, r * r - ddy * ddy)));
                }
            }
            int x0 = Math.max(x + inset, Math.round(cx - dx));
            int x1 = Math.min(x + w - inset, Math.round(cx + dx));
            if (x1 > x0) {
                c.fill(x0, y + row, x1, y + row + 1, color);
            }
        }
    }

    /** Draws a standalone square texture of texSize x texSize pixels as size x size (smooth icon). */
    static void tex(GuiGraphicsExtractor c, Identifier id, int x, int y, int size, int texSize) {
        c.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0, 0, size, size, texSize, texSize, texSize, texSize);
    }

    /** Draws atlas cell {@code index} as a size x size square at x,y. */
    static void icon(GuiGraphicsExtractor c, int index, int x, int y, int size) {
        int u = (index % COLS) * CELL;
        int v = (index / COLS) * CELL;
        c.blit(RenderPipelines.GUI_TEXTURED, ATLAS, x, y, u, v, size, size, CELL, CELL, ATLAS_W, ATLAS_H);
    }

    /** Draws a whole standalone 128x128 texture (the top icon row) as size x size. */
    static void whole(GuiGraphicsExtractor c, Identifier tex, int x, int y, int size) {
        c.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0, 0, size, size, 128, 128, 128, 128);
    }

    static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** The normal Minecraft button click sound (every clickable thing in the custom screens plays it). */
    static void click(Minecraft c) {
        if (c != null) {
            c.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    /**
     * The same faint grey-blue edge the title-screen buttons have: brightest in the top-left and bottom-right
     * corners (rounded arc included) and fading out along the top/bottom (45% of the width) and down the sides
     * (60% of the height). One textured quad; strength = how visible (0..1).
     */
    static void accent(GuiGraphicsExtractor c, int x, int y, int w, int h, int r, float strength) {
        if (w < 8 || h < 8 || strength <= 0.004f) {
            return;
        }
        boolean wide = w >= h * 1.3f;
        Identifier t = wide ? ACC_CARD : ACC_TILE;
        int tw = wide ? 336 : 84;
        int th = wide ? 230 : 70;
        int col = (Math.round(255 * Math.min(1f, strength)) << 24) | 0x3C444D;
        c.blit(RenderPipelines.GUI_TEXTURED, t, x, y, 0, 0, w, h, tw, th, tw, th, col);
    }
}
