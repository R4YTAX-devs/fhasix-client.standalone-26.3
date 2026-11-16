package com.fhasix.uitheme.mixin;

import com.fhasix.uitheme.FhasixSkins;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Equips the skin chosen in the Skin pop-up on the LOCAL player only (client-side: other players
 * see whatever the server/Mojang says).
 *
 * AbstractClientPlayerEntity.getSkin() is the single place the game asks "what does this player
 * look like": the entity renderer picks the slim/wide model from it, the render state copies the
 * texture from it, and first-person arms read it too. Replacing its return value therefore
 * changes the texture AND the model type everywhere at once (name verified against the 1.21.11
 * mappings: renamed from getSkinTextures in 1.21.9).
 *
 * The equipped FHASIX cape is layered on top here too (FhasixCapeSkin): vanilla's own cape renderer then draws it.
 *
 * require = 0 on purpose: if a future version moves or renames the method the injection is
 * skipped instead of crashing the game at startup - the skin simply won't apply.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class skin {

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true, require = 0)
    private void fhasix$equipSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        if (com.fhasix.uitheme.NewSafe.disabled("skin_mixin")) {
            return; // the account skin shows
        }
        try {
            Minecraft client = Minecraft.getInstance();
            if (client == null || client.player != (Object) this) {
                return;
            }
            PlayerSkin replaced = FhasixSkins.activeSkin(client, cir.getReturnValue());
            PlayerSkin shown = replaced != null ? replaced : cir.getReturnValue();
            PlayerSkin withCape = com.fhasix.uitheme.FhasixCapeSkin.apply(shown); // equipped FHASIX cape
            if (withCape != null) {
                cir.setReturnValue(withCape);
            } else if (replaced != null) {
                cir.setReturnValue(replaced);
            }
        } catch (Exception | LinkageError t) {
            com.fhasix.uitheme.NewSafe.failed("skin_mixin", t); // a bad skin file must never stop you joining a world
        }
    }
}
