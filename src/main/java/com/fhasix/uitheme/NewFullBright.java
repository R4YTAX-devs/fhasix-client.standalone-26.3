package com.fhasix.uitheme;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Full Bright: pushes the game's brightness (gamma) option past its normal maximum so caves and the
 * night are fully lit. "Light level" sets how far (1 = vanilla Bright, 15 = everything fully lit).
 * The player's own brightness is remembered and put back when the mod is turned off or the game closes.
 */
final class NewFullBright {

    static final String ID = "full_bright";

    private static Double original;
    private static boolean nvAdded;

    private NewFullBright() {
    }

    static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(NewSafe.tick("full_bright", NewFullBright::tick));
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> NewSafe.run("full_bright", () -> restore(c)));
    }

    private static void set(OptionInstance<Double> opt, double v) {
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
        OptionInstance<Double> gamma = c.options.gamma();
        if (original == null) {
            original = gamma.get();
        }
        double target = NewModSettings.opt(c, ID, "level");
        if (Math.abs(gamma.get() - target) > 1.0e-6) {
            set(gamma, target);
        }
        // 26.x lights the world through a new lightmap, so also add a hidden client-side Night Vision
        if (c.player != null && !c.player.hasEffect(MobEffects.NIGHT_VISION)) {
            c.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 1200, 0, false, false, false));
            nvAdded = true;
        }
    }

    private static void restore(Minecraft c) {
        if (nvAdded && c.player != null) {
            c.player.removeEffect(MobEffects.NIGHT_VISION);
        }
        nvAdded = false;
        if (original != null && c.options != null) {
            set(c.options.gamma(), original);
            original = null;
        }
    }
}
