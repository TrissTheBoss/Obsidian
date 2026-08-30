# Obsidian Current State

Last updated: 2026-08-30

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Product phase: **Phase 3 — production asynchronous CPU mesher / greedy meshing**.
- P3.1-P3.4: COMPLETE.
- **P3.5 border/halo correctness: COMPLETE** through dev12.1; promotion merge `1f34b3e4819b4eaa3a8fa474b09570a2e049b15a`.
- **P3.6 T-junction policy: COMPLETE** through dev13; promotion merge `602c53abb76dff0e27cf314abc308ff5b7ac0cae`.
- **P3.7 differential correctness: COMPLETE** through dev14; PR #50 merge `e1e0c583160bd2a36a2fd42a969bf35e5697591b`.
- **P3.8 meshing benchmark: COMPLETE** through dev15; PR #52 merge `49385aedff74f2382fcd9a9bb44e59cf559e63c4`.
- Synchronized P3.8-complete main: `169274b468d2a278d39043938efff19844bec9ba`, Build `33272073819` SUCCESS.

## P3.9 partial remeshing — REJECTED / DEFERRED

Frozen parent contract: **A-0159**. The experiment used exactly four fixed section-local Y slices, exact block-local dirty provenance, permanent P3.7 correctness, immutable per-slice render-truth fingerprints, mandatory fail-closed fallback, a matched production full-section control, and pre-frozen benefit/complexity thresholds.

Production full-section invalidation/capture/worker mesh/upload/install/draw remained authoritative throughout P3.9. Shadow partial-remesh output was never uploaded or drawn. No partial GPU patch exists.

Causal history:

- dev16: initial correctness defects found.
- dev17: permanent P3.7 semantics and direction-domain correction.
- dev18: pending same-section coalescing fixed.
- dev19: missing/empty provenance identified.
- dev20: missing/empty correlated to outer `SINGLE_SECTION` lifecycle events.
- dev21: the relevant missing/empty single-section population proved `LIGHT_UPDATE` only.
- dev22: preserved an already-pending episode only across exact same-section LIGHT_UPDATE-only empty-provenance intervals.
- A-0185: full-volume dev22 benefit/complexity passed strongly, but 6 deterministic unselected-slice truth changes failed correctness.
- A-0186: final permitted safety correction froze deterministic `FAILURE_UNSELECTED_CHANGED` as mandatory full-section fallback without weakening the oracle or mutating the request.
- A-0187: dev23 package SUCCESS.
- **A-0188: final dev23 runtime closed correctness and volume but missed the frozen projected-upload P95 threshold: `807 > 800` permille. P3.9 fixed four-slice strategy is formally REJECTED/DEFERRED.**

### A-0188 final dev23 evidence

The final dev23 run loaded `0.3.0-phase3-dev23`, attached to Vulkan on AMD Radeon RX 6800 XT, and armed both P3.9 measurement lines before the workload.

Safety correction:

- `fallbackUnselectedTruthChanged=5`
- completed localized episodes `32`
- exact `32/32`
- correctness failures `0`
- completed-episode unselected-change failures `0`
- determinism failures `0`
- oracle changed `false`
- slice-mask mutation `false`
- production renderer changed `false`
- partial GPU patch `false`
- thresholds changed `false`.

Frozen local closure:

- completed `32` — PASS
- one-slice `19` — PASS
- two-slice `13` — PASS
- coalesced `3` — PASS
- fallbacks `150` — PASS
- observed/retained/overflow `32/32/0` — PASS
- selected cells P50 `250` permille — PASS
- CPU P50/P95 `24/256` permille — PASS
- projected upload P50 `300` permille — PASS
- **projected upload P95 `807` permille — FAIL against frozen `<=800`**
- metadata `96` bytes/section — PASS
- slice identities `4` — PASS
- inflation mean/max `0/0` — PASS
- diagnostic `thresholdsPassed=false`.

Underlying proof contents remained exact despite final close occurring after a world/scene transition that made instantaneous high-level readiness booleans false:

- P3.5 proof records `865`, exact border visibility/reference accounting
- P3.6 determinism `865/865`, strict T-junction points `4,077`, junction-bearing transforms `606`, transform failures `0`
- P3.7 determinism `865/865`, missing/duplicate/optimized-without-reference/real mismatch all `0`
- worker world reads after capture `0`
- synchronous scene mesh builds `0`
- unsafe stale installs `0`
- queue rejections/failures/join failures `0`
- workers/staging/arena/resources clean
- staging submitted/reclaimed `57,699,168 / 57,699,168`
- arena used `0`, allocations/retired/reclaimed `2,595/2,595/2,595`
- retired/released resources `865/865`, pending `0`
- resource reload events `2`, camera recenter events `2`
- process exit `0`.

Frozen conclusion: **do not retune or rerun.** The miss is only 7 permille, but A-0159 explicitly forbids threshold weakening after measurement. The fixed four-slice strategy is retained as evidence/reference only.

Immutable decision record: `ai/attempts/A-0188-phase3-p3.9-dev23-final-runtime-reject-defer.md`.

## Next active engineering target — production opaque/cutout terrain replacement

P3.9 is no longer on the critical path. Partial GPU patching is not a prerequisite.

Proceed directly to replacing the supported vanilla/Fabric opaque/cutout terrain rendering path with the already-proven **full-section asynchronous greedy output**. Before source changes, freeze a new immutable contract for this production replacement.

The new contract must preserve at minimum:

- Vulkan-only / vendor-neutral architecture;
- Minecraft-owned device/presentation boundary unless new evidence requires deeper takeover;
- render-thread capture and GPU ownership;
- zero worker live-world reads after capture;
- permanent independent P3.7 reference/differential oracle;
- exact supported material/sprite/UV/tint/color/light/model semantics;
- bounded worker queues, staging, geometry arena and completion-gated retirement;
- generation-safe stale-result rejection;
- no routine `vkDeviceWaitIdle`;
- conservative passthrough/fallback for unsupported/generalized geometry;
- explicit evidence that the production Obsidian opaque/cutout draw path replaces, rather than merely compares with, the corresponding vanilla terrain path;
- runtime correctness, lifecycle/reload/recenter, clean shutdown, and targeted visual validation before promotion.

Phase 4 large-scale GPU visibility tuning remains downstream of real production terrain replacement.

## Dev23 package reference

The final P3.9 binary remains useful only as immutable experiment evidence:

- implementation/package commit `fa779604924a6e6f7d6b845b9a3c8522bfa222b6`
- tree `ac6d83c1204fdbd519eff391ba927225a9e00db8`
- Build `33280453234` SUCCESS
- artifact `9722827474`
- `Obsidian-0.3.0-phase3-dev23.jar`
- size `525,855` bytes
- SHA-256 `f920cae998a8d27c6419dd05fb50c58cf2d7626c27616e8f7ec6e224dd4368d1`
- sources SHA-256 `527eab20994c4fc75ef6e63ebf92a090a19e0210ff52e148c8df5cf44ebe7b38`.

Do not create dev24 as another four-slice P3.9 attempt.

## PR / release policy

- PR #53 is an experimental evidence branch and must close **without merge** after continuity is synchronized.
- Keep the existing public checkpoint; internal milestone commits remain `[no-release]`.
- The next production replacement work should begin from synchronized `main` plus any deliberately ported proven components, not by merging the rejected P3.9 experiment wholesale.
- Runtime handoff is always the direct versioned JAR, never the Actions ZIP wrapper.
