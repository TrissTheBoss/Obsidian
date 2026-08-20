# A-0051 - Phase 1 dev9 final package verification

- **Date:** 2026-08-20
- **Status:** SUCCESS / RUNTIME PENDING
- **Branch:** `phase1/visibility-compaction`
- **Draft PR:** #11
- **Version:** `0.1.0-phase1-dev9`
- **Verified code/docs head:** `68e4a331074577de8f7b52006b0362b13a6df25d`
- **GitHub Actions run:** `32415959954`
- **Artifact ID:** `9423907764`

## CI result

GitHub Actions on the exact documented dev9 head passed:

- Java 25 / Gradle 9.5.1 build: SUCCESS;
- artifact upload: SUCCESS;
- versioned release job: SKIPPED as intended for a development milestone.

## Artifact inspection

The downloaded artifact contains:

- `Obsidian-0.1.0-phase1-dev9.jar`;
- `Obsidian-0.1.0-phase1-dev9-sources.jar`.

`fabric.mod.json` inside the main JAR reports:

- id: `obsidian`;
- version: `0.1.0-phase1-dev9`.

Required new classes are present:

- `dev/obsidian/render/draw/VisibilityCompactionProbe.class`;
- `dev/obsidian/render/frame/FrameCoordinator.class`;
- `dev/obsidian/render/vulkan/VulkanInteropBuffer.class`;
- `dev/obsidian/render/vulkan/VulkanVisibilityCompactor.class`.

Completed/superseded dev8 classes are absent:

- `ComputeIndirectDrawProbe`;
- `VulkanComputeIndirectGenerator`;
- `VulkanStorageIndirectBuffer`.

## SHA-256

Main JAR:

`c133229502f4969aa1f894aa09c404d20c06d833ceb11ba26312cf3e6f6ce6de`

Sources JAR:

`c44438ba09dfa4161fa6cbe1c0f74df4b7e25bf6927bfa78fa41ded921c0fc55`

## Distribution rule

This is the canonical dev9 runtime-test binary unless a later source/code change occurs. This record itself is documentation-only; run one final CI gate on the resulting repository head before handoff so repository continuity is exact. If the final docs-only artifact differs, verify the newer artifact instead of assuming byte identity.

## Next action

Keep PR #11 draft/unmerged. Runtime-test the exact dev9 binary on Windows 11 / RX 6800 XT and verify GPU visibility count/compaction, zero-tail public fixed-count indirect rendering, pixel oracle, command-buffer oracle, staging/arena accounting, world entry and clean shutdown.