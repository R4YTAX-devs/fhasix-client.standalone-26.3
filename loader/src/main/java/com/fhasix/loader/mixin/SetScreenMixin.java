package com.fhasix.loader.mixin;

import com.fhasix.loader.FhasixLoader;
import com.fhasix.loader.UpdateScreen;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Puts the Update screen in front of the very first title screen. */
@Mixin(Gui.class)
public abstract class SetScreenMixin {

    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true, require = 0)
    private Screen fhasixloader$gate(Screen screen) {
        if (screen instanceof TitleScreen && !FhasixLoader.gateDone) {
            FhasixLoader.gateDone = true;
            return new UpdateScreen(screen);
        }
        return screen;
    }
}
