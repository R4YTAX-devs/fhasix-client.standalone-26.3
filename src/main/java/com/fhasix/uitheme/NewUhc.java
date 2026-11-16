package com.fhasix.uitheme;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.client.Minecraft;

/**
 * UHC Overlay: dropped golden apples / apples, gold ingots and player heads are drawn bigger on
 * the ground so they are easy to spot in a fight (used by mixin/NewItemEntityMixin). The ore
 * outlines are drawn by NewOreHighlight.
 */
public final class NewUhc {

    static final String ID = "uhc_overlay";

    private NewUhc() {
    }

    /** Scale for a DROPPED item entity; 1.0 = normal. */
    public static float droppedScale(ItemStack s) {
        if (s.isEmpty()) {
            return 1f;
        }
        Minecraft c = Minecraft.getInstance();
        if (c == null || !NewModSettings.isEnabled(c, ID)) {
            return 1f;
        }
        boolean hit = (NewModSettings.optBool(c, ID, "apples")
                && (s.is(Items.APPLE) || s.is(Items.GOLDEN_APPLE) || s.is(Items.ENCHANTED_GOLDEN_APPLE)))
                || (NewModSettings.optBool(c, ID, "gold") && s.is(Items.GOLD_INGOT))
                || (NewModSettings.optBool(c, ID, "heads") && s.is(Items.PLAYER_HEAD));
        return hit ? NewModSettings.opt(c, ID, "scale") / 100f : 1f;
    }
}
