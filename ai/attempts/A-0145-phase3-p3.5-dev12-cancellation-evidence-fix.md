# A-0145 - P3.5 dev12.1 cancellation-aware evidence correction

**Date:** 2026-08-29  
**Objective:** Correct the promotion-evidence accounting defect exposed by A-0144 without weakening any installed-scene, worker, geometry, raster, lifetime, or prior Phase 3 correctness requirement.  
**Action:** Updated the dev8/dev9/dev10 evidence aggregators so intentional worker cancellation between pure pipeline stages is represented as an exact nonnegative upstream-minus-downstream residual attributable to cancelled tickets. Bumped the corrected binary to `0.3.0-phase3-dev12.1`, compiled/package-validated the exact source head in GitHub CI, downloaded the artifact, and inspected the direct runtime JAR.  
**Result:** `SUCCESS` for the correction/package checkpoint. Corrected reference runtime validation is still required before P3.5 promotion; P3.6 remains inactive until that runtime closes.

## Root cause carried from A-0144

`SectionMeshWorkerPool.Worker.execute()` checks cancellation between pure sidecar stages. A running ticket can validly finish and publish an upstream stage, then observe cancellation before the next stage. In the dev12 reference run one ticket published merge-candidate telemetry and was cancelled before emission-safety. The ticket never installed, but global merge totals therefore exceeded downstream totals by one cancelled job's exact contribution.

The old gates treated all global stage totals as transactionally identical, causing a false inherited gate collapse even though scene-local P3.5 correctness, stale-install safety, lifetime cleanup and process exit all succeeded.

## Correction contract

The correction does **not** waive exact accounting.

At each affected pipeline boundary:

- downstream build count must remain positive and at least the completed-job count;
- upstream minus downstream build count must be nonnegative and no greater than the worker pool's cancelled-job count;
- represented upstream-minus-downstream payload residuals must be nonnegative;
- if build counts are equal, all payload residuals must be exactly zero;
- stage-local identities, retained-byte equations, direction sums, classification/proof audits, determinism audits and clean lifetime requirements remain exact;
- no cancelled ticket is treated as completed or installed.

Specific boundaries:

1. merge candidates -> ordinary emission safety: residual candidate count must equal residual singleton + residual multi-face counts;
2. emission safety -> repeat-aware UV: residual multi-face count is admitted only when at least one upstream build was cancelled;
3. repeat-aware UV -> repeat transport: residual multi-face/representable/four-vertex-safe counts must be ordered exactly (`safe <= representable <= multi-face`) and require a cancelled upstream build when nonzero.

With zero stage-boundary cancellations, the prior exact global equality behavior is preserved.

## Scope

No emitted geometry changed. No shader, vertex/index format, graphics pipeline, atlas/lightmap binding, snapshot/halo capture, dirty propagation, generation identity, worker world-read ownership, GPU lifetime, staging, arena, resource-retirement, or native Vulkan behavior changed.

Therefore this correction does not introduce a new human visual gate. The original dev11 explicit visual PASS remains authoritative for the unchanged geometry path.

## Exact implementation/package evidence

Corrected source/package head:

- `9d52a0d71b73f1f148a0f672555a98d6c97fe83f`

GitHub Actions workflow:

- run `33261260933`;
- Java 25 / Gradle 9.5.1: **SUCCESS**;
- build: **SUCCESS**;
- artifact upload: **SUCCESS**;
- versioned release publication: **SKIPPED**.

Artifact:

- id `9717321381`;
- wrapper `obsidian-975fc6f933583da3f728462283df86127793a120`;
- wrapper size `596,779` bytes;
- wrapper digest `sha256:5c510e1230d0614131a58dd7b983f295fd3ab53e3f00b631d6c0df76a60e7bd8`.

Direct runtime JAR:

- `Obsidian-0.3.0-phase3-dev12.1.jar`;
- size **410,243 bytes**;
- SHA-256 **`2a11b6aff62f671e53b48b37db73f38c6e8ba2749294e2fa946267aec533a13b`**.

Sources JAR:

- size `212,289` bytes;
- SHA-256 `957baef4d3b1e01c0d6b35d4486ad47a981b9a64e09c0f165c7d6e3669ec3ab8`.

Package inspection confirms `0.3.0-phase3-dev12.1`, Minecraft `~26.2`, Java `>=25`, and the corrected `OrdinaryQuadEmissionSafetyEvidence`, `RepeatAwareUvEvidence`, `RepeatAwareTransportEvidence`, plus existing P3.5 border proof/coordinator classes.

## Promotion boundary

P3.5 remains ACTIVE and PR #45 remains draft/unmerged. Run the same reference exercise with dev12.1:

1. initial 3x3 READY;
2. ordinary block break/place and rebuilt READY;
3. F3+T and rebuilt READY;
4. normal shutdown and complete log/tail.

Promotion still requires every prior gate through `repeatAwareGreedyEmissionEvidenceReady=true`, `borderHaloCorrectnessEvidenceReady=true`, `hardFailure=false`, zero worker live-world reads after capture, zero unsafe stale installs, clean worker/staging/arena/resources and process exit code 0.

Only after that corrected runtime succeeds may P3.5 be promoted and P3.6 be activated under the standing Phase 3 authorization.
