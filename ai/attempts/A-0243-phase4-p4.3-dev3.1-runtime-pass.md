# A-0243 - P4.3 dev3.1 corrected runtime PASS

**Date:** 2026-10-03  
**Status:** `SUCCESS / P4.3 RUNTIME GATES CLOSED`  
**Milestone:** Phase 4 P4.3 hierarchy-fed fine section visibility  
**Version:** `0.4.0-phase4-dev3.1`  
**Exact source:** `3b38e63af1153d8ede4955da6cfc087cda5de423`  
**Tester verdict:** explicit human **Visual PASS**

## Package authority

Final-head CI:

- Build #850 / run `37078070257` — SUCCESS;
- Context Governor #71 / run `37078070123` — SUCCESS.

Runtime JAR:

- `Obsidian-0.4.0-phase4-dev3.1.jar`;
- size: **529,595 bytes**;
- SHA-256: **`076dfdadea03d51fd46ea6804f492f73691c493a4c02cf8a3c84d8404f4ff6ef`**.

Immutable publication:

- Publish Preview #16 / run `37078371213` — SUCCESS;
- GitHub release ID `402223904`;
- tag `v0.4.0-phase4-dev3.1`;
- exact release target `3b38e63af1153d8ede4955da6cfc087cda5de423`;
- Modrinth mirror job `111073415030` — SUCCESS;
- Modrinth version ID `ImfQC3L5`.

## Runtime identity

The supplied Prism Launcher log identifies:

- Minecraft 26.2;
- Fabric Loader 0.19.3;
- Java 25.0.1;
- Obsidian `0.4.0-phase4-dev3.1`;
- AMD Radeon RX 6800 XT / Vulkan;
- render distance **32**;
- explicit human **Visual PASS**;
- normal process exit code 0.

## P4.3 closure evidence

Final P4.3 telemetry:

- samples started: **2,747**;
- samples completed: **2,671**;
- stale-aborted samples: 75;
- deferred samples: 5,052;
- candidate build probes: 26,049,120;
- candidate build frames: 5,052;
- snapshot lookup failures: **0**;
- missing fine identities: **0**;
- unexpected fine identities: **0**;
- duplicate fine identities: **0**;
- `gpuHierarchicalFineFalseCullCount=0`;
- cumulative safe-extra coarse columns: **3**;
- cumulative safe-extra lineage fine identities: **1**;
- readback pending high-water: 1;
- `hardFailure=false`;
- `abandonedForDeviceShutdown=false`;
- `cameraOnlyFullSectionScan=false`;
- `productionDrawOwnershipChanged=false`;
- `nativeGraphicsExpansion=false`;
- `commandCompactionEnabled=false`;
- `temporalVisibilityEnabled=false`;
- `hizEnabled=false`.

The corrected package therefore reproduced the previously failing conservative lineage condition **non-zero** while keeping every true fine mismatch counter at zero.

The frozen accounting rule held: safe-extra lineage is separated from true fine mismatch accounting rather than hidden or discarded.

Representative steady-state samples continued to reduce roughly 32k-35k flat live sections to roughly 9k-14k hierarchy-fed fine candidates while exact fine-visible identity agreement remained intact.

## P4.2 inherited coarse gate

Final P4.2 evidence at render distance 32:

- live columns: 3,666;
- high-water columns: 3,725;
- live sections / column membership: 32,320 / 32,320;
- capacity failures: 0;
- mutation failures: 0;
- hierarchy audit runs/failures: 159 / 0;
- samples completed: 7,799;
- missing visible columns: 0;
- duplicate visible columns: 0;
- `gpuColumnFalseCullCount=0`;
- conservative unexpected visible columns: 8 cumulative;
- `hardFailure=false`;
- `cameraOnlyFullHierarchyScan=false`;
- ownership/native/scope flags unchanged.

## P4.1 inherited fine control

Final P4.1 evidence:

- live/high-water sections: 32,320 / 34,861;
- capacity failures: 0;
- lifecycle overflow: 0;
- samples completed/exact: 498 / 498;
- missing visible: 0;
- unexpected visible: 0;
- duplicate visible: 0;
- GPU false culls: 0;
- `hardFailure=false`;
- `cameraOnlyFullSceneScan=false`;
- production/native ownership unchanged.

## Production/lifetime inheritance

P3.10 remained coherent:

- duplicate claims: 0;
- claim overflows: 0;
- stale-plan failures: 0;
- execution without claim: 0;
- execution revalidation failures: 0;
- `suppressionExecutionAccountingCoherent=true`;
- `productionCoordinatesExact=true`;
- `productionExactColor=true`;
- `sameOpaquePass=true`;
- `nativeGraphicsExpansion=false`.

P3.5/P3.7 and worker/lifetime evidence remained clean:

- border visibility/reference comparisons matched;
- differential missing/duplicate/real mismatches = 0;
- worker failed jobs = 0;
- worker shutdown join failures = 0;
- `workersClean=true`;
- `stagingClean=true`;
- `arenaClean=true`;
- `resourcesClean=true`;
- pending retirements = 0;
- process exit code 0.

## Promotion decision

Every frozen A-0238/A-0241 P4.3 runtime and visual gate is now closed.

P4.3 is **eligible for promotion**.

Promotion must preserve:

- P4.3 remains shadow-only;
- P3.10 remains production terrain owner;
- P4.2 remains coarse hierarchy/control;
- P4.1 remains flat fine control;
- command compaction remains disabled;
- temporal visibility remains disabled;
- Hi-Z remains disabled;
- native graphics expansion remains disabled.

## Next action

Synchronize continuity on PR #78, freeze its final promotion head, require hosted Build + Context Governor on that exact head, merge if green, validate merged main, record P4.3 COMPLETE, then freeze the next Phase 4 milestone separately.
