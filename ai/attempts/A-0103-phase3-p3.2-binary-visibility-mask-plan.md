# A-0103 - Phase 3 P3.2 binary visibility mask plan

**Date:** 2026-08-22  
**Branch:** `phase3/bitmask-visibility-masks`  
**Planned version:** `0.3.0-phase3-dev4`  
**Result:** `PARTIAL` — scope frozen; implementation/runtime evidence still required.

## Objective

Start P3.2 from the synchronized P3.1-complete `main` baseline by introducing the first production-suitable binary/bitmask visibility representation without jumping ahead to P3.3 greedy rectangle extraction or P3.4 render-correct face merging.

## Governing contract

Repository truth requires:

- D-0024 remains active: production terrain meshing targets worker-local binary/bitmask greedy meshing while the simple P2.1 reference oracle remains permanently independent;
- immutable renderer-owned snapshots remain the worker input boundary;
- no live Minecraft world/model/light/resource reads are allowed after capture;
- P3.2 must prove deterministic directional face coverage against independent reference semantics before greedy rectangle extraction becomes production;
- arbitrary/general baked-model geometry and exact material/light/AO data must not be discarded or approximated merely to increase mask coverage.

## First implementation slice

Add a pure `BinarySectionVisibility` representation built only from `SectionSnapshot`.

Representation:

- six directional face masks corresponding exactly to the permanent `ReferenceFaceMesh` direction order: WEST, EAST, DOWN, UP, NORTH, SOUTH;
- one bit per interior 16^3 source cell for each direction;
- 4096 bits = 64 `long` words per direction;
- 384 retained `long` words total = 3,072 bytes per complete six-direction mask set;
- bit index is deterministic local-cell order `((y * 16) + z) * 16 + x`;
- worker-local reusable scratch holds 18x18 row masks for supported and air classification, using the existing one-block halo;
- retained mask output owns only the compact directional bitsets plus counts/fingerprint.

Visibility semantics exactly match the existing conservative cube oracle:

`emit face iff source == SUPPORTED_FULL_CUBE and neighbor == AIR`.

An `UNSUPPORTED` neighbor suppresses the face exactly as `ReferenceFaceMesh` does.

## Independent correctness proof

`BinarySectionVisibility` must provide:

1. self-validation against the immutable snapshot;
2. deterministic fingerprint/content comparison;
3. exact set-equivalence validation against an independently built `ReferenceFaceMesh`:
   - total visible face count equals reference face count;
   - every reference packed face is present in the corresponding directional bitset;
   - equal count + complete reference inclusion proves no extra mask faces;
4. per-direction visible-face counts;
5. bounded retained-byte and scratch-use/high-water metrics.

The reference oracle must not call or share the optimized bitmask construction algorithm.

## Integration staging

The first code commit will add the pure mask primitive and compile/package it as `0.3.0-phase3-dev4`.

The next P3.2 integration step will attach the mask sidecar to real `SectionMeshWorkerPool` jobs and expose runtime evidence without replacing the already validated `BakedSectionMesh` output yet. That preserves current production rendering while the new topology representation proves itself under real asynchronous scene churn.

Once worker integration is present, the P3.2 runtime gate should require:

- nonzero binary visibility builds;
- nonzero visible faces;
- all six direction counters populated where the sampled terrain exercises them, without requiring every direction on every individual section;
- nonzero reusable visibility-scratch use;
- deterministic mask audits > 0 and matches == audits;
- independent reference audits > 0 and matches == audits;
- zero worker failures / queue-full rejections / shutdown join failures;
- existing P3.1 ownership, lifecycle and cleanup gates remain green.

## Deliberate non-goals

Not P3.2:

- greedy rectangle extraction;
- emitting merged geometry;
- replacing `BakedSectionMesh` production output;
- constructing the final material/light/AO merge key;
- merging arbitrary non-cube model quads;
- fluid/translucent terrain;
- partial remeshing;
- worker-thread live-world capture.

## Exit criteria

P3.2 can be marked COMPLETE only after:

- the bitmask primitive is integrated into production worker jobs;
- exact reference-set equivalence is demonstrated by runtime audits;
- deterministic mask construction is demonstrated;
- bounded scratch/output accounting is visible in the final runtime log;
- existing async scene correctness and cleanup remain green;
- exact CI/package evidence and the reference-machine runtime are recorded in new immutable attempts.

## Next action

Implement the pure `BinarySectionVisibility` primitive first, bump the development version to `0.3.0-phase3-dev4`, compile it through the main-targeting draft PR, then integrate it into the worker/runtime gate in the same P3.2 branch.