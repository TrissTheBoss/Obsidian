# A-0046 - Phase 1 dev8 final CI/package verification

**Date:** 2026-08-20  
**Status:** SUCCESS - hosted compile/package; runtime pending  
**Version:** `0.1.0-phase1-dev8`

## Objective

Verify the exact cleaned/documented dev8 CI artifact before distributing it for the real Windows 11 / RX 6800 XT Vulkan compute-indirect test.

## Action

Ran canonical GitHub Actions build on exact branch head `994f33c8722218e650258a51158d5b6eaa4dcb24` after implementation, cleanup, A-0044/A-0045, D-0025/D-0026 and CURRENT_STATE synchronization.

GitHub Actions run: `32409253739`.
Artifact ID: `9421495267`.

Java 25 / Gradle 9.5.1 build and artifact upload both completed successfully. Downloaded and inspected the packaged JARs.

## Result

SUCCESS.

Artifact contents:

- `Obsidian-0.1.0-phase1-dev8.jar`;
- `Obsidian-0.1.0-phase1-dev8-sources.jar`.

Main JAR verification:

- `fabric.mod.json` reports id `obsidian` and version `0.1.0-phase1-dev8`;
- contains `ComputeIndirectDrawProbe`;
- contains `VulkanComputeIndirectGenerator`;
- contains `VulkanStorageIndirectBuffer`;
- contains `GpuDeviceAccessor` and `CommandEncoderAccessor`;
- contains reusable `FrameGraphCommandStream`;
- completed dev7 `ArenaIndirectDrawProbe` and CPU-authored `IndexedIndirectCommandBuffer` are absent;
- main JAR SHA-256: `febadbeef3fa3e45e64709c63b79c9f57f8d26468865b155289261b6ede4a2fa`;
- sources JAR SHA-256: `690128ac9e50206838547317a2bb01f12334ef2c1e4e101a6c89b4bb0d14a13a`.

## Intended effect

Ensure the user receives the exact CI-built dev8 binary containing the narrow native compute seam and no stale dev7 validation implementation.

## Actual effect

The package matches the intended architecture and version. Hosted compile/package validation is complete; real Vulkan runtime validation remains the only merge gate.

## Next action

Run one final CI check after this continuity-only commit. If green, distribute the exact resulting dev8 JAR and keep PR #10 draft/unmerged until the RX 6800 XT runtime test proves compute shader/pipeline creation, dispatch, explicit compute-to-indirect synchronization, two generated indirect commands, deterministic pixels, exact staging/arena accounting, world entry and clean shutdown.