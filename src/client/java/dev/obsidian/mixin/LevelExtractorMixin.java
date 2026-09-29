package dev.obsidian.mixin;

import dev.obsidian.render.terrain.SectionLifecycleEvents;
import dev.obsidian.render.visibility.LargeSceneLifecycleEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {
    @Inject(method = "setSectionDirty(IIIZ)V", at = @At("TAIL"))
    private void obsidian$sectionDirty(
            int sectionX,
            int sectionY,
            int sectionZ,
            boolean dirtyFromPlayer,
            CallbackInfo ci) {
        SectionLifecycleEvents.sectionDirty(sectionX, sectionY, sectionZ, dirtyFromPlayer);
    }

    @Inject(method = "setLevel", at = @At("TAIL"))
    private void obsidian$levelChanged(ClientLevel level, CallbackInfo ci) {
        SectionLifecycleEvents.worldChanged();
        LargeSceneLifecycleEvents.worldChanged();
    }
}
