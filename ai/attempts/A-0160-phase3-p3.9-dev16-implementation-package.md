# A-0160 - P3.9 dev16 shadow partial-remeshing implementation and package handoff

**Date:** 2026-08-29  
**Objective:** Implement the frozen A-0159 fixed-slice shadow partial-remeshing experiment without changing production rendered geometry or GPU install behavior, then establish exact hosted package authority for the reference runtime.  
**Status:** `SUCCESS` for implementation/package / `REFERENCE RUNTIME REQUIRED`  
**Version:** `0.3.0-phase3-dev16`  
**Branch:** `phase3/partial-remeshing`

## Frozen authority

A-0159 remains the experiment contract. Dev16 is shadow-only: the existing full-section capture, production worker result, GPU allocation/upload/install and draw path remain authoritative. No threshold or workload definition in A-0159 was weakened after implementation.

## Implementation

Dev16 adds:

- exact block-local dirty provenance from the client `setBlocksDirty` render-dirty lifecycle surface;
- a bounded primitive coalescing bridge with exactly four fixed Y-slice identities: `[0,4)`, `[4,8)`, `[8,12)`, `[12,16)`;
- conservative one-row neighboring-slice expansion at Y boundaries and full-experiment fallback for X/Z boundary, halo, global lifecycle, resource reload, recenter, ambiguous/missing provenance, overflow, pending overlap and all-slice selection;
- immutable previous/current per-slice fingerprints over frozen baked source/material/position/UV/ARGB/light truth plus independent reference-face/state truth;
- optional worker ticket shadow requests that do not alter normal job priority, scheduling or production output;
- fixed worker-local scratch for selected source coverage and independent reference visibility comparison;
- shadow projection over the already-built production visibility/rectangle/render-key/candidate/transport/greedy artifacts, including forced split accounting when a production merged identity crosses a fixed slice boundary;
- exact selected-source once-only coverage, selected reference/visibility agreement, render-equivalent merged source identity checks and unchanged-fingerprint proof for all unselected slices;
- double-built deterministic per-slice truth and shadow results;
- matched full-production control execution time captured before dev16 shadow work and matched production upload-byte control;
- a bounded 512-sample primitive telemetry window with exact observed/retained/overflow accounting, P50/P95/P99/max distributions, fallback reasons, GC deltas and the pre-frozen A-0159 benefit/complexity thresholds;
- `partialRemeshExperimentEvidenceReady` layered on inherited P3.8/P3.7 correctness and clean-lifetime gates.

Production behavior deliberately remains unchanged:

- full-section rebuild/invalidation and capture remain authoritative;
- no slice is uploaded or drawn;
- no extra GPU scene record, arena range, indirect command, shader or pipeline is introduced;
- greedy eligibility/render-key/transport policy is unchanged;
- worker count/priority/backpressure policy is unchanged;
- staging/arena/completion-gated lifetime is unchanged;
- worker live-world reads after immutable capture remain prohibited.

## Compile/implementation attempts

The first temporary implementation workflow, run `33272955619`, failed before Java compilation because the helper incorrectly invoked `./gradlew`; this repository intentionally uses hosted Gradle setup and has no wrapper. The source patch had applied only in the ephemeral runner and no implementation commit was produced. This was workflow plumbing, not a source or contract failure.

The corrected temporary helper used Java 25 and Gradle 9.5.1 with `gradle build --stacktrace`. Workflow `33272993694` succeeded through patch, build, helper removal and commit. The resulting clean implementation commit is:

- `f9a67626722579fda7c8b46eb73b6970395d92f4`
- tree `376541214fba2750131113490e5bd2abd9961b87`
- message `[no-release] Implement P3.9 dev16 shadow partial remeshing`.

Temporary implementation helper files are absent from that tree.

A later accidental connector bookkeeping write briefly reintroduced a temporary helper path in an abandoned commit. It changed no dev16 source and was detected before package authority. The branch was restored to the already-created clean same-tree validation commit below; the abandoned commit/run is not package authority.

## Exact hosted package authority

Clean same-tree validation head:

- `9b5930a24c8bd1841c474a03f67407231e11bc65`
- tree `376541214fba2750131113490e5bd2abd9961b87`
- message `[no-release] Validate P3.9 dev16 package head`.

Hosted Build workflow `33273077105` on that exact head:

- Java 25 / Gradle 9.5.1: **SUCCESS**;
- Build: **SUCCESS**;
- Upload build artifacts: **SUCCESS**;
- Publish versioned release: **SKIPPED** as intended.

Artifact:

- artifact id `9720677867`;
- wrapper name `obsidian-4929cdb7c9c228f5155e18d1b9187dd18c4fcefb`;
- wrapper size `709,808` bytes;
- wrapper digest `sha256:82d48cbd5a866d2619a495eb9c564671556e16908f9267625833a306188a41ee`.

Canonical direct runtime JAR extracted from that artifact:

- `Obsidian-0.3.0-phase3-dev16.jar`;
- size `490,250` bytes;
- SHA-256 `b14640ab1a397561371564e6b3c38b93b105e481be6a32b8172b8448de701ffd`.

Sources JAR:

- `Obsidian-0.3.0-phase3-dev16-sources.jar`;
- size `250,053` bytes;
- SHA-256 `a2f03afba05929efbaa58fef398ebb888ea08b5fdd984887fb9542754a0b9bbc`.

The direct versioned runtime JAR, not the Actions ZIP wrapper, is reference-runtime authority.

## Required reference runtime

Use the exact canonical dev16 JAR above. In one coherent reference run:

1. Enter a representative world and wait until inherited P3.7/P3.8 correctness is settled and the log explicitly says the dev16 P3.9 shadow window is armed.
2. Perform localized edits away from section X/Z boundaries. Accumulate at least 16 one-slice episodes. Local section-Y rows 1, 5, 9 and 13 are useful one-slice targets; wait for the scene to recover to READY between episodes.
3. Accumulate at least 8 two-slice boundary-expansion episodes using local Y rows 3/4, 7/8 or 11/12, again allowing READY recovery.
4. Include at least one short multi-edit/coalesced episode before READY recovery.
5. Press F3+T after the window is armed and wait for resource reload plus READY; this is an explicit full-fallback episode, not a localized candidate.
6. Cause at least one real `scene-recenter` and recover to READY; this is also an explicit full fallback.
7. Reach at least 32 completed localized episodes total. Prefer waiting for `partialRemeshExperimentEvidenceReady=true` if it arms.
8. Exit normally and return the complete log, especially the final `Phase 3 dev16 P3.9 frame coordinator closed...` line.

The frozen promotion requirements remain exactly A-0159: zero correctness/unselected/determinism failures; every inherited P3.8/P3.7/lifetime gate green; evidence-count requirements met; median selected cells <= 50%; at least 75% of localized episodes select at most two slices; matched shadow CPU P50 <= 60% and P95 <= 80% of control; projected replacement upload P50 <= 60% and P95 <= 80%; fixed metadata <= 1,024 bytes/section with exactly four identities; mean/max assembled-geometry inflation <= 5%/10%.

This runtime is allowed to prove the experiment **not worthwhile**. A threshold miss must not be hidden or retuned after seeing results. In that case P3.9 is rejected/deferred or redesigned in a new immutable attempt; production remains the proven full-section path.

No separate visual verdict is required for dev16 unless an unexpected visual change occurs, because shadow output is never rendered.

## Result

`SUCCESS` for dev16 implementation and exact package creation. Reference runtime/experiment evidence remains mandatory before P3.9 can be promoted, rejected or redesigned.

## Next action

Synchronize `ai/CURRENT_STATE.md` and `ai/ATTEMPT_LOG.md` to A-0160, validate the synchronized branch head with hosted Build, keep PR #53 draft, then run the exact canonical dev16 JAR under the A-0159 runtime contract.
