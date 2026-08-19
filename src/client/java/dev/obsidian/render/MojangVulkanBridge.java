package dev.obsidian.render;

import com.mojang.blaze3d.systems.DeviceFeatures;
import com.mojang.blaze3d.systems.DeviceInfo;
import com.mojang.blaze3d.systems.DeviceLimits;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;

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

        DeviceInfo info = device.getDeviceInfo();
        DeviceLimits limits = info.limits();
        DeviceFeatures features = info.features();

        GpuCapabilities caps = new GpuCapabilities(
                info.backendName(),
                RenderSystem.getBackendDescription(),
                info.vendorName(),
                info.name(),
                info.driverInfo(),
                info.type().name(),
                info.underlyingExtensions(),
                device.isDebuggingEnabled(),
                limits.maxTextureSize(),
                limits.minUniformOffsetAlignment(),
                features.shaderDrawParameters(),
                features.multiDrawIndirect(),
                features.drawIndirect(),
                features.persistentMapping());

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
