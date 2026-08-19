package dev.obsidian.render;

import com.mojang.blaze3d.systems.GpuDevice;

/**
 * Stable seam between Minecraft's render lifecycle and Obsidian's renderer.
 * Phase 0 observes the active Mojang Vulkan device. Later phases move terrain
 * and world submission behind this boundary without leaking renderer details
 * into gameplay-facing code.
 */
public interface RendererBridge extends AutoCloseable {
    GpuCapabilities capabilities();

    GpuDevice nativeDeviceHandle();

    @Override
    default void close() {
        // Phase 0 owns no Vulkan resources yet.
    }
}
