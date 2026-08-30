package dev.obsidian.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.obsidian.bootstrap.ObsidianBootstrap;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** P3.10 exact section/layer suppression seam proven by A-0190. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererTerrainReplacementMixin {
    @Inject(method = "prepareChunkRenders", at = @At("HEAD"))
    private void obsidian$beginProductionTerrainPlan(
            Matrix4fc cameraMatrix,
            CallbackInfoReturnable<ChunkSectionsToRender> cir) {
        ObsidianBootstrap.beginProductionTerrainPreparation();
    }

    @ModifyExpressionValue(
            method = "prepareChunkRenders",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/chunk/SectionMesh;getSectionDraw(Lnet/minecraft/client/renderer/chunk/ChunkSectionLayer;)Lnet/minecraft/client/renderer/chunk/SectionMesh$SectionDraw;"))
    private SectionMesh.SectionDraw obsidian$suppressClaimedTerrainDraw(
            SectionMesh.SectionDraw original,
            @Local SectionRenderDispatcher.RenderSection renderSection,
            @Local ChunkSectionLayer layer) {
        if (original == null || (layer != ChunkSectionLayer.SOLID && layer != ChunkSectionLayer.CUTOUT)) {
            return original;
        }
        int sectionX = renderSection.getRenderOrigin().getX() >> 4;
        int sectionY = renderSection.getRenderOrigin().getY() >> 4;
        int sectionZ = renderSection.getRenderOrigin().getZ() >> 4;
        return ObsidianBootstrap.tryClaimProductionTerrainReplacement(sectionX, sectionY, sectionZ, layer)
                ? null
                : original;
    }
}
