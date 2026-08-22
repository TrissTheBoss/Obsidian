# A-0129 — Phase 3 P3.4 dev9 pure repeat-aware UV descriptor CI

Date: 2026-08-23
Result: **SUCCESS — PURE CORE/API COMPILE CHECKPOINT**
Milestone: P3.4 dev9

## Scope

A-0128 froze the repeat-aware UV descriptor/representability contract. This attempt records the isolated pure sidecar implementation before production worker/coordinator integration.

Implemented class:

- `src/client/java/dev/obsidian/render/terrain/RepeatAwareUvDescriptors.java`

Version target:

- `0.3.0-phase3-dev9`

## Exactness review before CI

The first implementation used `Integer.MIN_VALUE` as a sentinel while scanning raw float UV bits. That conflicts with the valid raw float bit pattern `0x80000000` (`-0.0f`) and therefore violated the frozen raw-bit exactness contract even though ordinary atlas coordinates are unlikely to use it.

The sentinel was removed before this checkpoint. The implementation now finds the second distinct raw value with explicit branches, so all 32-bit float patterns remain representable as data.

## Implemented pure contract

- only multi-face dev7 candidates are considered for repeat-aware descriptors;
- representative source quad is independently reconstructed into canonical geometric corners;
- exact two-U × two-V rectangle proof uses raw float bits;
- all four U/V combinations must occur exactly once;
- affine square orientation proof admits only the eight flip/rotation symmetries;
- retained descriptor preserves candidate index, four raw atlas-bound bit patterns and exact orientation signature;
- logical retained size is exactly 19 bytes/descriptor;
- source fingerprints, exact recomputation, strict ascending candidate order, partition accounting, safe-face accounting and retained bytes self-validate;
- `repeatAwareFourVertexSafe` combines descriptor representability with dev8 color/light interpolation safety;
- `contentEquals` supports determinism auditing;
- no GPU geometry or shader path is changed.

## CI

Exact PR head: `c88aebcb147d46df820963709b2534bfa217510c`
Workflow: `32604351532`

- Java 25 / Gradle 9.5.1: SUCCESS
- Build: SUCCESS
- Upload build artifacts: SUCCESS
- Publish versioned release: SKIPPED

## Boundary

This proves source/API compilation for the frozen pure descriptor only. It is not production worker integration or runtime evidence.

Next: integrate `RepeatAwareUvDescriptors` after `OrdinaryQuadEmissionSafety` in `SectionMeshWorkerPool`, add exact primary/determinism metrics, then add `repeatAwareUvEvidenceReady` in `FrameCoordinator` while keeping `greedyRectangleGpuEmission=false`, `renderCorrectMergeKeyComplete=false`, and `BakedSectionMesh` authoritative.
