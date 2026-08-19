package dev.obsidian.render;

import java.util.List;

public record GpuCapabilities(
        String backend,
        String vendor,
        String renderer,
        String version,
        String implementation,
        List<String> enabledExtensions,
        boolean debuggingEnabled,
        int maxTextureSize,
        int uniformOffsetAlignment) {

    public boolean isVulkan() {
        return backend != null && backend.toLowerCase(java.util.Locale.ROOT).contains("vulkan");
    }

    public boolean hasExtension(String name) {
        return enabledExtensions.stream().anyMatch(name::equalsIgnoreCase);
    }
}
