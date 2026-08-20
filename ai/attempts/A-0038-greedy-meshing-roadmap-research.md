# A-0038 - Greedy meshing roadmap research

**Date:** 2026-08-20  
**Status:** SUCCESS - architecture research / roadmap decision

## Objective

Decide whether greedy meshing belongs in Obsidian's final terrain renderer, which variant best matches the project's performance goals, what correctness constraints it requires for Minecraft-like rendering, and where it belongs in the roadmap.

## Research basis

Reviewed established and current voxel-meshing references, including:

- Mikola Lysenko / 0fps, `Meshing in a Minecraft Game` and Part 2: classic greedy rectangle merging, multi-type/orientation considerations and T-junction discussion;
- Mikola Lysenko / 0fps, `Ambient occlusion for Minecraft-like worlds`: greedy merging with equal AO corner values and AO-based triangle-diagonal selection;
- `cgerikj/binary-greedy-meshing`: modern bitwise/binary greedy approach using machine-word face masks, compact quad records, vertex pulling example and explicit T-junction mitigation notes;
- the current Rust port `Inspirateur/binary-greedy-meshing`, which extends the approach to transparent-block handling and reusable meshing buffers.

Public references:

- https://0fps.net/category/programming/voxels/
- https://0fps.net/2012/07/07/meshing-minecraft-part-2/
- https://0fps.net/2013/07/03/ambient-occlusion-for-minecraft-like-worlds/
- https://github.com/cgerikj/binary-greedy-meshing
- https://docs.rs/crate/binary-greedy-meshing/latest/source/README.md

The research is conceptual only. Obsidian will not copy source from external renderers/meshers; implementation must be original and compatible with the project's licensing choices.

## Result

Greedy meshing should be part of the final product. The production performance target should be a binary/bitmask greedy CPU mesher, not a naive face emitter and not an allocation-heavy textbook mask implementation.

### Why this fits Obsidian

- Large flat or repeated voxel surfaces collapse from many visible unit faces into a small number of quads, reducing vertex/index bytes, staging traffic, arena occupancy, draw/triangle work and downstream visibility metadata.
- Bitmask visibility extraction can process many cells per machine word, which fits worker-local scratch and predictable allocation-free CPU meshing.
- Greedy meshing is complementary to GPU-driven visibility/indirect rendering: the CPU creates fewer, larger surface primitives; the GPU decides which section/mesh records are visible and compacts draw work.
- The algorithm can remain vendor-neutral and entirely CPU-side.

### Correctness constraints for Minecraft-like terrain

A face may merge only when every attribute that affects the rendered result is compatible. The eventual merge key must account for at least:

- face axis/direction;
- material/block-face or sprite/texture identity;
- render layer (opaque/cutout/translucent etc.);
- tint/color state;
- sky/block light values or the final per-corner light representation;
- four-corner ambient-occlusion pattern;
- AO-dependent diagonal orientation where applicable;
- UV/repeat/stretch semantics;
- fluid/special-face state;
- model-specific attributes for any block class the greedy path supports.

Matching block IDs alone is insufficient.

### AO

The 0fps AO analysis shows greedy facets can merge when their vertex AO values agree, because AO stays constant along a valid long greedy edge. Quad triangulation should choose the diagonal from the corner AO sums when the values are non-coplanar, avoiding visible interpolation flips.

### Boundaries

Meshing snapshots should include neighbor halo/padding so visibility, AO, light and merge eligibility at section edges do not require synchronous world reads. This also makes meshing jobs immutable and worker-friendly.

### T-junctions

Greedy rectangle merging can produce T-junctions. Historical tests did not show them as an inherent correctness failure, while modern binary-greedy renderers still use defensive techniques such as eye-relative coordinates and slight face expansion. Obsidian should not globally split every greedy quad by default. First use stable local/eye-relative geometry and test real Vulkan GPUs. Add targeted mitigation only if cracks are actually reproduced.

### Data representation

The binary-greedy reference demonstrates that compact per-quad records are possible and pairs the technique with vertex pulling. Obsidian should treat that as an optimization direction, not a requirement for the first production mesher. The first implementation should benchmark at least:

1. conventional indexed quad output into the existing geometry arena;
2. compact packed quad records + generated/pulled vertices if the later graphics/indirect path supports it cleanly.

The choice should be made from measured CPU mesh time, output bytes, GPU time and tail-latency impact rather than theoretical compactness alone.

## Roadmap judgment

Do **not** insert greedy meshing into Phase 1. Phase 1 is establishing GPU ownership, command submission, memory and indirect rendering.

Recommended sequence:

- **Phase 2 - one chunk correctly:** establish extraction/snapshot semantics and a correctness/reference mesh path. A deliberately simple mesher is acceptable here because it provides a differential oracle.
- **Phase 3 - terrain engine / CPU mesh system:** implement the production binary-greedy mesher, worker-local reusable scratch, halo snapshots, merge keys, AO/light correctness, priority scheduling and metrics. Differential-test it against the Phase 2 reference path.
- **Phase 4 - GPU-driven visibility:** consumes the compact section meshes produced by Phase 3; greedy meshing reduces bytes/draw work but does not replace culling/compaction.

Keep the simple reference mesher after Phase 3 for tests/fuzzing; do not make the optimized mesher its own correctness oracle.

## Intended effect

Reduce terrain mesh size and upload/GPU workload without sacrificing intended vanilla visuals, while keeping meshing CPU cost predictable enough for excellent 1%/0.1% lows at large render distance.

## Actual effect

Research completed and durable decision D-0024 added. No runtime behavior changes in this attempt.

## Next action

Finish Phase 1 indirect-draw infrastructure. During Phase 2 define the immutable section snapshot/face-material contract so it already contains the halo/light/AO information Phase 3 greedy meshing needs. Implement and benchmark the binary-greedy production mesher in Phase 3.