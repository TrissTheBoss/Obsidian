# A-0194 — Phase 3 P3.10 dev24.1 recenter/admission correction contract

Date: 2026-09-02
Status: **PLAN FROZEN**
Parent: A-0193
Target: `0.3.0-phase3-dev24.1`

## Purpose

Correct the two narrow production-record admission assumptions demonstrated by the failed dev24 reference runtime without changing the frozen P3.10 render seam, per-layer claim safety, P3.7 exactness rules, scene footprint, or GPU ownership model.

## Frozen corrections

### 1. Deterministic empty permanent reference is non-fatal

`ReferenceFaceMesh` remains the independent canonical full-cube topology oracle. Build it twice on the render thread exactly as before and require deterministic equality.

Change only the invalid assumption that `faceCount() <= 0` is itself a hard failure. A section may contain real supported baked SOLID/CUTOUT geometry while containing zero faces eligible for the canonical full-cube reference.

Safety remains unchanged because:

- generalized baked capture is still built twice and must be deterministic;
- the worker still builds the permanent visibility/render-key/candidate/transport chain;
- P3.7 still audits every source baked quad exactly once;
- P3.7 still rejects any optimized canonical face without an independent reference (`optimizedCanonicalWithoutReference > 0`);
- P3.7 `exact()` remains mandatory before GPU installation or production claim.

No oracle mismatch threshold is weakened.

### 2. Supported-layer admission is OR, not AND

A section is eligible for a production record when its deterministic baked snapshot contains at least one supported layer:

- `solidQuads > 0 || cutoutQuads > 0`.

A section with neither supported layer remains skipped/fallback.

The worker capture gate uses the same rule. It must not wait forever for a second layer that the section legitimately does not contain.

This aligns implementation with frozen A-0191 rather than broadening the frozen production claim rule.

### 3. Production claim remains strictly per-layer

Do not change `ProductionTerrainReplacementPlan.tryClaim` or `WorkerBackedSectionLifecycleProbe.canClaimProductionReplacement` semantics:

- SOLID suppresses only if the exact LIVE record has non-empty SOLID hybrid output;
- CUTOUT suppresses only if the exact LIVE record has non-empty CUTOUT hybrid output;
- unavailable/empty/ambiguous/stale layers stay vanilla;
- suppression and execution remain one-for-one in the same Minecraft OPAQUE RenderPass.

A SOLID-only record must never suppress CUTOUT and a CUTOUT-only record must never suppress SOLID.

### 4. Scope exclusions

Do not change:

- the 3x3x1 managed scene footprint;
- scene recenter policy;
- P3.7 differential rules;
- merge eligibility/material/UV/color/light rules;
- production coordinates or exact color;
- same-OPAQUE-pass seam;
- native graphics ownership;
- arena/staging/deferred lifetime rules;
- partial remeshing or partial GPU patching.

## CI gates

Hosted build/package must pass for the exact dev24.1 source head.

Static review must confirm:

- no `faceCount() <= 0` hard failure remains in production capture;
- eligibility and capture both use at-least-one-supported-layer semantics;
- per-layer `canClaimProductionReplacement` non-empty checks remain unchanged;
- no unrelated renderer source changes.

## Runtime gates

Reference runtime must repeat the dev24 canary and additionally prove:

- a real recenter across section Y and/or terrain composition no longer hard-fails solely because the permanent reference has zero faces;
- scene reaches READY again after recenter when enough supported records exist;
- at least one SOLID suppression/execution occurs;
- at least one CUTOUT suppression/execution occurs;
- suppression/execution accounting remains exact;
- zero duplicate/overflow/stale/unclaimed/revalidation failures;
- P3.7 missing/duplicate/optimized-without-reference/real mismatch remain zero;
- worker world reads after capture 0;
- synchronous scene mesh builds 0;
- unsafe stale installs 0;
- clean lifetime and normal exit.

Human visual gate remains A-0191 unchanged. Correct full-block replacement is expected to look vanilla because dev24+ has no comparison overlay, offset, or dim tint.

## Failure policy

Any P3.7 mismatch, unsafe layer suppression, hole, duplicate, stale execution, or new visual regression blocks promotion. Do not weaken P3.7 or fallback rules to rescue the canary.
