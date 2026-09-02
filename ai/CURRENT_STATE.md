# Obsidian Current State

Last updated: 2026-09-02

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Production-replacement work branch: `phase3/p3.10-production-terrain-replacement`
- Product phase: **Phase 3 — production asynchronous CPU mesher / greedy meshing**.
- P3.1-P3.4: COMPLETE.
- P3.5 border/halo correctness: COMPLETE through `0.3.0-phase3-dev12.1`.
- P3.6 T-junction policy: COMPLETE through `0.3.0-phase3-dev13`.
- P3.7 differential correctness: COMPLETE through `0.3.0-phase3-dev14`.
- P3.8 meshing benchmark: COMPLETE through `0.3.0-phase3-dev15`.
- Synchronized P3.8-complete `main`: `169274b468d2a278d39043938efff19844bec9ba`.

## P3.9 partial remeshing — REJECTED / DEFERRED

P3.9 was completed as a fixed four-Y-slice shadow experiment on branch `phase3/partial-remeshing` and closed without merge. A-0188 is the terminal experiment decision: correctness and most frozen benefit/complexity gates passed, but projected-upload P95 was `807` permille against the pre-frozen `<=800` threshold. The threshold may not be retuned after measurement.

Do not create another four-slice dev24 attempt, do not merge the rejected P3.9 branch wholesale, and do not treat partial GPU patching as a prerequisite for production terrain replacement.

## P3.10 production opaque/cutout terrain replacement — ACTIVE

P3.10 replaces the supported vanilla/Fabric SOLID/CUTOUT terrain draw units with Obsidian's already-proven full-section asynchronous greedy output while leaving unsupported/unavailable/ambiguous units on vanilla fallback.

Continuity authority:

- A-0189 — P3.10 parent contract.
- A-0190 — exact Minecraft 26.2 terrain seam result.
- A-0191 — frozen dev24 production replacement canary contract.
- A-0192 — exact hosted-CI package and runtime handoff.
- PR #55 — draft canary PR; **DO NOT MERGE** until runtime + visual gates close.

### Dev24 implementation state

Source version: `0.3.0-phase3-dev24`.

The frozen canary:

- suppresses only exact vanilla SOLID/CUTOUT `SectionDraw` units for matching LIVE, generation/resource-current, differential-exact installed Obsidian records;
- encodes corresponding Obsidian passthrough + repeat-aware greedy commands into the same Minecraft OPAQUE `RenderPass`;
- leaves fallback vanilla draws unchanged when a claim is unavailable or unsafe;
- disables the old post-world comparison copy for production replacement;
- removes comparison-only `1/512` face offset and 75% RGB tint, using exact source coordinates and captured ARGB;
- preserves permanent P3.7 coverage/material/direction/geometry/UV/color/light truth;
- adds no native Vulkan graphics ownership expansion and no partial GPU patch.

Implementation authority before continuity-only handoff commits: `416662fb4dc132e1622f87349219a813e150c90f` (`[no-release] Fix dev24 bootstrap banner syntax`).

Hosted Build run `33334749141` / run #701 passed on that exact source head.

Canonical direct runtime JAR from artifact `9738683436`:

- `Obsidian-0.3.0-phase3-dev24.jar`
- size **466,364 bytes**
- SHA-256 **`d6585db05b67b815f30a64cc64d767f88e3cb2608b1593f63b746bee92b3d690`**

Sources JAR:

- `Obsidian-0.3.0-phase3-dev24-sources.jar`
- size `240,057` bytes
- SHA-256 `03991b9c668ce61873e6858a7066b2eb83ce3ce649befd96e8a97cff6f8f3c56`.

Runtime handoff is always the direct versioned JAR, never the Actions ZIP wrapper.

## Current handoff point — reference runtime required

No further renderer-source change is justified before the frozen A-0191 runtime canary is exercised.

Run the exact dev24 JAR on the reference Windows 11 / RX 6800 XT Vulkan environment and exercise:

1. initial world load until replacement activates;
2. real SOLID replacement activity;
3. real CUTOUT replacement activity;
4. at least one vanilla fallback interval while a managed record is unavailable/not-LIVE;
5. ordinary block/terrain edit -> rebuild -> replacement recovery;
6. `F3+T` resource reload -> fallback during invalidation -> replacement recovery;
7. real section/scene recenter -> replacement recovery;
8. normal game exit.

Automated gates must retain:

- SOLID suppressions/replacement executions > 0;
- CUTOUT suppressions/replacement executions > 0;
- exact suppression == execution accounting;
- duplicate/overflow/stale-plan/unclaimed/revalidation failures `0`;
- production-coordinate and exact-color flags true;
- post-world comparison draw disabled true;
- same-OPAQUE-pass flag true;
- native graphics expansion false;
- permanent P3.7 missing/duplicate/optimized-without-reference/real mismatch all `0`;
- worker world reads after capture `0`;
- synchronous scene mesh builds `0`;
- unsafe stale installs `0`;
- clean worker/staging/arena/deferred-resource lifetime;
- process exit `0`.

Explicit human visual PASS is mandatory. Inspect opaque terrain, cutout vegetation, section/chunk boundaries, camera motion, edits, reload and recenter. Any holes, duplicate/z-fighting terrain, comparison offset/tint, UV/tint/light/AO regression, cracks/pinholes, cutout-alpha regression, depth-order regression or stale popping blocks promotion.

## Next action after runtime

Return the complete relevant runtime log and explicit visual verdict. Record the result in a new immutable attempt. If every frozen gate passes, synchronize continuity, validate the exact evidence head in hosted CI, and only then prepare P3.10 promotion. If any gate fails, keep PR #55 draft and record/fix only the narrow demonstrated defect without broadening eligibility or weakening fallback/oracle rules.
