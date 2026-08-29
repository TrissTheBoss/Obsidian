# A-0152 - P3.7 dev14 reference runtime PARTIAL: automated differential gate PASS, recenter exercise missing

**Date:** 2026-08-29  
**Objective:** Evaluate the first `0.3.0-phase3-dev14` reference runtime against the complete frozen A-0150/A-0151 P3.7 promotion contract.  
**Result:** `PARTIAL` — the automated differential/correctness/lifetime gate passed cleanly, but the required section-boundary/recenter runtime exercise was not evidenced. No source or package defect is identified by this run.

## Exact package under test

- branch: `phase3/differential-correctness`
- draft PR: #49
- implementation head: `83388481b7a0fd566daf5cc39fd8713945df912c`
- synchronized runtime-handoff head before this attempt: `47cf9386ca7cde81f02a656692b697ec5bcfc4bf`
- JAR: `Obsidian-0.3.0-phase3-dev14.jar`
- size: `441,563` bytes
- SHA-256: `9d79b1de179768d5b872178564f708b42dab0d9cc8e99a0dd8f80bf10336bc39`
- implementation/package workflow `33264171457`: Java 25 / Gradle 9.5.1 SUCCESS, artifact upload SUCCESS, release SKIPPED
- synchronized handoff workflow `33264260675`: Java 25 / Gradle 9.5.1 SUCCESS, artifact upload SUCCESS, release SKIPPED

Reference machine/runtime remained Windows 11 / AMD Radeon RX 6800 XT / Vulkan / AMD proprietary driver 26.8.1 / Java 25.0.1 / Minecraft 26.2 / Fabric Loader 0.19.3 / Prism Launcher 10.0.5.

## Automated P3.7 result — PASS

The final runtime coordinator reported every inherited gate through P3.6 true and `differentialCorrectnessEvidenceReady=true` with `hardFailure=false`, `productionWorkerIntegrationReady=true`, `workerWorldReadsAfterCapture=0`, `synchronousSceneMeshBuilds=0`, and no renderer-semantic change.

Differential proof evidence:

- installed proof records: `318`;
- deterministic audits/matches: `318 / 318`;
- independent reference faces: `94,732`;
- reference mapped / unmapped / ambiguous: `67,161 / 0 / 27,571`;
- complete source baked quads checked: `229,831`;
- passthrough source identities: `216,457`;
- actual merged candidates: `5,868`;
- expanded merged source faces: `13,374`;
- material checks/matches: `13,374 / 13,374`;
- direction checks/matches: `13,374 / 13,374`;
- canonical geometry/corner checks/matches: `13,374 / 13,374`;
- raw UV checks/matches: `53,496 / 53,496`;
- exact ARGB checks/matches: `53,496 / 53,496`;
- packed-light checks/matches: `53,496 / 53,496`;
- missing source coverage: `0`;
- duplicate source coverage: `0`;
- optimized canonical mappings without independent reference: `0`;
- real differential mismatches: `0`;
- deterministic fixture self-tests: `318 / 318`.

This is a real merged-path run rather than passthrough-only evidence.

Inherited P3.5/P3.6 evidence also remained exact, including `borderHaloCorrectnessEvidenceReady=true`, `tJunctionPolicyEvidenceReady=true`, T-junction proof determinism `318 / 318`, strict T-junction points `2,521`, camera-relative transform proof records `318`, junction-bearing transform records `286`, and transform failures `0`.

## Worker / lifecycle / lifetime closure — PASS

- scene worker submitted/completed: `323 / 323`;
- worker cancelled / cancellation requests / queue-full rejections / failures / shutdown join failures: all `0`;
- stale result discards: `5`;
- preinstall invalidations: `5`;
- unsafe stale installs: `0`;
- scene record installs: `318`;
- scene READY transitions: `35`;
- scene rebuilds: `34`;
- resource reload events: `2`;
- dropped lifecycle events: `0`;
- workers/staging/arena/resources clean: all `true`;
- staging submitted/reclaimed: `31,012,744 / 31,012,744` bytes;
- pending upload batches: `0`;
- arena allocations/retired/reclaimed: `954 / 954 / 954`;
- arena used bytes: `0`;
- arena allocation failures: `0`;
- retired/released resources: `318 / 318`;
- pending retirements: `0`;
- normal process exit code: `0`.

The run exercised substantial section-dirty/rebuild activity and the required resource reload path. The automated proof/lifetime result itself is fully clean.

## Promotion blocker — required recenter/boundary exercise not evidenced

A-0150 explicitly requires the reference runtime to exercise at least initial READY, an ordinary block rebuild, resource reload, **and scene recenter/boundary movement** so multiple snapshots/resource epochs/generations are covered. A-0151 and the runtime instruction repeat the same requirement: section-boundary/recenter movement before normal exit.

This run does **not** contain that evidence:

- `cameraRecenterEvents=0`;
- final `observedReasons=section-dirty|world-change|resource-reload`;
- `scene-recenter` is absent from the observed reason set;
- the scene center remains the initially bound `(68,4,-3)` throughout READY snapshots before shutdown.

Therefore the complete frozen runtime exercise did not close even though the software's final `differentialCorrectnessEvidenceReady` flag is true. The final automated flag intentionally does not encode every human-directed exercise precondition; continuity must not reinterpret A-0150 after the run.

**No waiver is allowed. P3.7 remains ACTIVE and PR #49 remains draft.**

## Required rerun

Use the exact same canonical dev14 JAR; no code/package change is justified by this attempt.

For one coherent reference run:

1. wait for the dev14 differential gate to arm and for the scene to reach READY;
2. perform an ordinary block break/place rebuild and let READY return;
3. perform F3+T/resource reload and let READY return;
4. move far enough across section boundaries for the async scene center actually to rebind/recenter; verify the log emits a `scene-recenter` invalidation/rebind and let READY return afterward;
5. exit normally and return the complete log.

Promotion requires the same clean A-0150 differential/lifetime evidence plus nonzero recenter evidence (`cameraRecenterEvents > 0` / `scene-recenter` observed) in that run.

No new visual verdict is required unless an unexpected rendering change is observed.

## Engineering conclusion

This attempt increases confidence in the dev14 differential framework: the optimized source stream reconstructed exactly across 318 installed records and 229,831 frozen source quads with zero real mismatch. The only open obligation is runtime exercise coverage, not a detected algorithmic/rendering defect. Do not change the oracle, proof, renderer, or package in response to this PARTIAL result.