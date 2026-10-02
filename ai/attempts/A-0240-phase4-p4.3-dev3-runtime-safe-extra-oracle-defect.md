# A-0240 - P4.3 dev3 runtime: visual PASS, safe-extra fine oracle defect

**Date:** 2026-10-03  
**Result:** `PARTIAL / VISUAL PASS, P4.3 VALIDATOR CONTRACT DEFECT`  
**Version:** `0.4.0-phase4-dev3`  
**Exact source:** `a61f70f895eb0416caf83605f3f12eacaad43811`  
**Tester verdict:** explicit human **Visual PASS**

## Package/publication authority

Exact-head validation:

- Build #844 / run `37077177385` — SUCCESS;
- Context Governor #65 / run `37077177190` — SUCCESS.

Canonical runtime JAR:

- `Obsidian-0.4.0-phase4-dev3.jar`;
- size: **529,067 bytes**;
- SHA-256: **`d0fd48734be5ad41c9981909a6a3070612ab7ac9565bed76ca1f0c55fd3c95b9`**.

Publish Preview #15 / run `37077276604` completed SUCCESS.

- GitHub release ID: `402218950`;
- tag: `v0.4.0-phase4-dev3`;
- exact target: `a61f70f895eb0416caf83605f3f12eacaad43811`;
- Modrinth mirror job `111070060430` completed SUCCESS.

## Runtime identity

The supplied Prism Launcher log identifies:

- Minecraft 26.2;
- Fabric Loader 0.19.3;
- Java 25.0.1;
- Obsidian `0.4.0-phase4-dev3`;
- AMD Radeon RX 6800 XT / Vulkan;
- render distance **32**;
- explicit human **Visual PASS**;
- normal process exit code 0.

## P4.3 positive evidence

P4.3 produced hundreds of exact hierarchy-fed fine samples before the failing boundary case.

Representative render-distance-32 samples reduced the flat fine candidate set from **35,273** sections to roughly **9k-14k** hierarchy-fed candidates while matching the fine visible count exactly.

Before the failure:

- many samples had `missing=0`;
- `unexpected=0`;
- `duplicate=0`;
- `gpuHierarchicalFineFalseCullCount=0`;
- `cameraOnlyFullSectionScan=false`;
- production draw ownership unchanged;
- native graphics ownership unchanged.

The runtime exercised chunk churn, world re-entry, resource reloads, edits, vertical/horizontal movement and normal shutdown.

## Failure evidence

Two independent world epochs exposed the same shape:

- GPU coarse-visible columns exceeded CPU coarse-visible columns by exactly **1**;
- P4.3 final fine GPU-visible count exceeded the CPU expected fine set by exactly **1**;
- `missing=0`;
- `duplicate=0`;
- `unexpected=1`;
- `gpuHierarchicalFineFalseCullCount=0`.

First mismatch:

`gpuCoarseColumns=1249, cpuCoarseColumns=1248, ... gpuFineVisible=9273, missing=0, unexpected=1, duplicate=0`

Second mismatch:

`gpuCoarseColumns=1238, cpuCoarseColumns=1237, ... gpuFineVisible=4707, missing=0, unexpected=1, duplicate=0`

Final P4.3 telemetry latched `hardFailure=true` only because the validator treated that single unexpected fine identity as a fatal mismatch.

## Root cause

The P4.2 CPU coarse oracle and P4.3/P4.1 fine oracle intentionally do not have identical boundary semantics.

P4.2 CPU coarse classification first calls Minecraft's:

`Frustum.isVisible(columnAabb)`

and then applies extracted-plane boundary accounting.

The P4.2 GPU classifier uses the extracted six frustum planes plus the conservative epsilon only.

Therefore P4.2 explicitly permits **safe extra GPU columns** as long as there are no missing CPU-visible columns.

P4.1 and P4.3 fine CPU/GPU classification use the extracted six-plane + epsilon policy. A GPU-only safe coarse column can therefore contain a section that is visible under the fine plane policy even though Minecraft's stricter coarse `Frustum.isVisible(...)` rejected the parent column.

A-0238 incorrectly assumed such a section must be culled by the fine stage and required the final fine set to equal the CPU-coarse-derived fine set exactly.

The observed `unexpected=1` is therefore a **safe conservative lineage identity**, not a fine false cull, duplicate, lifecycle error or production rendering defect.

## Inherited gates

P4.2 remained conservative:

- render distance 32;
- hierarchy audit failures 0;
- capacity failures 0;
- mutation failures 0;
- missing coarse identities 0;
- duplicate coarse identities 0;
- GPU coarse false culls 0.

P4.1 final evidence remained exact:

- 817 completed/exact samples;
- missing 0;
- unexpected 0;
- duplicate 0;
- GPU false culls 0;
- hard failure false.

P3.10 replacement accounting, P3.5/P3.7 correctness, worker/staging/arena/resource lifetime and normal exit remained clean.

## Promotion impact

P4.3 is **not promoted** from dev3 because the frozen A-0238 validator contract requires zero unexpected fine identities.

Do not weaken actual fine correctness.

Instead freeze a correction that:

1. validates P4.3 GPU fine output against the CPU fine plane oracle over the **actual GPU-coarse candidate set**;
2. retains the CPU-coarse-derived fine set as a baseline;
3. explicitly counts fine identities whose only lineage is a P4.2 safe-extra GPU column;
4. requires zero missing/unexpected/duplicate identities against the actual candidate-set fine oracle;
5. keeps safe-extra lineage separate from true fine mismatches.

The explicit human Visual PASS remains valid because P4.3 is shadow-only and no production rendering defect occurred.
