# A-0063 - Phase 2 dev2 sustained visual-validation window fix

Date: 2026-08-21
Status: IMPLEMENTED / CI PENDING
Milestone: Phase 2 P2.2 / 0.2.0-phase2-dev2
Branch: `phase2/drawable-real-section`
PR: #14

## Trigger

A-0062 recorded that the first dev2 runtime test passed the low-level geometry/GPU/lifetime checks but the tester did not see the colored comparison mesh. The log showed the entire original comparison window lasted only about 1.23 seconds and was consumed immediately during world entry.

## Correction

The human-observation harness is changed without altering the P2.1 snapshot/reference oracle or the actual drawable geometry format:

- wait 5 seconds after the first live world render before starting the first comparison probe;
- after one 1.5-second comparison probe fully verifies and reclaims its resources, re-arm a fresh probe immediately;
- run 6 verified comparison passes total, producing roughly 9 seconds of repeated colored overlays after the 5-second world-entry delay;
- each pass still uses the same bounded staging, generation-safe arena, public indexed-indirect graphics, reversed-depth live target, and completion-gated reclamation rules;
- a new probe is only created after the previous pass reaches VERIFIED, so arena and indirect-command ownership never overlap unsafely;
- log each completed visual pass and include `completedVisualPasses` in final coordinator shutdown metrics.

This deliberately changes only the validation harness. It does not claim material/texture/light semantics or global terrain replacement.

## Expected retest behavior

After entering a world, the tester should have about five seconds to finish initial world entry. The orientation-colored comparison should then recur continuously enough to inspect while moving/turning the camera. A successful visual gate still requires the tester to explicitly report that the colored geometry is visible and aligned with the corresponding vanilla full-cube faces, with sensible depth occlusion.

## Next action

Run exact-head Java 25 / Gradle 9.5.1 CI, package the new dev2 retest JAR, then repeat the RX 6800 XT visual/runtime validation. Keep PR #14 draft until that human-visible gate passes.
