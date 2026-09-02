package dev.obsidian.mixin;

import dev.obsidian.render.visibility.LargeSceneLifecycleEvents;
import net.minecraft.client.multiplayer.ClientChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observation-only hook for Minecraft 26.2's exact section emptiness lifecycle signal. */
@Mixin(ClientChunkCache.class)
public abstract class ClientChunkCacheMixin {
    @Inject(method = "onSectionEmptinessChanged(IIIZ)V", at = @At("TAIL"))
    private void obsidian$p4SectionEmptinessChanged(
            int sectionX,
            int sectionY,
            int sectionZ,
            boolean hasOnlyAir,
            CallbackInfo ci) {
        LargeSceneLifecycleEvents.sectionEmptinessChanged(
                sectionX, sectionY, sectionZ, hasOnlyAir);
    }
}
