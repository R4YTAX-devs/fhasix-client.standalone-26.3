package com.fhasix.uitheme;

import com.fhasix.uitheme.mixin.SimpleOptionAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;

/**
 * Full Bright: pushes the game's brightness (gamma) option past its normal maximum so caves and the
 * night are fully lit. "Light level" sets how far (1 = vanilla Bright, 15 = everything fully lit).
 * The player's own brightness is remembered and put back when the mod is turned off or the game closes.
 */
final class NewFullBright {

    static final String ID = "full_bright";

    private static Double original;

    private NewFullBright() {
    }

    static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(NewSafe.tick("full_bright", NewFullBright::tick));
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> NewSafe.run("full_bright", () -> restore(c)));
    }

    private static void set(OptionInstance<Double> opt, double v) {
        try {
            ((SimpleOptionAccessor<Double>) (Object) opt).fhasix$setValue(v);
        } catch (Exception | LinkageError accessorMissing) {
            try {
                java.lang.reflect.Field f = net.minecraft.client.OptionInstance.class.getDeclaredField("value");
                f.setAccessible(true);
                f.set(opt, v);
            } catch (Exception | LinkageError t) {
                NewSafe.failed("full_bright", t);
            }
        }
    }

    private static void tick(Minecraft c) {
        if (c.options == null) {
            return;
        }
        if (!NewModSettings.isEnabled(c, ID)) {
            restore(c);
            return;
        }
        OptionInstance<Double> gamma = c.options.gamma();
        if (original == null) {
            original = gamma.get();
        }
        double target = NewModSettings.opt(c, ID, "level");
        if (Math.abs(gamma.get() - target) > 1.0e-6) {
            set(gamma, target);
        }
    }

    private static void restore(Minecraft c) {
        if (original != null && c.options != null) {
            set(c.options.gamma(), original);
            original = null;
        }
    }
}
