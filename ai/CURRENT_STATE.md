# Obsidian Current State

Last updated: 2026-08-22

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Current public release intent: keep the existing public checkpoint until a separate release decision.
- Canonical long-range plan: `ai/MASTER_ROADMAP.md`
- Current product phase: **Phase 3 — production asynchronous CPU mesher / greedy meshing**.
- Current active milestone: **P3.3 dev5 — correctness-first greedy rectangle extraction sidecar**.
- Active branch: `phase3/greedy-rectangle-sidecar`.
- Canonical draft PR: #37 against `main`.
- Active development version: `0.3.0-phase3-dev5`.
- Runtime test handoff preference: provide the direct versioned `.jar`, not a GitHub Actions ZIP wrapper.

## Completed merged foundation

- Phase 0: COMPLETE — public checkpoint `v0.0.2-phase0`.
- Phase 1: COMPLETE — merge `61df7b8e2abc09ce387e09c9e4d6811a9ef6c40f`.
- Phase 2 through P2.7: COMPLETE.
- P3.1 dev1: COMPLETE — PR #29 merge `c39cf17b4864e7f7081007238117aea5be3c26e3`.
- P3.1 dev2: COMPLETE — PR #32 merge `58b2b8b8b1962f2809029e32d147a4a96a93b486`.
- P3.1 dev3: COMPLETE — PR #34 merge `1b6615eac2494a197cea86d314cf5b099d2418e8`.
- P3.2 dev4 binary visibility masks: COMPLETE — PR #36 merge `54ca3cb2d64eda958579407728e757eb0c98b948`.

A-0101 remains the canonical proof for the already-closed Phase 2 fixed-target chunk unload/return lifecycle. Later Phase 3 runtime tests do not need to repeat that long-distance sequence unless the lifecycle hooks or semantics materially change.

## P3.2 proven input to P3.3

`BinarySectionVisibility` is the proven immutable worker-side topology input:

- six direction masks in permanent oracle order WEST/EAST/DOWN/UP/NORTH/SOUTH;
- 4,096 bits / 64 `long` words per direction;
- exactly 3,072 retained bytes per complete mask set;
- deterministic cell bit order `((y * 16) + z) * 16 + x`;
- reusable 18x18 supported/air halo-row scratch;
- exact conservative semantics `SUPPORTED_FULL_CUBE && neighbor == AIR`;
- unsupported neighbors suppress faces exactly as the independent `ReferenceFaceMesh` oracle does.

A-0105 runtime closure passed `phase3GateReady=true`, `schedulerEvidenceReady=true`, `binaryVisibilityEvidenceReady=true`, 288/288 production jobs, 102,367 exact visible faces, 7/7 visibility determinism audits, 7/7 independent reference audits, zero worker failure/rejection/join failure, clean workers/staging/arena/resources and exit 0.

## ACTIVE: P3.3 dev5 — correctness-first greedy rectangle sidecar

Evidence so far:

- A-0108 — frozen P3.3 dev5 sidecar/validation contract;
- draft PR #37 — implementation branch;
- exact worker-integrated CI run `32599551037` passed Java 25 / Gradle 9.5.1 build and artifact upload; release skipped.

### Greedy topology representation

`GreedySectionRectangles` is implemented as a pure topology sidecar over `BinarySectionVisibility`:

- one packed `int` per rectangle;
- bounded maximum `24,576` records (`ReferenceFaceMesh.MAX_FACES`);
- fixed worker-local primitive scratch: record array, 16-row plane workspace, reusable coverage words, per-direction area counters;
- retained output copies only actual rectangle records;
- deterministic coordinate mapping:
  - WEST/EAST: `plane=x`, `u=z`, `v=y`;
  - DOWN/UP: `plane=y`, `u=x`, `v=z`;
  - NORTH/SOUTH: `plane=z`, `u=x`, `v=y`;
- deterministic extraction order: direction, plane, row, least-significant visible bit;
- each seed takes its full contiguous horizontal run and extends vertically while the whole run remains present;
- every primary build expands its rectangles back into reusable machine words and requires exact equality with every P3.2 source-mask word;
- overlap, missing-face, extra-face and directional-area mismatches are hard failures.

### Production worker integration

Every real `SectionMeshWorkerPool` job now builds, in order:

1. proven P3.2 `BinarySectionVisibility`;
2. P3.3 `GreedySectionRectangles` topology sidecar;
3. existing generalized `BakedSectionMesh` production drawable.

On the established first/every-64-local-completions audit cadence, workers additionally:

- rebuild visibility and require exact deterministic equality;
- build the permanent independent `ReferenceFaceMesh` and retain the existing P3.2 differential proof;
- rebuild rectangles and require exact deterministic equality;
- expand the rectangle set and require exact independent-reference inclusion/count equivalence;
- retain the existing `BakedSectionMesh` deterministic audit.

Worker metrics now include rectangle builds/count/covered face area/retained bytes/build time, all six direction counts and covered areas, scratch use/high-water, primary exact mask-coverage audits, deterministic audits and independent-reference audits.

### Deliberate dev5 rendering boundary

The existing generalized SOLID/CUTOUT `BakedSectionMesh` remains the authoritative GPU drawable. Dev5 explicitly reports:

- `binaryVisibilitySidecarIntegrated=true`;
- `greedyRectangleSidecarIntegrated=true`;
- `greedyRectangleGpuEmission=false`;
- `renderCorrectMergeKeyComplete=false`.

Therefore the topology reduction measured by dev5 is **not yet a claim that all rectangles are safe to render as merged quads**. P3.4 remains the checkpoint for the complete direction/material/sprite/layer/tint/light/AO/UV/special/model render-correct merge-key integration. Arbitrary generalized baked-model quads are not merged by dev5.

## Dev5 runtime closure — REQUIRED NEXT

The canonical runtime package must be built by exact GitHub CI from the final source/package head before handoff.

Reference runtime should exercise ordinary terrain, initial 3x3 READY, a break/place rebuild, F3+T resource-reload rebuild, optional normal recenter movement, then normal exit. The old fixed-anchor far-travel sequence is not required again.

Required final evidence includes:

- `phase3GateReady=true`;
- `schedulerEvidenceReady=true`;
- `binaryVisibilityEvidenceReady=true`;
- `greedyRectangleEvidenceReady=true`;
- rectangle builds cover completed production jobs;
- rectangle covered-face area equals visibility face count globally and by direction;
- rectangle record count is positive and strictly lower than covered face area across the run, proving real topology reduction;
- retained rectangle bytes equal record count * 4;
- primary mask coverage audits match builds with zero mismatch;
- rectangle determinism audits > 0 and matches == audits;
- independent rectangle/reference audits > 0 and matches == audits;
- zero worker queue-full rejection/failure/shutdown join failure;
- zero dropped lifecycle events / unsafe stale installs;
- workers/staging/arena/resources clean;
- process exit code 0.

Because dev5 does not alter emitted GPU geometry, visual inspection is a regression guard rather than evidence that greedy rectangles are already rendered.

## Promotion authorization

The user explicitly authorized merge for P3.3 dev5. PR #37 may be promoted without a new authorization request **only after** the frozen CI/runtime closure contract passes and the final evidence-only PR head is green. Promotion must use `[no-release]`.

## Continuity model

Read in this order before changing architecture or milestone status:

1. `ai/CURRENT_STATE.md`
2. `ai/MASTER_ROADMAP.md`
3. `ai/OPERATING_MANUAL.md`
4. `ai/DECISIONS.md`
5. `ai/ATTEMPT_LOG.md`
6. newest relevant `ai/attempts/`

Source/runtime evidence overrides stale planning text. Attempts are immutable.

## Reference runtime

- Windows 11
- Prism Launcher 10.0.5
- Minecraft 26.2
- Fabric Loader 0.19.3
- Fabric API 0.158.0+26.2
- Java 25.0.1
- AMD Radeon RX 6800 XT, 16 GB VRAM
- Ryzen 5 5600X
- 16 GB DDR4-2666
- Vulkan backend

## Relevant durable decisions

D-0014 through D-0027 remain active, especially D-0016 completion-gated reclamation, D-0017 bounded/backpressured staging, D-0020 generation-safe arena identity, D-0023 public Blaze3D graphics first, D-0024 permanent independent reference oracle + worker-local binary/bitmask greedy meshing, D-0025 narrow native seam, and D-0027 public fixed-count indirect baseline.
