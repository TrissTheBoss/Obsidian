# A-0195 — Phase 3 P3.10 dev24.1 CI/package runtime handoff

Date: 2026-09-02
Status: **CI/PACKAGE PASS — REFERENCE RUNTIME REQUIRED**
Parent: A-0194
Target: `0.3.0-phase3-dev24.1`

## Exact source authority

Renderer-source/package authority:

- commit: `61f90ddc48d654edee2cbd87b7a9d1a7f461e54e`
- branch: `phase3/p3.10-production-terrain-replacement`
- PR: #55 remains DRAFT / DO NOT MERGE

Later continuity-only commits do not change renderer source or the canonical package.

## Frozen correction implemented

The A-0194 correction is implemented exactly and narrowly:

1. `WorkerBackedSectionLifecycleProbe.captureAndSubmit` still builds the permanent `ReferenceFaceMesh` twice and requires exact deterministic equality, but a deterministic zero-face reference is no longer a hard failure by itself.
2. Worker record capture now waits/skips only when both supported layers are empty (`solidQuads <= 0 && cutoutQuads <= 0`).
3. `AsyncMultiSectionSceneProbe` eligibility now accepts a section containing either supported layer (`solidQuads > 0 || cutoutQuads > 0`).
4. `canClaimProductionReplacement` remains unchanged and strictly per-layer: a SOLID claim requires non-empty SOLID hybrid output; a CUTOUT claim requires non-empty CUTOUT hybrid output.
5. No P3.7 exactness rule, render seam, scene footprint, pipeline, native ownership, lifecycle, partial-remesh or partial-GPU-patch behavior changed.

Static compare from A-0194 to exact source head contains only four files:

- `WorkerBackedSectionLifecycleProbe.java`: 5 additions / 5 deletions;
- `AsyncMultiSectionSceneProbe.java`: 1 addition / 1 deletion;
- `gradle.properties`: version only;
- `ObsidianBootstrap.java`: runtime banner only.

## Hosted CI

GitHub Actions Build run:

- workflow run: `33646088370`
- run number: `709`
- exact head: `61f90ddc48d654edee2cbd87b7a9d1a7f461e54e`
- Java 25 / Gradle 9.5.1 job: **SUCCESS**
- Build step: **SUCCESS**
- artifact upload: **SUCCESS**
- versioned release job: correctly skipped

## Canonical artifact

Actions artifact:

- artifact ID: `9852848319`
- archive name: `obsidian-1f9f76dead0f60118227c285b742b852792357c1`
- archive digest: `sha256:ca8d4b00fb77fb4a457e12112beb3925ef2e65e25b970a1231a3c4ae9b9cdf53`

Use the direct runtime JAR inside the archive, not the ZIP wrapper:

- file: `Obsidian-0.3.0-phase3-dev24.1.jar`
- size: `466,295` bytes
- SHA-256: `c6c624da8aed061030db1c0791955ae2efa456eb970de9115da516b207920af9`

## Required reference retest

Repeat the failed dev24 path first:

1. load the same or equivalent normal world and wait for P3.10 replacement activity;
2. move horizontally far enough to leave the managed 3x3 window and trigger a real scene recenter, preferably across terrain/elevation such that the center section Y changes as it did in A-0193;
3. confirm no `permanent cube oracle is empty or nondeterministic` failure;
4. allow the scene to return READY after recenter;
5. continue ordinary movement and inspect terrain.

Then exercise the inherited P3.10 gates:

- ordinary block break/place -> rebuild -> replacement recovery;
- F3+T -> fallback during invalidation -> replacement recovery;
- another real recenter -> replacement recovery;
- normal exit.

Human visual gate remains A-0191. Full-block replacement is intentionally visually identical to vanilla because production mode has no comparison overlay, face offset, or dim tint. Report only actual holes, missing faces/blocks, duplicates/z-fighting, UV/light/tint/AO regressions, cutout-alpha problems, cracks/pinholes, depth artifacts or stale popping.

## Promotion status

Not authorized. Dev24.1 must pass the reference runtime and visual gates before PR #55 can leave draft or merge.
