package dev.obsidian.mixin;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.CommandEncoderBackend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Narrow backend seam used only when public Blaze3D lacks a required Vulkan capability. */
@Mixin(CommandEncoder.class)
public interface CommandEncoderAccessor {
    @Accessor("backend")
    CommandEncoderBackend obsidian$getBackend();
}
