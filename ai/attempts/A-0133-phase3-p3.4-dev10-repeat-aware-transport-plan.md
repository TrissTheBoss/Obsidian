# A-0133 — Phase 3 P3.4 dev10 repeat-aware transport/sampling proof plan

**Date:** 2026-08-23  
**Result:** SUCCESS — scope frozen before implementation.

## Objective
Freeze the no-emission correctness contract that bridges dev9's proven sprite-local UV descriptor to a later large-quad shader/vertex representation. Dev10 must prove the transport algebra, preserve source vertex ordering/diagonal identity, and make filtering/raster obligations explicit without changing the authoritative `BakedSectionMesh` GPU drawable.

## Source truth
Dev10 consumes only immutable worker-safe truth already captured/proven by P3.4:

- `RenderMergeCandidates` — candidate geometry, direction and representative source baked quad;
- `OrdinaryQuadEmissionSafety` — exact per-candidate color/light interpolation safety;
- `RepeatAwareUvDescriptors` — exact raw source atlas U/V bounds plus affine geometric-corner→UV-corner orientation;
- `SectionBakedQuadSnapshot` — representative source vertex order/material/geometry identity.

No worker may query live world, model, sprite, texture or sampler state.

## Key architecture conclusion from exact source inspection
`SectionBakedQuadSnapshot.MaterialIdentity` retains atlas/sprite IDs, layer, material flags, tint, shade, emission and animation plus the raw per-vertex atlas UVs, but it does not retain sampler/filter/mip parameters. The live draw path binds `Minecraft`'s current blocks-atlas texture view and `AbstractTexture.getSampler()` directly.

Dev10 therefore does **not** invent or capture a second model of sampler/filter/mip/padding state. The future repeat-aware path is required to bind the **same live blocks-atlas texture view and sampler** under the same resource epoch as the vanilla-block comparison path. Sampling equivalence is proved by matching source atlas coordinates and gradients, making the proof independent of the sampler's internal filter/mip settings for fragment interiors.

This keeps D-0023/D-0025 intact: no broader Vulkan/native seam is introduced for convenience.

## Frozen transport algebra
For a dev7 candidate of integer extent `W x H`, candidate-local coordinates are `s in [0,W]`, `t in [0,H]`.

Cell selection uses a half-open rule with an explicit outer-max endpoint:

- `cellS = min(floor(s), W - 1)`;
- `cellT = min(floor(t), H - 1)`;
- `x = s - cellS`;
- `y = t - cellT`.

Therefore ordinary interiors have `x,y in [0,1)`, while the candidate's positive outer edges map to local value `1` rather than incorrectly wrapping to `0`.

Dev9's orientation signature stores UV-corner codes `c0,c1,c2,c3` for geometric corners `(0,0),(1,0),(0,1),(1,1)`. Each UV corner code is `(uBit | vBit<<1)`. The frozen affine square-symmetry proof requires:

- `c0..c3` contain all four UV corners exactly once;
- `dS = c0 xor c1` and `dT = c0 xor c2` are `{1,2}` in either order;
- `c3 = c0 xor dS xor dT`.

For local `(x,y)`, sprite-local coordinates are the affine transform implied by those four corner bits:

- `spriteU = u00 + (u10-u00)*x + (u01-u00)*y`;
- `spriteV = v00 + (v10-v00)*x + (v01-v00)*y`.

Atlas coordinates are then:

- `atlasU = uLow + spriteU * (uHigh-uLow)`;
- `atlasV = vLow + spriteV * (vHigh-vLow)`.

The future shader must use explicit gradients derived from the **unwrapped** candidate-local `s,t`, not derivatives of `fract`/wrapped coordinates. For an open unit-cell interior:

- `dAtlasU = (uHigh-uLow) * ((u10-u00)*dS + (u01-u00)*dT)`;
- `dAtlasV = (vHigh-vLow) * ((v10-v00)*dS + (v01-v00)*dT)`.

Because the dev9 orientation is an affine square symmetry, these are exactly the source unit-quad affine coordinate/gradient equations in real arithmetic inside each cell. This prevents repeat-boundary derivative spikes from selecting incorrect mip/filter footprints.

## Retained dev10 proof record
Dev10 retains one compact proof record only for each `repeatAwareFourVertexSafe` multi-face candidate:

- unsigned-short dev7 candidate index — 2 bytes;
- source geometric-corner order signature in source baked-vertex order — 1 byte;
- transport-obligation flags — 1 byte.

Exactly **4 logical bytes per transport record**.

The source corner-order signature is four 2-bit geometric corner IDs in source vertex order. A later geometry builder can emit the large quad in the same source order and keep the existing `0,1,2,0,2,3` index diagonal/winding contract rather than silently canonicalizing it.

Frozen flags:

- bit 0: explicit-gradient sampling required;
- bit 1: internal S reset boundary exists (`W > 1`);
- bit 2: internal T reset boundary exists (`H > 1`);
- bit 3: positive outer-edge endpoint policy required;
- bit 4: same blocks-atlas view/sampler required;
- bit 5: raster/reset-boundary visual obligation remains open;
- bits 6-7: reserved, must be zero.

Every multi-face record necessarily has at least one internal reset boundary and therefore retains the raster/visual obligation flag.

## What dev10 proves
For every dev9 four-vertex-safe candidate, dev10 must prove:

1. an exact dev9 descriptor exists for that candidate;
2. width/height are valid bounded integer extents and at least one exceeds one;
3. dev9 orientation is an affine square symmetry;
4. all four raw atlas bounds are finite and strictly ordered;
5. source baked vertex order contains each geometric face corner exactly once;
6. candidate-local outer corners select the exact dev9 source UV corner endpoints without normalization loss;
7. the affine interior mapping and explicit-gradient coefficients agree with the representative unit quad's UV-corner mapping;
8. exact color/light safety remains inherited from dev8;
9. material/sprite/layer/etc. identity remains inherited from dev6/dev7 and is never widened by state/block ID;
10. retained bytes equal `transportRecordCount * 4` and fingerprints/accounting are exact/deterministic.

`transportRecordCount` must equal dev9 `repeatAwareFourVertexSafe`. Zero remains a valid measured outcome; dev10 may not manufacture a useful subset.

## Explicitly open raster boundary
The half-open repeat rule chooses the positive-axis source cell at an exact internal integer reset boundary. The original repeated unit quads have a real primitive boundary there, so Vulkan edge ownership can choose one side's endpoint. A single large primitive removes that internal edge.

Dev10 therefore **does not claim pixel-identical behavior at fragments whose sample position lies exactly on an internal reset line**, nor does it claim T-junction closure. This is a named raster obligation, not a hidden approximation.

Before any geometry-changing promotion, a later P3.4 emission slice must:

- implement the frozen transport with the same atlas view/sampler and explicit gradients (or prove an equivalent representation);
- preserve source vertex order/diagonal;
- exercise internal repeat boundaries and section/rectangle T-junctions on real Vulkan hardware;
- receive renewed explicit human visual validation;
- add selective split/mitigation only if artifacts are observed or a concrete correctness case requires it, following D-0024.

P3.6 remains the broader T-junction policy milestone; dev10 does not falsely mark it complete.

## Dev10 runtime evidence gate
The integrated runtime gate will be `repeatAwareTransportEvidenceReady=true` and must sit strictly after `repeatAwareUvEvidenceReady=true`.

Required exact conditions:

- transport builds > 0 and >= completed worker jobs;
- source multi-face count equals dev9 source multi-face count;
- source four-vertex-safe count equals dev9 repeat-aware four-vertex-safe count;
- transport record count equals source four-vertex-safe count;
- unsafe count equals source multi-face minus transport record count;
- explicit-gradient-required count equals transport record count;
- same-atlas-sampler-required count equals transport record count;
- outer-edge-policy count equals transport record count;
- raster-boundary-obligation count equals transport record count for multi-face records;
- internal-S + internal-T accounting is directionally/record consistent;
- directional transport record counts and covered faces sum exactly;
- retained bytes equal `recordCount * 4`;
- scratch uses >= builds;
- primary proof audits == builds and all match;
- determinism audits > 0 and all match;
- all prior worker/lifecycle/staging/arena/resource gates remain clean.

The gate is evidence that the **no-emission transport proof is complete**, not authorization to call the render path complete.

## Non-negotiable flags
Dev10 keeps:

- `repeatAwareTransportSidecarIntegrated=true` after integration;
- `greedyRectangleGpuEmission=false`;
- `renderCorrectMergeKeyComplete=false`;
- `BakedSectionMesh` authoritative;
- `workerWorldReadsAfterCapture=0`;
- no new native Vulkan takeover.

## Promotion boundary
Dev10 itself does not change emitted geometry, so its promotion is governed by frozen CI/runtime proof gates. The subsequent geometry-changing P3.4 slice must freeze a separate emission contract and requires renewed explicit human visual validation before merge.