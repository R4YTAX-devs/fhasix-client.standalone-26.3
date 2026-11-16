package com.fhasix.uitheme;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

/**
 * Time Changer: locks the time of day you SEE (client side only - the server clock is untouched).
 * 0 = sunrise, 6000 = noon, 12000 = sunset, 18000 = midnight.
 */
final class NewTimeChanger {

    static final String ID = "time_changer";
    private static java.lang.reflect.Method setter;
    private static boolean looked;

    private NewTimeChanger() {
    }

    static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(NewSafe.tick("time_changer", c -> {
            if (c.level != null && NewModSettings.isEnabled(c, ID)) {
                Object data = c.level.getLevelData();
                if (!looked) {
                    looked = true;
                    for (String n : new String[]{"setDayTime", "setTimeOfDay"}) {
                        try {
                            setter = data.getClass().getMethod(n, long.class);
                            setter.setAccessible(true);
                            break;
                        } catch (Throwable ignored) {
                            setter = null;
                        }
                    }
                }
                if (setter != null) {
                    try {
                        setter.invoke(data, (long) NewModSettings.opt(c, ID, "time"));
                    } catch (Throwable ignored) {
                        // 26.3 moved the world clock; if this setter is gone the time just stays normal
                    }
                }
            }
        }));
    }
}
