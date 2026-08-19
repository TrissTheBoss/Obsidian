# Changelog

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
