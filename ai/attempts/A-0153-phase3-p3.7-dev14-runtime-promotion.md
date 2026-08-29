# A-0153 - P3.7 dev14 reference runtime promotion

**Date:** 2026-08-29  
**Objective:** Re-run the exact canonical `0.3.0-phase3-dev14` package after A-0152 and close the one remaining frozen A-0150 runtime obligation: actual async scene recenter/boundary movement followed by READY, while preserving the complete differential/lifetime gate.  
**Result:** `SUCCESS` — the second reference runtime closes the complete A-0150/A-0151 P3.7 contract. P3.7 is promotion-ready.

## Exact package under test

- branch: `phase3/differential-correctness`
- draft PR before promotion: #49
- implementation head: `83388481b7a0fd566daf5cc39fd8713945df912c`
- canonical JAR: `Obsidian-0.3.0-phase3-dev14.jar`
- size: `441,563` bytes
- SHA-256: `9d79b1de179768d5b872178564f708b42dab0d9cc8e99a0dd8f80bf10336bc39`
- implementation/package workflow `33264171457`: Java 25 / Gradle 9.5.1 SUCCESS, artifact upload SUCCESS, release SKIPPED
- synchronized handoff workflow `33264260675`: SUCCESS
- latest pre-promotion branch-head Build workflow `33264593812` on `9f23ff4b46148ff0961aa95c31e1777b4cf18fff`: Java 25 / Gradle 9.5.1 SUCCESS, artifact upload SUCCESS, release SKIPPED

Reference runtime: Windows 11 / AMD Radeon RX 6800 XT / Vulkan / AMD proprietary driver 26.8.1 / Java 25.0.1 / Minecraft 26.2 / Fabric Loader 0.19.3 / Prism Launcher 10.0.5.

## Frozen runtime exercise closure

A-0152 had already proven the automated differential/lifetime gate but correctly remained PARTIAL because no `scene-recenter` event occurred. The exact same package was rerun without source, renderer, oracle or package changes.

The second run closes the missing exercise:

- `cameraRecenterEvents=5`;
- final observed invalidation reasons include `scene-recenter` alongside `section-dirty`, `world-change` and `resource-reload`;
- multiple async scene centers were rebound during movement;
- READY returned after recenter activity;
- resource reload events: `2`;
- ordinary section-dirty rebuild activity remained present;
- process exited normally.

Therefore the complete A-0150 human-directed runtime exercise is now evidenced rather than inferred from the final automated flag.

## Differential correctness gate — PASS

Final coordinator evidence:

- `repeatAwareGreedyEmissionEvidenceReady=true`;
- `borderHaloCorrectnessEvidenceReady=true`;
- `tJunctionPolicyEvidenceReady=true`;
- `differentialCorrectnessEvidenceReady=true`;
- `productionWorkerIntegrationReady=true`;
- `hardFailure=false`;
- `workerWorldReadsAfterCapture=0`;
- `synchronousSceneMeshBuilds=0`;
- `unsafeStaleSceneInstalls=0`;
- `droppedLifecycleEvents=0`;
- dev14 differential geometry/shader/pipeline changed flags all `false`.

Installed differential evidence:

- proof records: `238`;
- deterministic audits/matches: `238 / 238`;
- independent reference faces: `79,754`;
- reference mapped / unmapped / ambiguous: `59,933 / 0 / 19,821`;
- complete source baked quads: `165,638`;
- passthrough identities: `154,306`;
- real merged candidates: `4,964`;
- expanded merged source faces: `11,332`;
- material checks/matches: `11,332 / 11,332`;
- direction checks/matches: `11,332 / 11,332`;
- canonical geometry/corner checks/matches: `11,332 / 11,332`;
- raw UV checks/matches: `45,328 / 45,328`;
- exact ARGB checks/matches: `45,328 / 45,328`;
- packed-light checks/matches: `45,328 / 45,328`;
- missing source coverage: `0`;
- duplicate source coverage: `0`;
- optimized canonical mappings without independent reference: `0`;
- real differential mismatches: `0`;
- deterministic fixture self-tests: `238 / 238`.

This is not passthrough-only evidence: the actual retained optimized path contains thousands of merged candidates and more than eleven thousand expanded merged source faces.

## Worker / lifecycle / lifetime closure — PASS

- scene workers submitted/completed: `244 / 244`;
- cancellations: `0`;
- cancellation requests: `0`;
- queue-full rejections: `0`;
- worker failures: `0`;
- shutdown join failures: `0`;
- stale result discards: `6`;
- preinstall invalidations: `6`;
- unsafe stale installs: `0`;
- workers clean: `true`;
- staging clean: `true`;
- staging submitted/reclaimed: `22,315,152 / 22,315,152` bytes;
- pending upload batches: `0`;
- arena clean: `true`;
- arena allocations/retired/reclaimed: `714 / 714 / 714`;
- arena used bytes: `0`;
- arena allocation failures: `0`;
- resources clean: `true`;
- retired/released resources: `238 / 238`;
- pending retirements: `0`;
- normal process exit code: `0`.

## Visual gate

No new human visual verdict is required. A-0150/A-0151 froze dev14 as non-render-changing, and the runtime confirms the differential geometry/shader/pipeline change flags remain false. Existing dev11 and A-0149 visual evidence therefore remains applicable to the unchanged emitted rendering path.

## Engineering conclusion

P3.7's permanent differential framework is now validated on representative real immutable snapshots and lifecycle transitions. The optimized greedy output reconstructs complete frozen source coverage exactly once, independently agrees with canonical reference topology where mapped, and preserves exact material/direction/canonical geometry/raw UV/ARGB/packed-light semantics across real merged coverage. Failure-fixture diagnostics are deterministic, worker ownership remains clean, stale generations do not install, and resource lifetime closes exactly.

**P3.7 may be promoted COMPLETE. Activate P3.8 meshing benchmarks next.**

Do not weaken or remove the differential oracle after promotion. P3.8 must measure representative meshing cost/percentiles and workload behavior without consuming P3.9 partial-remeshing scope. Freeze the P3.8 benchmark/workload contract in a new immutable attempt before implementation.