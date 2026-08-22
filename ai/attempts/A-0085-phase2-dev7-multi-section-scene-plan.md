# A-0085 - Phase 2 dev7 persistent multi-section scene architecture

Status: **ACTIVE IMPLEMENTATION / STACKED ON CORRECTED DEV6 / EXACT CI PENDING**

Date: 2026-08-21
Branch: `phase2/multi-section-scene`
Canonical review PR: #27 (stacked on `phase2/section-lifecycle-rebuild`)
Temporary exact-CI PR: #28 (main-targeting, never merge)
Version: `0.2.0-phase2-dev7`

## Dependency state

P2.6 is not formally COMPLETE. A-0084 proves the corrected target-scoped event bridge eliminated lifecycle drops/overflow and preserved edit/resource rebuild correctness, but the latest reference run did not exercise tracked-neighborhood chunk unload/load. Therefore dev7 is intentionally implemented as a stacked branch and may not merge ahead of P2.6 closure.

## Roadmap objective

P2.7 is the first persistent multi-section integration milestone. It must prove:

- several neighboring real sections simultaneously;
- renderer-side scene database records instead of one one-shot section owner;
- stable camera movement;
- no visible duplicate or missing shared borders;
- bounded upload behavior under rebuild bursts;
- progressive retirement of synthetic probe-only assumptions.

It does not yet own the Phase 3 production asynchronous mesh scheduler or binary greedy mesher.

## Chosen correctness architecture

### Fixed scene window

The validation scene is one fixed 3x3 horizontal section window at one selected section Y:

- maximum 9 renderer-owned records;
- center is selected from `SectionSnapshot.tryCaptureNearPlayer()` so the scene starts at an already useful surface section;
- neighboring records use the same Y and X/Z offsets `-1..+1`;
- scene recenters once the player leaves the current +/-1-section X/Z window.

### Persistent scene records

`RealMultiSectionSceneProbe` owns a fixed preallocated record table. Each eligible record reuses the already-proven P2.6 `RealSectionLifecycleProbe` for:

- immutable exact-section + halo capture;
- permanent cube oracle;
- deterministic generalized vanilla SOLID/CUTOUT capture;
- BLOCK-format mesh construction;
- bounded staging upload;
- generation-safe device-arena ownership;
- public SOLID/CUTOUT BLOCK/lightmap indexed-indirect drawing;
- completion-gated geometry and indirect-command retirement.

The reuse is intentional: P2.7 changes ownership cardinality and scene lifetime, not terrain semantic correctness.

### Eligibility

The current P2.6 validation drawable intentionally requires both supported SOLID and CUTOUT quads. Dev7 therefore performs one bounded render-thread eligibility scan per candidate record before GPU admission.

A record is eligible only when exact capture succeeds and produces both SOLID and CUTOUT quads. The scene requires at least:

- 3 simultaneous eligible/live records;
- 2 horizontally adjacent live record pairs.

This is a validation constraint, not a long-term scene-database rule. Later production records must support empty/single-layer sections without needing this probe-specific requirement.

### Scene validity domain

The A-0083 lossless sticky/coalesced bridge is widened without reintroducing a ring:

- exact dirty events are relevant for any rendered section in the 3x3 window at the scene Y;
- chunk load/unload events are relevant within radius 2 of the center in X/Z;
- radius 2 is required because each rendered section needs a one-block halo, so the union of all nine records spans a 5x5 chunk footprint;
- world replacement and successful resource reload always invalidate;
- renderer-originated scene recenter is an explicit internal reason;
- unrelated world dirtiness does not advance the scene validity sequence;
- normal lifecycle transport has no overflow/drop path.

All current records share one scene generation and one relevant event sequence. Any relevant change makes the whole current scene stale.

### Rebuild policy

P2.7 deliberately uses whole-window invalidation/rebuild:

- all current record draw eligibility is revoked together;
- old GPU ownership completion-retires before the replacement record is admitted;
- replacements are then re-scanned/rebuilt under the new scene generation.

This is coarse but correct and keeps the milestone focused on multi-record ownership/borders/lifetimes. Fine per-section scheduling, dependency-aware cancellation and partial rebuild throughput belong to Phase 3.

### Bounded upload policy

- staging remains fixed at 4 MiB;
- the validation device geometry arena is fixed at 32 MiB, enough for nine bounded P2.5-class meshes without fallback growth;
- at most one new record is admitted while the staging arena has no pending batch;
- upload pressure therefore defers later records rather than growing unbounded memory or blocking for GPU completion;
- all normal completion polling remains nonblocking.

## Expected runtime evidence

A dev7 reference run should prove:

- `sceneReadyTransitions >= 2` (initial scene plus at least one rebuild);
- `sceneRebuilds >= 1`;
- `maxLiveRecords >= 3`;
- `maxAdjacentPairs >= 2`;
- `cameraRecenterEvents >= 1`;
- nonzero exact dirty/resource/chunk-unload/chunk-load counters;
- `droppedLifecycleEvents=0`;
- zero stale scene/probe installs;
- no persistent duplicate/missing borders during camera movement;
- no stale old-window geometry after invalidation/recenter;
- bounded staging and device-arena usage with complete reclamation;
- public indexed-indirect graphics and no widened native graphics seam;
- normal process exit 0 and shutdown `sceneGateReady=true`.

A temporary whole-window blank interval during invalidation/retirement/rebuild is acceptable for this correctness milestone.

## Deliberate non-goals

Not P2.7:

- global vanilla terrain replacement;
- production async worker pool;
- binary/bitmask greedy meshing;
- partial remeshing;
- GPU visibility integration for the full scene database;
- translucent/fluid terrain;
- broad mod/shader compatibility;
- performance claims from this validation scene.

This attempt is immutable once committed.
