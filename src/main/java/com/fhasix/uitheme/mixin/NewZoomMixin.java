package com.fhasix.uitheme.mixin;

import com.fhasix.uitheme.NewSafe;
import com.fhasix.uitheme.NewZoom;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zoom mod: narrows the field of view while the zoom key is held. require = 0 so a renamed method only disables zoom. */
@Mixin(GameRenderer.class)
public abstract class NewZoomMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true, require = 0)
    private void fhasix$zoom(CallbackInfoReturnable<Float> cir) {
        try {
            cir.setReturnValue(NewZoom.apply(cir.getReturnValueF()));
        } catch (Exception | LinkageError t) {
            NewSafe.failed("zoom_mixin", t);
        }
    }
}
