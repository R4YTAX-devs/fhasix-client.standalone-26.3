package com.fhasix.uitheme;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * FHASIX moving panorama background (UPDATE 42) - replaces the single still title picture on the title screen and on
 * every Fhasix screen.
 *
 * The six panorama faces (panorama_0..5) are baked into ONE seamless 360-degree picture
 * (textures/gui/title/panorama.png, 3072 x 1536: 360 deg across, 180 deg down). Drawing it is just two plain
 * textured rectangles per frame - no 3D, no extra render passes - so it is cheap enough for phones: a window of the
 * picture is shown and it slowly turns round (with a gentle up/down sway). The turning is driven by one global clock,
 * so it keeps moving smoothly while you switch between screens instead of restarting.
 *
 * The window keeps the picture's own proportions (same degrees per pixel across and down), so it is never stretched,
 * whatever the screen shape.
 */
public final class FhasixPanorama {
    private static final Identifier TEX = Identifier.fromNamespaceAndPath("fhasixuitheme", "textures/gui/title/panorama.png");
    private static final int TW = 3072;
    private static final int TH = 1536;

    /** How many degrees of the picture are visible from top to bottom (smaller = more zoomed in; 93 is 20% closer than the old 112; above about 115 the sky starts to stretch). */
    private static final float V_SPAN = 93f;
    /** Turning speed in degrees per second (a full circle takes about 72 seconds). */
    private static final float YAW_SPEED = 5.0f;
    /** Where the view starts (degrees; the moon is at about 270) and the average look-up angle. */
    private static final float YAW_START = 215f;
    private static final float PITCH = 2f;
    private static final float SWAY = 2.5f;
    /** Extra darkening laid over the panorama on every screen (alpha of black; 0x2E is about 18%). */
    private static final int DIM = 0x2E000000;
    private static final long START = System.nanoTime();

    private FhasixPanorama() {
    }

    public static void draw(GuiGraphicsExtractor c, int w, int h) {
        if (w <= 0 || h <= 0) {
            return;
        }
        // always moving: it is only two textured rectangles, so it does not follow the HUD Optimizer / animation switch
        double t = (System.nanoTime() - START) / 1_000_000_000.0;
        double yaw = YAW_START + YAW_SPEED * t;
        double pitch = PITCH + SWAY * Math.sin(t * 0.21);

        // visible window in degrees: same scale in both directions, so the picture is never stretched
        double hSpan = Math.min(330.0, V_SPAN * w / (double) h);
        int srcW = Math.max(2, (int) Math.round(hSpan / 360.0 * TW));
        int srcH = Math.max(2, Math.min(TH, (int) Math.round(V_SPAN / 180.0 * TH)));

        double left = (((yaw - hSpan / 2.0) % 360.0) + 360.0) % 360.0;
        int u0 = (int) Math.round(left / 360.0 * TW) % TW;
        int v0 = (int) Math.round((90.0 - (pitch + V_SPAN / 2.0)) / 180.0 * TH);
        v0 = Math.max(0, Math.min(TH - srcH, v0));

        if (u0 + srcW <= TW) {
            c.blit(RenderPipelines.GUI_TEXTURED, TEX, 0, 0, u0, v0, w, h, srcW, srcH, TW, TH);
            c.fill(0, 0, w, h, DIM);
            return;
        }
        // the window runs past the right edge of the picture: draw the end of it, then the start
        int a = TW - u0;
        int wa = Math.max(1, Math.min(w - 1, Math.round(w * (float) a / srcW)));
        c.blit(RenderPipelines.GUI_TEXTURED, TEX, 0, 0, u0, v0, wa, h, a, srcH, TW, TH);
        c.blit(RenderPipelines.GUI_TEXTURED, TEX, wa, 0, 0, v0, w - wa, h, srcW - a, srcH, TW, TH);
        c.fill(0, 0, w, h, DIM);
    }
}
