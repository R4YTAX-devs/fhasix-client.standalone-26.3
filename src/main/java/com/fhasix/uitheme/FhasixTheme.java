package com.fhasix.uitheme;

import net.minecraft.client.Minecraft;

/**
 * The main (accent) colour of the whole Fhasix UI. It starts as the Fhasix red and can be changed in the Mod
 * Menu (General -> Main Color): every button, tab, highlight and the red Store button follows it. Stored in the
 * normal mod settings ("theme.accent" = packed 0xRRGGBB; 0 = not chosen = the default red).
 */
public final class FhasixTheme {
    /** The original Fhasix red. */
    public static final int DEFAULT = 0xDD3538;

    /** Colours offered as one-tap presets in the picker. */
    public static final int[] PRESETS = {
            0xDD3538, 0xFF8A1F, 0xFFC21F, 0x3FBF4D, 0x1FBFA5, 0x22A9EE, 0x3B82F6, 0x8B5CF6, 0xEC4899, 0xF1F1F4
    };

    private static int accent = DEFAULT;
    private static boolean loaded = false;

    private FhasixTheme() {}

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        Minecraft c = Minecraft.getInstance();
        if (c == null) {
            return;
        }
        loaded = true;
        int v = NewModSettings.opt(c, "theme", "accent");
        if (v != 0) {
            accent = v & 0xFFFFFF;
        }
    }

    /** Packed 0xRRGGBB of the main colour. */
    public static int rgb() {
        ensureLoaded();
        return accent;
    }

    /** The main colour as opaque ARGB (the old fixed "red"). */
    public static int red() {
        return 0xFF000000 | rgb();
    }

    /** Slightly lighter main colour for hovered buttons. */
    public static int redHover() {
        return shade(1.0f, 0.15f);
    }

    /** The darker outline colour of the close button. */
    public static int ring() {
        return shade(0.86f, 0f);
    }

    /** accent * mul, then moved lift (0..1) of the way to white; opaque ARGB. */
    public static int shade(float mul, float lift) {
        int c = rgb();
        int r = shadeChannel((c >> 16) & 255, mul, lift);
        int g = shadeChannel((c >> 8) & 255, mul, lift);
        int b = shadeChannel(c & 255, mul, lift);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int shadeChannel(int v, float mul, float lift) {
        float x = Math.min(255f, v * mul);
        x = x + (255f - x) * lift;
        return Math.max(0, Math.min(255, Math.round(x)));
    }

    /** {r, g, b} of the main colour. */
    public static int[] channels() {
        int c = rgb();
        return new int[]{(c >> 16) & 255, (c >> 8) & 255, c & 255};
    }

    /** Changes the colour in memory (everything recolours at once). Call save() when the change is finished. */
    public static void set(Minecraft c, int rgb) {
        loaded = true;
        accent = (rgb & 0xFFFFFF) == 0 ? DEFAULT : (rgb & 0xFFFFFF);
        NewModSettings.setOpt(c, "theme", "accent", accent);
    }

    public static void reset(Minecraft c) {
        set(c, DEFAULT);
        NewModSettings.save(c);
    }

    public static void save(Minecraft c) {
        NewModSettings.save(c);
    }
}
