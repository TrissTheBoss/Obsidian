# A-0198 — P3.10 dev24.2 CI package + runtime handoff

Date: 2026-09-02
Status: SUCCESS / REFERENCE RUNTIME REQUIRED
Branch: `phase3/p3.10-production-terrain-replacement`
Parent correction contract: A-0197

## Exact source authority

Renderer/package source head: `debe41eb3b6fdc7e975e904ae913f1a0f18ebb28`.

Static compare from frozen A-0197 commit `2bac0e8be0f3974ca68f1e5fecc81901b4944f3b` to the source head changes exactly four files:

- `AsyncMultiSectionSceneProbe.java`: vertical section Y now participates in the existing recenter trigger;
- `ProductionTerrainReplacementPlan.java`: production suppression/revalidation requires a non-null capture with `rejectedBlocks() == 0`;
- `gradle.properties`: version only;
- `ObsidianBootstrap.java`: runtime identity / correction description only.

No mesher, shader, pipeline, P3.7 proof, lifetime, native Vulkan ownership, partial-remesh, or partial-GPU-patch source changed.

## Hosted CI

GitHub Actions Build run:

- workflow run ID: `33648273131`;
- run number: `723`;
- exact head: `debe41eb3b6fdc7e975e904ae913f1a0f18ebb28`;
- Java 25 / Gradle 9.5.1 job: SUCCESS;
- Build: SUCCESS;
- artifact upload: SUCCESS;
- versioned release job: intentionally SKIPPED.

Artifact ID: `9853678809`.

## Canonical direct runtime JAR

Use the direct JAR, never the Actions ZIP wrapper:

- `Obsidian-0.3.0-phase3-dev24.2.jar`
- size: `466,654` bytes
- SHA-256: `7146efd6be8faf5f926eee094a65a149a6187764631abbe4fb8926f2dedbdba4`

Sources JAR:

- `Obsidian-0.3.0-phase3-dev24.2-sources.jar`
- size: `240,261` bytes
- SHA-256: `2aa38383e5b3bddb150cedc942d329247accd06e8999664aab71a7fe7c89484a`

## What dev24.2 changes

1. The 3x3x1 scene recenters when the player's section Y differs from the current center Y, even if X/Z remain inside the current horizontal window.
2. A production replacement claim is denied if the immutable generalized capture reports any rejected block. This preserves vanilla for sections containing leaves, fluid-bearing model blocks such as kelp, block entities, unsupported material/layer output, missing models, or non-block-atlas output.
3. Existing per-layer non-empty output, LIVE/generation/resource-current, P3.7-exact, same-OPAQUE-pass, bounded lifetime and fallback gates remain unchanged.
4. Dev24.2 intentionally does **not** add native leaf/kelp/fluid support to Obsidian yet. Their visibility is restored by conservative vanilla fallback.
5. Known thin/coplanar 2D grass/leaf-litter overlap behavior is unchanged by this correction.

## Required reference-runtime retest

First prove the two demonstrated failures are closed:

- stand near/in foliage and verify leaves remain visible;
- stand near/in kelp and verify kelp remains visible;
- cross from one vertical section to the next while staying in the same X/Z chunk column and verify the logged scene center Y follows the player and READY/replacement recovers;
- repeat downward as well as upward if practical.

Then verify:

- horizontal recenter still works;
- clean supported terrain still produces real SOLID/CUTOUT suppression and replacement;
- incomplete/unsupported sections visibly remain vanilla rather than developing holes;
- suppression count equals execution count per layer;
- duplicate/overflow/stale/unclaimed/revalidation failures remain zero;
- P3.7 missing/duplicate/optimized-without-reference/real mismatch counters remain zero;
- worker world reads after capture remain zero;
- synchronous scene mesh builds and unsafe stale installs remain zero;
- normal exit and completion-gated resource lifetime remain clean.

PR #55 remains DRAFT / DO NOT MERGE until explicit human visual PASS and runtime gates close.
