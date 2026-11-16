package com.fhasix.uitheme.mixin;

import com.fhasix.uitheme.NewGuiScale;
import com.fhasix.uitheme.NewSafe;
import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** GUI scale lock: while a Fhasix screen is open the calculated GUI scale is always 2 (if the window is big enough). */
@Mixin(Window.class)
public abstract class NewWindowMixin {

    @Shadow
    public abstract int getWidth();

    @Shadow
    public abstract int getHeight();

    @Inject(method = "calculateScale", at = @At("RETURN"), cancellable = true, require = 0)
    private void fhasix$lockScale(int guiScale, boolean forceUnicodeFont, CallbackInfoReturnable<Integer> cir) {
        try {
            if (NewGuiScale.lockWanted() && getWidth() / NewGuiScale.LOCKED >= 320
                    && getHeight() / NewGuiScale.LOCKED >= 240) {
                cir.setReturnValue(NewGuiScale.LOCKED);
            }
        } catch (Exception | LinkageError t) {
            NewSafe.failed("gui_scale_window", t);
        }
    }
}
