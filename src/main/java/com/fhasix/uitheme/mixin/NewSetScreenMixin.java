package com.fhasix.uitheme.mixin;

import com.fhasix.uitheme.NewGuiScale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** GUI scale lock: tells NewGuiScale which screen is about to open, before it gets its width/height. */
@Mixin(Gui.class)
public abstract class NewSetScreenMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), require = 0)
    private void fhasix$guiScale(Screen screen, CallbackInfo ci) {
        NewGuiScale.onSetScreen(Minecraft.getInstance(), screen);
    }
}
