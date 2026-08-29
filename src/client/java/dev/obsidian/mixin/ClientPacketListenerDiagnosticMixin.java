package dev.obsidian.mixin;

import dev.obsidian.render.terrain.PartialRemeshSingleSectionCallerDiagnostics;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** P3.9 dev21 caller scope for broad biome-packet section invalidations. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerDiagnosticMixin {
    @Inject(
            method = "handleChunksBiomes(Lnet/minecraft/network/protocol/game/ClientboundChunksBiomesPacket;)V",
            at = @At("HEAD"))
    private void obsidian$enterBiomePacket(ClientboundChunksBiomesPacket packet, CallbackInfo ci) {
        PartialRemeshSingleSectionCallerDiagnostics.enterCaller(
                PartialRemeshSingleSectionCallerDiagnostics.CALLER_BIOME_PACKET);
    }

    @Inject(
            method = "handleChunksBiomes(Lnet/minecraft/network/protocol/game/ClientboundChunksBiomesPacket;)V",
            at = @At("RETURN"))
    private void obsidian$exitBiomePacket(ClientboundChunksBiomesPacket packet, CallbackInfo ci) {
        PartialRemeshSingleSectionCallerDiagnostics.exitCaller();
    }
}
