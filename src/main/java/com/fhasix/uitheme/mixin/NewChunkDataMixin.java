package com.fhasix.uitheme.mixin;

import com.fhasix.uitheme.NewPerf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Performance > Lazy Chunk Loading: far chunk packets are applied later (see NewPerf.holdChunk). */
@Mixin(ClientPacketListener.class)
public abstract class NewChunkDataMixin {

    @Inject(method = "handleLevelChunkWithLight", at = @At("HEAD"), cancellable = true, require = 0)
    private void fhasix$lazy(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
        if (NewPerf.holdChunk((ClientPacketListener) (Object) this, packet)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleForgetLevelChunk", at = @At("HEAD"), require = 0)
    private void fhasix$unload(ClientboundForgetLevelChunkPacket packet, CallbackInfo ci) {
        NewPerf.dropHeld(packet.pos().x(), packet.pos().z());
    }
}
