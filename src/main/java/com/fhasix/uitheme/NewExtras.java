package com.fhasix.uitheme;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;

/** Update 5.7 features: starts Auto Component, Motion Blur, Hit Color, Waypoints, Minimap and Discord Rich Presence. */
final class NewExtras {

    private NewExtras() {
    }

    static void init(KeyMapping.Category category) {
        NewAutoText.init();
        NewWaypoints.init(category);
        NewMinimap.init();
        NewDiscord.init();
        ClientTickEvents.END_CLIENT_TICK.register(NewSafe.tick("motion_blur", NewMotionBlur::tick));
        ClientTickEvents.END_CLIENT_TICK.register(NewSafe.tick("hit_color", NewHitColor::tick));
    }
}
