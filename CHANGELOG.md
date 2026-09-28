# Changelog

Notable user-facing Obsidian checkpoints are tracked here. Detailed engineering evidence lives in [`ai/attempts/`](ai/attempts/) and exact current truth lives in [`ai/CURRENT_STATE.md`](ai/CURRENT_STATE.md).

## 0.4.0-phase4-dev1 — Preview — 2026-09-28

### Added

- Phase 4 P4.1 shadow-only persistent large-scene section metadata.
- Scalable GPU frustum classification and visible-identity compaction beside the validated Phase 3 renderer.
- Budgeted independent CPU/GPU visibility validation and lifecycle accounting.
- Public **Preview prerelease** channel for CI-green tester builds.

### Important

- This is a **test build, not a production-ready renderer release**.
- P4.1 does not take production draw ownership; the validated Phase 3 P3.10 path remains the visual authority.
- Reference-machine runtime and human visual validation are still required before P4.1 promotion.

## Phase 3 production terrain checkpoint — 2026-09-02

### Completed

- P3.10 same-OPAQUE-pass opaque/cutout terrain replacement validated and merged.
- Conservative vanilla fallback retained for unsupported/incomplete sections including the previously demonstrated leaves/kelp cases.
- Differential correctness, vertical recentering, F3+T recovery and resource/lifetime gates closed on the reference runtime.

### Deferred

- P3.9 fixed four-Y-slice partial remeshing was rejected/deferred after missing its frozen upload-tail threshold; it is not the production baseline.

---
All notable Obsidian development milestones are tracked here.

## 0.0.2-phase0 - 2026-08-20

### Fixed

- OpenGL is no longer treated as a fatal Phase 0 startup error. Obsidian now stays inactive for that session, logs the backend mismatch, and lets the player reach Video Settings to select Vulkan.
- Removed the obsolete `failOnNonVulkan` configuration switch so an old Phase 0 config cannot preserve the crash behavior.
- The renderer bridge is only published as ready after the active Minecraft backend is confirmed to be Vulkan.

### Changed

- GitHub release automation is now version-aware and reads `mod_version` instead of hardcoding `0.0.1-phase0` artifact/tag names.
- Phase 0 startup logging now distinguishes a Vulkan-only renderer requirement from the ability to launch Minecraft temporarily on OpenGL for configuration.

## 0.0.1-phase0 - 2026-08-20

### Added

- Fabric 26.2 client bootstrap.
- Vulkan-only backend validation.
- Minecraft `GpuDevice` capability capture.
- Stable `RendererBridge` seam for later renderer ownership.
- renderer/optimization mod conflict detection.
- persistent Phase 0 configuration file.
- VS Code tasks and recommended extensions.
- clean Gradle 9.5.1 / Java 25 project configuration.
- GitHub Actions build, checksum, artifact, and Phase 0 release automation.

### Not yet implemented

- custom terrain rendering.
- Vulkan resource ownership beyond observing Minecraft's active device.
- GPU-driven culling or indirect draw generation.
- entity, particle, block-entity, or GUI replacement paths.
