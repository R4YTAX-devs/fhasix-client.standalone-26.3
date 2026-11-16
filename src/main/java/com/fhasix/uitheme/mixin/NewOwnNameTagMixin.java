package com.fhasix.uitheme.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The REAL Minecraft name tag, for you only: vanilla never draws the name tag of the player you are
 * playing, so hasLabel() is forced to true for the local player (third person then shows the normal
 * name tag, with the vanilla background, text and see-through look). The Fhasix F logo - a glyph of
 * the default font, see assets/minecraft/font/default.json - is put in front of the name. Default
 * feature: no setting. Other players and mobs are not touched.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class NewOwnNameTagMixin {

    /** Private-use character that the bundled font maps to the Fhasix F logo. */
    private static final String LOGO = "\ue9a1";

    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void fhasix$ownLabel(LivingEntity entity, double squaredDistance, CallbackInfoReturnable<Boolean> cir) {
        try {
            Minecraft c = Minecraft.getInstance();
            if (entity == c.player && !c.gui.hud.isHidden()) {
                cir.setReturnValue(true);
            }
        } catch (Exception | LinkageError t) {
            com.fhasix.uitheme.NewSafe.failed("own_nametag", t);
        }
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"), require = 0)
    private void fhasix$ownLogo(LivingEntity entity, LivingEntityRenderState state, float tickProgress, CallbackInfo ci) {
        try {
            if (entity != Minecraft.getInstance().player) {
                return;
            }
            if (state.nameTagAttachment == null) {
                state.nameTagAttachment = entity.getAttachments().getNullable(
                        EntityAttachment.NAME_TAG, 0, entity.getViewYRot(tickProgress));
            }
            if (state.nameTagAttachment == null) {
                return; // no position for a tag: leave the state alone
            }
            Component name = state.nameTag != null ? state.nameTag : entity.getName();
            if (!name.getString().startsWith(LOGO)) {
                state.nameTag = Component.literal(LOGO + " ").append(name);
            }
        } catch (Exception | LinkageError t) {
            com.fhasix.uitheme.NewSafe.failed("own_nametag", t);
        }
    }
}
