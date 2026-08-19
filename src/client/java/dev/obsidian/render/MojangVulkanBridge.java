package dev.obsidian.render;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;

import java.util.List;

public final class MojangVulkanBridge implements RendererBridge {
    private final GpuDevice device;
    private final GpuCapabilities capabilities;

    private MojangVulkanBridge(GpuDevice device, GpuCapabilities capabilities) {
        this.device = device;
        this.capabilities = capabilities;
    }

    public static MojangVulkanBridge attach() {
        GpuDevice device = RenderSystem.getDevice();
        if (device == null) {
            throw new IllegalStateException(
                    "Obsidian could not attach to Minecraft 26.2's GpuDevice because it was not initialized.");
        }

        GpuCapabilities caps = new GpuCapabilities(
                device.getBackendName(),
                device.getVendor(),
                device.getRenderer(),
                device.getVersion(),
                device.getImplementationInformation(),
                List.copyOf(device.getEnabledExtensions()),
                device.isDebuggingEnabled(),
                device.getMaxTextureSize(),
                device.getUniformOffsetAlignment());

        return new MojangVulkanBridge(device, caps);
    }

    @Override
    public GpuCapabilities capabilities() {
        return capabilities;
    }

    @Override
    public GpuDevice nativeDeviceHandle() {
        return device;
    }
}
