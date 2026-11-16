package com.fhasix.uitheme;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;

/**
 * FOV Changer: sets the field of view past the vanilla limit (110) - up to 180. The player's own FOV
 * is remembered and put back when the mod is turned off or the game closes.
 */
final class NewFovChanger {

    static final String ID = "fov_changer";

    private static Integer original;

    private NewFovChanger() {
    }

    static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(NewSafe.tick("fov_changer", NewFovChanger::tick));
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> NewSafe.run("fov_changer", () -> restore(c)));
    }

    private static void set(OptionInstance<Integer> opt, int v) {
        OptSet.set(opt, v);
    }

    private static void tick(Minecraft c) {
        if (c.options == null) {
            return;
        }
        if (!NewModSettings.isEnabled(c, ID)) {
            restore(c);
            return;
        }
        if (NewZoom.optionZoomActive()) {
            return;
        }
        OptionInstance<Integer> fov = c.options.fov();
        if (original == null) {
            original = fov.get();
        }
        int target = NewModSettings.opt(c, ID, "fov");
        if (fov.get() != target) {
            set(fov, target);
        }
    }

    private static void restore(Minecraft c) {
        if (original != null && c.options != null) {
            set(c.options.fov(), original);
            original = null;
        }
    }
}
