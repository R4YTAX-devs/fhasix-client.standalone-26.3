package com.fhasix.uitheme.mixin;

import com.fhasix.uitheme.NewAnimations;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.ItemStack;
import com.mojang.math.Axis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Animations (1.7 block-hitting): right after the game has positioned the held item (equip + swing
 * offsets) and right before it draws it, tilt a sword into the old blocking pose while the use key
 * is held. Because this is injected before the draw call, the swing animation keeps playing.
 *
 * NOTE FOR WHOEVER COMPILES THIS: written without being able to compile it. If the renderItem call
 * name/owner differs in 1.21.11, adapt the @At target. The rotation numbers are the classic 1.8
 * values; they may need a small tweak in game to look right.
 */
@Mixin(targets = "net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer")
public abstract class NewHandMixin {

    @Inject(method = "renderArmWithItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/FirstPersonHandsAndItemsRenderer;renderItem"),
            require = 0)
    private void fhasix$blockPose(CallbackInfo ci,
                                  @Local(argsOnly = true) PoseStack matrices,
                                  @Local(argsOnly = true) ItemStack item) {
        try {
            if (NewAnimations.blocking(item)) {
                matrices.translate(-0.1f, 0.15f, 0.0f);
                matrices.rotateDegrees(Axis.YP, 30f);
                matrices.rotateDegrees(Axis.XP, -80f);
                matrices.rotateDegrees(Axis.YP, 60f);
                matrices.scale(0.9f, 0.9f, 0.9f);
            }
        } catch (Exception | LinkageError t) {
            com.fhasix.uitheme.NewSafe.failed("hand_mixin", t);
        }
    }
}
