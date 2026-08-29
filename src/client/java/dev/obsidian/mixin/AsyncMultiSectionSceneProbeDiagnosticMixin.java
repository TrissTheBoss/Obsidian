package dev.obsidian.mixin;

import dev.obsidian.render.terrain.AsyncMultiSectionSceneProbe;
import dev.obsidian.render.terrain.PartialRemeshProvenanceDiagnostics;
import dev.obsidian.render.terrain.SectionLifecycleEvents;
import java.lang.reflect.Field;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** P3.9 dev20 observational context capture; no target control flow is modified. */
@Mixin(AsyncMultiSectionSceneProbe.class)
public abstract class AsyncMultiSectionSceneProbeDiagnosticMixin {
    @Shadow private boolean partialRemeshWindowArmed;
    @Shadow private AsyncMultiSectionSceneProbe.State state;
    @Shadow private boolean centerKnown;
    @Shadow @Final private SectionLifecycleEvents.Cursor lifecycleCursor;

    @Inject(method = "preparePartialRemeshEpisode", at = @At("HEAD"))
    private void obsidian$captureProvenanceContext(int reasons, CallbackInfo ci) {
        if (!partialRemeshWindowArmed) return;
        boolean pending = false;
        boolean pendingProbeAvailable = true;
        try {
            Field field = AsyncMultiSectionSceneProbe.class.getDeclaredField("pendingPartialEpisode");
            field.setAccessible(true);
            pending = field.get(this) != null;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            pendingProbeAvailable = false;
        }
        PartialRemeshProvenanceDiagnostics.captureContext(
                state == null ? -1 : state.ordinal(), centerKnown, pending, pendingProbeAvailable,
                lifecycleCursor == null ? 0 : lifecycleCursor.lastRelevantEventCount());
    }
}
