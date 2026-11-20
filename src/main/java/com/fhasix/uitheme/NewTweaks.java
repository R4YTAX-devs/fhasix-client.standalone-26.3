package com.fhasix.uitheme;

import net.minecraft.client.Minecraft;

/** Small PvP view tweaks: No Hurt Cam and Low Fire (read by their mixins) and the Hit Color tint. */
public final class NewTweaks {

    static final String HURT = "no_hurt_cam";
    static final String FIRE = "low_fire";

    private NewTweaks() {
    }

    /** True while the camera shake you get when you are hit should be skipped. */
    public static boolean noHurtCam() {
        Minecraft c = Minecraft.getInstance();
        return c != null && NewModSettings.isEnabled(c, HURT);
    }

    /** How far (in blocks) the on-screen fire is moved down; 0 = vanilla. */
    public static float fireDrop() {
        Minecraft c = Minecraft.getInstance();
        if (c == null || !NewModSettings.isEnabled(c, FIRE)) {
            return 0f;
        }
        return Math.max(0, Math.min(60, NewModSettings.opt(c, FIRE, "drop"))) / 100f;
    }
}
