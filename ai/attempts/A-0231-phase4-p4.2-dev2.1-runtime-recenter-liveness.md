# A-0231 - P4.2 dev2.1 runtime: 32+ scale pass, recenter liveness defect

**Date:** 2026-09-29  
**Result:** `PARTIAL / SCALE + ASCENT RECENTER PASS, RECENTER LIVENESS DEFECT`  
**Version:** `0.4.0-phase4-dev2.1`

## Objective

Evaluate the corrected exact-player-section anchor from A-0229 at the intended 32+ scale and investigate the tester report that the previously visible managed rendering/“overlay” was no longer visible.

## Runtime facts

The supplied Prism Launcher log confirms:

- Minecraft 26.2 / Fabric Loader 0.19.3 / Java 25;
- Obsidian `0.4.0-phase4-dev2.1`;
- Vulkan on AMD Radeon RX 6800 XT;
- view distance changed from 10 to **32**;
- P4.2 reconfigured at render distance 32 with column capacity 5,041;
- P4.2 reached 3,725 live columns / 35,636 live sections during the 32-distance sample;
- thousands of P4.2 samples completed with zero missing columns, zero duplicate columns and zero GPU false culls;
- P4.1 remained clean;
- normal process exit code 0.

## Exact recenter evidence

Initial managed-scene bind:

`center=(43,4,-2); exactPlayerSectionAnchor=true`

The tester then crossed upward and the new correction fired exactly once:

`previous=(43,4,-2), playerSection=(43,5,-2), center=(43,5,-2)`

Therefore A-0229's **exact upward section anchoring** worked.

## Why the visible managed replacement disappeared

Immediately after the Y=5 recenter, the 3x3x1 managed window repeatedly reported:

- eligible records: 2;
- adjacent eligible pairs: 1;
- minimum required: 3 records / 2 adjacent pairs.

That is a safe fallback condition: P3.10 does not claim production replacement unless enough eligible scene records exist.

The tester described the missing visible effect as an “overlay”, but the current P3.10 production contract intentionally has `postWorldComparisonDrawDisabled=true`. The visible difference from earlier runs was the managed production replacement itself, not a supported debug overlay.

## Newly exposed liveness defect

`AsyncMultiSectionSceneProbe.afterWorldRender(...)` currently calls `tryRecenterIfPlayerLeftWindow(...)` only when `state == LIVE`.

After the valid Y=4 -> Y=5 recenter, the Y=5 scene remained in `SCANNING` because it could not satisfy the minimum eligibility threshold. While stuck in `SCANNING`, further player section changes are never checked.

Therefore descending back to a viable section cannot recenter the managed scene. This violates the intended “follow the player section” behavior even though the first upward transition was correct.

## Promotion impact

P4.2 scale evidence at render distance 32 is strong and clean.

Promotion remains blocked because the inherited P3.10 managed-scene anchor can strand itself in a non-live state after entering an ineligible/air-heavy section.

Do not reintroduce the old post-world comparison overlay. Fix recenter liveness instead.

## Next action

Freeze a narrow correction allowing exact-player-section recenter checks while the scene is SCANNING or BUILDING as well as LIVE, preserving the existing completion-gated invalidation/retirement path.
