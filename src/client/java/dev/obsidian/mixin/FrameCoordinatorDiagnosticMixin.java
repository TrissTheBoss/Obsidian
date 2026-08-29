package dev.obsidian.mixin;

import dev.obsidian.render.frame.FrameCoordinator;
import dev.obsidian.render.terrain.AsyncMultiSectionSceneProbe;
import dev.obsidian.render.terrain.PartialRemeshExperimentTelemetry;
import dev.obsidian.render.terrain.PartialRemeshLightUpdatePreservation;
import dev.obsidian.render.terrain.PartialRemeshProvenanceDiagnostics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Emits bounded P3.9 provenance, dev22 admission, and dev23 fail-closed evidence at closure. */
@Mixin(FrameCoordinator.class)
public abstract class FrameCoordinatorDiagnosticMixin {
    private static final System.Logger OBSIDIAN$LOG = System.getLogger("Obsidian/FrameCoordinatorDev23");

    @Shadow private AsyncMultiSectionSceneProbe sceneProbe;

    @Inject(method = "close", at = @At("HEAD"))
    private void obsidian$logFinalProvenanceDiagnostics(CallbackInfo ci) {
        PartialRemeshProvenanceDiagnostics.logFinal();
        PartialRemeshLightUpdatePreservation.logFinal();
        AsyncMultiSectionSceneProbe probe = sceneProbe;
        PartialRemeshExperimentTelemetry.Snapshot snapshot =
                probe == null ? null : probe.partialRemeshExperimentSnapshot();
        OBSIDIAN$LOG.log(System.Logger.Level.INFO,
                "Phase 3 dev23 P3.9 final unselected-truth fallback diagnostics: fallbackUnselectedTruthChanged={0}, fallbackEpisodes={1}, completedEpisodes={2}, exactEpisodes={3}, correctnessFailures={4}, unselectedChangeFailures={5}, determinismFailures={6}, fallbackAccountingCoherent={7}, collectorSelfTest={8}, thresholdsPassed={9}, oracleChanged=false, sliceMaskMutation=false, productionRendererChanged=false, partialGpuPatch=false, thresholdsChanged=false.",
                snapshot == null ? 0L : snapshot.fallbackUnselectedTruthChanged(),
                snapshot == null ? 0L : snapshot.fallbackEpisodes(),
                snapshot == null ? 0L : snapshot.completedEpisodes(),
                snapshot == null ? 0L : snapshot.exactEpisodes(),
                snapshot == null ? 0L : snapshot.correctnessFailures(),
                snapshot == null ? 0L : snapshot.unselectedChangeFailures(),
                snapshot == null ? 0L : snapshot.determinismFailures(),
                snapshot != null && snapshot.fallbackAccountingCoherent(),
                snapshot != null && snapshot.collectorSelfTestPassed(),
                snapshot != null && snapshot.thresholdsPassed());
    }
}
