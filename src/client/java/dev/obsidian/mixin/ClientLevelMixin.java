package dev.obsidian.mixin;

import dev.obsidian.render.terrain.SectionLifecycleEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Inject(method = "onChunkLoaded", at = @At("TAIL"))
    private void obsidian$chunkLoaded(ChunkPos pos, CallbackInfo ci) {
        SectionLifecycleEvents.chunkLoaded(pos.x(), pos.z());
    }

    @Inject(method = "unload", at = @At("TAIL"))
    private void obsidian$chunkUnloaded(LevelChunk chunk, CallbackInfo ci) {
        ChunkPos pos = chunk.getPos();
        SectionLifecycleEvents.chunkUnloaded(pos.x(), pos.z());
    }
}
