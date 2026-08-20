package dev.obsidian.mixin;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.GpuDeviceBackend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Narrow backend seam used only when public Blaze3D lacks a required Vulkan capability. */
@Mixin(GpuDevice.class)
public interface GpuDeviceAccessor {
    @Accessor("backend")
    GpuDeviceBackend obsidian$getBackend();
}
