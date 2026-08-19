# Obsidian Current State

Last updated: 2026-08-20

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Current released milestone: `v0.0.1-phase0`
- Phase 0 release commit/tag state was verified identical at publication time.

## Current phase

**Phase 0: bootstrap - implemented and CI-validated.**

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
- GitHub Actions build and release workflow.
- Phase 0 release notes and changelog.

## What is validated

### Hosted compile/build validation

GitHub Actions completed a clean full build successfully with the real project dependencies after the Minecraft 26.2 GPU API fix.

Observed successful tasks included:

- `compileClientJava`
- resource processing
- JAR creation
- sources JAR creation
- assemble/build

The successful diagnostic build used Java 25, Gradle 9.5.1, and Fabric Loom 1.17.19 as resolved at that time.

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

A real Windows 11 Minecraft 26.2 runtime launch on the reference RX 6800 XT system has not yet been recorded in this continuity directory.

Before calling Phase 0 runtime-validated, test the GitHub release JAR on the real machine and record:

- whether Fabric loads the mod;
- whether the Minecraft Vulkan backend is detected correctly;
- GPU/vendor/driver values logged by Obsidian;
- whether conflict detection behaves as intended;
- whether the client reaches the title screen/world successfully;
- any crash report or log anomalies.

## Current architecture boundary

Phase 0 attaches to Minecraft's initialized Blaze3D GPU device and records capability metadata. It intentionally does **not** create a competing Vulkan device or replace terrain drawing yet.

This boundary should be preserved unless Phase 1 research demonstrates a concrete reason to change it.

## Next concrete milestone

**Phase 1: low-level Vulkan/render infrastructure.**

Planned areas:

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
- Real runtime behavior on Windows 11/RDNA2 still needs validation.
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

Before beginning Phase 1, test `v0.0.1-phase0` on the real Windows 11 Minecraft 26.2 environment if that test has not yet been logged. Record the result in `ai/ATTEMPT_LOG.md` and update this file accordingly.
