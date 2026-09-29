# A-0219 - Phase 4 P4.1 dev1 first reference runtime partial

**Date:** 2026-09-29  
**Status:** `PARTIAL` / **AUTOMATED P4.1 CORRECTNESS CLEAN; IN-WORLD F3+T + HUMAN VISUAL VERDICT STILL REQUIRED**  
**Milestone:** Phase 4 P4.1 shadow large-scene visibility  
**Test package:** public Preview `Obsidian-0.4.0-phase4-dev1.jar`  
**Preview source:** `6af5174f054b272c65174fbf0c37d981a66aff31`  
**Preview JAR SHA-256:** `8bf25f3aa6bfe6fb4392044ba0973692fc6a24fd9948f3fea51cf55b12e157f3`

## Objective

Evaluate the first user-supplied reference-machine runtime for the frozen A-0203 P4.1 contract without weakening any promotion gate or changing renderer source.

## Action

The user ran the published dev1 Preview on the reference Windows 11 / AMD Radeon RX 6800 XT / Minecraft 26.2 / Fabric Loader 0.19.3 / Java 25 Vulkan setup, entered a single-player world, exercised substantial camera/scene movement and block-driven section churn, then exited normally. The full Prism Launcher log was reviewed against A-0203/A-0205.

No renderer-source correction is authorized from this run because the observed automated P4.1 path is clean. The run is classified PARTIAL solely because the frozen exercise is not fully evidenced.

## Result

`PARTIAL`.

The automated P4.1 correctness/scale/lifetime evidence is strong and clean:

- startup identity is `Obsidian 0.4.0-phase4-dev1`;
- Vulkan backend and AMD Radeon RX 6800 XT are active;
- P4.1 configured at effective render distance 16 with exact section range `[-4,19)`, hard slot ceiling `2,500,000`, candidate capacity `36,504`, and `nativeGraphicsExpansion=false`;
- bounded scene resync completed;
- final live/high-water scene scale reached `10,642` non-empty sections;
- `2,779` GPU visibility dispatches tested `25,985,145` candidates;
- `2,644` sampled comparisons completed and all `2,644` were exact/conservative PASS samples;
- `missingVisible=0`;
- `unexpectedVisible=0`;
- `duplicateVisible=0`;
- `gpuFalseCullCount=0`;
- `capacityFailures=0`;
- `lifecycleOverflow=0`;
- `readbackPendingHighWater=1`;
- `hardFailure=false`;
- `abandonedForDeviceShutdown=false`;
- `cameraOnlyFullSceneScan=false`;
- `productionDrawOwnershipChanged=false`;
- `nativeGraphicsExpansion=false`;
- real scene churn occurred: `chunkLoads=2,317`, `chunkUnloads=1,260`, `installs=21,420`, `removals=10,778`, `slotReuses=10,778`, `sceneUpdateFrames=452`, `cameraOnlyFrames=13,522`;
- vertical/horizontal movement is evidenced by 20 Phase-3 scene recenter events and changing Y centers;
- normal shutdown completed with process exit code 0.

Inherited production/correctness/lifetime evidence also remained clean:

- P3.10 replacement: `duplicateClaims=0`, `claimOverflows=0`, `stalePlanFailures=0`, `executionWithoutClaim=0`, `executionRevalidationFailures=0`, coherent suppression/execution accounting, exact production coordinates/color, same OPAQUE pass, no native graphics expansion, no partial remeshing/GPU patch;
- P3.5 final border/halo evidence ready with zero worker live-world reads, zero synchronous scene builds, zero unsafe stale installs;
- P3.6 final T-junction evidence ready with all recorded bounds/plane/lattice checks matching;
- P3.7 final differential evidence ready with `missing=0`, `duplicate=0`, `optimizedWithoutReference=0`, `realMismatches=0`;
- worker pool completed `301/301` jobs with zero failures, queue-full rejections or shutdown join failures;
- `workersClean=true`, `stagingClean=true`, `arenaClean=true`, `resourcesClean=true`;
- zero pending upload batches, arena retirement batches or resource retirements at shutdown.

## Remaining frozen-gate gaps

### 1. Required in-world F3+T recovery is not proven

The only explicit Minecraft `Reloading ResourceManager` line occurs during initial startup, before the player joins the world and before P4.1 is configured. The only explicit Phase-3 `resource-reload` invalidation likewise occurs while the scene center is still unbound.

The final P4.1 summary reports `resourceReloads=1`, but the inherited measured benchmark summary reports `benchmarkResourceReloadDelta=0`. Taken together with the log chronology, this does **not** prove the required post-startup/in-world F3+T reload-and-recovery exercise.

A-0203 requires F3+T and recovery; this gate is not waived.

### 2. Explicit human visual PASS was not supplied with the log

Because P4.1 is shadow-only, promotion requires an explicit human statement that the world remained visually indistinguishable from the promoted P3.10 baseline: no new holes, missing/duplicate terrain, texture/light/cutout/depth regressions, or stale popping.

No such explicit verdict accompanied this uploaded log, so the visual gate remains open.

### 3. World leave/re-entry was not demonstrated

The log shows one world join followed by normal disconnect/shutdown, not a leave-and-re-enter cycle. A-0203 phrases this part as required “if practical”; it should be included in the rerun when practical but is not the primary blocker.

## Intended effect

Validate the first real large-scene P4.1 shadow run, identify whether a renderer correction is required, and close only gates actually supported by evidence.

## Actual effect

The run strongly validates the current P4.1 implementation under real camera movement, chunk churn, vertical movement, edits, production P3.10 rendering, and clean shutdown. No automated correctness, capacity, ownership, worker, staging, arena or resource-lifetime defect was observed.

The run does **not** satisfy the complete frozen promotion contract because the required in-world F3+T recovery and explicit human visual PASS are still missing.

## Why / root cause

This is an exercise-coverage gap, not an observed renderer defect. The test ended with clean evidence before a post-startup F3+T cycle was logged, and the user supplied the launcher log without a separate explicit visual verdict.

## Side effects / lessons

- Do not change renderer source from this result.
- Do not reinterpret the startup resource reload as proof of the required in-world F3+T exercise.
- Keep PR #57 draft.
- Reuse the **exact same published Preview JAR** for the follow-up so only missing evidence is added.
- Existing P4.1 oracle, ambiguity, capacity, ownership and inherited P3.10 gates remain unchanged.

## Next action

Run the exact same `0.4.0-phase4-dev1` Preview again. After entering the world and allowing P4.1 bounded scene resync plus PASS samples to establish, press **F3+T**, wait for reload/recovery and new P4.1 PASS samples, optionally leave/re-enter the world if practical, then exit normally. Supply the full log plus an explicit human visual verdict.

If that rerun remains clean and the visual verdict is PASS, record a new immutable promotion attempt, run exact synchronized-head CI, then decide the next Phase 4 slice from measured evidence. Do not expand production draw ownership inside P4.1 itself.
