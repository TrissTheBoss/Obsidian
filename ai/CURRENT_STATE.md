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

P3.9 completed as a fixed four-Y-slice shadow experiment on `phase3/partial-remeshing` and was rejected/deferred by A-0188: projected-upload P95 was `807` permille versus the pre-frozen `<=800` threshold. Do not retune that threshold, revive the same experiment, merge the P3.9 branch wholesale, or treat partial GPU patching as a prerequisite for production replacement.

## P3.10 production opaque/cutout terrain replacement — ACTIVE

Continuity authority:

- A-0189 — P3.10 parent contract.
- A-0190 — exact Minecraft 26.2 terrain seam result.
- A-0191 — frozen dev24 production replacement canary contract.
- A-0192 — dev24 hosted-CI/package handoff.
- A-0193 — dev24 reference runtime FAILED on recenter.
- A-0194 — frozen dev24.1 recenter/admission correction contract.
- A-0195 — dev24.1 hosted-CI/package handoff.
- A-0196 — dev24.1 reference runtime FAILED visual + vertical-scene gates.
- A-0197 — frozen dev24.2 vertical/capture-completeness correction contract.
- A-0198 — dev24.2 hosted-CI/package runtime handoff.
- PR #55 — remains DRAFT / **DO NOT MERGE** until runtime + visual gates close.

### A-0196 dev24.1 runtime failure

Dev24.1 fixed the prior deterministic-empty-reference crash and single-layer admission issue, and its automated replacement/proof accounting remained coherent. The reference runtime nevertheless produced two blocking visual/runtime defects:

1. leaves and kelp became invisible near claimed production sections;
2. crossing from one vertical section to another while remaining inside the same X/Z chunk column did not switch the managed 3x3x1 scene to the player's new section Y.

The tester also observed z-fighting on thin/coplanar 2D geometry such as grass/leaf litter and considered it expected for this canary. A-0197 does not change that behavior.

Root causes are now explicit:

- `SectionBakedQuadSnapshot.capture(...)` intentionally rejects every `LeavesBlock` and every block with a non-empty fluid state before tessellation. Kelp is covered by the fluid-state exclusion. Those exclusions were safe for comparison/proof use but are not sufficient to authorize suppression of a whole vanilla section/layer.
- `AsyncMultiSectionSceneProbe.tryRecenterIfPlayerLeftWindow(...)` previously used only X/Z distance for its early-return, so a same-column Y transition could leave the 3x3x1 scene at a stale vertical section.

Dev24.1 is not promotable.

### Dev24.2 correction

Source version: `0.3.0-phase3-dev24.2`.

Exact renderer-source/package authority:

`debe41eb3b6fdc7e975e904ae913f1a0f18ebb28`

Later A-0198 / `CURRENT_STATE` continuity-only commits do not change the renderer source or canonical runtime package.

A-0197 freezes only these behavioral corrections:

1. the existing scene recenter path now runs whenever `playerSection.y() != centerSectionY`, even if X/Z remain within the current horizontal 3x3 window;
2. production suppression/revalidation requires a non-null immutable baked capture with `rejectedBlocks() == 0`;
3. existing per-layer non-empty output, LIVE/generation/resource-current, P3.7-exact and same-OPAQUE-pass requirements remain mandatory.

The production completeness rule is intentionally conservative. It does **not** add native Obsidian support for leaves, kelp/fluids, block entities, translucent output, unsupported materials, missing models or non-block-atlas geometry. If any such block was rejected by capture, that section/layer remains vanilla rather than suppressing vanilla with an incomplete replacement.

No mesher algorithm, P3.7 proof, shader, pipeline, 3x3x1 footprint, completion-gated lifetime rule, native Vulkan graphics ownership, partial remeshing or partial GPU patching changed.

Static compare from frozen A-0197 (`2bac0e8be0f3974ca68f1e5fecc81901b4944f3b`) to the exact source head changes exactly four files and 15 lines total: vertical recenter condition, production completeness/revalidation gates, version, and bootstrap banner.

### Dev24.2 hosted CI/package

GitHub Actions Build run `33648273131` / run #723 passed on exact source head `debe41eb3b6fdc7e975e904ae913f1a0f18ebb28`.

Artifact `9853678809` contains the canonical direct runtime JAR:

- `Obsidian-0.3.0-phase3-dev24.2.jar`
- size **466,654 bytes**
- SHA-256 **`7146efd6be8faf5f926eee094a65a149a6187764631abbe4fb8926f2dedbdba4`**

Sources JAR:

- `Obsidian-0.3.0-phase3-dev24.2-sources.jar`
- size `240,261` bytes
- SHA-256 `2aa38383e5b3bddb150cedc942d329247accd06e8999664aab71a7fe7c89484a`.

Use the direct JAR, not the Actions ZIP wrapper.

## Current handoff point — dev24.2 reference retest required

First prove the two demonstrated dev24.1 failures are closed:

1. load a normal world and wait for P3.10 activity;
2. inspect leaves in/near managed terrain — they must remain visible;
3. inspect kelp in/near managed terrain — it must remain visible;
4. while staying in the same X/Z chunk column, cross upward through a 16-block section-Y boundary and verify the logged scene center Y follows the player and returns READY;
5. cross downward through a section-Y boundary and verify the same behavior if practical.

Then verify:

6. horizontal scene recenter still works;
7. clean supported sections still produce real SOLID/CUTOUT replacement;
8. sections with rejected/unsupported capture content visibly stay vanilla with no holes;
9. ordinary block edit and `F3+T` fallback/recovery still work;
10. normal exit.

Automated gates must retain:

- SOLID suppressions/executions > 0 and equal where clean supported sections exist;
- CUTOUT suppressions/executions > 0 and equal where clean supported sections exist;
- vanilla fallback > 0 is expected, especially in incomplete sections;
- duplicate/overflow/stale-plan/unclaimed/revalidation failures `0`;
- complete-capture-required flag true in the dev24.2 final production log;
- production coordinates/color exact;
- same OPAQUE pass true;
- native graphics expansion false;
- P3.7 missing/duplicate/optimized-without-reference/real mismatch all `0`;
- worker world reads after capture `0`;
- synchronous scene mesh builds `0`;
- unsafe stale installs `0`;
- clean worker/staging/arena/deferred-resource lifetime;
- process exit `0`.

Human visual PASS remains mandatory. Leaves/kelp visibility in dev24.2 is expected to come from conservative vanilla fallback, not new Obsidian leaf/fluid support. Continue reporting actual holes, missing terrain/faces, unexpected duplicate/z-fighting, UV/tint/light/AO regressions, cracks/pinholes, cutout-alpha problems, depth artifacts or stale popping.

## Next action after runtime

Return the complete relevant dev24.2 runtime log plus the visual verdict. Record the result in a new immutable attempt. If every frozen gate passes, synchronize continuity, validate the exact evidence head in hosted CI, and only then prepare P3.10 promotion. If any gate fails, keep PR #55 draft and correct only the demonstrated defect without weakening P3.7 or fallback safety.
