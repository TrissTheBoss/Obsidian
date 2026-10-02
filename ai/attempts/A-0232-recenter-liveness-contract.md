# A-0232 - Recenter liveness across non-live managed-scene states

**Date:** 2026-09-29  
**Status:** `FROZEN BEFORE SOURCE CHANGE`  
**Target version:** `0.4.0-phase4-dev2.2`

## Objective

Prevent the exact-player-section managed scene from becoming stranded after recentering into an ineligible or air-heavy vertical section.

## Root cause

The exact section anchor from A-0229 is correct, but `AsyncMultiSectionSceneProbe.afterWorldRender(...)` gates recenter checks behind:

`state == State.LIVE`

A valid recenter may transition the scene into `SCANNING`. If that window cannot reach the minimum 3 eligible records / 2 adjacent pairs, it remains SCANNING indefinitely. Because recenter checks are then disabled, subsequent player movement cannot move the center again.

## Frozen correction

Change only the recenter eligibility in `afterWorldRender(...)`:

- when `centerKnown` and the scene is not already RETIRING/FAILED/CLOSED, check whether the player left the tracked X/Z window or changed section Y;
- allow `tryRecenterIfPlayerLeftWindow(...)` from SCANNING, BUILDING and LIVE;
- keep the existing top-level RETIRING early return;
- keep `invalidateScene(..., recenter=true)` authoritative:
  - if probes exist, request invalidation and enter RETIRING;
  - if no probes exist, reconfigure directly and remain SCANNING;
- keep exact-player-section `tryCaptureSection(x,y,z)` readiness behavior;
- keep all production replacement eligibility/fallback rules unchanged.

## Explicit non-goals

Do not:

- lower MIN_LIVE_RECORDS or MIN_ADJACENT_PAIRS;
- make air-heavy/ineligible sections claim production replacement;
- reintroduce the post-world comparison overlay;
- change P4.1/P4.2 visibility logic;
- change native graphics ownership;
- change meshing, command compaction, temporal visibility or Hi-Z.

## Runtime gates

The corrected package must show:

1. exact upward recenter into an air-heavy/ineligible section;
2. scene may remain SCANNING there without production replacement;
3. moving back down triggers a second exact recenter even though the scene never reached LIVE at the upper Y;
4. managed replacement can recover once a viable section is reached;
5. horizontal recenter still works;
6. P4.1/P4.2 correctness and 32+ scale remain clean;
7. F3+T/lifetime/normal-exit gates remain clean;
8. explicit human visual PASS for ordinary terrain rendering.

The debug/post-world comparison overlay remains intentionally disabled.
