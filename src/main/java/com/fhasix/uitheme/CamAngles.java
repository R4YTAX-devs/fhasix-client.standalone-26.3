package com.fhasix.uitheme;

import net.minecraft.client.Camera;

/** Camera yaw/pitch. Minecraft 26.x renamed these accessors, so try each known name. */
final class CamAngles {
    private CamAngles() {
    }

    static float yaw(Camera cam) {
        return call(cam, "getYRot", "yRot", "getYaw", "yaw");
    }

    static float pitch(Camera cam) {
        return call(cam, "getXRot", "xRot", "getPitch", "pitch");
    }

    private static float call(Object o, String... names) {
        for (String n : names) {
            try {
                java.lang.reflect.Method m = o.getClass().getMethod(n);
                return ((Number) m.invoke(o)).floatValue();
            } catch (Throwable ignored) {
            }
        }
        return 0f;
    }
}
