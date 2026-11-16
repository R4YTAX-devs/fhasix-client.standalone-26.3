package com.fhasix.uitheme;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Autohide HUD: hides the chosen vanilla HUD parts (chat, scoreboard, potion effects, hotbar)
 * around PvP.
 *
 * Mode "During combat" (default): the parts are hidden while you are fighting (you got hurt or
 * you attacked in the last few seconds) and visible when you are standing around.
 * Mode "When idle": the opposite - hidden while standing, shown in combat.
 * Any open screen (inventory, chat, the Fhasix menus) always shows everything.
 *
 * Done by replacing the vanilla HUD element with a wrapper that only calls the original while it
 * should be visible - no mixins needed.
 */
final class NewAutohide {

    static final String ID = "autohide_hud";

    private static long lastCombat = 0L;

    private NewAutohide() {
    }

    static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(NewSafe.tick("autohide", NewAutohide::tick));
        wrap(VanillaHudElements.CHAT, "chat");
        wrap(VanillaHudElements.SCOREBOARD, "scoreboard");
        Identifier effects = effectsElement();
        if (effects != null) {
            wrap(effects, "effects");
        }
        wrap(VanillaHudElements.HOTBAR, "hotbar");
    }

    /** Fabric renamed the status-effects HUD constant across 26.x, so look it up by name (null = skip this part). */
    private static Identifier effectsElement() {
        for (String n : new String[]{"STATUS_EFFECTS", "EFFECTS", "MOB_EFFECTS"}) {
            try {
                Object v = VanillaHudElements.class.getField(n).get(null);
                if (v instanceof Identifier id) {
                    return id;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static void wrap(Identifier vanillaElement, String optionKey) {
        HudElementRegistry.replaceElement(vanillaElement, original -> NewSafe.replace("autohide", original, (context, tickCounter) -> {
            if (visible(optionKey)) {
                original.extractRenderState(context, tickCounter);
            }
        }));
    }

    private static boolean visible(String optionKey) {
        Minecraft c = Minecraft.getInstance();
        if (c.player == null || !NewModSettings.isEnabled(c, ID) || !NewModSettings.optBool(c, ID, optionKey)) {
            return true;
        }
        if (c.gui.screen() != null) {
            return true;
        }
        boolean inCombat = System.currentTimeMillis() - lastCombat < NewModSettings.opt(c, ID, "delay") * 1000L;
        return NewModSettings.opt(c, ID, "mode") == 0 ? !inCombat : inCombat;
    }

    private static void tick(Minecraft c) {
        if (c.player == null || !NewModSettings.isEnabled(c, ID)) {
            return;
        }
        if (NewModSettings.optBool(c, ID, "ondamage") && c.player.hurtTime > 0) {
            lastCombat = System.currentTimeMillis();
        }
        if (NewModSettings.optBool(c, ID, "oncombat") && NewHud.cps() > 0) {
            lastCombat = System.currentTimeMillis();
        }
    }
}
