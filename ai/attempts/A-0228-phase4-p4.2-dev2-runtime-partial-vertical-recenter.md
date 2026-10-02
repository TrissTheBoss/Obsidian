# A-0228 - P4.2 dev2 reference runtime partial + vertical recenter defect

**Date:** 2026-09-29  
**Result:** `PARTIAL / CORRECTNESS CLEAN, PROMOTION BLOCKED`  
**Milestone:** Phase 4 P4.2 dev2 reference runtime  
**Tester verdict:** explicit human **Visual PASS** for world rendering

## Objective

Evaluate the published `0.4.0-phase4-dev2` Preview under the frozen A-0224 runtime contract and preserve the tester's additional report that the managed rendering window does not transition correctly across vertical chunk-section boundaries.

## Exact runtime

The supplied Prism Launcher log identifies:

- Obsidian `0.4.0-phase4-dev2`;
- Minecraft 26.2;
- Fabric Loader 0.19.3;
- Java 25.0.1;
- Vulkan on AMD Radeon RX 6800 XT;
- normal process exit code 0.

The user supplied an explicit human **Visual PASS** for world rendering.

## Automated evidence

The run exercised substantial real-world churn and closed the following automated gates:

- repeated P4.2 structural hierarchy audits passed;
- final P4.2 `hierarchyAuditFailures=0`;
- `capacityFailures=0`;
- `mutationFailures=0`;
- `missingVisibleColumns=0`;
- `duplicateVisibleColumns=0`;
- `gpuColumnFalseCullCount=0`;
- nonblocking readback high-water remained 1;
- `hardFailure=false`;
- `cameraOnlyFullHierarchyScan=false`;
- `productionDrawOwnershipChanged=false`;
- `nativeGraphicsExpansion=false`;
- inherited P4.1 final evidence remained clean with zero missing/unexpected/duplicate/false-cull counts;
- two resource reload events were observed;
- a real world leave/re-entry cycle occurred;
- normal exit returned code 0;
- worker/staging/arena/resource lifetime summaries were clean.

The main populated final P4.2 sample reached 1,057 live columns and 11,110 live sections and produced thousands of conservative coarse-visibility samples.

## Scale gap

The server/runtime remained at render distance **16**. A-0224 asks for render distance **32 or higher for the main scale sample when practical**.

Therefore this run is strong correctness/lifecycle evidence but does **not** close the intended large-distance scale sample.

## Tester-observed vertical recenter defect

The tester additionally reported:

> the rendering overlay appears not to transition well between vertical chunk sections; it only shows in lower chunk sections and does not move up with the player.

This is treated as a real inherited managed-scene defect, not as a failure of the tester's general visual parity verdict.

Source inspection found that `AsyncMultiSectionSceneProbe.tryBindCenterNearPlayer()` and `tryRecenterIfPlayerLeftWindow(...)` obtain their center through `SectionSnapshot.tryCaptureNearPlayer()`.

That helper is intentionally a **validation-target selector**: it searches outward from the player's section for the nearest section containing both air and a supported full-cube block. It can therefore return a lower “interesting” section even when the player's exact section Y has changed.

This conflicts with the already-frozen P3.10 dev24.2 vertical-recenter requirement in A-0197: the managed 3x3x1 scene center Y must follow `playerSection.y()`.

## Interpretation

P4.2 itself showed clean hierarchy/GPU correctness at render distance 16, and the world-rendering visual verdict is PASS.

Promotion remains blocked for two reasons:

1. A-0224's 32+ scale sample is still missing.
2. The inherited P3.10 managed-scene vertical anchor does not always follow the player's exact section, so the inherited production-behavior gate is not fully closed for this run.

Do not weaken either gate.

## Next action

Freeze and implement a narrow correction that separates **exact player-section scene anchoring** from the older **interesting-section validation-target selection**. Publish a new immutable dev2.x Preview and rerun the vertical transition plus the 32+ scale sample.
