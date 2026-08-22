# A-0106 - P3.2 promotion and P3.3 activation

**Date:** 2026-08-22  
**Target branch:** `main`  
**Result:** `SUCCESS`

## Runtime prerequisite

A-0105 recorded the successful `0.3.0-phase3-dev4` reference run:

- `phase3GateReady=true`;
- `schedulerEvidenceReady=true`;
- `binaryVisibilityEvidenceReady=true`;
- `binaryVisibilitySidecarIntegrated=true`;
- `greedyRectangleEmission=false`;
- 288/288 worker jobs completed;
- visibility builds `288`, total faces `102,367`;
- WEST/EAST/DOWN/UP/NORTH/SOUTH totals `7,159 / 11,145 / 4,424 / 56,663 / 15,272 / 7,704`, exact sum `102,367`;
- retained bytes `884,736 = 288 * 3,072`;
- visibility determinism audits/matches `7/7`;
- independent reference audits/matches `7/7`;
- zero worker queue-full rejection, failure, or shutdown join failure;
- zero dropped lifecycle events / unsafe stale scene installs;
- clean workers/staging/arena/resources;
- process exit code `0`.

The fixed-anchor Phase 2 unload/return sequence was intentionally not repeated. A-0101 remains the canonical proof for that already-closed dependency.

## Exact CI prerequisite

- canonical runtime code/package head `ab394076853d2647340c8eb4f2983ec842823938`: run `32583676238` passed Java 25 / Gradle 9.5.1 build + artifact upload; release skipped;
- later P3.2 evidence/documentation head `03ff120fe4996c5d3d1ac85d2d355180f0fa204b`: run `32583773383` passed build + artifact upload; release skipped;
- final runtime-evidence PR head `0c0d53ad59dd1d52e2a8ccc2e9194b770799ad6f`: run `32584015647` passed build + artifact upload; release skipped.

## Promotion

Standing user merge authorization applied to the validated Phase 3 chain.

PR #36 — Phase 3 dev4 binary section visibility masks — was marked ready and merged into `main` using `[no-release]` protection.

Merge commit:

- `54ca3cb2d64eda958579407728e757eb0c98b948`.

## Closure judgment

P3.2 is COMPLETE and merged because it proved:

1. permanent independent `ReferenceFaceMesh` correctness oracle retained;
2. immutable worker inputs with zero live-world reads after capture;
3. compact six-direction machine-word visibility masks at exactly 3,072 retained bytes per section;
4. deterministic construction via matching audits;
5. exact directional face coverage against the independent oracle;
6. bounded reusable scratch, bounded queues, and observable output/build metrics;
7. no greedy rectangle emission yet.

No new human visual verdict is recorded by A-0105; P3.2 did not change GPU-emitted geometry because `BakedSectionMesh` remained the authoritative drawable path. Existing P2/P3.1 visual correctness evidence therefore remains the visual baseline while P3.2 closure is based on topology/differential/runtime evidence.

## Next milestone

**P3.3 — greedy rectangle extraction is now ACTIVE.**

P3.3 must consume the proven binary visibility masks and add rectangle extraction / merged face emission without weakening the independent oracle or merge-key correctness requirements. The final material/light/AO/UV/model compatibility key must remain exact for any faces actually merged. Arbitrary generalized baked-model quads must remain on a safe passthrough path unless explicitly proven mergeable.

P3.3 activation does not mean greedy rectangles exist yet. This attempt only promotes validated P3.2 and synchronizes milestone status.

## Release discipline

The P3.2 merge used `[no-release]`. Public release intent remains unchanged until a separate release decision.