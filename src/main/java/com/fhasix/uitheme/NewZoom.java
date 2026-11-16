package com.fhasix.uitheme;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * Zoom: hold the Zoom key (default C) to zoom in. The zoom animates in and out (on by default); the animation
 * can be smooth (eased) or normal (constant speed), and the smoothness is adjustable. The field of view is
 * changed by mixin/NewZoomMixin, which asks {@link #apply(float)}.
 */
public final class NewZoom {

    static final String ID = "zoom";

    private static KeyMapping zoomKey;
    private static boolean held = false;
    /** ln(zoom factor): 0 = not zoomed. Working in ln space makes zooming in and out feel even. */
    private static double cur = 0.0;
    private static long lastNs = 0L;

    private NewZoom() {
    }

    static void init(KeyMapping.Category category) {
        zoomKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.fhasixuitheme.zoom", InputConstants.Type.KEYBOARD, InputConstants.KEY_C, category));
        ClientTickEvents.END_CLIENT_TICK.register(NewSafe.tick("zoom", NewZoom::tick));
    }

    private static void tick(Minecraft c) {
        held = c.player != null && c.gui.screen() == null
                && NewModSettings.isEnabled(c, ID) && zoomKey != null && zoomKey.isDown();
    }

    private static double targetLn(Minecraft c) {
        if (!held) {
            return 0.0;
        }
        return Math.log(Math.max(1, NewModSettings.opt(c, ID, "level")));
    }

    /** Advances the animation to "now" and returns the current zoom factor (1 = not zoomed). */
    private static double factor(Minecraft c) {
        long now = System.nanoTime();
        double dt = lastNs == 0L ? 0.0 : Math.min(0.1, (now - lastNs) / 1_000_000_000.0);
        lastNs = now;
        double target = targetLn(c);
        if (!NewModSettings.optBool(c, ID, "anim")) {
            cur = target;
        } else {
            double smooth = Math.max(1, NewModSettings.opt(c, ID, "smooth"));
            if (NewModSettings.opt(c, ID, "style") == 0) {
                // smooth: eases out, fast at first and gentle at the end
                double rate = 26.0 / smooth;
                cur += (target - cur) * Math.min(1.0, dt * rate);
                if (Math.abs(target - cur) < 0.0005) {
                    cur = target;
                }
            } else {
                // normal: constant speed
                double step = 14.0 / smooth * dt;
                if (Math.abs(target - cur) <= step) {
                    cur = target;
                } else {
                    cur += Math.signum(target - cur) * step;
                }
            }
        }
        return Math.exp(cur);
    }

    /** True while zoomed in (or still animating back out). */
    public static boolean zoomed() {
        return cur > 0.001;
    }

    /** Mouse sensitivity multiplier while zoomed, so aiming stays precise. */
    public static double sensitivityScale() {
        try {
            Minecraft c = Minecraft.getInstance();
            if (!zoomed() || c == null || !NewModSettings.isEnabled(c, ID) || !NewModSettings.optBool(c, ID, "sens")) {
                return 1.0;
            }
            return 1.0 / Math.exp(cur);
        } catch (Exception | LinkageError t) {
            NewSafe.failed("zoom", t);
            return 1.0;
        }
    }

    /** Called by the FOV mixin with the field of view (degrees) Minecraft just worked out; returns the zoomed one. */
    public static float apply(float fov) {
        try {
            Minecraft c = Minecraft.getInstance();
            if (c == null || c.options == null) {
                return fov;
            }
            if (!NewModSettings.isEnabled(c, ID)) {
                cur = 0.0;
                lastNs = 0L;
                return fov;
            }
            double f = factor(c);
            if (f < 1.0005) {
                return fov;
            }
            double half = Math.toRadians(fov) / 2.0;
            return (float) Math.toDegrees(2.0 * Math.atan(Math.tan(half) / f));
        } catch (Exception | LinkageError t) {
            NewSafe.failed("zoom", t);
            return fov;
        }
    }
}
