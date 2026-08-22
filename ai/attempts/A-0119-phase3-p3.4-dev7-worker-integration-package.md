# A-0119 - Phase 3 P3.4 dev7 production worker integration and package

**Date:** 2026-08-23  
**Branch:** `phase3/render-merge-candidate-sidecar`  
**Canonical PR:** #39 against `main`  
**Version:** `0.3.0-phase3-dev7`  
**Result:** `SUCCESS` for implementation/package CI; reference runtime validation remains required before promotion.

## Objective

Complete the A-0117 dev7 production integration after the isolated A-0118 candidate-core compile checkpoint, add the frozen runtime evidence gate, and produce the canonical direct runtime JAR without changing emitted GPU geometry.

## Production worker integration

`SectionMeshWorkerPool` now builds each successful production job in this order:

1. `BinarySectionVisibility`;
2. `GreedySectionRectangles`;
3. `CanonicalFaceRenderKeys`;
4. `RenderMergeCandidates`;
5. existing generalized `BakedSectionMesh` drawable.

Completed tickets retain the dev7 candidate sidecar in addition to the prior visibility/rectangle/render-key products. Each worker owns one bounded `RenderMergeCandidates.BuildScratch` and reuses it across jobs/audits.

Primary candidate metrics now include:

- builds;
- candidate count;
- covered eligible faces;
- canonical passthrough faces;
- singleton and multi-face candidate counts;
- logical retained bytes;
- total/max build time;
- max candidates/build;
- scratch uses/high-water;
- candidate counts and covered faces by direction;
- exact primary coverage audits/matches.

On the existing first/every-64-local-completions cadence, workers rebuild dev7 candidates from the duplicate visibility/topology/render-key products and require exact `contentEquals` determinism. Existing P3.2/P3.3/dev6 and `BakedSectionMesh` audits remain intact.

## Runtime gate

`FrameCoordinator` now reports `renderMergeCandidateEvidenceReady` after all prior Phase 3/P3.2/P3.3/dev6 gates.

The new gate requires:

- nonzero candidate builds covering completed worker jobs;
- candidate-covered eligible faces exactly equal dev6 render-key eligible faces;
- canonical passthrough exactly equals visible minus eligible;
- positive candidate count not exceeding eligible faces;
- singleton + multi-face candidates exactly equal candidate count;
- at least one multi-face candidate;
- positive faces saved by candidate merging;
- exact candidate-count and covered-face directional sums;
- retained bytes exactly `candidateCount * 6`;
- scratch use covering builds;
- primary exact coverage audits exactly equal builds with all matches;
- nonzero determinism audits with all matches;
- all prior worker/lifecycle/lifetime cleanliness requirements.

Final diagnostics explicitly report `renderMergeCandidateSidecarIntegrated=true`, while retaining:

- `greedyRectangleGpuEmission=false`;
- `renderCorrectMergeKeyComplete=false`;
- `workerWorldReadsAfterCapture=0`.

`ObsidianBootstrap` and validation instructions were advanced to dev7 and explicitly state that rectangle-level interpolation/UV emission safety is not yet claimed.

## Exact integrated CI

Integrated source/package head:

- `cbb576836a304e4691e95eb395f624aefc8a2c5f`.

GitHub Actions run:

- `32602196609`;
- Java 25 / Gradle 9.5.1 build SUCCESS;
- artifact upload SUCCESS;
- versioned release publishing SKIPPED.

Artifact:

- id `9483192810`;
- wrapper name `obsidian-1736a1459915439b120bd38e564f21a68e9a1c8e`;
- wrapper size `468,101` bytes;
- wrapper digest `sha256:65312d0391607a48dfb6b3c89d7b1c15c0e7679b3f6ab9613ae03610847e706e`.

## Canonical runtime package

Direct JAR extracted from that artifact:

- `Obsidian-0.3.0-phase3-dev7.jar`;
- size `320,735` bytes;
- SHA-256 `ef2ff6f1bc78469a9a65db486f735c178565c8982fd62aa1bb60901bf56ce1c7`.

Sources JAR:

- size `167,506` bytes;
- SHA-256 `c4f86c7b595baaaa60432a510a0c2cec5ec26befe21d8d9ef5f9d1c0cdb9271e`.

Package inspection confirms `fabric.mod.json` version `0.3.0-phase3-dev7`, Fabric Loader `>=0.19.3`, Minecraft `~26.2`, Java `>=25`, client environment, and inclusion of `RenderMergeCandidates`, `CanonicalFaceRenderKeys`, `SectionMeshWorkerPool`, `FrameCoordinator` and `ObsidianBootstrap` classes.

## Deliberate boundary

This package changes CPU-side classification/sidecar work and diagnostics only. The generalized `BakedSectionMesh` remains the authoritative GPU geometry, so dev7 still does not prove or enable one-quad rectangle emission.

No merge is allowed from package CI alone. The reference runtime must exercise initial READY, block dirty/rebuild, F3+T/rebuild, ordinary movement/recentering if convenient, visual regression guard, then normal full process exit with the final dev7 coordinator line and launcher exit code.

## Next action

Run the canonical direct dev7 JAR on the reference Vulkan machine and capture the complete final shutdown output. Promotion requires `renderMergeCandidateEvidenceReady=true` plus every prior gate, exact candidate accounting/determinism, clean lifetime and process exit code 0.