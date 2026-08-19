package dev.obsidian.render;

import java.util.Locale;
import java.util.Set;

public record GpuCapabilities(
        String backend,
        String backendDescription,
        String vendor,
        String deviceName,
        String driverInfo,
        String deviceType,
        Set<String> underlyingExtensions,
        boolean debuggingEnabled,
        int maxTextureSize,
        int uniformOffsetAlignment,
        boolean shaderDrawParameters,
        boolean multiDrawIndirect,
        boolean drawIndirect,
        boolean persistentMapping) {

    public GpuCapabilities {
        underlyingExtensions = Set.copyOf(underlyingExtensions);
    }

    public boolean isVulkan() {
        return containsVulkan(backend) || containsVulkan(backendDescription);
    }

    public boolean hasExtension(String name) {
        return underlyingExtensions.stream().anyMatch(name::equalsIgnoreCase);
    }

    private static boolean containsVulkan(String value) {
        return value != null && value.toLowerCase(Locale.ROOT).contains("vulkan");
    }
}
