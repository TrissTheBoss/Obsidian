# A-0151 - P3.7 dev14 differential correctness implementation and package

**Date:** 2026-08-29  
**Objective:** Implement the frozen A-0150 first P3.7 differential-correctness slice without changing renderer output, then obtain exact hosted package authority before any reference-runtime promotion claim.  
**Result:** `SUCCESS` for implementation/package CI. Reference runtime remains required.

## Exact implementation

Branch: `phase3/differential-correctness`  
Draft PR: #49  
Implementation head: `83388481b7a0fd566daf5cc39fd8713945df912c`  
Version: `0.3.0-phase3-dev14`

Dev14 integrates the frozen A-0150 proof as a pure worker-side sidecar:

- new `DifferentialCorrectnessProof` over renderer-owned immutable inputs only;
- already captured independent `ReferenceFaceMesh` is passed immutably into worker tickets rather than rebuilt from live state;
- final `RepeatAwareGreedyMesh` exposes only bounded read-only passthrough-source and merged-candidate identity accessors needed for conceptual expansion;
- every completed worker builds the differential proof twice and requires deterministic equality before publication;
- cancellation remains checked around the new pure proof boundary;
- a result with a real differential mismatch is rejected before install and carries a bounded deterministic first-failure fixture;
- LIVE scene evidence is counted only after generation/resource-epoch validation;
- final coordinator adds layered `differentialCorrectnessEvidenceReady=true` on top of every prior P3.6 gate.

The proof uses the optimized output only as the system under test. Authority remains:

- `ReferenceFaceMesh` for independent canonical topology;
- `SectionBakedQuadSnapshot` / exact `BakedSectionMesh` for frozen render truth.

Actual final optimized identities are conceptually expanded back to source coverage:

- passthrough identities contribute their exact source baked quad;
- retained merged candidates expand their exact integer rectangle through `CanonicalFaceRenderKeys.sourceQuad(...)`;
- every source baked quad must have conceptual coverage exactly once;
- optimized canonical mappings without an independent reference face are rejected;
- transport record/expanded-face accounting must remain exact.

For merged coverage, dev14 independently compares source baked quads against the candidate representative without using the optimized render-equivalence helper as an oracle. Checks include immutable material identity/layer, direction, exact canonical geometry/corner order, raw UV bits, exact ARGB and packed light/AO result.

A deterministic synthetic fixture self-test exercises the bounded first-failure diagnostic path without perturbing production geometry/proof inputs.

## Renderer-semantic change audit

Dev14 changes no:

- candidate eligibility;
- source suppression/replacement policy;
- emitted vertex positions;
- vertex/index formats;
- shaders;
- graphics pipelines;
- atlas/lightmap behavior;
- draw classes;
- native Vulkan graphics scope;
- render-thread GPU ownership;
- staging/arena/resource lifetime semantics.

Therefore no new human visual verdict is required for this slice unless runtime evidence exposes an accidental renderer-semantic change.

## Hosted package authority

Pull-request Build workflow: `33264171457`  
Java 25 / Gradle 9.5.1 build: **SUCCESS**  
Artifact upload: **SUCCESS**  
Release: **SKIPPED** as required for the draft/internal milestone.

Artifact:

- artifact id: `9718131242`
- wrapper name: `obsidian-99409bc5317779124d619e6c191ce23f4593aa67`
- wrapper size: `642,620` bytes
- wrapper digest: `sha256:41686a431bf9e7699be06de197d85ba72eb0543e5d0ad70e1f8fadb1a4aa0c4c`

Canonical direct runtime JAR:

- `Obsidian-0.3.0-phase3-dev14.jar`
- size: **441,563 bytes**
- SHA-256: **`9d79b1de179768d5b872178564f708b42dab0d9cc8e99a0dd8f80bf10336bc39`**

Sources JAR:

- `Obsidian-0.3.0-phase3-dev14-sources.jar`
- size: `228,167` bytes
- SHA-256: `2b6b98611885bd264db5280cc866b6dddae3895e03e8389dd81bd29484d9ba30`

## Integration transport note

The first temporary patch-transport workflow failed before touching source because one long base64 payload was truncated. The second transport attempt successfully reconstructed the payload but exposed a version-file baseline-shape mismatch: the sources artifact represented `gradle.properties` as the version line while synchronized repository truth contains the full Gradle properties file. The final helper excluded that stale transport-only hunk, changed only the exact `mod_version` line against current branch truth, applied the identical Java source patch, and self-removed all temporary patch/chunk/workflow files in the implementation commit.

These were tooling/transport failures, not renderer or Java compile failures. The exact resulting source tree is the one validated by workflow `33264171457`.

## Required reference runtime gate

P3.7 remains ACTIVE and PR #49 remains draft. Runtime promotion requires the frozen A-0150 gate, including:

- every inherited gate through `tJunctionPolicyEvidenceReady=true`;
- `differentialCorrectnessEvidenceReady=true`;
- installed differential proof records > 0 and equal installed optimized records;
- exact deterministic proof audits;
- independent reference faces checked > 0;
- source baked quads checked > 0;
- passthrough source identities checked > 0;
- merged candidates and expanded merged source faces > 0;
- exact material, canonical geometry/corner, raw UV, exact ARGB and packed-light matches;
- source coverage missing = 0;
- source coverage duplicate = 0;
- optimized canonical-without-reference = 0;
- real mismatch count = 0;
- deterministic fixture self-test PASS;
- `workerWorldReadsAfterCapture=0`;
- zero unsafe stale installs and dropped lifecycle evidence;
- clean worker/staging/arena/resource lifetime;
- normal process exit code `0`.

Exercise initial READY, an ordinary block rebuild, F3+T/resource reload, and section-boundary/recenter movement before normal exit. The scene must contain actual merged candidates/covered faces; a passthrough-only scene cannot promote this milestone.

If a real mismatch occurs, do not weaken the oracle or gate. Preserve the emitted deterministic fixture and record a new immutable attempt before making the narrow correction.
