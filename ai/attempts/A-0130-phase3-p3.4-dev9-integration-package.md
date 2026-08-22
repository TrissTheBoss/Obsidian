# A-0130 — Phase 3 P3.4 dev9 integration / runtime package

Date: 2026-08-23
Result: **SUCCESS — INTEGRATED PACKAGE GREEN; RUNTIME PENDING**
Milestone: P3.4 dev9 repeat-aware UV descriptor / representability sidecar

## Scope

A-0128 froze the dev9 contract. A-0129 records the pure descriptor compile checkpoint. This attempt records completed production worker/coordinator/bootstrap integration and the canonical reference-runtime package.

Dev9 remains sidecar-only. `BakedSectionMesh` remains the authoritative drawable.

## Production worker path

Every successful production worker job now builds, in order:

1. `BinarySectionVisibility`;
2. `GreedySectionRectangles`;
3. `CanonicalFaceRenderKeys`;
4. `RenderMergeCandidates`;
5. `OrdinaryQuadEmissionSafety`;
6. `RepeatAwareUvDescriptors`;
7. existing `BakedSectionMesh`.

Completed tickets retain the dev9 descriptor sidecar. Workers record exact multi-face representable/unrepresentable partitions, repeat-aware four-vertex safe/unsafe partitions, safe covered faces/faces saved, per-direction representable/safe accounting, retained bytes, build timing/scratch high-water, one primary classification audit per successful build, and duplicate `contentEquals` determinism on the existing first/every-64-local-completions cadence.

## Runtime evidence gate

`RepeatAwareUvEvidence` adds `repeatAwareUvEvidenceReady=true` strictly after the frozen dev8 gate.

The gate requires:

- all prior dev8 gates true;
- dev9 builds nonzero and covering completed production jobs;
- dev9 multi-face count exactly equals dev7 multi-face candidates;
- representable + unrepresentable exactly partitions multi-face candidates;
- repeat-aware-safe + repeat-aware-unsafe exactly partitions multi-face candidates;
- safe <= representable;
- exact per-direction representable/safe/safe-face sums;
- `safeFacesSaved == safeCoveredFaces - safeCandidateCount`;
- retained bytes exactly `representable * 19`;
- scratch use covering builds;
- primary classification audits exactly equal builds and all match;
- nonzero determinism audits and all match;
- all previous worker/staging/arena/resource cleanliness gates.

Zero representable or zero safe candidates remains a valid measured result.

## Compile-time integration defect and fix

The first full integrated CI run exposed one source-wiring regression in the existing `AtomicLongArray` max helper: the `index` argument had been accidentally omitted from `compareAndSet` while replacing the worker file.

Failure workflow: `32604593193`.

Compiler diagnostic:

`AtomicLongArray.compareAndSet` requires `(int index, long expected, long update)` but the helper called `(expected, update)`.

The fix was exactly:

`target.compareAndSet(index, previous, value)`

No dev9 descriptor/gate semantics changed.

## Canonical integrated source/package head

`0bca09023876cf661171749f7ef86f7f287307c0`

Exact workflow: `32604737940`

- Java 25 / Gradle 9.5.1: SUCCESS
- Build: SUCCESS
- Upload build artifacts: SUCCESS
- Publish versioned release: SKIPPED

Artifact:

- id `9483828345`
- name `obsidian-968996b3fc56976b0a035e755851eb6366e2dfa3`
- wrapper size `516,712` bytes
- wrapper digest `sha256:f05e4c09456779c328095e9868de2768b0cfce50b7e381ed90b8060e9df97cca`

## Canonical direct runtime JAR

- `Obsidian-0.3.0-phase3-dev9.jar`
- size `354,912` bytes
- SHA-256 `4f06323d7d60288a2c2bb48676918842e3e9cfa9bd604156c9e24aa1aedc0b46`

Sources JAR SHA-256:

- `05d207448fafd399462d9733dcc16920997fd2d20bb4f34250b408995bd2dd4f`

Package metadata verifies:

- mod version `0.3.0-phase3-dev9`;
- Minecraft `~26.2`;
- Fabric Loader `>=0.19.3`;
- Java `>=25`;
- client environment.

The runtime JAR contains:

- `RepeatAwareUvDescriptors`;
- `RepeatAwareUvEvidence`;
- integrated `SectionMeshWorkerPool`;
- integrated `FrameCoordinator`;
- integrated `ObsidianBootstrap`.

## Runtime handoff

Reference Vulkan runtime remains required before promotion. Ordinary validation is sufficient:

- initial async 3x3 scene reaches READY;
- normal block break/place rebuild reaches READY;
- F3+T rebuild reaches READY;
- ordinary recenter movement if convenient;
- normal full Minecraft/Prism exit.

The final coordinator line must include all prior gates plus `repeatAwareUvEvidenceReady=true`, exact dev9 accounting/audits, clean workers/staging/arena/resources, and launcher exit code 0.

The old fixed-anchor far-travel unload/return sequence is already closed by A-0101 and is not required again.

## Rendering boundary

Dev9 keeps:

- `repeatAwareUvDescriptorSidecarIntegrated=true`;
- `greedyRectangleGpuEmission=false`;
- `renderCorrectMergeKeyComplete=false`;
- `BakedSectionMesh` authoritative.

No human visual verdict is required for dev9 itself because emitted terrain geometry is unchanged. A later geometry/shader slice must obtain renewed explicit human visual validation before promotion.
