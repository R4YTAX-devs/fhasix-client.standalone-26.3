package com.fhasix.uitheme;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.CameraType;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * Camera mod state.
 *
 *  - Freelook: hold the Freelook key (default Left Alt) and the mouse turns only the CAMERA around
 *    your character; the character keeps facing/moving the way it was. Optionally switches to
 *    third person while held and back when released.
 *  - Camera offset: third-person distance, shoulder (left/right) and height offset.
 *
 * The actual camera / mouse changes are made by mixin/NewCameraMixin and mixin/NewLookMixin,
 * which read the values from here.
 */
public final class NewCamera {

    static final String ID = "camera";

    private static KeyMapping freelookKey;
    private static boolean active = false;
    private static float yaw;
    private static float pitch;
    private static CameraType previous;
    private static float lockYaw;
    private static float lockPitch;
    private static float lastYaw;
    private static float lastPitch;
    private static boolean haveLast;
    /** Set by NewLookMixin the first time Entity.turn is really hooked; then the frame fallback below stays idle. */
    public static volatile boolean turnHookSeen = false;

    private NewCamera() {
    }

    static void init(KeyMapping.Category category) {
        freelookKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.fhasixuitheme.freelook", InputConstants.Type.KEYBOARD, InputConstants.KEY_LALT, category));
        ClientTickEvents.END_CLIENT_TICK.register(NewSafe.tick("camera", NewCamera::tick));
    }

    private static void tick(Minecraft c) {
        boolean usable = c.player != null && NewModSettings.isEnabled(c, ID) && NewModSettings.optBool(c, ID, "freelook");
        boolean want = usable && c.gui.screen() == null && freelookKey.isDown();
        if (want && !active) {
            active = true;
            yaw = c.player.getYRot();
            pitch = c.player.getXRot();
            lockYaw = yaw;
            lockPitch = pitch;
            previous = c.options.getCameraType();
            if (NewModSettings.optBool(c, ID, "third")) {
                c.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
        } else if (!want && active) {
            active = false;
            if (previous != null && NewModSettings.optBool(c, ID, "third")) {
                c.options.setCameraType(previous);
            }
        }
    }

    /**
     * Runs every frame. If the Entity.turn hook is not working on this Minecraft version, mouse movement
     * turns the player; here that turn is moved over to the camera (free look) or scaled down (zoom).
     */
    static void frame(Minecraft c) {
        if (c.player == null || turnHookSeen) {
            return;
        }
        if (active) {
            float dy = c.player.getYRot() - lockYaw;
            float dp = c.player.getXRot() - lockPitch;
            if (dy != 0f || dp != 0f) {
                yaw += dy;
                pitch = Math.max(-90f, Math.min(90f, pitch + dp));
                c.player.setYRot(lockYaw);
                c.player.setXRot(lockPitch);
            }
            haveLast = false;
            return;
        }
        double s = NewZoom.sensitivityScale();
        if (s < 0.999) {
            if (haveLast) {
                float dy = c.player.getYRot() - lastYaw;
                float dp = c.player.getXRot() - lastPitch;
                if (dy != 0f || dp != 0f) {
                    c.player.setYRot(lastYaw + (float) (dy * s));
                    c.player.setXRot(Math.max(-90f, Math.min(90f, lastPitch + (float) (dp * s))));
                }
            }
            lastYaw = c.player.getYRot();
            lastPitch = c.player.getXRot();
            haveLast = true;
        } else {
            haveLast = false;
        }
    }

    /** Called once per frame from the HUD. Never throws. */
    static void frameAll(Minecraft c) {
        try {
            frame(c);
            NewZoom.frame(c);
        } catch (Exception | LinkageError t) {
            NewSafe.failed("frame_fallbacks", t);
        }
    }

    // ---- read by the mixins

    public static boolean freelookActive() {
        return active;
    }

    public static float yaw() {
        return yaw;
    }

    public static float pitch() {
        return pitch;
    }

    /** Mouse movement while freelooking turns the camera instead of the player (0.15 = vanilla sensitivity factor). */
    public static void look(double dx, double dy) {
        yaw += (float) dx * 0.15f;
        pitch = Math.max(-90f, Math.min(90f, pitch + (float) dy * 0.15f));
    }

    private static boolean enabled() {
        Minecraft c = Minecraft.getInstance();
        return c != null && NewModSettings.isEnabled(c, ID);
    }

    public static float distanceScale() {
        if (!enabled()) {
            return 1f;
        }
        return NewModSettings.opt(Minecraft.getInstance(), ID, "distance") / 100f;
    }

    /** Positive = camera moves to the right shoulder. */
    public static float shoulder() {
        return enabled() ? NewModSettings.opt(Minecraft.getInstance(), ID, "shoulder") / 50f : 0f;
    }

    /** Positive = camera moves up. */
    public static float height() {
        return enabled() ? NewModSettings.opt(Minecraft.getInstance(), ID, "height") / 50f : 0f;
    }
}
