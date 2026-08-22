# A-0127 — Phase 3 P3.4 dev8 promotion / dev9 activation

Date: 2026-08-23
Result: **SUCCESS**

## Dev8 promotion

P3.4 dev8 ordinary four-vertex emission-safety classification is complete.

Canonical reference evidence is A-0126. The final exact evidence head `f4b8028cb46708a8990b1c4456bc29e5bd993fa9` passed workflow `32604062038`:

- Java 25 / Gradle 9.5.1 build SUCCESS;
- artifact upload SUCCESS;
- release publishing SKIPPED.

PR #40 was marked ready and merged with `[no-release]` as:

- merge SHA `7a15f857a081fba642fcc28811ce88363b5abb66`.

The validated runtime package remains:

- `Obsidian-0.3.0-phase3-dev8.jar`;
- SHA-256 `f7155754683c6f484356cc4e729bd5de262b4acd355df05a49e55122903f9f4e`.

## Decisive dev8 runtime result

Across 234 production builds:

- dev7 multi-face candidates: 6,383;
- color-safe / unsafe: `6,352 / 31`;
- light-safe / unsafe: `6,383 / 0`;
- ordinary-atlas-UV-safe / unsafe: `0 / 6,383`;
- combined ordinary four-vertex safe / unsafe: `0 / 6,383`;
- repeat-aware-required: 6,383.

All frozen dev8 correctness/determinism/lifetime gates passed and the process exited 0.

Therefore ordinary four-vertex atlas UV0 is conclusively not a viable direct emission representation for any observed multi-face dev7 candidate. This is not a failure of render-key grouping. It is a representation limitation.

## Activate P3.4 dev9

P3.4 remains ACTIVE. P3.5 is not activated.

The next slice is **dev9 — repeat-aware UV descriptor / representability sidecar**.

Dev9 must remain sidecar-only. It must determine, from immutable captured baked truth, whether each dev7 multi-face candidate admits an exact sprite-local repeating UV descriptor suitable for a future shader/vertex representation.

Required direction for the frozen dev9 contract:

- preserve exact dev6 material/sprite/layer identity;
- use representative canonical baked-quad UVs mapped to geometric corners, not state/block-ID assumptions;
- prove the four atlas UV corners form an exact axis-aligned source sprite rectangle with a deterministic corner permutation/orientation;
- distinguish flipped/rotated mappings rather than normalizing them incorrectly;
- define per-cell local repetition independently of full-atlas sampler wrap;
- combine representable repeat-aware UVs with dev8 color/light interpolation safety to measure candidates that could later use one four-vertex rectangle plus repeat-aware UV metadata;
- keep color-unsafe candidates on split/passthrough evidence until a separate proof exists;
- retain deterministic bounded primitive metadata and exact audits;
- keep `greedyRectangleGpuEmission=false` and `renderCorrectMergeKeyComplete=false`;
- keep `BakedSectionMesh` authoritative.

A later geometry-changing slice must separately implement the representation/shader, prove raster/T-junction obligations relevant to that emission path, and obtain renewed explicit human visual validation before promotion.

## Status after activation

- Phase 3: ACTIVE.
- P3.4: ACTIVE.
- dev6: COMPLETE SLICE.
- dev7: COMPLETE SLICE.
- dev8: COMPLETE SLICE.
- dev9 repeat-aware UV descriptor/representability: ACTIVE.
- P3.5: PLANNED / not active.
