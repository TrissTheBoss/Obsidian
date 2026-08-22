# A-0088 - Phase 2 dev7 refined runtime-gate package

Status: **REFINED IMPLEMENTATION / EXACT CI SUCCESS / PACKAGE + BYTECODE VERIFIED / REFERENCE RUNTIME PENDING**

Date: 2026-08-21
Branch: `phase2/multi-section-scene`
Canonical stacked PR: #27
Temporary exact-CI PR: #28 (never merge)
Version: `0.2.0-phase2-dev7`

## Context

A-0086 proved the initial multi-section implementation and package. A-0087 then refined only the P2.7 shutdown/runtime closure contract so scene recenter is mandatory while chunk load/unload counters remain diagnostic. Chunk lifecycle observation and invalidation behavior were not removed; P2.6 separately retains the mandatory fixed-target chunk lifecycle closure gate.

Because A-0087 changed `FrameCoordinator` bytecode, a fresh exact dependency build and package inspection was required before reference handoff.

## Exact CI

Refined implementation/state head: `4adcfaacd6af7d63c07fa3be90a2997c74f41801`.

GitHub Actions run `32502112980`:

- Java 25 / Gradle 9.5.1: SUCCESS;
- Build: SUCCESS;
- Upload build artifacts: SUCCESS;
- Publish versioned release: SKIPPED.

Artifact ID: `9453894478`.

## Refined package

Artifact contents:

- `Obsidian-0.2.0-phase2-dev7.jar`;
- SHA-256 `59dde49b210b802fdd88e1bbc2da7a9eae9b7be045b9c760ae1adc3827599725`;
- `Obsidian-0.2.0-phase2-dev7-sources.jar`;
- SHA-256 `c1ebbe080fa5262cb77d4a4ff48c805712880100126f05611ed99fa163d76c57`;
- metadata exactly `obsidian 0.2.0-phase2-dev7`.

## Packaged-bytecode verification

The refined built JAR confirms:

- active `FrameCoordinator` owns `RealMultiSectionSceneProbe`;
- shutdown evidence string includes `sceneRecordCapacity=9`, `wholeWindowInvalidation=true`, `boundedOneRecordAdmission=true`, `chunkLifecycleCountersDiagnostic=true`, `nativeGraphicsSeam=false`, `indexedIndirect=true`;
- runtime instruction explicitly requires a camera scene recenter and states that P2.6 separately owns the mandatory exact chunk-lifecycle gate;
- previously verified dev7 scene architecture remains packaged: 3x3 scene radius/capacity, 5x5 union halo lifecycle filtering, persistent scene owner and reused P2.6 draw path;
- metadata remains dev7.

No terrain semantic, graphics pipeline, scene-record cardinality, event-hook selection or GPU ownership architecture changed from A-0086. The only behavior change after A-0086 is the deterministic P2.7 validation closure contract described in A-0087.

## Runtime gate for this package

Reference runtime closure requires:

- at least three simultaneous live neighboring records;
- at least two adjacent record pairs;
- initial scene READY plus at least one rebuild READY transition;
- at least one real camera-driven scene recenter;
- nonzero exact dirty events;
- nonzero successful resource reload events;
- zero dropped lifecycle events;
- zero stale scene/probe installs;
- bounded staging/arena behavior and complete completion-gated reclamation;
- process exit 0;
- shutdown `sceneGateReady=true`.

Human oracle: no persistent duplicate/missing borders among simultaneously rendered neighboring records and no stale old-window geometry after edit, reload or recenter. A temporary whole-window blank interval while rebuilding is acceptable for this correctness milestone.

Chunk load/unload counters remain logged diagnostics and relevant invalidation inputs when they occur. They are not a P2.7 closure requirement. P2.6 still cannot merge until its own corrected fixed-target test produces the required chunk unload/load evidence.

## Merge rule

Dev7 remains stacked/draft, may not merge before dev6/P2.6, and currently has no user merge authorization.

This attempt is immutable once committed.
