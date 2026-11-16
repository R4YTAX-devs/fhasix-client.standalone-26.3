package com.fhasix.uitheme;

import net.minecraft.client.Minecraft;
import org.joml.Vector4f;

/**
 * Custom Fog values, read by mixin/NewFogMixin: an own fog colour (colour picker), a fog distance
 * scale (below 100% = fog comes closer, above = further away) and "Remove fog".
 *
 * Only active while a world is loaded. While the mod is on the fog is always switched ON: if fog was
 * turned off elsewhere (the fog values arrive as "infinite"), real fog distances are put back so the
 * mod's colour / distance settings actually show.
 *
 * The sky and the clouds are drawn with the same fog uniform (they fade into the fog colour out to "sky end"
 * and "cloud end"). While a custom colour is on, skyEnd() and cloudEnd() are pulled in to the world fog's end
 * distance, so the whole environment - ground (down), clouds (middle) and sky (up) - is covered by the fog colour.
 *
 * The Fog Optimizer (Performance page, a single switch) does the same job as this mod at distance 60% with the
 * normal fog colour: it works on its own, without this mod being on. If both are on, this mod's settings win.
 */
public final class NewFog {

    static final String ID = "custom_fog";

    /** Anything this big is "no fog" (the game uses huge values when fog is switched off). */
    private static final float NO_FOG = 1.0e7f;

    private NewFog() {
    }

    private static boolean active() {
        Minecraft c = Minecraft.getInstance();
        return c != null && c.level != null && c.options != null && NewModSettings.isEnabled(c, ID);
    }

    /** The Performance page's Fog Optimizer switch (only counts while this mod is off). */
    private static boolean optimizer() {
        Minecraft c = Minecraft.getInstance();
        return c != null && c.level != null && c.options != null && NewPerf.fogOptimizer();
    }

    /** Fog colour; the original colour (with fog forced on) when "Custom color" is off. */
    public static Vector4f color(Vector4f original) {
        try {
            return colorImpl(original);
        } catch (Exception | LinkageError t) {
            NewSafe.failed("fog", t);
            return original;
        }
    }

    private static Vector4f colorImpl(Vector4f original) {
        if (!active()) {
            if (optimizer()) { // normal colour, fog forced on
                return original == null ? null : new Vector4f(original.x, original.y, original.z, 1f);
            }
            return original;
        }
        Minecraft c = Minecraft.getInstance();
        if (!NewModSettings.optBool(c, ID, "color")) {
            return original == null ? null : new Vector4f(original.x, original.y, original.z, 1f);
        }
        int rgb = NewModSettings.opt(c, ID, "rgb");
        return new Vector4f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, 1f);
    }

    /** Sky fog distance: pulled in to the world fog end while a custom colour is on, so the sky takes the colour too. */
    public static float skyEnd(float original) {
        try {
            return skyEndImpl(original);
        } catch (Exception | LinkageError t) {
            NewSafe.failed("fog", t);
            return original;
        }
    }

    private static float skyEndImpl(float original) {
        if (!active()) {
            return original;
        }
        Minecraft c = Minecraft.getInstance();
        if (!NewModSettings.optBool(c, ID, "color") || NewModSettings.optBool(c, ID, "off")) {
            return original;
        }
        float end = lastEnvEnd;
        return end > 1f ? Math.min(original, end) : original;
    }

    /** Cloud fog distance: pulled in like the sky end, so the clouds in the middle take the colour too. */
    public static float cloudEnd(float original) {
        return skyEnd(original);
    }

    /** The world fog end distance of the last frame (what the sky end / cloud end are pulled in to). */
    private static float lastEnvEnd = 0f;

    /**
     * Fog start/end distance. kind: 0 = environmental start, 1 = environmental end, 2 = render
     * distance start, 3 = render distance end.
     */
    public static float distance(float original, int kind) {
        try {
            return distanceImpl(original, kind);
        } catch (Exception | LinkageError t) {
            NewSafe.failed("fog", t);
            return original;
        }
    }

    private static float distanceImpl(float original, int kind) {
        boolean custom = active();
        if (!custom && !optimizer()) {
            return original;
        }
        Minecraft c = Minecraft.getInstance();
        if (custom && NewModSettings.optBool(c, ID, "off")) {
            return 100000f;
        }
        float v = original;
        if (v > NO_FOG) {
            // fog was disabled elsewhere - bring it back so this mod has something to colour
            float r = Math.max(32f, c.options.getEffectiveRenderDistance() * 16f);
            switch (kind) {
                case 0: v = r * 0.05f; break;
                case 1: v = r * 0.9f; break;
                case 2: v = r - Math.max(4f, Math.min(64f, r / 10f)); break;
                default: v = r; break;
            }
        }
        v = v * ((custom ? NewModSettings.opt(c, ID, "dist") : 60) / 100f); // Fog Optimizer = distance 60%
        if (kind == 1) {
            lastEnvEnd = v;
        }
        return v;
    }
}
