# A-0184 — Phase 3 P3.9 dev22 reference runtime did not arm

Date: 2026-08-30
Status: PARTIAL — non-decisive runtime; P3.9 measurement window never armed

## Objective

Run the canonical dev22 binary through the full frozen A-0159 closure workload so the fixed four-slice P3.9 experiment can be concluded by PASS or formal REJECT/DEFER before moving to production opaque/cutout terrain replacement.

## Runtime authority

Canonical binary remains `Obsidian-0.3.0-phase3-dev22.jar`:

- package implementation head `177081d5b8605439f66d70ffca481c0044e62add`
- package tree `9fadf0e62b7833f7676dc067e7b4cab40ae19805`
- Build `33279229989` SUCCESS
- JAR size `524,452` bytes
- SHA-256 `ec0574c7d24a521eed3de13b5c7efc23f54d501c6c8915c597a283f9296a3f27`

Reference runtime: Minecraft 26.2 / Fabric Loader 0.19.3 / Fabric API 0.158.0+26.2 / Java 25.0.1 / Vulkan / AMD Radeon RX 6800 XT / AMD proprietary 26.8.1.

## Decisive runtime result

The run was clean but never entered the benchmark/P3.9 measurement window:

- `partialRemeshWindowArmed=false`
- `partialRemeshCompletedEpisodes=0`
- `partialRemeshFallbackEpisodes=0`
- `partialRemeshObservedSamples=0`
- `partialRemeshRetainedSamples=0`
- `partialRemeshExperimentEvidenceReady=false`
- `meshingBenchmarkEvidenceReady=false`

Therefore this run is not valid evidence for PASS or REJECT/DEFER of A-0159 benefit thresholds.

## Exact blocking prerequisite

The final inherited proof collectors showed:

- P3.5 border/halo: `borderHaloCorrectnessEvidenceReady=true`, 385 proof records, exact shared-border accounting, `workerWorldReadsAfterCapture=0`, `synchronousSceneMeshBuilds=0`, `unsafeStaleSceneInstalls=0`.
- P3.6: `tJunctionPolicyEvidenceReady=false`, 385/385 deterministic proofs, 1,475 emitted candidates, 3,707 strict interior lattice points, but `strictTJunctionPoints=0` and `junctionBearingTransformProofRecords=0`; camera transform failures remained `0`.
- P3.7 proof contents themselves remained exact: 385/385 deterministic, missing/duplicate/optimized-without-reference/real-mismatch all `0`, fixture self-tests 385/385. The high-level `differentialCorrectnessEvidenceReady=false` was inherited from the unsatisfied P3.6 gate rather than a differential mismatch.

Source inspection confirms `FrameCoordinator.afterWorldRender(...)` arms the benchmark and partial-remesh windows only when the scene is LIVE, workers are idle, and `sceneProbe.differentialCorrectnessEvidenceReady()` is already true. `AsyncMultiSectionSceneProbe.tJunctionPolicyEvidenceReady()` requires `tJunctionStrictPoints > 0` and `junctionBearingTransformProofRecords > 0`. The selected dev22 scene therefore could not arm P3.9 even though all observed differential comparisons were exact.

The run also recorded `cameraRecenterEvents=0` and observed lifecycle reasons only `section-dirty|world-change|resource-reload`; a real scene recenter was not completed in this run.

## Lifetime/safety

Shutdown remained clean:

- workers/staging/arena/resources clean
- scene worker queue rejections `0`
- worker failed jobs `0`
- worker shutdown join failures `0`
- unsafe stale scene installs `0`
- staging submitted/reclaimed bytes equal
- arena used bytes `0`, allocations/retired/reclaimed balanced
- retired/released resources balanced
- process exit code `0`.

## Classification

This is `PARTIAL`, specifically **NOT ARMED / NON-DECISIVE**. It is not a dev22 correctness failure and it does not authorize threshold changes. No new binary or source correction is justified.

The fixed four-slice strategy remains awaiting one valid full-volume dev22 closure run.

## Required rerun

Use the exact same canonical dev22 JAR.

Before beginning closure edits, deliberately establish an inherited P3.6 T-junction witness in a geometrically richer section. A previously successful reference scene was centered near section `(69,4,1)`; in world coordinates that corresponds approximately to X `1104..1119`, Y `64..79`, Z `16..31`. The current non-arming run began around section `(63,5,0)` and produced no strict T-junction witness.

1. Enter the world and move to/near a section with visible nontrivial terrain geometry, preferably the previously successful `(69,4,1)` area if the same world still contains it.
2. Wait for a READY rebuild and inspect the log. Do not begin the 32-episode workload until both lines appear:
   - `Phase 3 dev18 P3.9 measured benchmark window armed...`
   - `Phase 3 dev18 P3.9 pending-coalescing shadow partial-remesh window armed...`
3. Once armed, perform >=16 separate safe-interior one-slice edits with READY recovery.
4. Perform >=8 separate safe-interior two-slice boundary edits with READY recovery.
5. Perform >=1 quick same-section 3–5 edit burst for coalescing.
6. Perform F3+T and recover READY.
7. Move more than the 3x3 scene radius so `cameraRecenterEvents` increases by at least one, then recover READY.
8. Continue safe-interior localized edits until completed episodes >=32.
9. Quit normally and return the complete log.

## Project direction

This does not reopen P3.9 research. The next valid armed/full-volume dev22 run remains the final normal P3.9 decision run. PASS or formal benefit REJECT/DEFER moves immediately to full production opaque/cutout terrain rendering replacement. Partial GPU patching is not a prerequisite.