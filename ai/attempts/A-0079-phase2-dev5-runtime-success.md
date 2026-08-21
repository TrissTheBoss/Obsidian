# A-0079 - Phase 2 dev5 reference runtime and human visual validation

Status: **SUCCESS / RUNTIME + HUMAN VISUAL VALIDATED / MERGE AUTHORIZED**

Date: 2026-08-21
Branch: `phase2/broader-opaque-cutout-semantics`
PR: #22
Version: `0.2.0-phase2-dev5`

## Reference runtime

- Windows 11
- Prism Launcher 10.0.5
- Minecraft 26.2
- Fabric Loader 0.19.3
- Fabric API 0.158.0+26.2
- Java 25.0.1
- AMD Radeon RX 6800 XT 16 GB
- AMD Vulkan driver 26.7.1 / Vulkan 1.4.315
- Vulkan backend

## Human visual result

The user reported: **"everything looks fine"** and explicitly instructed Obsidian to merge dev5 and continue with dev6.

This closes the human visual gate for the validation harness. Fine tuning remains eligible for later milestones, but no correctness defect was observed in accepted SOLID/CUTOUT geometry, cutout alpha, UV/tint/light/AO alignment, or camera-relative placement.

## Machine evidence

The complete Prism log proves the exact `obsidian 0.2.0-phase2-dev5` package loaded and attached to Vulkan on the RX 6800 XT.

All six sustained generalized comparison passes completed and verified. The sampled section was `(64,4,8)` with stable deterministic fingerprints across every pass.

Representative/final captured counts:

- `cubeReferenceFaces=247`
- `modelBlocksScanned=2440`
- `acceptedBlocks=321`
- `noVisibleBlocks=2119`
- `rejectedBlocks=0`
- `generalizedQuads=626`
- `solidQuads=321`
- `cutoutQuads=305`
- `materialCount=6`
- `tintedQuads=561`
- `blockLightRange=0..0`
- `skyLightRange=11..15`
- `vertices=2504 = 626 * 4`
- `indices=3756 = 626 * 6`
- `vertexBytes=70112`
- `indexBytes=15024`

Every pass reported:

- `deterministicCubeReferenceBuilds=2`
- `deterministicGeneralizedCaptures=2`
- `deterministicDrawableBuilds=2`
- `worldReadsAfterGeneralizedCapture=0`
- `cubeOraclePreserved=true`
- `oneBlockHaloSufficientForCapturedCullingLightSamples=true`
- `pipelineValid=true`
- `nativeGraphicsSeam=false`
- `indexedIndirect=true`
- `textured=true`
- `blockVertexFormat=true`
- `blocksAtlasBound=true`
- `lightmapBound=true`
- `solidPipeline=true`
- `cutoutPipeline=true`
- `cutoutAlphaThreshold=0.5`
- `profilerOnlySubmissions=0`

Each pass submitted 91 comparison draws / 182 indirect calls and completed completion-gated reclamation before re-arming.

Final shutdown after all six passes reported:

- `generalizedSectionResult=VERIFIED`
- `completedVisualPasses=6`
- `stagingSubmittedBytes=511056`
- `stagingReclaimedBytes=511056`
- `stagingBackpressureEvents=0`
- `pendingUploadBatches=0`
- `arenaUsedBytes=0`
- `arenaAllocations=12`
- `arenaAllocationFailures=0`
- `arenaRetired=12`
- `arenaReclaimed=12`
- `arenaRetirementBackpressureEvents=0`
- `arenaStaleHandleRejections=0`
- `arenaFreeSpans=1`
- `arenaLargestFree=4194304`
- `arenaFragmentationPermille=0`
- `pendingArenaRetirementBatches=0`
- `retiredResources=6`
- `releasedResources=6`
- `pendingRetirements=0`
- process exit code `0`.

## Gate result

P2.5/dev5 satisfies its exact API, deterministic capture/build, public SOLID/CUTOUT graphics, bounded lifetime, reference runtime and human visual gates.

The user had already granted standing merge authorization and reaffirmed it with the successful runtime result. PR #22 may be marked ready and merged after a final exact-head CI run that includes this immutable evidence commit and continuity update.

This attempt is immutable once committed.
