package com.fhasix.uitheme;

import com.fhasix.uitheme.mixin.HandledScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;
import net.minecraft.core.NonNullList;

/**
 * Shulker Tooltips: while the mouse is over a shulker box in an inventory / container screen, a
 * rounded panel next to the cursor shows what is inside it as a 9x3 grid. No settings.
 */
final class NewShulkerTooltips {

    static final String ID = "shulker_tooltips";

    private static final int SLOT = 18;
    private static final int PAD = 7;

    private NewShulkerTooltips() {
    }

    static void init() {
        ScreenEvents.AFTER_INIT.register((minecraft, screen, w, h) -> {
            if (screen instanceof AbstractContainerScreen<?>) {
                ScreenEvents.afterExtract(screen).register((s, g, mx, my, delta) -> NewSafe.run("shulker_tooltips", () -> draw(g, (AbstractContainerScreen<?>) s, mx, my)));
            }
        });
    }

    private static boolean isShulker(ItemStack st) {
        return !st.isEmpty() && st.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock;
    }

    private static void draw(GuiGraphicsExtractor g, AbstractContainerScreen<?> screen, int mx, int my) {
        Minecraft c = Minecraft.getInstance();
        if (!NewModSettings.isEnabled(c, ID)) {
            return;
        }
        Slot slot = ((HandledScreenAccessor) screen).fhasix$getFocusedSlot();
        if (slot == null || !slot.hasItem() || !isShulker(slot.getItem())) {
            return;
        }
        ItemContainerContents contents = slot.getItem().get(DataComponents.CONTAINER);
        if (contents == null) {
            return;
        }
        NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
        contents.copyInto(items);

        int w = SLOT * 9 + PAD * 2;
        int h = SLOT * 3 + PAD * 2;
        // above-left of the cursor, so the vanilla tooltip (below-right) is not covered
        int x = Math.max(4, Math.min(screen.width - w - 4, mx - w - 6));
        int y = Math.max(4, Math.min(screen.height - h - 4, my - h - 10));

        NewUi.frame(g, x, y, w, h, 6, NewUi.CARD_BORDER, NewUi.PANEL_T, NewUi.PANEL_B);
        for (int i = 0; i < 27; i++) {
            int sx = x + PAD + (i % 9) * SLOT;
            int sy = y + PAD + (i / 9) * SLOT;
            NewUi.rrect(g, sx, sy, SLOT - 1, SLOT - 1, 3, NewUi.BTN_T, NewUi.BTN_B);
            ItemStack st = items.get(i);
            if (!st.isEmpty()) {
                g.item(st, sx + 1, sy + 1);
                g.itemDecorations(c.font, st, sx + 1, sy + 1);
            }
        }
    }
}
