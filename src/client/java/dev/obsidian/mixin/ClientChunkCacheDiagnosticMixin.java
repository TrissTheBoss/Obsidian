package dev.obsidian.mixin;

import dev.obsidian.render.terrain.PartialRemeshLightUpdatePreservation;
import dev.obsidian.render.terrain.PartialRemeshSingleSectionCallerDiagnostics;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** P3.9 dev21/dev22 caller scope for exact one-section light invalidations. */
@Mixin(ClientChunkCache.class)
public abstract class ClientChunkCacheDiagnosticMixin {
    @Inject(
            method = "onLightUpdate(Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/SectionPos;)V",
            at = @At("HEAD"))
    private void obsidian$enterLightUpdate(LightLayer layer, SectionPos sectionPos, CallbackInfo ci) {
        PartialRemeshLightUpdatePreservation.enterLightUpdate();
        PartialRemeshSingleSectionCallerDiagnostics.enterCaller(
                PartialRemeshSingleSectionCallerDiagnostics.CALLER_LIGHT_UPDATE);
    }

    @Inject(
            method = "onLightUpdate(Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/SectionPos;)V",
            at = @At("RETURN"))
    private void obsidian$exitLightUpdate(LightLayer layer, SectionPos sectionPos, CallbackInfo ci) {
        PartialRemeshSingleSectionCallerDiagnostics.exitCaller();
        PartialRemeshLightUpdatePreservation.exitLightUpdate();
    }
}
