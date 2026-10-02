# A-0229 - Exact player-section vertical recenter correction contract

**Date:** 2026-09-29  
**Status:** `FROZEN BEFORE SOURCE CHANGE`  
**Milestone:** Phase 4 P4.2 inherited P3.10 correction  
**Target version:** `0.4.0-phase4-dev2.1`

## Objective

Correct the managed P3.10 3x3x1 scene anchor so vertical section transitions follow the player's **exact loaded section Y**, while leaving the older “nearest interesting section” selector available only to validation probes that need a non-empty mixed-content target.

## Root cause

`SectionSnapshot.tryCaptureNearPlayer()` does two jobs that must not be conflated:

1. obtain coordinates near the player;
2. search vertically for a section containing both air and supported full-cube content so early validation probes do not select an empty/enclosed target.

`AsyncMultiSectionSceneProbe` currently uses that validation-target selector as its production managed-scene anchor.

When the player enters an air-heavy or otherwise non-interesting section, `tryCaptureNearPlayer()` can select a lower section. The recenter condition notices that `playerSection.y() != centerSectionY`, but then asks the same selector for a new center and can receive the same lower Y again. The managed scene therefore fails to advance vertically.

## Frozen correction

Only the scene-anchor selection changes:

- initial `AsyncMultiSectionSceneProbe` binding must capture the player's exact current section coordinates;
- vertical/horizontal recenter must target the player's exact current section coordinates;
- use existing non-loading `SectionSnapshot.tryCaptureSection(x,y,z)` as the readiness/capture check so missing surrounding chunks defer the recenter safely;
- do **not** change `SectionSnapshot.tryCaptureNearPlayer()` semantics, because older validation probes rely on its interesting-section search;
- keep the scene footprint 3x3x1;
- keep existing generation invalidation, bounded worker admission, production suppression/fallback rules, GPU ownership and completion-gated lifetime behavior unchanged.

## Required validation

Hosted Java 25 / Gradle 9.5.1 CI must pass.

Runtime must then verify:

1. crossing multiple vertical section boundaries inside the same X/Z area causes logged managed scene center Y to follow the player;
2. an air-heavy player section may legitimately produce no eligible production replacement, but the managed center must not remain anchored to a lower section;
3. descending again recenters correctly;
4. horizontal recenter still works;
5. P3.10 suppression/execution accounting and P3.7 correctness remain clean;
6. P4.1/P4.2 shadow visibility remains clean;
7. F3+T and world re-entry remain healthy;
8. normal exit and lifetime cleanup remain clean;
9. the A-0224 render-distance 32+ scale sample is collected when practical;
10. explicit human visual PASS is required for the corrected package.

## Non-goals

No change to:

- P4.2 hierarchy layout or coarse GPU classifier;
- P4.1 fine section classifier;
- production draw ownership scope;
- command compaction;
- native Vulkan graphics ownership;
- meshing semantics;
- partial remeshing;
- temporal visibility or Hi-Z.

## Promotion rule

The previous dev2 visual PASS remains evidence about that exact published binary but cannot promote P4.2 after this source correction. The new immutable dev2.1 package requires its own bounded runtime confirmation, focused on vertical recenter plus the still-missing 32+ scale sample.
