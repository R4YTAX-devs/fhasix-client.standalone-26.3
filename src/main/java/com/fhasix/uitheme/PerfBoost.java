package com.fhasix.uitheme;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;

/**
 * Extra Boost: switches off the video options that cost the most FPS, using Minecraft's own option objects.
 * Options are looked up by name, so one that this Minecraft version does not have is simply skipped.
 * The player's own value is remembered and put back when the mode is turned off.
 */
final class PerfBoost {
    private PerfBoost() {
    }

    /** {option name, value to use}. The value's type must match the option's own type or it is skipped. */
    private static final Object[][] SETTINGS = {
            {"improvedTransparency", Boolean.FALSE},
            {"cutoutLeaves", Boolean.FALSE},
            {"chunkSectionFadeInTime", 0.0},
            {"weatherRadius", 3},
            {"cloudRange", 32},
            {"entityShadows", Boolean.FALSE},
    };

    private static final Map<String, Object> ORIGINAL = new HashMap<>();

    static void apply(Minecraft c, boolean on) {
        Options o = c.options;
        if (o == null) {
            return;
        }
        for (Object[] s : SETTINGS) {
            String name = (String) s[0];
            try {
                Method accessor = Options.class.getMethod(name);
                Object inst = accessor.invoke(o);
                if (!(inst instanceof OptionInstance<?> opt)) {
                    continue;
                }
                if (on) {
                    Object cur = opt.get();
                    if (!ORIGINAL.containsKey(name) && cur != null && cur.getClass() == s[1].getClass()) {
                        ORIGINAL.put(name, cur);
                        set(opt, s[1]);
                    }
                } else if (ORIGINAL.containsKey(name)) {
                    set(opt, ORIGINAL.remove(name));
                }
            } catch (Throwable ignored) {
                // this option does not exist in this Minecraft version
            }
        }
    }

    private static void set(OptionInstance<?> opt, Object value) throws Exception {
        Method m = OptionInstance.class.getMethod("set", Object.class);
        m.invoke(opt, value);
    }
}
