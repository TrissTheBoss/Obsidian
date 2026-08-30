package dev.obsidian.bootstrap;

import dev.obsidian.compat.ConflictDetector;
import dev.obsidian.config.ObsidianConfig;
import dev.obsidian.render.GpuCapabilities;
import dev.obsidian.render.MojangVulkanBridge;
import dev.obsidian.render.RendererBridge;
import dev.obsidian.render.frame.FrameCoordinator;
import com.mojang.blaze3d.systems.RenderPass;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
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
                "Obsidian Phase 3 dev24 P3.10 production opaque/cutout replacement canary armed. Exact Minecraft 26.2 bytecode authorizes per-section SOLID/CUTOUT suppression at prepareChunkRenders and same-OPAQUE-RenderPass replacement before later world depth consumers. Only LIVE generation/resource-current P3.7-exact full-section records may suppress vanilla; every unavailable or ambiguous unit stays vanilla. Dev24 removes the old comparison-only 1/512 face offset and 75% RGB tint, uses exact frozen positions/color/light/material/UV truth, disables post-world comparison copies, retains bounded workers/staging/arena and completion-gated lifetime, expands no native Vulkan graphics ownership, and does not implement partial remeshing or partial GPU patching; workerWorldReadsAfterCapture=0.");
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

    public static void beginProductionTerrainPreparation() {
        FrameCoordinator coordinator = frameCoordinator;
        if (coordinator != null) coordinator.beginProductionTerrainPreparation();
    }

    public static boolean tryClaimProductionTerrainReplacement(
            int sectionX, int sectionY, int sectionZ, ChunkSectionLayer layer) {
        FrameCoordinator coordinator = frameCoordinator;
        return coordinator != null
                && coordinator.tryClaimProductionTerrainReplacement(sectionX, sectionY, sectionZ, layer);
    }

    public static void encodeProductionTerrainReplacements(RenderPass pass, GameRenderer renderer) {
        FrameCoordinator coordinator = frameCoordinator;
        if (coordinator != null) coordinator.encodeProductionTerrainReplacements(pass, renderer);
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
