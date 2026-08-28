# A-0137 — Phase 3 P3.4 dev11 repeat-aware greedy GPU emission canary plan

**Date:** 2026-08-28  
**Status:** FROZEN BEFORE IMPLEMENTATION  
**Target version:** `0.3.0-phase3-dev11`  
**Branch:** `phase3/repeat-aware-gpu-emission`

## Objective

Dev11 is the first geometry-changing P3.4 slice after dev6-dev10 proof work.

Replace only exact dev10 transport-safe canonical source-face groups in the existing bounded Obsidian comparison scene with one repeat-aware large quad per admitted candidate. Every unsupported, generalized, ambiguous, noncanonical or unsafe baked quad remains exact passthrough.

This is a **canary/comparison geometry path**, not full Minecraft chunk-renderer replacement. The existing Minecraft world remains underneath; Obsidian's comparison geometry keeps the existing `1/512` face-normal offset.

P3.5 is not active. P3.6 remains the broader T-junction policy milestone.

## Source/API facts frozen from inspection

1. `SectionMeshWorkerPool.Ticket` already retains `CanonicalFaceRenderKeys`, `RenderMergeCandidates`, `OrdinaryQuadEmissionSafety`, `RepeatAwareUvDescriptors`, `RepeatAwareTransportProof`, and the exact `BakedSectionMesh` oracle/drawable result.
2. `CanonicalFaceRenderKeys.sourceQuad(x,y,z,direction)` gives the exact unique baked source quad for an eligible canonical face. This is sufficient to identify exactly which baked source quads a transport record replaces.
3. `BakedSectionMesh` currently serializes `DefaultVertexFormat.BLOCK`: float3 position + RGBA8 comparison color + float2 UV0 + signed-short2 light, 28 bytes/vertex, 4 vertices/quad, 6 int indices/quad. It applies the comparison face offset and 3/4 RGB multiplier.
4. `WorkerBackedSectionLifecycleProbe` performs all GPU allocation/upload/draw/install/retirement on the render thread, binds the live blocks atlas with `blocksAtlas.getTextureView()` + `blocksAtlas.getSampler()`, binds the live lightmap, checks resource epoch, and uses completion-gated arena/deferred-resource retirement.
5. Minecraft 26.2 `SOLID_BLOCK` / `CUTOUT_BLOCK` are based on `core/block`, with DynamicTransforms/Projection/Fog, `Sampler0`, `Sampler2`, and BLOCK semantics. The block vertex shader transforms Position, samples lightmap using UV2, and forwards UV0. The block fragment shader samples `Sampler0`, multiplies vertex color and `ColorModulator`, applies alpha cutout where configured, and applies fog.
6. Public 26.2 `RenderPipeline` supports explicit custom shader identifiers and vertex formats. Public shader loading scans namespaced `shaders/*.vsh` / `*.fsh`. No native Vulkan graphics expansion is required.
7. Vanilla 26.2 registers vertex-element IDs 0-6. The public `VertexFormatElement.register` range is 0-31. Dev11 may reserve IDs 30 and 31 for its canary custom attributes; a duplicate registration must fail loudly rather than silently reinterpret data.

## Hybrid mesh representation

Create a pure worker-side `RepeatAwareGreedyMesh` built from immutable snapshot/sidecars only.

The mesh has:

1. **Passthrough BLOCK vertices** — every source baked quad not replaced by a dev10 transport record, serialized exactly like current `BakedSectionMesh` (including 3/4 comparison RGB and `1/512` normal offset).
2. **Merged repeat-aware vertices** — one 4-vertex large quad per dev10 transport record, using a custom 60-byte vertex format.
3. **One combined int index stream** ordered as passthrough SOLID, passthrough CUTOUT, merged SOLID, merged CUTOUT.

### Custom merged vertex format

Per merged vertex:

- `Position`: RGB32 float = 12 bytes;
- `Color`: RGBA8 UNORM = 4 bytes;
- `UV0`: RG32 float = 8 bytes, used as **unwrapped candidate-local repeat coordinate `(s,t)`**;
- `UV2`: RG16 SINT = 4 bytes, exact packed light;
- `RepeatAtlas0`: RGBA32 float = 16 bytes;
- `RepeatAtlas1`: RGBA32 float = 16 bytes.

Total: **60 bytes/vertex**, 240 bytes/merged quad.

`RepeatAtlas0 = (baseU, baseV, dSU, dSV)` where `base` is the exact dev9 atlas coordinate for geometric corner 0 and `dS` is the exact dev9 affine atlas delta from geometric corner 0 -> 1.

`RepeatAtlas1 = (dTU, dTV, width, height)` where `dT` is the exact dev9 affine atlas delta from geometric corner 0 -> 2 and width/height are the exact dev7 candidate extents.

The two custom attributes are constant across all four vertices of one merged quad.

## Source suppression / replacement theorem

For each dev10 transport record:

1. read its exact dev7 candidate index;
2. enumerate every covered source cell using dev7 direction/plane/u/v/width/height mapping;
3. resolve the exact source baked quad through `CanonicalFaceRenderKeys.sourceQuad(...)`;
4. require a valid unique source quad;
5. mark that exact baked quad suppressed once and only once.

Required aggregate identities:

- `suppressedSourceQuads == transport.coveredFaces()`;
- `mergedQuads == transport.recordCount()`;
- `passthroughQuads == baked.quadCount() - suppressedSourceQuads`;
- `hybridQuads == passthroughQuads + mergedQuads`;
- `hybridQuads == baked.quadCount() - transport.facesSaved()`;
- every source baked quad is exactly one of {passthrough, suppressed/replaced};
- no source baked quad is suppressed twice;
- no suppressed source quad remains in passthrough output;
- every transport record emits exactly one replacement merged quad.

Layer-specific source suppression and replacement counts must reconcile exactly for SOLID and CUTOUT independently.

## Merged geometry construction

Dev7 geometric corner convention remains:

- 0 = `(uLow,vLow)`;
- 1 = `(uHigh,vLow)`;
- 2 = `(uLow,vHigh)`;
- 3 = `(uHigh,vHigh)`.

Direction mapping remains:

- W/E: plane=x, u=z, v=y;
- D/U: plane=y, u=x, v=z;
- N/S: plane=z, u=x, v=y.

The large quad uses exact integer candidate bounds and the same `BakedSectionMesh.COMPARISON_FACE_OFFSET` along the face normal.

Vertex order is **not canonicalized**. For source baked vertex `i`, read the dev10 2-bit geometric-corner code from `sourceCornerOrderSignature`; emit the large-quad position/repeat coordinate for that geometric corner into vertex slot `i`. Keep the existing source diagonal/index pattern `0,1,2, 0,2,3`.

Color/light for each large-quad vertex come from the representative source baked vertex corresponding to that same geometric corner. Dev8/dev9 safety is the proof that these four endpoint values interpolate equivalently across the admitted rectangle.

## Repeat-aware shader contract

Add namespaced Obsidian block-comparison shaders under `assets/obsidian/shaders/` and build public Blaze3D SOLID/CUTOUT merged pipelines.

The merged vertex shader must preserve current `core/block` behavior for:

- `Position + ModelOffset`;
- DynamicTransforms / Projection;
- fog distances;
- exact lightmap sampling from UV2;
- vertex color.

It additionally forwards:

- varying unwrapped `repeatCoord`;
- flat `RepeatAtlas0`;
- flat `RepeatAtlas1`.

The merged fragment shader must preserve current block-fragment behavior except `Sampler0` coordinate generation:

```
cellS = min(floor(s), width  - 1)
cellT = min(floor(t), height - 1)
localS = s - cellS
localT = t - cellT
atlasUv = base + dS * localS + dT * localT
```

This implements the frozen positive outer-edge endpoint policy: at exact `s==width` / `t==height`, the final cell is selected and local coordinate is `1`, while internal integer reset lines map to the next cell's local `0`.

Explicit gradients must be derived from **unwrapped** repeat coordinates:

```
gradX = dS * dFdx(s) + dT * dFdx(t)
gradY = dS * dFdy(s) + dT * dFdy(t)
texel = textureGrad(Sampler0, atlasUv, gradX, gradY)
```

Never derive gradients from `fract`, wrapped atlas coordinates, or adjacent-sprite atlas wrapping.

The fragment shader then applies the same comparison vertex color / `ColorModulator`, same cutout threshold, and same fog function as the current block path.

The render-thread draw must bind **the same live blocks-atlas texture view and sampler** already used by the current comparison path and the same lightmap sampler. Resource-epoch validation remains mandatory.

## GPU layout / draw contract

Keep passthrough and merged vertex buffers separate because they have different formats. Use one combined int index buffer and four fixed public indexed-indirect commands:

1. passthrough SOLID;
2. passthrough CUTOUT;
3. merged SOLID;
4. merged CUTOUT.

A zero index count is a legal no-op command for an empty layer/class. Do not allocate a zero-length vertex buffer; skip the corresponding pass when that vertex class has zero quads.

No native Vulkan graphics calls are added.

Arena/staging remain existing bounded/completion-gated infrastructure. No capacity increase is required: replacing any transport record covers at least 2 source quads. Two original BLOCK quads cost at least `2 * (4*28 + 6*4) = 272` upload bytes; one merged quad costs `4*60 + 6*4 = 264` bytes. Therefore the hybrid upload payload is never larger than the original BakedSectionMesh payload for the same captured source set.

## Worker integration

Every worker job continues to build all existing P3.2-dev10 proof sidecars and the independent exact `BakedSectionMesh` result.

After dev10 proof, build `RepeatAwareGreedyMesh` from the exact existing immutable inputs and retain it on the completed ticket.

Determinism cadence must duplicate the hybrid build and require exact content equality.

Metrics must include at least:

- builds;
- source baked quads;
- transport records;
- suppressed source quads;
- passthrough quads;
- merged quads;
- hybrid quads;
- source/hybrid SOLID and CUTOUT counts;
- faces saved;
- passthrough/merged vertex bytes;
- index bytes;
- total hybrid upload bytes;
- exact accounting audits/matches;
- determinism audits/matches;
- scratch uses/high-water;
- build time/max;
- per-direction merged counts/covered faces/faces saved.

## Runtime gate

New final gate: **`repeatAwareGreedyEmissionEvidenceReady=true`**.

It requires all prior dev10 gates plus:

- hybrid builds > 0 and >= completed worker jobs;
- total transport records == total merged quads;
- total suppressed source quads == total dev10 covered faces;
- total passthrough + suppressed == total source baked quads;
- total hybrid == passthrough + merged;
- total hybrid == total source baked quads - dev10 faces saved;
- layer source/passthrough/suppressed/merged/hybrid accounting exact;
- per-direction merged/covered/saved sums exact to dev10 transport evidence;
- total hybrid upload bytes <= corresponding original BakedSectionMesh upload bytes;
- primary accounting audits == builds and all match;
- determinism audits > 0 and all match;
- render-thread capture/GPU ownership true;
- worker live-world reads after capture 0;
- queue failures/rejections/join failures 0;
- scene/lifecycle/lifetime gates clean;
- normal process exit 0.

Runtime integration flags:

- `repeatAwareGreedyGpuEmission=true` once the hybrid mesh is actually installed/drawn;
- retain `greedyRectangleGpuEmission=false` because raw P3.3 topology rectangles are not what is emitted;
- retain `renderCorrectMergeKeyComplete=false` because dev11 is still a bounded canary, not the broad P3.4 completion proof.

## Mandatory human visual/raster gate

**Dev11 cannot be promoted from automated evidence alone.**

The user must provide an explicit visual PASS after running the geometry-changing canary on the reference Vulkan machine.

Required deliberate visual exercise:

- close and oblique inspection of repeated planar textures across internal integer repeat-reset lines;
- rectangle-to-rectangle boundaries, especially long edges meeting shorter edges;
- section boundaries where available;
- camera rotation and movement to expose cracks, mip/filter shimmer or transient seams;
- block break/place and rebuilt READY scene;
- F3+T and rebuilt READY scene;
- normal exit with complete shutdown evidence.

Explicitly reject promotion for observed:

- texture stretching;
- atlas bleed / wrong neighboring sprite;
- wrong mip/filter footprint or shimmer;
- one-pixel repeat-line seam;
- T-junction/section crack or z-fighting;
- wrong winding/diagonal/culling;
- color/light mismatch;
- duplicate source+merged face;
- missing covered face.

If artifacts appear, follow D-0024: use targeted candidate exclusion or selective split/mitigation and revalidate. Do not globally abandon greedy meshing or globally conform all topology without evidence.

## Non-claims

Dev11 does not complete:

- full production Minecraft opaque/cutout terrain replacement;
- P3.5 border/halo correctness;
- the broader P3.6 T-junction policy;
- P3.7 full differential framework;
- P3.4 `renderCorrectMergeKeyComplete` unless a later frozen completion slice establishes that separately.

## Promotion rule

Promotion requires all of:

1. exact CI/package success;
2. `repeatAwareGreedyEmissionEvidenceReady=true` with all prior gates true and clean lifetime;
3. exact source suppression/replacement accounting;
4. normal launcher exit code 0;
5. **explicit human visual PASS** for the geometry-changing path.

No one of these substitutes for another.
