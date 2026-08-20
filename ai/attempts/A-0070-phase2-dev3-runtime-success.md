# A-0070 - Phase 2 dev3 runtime and human visual success

Date: 2026-08-21
Status: SUCCESS / RUNTIME + HUMAN VISUAL VALIDATED / MERGE PENDING
Milestone: Phase 2 P2.3 / 0.2.0-phase2-dev3
Branch: `phase2/material-texture-identity`
PR: #16

## Runtime target

Reference validation machine:

- Windows 11
- Prism Launcher 10.0.5
- Minecraft 26.2
- Fabric Loader 0.19.3
- Fabric API 0.158.0+26.2
- Java 25.0.1
- AMD Radeon RX 6800 XT 16 GiB
- Vulkan backend

The runtime loaded exactly `obsidian 0.2.0-phase2-dev3` on the expected AMD Vulkan device and completed the existing world-render hook path without crash.

## Human visual result

The tester reported no visible texture/material issues during the dev3 comparison:

- recognizable Minecraft textures were visible;
- no mirrored, rotated or stretched UV issue was observed;
- no world/camera alignment issue was observed while moving/turning;
- no obviously incorrect tint issue was observed;
- the known lighting/AO mismatch was not treated as a P2.3 failure because P2.4 is deliberately out of scope.

The tester also broke one block during the validation interval and observed the stale overlay disappear after roughly 0.3 seconds.

## Block-update observation

This short stale-overlay interval is a property of the temporary validation harness, not evidence of a P2.3 texture/material defect.

The log captures the world edit across the pass boundary:

- passes 1 through 5 sampled `interiorAir=4014`, `interiorSupported=82`, `referenceFaces=139`, `materializedFaces=88`;
- the sixth recapture sampled `interiorAir=4015`, `interiorSupported=81`, `referenceFaces=142`, `materializedFaces=87`;
- snapshot, reference, material and drawable fingerprints all changed on that sixth recapture;
- resource epoch remained stable, so the change was world/section content rather than a resource reload.

Each validation pass intentionally holds one immutable snapshot/materialized mesh for up to 90 comparison draws / about 0.75 seconds on this run, then completion-gates retirement and recaptures for the next pass. A block edit made during a pass can therefore remain visible until that pass ends. The tester-observed ~0.3 second delay is consistent with editing partway through such a pass.

This is acceptable for the bounded human-validation probe only. It is not a production renderer latency target. Event-driven block-update/neighbor invalidation and section rebuild scheduling remain P2.6 scope and must replace this validation-only recapture cadence before global terrain replacement.

No dev3 code change is justified solely to reduce this probe-only stale interval, because doing so would prematurely mix P2.6 lifecycle/rebuild semantics into the P2.3 material-identity milestone.

## Machine validation

The final run completed all six comparison passes.

Final/representative invariants:

- `completedVisualPasses=6`;
- duplicate reference builds deterministic;
- `deterministicMaterialCaptures=2`;
- `deterministicDrawableBuilds=2`;
- accepted materialized faces were nonzero;
- final pass: `referenceFaces=142`, `materializedFaces=87`, `rejectedMaterialFaces=55`;
- accounting: `87 + 55 = 142` reference faces;
- final vertices `348 = 87 * 4`;
- final indices `522 = 87 * 6`;
- rejection accounting remained explicit: final `rejectedDirectionalQuads=55`, with missing/general/layer/atlas/geometry/tint rejection counters zero;
- final `materialCount=2`;
- final `tintedFaces=78`, `tintWorldQueries=78`;
- `worldReadsAfterMaterialCapture=0`;
- `pipelineValid=true`;
- `nativeGraphicsSeam=false`;
- `indexedIndirect=true`;
- `textured=true`;
- `blocksAtlasBound=true`;
- `p2_4LightingAo=false` by design;
- comparison color scale remained 3/4;
- resource epoch checks completed for every comparison draw and the resource epoch remained stable;
- `profilerOnlySubmissions=0`;
- total arena allocations/retired/reclaimed `12/12/12`;
- arena allocation failures `0`;
- final arena used bytes `0`;
- one full `4194304` byte free span;
- arena fragmentation `0` permille;
- staging submitted/reclaimed `63360/63360` bytes;
- staging backpressure events `0`;
- indirect resources retired/released `6/6`;
- pending upload/arena/resource retirement counts all `0`;
- process exit code `0`.

World coordinates, material/rejection counts and fingerprints are terrain/resource dependent and are evidence from this run, not hard-coded success constants.

## Conclusion

P2.3's required texture/material identity gate passed on the reference runtime. The exact Minecraft 26.2 model/material extraction, baked UV mapping, tint capture, immutable post-capture mesh path, live blocks-atlas textured drawing, validated P2.2 placement and completion-gated lifetime all behaved correctly for the deliberately conservative SOLID subset.

P2.3 is runtime + human-visual validated. PR #16 remains unmerged until explicit merge authorization and the final exact-head CI/evidence synchronization gate.

The observed temporary stale overlay after a block edit is recorded for P2.6 lifecycle/rebuild work and does not block P2.3.
