package com.fhasix.uitheme.mixin;

import com.fhasix.uitheme.NewSafe;
import com.fhasix.uitheme.NewTweaks;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No Hurt Cam: skips the camera shake when you take damage (GameRenderer.bobHurt). */
@Mixin(GameRenderer.class)
public abstract class NewHurtCamMixin {

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true, require = 0)
    private void fhasix$noHurtCam(CallbackInfo ci) {
        try {
            if (NewTweaks.noHurtCam()) {
                ci.cancel();
            }
        } catch (Exception | LinkageError t) {
            NewSafe.failed("no_hurt_cam", t);
        }
    }
}
