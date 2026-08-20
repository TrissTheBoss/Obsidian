# A-0065 - Phase 2 dev2 visual retest package plan

Date: 2026-08-21
Status: IN PROGRESS
Milestone: Phase 2 P2.2 / visual retest package
Branch: `phase2/drawable-real-section`
PR: #14

## Package gate

The first dev2 runtime proved the low-level path but did not prove human-visible overlay correctness. The retest package must therefore be built only after:

- the 5-second post-world-entry observation delay is present;
- six sequential fully reclaimed comparison passes are present;
- A-0062 and A-0063 are in the branch history;
- the canonical long-form master roadmap is restored byte-for-byte from `main`;
- exact-head Java 25 / Gradle 9.5.1 CI succeeds;
- artifact upload succeeds and release remains skipped;
- the final JAR reports `0.2.0-phase2-dev2` and contains the modified FrameCoordinator plus the existing drawable probe.

The retest should not be merged merely because CI passes. Human-visible alignment and sensible depth occlusion remain mandatory.
