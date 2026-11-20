package com.fhasix.uitheme.mixin;

import com.fhasix.uitheme.NewHitColor;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hit Color: hands the live overlay texture (its top 8 rows are the red hurt tint) to NewHitColor. */
@Mixin(OverlayTexture.class)
public abstract class NewOverlayMixin {

    @Inject(method = "<init>", at = @At("TAIL"), require = 0)
    private void fhasix$attach(CallbackInfo ci) {
        NewHitColor.attach(this);
    }
}
