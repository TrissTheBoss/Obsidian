# A-0076 - Phase 2 dev5 broader opaque/cutout block semantics plan

Status: **ACTIVE / EXACT MINECRAFT 26.2 API GROUNDING REQUIRED BEFORE IMPLEMENTATION**

Date: 2026-08-21
Branch: `phase2/broader-opaque-cutout-semantics`
Version: `0.2.0-phase2-dev5`
Base: synchronized `main` after P2.4 closing merge `fa0d40182cd0bc29a526b28a8b2b3b43fc8fc8ba` and Class-A status sync `a6d7d2ff96948910e22b2ab4e3e5212408ef97c2`.

## Goal

P2.5 broadens Phase 2 terrain semantics beyond the conservative canonical SOLID full-cube subset while preserving every proven P2.1-P2.4 correctness/lifetime contract.

Canonical roadmap scope:

- ordinary full cubes remain supported;
- axis-aligned simple model cases;
- cutout vegetation/model classes where architecture allows;
- tinted blocks;
- biome-dependent color inputs;
- selected non-full model cases only after exact semantics are understood;
- unsupported cases remain explicit and measurable.

## Non-negotiable rule

Do not turn unsupported geometry into an approximate cube or otherwise inflate coverage by rendering the wrong thing.

The current `ReferenceFaceMesh` remains the permanent P2.1 cube-face oracle. P2.5 must not mutate its meaning merely to support arbitrary baked geometry. If broader geometry requires a companion immutable reference-quad representation, create one with its own deterministic identity and keep the cube oracle independent.

## Exact Minecraft 26.2 inspection required first

Inspect Loom-resolved APIs/bytecode for at least:

1. `BlockStateModel` / `BlockStateModelPart` selection and `getQuads(Direction)` versus `getQuads(null)` general/unculled geometry.
2. Multiple model parts and multiple quads per cull direction/general bucket.
3. `BakedQuad` arbitrary vertex positions, UVs, direction, material info, shade/emission/flags and any winding/recalculation behavior.
4. Vanilla section compilation ordering for directional and general quads, including exact face-culling conditions and shape tests.
5. Render-layer selection for SOLID versus CUTOUT and the exact public Blaze3D cutout pipeline/state contract.
6. Whether SOLID and CUTOUT share `DefaultVertexFormat.BLOCK`, blocks atlas and level lightmap bindings.
7. Alpha-test/discard/cull/depth/write semantics for CUTOUT.
8. Tint and biome-color behavior per arbitrary baked quad.
9. `ModelBlockRenderer` / `BlockModelLighter` handling of non-full/arbitrary quad positions, AO face selection, shape flags and per-vertex light/AO mapping.
10. Whether any broader supported geometry can sample beyond the P2.4 one-block halo.
11. Crossed/vegetation models: whether common plant geometry is represented through general quads, how layers/tint/light are assigned, and whether it is a sensible first dev5 proof.
12. How multiple layers/quad groups should be drawn: separate SOLID/CUTOUT ranges or commands while retaining bounded public indexed-indirect submission.

No remembered pre-26.2 API assumptions are acceptable.

## Expected architectural direction, subject to inspection

The likely shape is a new immutable renderer-owned broader quad capture that freezes exact baked geometry rather than trying to force arbitrary quads through the canonical cube-face representation.

A generalized immutable quad record may need:

- source block local/world identity;
- four section-local positions;
- four baked UVs;
- cull direction/general-quad identity;
- exact material/sprite/render-layer identity;
- tint index and resolved tint color;
- shade/emission/material flags;
- stable renderer material ID;
- exact per-vertex light/AO results or a stable link to a generalized lighting capture.

This is only a hypothesis until exact 26.2 inspection confirms the right boundary.

## Preferred first proof

Choose the smallest semantically meaningful broader subset after inspection. Preference order:

1. exact axis-aligned non-full/simple baked quads if their culling/light semantics are clean;
2. exact CUTOUT quads using the public cutout pipeline;
3. common crossed vegetation only if general-quad semantics can be captured and lit exactly without pulling P2.6 lifecycle or Phase 6 translucency into scope.

It is acceptable for dev5 to support fewer categories than the whole P2.5 roadmap if the first exact proof establishes the correct generalized architecture. It is not acceptable to approximate unsupported categories.

## Required invariants

- render-thread-only live model/world/tint/light capture;
- immutable primitive renderer-owned data after capture;
- zero live world/model/light/resource reads during pure mesh construction;
- deterministic duplicate capture/build checks;
- explicit accepted/rejected counts by reason and render layer;
- P2.1 cube oracle retained unchanged;
- P2.3 exact sprite/UV/material/tint semantics retained for overlapping cases;
- P2.4 exact light/AO semantics retained for overlapping cases;
- public Blaze3D graphics first;
- bounded staging/arena use;
- completion-gated geometry/command lifetime;
- no profiler-only submissions;
- `nativeGraphicsSeam=false` unless an exact public-capability gap is demonstrated and separately justified.

## Deliberate boundary

P2.5 does not claim:

- translucent/fluid terrain; Phase 6;
- event-driven block/light/resource invalidation and production rebuild lifecycle; P2.6;
- persistent multi-section scene ownership; P2.7;
- production greedy meshing; Phase 3;
- global vanilla terrain replacement;
- production performance/scale claims.

## Validation gate

Before merge, dev5 must have:

- exact Minecraft 26.2 inspection evidence recorded immutably;
- exact-head Java 25 / Gradle 9.5.1 CI success;
- deterministic generalized geometry/material/light captures/builds;
- explicit per-layer/unsupported accounting;
- public SOLID/CUTOUT draw path as applicable;
- correct texture, tint, lighting and geometry placement in a human comparison;
- sustained reference RX 6800 XT runtime passes;
- full staging/arena/indirect reclamation;
- process exit 0.

PR remains draft until that gate passes. Dev5 must not be merged without new explicit user authorization.

This attempt is immutable once committed.
