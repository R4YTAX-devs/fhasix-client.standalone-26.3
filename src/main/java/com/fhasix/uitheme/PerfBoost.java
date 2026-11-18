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
 * A String value means "the enum constant with that name" (for options that are enums).
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
            {"prioritizeChunkUpdates", "NONE"},      // fully threaded chunk building: no stalls
    };

    /** Far-away texture clarity (no blur on the ground in the distance). Applied with the Texture Detail mode. */
    private static final Object[][] CLARITY = {
            {"textureFiltering", "ANISOTROPIC"},
            {"maxAnisotropyBit", 2},                 // 4x
    };

    private static final Map<String, Object> ORIGINAL = new HashMap<>();

    static void apply(Minecraft c, boolean on) {
        run(c, on, SETTINGS);
    }

    static void clarity(Minecraft c, boolean on) {
        run(c, on, CLARITY);
    }

    private static void run(Minecraft c, boolean on, Object[][] table) {
        Options o = c.options;
        if (o == null) {
            return;
        }
        for (Object[] s : table) {
            String name = (String) s[0];
            try {
                Method accessor = Options.class.getMethod(name);
                Object inst = accessor.invoke(o);
                if (!(inst instanceof OptionInstance<?> opt)) {
                    continue;
                }
                if (on) {
                    Object cur = opt.get();
                    Object want = convert(cur, s[1]);
                    if (!ORIGINAL.containsKey(name) && want != null) {
                        ORIGINAL.put(name, cur);
                        set(opt, want);
                    }
                } else if (ORIGINAL.containsKey(name)) {
                    set(opt, ORIGINAL.remove(name));
                }
            } catch (Throwable ignored) {
                // this option does not exist in this Minecraft version
            }
        }
    }

    /** The value to set, in the option's own type, or null when it does not fit this option. */
    private static Object convert(Object cur, Object want) {
        if (cur == null) {
            return null;
        }
        if (want instanceof String name && cur instanceof Enum<?> sample) {
            Object[] all = sample.getDeclaringClass().getEnumConstants();
            if (all != null) {
                for (Object k : all) {
                    if (((Enum<?>) k).name().equals(name)) {
                        return k;
                    }
                }
            }
            return null;
        }
        return cur.getClass() == want.getClass() ? want : null;
    }

    private static void set(OptionInstance<?> opt, Object value) throws Exception {
        Method m = OptionInstance.class.getMethod("set", Object.class);
        m.invoke(opt, value);
    }
}
