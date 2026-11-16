package com.fhasix.uitheme;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Posts a line to the in-game chat. The ChatComponent API changed in 26.x, so try the known names. */
final class ChatMsg {
    private ChatMsg() {
    }

    static void post(Minecraft mc, Component message) {
        Object chat = mc.gui.hud.getChat();
        for (String n : new String[]{"addClientSystemMessage", "addMessage"}) {
            for (java.lang.reflect.Method m : chat.getClass().getMethods()) {
                if (m.getName().equals(n) && m.getParameterCount() == 1 && m.getParameterTypes()[0] == Component.class) {
                    try {
                        m.invoke(chat, message);
                        return;
                    } catch (Throwable ignored) {
                        return;
                    }
                }
            }
        }
    }
}
