# Obsidian

[![Build](https://github.com/TrissTheBoss/Obsidian/actions/workflows/build.yml/badge.svg)](https://github.com/TrissTheBoss/Obsidian/actions/workflows/build.yml)

Obsidian is an experimental, Vulkan-only rendering engine replacement project for Minecraft Java 26.2 on Fabric. Its primary performance goal is consistent frame pacing and strong 1% lows, followed by smooth chunk streaming and scaling to large render distances.

> **Current status:** Phase 0 bootstrap. Obsidian does not replace terrain rendering yet and should not be expected to improve FPS in this milestone.

## Phase 0 scope

Phase 0 establishes the boundary the real renderer will build on:

- Fabric client bootstrap
- strict renderer-mod conflict detection
- Vulkan-only backend validation
- runtime GPU capability capture through Minecraft's `GpuDevice`
- a clean `RendererBridge` seam for later renderer ownership
- configuration scaffolding
- VS Code / Gradle project setup
- reproducible GitHub Actions builds and release artifacts

## Requirements

- Minecraft Java Edition 26.2
- Fabric Loader 0.19.3+
- Java 25
- Gradle 9.5.1 for local development
- Vulkan selected under Minecraft's experimental **Graphics API** setting

## VS Code

1. Install a Java 25 JDK.
2. Install Gradle 9.5.1 and make `gradle` available on `PATH`.
3. Install the extensions recommended by `.vscode/extensions.json`.
4. Open the repository folder in VS Code.
5. Run **Obsidian: build** or **Obsidian: run client** from **Terminal > Run Task**.

Equivalent terminal commands:

```text
gradle build
gradle runClient
```

## Renderer conflicts

Obsidian is intended to replace the renderer/optimization stack rather than layer on top of another renderer. Phase 0 rejects or flags Sodium, VulkanMod, Iris, ImmediatelyFast, EntityCulling, and MoreCulling.

## Roadmap

- **Phase 0:** Vulkan bootstrap, lifecycle seam, capability reporting, conflicts, configuration.
- **Phase 1:** Vulkan infrastructure, frame contexts, synchronization, memory/staging allocators, render graph, profiling.
- **Phase 2:** Initial Obsidian-owned terrain path.
- **Phase 3:** GPU-driven visibility and indirect terrain submission.
- Later phases: translucency, entities, block entities, particles, UI/text, experimental GPU paths.

## Development priorities

1. 1% and 0.1% lows / frame pacing
2. smooth chunk streaming
3. large render distance scaling
4. average FPS
5. low avoidable CPU allocation and synchronization
6. minimal visual differences without deliberately preserving vanilla rendering bugs

## License status

No public redistribution license has been selected yet. Until one is added, normal copyright rules apply to the source code.
