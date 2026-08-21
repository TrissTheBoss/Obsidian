# A-0075 - Phase 2 dev4 runtime + human visual success

Status: **SUCCESS / RUNTIME + HUMAN VISUAL VALIDATED / MERGE AUTHORIZED**

Date: 2026-08-21
Branch: `phase2/lighting-ao-correctness`
Validated package: `Obsidian-0.2.0-phase2-dev4-visual-retest.jar`
Validated behavior head before this evidence-only commit: `10e86d1a922186acc5b0297b9b619ca35f3ae396`
Reference runtime: Minecraft 26.2 / Fabric Loader 0.19.3 / Fabric API 0.158.0+26.2 / Java 25.0.1 / Vulkan / AMD Radeon RX 6800 XT / AMD proprietary driver 26.7.1.

## Human result

The first dev4 runtime was machine-clean but the exact coplanar overlay was visually hard to judge because vanilla and Obsidian depth-fought. A-0074 preserved that result and required a presentation-only retest.

The retest moved only the validation overlay faces outward by `1/512` block along their face normal. Lighting/AO values, UVs, material identity, indices, canonical oracle geometry, camera placement and lifetime semantics were unchanged.

User verdict after retest: **"yes it works, exact fine tuning can be done later."**

That is accepted as the P2.4 human visual gate. The presentation offset is validation-harness-only and does not become a production geometry rule.

## Runtime evidence

The retest loaded `obsidian 0.2.0-phase2-dev4` on the expected Vulkan backend and RX 6800 XT.

All six sustained visual passes completed and verified. Passes 1-5 sampled section `(63,5,6)` with:

- reference faces: `637`
- materialized/lit faces: `490`
- rejected material faces: `147`
- AO faces: `490`
- flat faces: `0`
- block-light range: `0..0`
- sky-light range: `0..15`
- vertices: `1960`
- indices: `2940`
- deterministic reference builds: `2`
- deterministic material captures: `2`
- deterministic lighting captures: `2`
- deterministic drawable builds: `2`
- `worldReadsAfterLightingCapture=0`
- `oneBlockHaloSufficient=true`
- `blockVertexFormat=true`
- `lightmapBound=true`
- `profilerOnlySubmissions=0`

The sixth pass sampled neighboring section `(64,5,6)` after movement and also verified:

- reference faces: `321`
- materialized/lit faces: `165`
- rejected material faces: `156`
- AO faces: `165`
- flat faces: `0`
- block-light range: `0..0`
- sky-light range: `15..15`
- vertices: `660`
- indices: `990`
- deterministic reference/material/lighting/drawable duplicates all `2`
- zero post-light-capture world reads
- one-block halo sufficient

This movement across a section boundary is useful evidence that the supported full-cube halo contract held at borders rather than only inside one fixed section.

## Final shutdown invariants

Final coordinator state:

- `lightingSectionResult=VERIFIED`
- `completedVisualPasses=6`
- `pipelineValid=true`
- `nativeGraphicsSeam=false`
- `indexedIndirect=true`
- `textured=true`
- `blockVertexFormat=true`
- `blocksAtlasBound=true`
- `lightmapBound=true`
- `worldReadsAfterLightingCapture=0`
- `oneBlockHaloSufficient=true`
- `profilerOnlySubmissions=0`
- staging submitted/reclaimed: `355760 / 355760`
- staging high-water: `66672`
- staging backpressure: `0`
- pending upload batches: `0`
- arena used bytes: `0`
- arena high-water: `66640`
- arena allocations/failures: `12 / 0`
- arena retired/reclaimed: `12 / 12`
- arena retirement backpressure: `0`
- stale handle rejections: `0`
- free spans: `1`
- largest free block: `4194304`
- fragmentation permille: `0`
- pending arena retirements: `0`
- indirect/deferred resources retired/released/pending: `6 / 6 / 0`
- process exit code: `0`

## Interpretation

P2.4 proved the conservative P2.3 full-cube material path can carry exact Minecraft 26.2 block/sky-light, directional shade and AO corner semantics into Obsidian-owned immutable data and the public BLOCK/lightmap graphics path while preserving deterministic builds, bounded resources, public indexed-indirect submission and completion-gated reclamation.

The sample contained no emitted block light (`0..0`), but sky light exercised both full-sky and gradient values (`0..15`), every accepted face exercised the AO path, and the exact runtime oracle used Minecraft's own `BlockModelLighter`. P2.4 therefore passes its intended correctness gate for the supported subset without claiming P2.5 broader block/model semantics.

Fine tuning of comparison presentation may be revisited later; it is not a blocker for P2.4 closure.

## Closure

The user explicitly authorized: **merge and continue to dev5**.

Before merge, run CI on the exact evidence-synchronized head. After merge, perform the normal Class-A status/roadmap sync and begin P2.5 from synchronized `main`.

This attempt is immutable once committed.
