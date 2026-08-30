package dev.obsidian.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuSampler;
import dev.obsidian.bootstrap.ObsidianBootstrap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** P3.10 same-OPAQUE-pass replacement seam proven by A-0190. */
@Mixin(ChunkSectionsToRender.class)
public abstract class ChunkSectionsToRenderMixin {
    @Inject(
            method = "renderGroup",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderPass;close()V",
                    ordinal = 0,
                    shift = At.Shift.BEFORE))
    private void obsidian$encodeProductionTerrainReplacements(
            ChunkSectionLayerGroup group,
            GpuSampler sampler,
            CallbackInfo ci,
            @Local RenderPass pass) {
        if (group == ChunkSectionLayerGroup.OPAQUE) {
            ObsidianBootstrap.encodeProductionTerrainReplacements(
                    pass, Minecraft.getInstance().gameRenderer);
        }
    }
}
