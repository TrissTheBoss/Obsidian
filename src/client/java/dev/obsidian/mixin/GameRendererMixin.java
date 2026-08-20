package dev.obsidian.mixin;

import dev.obsidian.bootstrap.ObsidianBootstrap;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Exact Minecraft 26.2 P2.2 hook: after vanilla LevelRenderer.render returns,
 * while the world projection and live color/depth targets are still active and
 * before GameRenderer switches to HUD projection / clears depth.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",
                    shift = At.Shift.AFTER),
            require = 1)
    private void obsidian$afterVanillaWorldRender(DeltaTracker deltaTracker, CallbackInfo ci) {
        ObsidianBootstrap.onWorldRendered((GameRenderer) (Object) this);
    }
}
