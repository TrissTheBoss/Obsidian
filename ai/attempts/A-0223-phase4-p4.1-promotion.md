# A-0223 - Phase 4 P4.1 promotion and Phase 4 continuation

**Date:** 2026-09-29  
**Status:** `SUCCESS` / **P4.1 PROMOTED**  
**Milestone:** Phase 4 P4.1 shadow large-scene GPU visibility

## Objective

Close P4.1 promotion after the frozen A-0203 runtime/visual contract passed and synchronize the repository onto the next Phase 4 slice.

## Promotion evidence

- A-0220 closed automated runtime gates on the exact public dev1 Preview, including two post-startup F3+T reload/recovery cycles, world leave/re-entry, 1,836/1,836 exact GPU/CPU visibility samples, zero missing/unexpected/duplicate identities, `gpuFalseCullCount=0`, clean inherited P3.10/P3.5/P3.6/P3.7/lifetime evidence and exit code 0.
- A-0221 recorded the tester's explicit human visual **PASS**.
- A-0222 rebuilt the promotion candidate on current `main` with all 11 P4.1 implementation/version blobs copied byte-for-byte from the tested branch.
- promotion PR #74 exact head `8be121eaf473423b8fc0766be37e48bb8fa2af16` passed:
  - Build #809 / run `36604112680`;
  - Context Governor #37 / run `36604112007`.
- PR #74 merged `[no-release]` as `59d130e17000b07e6ed8bbe226cb2f50eb795c74`.
- exact merged `main` then passed:
  - Build #810 / run `36604279863`;
  - Context Governor #38 / run `36604279375`;
  - Repository Hygiene #31 / run `36604279326`.
- obsolete diverged draft PR #57 was closed as superseded rather than force-merged.

## Result

P4.1 is **COMPLETE**.

The promoted capability is deliberately shadow-only:

- persistent bounded large-scene non-empty-section metadata;
- exact lifecycle-driven membership;
- scalable Vulkan compute frustum classification;
- visible-identity compaction/count;
- independent CPU oracle and asynchronous sampled readback;
- no camera-only full Java-scene scan;
- no production draw ownership change;
- no native graphics expansion.

P3.10 remains the production SOLID/CUTOUT terrain draw owner.

## Next action

Freeze and begin P4.2 as a separate Phase 4 milestone. Do not retroactively expand P4.1.
