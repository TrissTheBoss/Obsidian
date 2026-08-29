# A-0165 — Phase 3 P3.9 dev18 pending-episode coalescing contract

**Date:** 2026-08-29
**Status:** PLAN FROZEN
**Planned version:** `0.3.0-phase3-dev18`
**Governing experiment:** A-0159 unchanged
**Predecessor evidence:** A-0164 PARTIAL

## Problem proven by A-0164

Dev17 closed the prior shadow correctness failure: `19/19` localized episodes exact, zero correctness/unselected/determinism failures, no first-failure fixture. Exact provenance and multi-section fallback counts were both zero.

The remaining evidence-volume loss is dominated by the current shadow episode state machine:

- pending-episode fallbacks: `9`;
- not-LIVE fallbacks: `19`;
- halo/boundary fallbacks: `7`;
- global lifecycle fallbacks: `14`.

Current source discards any pending localized episode at the start of the next invalidation batch, then rejects subsequent exact dirty provenance while the production scene is rebuilding because scene state is not LIVE. This behavior prevents the required coalesced workload from surviving even though A-0159 explicitly requires coalesced multi-edit evidence.

## Hypothesis

If exact same-section localized dirty provenance is coalesced into the already-pending shadow episode while production full-section rebuild generations advance, then the experiment can retain the original pre-edit reference fingerprints and produce one exact matched shadow result for the final coalesced generation without changing production scheduling, capture, mesh, GPU upload/install or rendering.

## Allowed dev18 source changes

1. `PartialRemeshShadowRequest` may gain a pure immutable coalescing operation that:
   - preserves `episodeId`;
   - preserves all four original previous-generation fingerprints;
   - ORs the existing and new 4-bit slice masks;
   - adds exact edit counts with bounded integer overflow rejection;
   - rejects an all-four-slice result so the caller falls back full-section;
   - remains primitive-only and immutable.

2. `AsyncMultiSectionSceneProbe.preparePartialRemeshEpisode` may change only the shadow admission state machine:
   - drain exact provenance as before;
   - global/non-section-dirty lifecycle remains an explicit full fallback and clears pending shadow state;
   - provenance ambiguity/overflow, multi-section, X/Z halo/boundary and all-slice cases remain explicit full fallbacks and clear pending shadow state;
   - if an exact localized event targets the same section as an existing pending episode, merge it into the pending request even while production scene state is SCANNING/BUILDING/RETIRING rather than counting pending/not-LIVE fallback;
   - if a pending episode exists but the new exact localized event targets a different section, fail closed to full fallback rather than combining sections;
   - a first new localized episode with no pending request still requires a LIVE installed record and retained prior slice truth exactly as dev17 did.

3. `observePartialRemeshResult` must require exact pending/result episode identity **and slice-mask identity**. A worker result created before the pending request widened cannot close the widened episode. Production generation/event-sequence stale-result rejection remains authoritative.

4. Telemetry may add bounded primitive counters for pending coalesces and incompatible pending fallbacks if useful, but A-0159 threshold semantics and existing exact accounting must remain unchanged.

5. Version/log labels may advance to dev18 and explicitly describe this correction.

## Forbidden changes

Dev18 must not change:

- any A-0159 threshold;
- four fixed vertical slices;
- one-row Y dependency expansion;
- the `ClientLevel.setBlocksDirty` provenance surface;
- X/Z boundary conservatism;
- permanent P3.7 oracle semantics;
- merge eligibility/render key/transport/greedy geometry;
- production full-section invalidation or rebuild granularity;
- worker count, queue policy, priorities or backpressure;
- GPU allocation/upload/install/draw paths;
- shader/pipeline/atlas/lightmap behavior;
- staging/arena/deferred-release lifetime;
- any partial GPU patching.

## Correctness invariants

- Original pre-edit fingerprints must survive every same-section coalesce.
- Combined mask must equal the OR of every accepted exact edit mask in the episode.
- Combined edit count must equal the sum of accepted exact edit counts.
- Coalescing cannot cross sections or global lifecycle boundaries.
- All-four-slice coalescing is full fallback, never localized evidence.
- Result completion requires matching episode id and final combined slice mask.
- Existing exact selected-source/reference/merged-identity/unselected-slice/determinism proofs remain unchanged.
- Worker world reads after capture remain zero.
- Production GPU/render behavior remains unchanged.

## Self-tests required before package

- same-slice coalesce retains same mask and increments edit count;
- adjacent-slice coalesce ORs masks and becomes a coalesced two/three-slice request as applicable;
- original fingerprints remain bit-exact after coalesce;
- coalescing to all four slices is rejected/fallback;
- edit-count overflow is rejected/fallback;
- result mask mismatch cannot close a pending episode;
- all inherited dev17 self-tests remain green.

## Runtime interpretation

The exact dev18 runtime repeats the unchanged A-0159 workload. Promotion/strategy classification still requires all original frozen evidence thresholds.

If dev18 reaches the required volume and projected upload P50/P95 remains above `600/800 permille`, that is a valid experiment rejection of the fixed four-slice strategy; do not retune thresholds after seeing the result.

If volume remains insufficient, use the new exact fallback/coalescing counters to decide the next immutable attempt. No unchanged rerun should be requested unless the remaining shortfall is clearly user workload rather than a source admission defect.
