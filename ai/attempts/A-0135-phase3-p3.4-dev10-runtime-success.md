# A-0135 — Phase 3 P3.4 dev10 reference runtime SUCCESS

Date: 2026-08-28
Status: **SUCCESS**
Branch: `phase3/repeat-aware-transport-proof`
Version: `0.3.0-phase3-dev10`

## Purpose

Close the frozen A-0133 dev10 repeat-aware transport/sampling proof on the reference Vulkan machine. Dev10 remains proof/sidecar-only; it does not replace the authoritative `BakedSectionMesh` drawable.

## Reference runtime

- Prism Launcher 10.0.5
- Minecraft 26.2
- Fabric Loader 0.19.3
- Fabric API 0.158.0+26.2
- Java 25.0.1
- Windows 11
- AMD Radeon RX 6800 XT
- AMD Vulkan driver 1.4.315 / 26.8.1
- Runtime package: `Obsidian-0.3.0-phase3-dev10.jar`
- Canonical package SHA-256: `f37531a48608d6a2e0c0143a7ef72dc6d0c8533f4871d21137ea85a69a8feaf9`

## Final gate result

The final shutdown line reports:

- `phase3GateReady=true`
- `schedulerEvidenceReady=true`
- `binaryVisibilityEvidenceReady=true`
- `greedyRectangleEvidenceReady=true`
- `renderMergeKeyEvidenceReady=true`
- `renderMergeCandidateEvidenceReady=true`
- `ordinaryQuadEmissionSafetyEvidenceReady=true`
- `repeatAwareUvEvidenceReady=true`
- `repeatAwareTransportEvidenceReady=true`
- `productionWorkerIntegrationReady=true`
- `hardFailure=false`
- `productionSceneInstallStillSynchronous=false`
- `productionWorkerSceneIntegration=true`
- `renderThreadCaptureOwnership=true`
- `renderThreadGpuOwnership=true`
- `workerWorldReadsAfterCapture=0`
- `binaryVisibilitySidecarIntegrated=true`
- `greedyRectangleSidecarIntegrated=true`
- `renderMergeKeySidecarIntegrated=true`
- `renderMergeCandidateSidecarIntegrated=true`
- `ordinaryQuadEmissionSafetySidecarIntegrated=true`
- `repeatAwareUvDescriptorSidecarIntegrated=true`
- `repeatAwareTransportSidecarIntegrated=true`
- `greedyRectangleGpuEmission=false`
- `renderCorrectMergeKeyComplete=false`
- `synchronousSceneMeshBuilds=0`

`phase2ChunkLifecycleEvidenceReady=false` and `fixedAnchorReturnSceneReady=false` are expected/nonblocking because A-0101 already permanently closed the old fixed-target unload/return lifecycle proof.

## Worker / scheduler evidence

- submitted / started / completed: `92 / 92 / 92`
- cancelled / cancellation requests: `0 / 0`
- stolen: `69`
- queue-full rejections: `0`
- failed jobs: `0`
- shutdown join failures: `0`
- HIGH / NORMAL / LOW submitted: `11 / 41 / 40`
- HIGH / NORMAL / LOW completed: `11 / 41 / 40`
- worker determinism: `4 / 4`
- output: `80,435` quads, `9,008,720` vertex bytes, `1,930,440` index bytes

## P3.2 visibility

- builds: `92`
- visible faces: `43,967`
- W/E/D/U/N/S: `3,354 / 4,976 / 3,293 / 18,719 / 7,146 / 6,479`
- retained: `282,624 = 92 * 3,072` bytes
- scratch uses: `96`
- determinism: `4 / 4`
- independent reference: `4 / 4`

## P3.3 topology rectangles

- builds: `92`
- rectangles: `17,752`
- covered faces: `43,967` exact
- faces saved: `26,215`
- reduction: `59.6%`
- retained: `71,008 = 17,752 * 4` bytes
- primary coverage: `92 / 92`
- determinism: `4 / 4`
- independent reference: `4 / 4`

## Dev6 canonical render keys

- builds: `92`
- visible / eligible / unmapped / ambiguous: `43,967 / 35,380 / 0 / 8,587`
- recognized canonical baked quads: `52,554`
- ignored noncanonical baked quads: `27,881`
- same / different / ineligible adjacencies: `4,065 / 31,815 / 5,734`
- retained: `4,521,984 = 92 * 49,152` bytes
- determinism: `4 / 4`

## Dev7 render-key-aware candidates

- builds: `92`
- candidates: `32,129`
- covered eligible faces: `35,380` exact
- passthrough canonical faces: `8,587` exact
- singleton / multi-face: `29,900 / 2,229`
- faces saved: `3,251`
- reduction: `9.1%`
- retained: `192,774 = 32,129 * 6` bytes
- primary coverage: `92 / 92`
- determinism: `4 / 4`

## Dev8 ordinary four-vertex safety

- builds: `92`
- candidates classified: `32,129`
- singleton / multi-face: `29,900 / 2,229`
- color safe / unsafe multi-face: `2,219 / 10`
- light safe / unsafe multi-face: `2,229 / 0`
- ordinary atlas-UV safe / unsafe multi-face: `0 / 2,229`
- ordinary safe / unsafe multi-face: `0 / 2,229`
- repeat-aware required: `2,229`
- retained: `32,129 = 32,129 * 1` bytes
- primary classification: `92 / 92`
- determinism: `4 / 4`

## Dev9 repeat-aware UV descriptors

- builds: `92`
- source multi-face: `2,229`
- representable / unrepresentable: `2,229 / 0` = **100% representable**
- repeat-aware four-vertex safe / unsafe: `2,219 / 10`
- safe covered faces: `5,460`
- safe faces saved: `3,241`
- retained: `42,351 = 2,229 * 19` bytes
- primary classification: `92 / 92`
- determinism: `4 / 4`

The ten unsafe candidates are exactly the dev8 color-interpolation failures; light and repeat-aware UV representation are not blockers in this runtime set.

## Dev10 repeat-aware transport proof

- builds: `92`
- source multi-face: `2,229`
- source representable: `2,229`
- source four-vertex-safe: `2,219`
- transport records: `2,219` exact
- transport unsafe: `10`
- covered faces: `5,460`
- faces saved: `3,241`
- reduction over dev6 eligible faces: `9.1%`
- explicit-gradient-required: `2,219`
- internal S-reset: `900`
- internal T-reset: `1,409`
- internal both-reset: `90`
- internal reset union: `2,219`
- outer-edge-policy-required: `2,219`
- same-atlas-sampler-required: `2,219`
- raster-boundary-review-required: `2,219`
- `repeatAwareTransportBoundaryRasterObligationOpen=true`
- retained: `8,876 = 2,219 * 4` bytes
- primary proof audits / matches: `92 / 92`
- determinism audits / matches: `4 / 4`

Per direction transport records W/E/D/U/N/S:
`120 / 142 / 20 / 1,047 / 500 / 390` = `2,219` exact.

Per direction covered faces W/E/D/U/N/S:
`270 / 324 / 40 / 2,226 / 1,540 / 1,060` = `5,460` exact.

Per direction faces saved W/E/D/U/N/S:
`150 / 182 / 20 / 1,179 / 1,040 / 670` = `3,241` exact.

The open raster-boundary obligation is **expected and non-failing** in dev10. It records the exact later geometry-changing obligation; it is not evidence that internal repeat-line primitive-edge ownership or T-junction behavior has already been proven.

## Scene / lifetime closure

- scene worker submitted / completed: `92 / 92`
- stale discards / preinstall invalidations: `2 / 2`
- scene installs / record installs: `90 / 90`
- queue rejections: `0`
- READY transitions: `10`
- rebuilds: `9`
- dirty events: `662`
- resource reload events: `1`
- dropped lifecycle events: `0`
- unsafe stale installs: `0`
- workers clean: `true`
- staging clean: `true`
- arena clean: `true`
- resources clean: `true`
- staging submitted / reclaimed: `10,773,984 / 10,773,984`
- arena allocations / retired / reclaimed: `180 / 180 / 180`
- arena used bytes: `0`
- resources retired / released: `90 / 90`
- pending retirements: `0`
- normal render-thread `Stopping!`
- Prism: `Process exited with code 0`

## Promotion conclusion

**SUCCESS.** A-0133's frozen dev10 runtime contract is satisfied with no gate waived.

Dev10 proves the no-emission transport representation and exact accounting for the dev9-safe set. It does **not** close the internal repeat-line raster ownership / rectangle and section T-junction obligation. `BakedSectionMesh` remains authoritative and GPU greedy rectangle emission remains disabled.

Because dev10 changes no drawable geometry, no new human visual verdict is required for dev10 promotion. Any next geometry-changing P3.4 slice must freeze its own emission contract and receive renewed explicit human visual validation before promotion.
