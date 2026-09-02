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

P3.9 completed as a fixed four-Y-slice shadow experiment on `phase3/partial-remeshing` and was rejected/deferred by A-0188: projected-upload P95 was `807` permille versus the pre-frozen `<=800` threshold. Do not retune that threshold, revive the same experiment as another dev24 attempt, merge the P3.9 branch wholesale, or treat partial GPU patching as a prerequisite for production replacement.

## P3.10 production opaque/cutout terrain replacement — ACTIVE

Continuity authority:

- A-0189 — P3.10 parent contract.
- A-0190 — exact Minecraft 26.2 terrain seam result.
- A-0191 — frozen dev24 production replacement canary contract.
- A-0192 — dev24 hosted-CI/package handoff.
- A-0193 — dev24 reference runtime **FAILED** on real recenter.
- A-0194 — frozen dev24.1 recenter/admission correction contract.
- A-0195 — dev24.1 hosted-CI/package runtime handoff.
- PR #55 — remains DRAFT / **DO NOT MERGE** until runtime + visual gates close.

### A-0193 dev24 runtime failure

The exact dev24 JAR reached READY and executed real production replacement, but after the tester moved out of the starting managed window a real recenter changed the scene center from section Y=4 to Y=3. Section `(62,3,-16)` captured `663` generalized vanilla quads and then failed with:

`Phase 3 dev11 permanent cube oracle is empty or nondeterministic`

Final replacement accounting before shutdown was otherwise coherent:

- SOLID suppressions/executions `13,529 / 13,529`;
- CUTOUT suppressions/executions `13,529 / 13,529`;
- duplicate/overflow/stale/unclaimed/revalidation failures all `0`;
- same-OPAQUE-pass execution true;
- P3.7 missing/duplicate/optimized-without-reference/real mismatches all `0`;
- worker world reads after capture `0`;
- synchronous scene mesh builds `0`;
- unsafe stale scene installs `0`.

Promotion of dev24 is blocked.

The same review found an implementation mismatch with A-0191: both scene eligibility and worker capture required each section to contain **both** SOLID and CUTOUT quads. Frozen production claims are per-layer, so SOLID-only and CUTOUT-only exact records must be legal while the absent layer remains vanilla.

Note: production mode intentionally has **no comparison overlay**. Correctly replaced ordinary full blocks should look like vanilla because the comparison-only face offset and dim tint were removed in dev24.

### Dev24.1 correction

Source version: `0.3.0-phase3-dev24.1`.

Exact renderer-source/package authority:

`61f90ddc48d654edee2cbd87b7a9d1a7f461e54e`

Later A-0195 / `CURRENT_STATE` continuity-only commits do not change the renderer source or canonical runtime package.

A-0194 permits only these behavioral corrections:

1. build the permanent `ReferenceFaceMesh` twice and still require deterministic equality, but do not hard-fail merely because the deterministic reference has zero canonical full-cube faces;
2. scene eligibility accepts `solidQuads > 0 || cutoutQuads > 0`;
3. worker capture waits/skips only when `solidQuads <= 0 && cutoutQuads <= 0`;
4. production claim remains unchanged and strictly per-layer, requiring non-empty output for the claimed layer.

No P3.7 exactness rule, draw seam, pipeline, 3x3x1 scene footprint, native graphics ownership, lifetime rule, partial-remeshing behavior or GPU patching changed.

Static compare from A-0194 to the exact source head touches only four files: two narrow behavioral conditions, the version line, and runtime banner.

### Dev24.1 hosted CI/package

GitHub Actions Build run `33646088370` / run #709 passed on exact source head `61f90ddc48d654edee2cbd87b7a9d1a7f461e54e`.

Artifact `9852848319` contains the canonical direct runtime JAR:

- `Obsidian-0.3.0-phase3-dev24.1.jar`
- size **466,295 bytes**
- SHA-256 **`c6c624da8aed061030db1c0791955ae2efa456eb970de9115da516b207920af9`**

Use the direct JAR, not the Actions ZIP wrapper.

## Current handoff point — dev24.1 reference retest required

First reproduce the exact path that failed dev24:

1. load a normal world and wait for P3.10 replacement activity;
2. move horizontally far enough to leave the managed 3x3 window and trigger a real scene recenter, preferably across terrain/elevation so the center section Y changes;
3. verify no permanent-cube-oracle hard failure occurs;
4. allow the recentered scene to return READY and keep moving/looking.

Then complete the inherited canary:

5. ordinary block break/place -> rebuild -> replacement recovery;
6. `F3+T` -> vanilla fallback during invalidation -> replacement recovery;
7. another real recenter -> replacement recovery;
8. normal exit.

Automated gates must retain:

- SOLID suppressions/executions > 0 and equal;
- CUTOUT suppressions/executions > 0 and equal;
- duplicate/overflow/stale-plan/unclaimed/revalidation failures `0`;
- production coordinates/color exact;
- same OPAQUE pass true;
- native graphics expansion false;
- P3.7 missing/duplicate/optimized-without-reference/real mismatch all `0`;
- worker world reads after capture `0`;
- synchronous scene mesh builds `0`;
- unsafe stale installs `0`;
- clean worker/staging/arena/deferred-resource lifetime;
- process exit `0`.

Human visual PASS remains mandatory. Report actual holes, missing terrain/faces, duplicates/z-fighting, UV/tint/light/AO regressions, cracks/pinholes, cutout-alpha problems, depth artifacts or stale popping. Do not use presence/absence of an overlay as the oracle: dev24+ production replacement is intentionally visually identical to vanilla when correct.

## Next action after runtime

Return the complete relevant dev24.1 runtime log plus the visual verdict. Record the result in a new immutable attempt. If every frozen gate passes, synchronize continuity, validate the exact evidence head in hosted CI, and only then prepare P3.10 promotion. If any gate fails, keep PR #55 draft and correct only the demonstrated defect without weakening P3.7 or fallback safety.
