# A-0108 - Phase 3 P3.3 dev5 greedy rectangle sidecar plan

**Date:** 2026-08-22  
**Branch:** `phase3/greedy-rectangle-sidecar`  
**Planned version:** `0.3.0-phase3-dev5`  
**Result:** `PARTIAL` — implementation and runtime evidence still required.

## Objective

Implement the first P3.3 milestone as a correctness-first **topology rectangle sidecar** over the proven P3.2 `BinarySectionVisibility` masks. The sidecar must prove deterministic machine-word rectangle extraction and exact face coverage without changing GPU-emitted terrain geometry.

## Frozen dev5 scope

1. Consume only immutable worker-owned `BinarySectionVisibility`; no live world/model/material/light reads.
2. Preserve the permanent independent `ReferenceFaceMesh` oracle.
3. Extract deterministic maximal same-direction topology rectangles from the six P3.2 visibility masks.
4. Keep the existing generalized `BakedSectionMesh` as the authoritative production drawable output.
5. Keep arbitrary/non-canonical baked-model quads on the existing exact path; dev5 must not reinterpret them as voxel rectangles.
6. Keep the full render-correct material/light/AO/UV/model merge-key checkpoint in P3.4. Dev5 rectangle reduction is therefore a topology-sidecar measurement, not yet production-safe merged geometry.
7. Preserve bounded worker queues, cancellation/generation/event/resource checks, render-thread GPU ownership, and completion-gated lifetime handling.

## Deterministic rectangle coordinate contract

Direction order remains the permanent P2/P3 order:

- `WEST=0`, `EAST=1`, `DOWN=2`, `UP=3`, `NORTH=4`, `SOUTH=5`.

Each rectangle stores `direction`, `plane`, `u`, `v`, `width`, and `height` in one packed primitive record. Coordinate mapping is fixed as:

- WEST/EAST: `plane=x`, `u=z`, `v=y`;
- DOWN/UP: `plane=y`, `u=x`, `v=z`;
- NORTH/SOUTH: `plane=z`, `u=x`, `v=y`.

Extraction order is deterministic: direction ascending, plane ascending, row `v` ascending, least-significant visible `u` first.

For each 16x16 plane, dev5 uses 16-bit primitive rows. At the first remaining set bit it takes the full contiguous horizontal run, then extends the rectangle vertically while every row contains that complete run mask. The emitted rectangle is then cleared from the working plane before extraction continues.

This produces a deterministic inclusion-maximal topology partition while staying primitive/machine-word based.

## Bounded representation

Worst-case rectangle count is bounded by `ReferenceFaceMesh.MAX_FACES` (`24,576`). Worker-local build scratch may therefore reserve a fixed primitive record array up to that bound plus one 16-row plane workspace and reusable coverage words. Retained output copies only the actual packed rectangle records.

Required metrics include:

- primary rectangle builds;
- rectangle count and covered source-face area;
- per-direction rectangle count and covered area;
- retained bytes;
- total/max build time;
- source-face -> rectangle reduction ratio/accounting;
- worker-local scratch uses/high-water;
- deterministic duplicate-build audits;
- exact P3.2-mask coverage audits;
- independent reference-oracle audits.

## Correctness proof

Every primary dev5 sidecar build must validate exact coverage against its source `BinarySectionVisibility` using reusable primitive coverage words:

- no rectangle overlap;
- every rectangle face lies inside the source mask;
- expanded rectangle coverage equals every P3.2 mask word exactly;
- rectangle area totals equal source visible-face totals globally and per direction.

On the existing sparse worker audit cadence:

1. build a second rectangle sidecar and require exact content equality;
2. retain the existing independent `ReferenceFaceMesh` build and P3.2 visibility/reference check;
3. directly validate rectangle expanded coverage against the independent reference face set.

The optimized rectangle extractor never becomes its own oracle.

## Runtime closure contract

Dev5 runtime may be promoted only if the final coordinator proves, at minimum:

- existing `phase3GateReady=true`;
- existing `schedulerEvidenceReady=true`;
- existing `binaryVisibilityEvidenceReady=true`;
- new `greedyRectangleEvidenceReady=true`;
- rectangle builds cover all completed production jobs;
- covered rectangle area equals visibility face count;
- rectangle count is positive and no greater than covered face area;
- at least one real reduction is observed across the run (`total rectangles < total covered faces`);
- deterministic audits/matches are nonzero and equal;
- exact mask coverage audits/matches are nonzero and equal;
- independent reference audits/matches are nonzero and equal;
- zero worker queue-full rejection/failure/shutdown join failure;
- zero unsafe stale installs/dropped lifecycle events;
- workers/staging/arena/resources clean;
- normal process exit code `0`.

Because dev5 does not change GPU-emitted geometry, human visual comparison is a regression guard rather than evidence that greedy geometry is already production-rendered.

## Explicit non-goals

Dev5 does **not**:

- replace `BakedSectionMesh` GPU output;
- emit final greedy vertices/indices;
- claim the complete P3.4 render-correct merge key;
- merge arbitrary generalized model quads;
- add fluid/translucent merging;
- add partial remeshing;
- alter Phase 2 chunk-lifecycle hooks;
- widen native Vulkan scope.

## Promotion authorization

The user explicitly authorized merge for P3.3 dev5. That authorization permits promotion after the frozen CI/runtime closure contract is actually satisfied; it does not waive missing runtime evidence. Promotion must use `[no-release]`.