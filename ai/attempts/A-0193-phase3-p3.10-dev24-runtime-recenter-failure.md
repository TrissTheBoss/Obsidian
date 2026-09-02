# A-0193 — Phase 3 P3.10 dev24 runtime recenter failure

Date: 2026-09-02
Status: **RUNTIME FAILURE — PROMOTION BLOCKED**
Parent: A-0192
Tested artifact: `Obsidian-0.3.0-phase3-dev24.jar`
Artifact SHA-256: `d6585db05b67b815f30a64cc64d767f88e3cb2608b1593f63b746bee92b3d690`
Reference environment: Windows 11, Minecraft 26.2, Fabric Loader 0.19.3, Fabric API 0.158.0+26.2, Java 25.0.1, AMD Radeon RX 6800 XT, Vulkan AMD proprietary driver 26.8.1.

## Result

The first P3.10 production replacement runtime canary is **FAILED**. Keep PR #55 draft / DO NOT MERGE.

The initial scene reached READY and production replacement executed with exact suppression/execution accounting. After ordinary movement caused a real scene recenter from center section Y=4 to a new center at section Y=3, a newly admitted record for section `(62,3,-16)` captured `663` generalized vanilla quads but failed before worker submission with:

`java.lang.IllegalStateException: Phase 3 dev11 permanent cube oracle is empty or nondeterministic`

The failure originated in `WorkerBackedSectionLifecycleProbe.captureAndSubmit` where `ReferenceFaceMesh.faceCount() <= 0` is currently combined with nondeterminism as a hard failure. The section had real supported baked geometry, so an empty canonical full-cube reference is not by itself evidence of nondeterminism or corruption. P3.7 already has the independent `optimizedCanonicalWithoutReference` mismatch gate and full source-quad coverage proof needed to reject unsafe optimization.

The same run also exposed an implementation-level layer admission restriction: `AsyncMultiSectionSceneProbe` and `WorkerBackedSectionLifecycleProbe` currently require a section to contain both SOLID and CUTOUT baked quads before it may become a production record. This is stricter than frozen A-0191, whose claim rule is per layer: a LIVE exact record may suppress only when the matching layer has non-empty output. The final counters showed exactly equal SOLID/CUTOUT suppression counts, consistent with the both-layer-only admission rule.

## Runtime evidence before failure

Final P3.10 replacement counters:

- `prepareCalls=6782`
- `supportedVanillaCandidates=2759790`
- `vanillaFallbacks=2732732`
- `solidSuppressions=13529`
- `cutoutSuppressions=13529`
- `solidExecutions=13529`
- `cutoutExecutions=13529`
- `framesWithReplacement=1927`
- `maxClaimsPerPrepare=16`
- duplicate claims `0`
- claim overflows `0`
- stale-plan failures `0`
- execution-without-claim `0`
- execution revalidation failures `0`
- suppression/execution accounting coherent `true`
- production coordinates exact `true`
- production exact color `true`
- post-world comparison draw disabled `true`
- same OPAQUE pass `true`
- native graphics expansion `false`

P3.7 evidence before failure retained `missing=0`, `duplicate=0`, `optimizedWithoutReference=0`, and `realMismatches=0` across the installed records. Worker world reads after capture remained `0`, synchronous scene mesh builds remained `0`, and unsafe stale scene installs remained `0`.

The scene-level correctness-ready flags were false at final shutdown because the recenter hard failure intentionally invalidated the active scene.

## Human observation

The tester reported that after moving a short distance away from the starting area the failure occurred. They also reported that the visible Obsidian-looking effect seemed absent on ordinary full blocks and more apparent on grass/cutout-like geometry.

Dev24 intentionally disables the old comparison overlay and emits exact source coordinates/color, so a correctly replaced full block is expected to be visually indistinguishable from vanilla. However, the source-level both-layer-only admission restriction is still a real contract mismatch and must be corrected independently of that visual interpretation.

## Decision

Do not promote dev24. Record and implement only a narrow dev24.1 correction:

1. deterministic empty `ReferenceFaceMesh` must not be a hard failure by itself; P3.7 exactness remains the safety authority;
2. scene/record admission must require at least one supported SOLID or CUTOUT layer, not both;
3. actual production claim remains per-layer and only succeeds when that layer has non-empty exact output;
4. do not change the 3x3x1 scene footprint, draw seam, pipelines, P3.7 mismatch rules, fallback semantics, native ownership, or partial-remeshing policy.
