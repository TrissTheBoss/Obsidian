# A-0121 - Phase 3 P3.4 dev7 promotion and dev8 activation

**Date:** 2026-08-23  
**Result:** `SUCCESS` — validated dev7 was promoted; P3.4 remains ACTIVE and the next rectangle-emission-safety slice is activated.

## Dev7 promotion

A-0120 records successful real-machine closure of all frozen dev7 gates. Final evidence head `ae53eb6c9a0deaa86f4e92f70bfd029ab1c2e579` passed exact workflow `32602740308`: Java 25 / Gradle 9.5.1 build SUCCESS, artifact upload SUCCESS, release publishing SKIPPED.

PR #39 was marked ready and merged under standing Phase 3 authorization with `[no-release]`.

Merge commit:

- `cec4ecb2432ec92f17a94a358895de6c2f21257e`.

Dev7 closes the render-key-aware merge-candidate partition slice with real-terrain evidence:

- candidate builds `263`;
- candidates `85,880`;
- covered eligible faces `95,805` exact;
- canonical passthrough `23,617` exact;
- multi-face candidates `7,318`;
- faces saved `9,925` = `10.3%` over eligible faces;
- coverage `263/263`;
- determinism `6/6`;
- clean workers/staging/arena/resources and exit code 0.

The canonical dev7 runtime package remains `Obsidian-0.3.0-phase3-dev7.jar`, size `320,735` bytes, SHA-256 `ef2ff6f1bc78469a9a65db486f735c178565c8982fd62aa1bb60901bf56ce1c7`.

## Status boundary

Dev7 did not change emitted GPU geometry and deliberately ends with:

- `greedyRectangleGpuEmission=false`;
- `renderCorrectMergeKeyComplete=false`.

Therefore P3.4 remains **ACTIVE** and P3.5 remains planned/not active.

## Source-grounded next correctness problem

Exact source inspection shows the current generalized drawable uses Minecraft `DefaultVertexFormat.BLOCK` semantics with only four vertices per source quad: float3 position, RGBA8 color, float2 atlas UV0 and packed light UV2. `SectionBakedQuadSnapshot` captures exact atlas UV floats and exact per-corner color/light for every source quad.

A dev7 same-render-key candidate therefore does **not** automatically imply that one four-vertex large quad is raster-equivalent:

1. **Per-cell interpolation reset:** repeated source unit quads may carry the same four-corner color/light pattern, but each unit face restarts that pattern. One large quad interpolates only once across the complete rectangle. Exact equivalence requires stricter axis-wise corner constraints.
2. **Atlas UV repetition:** repeated unit faces use sprite-local UV rectangles expressed as atlas coordinates. A naive large quad either stretches one sprite over the rectangle or extends UVs outside the sprite rectangle into neighboring atlas content. The current four-UV vertex representation has no explicit per-cell repeat metadata.

This is now proven from the repository's actual capture/drawable representation rather than treated as a theoretical concern.

## Dev8 activation

Next version: `0.3.0-phase3-dev8`.

Next branch: `phase3/rectangle-emission-safety`.

Dev8 is a **sidecar-only emission-feasibility classifier**. Its goal is to quantify and prove which dev7 candidates, if any, are exactly representable as one ordinary four-vertex `DefaultVertexFormat.BLOCK` rectangle with the current shader/atlas semantics, while explicitly classifying why other candidates are unsafe.

No GPU geometry replacement occurs in dev8. Keep:

- `greedyRectangleGpuEmission=false`;
- `renderCorrectMergeKeyComplete=false`.

The likely classes to measure are:

- interpolation-safe vs interpolation-unsafe by merge axis;
- current-UV-representable vs UV-repeat-required;
- combined ordinary-quad-safe candidates;
- candidates requiring a future custom repeat-aware shader/metadata path.

A later slice may design that custom representation only after dev8 measures the real terrain distribution and freezes exact semantics. Any slice that actually changes GPU geometry requires renewed explicit human visual validation.

## Next action

Create `phase3/rectangle-emission-safety` from fresh `main`, freeze the exact dev8 mathematical/representation contract in A-0122, then implement the pure classifier and run exact GitHub CI before production worker integration.