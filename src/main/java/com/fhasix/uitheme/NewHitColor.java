package com.fhasix.uitheme;

import com.mojang.blaze3d.platform.NativeImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;

/**
 * Hit Color: the tint on a mob (or player) that was just hit. Vanilla paints it red from a small 16 x 16 overlay
 * texture; this re-colours the "hurt" half (the top 8 rows) of that texture. The overlay-texture mixin gives us the
 * live object; nothing is touched while the mod is off.
 */
public final class NewHitColor {

    static final String ID = "hit_color";
    private static final int VANILLA = 0xB2FF0000;
    private static final int UNKNOWN = 0x00000001;

    private static Object overlay;
    private static int applied = UNKNOWN;

    private NewHitColor() {
    }

    /** Called by the OverlayTexture mixin when the game creates it. */
    public static void attach(Object overlayTexture) {
        overlay = overlayTexture;
        applied = UNKNOWN;
    }

    static void tick(Minecraft c) {
        if (overlay == null) {
            return;
        }
        int wanted = VANILLA;
        if (NewModSettings.isEnabled(c, ID)) {
            int rgb = NewModSettings.opt(c, ID, "rgb") & 0xFFFFFF;
            int a = Math.max(20, Math.min(100, NewModSettings.opt(c, ID, "alpha"))) * 255 / 100;
            wanted = (a << 24) | rgb;
        }
        if (wanted == applied) {
            return;
        }
        if (applied == UNKNOWN && wanted == VANILLA) {
            applied = VANILLA; // never touched: nothing to restore
            return;
        }
        applied = wanted;
        recolor(wanted);
    }

    private static void recolor(int argb) {
        try {
            for (Field f : overlay.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                Object v = f.get(overlay);
                if (v instanceof DynamicTexture texture) {
                    Method getPixels = DynamicTexture.class.getMethod("getPixels");
                    Object pixels = getPixels.invoke(texture);
                    if (pixels instanceof NativeImage image) {
                        for (int y = 0; y < 8; y++) {
                            for (int x = 0; x < 16; x++) {
                                image.setPixel(x, y, argb);
                            }
                        }
                        DynamicTexture.class.getMethod("upload").invoke(texture);
                    }
                    return;
                }
            }
        } catch (Exception | LinkageError t) {
            NewSafe.failed("hit_color", t);
        }
    }
}
