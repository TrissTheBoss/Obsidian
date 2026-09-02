# A-0196 — P3.10 dev24.1 runtime visual + vertical-scene failure

Date: 2026-09-02
Status: FAILED / PROMOTION BLOCKED
Branch: `phase3/p3.10-production-terrain-replacement`
Package under test: `Obsidian-0.3.0-phase3-dev24.1.jar`
Renderer source/package authority: `61f90ddc48d654edee2cbd87b7a9d1a7f461e54e`
Prior handoff: A-0195

## Human runtime report

Reference-runtime testing reported:

- kelp is invisible;
- leaves are invisible;
- some z-fighting is visible on thin/coplanar 2D vegetation such as leaf litter / grass; tester considers this expected for the current canary and it is not the demonstrated blocker being corrected here;
- moving vertically from a lower section to a higher section in the same X/Z chunk column does not switch the managed Obsidian scene to the higher section even while the player is inside it.

This is an explicit visual/runtime FAIL for dev24.1. PR #55 remains DRAFT / DO NOT MERGE.

## Runtime evidence

The dev24.1 run itself remained mechanically coherent at shutdown:

- SOLID suppressions/executions: `163174 / 163174`;
- CUTOUT suppressions/executions: `138154 / 138154`;
- duplicate claims: `0`;
- claim overflows: `0`;
- stale-plan failures: `0`;
- execution without claim: `0`;
- execution revalidation failures: `0`;
- same-OPAQUE-pass execution remained true/coherent;
- P3.7 missing/duplicate/optimized-without-reference/real mismatch counters remained `0`;
- worker world reads after capture remained `0`;
- synchronous scene mesh builds remained `0`;
- unsafe stale scene installs remained `0`.

Therefore the observed invisibility is not explained by claim/execution accounting failure. It is an incompleteness problem in what a claimed full-section replacement contains.

## Root cause 1 — leaves / kelp omitted from captured replacement

`SectionBakedQuadSnapshot.capture(...)` still contains earlier comparison-sidecar exclusions:

- any block with non-empty `FluidState` is rejected before model tessellation;
- any `LeavesBlock` is rejected before model tessellation.

Those exclusions were safe when the generalized mesh was only validation/comparison evidence. They are unsafe for production suppression if another supported quad in the same vanilla section/layer makes the record claimable: the vanilla section draw can be suppressed while the Obsidian replacement omits the rejected leaf/fluid-bearing model geometry.

Kelp is covered by the fluid-state exclusion. Leaves are explicitly covered by the `LeavesBlock` exclusion.

## Root cause 2 — vertical section changes are ignored by recenter trigger

`AsyncMultiSectionSceneProbe.tryRecenterIfPlayerLeftWindow(...)` only decides whether to recenter from horizontal X/Z distance:

- if player X and Z remain inside the 3x3 horizontal window, the method returns false;
- player section Y is not part of that first recenter condition;
- because the scene footprint is 3x3x1, the current center Y can therefore remain stale across a same-column vertical section transition.

The run later produced Y=4 records only after a horizontal scene move caused the recenter path to execute, which is consistent with the human report.

## Decision

Dev24.1 is not promotable.

The next correction must be conservative:

1. vertical player-section changes must trigger a scene recenter for the 3x3x1 scene;
2. production suppression must not claim a record whose generalized capture omitted any block category. Such records remain useful for inherited proof/benchmark machinery where appropriate, but production replacement must fall back to vanilla until those categories are intentionally supported;
3. do not remove the leaves/fluid exclusions or otherwise broaden proven geometry support in this correction;
4. do not change the known thin/coplanar 2D overlap behavior in this correction;
5. keep the exact public-Blaze3D same-OPAQUE-pass seam, P3.7 proof, bounded lifetime, and native-ownership rules unchanged.
