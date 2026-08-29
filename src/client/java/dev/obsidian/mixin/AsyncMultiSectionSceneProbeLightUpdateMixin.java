package dev.obsidian.mixin;

import dev.obsidian.render.terrain.AsyncMultiSectionSceneProbe;
import dev.obsidian.render.terrain.PartialRemeshDirtyProvenance;
import dev.obsidian.render.terrain.PartialRemeshLightUpdatePreservation;
import dev.obsidian.render.terrain.SectionLifecycleEvents;
import java.lang.reflect.Field;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A-0182 dev22 shadow-only correction. Production invalidation is untouched;
 * this mixin can only prevent preparePartialRemeshEpisode from discarding an
 * already-pending shadow request when the otherwise-empty provenance interval
 * is proven to be same-section LIGHT_UPDATE only.
 */
@Mixin(AsyncMultiSectionSceneProbe.class)
public abstract class AsyncMultiSectionSceneProbeLightUpdateMixin {
    @Shadow private boolean partialRemeshWindowArmed;
    @Shadow @Final private PartialRemeshDirtyProvenance.Drain partialDirtyDrain;

    @Unique private static Field obsidian$pendingField;
    @Unique private static Field obsidian$pendingXField;
    @Unique private static Field obsidian$pendingYField;
    @Unique private static Field obsidian$pendingZField;

    @Inject(
            method = "preparePartialRemeshEpisode",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/obsidian/render/terrain/PartialRemeshDirtyProvenance;drainInto(Ldev/obsidian/render/terrain/PartialRemeshDirtyProvenance$Drain;)V",
                    shift = At.Shift.AFTER),
            cancellable = true)
    private void obsidian$preservePendingAcrossExactLightUpdate(int reasons, CallbackInfo ci) {
        if (!partialRemeshWindowArmed || reasons != SectionLifecycleEvents.REASON_SECTION_DIRTY) return;
        if (partialDirtyDrain.fallbackFlags() != 0 || partialDirtyDrain.count() != 0) return;

        try {
            Object pending = obsidian$pendingEpisode();
            if (pending == null) return;
            int sectionX = obsidian$pendingXField.getInt(pending);
            int sectionY = obsidian$pendingYField.getInt(pending);
            int sectionZ = obsidian$pendingZField.getInt(pending);
            if (PartialRemeshLightUpdatePreservation.tryPreserveEmptyProvenanceForPending(
                    partialDirtyDrain.count(), partialDirtyDrain.fallbackFlags(),
                    sectionX, sectionY, sectionZ)) {
                ci.cancel();
            }
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            PartialRemeshLightUpdatePreservation.recordReflectionFailure();
            // Fail closed: allow the original provenance fallback to run.
        }
    }

    @Unique
    private Object obsidian$pendingEpisode() throws ReflectiveOperationException {
        if (obsidian$pendingField == null) {
            Field pending = AsyncMultiSectionSceneProbe.class.getDeclaredField("pendingPartialEpisode");
            pending.setAccessible(true);
            obsidian$pendingField = pending;
        }
        Object value = obsidian$pendingField.get(this);
        if (value == null) return null;
        if (obsidian$pendingXField == null) {
            Class<?> type = value.getClass();
            Field x = type.getDeclaredField("sectionX");
            Field y = type.getDeclaredField("sectionY");
            Field z = type.getDeclaredField("sectionZ");
            x.setAccessible(true);
            y.setAccessible(true);
            z.setAccessible(true);
            obsidian$pendingXField = x;
            obsidian$pendingYField = y;
            obsidian$pendingZField = z;
        }
        return value;
    }
}
