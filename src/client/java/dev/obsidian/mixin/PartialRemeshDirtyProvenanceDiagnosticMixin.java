package dev.obsidian.mixin;

import dev.obsidian.render.terrain.PartialRemeshDirtyProvenance;
import dev.obsidian.render.terrain.PartialRemeshProvenanceDiagnostics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** P3.9 dev19 observational drain snapshot; target data and reset semantics are unchanged. */
@Mixin(PartialRemeshDirtyProvenance.class)
public abstract class PartialRemeshDirtyProvenanceDiagnosticMixin {
    @Inject(method = "drainInto", at = @At("TAIL"))
    private static void obsidian$captureProvenanceDrain(
            PartialRemeshDirtyProvenance.Drain out, CallbackInfo ci) {
        PartialRemeshProvenanceDiagnostics.captureDrain(
                out.count(), out.fallbackFlags(), out.overflowEvents());
    }
}
