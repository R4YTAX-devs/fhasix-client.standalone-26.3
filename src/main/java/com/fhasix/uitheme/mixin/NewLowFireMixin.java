package com.fhasix.uitheme.mixin;

import com.fhasix.uitheme.NewSafe;
import com.fhasix.uitheme.NewTweaks;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Low Fire: moves the fire you see when you burn down. Targeted by name (the class is looked up as a string), and the
 * pose stack is taken from the method's own arguments by type, so the exact argument list does not matter.
 */
@Mixin(targets = "net.minecraft.client.renderer.ScreenEffectRenderer")
public abstract class NewLowFireMixin {

    @Inject(method = {"submitFire", "renderFire"}, at = @At("HEAD"), require = 0)
    private static void fhasix$lowFire(CallbackInfo ci, @Local(argsOnly = true) PoseStack poseStack) {
        try {
            float drop = NewTweaks.fireDrop();
            if (drop > 0f) {
                poseStack.translate(0.0F, -drop, 0.0F);
            }
        } catch (Exception | LinkageError t) {
            NewSafe.failed("low_fire", t);
        }
    }
}
