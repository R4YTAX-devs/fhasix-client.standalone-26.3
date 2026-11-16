package com.fhasix.uitheme.mixin;

import com.fhasix.uitheme.NewTimeChanger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Time Changer. Since 26.1 the sky reads its time from the client clock manager, not from the level's day time.
 * The package name is listed a few ways (string targets never break the build); require = 0 keeps a miss harmless.
 */
@Mixin(targets = {
        "net.minecraft.client.multiplayer.ClientClockManager",
        "net.minecraft.client.ClientClockManager",
        "net.minecraft.client.clock.ClientClockManager",
        "net.minecraft.client.world.ClientClockManager"
})
public abstract class NewClockMixin {
    @Inject(method = "getTotalTicks", at = @At("RETURN"), cancellable = true, require = 0)
    private void fhasix$time(CallbackInfoReturnable<Long> cir) {
        try {
            cir.setReturnValue(NewTimeChanger.override(cir.getReturnValueJ()));
        } catch (Exception | LinkageError t) {
            com.fhasix.uitheme.NewSafe.failed("clock_mixin", t);
        }
    }
}
