package com.fhasix.uitheme.mixin;

import com.fhasix.uitheme.NewNoCrash;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * No Crash: wraps the client's per-frame call inside MinecraftClient.run(). If the frame throws,
 * NewNoCrash decides whether to swallow it (show the crash screen) or let the normal crash happen.
 */
@Mixin(Minecraft.class)
public abstract class NewCrashMixin {

    @WrapOperation(method = "run",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runTick(Z)V"),
            require = 0)
    private void fhasix$guardFrame(Minecraft self, boolean tick, Operation<Void> original) {
        try {
            original.call(self, tick);
        } catch (Throwable t) {
            if (!NewNoCrash.handle(self, t)) {
                throw t;
            }
        }
    }
}
