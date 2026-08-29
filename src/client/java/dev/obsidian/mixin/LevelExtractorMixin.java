package dev.obsidian.mixin;

import dev.obsidian.render.terrain.PartialRemeshLightUpdatePreservation;
import dev.obsidian.render.terrain.PartialRemeshSectionDirtyOriginDiagnostics;
import dev.obsidian.render.terrain.PartialRemeshSingleSectionCallerDiagnostics;
import dev.obsidian.render.terrain.SectionLifecycleEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {
    @Unique private static final int OBSIDIAN_ORIGIN_STACK_CAPACITY = 8;
    @Unique private final int[] obsidian$originStack = new int[OBSIDIAN_ORIGIN_STACK_CAPACITY];
    @Unique private int obsidian$originDepth;
    @Unique private int obsidian$currentOrigin = PartialRemeshSectionDirtyOriginDiagnostics.ORIGIN_NONE;
    @Unique private int obsidian$overflowDepth;
    @Unique private int obsidian$originBeforeOverflow = PartialRemeshSectionDirtyOriginDiagnostics.ORIGIN_NONE;

    @Inject(
            method = "setBlockDirty(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)V",
            at = @At("HEAD"))
    private void obsidian$enterExactBlockDirty(
            BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
        obsidian$enterOrigin(PartialRemeshSectionDirtyOriginDiagnostics.ORIGIN_EXACT_BLOCK);
    }

    @Inject(
            method = "setBlockDirty(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)V",
            at = @At("RETURN"))
    private void obsidian$exitExactBlockDirty(
            BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
        obsidian$exitOrigin();
    }

    @Inject(method = "setBlocksDirty(IIIIII)V", at = @At("HEAD"))
    private void obsidian$enterBlockRange(
            int minX, int minY, int minZ, int maxX, int maxY, int maxZ, CallbackInfo ci) {
        obsidian$enterOrigin(PartialRemeshSectionDirtyOriginDiagnostics.ORIGIN_BLOCK_RANGE);
    }

    @Inject(method = "setBlocksDirty(IIIIII)V", at = @At("RETURN"))
    private void obsidian$exitBlockRange(
            int minX, int minY, int minZ, int maxX, int maxY, int maxZ, CallbackInfo ci) {
        obsidian$exitOrigin();
    }

    @Inject(method = "setSectionDirtyWithNeighbors(III)V", at = @At("HEAD"))
    private void obsidian$enterNeighborRange(int sectionX, int sectionY, int sectionZ, CallbackInfo ci) {
        obsidian$enterOrigin(PartialRemeshSectionDirtyOriginDiagnostics.ORIGIN_NEIGHBOR_RANGE);
    }

    @Inject(method = "setSectionDirtyWithNeighbors(III)V", at = @At("RETURN"))
    private void obsidian$exitNeighborRange(int sectionX, int sectionY, int sectionZ, CallbackInfo ci) {
        obsidian$exitOrigin();
    }

    @Inject(method = "setSectionRangeDirty(IIIIII)V", at = @At("HEAD"))
    private void obsidian$enterSectionRange(
            int minX, int minY, int minZ, int maxX, int maxY, int maxZ, CallbackInfo ci) {
        obsidian$enterOrigin(PartialRemeshSectionDirtyOriginDiagnostics.ORIGIN_SECTION_RANGE);
    }

    @Inject(method = "setSectionRangeDirty(IIIIII)V", at = @At("RETURN"))
    private void obsidian$exitSectionRange(
            int minX, int minY, int minZ, int maxX, int maxY, int maxZ, CallbackInfo ci) {
        obsidian$exitOrigin();
    }

    @Inject(method = "setSectionDirty(III)V", at = @At("HEAD"))
    private void obsidian$enterSingleSection(int sectionX, int sectionY, int sectionZ, CallbackInfo ci) {
        obsidian$enterOrigin(PartialRemeshSectionDirtyOriginDiagnostics.ORIGIN_SINGLE_SECTION);
    }

    @Inject(method = "setSectionDirty(III)V", at = @At("RETURN"))
    private void obsidian$exitSingleSection(int sectionX, int sectionY, int sectionZ, CallbackInfo ci) {
        obsidian$exitOrigin();
    }

    @Inject(method = "setSectionDirty(IIIZ)V", at = @At("TAIL"))
    private void obsidian$sectionDirty(
            int sectionX,
            int sectionY,
            int sectionZ,
            boolean dirtyFromPlayer,
            CallbackInfo ci) {
        long before = SectionLifecycleEvents.latestSequence();
        SectionLifecycleEvents.sectionDirty(sectionX, sectionY, sectionZ, dirtyFromPlayer);
        long after = SectionLifecycleEvents.latestSequence();
        if (after == before + 1L) {
            int origin = obsidian$currentOrigin == PartialRemeshSectionDirtyOriginDiagnostics.ORIGIN_NONE
                    ? PartialRemeshSectionDirtyOriginDiagnostics.ORIGIN_UNCLASSIFIED
                    : obsidian$currentOrigin;
            PartialRemeshSectionDirtyOriginDiagnostics.observeRelevantSectionDirty(
                    origin, sectionX, sectionY, sectionZ, dirtyFromPlayer);
            if (origin == PartialRemeshSectionDirtyOriginDiagnostics.ORIGIN_SINGLE_SECTION) {
                PartialRemeshSingleSectionCallerDiagnostics.observeRelevantSingleSection(
                        sectionX, sectionY, sectionZ);
                PartialRemeshLightUpdatePreservation.observeRelevantSingleSection(
                        sectionX, sectionY, sectionZ);
            }
        }
    }

    @Inject(method = "setLevel", at = @At("TAIL"))
    private void obsidian$levelChanged(ClientLevel level, CallbackInfo ci) {
        SectionLifecycleEvents.worldChanged();
    }

    @Unique
    private void obsidian$enterOrigin(int requestedOrigin) {
        if (obsidian$overflowDepth > 0) {
            obsidian$overflowDepth++;
            obsidian$currentOrigin = PartialRemeshSectionDirtyOriginDiagnostics.ORIGIN_UNCLASSIFIED;
            return;
        }
        if (obsidian$originDepth >= obsidian$originStack.length) {
            obsidian$originBeforeOverflow = obsidian$currentOrigin;
            obsidian$overflowDepth = 1;
            obsidian$currentOrigin = PartialRemeshSectionDirtyOriginDiagnostics.ORIGIN_UNCLASSIFIED;
            return;
        }
        obsidian$originStack[obsidian$originDepth++] = obsidian$currentOrigin;
        obsidian$currentOrigin = PartialRemeshSectionDirtyOriginDiagnostics.inheritOrigin(
                obsidian$currentOrigin, requestedOrigin);
    }

    @Unique
    private void obsidian$exitOrigin() {
        if (obsidian$overflowDepth > 0) {
            obsidian$overflowDepth--;
            if (obsidian$overflowDepth == 0) {
                obsidian$currentOrigin = obsidian$originBeforeOverflow;
                obsidian$originBeforeOverflow = PartialRemeshSectionDirtyOriginDiagnostics.ORIGIN_NONE;
            }
            return;
        }
        if (obsidian$originDepth <= 0) {
            obsidian$currentOrigin = PartialRemeshSectionDirtyOriginDiagnostics.ORIGIN_UNCLASSIFIED;
            return;
        }
        obsidian$currentOrigin = obsidian$originStack[--obsidian$originDepth];
    }
}
