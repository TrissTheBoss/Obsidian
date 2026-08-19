package dev.obsidian.bootstrap;

import dev.obsidian.compat.ConflictDetector;
import dev.obsidian.config.ObsidianConfig;
import dev.obsidian.render.GpuCapabilities;
import dev.obsidian.render.MojangVulkanBridge;
import dev.obsidian.render.RendererBridge;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ObsidianBootstrap {
    private static final System.Logger LOG = System.getLogger("Obsidian");
    private static final AtomicBoolean EARLY_INIT = new AtomicBoolean();
    private static final AtomicBoolean DEVICE_INIT = new AtomicBoolean();

    private static volatile ObsidianConfig config;
    private static volatile RendererBridge bridge;

    private ObsidianBootstrap() {}

    public static void earlyInitialize() {
        if (!EARLY_INIT.compareAndSet(false, true)) {
            return;
        }

        LOG.log(System.Logger.Level.INFO, "Obsidian 0.0.2-phase0 starting (Minecraft 26.2, Vulkan-only renderer)");
        config = ObsidianConfig.load();

        List<String> conflicts = ConflictDetector.findLoadedConflicts();
        if (!conflicts.isEmpty()) {
            String message = "Obsidian cannot start with renderer/optimization conflicts loaded: "
                    + String.join(", ", conflicts)
                    + ". Remove them before using Obsidian.";
            if (config.strictConflictCheck()) {
                throw new IllegalStateException(message);
            }
            LOG.log(System.Logger.Level.WARNING, message);
        }

        LOG.log(System.Logger.Level.INFO,
                "Phase 0 early bootstrap complete; waiting for Minecraft GpuDevice initialization.");
    }

    public static void onMinecraftReady() {
        if (!DEVICE_INIT.compareAndSet(false, true)) {
            return;
        }
        if (!EARLY_INIT.get()) {
            earlyInitialize();
        }

        RendererBridge candidate = MojangVulkanBridge.attach();
        GpuCapabilities caps = candidate.capabilities();

        if (!caps.isVulkan()) {
            bridge = null;
            LOG.log(System.Logger.Level.WARNING,
                    "Obsidian is Vulkan-only, but Minecraft initialized backend ''{0}''. Obsidian will remain inactive for this session instead of crashing.",
                    caps.backend());
            LOG.log(System.Logger.Level.WARNING,
                    "Open Video Settings, set Graphics API to 'Prefer Vulkan (Experimental)', then restart Minecraft to activate Obsidian.");
            LOG.log(System.Logger.Level.INFO,
                    "Detected GPU while inactive: {0} | {1} ({2}); driver: {3}",
                    caps.vendor(), caps.deviceName(), caps.deviceType(), caps.driverInfo());
            return;
        }

        bridge = candidate;

        LOG.log(System.Logger.Level.INFO, "Attached to Vulkan backend: {0}", caps.backend());
        LOG.log(System.Logger.Level.INFO, "GPU: {0} | {1} ({2})",
                caps.vendor(), caps.deviceName(), caps.deviceType());
        LOG.log(System.Logger.Level.INFO, "Driver: {0}", caps.driverInfo());

        if (config.verboseCapabilityLog()) {
            LOG.log(System.Logger.Level.INFO,
                    "Vulkan capabilities: extensions={0}, maxTextureSize={1}, uniformAlignment={2}, debug={3}, indirect={4}, multiDrawIndirect={5}, persistentMapping={6}",
                    caps.underlyingExtensions().size(), caps.maxTextureSize(), caps.uniformOffsetAlignment(),
                    caps.debuggingEnabled(), caps.drawIndirect(), caps.multiDrawIndirect(), caps.persistentMapping());
            LOG.log(System.Logger.Level.DEBUG, "Backend description: {0}", caps.backendDescription());
        }

        LOG.log(System.Logger.Level.INFO,
                "Obsidian Phase 0 ready. RendererBridge established; terrain replacement is intentionally not active yet.");
    }

    public static boolean isRendererBridgeReady() {
        return bridge != null;
    }

    public static RendererBridge bridge() {
        RendererBridge value = bridge;
        if (value == null) {
            throw new IllegalStateException(
                    "Obsidian renderer bridge is unavailable. The Vulkan backend must be active and device bootstrap must be complete.");
        }
        return value;
    }
}
