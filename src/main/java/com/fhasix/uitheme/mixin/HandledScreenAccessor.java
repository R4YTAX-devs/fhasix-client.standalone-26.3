package com.fhasix.uitheme.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Gives Shulker Tooltips the slot under the mouse in inventory / container screens. */
@Mixin(AbstractContainerScreen.class)
public interface HandledScreenAccessor {

    @Accessor("hoveredSlot")
    Slot fhasix$getFocusedSlot();
}
