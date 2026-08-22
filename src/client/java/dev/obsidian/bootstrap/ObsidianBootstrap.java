package dev.obsidian.bootstrap;

import dev.obsidian.compat.ConflictDetector;
import dev.obsidian.config.ObsidianConfig;
import dev.obsidian.render.GpuCapabilities;
import dev.obsidian.render.MojangVulkanBridge;
import dev.obsidian.render.RendererBridge;
import dev.obsidian.render.frame.FrameCoordinator;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.GameRenderer;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ObsidianBootstrap {
    private static final System.Logger LOG = System.getLogger("Obsidian");
    private static final AtomicBoolean EARLY_INIT = new AtomicBoolean();
    private static final AtomicBoolean DEVICE_INIT = new AtomicBoolean();

    private static volatile ObsidianConfig config;
    private static volatile RendererBridge bridge;
    private static volatile FrameCoordinator frameCoordinator;

    private ObsidianBootstrap() {}

    public static void earlyInitialize() {
        if (!EARLY_INIT.compareAndSet(false, true)) return;
        LOG.log(System.Logger.Level.INFO,
                "Obsidian {0} starting (Minecraft 26.2, Vulkan-only renderer)", version());
        config = ObsidianConfig.load();
        List<String> conflicts = ConflictDetector.findLoadedConflicts();
        if (!conflicts.isEmpty()) {
            String message = "Obsidian cannot start with renderer/optimization conflicts loaded: "
                    + String.join(", ", conflicts)
                    + ". Remove them before using Obsidian.";
            if (config.strictConflictCheck()) throw new IllegalStateException(message);
            LOG.log(System.Logger.Level.WARNING, message);
        }
        LOG.log(System.Logger.Level.INFO,
                "Early bootstrap complete; waiting for Minecraft GpuDevice initialization.");
    }

    public static void onMinecraftReady() {
        if (!DEVICE_INIT.compareAndSet(false, true)) return;
        if (!EARLY_INIT.get()) earlyInitialize();

        RendererBridge candidate = MojangVulkanBridge.attach();
        GpuCapabilities caps = candidate.capabilities();
        if (!caps.isVulkan()) {
            candidate.close();
            bridge = null;
            frameCoordinator = null;
            LOG.log(System.Logger.Level.WARNING,
                    "Obsidian is Vulkan-only, but Minecraft initialized backend ''{0}''. Obsidian will remain inactive for this session instead of crashing.", caps.backend());
            LOG.log(System.Logger.Level.WARNING,
                    "Open Video Settings, set Graphics API to 'Prefer Vulkan (Experimental)', then restart Minecraft to activate Obsidian.");
            LOG.log(System.Logger.Level.INFO,
                    "Detected GPU while inactive: {0} | {1} ({2}); driver: {3}",
                    caps.vendor(), caps.deviceName(), caps.deviceType(), caps.driverInfo());
            return;
        }

        bridge = candidate;
        frameCoordinator = new FrameCoordinator(candidate.nativeDeviceHandle());
        LOG.log(System.Logger.Level.INFO, "Attached to Vulkan backend: {0}", caps.backend());
        LOG.log(System.Logger.Level.INFO, "GPU: {0} | {1} ({2})", caps.vendor(), caps.deviceName(), caps.deviceType());
        LOG.log(System.Logger.Level.INFO, "Driver: {0}", caps.driverInfo());
        if (config.verboseCapabilityLog()) {
            LOG.log(System.Logger.Level.INFO,
                    "Vulkan capabilities: extensions={0}, maxTextureSize={1}, uniformAlignment={2}, debug={3}, indirect={4}, multiDrawIndirect={5}, persistentMapping={6}",
                    caps.underlyingExtensions().size(), caps.maxTextureSize(), caps.uniformOffsetAlignment(),
                    caps.debuggingEnabled(), caps.drawIndirect(), caps.multiDrawIndirect(), caps.persistentMapping());
            LOG.log(System.Logger.Level.DEBUG, "Backend description: {0}", caps.backendDescription());
        }
        LOG.log(System.Logger.Level.INFO,
                "Obsidian Phase 3 dev5 P3.3 greedy rectangle sidecar validation armed. The proven P3.2 six-direction binary visibility masks remain active. Every production worker job now also derives deterministic packed topology rectangles from those immutable masks using fixed worker-local primitive scratch. Every primary rectangle build proves non-overlap and exact expansion back to the source visibility words; periodic audits prove duplicate rectangle determinism and exact expanded set equivalence against the independent ReferenceFaceMesh oracle. Live world/model/material/light capture and all GPU allocation/upload/draw/retirement remain render-thread-only. The generalized SOLID/CUTOUT BakedSectionMesh remains the production drawable output; binaryVisibilitySidecarIntegrated=true, greedyRectangleSidecarIntegrated=true, greedyRectangleGpuEmission=false, renderCorrectMergeKeyComplete=false, workerWorldReadsAfterCapture=0. P3.4 render-correct merge-key integration remains downstream.");
    }

    public static void onFrameStart() {
        FrameCoordinator coordinator = frameCoordinator;
        if (coordinator != null) coordinator.beginFrame();
    }

    public static void onWorldRendered(GameRenderer renderer) {
        FrameCoordinator coordinator = frameCoordinator;
        if (coordinator != null) coordinator.afterWorldRender(renderer);
    }

    public static void onFrameEnd() {
        FrameCoordinator coordinator = frameCoordinator;
        if (coordinator != null) coordinator.endFrame();
    }

    public static void shutdown() {
        FrameCoordinator coordinator = frameCoordinator;
        frameCoordinator = null;
        if (coordinator != null) coordinator.close();
        RendererBridge rendererBridge = bridge;
        bridge = null;
        if (rendererBridge != null) rendererBridge.close();
    }

    public static boolean isRendererBridgeReady() { return bridge != null; }

    public static RendererBridge bridge() {
        RendererBridge value = bridge;
        if (value == null) {
            throw new IllegalStateException("Obsidian renderer bridge is unavailable. The Vulkan backend must be active and device bootstrap must be complete.");
        }
        return value;
    }

    public static FrameCoordinator frameCoordinator() {
        FrameCoordinator value = frameCoordinator;
        if (value == null) {
            throw new IllegalStateException("Obsidian frame coordinator is unavailable until Vulkan bootstrap has completed.");
        }
        return value;
    }

    private static String version() {
        return FabricLoader.getInstance()
                .getModContainer("obsidian")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("development");
    }
}
