# Obsidian Current State

Last updated: 2026-08-20

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Current released milestone: `v0.0.2-phase0`
- Release source: merged PR #1, `Fix Phase 0 OpenGL startup dead-end`.

## Current phase

**Phase 0: bootstrap - implemented, CI-validated, and partially runtime-validated.**

Phase 0 does not yet replace terrain rendering and is not expected to improve FPS. Its purpose is to establish the project/toolchain/backend boundary that later phases build on.

Implemented Phase 0 responsibilities:

- Fabric client bootstrap.
- Renderer-conflict detection.
- Vulkan-only backend validation.
- Minecraft 26.2 `RenderSystem` / `GpuDevice` attachment seam.
- GPU capability capture through Minecraft 26.2 `DeviceInfo`, `DeviceLimits`, and `DeviceFeatures`.
- `RendererBridge` abstraction for future ownership of rendering work.
- Configuration scaffolding.
- VS Code task/extension setup.
- GitHub Actions build and version-aware release workflow.
- Phase 0 release notes and changelog.

## What is validated

### Hosted compile/build validation

GitHub Actions completed a clean pull-request build for the `0.0.2-phase0` patch against the real Minecraft 26.2 project dependencies. Workflow run `32314279287` completed with conclusion `success` before merge.

The patch was then squash-merged to `main` as commit `5ffac551e921eb7c90eacf2236071f92027aaef5`, and the version-aware release workflow created tag/release `v0.0.2-phase0`.

### Real Windows runtime test: v0.0.1-phase0

A real launch was recorded on the reference machine using Prism Launcher 10.0.5, Minecraft 26.2, Fabric Loader 0.19.3, Fabric API 0.158.0+26.2, and Java 25.0.1.

Confirmed working before the failure:

- Fabric loaded Obsidian `0.0.1-phase0`.
- Obsidian `earlyInitialize()` executed.
- Minecraft created its GPU backend/device far enough for Obsidian's backend validation hook to run.
- The AMD Radeon RX 6800 XT was correctly identified by Minecraft.
- Windows had the Vulkan loader (`vulkan-1.dll`) present.

Observed failure:

- Minecraft 26.2 initialized **OpenGL**, which is currently its default graphics backend.
- Obsidian 0.0.1 intentionally threw `IllegalStateException` when it detected OpenGL.
- This happened during game initialization before the player could reach Video Settings.

Patch outcome in 0.0.2:

- Non-Vulkan startup is now nonfatal.
- Obsidian remains inactive for that session and logs instructions to select **Prefer Vulkan (Experimental)**.
- The public renderer bridge remains unavailable unless Vulkan is actually active.
- The obsolete `failOnNonVulkan` property is ignored/removed from the active config model, so an old 0.0.1 config cannot preserve fatal startup behavior.

### API truth established

For Minecraft 26.2, relevant device information is exposed through:

- `GpuDevice.getDeviceInfo()`
- `DeviceInfo.backendName()`
- `DeviceInfo.vendorName()`
- `DeviceInfo.name()`
- `DeviceInfo.driverInfo()`
- `DeviceInfo.underlyingExtensions()`
- `DeviceInfo.features()`
- `DeviceInfo.limits()`
- `DeviceLimits.maxTextureSize()`
- `DeviceLimits.minUniformOffsetAlignment()`

`GpuDevice` itself does not expose the older direct getters that were initially assumed.

## What is not yet validated

The Vulkan-active runtime path is still unvalidated on the real reference machine.

Before calling Phase 0 runtime-validated, test `v0.0.2-phase0` with Minecraft configured to **Video Settings > Graphics API > Prefer Vulkan (Experimental)** and record:

- whether the log reports `Graphics Backend: Vulkan`;
- whether Obsidian logs `Attached to Vulkan backend`;
- GPU/vendor/driver/device type values logged by Obsidian;
- Vulkan extension/feature/limit values logged by Obsidian;
- whether the client reaches the title screen and a world successfully;
- any crash report or rendering anomalies.

## Current architecture boundary

Phase 0 attaches to Minecraft's initialized Blaze3D GPU device and records capability metadata. It intentionally does **not** create a competing Vulkan device or replace terrain drawing yet.

The renderer bridge must only be considered ready when the active backend is Vulkan. OpenGL may be allowed temporarily only so the player can reach settings and restart with Vulkan.

This boundary should be preserved unless Phase 1 research demonstrates a concrete reason to change it.

## Next concrete milestone

**Complete Phase 0 Vulkan runtime validation, then begin Phase 1: low-level Vulkan/render infrastructure.**

Planned Phase 1 areas:

1. Determine the safe ownership/interception boundaries around Minecraft 26.2's Vulkan backend.
2. Frame contexts and resource lifetime tracking.
3. Synchronization strategy (fences/timeline semantics available through the actual backend).
4. Device-local arena allocation strategy.
5. Staging/upload ring strategy.
6. Deferred destruction/reclamation.
7. Shader/pipeline management.
8. Render graph/pass dependency model.
9. GPU timestamp profiling.
10. Validation/debug instrumentation.

The first Phase 1 success criterion should be Obsidian submitting/controlling a small piece of GPU work inside Minecraft's real frame lifecycle without breaking vanilla rendering.

## Major risks / open questions

- Mojang's 26.2 Vulkan backend is experimental; ownership and synchronization assumptions must be verified against actual code/API behavior.
- The public `GpuDevice` abstraction may not expose all low-level Vulkan handles needed for the final renderer. Do not assume it does or does not; inspect exact 26.2 implementation before designing around this.
- Vulkan-active behavior on Windows 11/RDNA2 still needs validation.
- Later direct renderer replacement may require mixins/accessors into implementation details that can move between Minecraft versions.
- CI proves compile/package compatibility, not rendering correctness or performance.

## Reference hardware and priorities

Primary user/reference system:

- Windows 11
- AMD Radeon RX 6800 XT (16 GB VRAM)
- AMD Ryzen 5 5600X
- 16 GB DDR4-2666

Performance priorities:

1. 1% / 0.1% lows
2. smooth chunk loading
3. high render distances
4. average FPS

## Immediate handoff instruction

Test `v0.0.2-phase0` on the real machine with **Prefer Vulkan (Experimental)** selected. Record the result in `ai/ATTEMPT_LOG.md`. Do not begin deep Phase 1 renderer ownership work until the Vulkan-active Phase 0 path has reached the title screen/world successfully.
