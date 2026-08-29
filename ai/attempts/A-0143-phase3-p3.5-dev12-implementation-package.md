# A-0143 — Phase 3 P3.5 dev12 implementation/package checkpoint

**Date:** 2026-08-29  
**Result:** SUCCESS — implementation integrated, hosted package CI green, reference runtime pending.

## Scope
Implement the frozen A-0142 P3.5 border/halo correctness contract without changing dev11 emitted geometry, shaders, graphics pipelines, vertex/index formats or the native Vulkan seam.

## Canonical implementation/package head
- branch: `phase3/border-halo-correctness`
- exact package source head: `63c6727dd1ace2c231ece940d22faabda4c083e8`
- version: `0.3.0-phase3-dev12`

## Hosted CI
Workflow `33253251888`:
- Java 25 / Gradle 9.5.1 build: SUCCESS
- artifact upload: SUCCESS
- versioned release publishing: SKIPPED

Artifact:
- id `9715019632`
- wrapper `obsidian-7b28a94ac66e0fff697767f1ff819a75cc7657a4`
- wrapper size `595,406` bytes
- digest `sha256:68158d3b08fe0835c435d0149b961962a89e54e123064b9490501c19b89c1d2a`

## Canonical direct runtime JAR
- `Obsidian-0.3.0-phase3-dev12.jar`
- size **409,471 bytes**
- SHA-256 **`9915e12d31ec9bc52194af0875f40d03bcb0ff86b267c1dc96b43a78c777a5ce`**

Sources JAR:
- `Obsidian-0.3.0-phase3-dev12-sources.jar`
- size `211,668` bytes
- SHA-256 `25a91824b35b7079a69dc93751756cd51f3b1a77fdb2884c24b92ab6e81ad561`

Package inspection confirms:
- Fabric metadata version `0.3.0-phase3-dev12`;
- Minecraft `~26.2`;
- Java `>=25`;
- `BorderHaloCorrectnessProof` + reusable `BuildScratch` present;
- updated `SectionLifecycleEvents` present;
- P3.5 scene/coordinator/bootstrap classes present;
- dev11 repeat-aware geometry/shaders remain packaged and unchanged in scope.

## Implemented correctness boundary

### Dirty dependency closure
`SectionLifecycleEvents.sectionDirty(...)` now treats a section as relevant to the active 3x3 scene whenever it lies in the conservative dependency volume implied by one-block halos:
- X/Z offset within ±2 sections;
- Y offset within ±1 section.

The bridge distinguishes rendered-core vs halo-only dirty events and horizontal vs vertical halo dependencies. A pure deterministic self-test covers core, horizontal halo, vertical halo, combined halo and outside-domain cases.

Chunk load/unload relevance remains X/Z radius 2.

### Installed-record border proof
Every first LIVE install builds `BorderHaloCorrectnessProof` twice and requires deterministic equality before the install is counted.

Each proof:
- performs exactly 1,536 outward boundary checks;
- derives expected conservative visibility from immutable source classification plus immutable halo neighbor;
- requires `BinarySectionVisibility` exact agreement;
- requires independent `ReferenceFaceMesh` exact agreement;
- counts and fingerprints exact generalized baked quads whose source cells touch any section boundary;
- includes every such quad's exact packed light and ARGB vertex payload;
- retains summary counters/hashes only;
- does not allocate per boundary check.

### Shared-border proof
At every complete scene READY transition, every LIVE horizontally adjacent pair is checked across both independently captured halo/interior directions.

Each pair performs exactly 512 state-ID/classification comparisons. Any disagreement hard-fails the P3.5 scene.

### Generation/lifetime invariants
The existing scene event sequence and worker install generation checks remain authoritative. Newly relevant halo-only dirty events therefore invalidate queued/running/preinstall output through the already-proven stale-result path.

Mandatory unchanged invariants:
- `workerWorldReadsAfterCapture=0`;
- `synchronousSceneMeshBuilds=0`;
- render-thread capture/GPU ownership;
- bounded workers/staging/arena/resources;
- completion-gated reclamation.

## Runtime gate
New final flag:
- `borderHaloCorrectnessEvidenceReady=true`

It is layered on top of all prior dev11 gates including `repeatAwareGreedyEmissionEvidenceReady=true`.

Reference runtime must:
1. reach initial async 3x3 READY;
2. perform an ordinary block break/place rebuild and reach READY;
3. perform F3+T/resource reload and reach READY;
4. exit normally and provide the complete shutdown log.

No new human visual verdict is required because dev12 changes no emitted geometry, shader or pipeline semantics.

## Promotion boundary
PR #45 remains draft/unmerged until the complete reference runtime gate passes. Standing Phase 3 authorization applies after that gate closes. P3.6 broader T-junction policy remains outside dev12.