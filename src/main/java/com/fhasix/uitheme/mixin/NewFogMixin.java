package com.fhasix.uitheme.mixin;

import com.fhasix.uitheme.NewFog;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Custom Fog (26.3). FogRenderer.setupFog(...) builds the fog data for the frame and returns it; Iris and
 * NeoForge hook the same point. At the end of that method the fog data is passed to NewFog, which changes
 * the colour, the start / end distances and the sky / cloud end in place.
 *
 * The handler takes the return value as Object on purpose: whatever setupFog returns, nothing here can fail with
 * a wrong cast (NewFog only touches it when it really is the fog data). require = 0, so if the method were ever
 * renamed the mod just logs it instead of crashing.
 */
@Mixin(FogRenderer.class)
public abstract class NewFogMixin {

    @Inject(method = "setupFog", at = @At("RETURN"), require = 0)
    private void fhasix$fog(CallbackInfoReturnable<Object> cir) {
        NewFog.applyData(cir.getReturnValue());
    }
}
