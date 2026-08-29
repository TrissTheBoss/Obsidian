# A-0164 — Phase 3 P3.9 dev17 reference runtime

**Date:** 2026-08-29
**Result:** PARTIAL
**Package:** `Obsidian-0.3.0-phase3-dev17.jar`
**Package SHA-256:** `4f8d58251f29742afbc67d95e33a884ea72849fe099a225b154af19616ef7904`
**Frozen governing contract:** A-0159
**Correction contract:** A-0162

## Objective

Evaluate the exact dev17 diagnostic/correction package against the unchanged A-0159 partial-remeshing shadow gates. Dev17 was required to prove whether the dev16 correctness failure was a shadow-oracle defect and to expose exact admission/fallback causes without changing production rendering or any frozen threshold.

## Runtime result

The reference Windows 11 / RX 6800 XT Vulkan run closed normally with every inherited gate through P3.8 green:

- `phase3GateReady=true`;
- scheduler/binary visibility/greedy/render-key/candidate/emission/UV/transport/greedy GPU gates all true;
- `borderHaloCorrectnessEvidenceReady=true`;
- `tJunctionPolicyEvidenceReady=true`;
- `differentialCorrectnessEvidenceReady=true`;
- `meshingBenchmarkEvidenceReady=true`;
- measured benchmark reload delta `1` and scene-recenter delta `1`;
- P3.7 final proof records `329`, determinism `329/329`, `missing=0`, `duplicate=0`, `optimizedWithoutReference=0`, `realMismatches=0`;
- `workerWorldReadsAfterCapture=0`;
- `synchronousSceneMeshBuilds=0`;
- `unsafeStaleSceneInstalls=0`;
- worker queue-full rejections `0`, worker failures `0`, shutdown join failures `0`;
- production GPU/render path unchanged.

## Dev17 correctness conclusion

The dev16 correctness defect is closed by the A-0162 corrections:

- localized completed episodes: `19`;
- exact episodes: `19/19`;
- correctness failures: `0`;
- unselected-slice change failures: `0`;
- determinism failures: `0`;
- first failure: none (`episodeId=0`, `code=0`, `name=none`, `index=-1`).

Therefore the previous dev16 `1` shadow correctness failure was not reproduced after restoring permanent P3.7 reference semantics and correcting Minecraft-direction versus binary-direction comparison.

## Admission/fallback diagnostics

The run recorded `49` full-fallback episodes with coherent exact accounting:

- global lifecycle: `14`;
- provenance: `0`;
- multi-section: `0`;
- halo/XZ boundary: `7`;
- all-slices: `0`;
- pending episode: `9`;
- not-LIVE: `19`;
- accounting coherent: `true`.

This is decisive diagnostic evidence that the exact block-local provenance surface is not the present bottleneck. The dominant avoidable loss is episode-state timing: an exact new section-dirty event arriving while a prior localized episode is pending first discards the pending episode, and subsequent events while the scene is rebuilding are rejected as not-LIVE.

## Frozen A-0159 threshold evaluation

Correctness/complexity gates that passed:

- exact correctness: PASS (`19/19`, zero correctness/unselected/determinism failures);
- selected-cell P50: `250 permille` <= `500` PASS;
- CPU ratio P50/P95: `195/749 permille` <= `600/800` PASS;
- metadata: `96 bytes/section` <= `1024` PASS;
- exactly four identities: PASS;
- inflation mean/max: `0/0 permille` <= `50/100` PASS;
- sample accounting: `19 observed = 19 retained + 0 overflow` PASS;
- inherited gates/lifetime: PASS.

Required evidence volume that did not close:

- localized episodes `19 < 32`;
- one-slice `14 < 16`;
- two-slice `5 < 8`;
- coalesced episodes `0 < 1`.

Observed projected upload ratio P50/P95 was `1000/1000 permille`, above the frozen `600/800` benefit thresholds. This is adverse evidence, but A-0159 requires the minimum evidence volume before the four-slice strategy can be formally accepted or rejected on benefit. No post-hoc threshold or workload distribution requirement is introduced.

## Classification

**PARTIAL**, not FAILED and not a strategy rejection.

Reason: dev17 closes correctness and all inherited/lifetime gates, but required localized/coalesced evidence volume is still insufficient. The adverse upload ratio is retained unchanged as evidence and must not be retuned away; it becomes decisive if a corrected admission run reaches the frozen evidence volume and remains above threshold.

## Next action

Freeze a new bounded dev18 diagnostic/correction attempt before source changes. The only justified behavioral correction is shadow episode-state admission/coalescing:

- preserve the original pre-edit slice fingerprints while an exact same-section localized episode is pending;
- coalesce subsequent exact same-section dirty masks/edit counts into that pending immutable request across production rebuild invalidations;
- do not allow coalescing across world/resource/recenter/global lifecycle, provenance ambiguity, multi-section, X/Z halo boundary or all-slice cases;
- ensure a stale/narrow worker result cannot satisfy a widened pending request;
- production full-section capture/mesh/upload/install/draw remains unchanged;
- A-0159 thresholds remain unchanged;
- no partial GPU patching.
