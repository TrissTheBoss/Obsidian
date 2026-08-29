package dev.obsidian.mixin;

import dev.obsidian.render.frame.FrameCoordinator;
import dev.obsidian.render.terrain.PartialRemeshLightUpdatePreservation;
import dev.obsidian.render.terrain.PartialRemeshProvenanceDiagnostics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Emits bounded P3.9 provenance and dev22 correction evidence at closure. */
@Mixin(FrameCoordinator.class)
public abstract class FrameCoordinatorDiagnosticMixin {
    @Inject(method = "close", at = @At("HEAD"))
    private void obsidian$logFinalProvenanceDiagnostics(CallbackInfo ci) {
        PartialRemeshProvenanceDiagnostics.logFinal();
        PartialRemeshLightUpdatePreservation.logFinal();
    }
}
