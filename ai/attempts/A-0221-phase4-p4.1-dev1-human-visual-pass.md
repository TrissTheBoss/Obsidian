# A-0221 - Phase 4 P4.1 dev1 human visual PASS

**Date:** 2026-09-29  
**Status:** `SUCCESS` / **FINAL HUMAN VISUAL GATE CLOSED**  
**Milestone:** Phase 4 P4.1 shadow large-scene visibility  
**Test package:** public Preview `Obsidian-0.4.0-phase4-dev1.jar`  
**Preview source:** `6af5174f054b272c65174fbf0c37d981a66aff31`

## Objective

Close the last frozen P4.1 promotion gate after A-0220 closed all automated runtime gates.

## Action

After reviewing the exact follow-up run, the tester supplied the explicit human verdict:

**PASS**

This verdict applies to the exact P4.1 dev1 follow-up exercise that included in-world F3+T reload/recovery and world leave/re-entry.

## Result

`SUCCESS`.

The tester observed no new visual regression relative to the promoted P3.10 baseline during the exact tested run.

Combined with A-0220, the frozen A-0203 runtime contract is now closed:

- large-scene GPU/CPU visibility agreement clean;
- zero unsafe false culls;
- zero missing/unexpected/duplicate visibility identities;
- bounded capacity and memory;
- no lifecycle overflow or hard failure;
- nonblocking readback behavior;
- no camera-only full Java-scene scan;
- production draw ownership unchanged;
- native graphics ownership unchanged;
- inherited P3.10/P3.5/P3.6/P3.7/worker/lifetime gates clean;
- real post-startup F3+T reload/recovery proven;
- real world leave/re-entry proven;
- normal exit;
- explicit human visual PASS.

## Intended effect

Authorize promotion processing for P4.1 without changing the tested renderer behavior.

## Actual effect

All frozen P4.1 dev1 runtime and visual gates are closed. The next repository action is synchronization/promotion CI, not another dev1 runtime experiment.

## Promotion discipline

- Do not add renderer behavior to P4.1 after this PASS.
- Synchronize the exact tested P4.1 implementation onto current `main` without overwriting newer repository/AI continuity state.
- Run hosted CI on the synchronized promotion head.
- Only after that exact synchronized head passes should P4.1 be merged/promoted.
- The next Phase 4 version must be a separately frozen slice; P4.1 itself remains shadow-only and must not be expanded into production GPU-driven terrain ownership.

## Next action

Rebase/synchronize the exact tested P4.1 renderer files onto current `main`, validate the synchronized promotion head in hosted CI, promote P4.1, then freeze and begin the next Phase 4 development slice.
