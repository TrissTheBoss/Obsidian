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

        LOG.log(System.Logger.Level.INFO, "Obsidian 0.0.1-phase0 starting (Minecraft 26.2, Vulkan-only)");
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

        bridge = MojangVulkanBridge.attach();
        GpuCapabilities caps = bridge.capabilities();

        if (!caps.isVulkan()) {
            String message = "Obsidian is Vulkan-only, but Minecraft initialized backend '"
                    + caps.backend()
                    + "'. In Video Settings, set Graphics API to Vulkan and restart Minecraft.";
            if (config.failOnNonVulkan()) {
                throw new IllegalStateException(message);
            }
            LOG.log(System.Logger.Level.WARNING, message);
            return;
        }

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

    public static RendererBridge bridge() {
        RendererBridge value = bridge;
        if (value == null) {
            throw new IllegalStateException("Obsidian renderer bridge requested before device bootstrap completed.");
        }
        return value;
    }
}
