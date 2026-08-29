package dev.obsidian.mixin;

import dev.obsidian.render.terrain.PartialRemeshExperimentTelemetry;
import dev.obsidian.render.terrain.PartialRemeshProvenanceDiagnostics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** P3.9 dev19 observes the existing telemetry fallback after it has been counted. */
@Mixin(PartialRemeshExperimentTelemetry.class)
public abstract class PartialRemeshExperimentTelemetryDiagnosticMixin {
    @Inject(method = "begin", at = @At("TAIL"))
    private void obsidian$beginProvenanceDiagnostics(CallbackInfo ci) {
        PartialRemeshProvenanceDiagnostics.begin();
    }

    @Inject(method = "recordFallback", at = @At("TAIL"))
    private void obsidian$observeProvenanceFallback(int reasonMask, CallbackInfo ci) {
        if (reasonMask == PartialRemeshExperimentTelemetry.FALLBACK_PROVENANCE) {
            PartialRemeshProvenanceDiagnostics.observeProvenanceFallback();
        }
    }
}
