# A-0062 - Phase 2 dev2 runtime: GPU path success, visual gate inconclusive

Date: 2026-08-21
Status: PARTIAL / VISUAL VALIDATION INCONCLUSIVE
Milestone: Phase 2 P2.2 / 0.2.0-phase2-dev2
Branch: `phase2/drawable-real-section`
PR: #14
Reference runtime: Windows 11, Minecraft 26.2, Fabric Loader 0.19.3, Java 25.0.1, AMD Radeon RX 6800 XT

## User observation

The tester entered a normal Minecraft world and flew around looking for the orientation-colored dev2 comparison mesh, but did not see a colored mesh.

This means P2.2 is **not visually validated** and PR #14 must remain draft/unmerged.

## Runtime evidence that did pass

The submitted log confirms the exact `obsidian 0.2.0-phase2-dev2` build loaded on the Vulkan RX 6800 XT and the dev2 probe executed without an exception.

Captured section:
- section `(-3,4,14)` / world origin `(-48,64,224)`;
- sampled cells 5832;
- interior air 3142;
- interior supported 602;
- interior unsupported 352;
- interior total = 4096;
- snapshot fingerprint `2470203546823529204`.

Reference/drawable accounting:
- reference faces 370;
- drawable faces 370;
- drawable vertices 1480 = 370 * 4;
- drawable indices 2220 = 370 * 6;
- reference fingerprint `1906710279062412859`;
- drawable fingerprint `9749639529812485675`;
- deterministic reference builds = 2;
- deterministic drawable builds = 2;
- `worldReadsAfterSnapshot=0`.

GPU/submission/lifetime evidence:
- public comparison pipeline compiled (`pipelineValid=true`);
- `nativeGraphicsSeam=false`;
- indexed-indirect path executed;
- comparison draws = 91;
- profiler-only submissions = 0;
- staging submitted/reclaimed = 32580 / 32580 bytes;
- two arena allocations retired/reclaimed = 2 / 2;
- final arena used bytes = 0;
- final arena free spans = 1;
- final largest free span = 4194304 bytes;
- final fragmentation = 0;
- indirect command resource retired/released = 1 / 1;
- no pending upload, arena-retirement, or resource-retirement work;
- normal shutdown; process exit code 0.

## Why the visual gate was poorly designed

The log shows the comparison started immediately on world entry at frame 628 and ended on frame 718 after only about `1.233483 s` / 91 draws. The user was still entering/loading the world during that interval. The existing probe therefore consumed its entire visual-validation window before a human tester had a realistic opportunity to look for the overlay.

This is a validation-harness defect. It does **not** prove that the geometry was visible or correctly aligned, and low-level submission success must not be promoted into a visual correctness claim.

## Required correction before retest

- make the visual comparison window long enough to inspect deliberately after world entry;
- avoid treating the initial join/loading moment as the useful human-observation window;
- log the visual window duration clearly;
- preserve all existing deterministic geometry, bounded staging, indirect draw, and completion-gated lifetime checks;
- keep PR #14 draft until the tester explicitly reports that the colored geometry was visible and aligned/occluded sensibly.

## Result

**PARTIAL.** CPU mesh semantics, GPU submission, indexed-indirect execution, bounded memory behavior, and completion-gated reclamation passed. Human-visible placement/occlusion validation remains unproven.
