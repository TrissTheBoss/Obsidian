# A-0066 - Phase 2 dev2 runtime and visual success

Date: 2026-08-21
Result: SUCCESS
Milestone: Phase 2 P2.2 / `0.2.0-phase2-dev2`
Branch: `phase2/drawable-real-section`
PR: #14

## Objective

Close the P2.2 runtime gate by proving that real section-derived indexed geometry is not only submitted successfully, but is visibly placed correctly against Minecraft's live vanilla terrain on the reference Vulkan machine.

## Runtime environment

- Windows 11
- Prism Launcher 10.0.5
- Minecraft 26.2
- Fabric Loader 0.19.3
- Fabric API 0.158.0+26.2
- Java 25.0.1
- Vulkan backend
- AMD Radeon RX 6800 XT
- AMD proprietary driver 26.7.1 / Vulkan 1.4.315
- Obsidian `0.2.0-phase2-dev2` visual-retest package

## Human visual result

SUCCESS. The tester explicitly reported that the orientation-colored Obsidian geometry appeared and was **perfectly aligned** with the corresponding vanilla terrain during the sustained comparison window.

This closes the visual transform/alignment requirement that was inconclusive in A-0062. A-0063's delayed/repeated validation harness successfully made the comparison observable.

## Logged renderer evidence

The corrected harness delayed the first comparison for five seconds after the first world render, then completed all six intended visual comparison passes.

Across the final pass / shutdown evidence:

- `completedVisualPasses=6`;
- real immutable 18^3 snapshot / 5832 sampled cells;
- final observed section `(77,4,-4)`;
- final interior accounting: air=4014, supported=82, unsupported=0 -> 4096 total;
- reference faces=139;
- drawable faces=139;
- drawable vertices=556 = 139 * 4;
- drawable indices=834 = 139 * 6;
- deterministic reference builds=2;
- deterministic drawable builds=2;
- `worldReadsAfterSnapshot=0`;
- `pipelineValid=true`;
- `nativeGraphicsSeam=false`;
- indexed-indirect graphics active;
- profiler-only submissions=0;
- staging submitted/reclaimed=59,256 / 59,256 bytes;
- staging backpressure events=0;
- arena allocations=12 for six sequential passes;
- arena retired/reclaimed=12 / 12;
- arena used bytes=0 at shutdown;
- one 4,194,304-byte free span;
- arena fragmentation=0;
- indirect resources retired/released=6 / 6;
- pending uploads=0;
- pending arena retirements=0;
- pending generic resource retirements=0;
- normal world shutdown;
- process exit code 0.

The multiple passes intentionally recaptured nearby sections as the player moved. Terrain-dependent coordinates, counts, fingerprints, and camera-relative positions are not fixed success criteria.

## Interpretation

P2.2 has now passed the required real-machine correctness gate for the deliberately narrow conservative full-cube subset:

`immutable snapshot/reference oracle -> deterministic drawable indexed geometry -> bounded staging/device arena -> public Blaze3D indexed-indirect live-world draw -> correct camera/world transform -> depth-tested comparison against vanilla -> completion-gated reclamation`.

This does **not** claim P2.3 material/texture/UV/tint semantics, P2.4 light/AO, broader model/cutout semantics, greedy meshing, global vanilla replacement, or production-scale visibility/performance.

## Next action

1. Update current milestone status to runtime validated.
2. Run final exact-head Java 25 / Gradle 9.5.1 CI including this evidence/status change.
3. Promote and merge PR #14 with `[no-release]` if the exact-head CI remains green.
4. Perform the Class-A post-merge roadmap/current-state synchronization so P2.2 is COMPLETE and P2.3 becomes the next planned milestone.
