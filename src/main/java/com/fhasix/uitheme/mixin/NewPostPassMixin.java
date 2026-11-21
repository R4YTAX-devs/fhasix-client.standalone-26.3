package com.fhasix.uitheme.mixin;

import com.fhasix.uitheme.NewMotionBlur;
import com.fhasix.uitheme.NewSafe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Motion Blur (26.3): right before a post pass is added to the frame, the blend factor of our pass (a small uniform
 * buffer) is refreshed from the real frame time. The target is given by name (string) and the pass is only touched
 * through reflection inside NewMotionBlur, so a changed name in a future Minecraft build just means this hook is
 * skipped (require = 0) and the mod falls back to its fixed-strength effects; it can never stop the game from starting.
 */
@Mixin(targets = {"net.minecraft.client.renderer.PostPass"})
public abstract class NewPostPassMixin {

    @Inject(method = "addToFrame", at = @At("HEAD"), require = 0)
    private void fhasix$refreshBlend(CallbackInfo ci) {
        try {
            NewMotionBlur.beforePass(this);
        } catch (Exception | LinkageError t) {
            NewSafe.failed("motion_blur_inject", t);
        }
    }
}
