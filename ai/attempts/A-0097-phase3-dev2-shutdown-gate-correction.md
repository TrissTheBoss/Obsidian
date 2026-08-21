# A-0097 — Phase 3 dev2 shutdown gate accounting correction

Date: 2026-08-21
Status: **SUCCESS / EXACT CI SUCCESS / CORRECTED PACKAGE VERIFIED / REFERENCE RUNTIME STILL NEXT**

## Context

A-0096 implemented the production worker-backed scene path and passed exact hosted build/package/bytecode verification.

A source-level review before reference runtime found a validation-order defect in `FrameCoordinator.close()`:

- active scene records register completion-gated GPU retirements when the scene closes;
- the first dev2 implementation evaluated `phase3GateReady` immediately after `probe.close()` and worker shutdown;
- bounded staging/arena/deferred shutdown drainage occurred only **after** that gate calculation;
- therefore a correct live scene could still report `phase3GateReady=false` simply because its just-registered arena/deferred retirements had not yet been synchronously drained during shutdown.

This was a gate-accounting/order defect, not evidence of an unsafe renderer lifetime path. The retirement primitives themselves were already completion-gated.

## Correction

`FrameCoordinator.close()` now performs shutdown in this order:

1. snapshot scene/worker/runtime evidence;
2. close the scene, causing active GPU ownership to enter completion-gated retirement;
3. close/join `SectionMeshWorkerPool`;
4. close `StagingUploadArena`, using its bounded fence wait;
5. close `DeviceGeometryArena`, using its bounded retirement fence wait;
6. close `DeferredReleaseQueue`, using its bounded resource fence wait;
7. evaluate cleanup booleans and `phase3GateReady` from the **post-drain** state;
8. emit the final runtime evidence line.

The gate now explicitly requires:

- `workersClean=true`;
- `stagingClean=true`;
- `arenaClean=true`;
- `resourcesClean=true`.

`stagingClean` additionally requires:

- `abandonedForDeviceShutdown=false`;
- zero pending batches;
- submitted bytes equal reclaimed bytes.

`arenaClean` additionally requires:

- `abandonedForDeviceShutdown=false`;
- zero pending retirement batches;
- `usedBytes=0`;
- retired allocation count equals reclaimed allocation count.

`resourcesClean` requires:

- zero pending deferred releases;
- retired resource count equals released resource count.

This makes the runtime gate reflect the project’s existing bounded-shutdown contract instead of sampling the ownership state one step too early.

## Exact hosted CI

Corrected source head:

- `da4bd615a7de0bf90ac42c39ab945bb4903ae194`

Temporary NEVER-MERGE PR #33 triggered exact CI.

GitHub Actions:

- run `32521379106`;
- Java 25 / Gradle 9.5.1 job `96894077235`;
- Build: `success`;
- artifact upload: `success`;
- release publishing: correctly skipped;
- artifact id `9460674755`;
- artifact wrapper digest `sha256:0762557f387ae3e42b6d604ff4f6a052b0ea00e8d2ee609ac6b8a494b6ed628a`.

## Corrected package

- `Obsidian-0.3.0-phase3-dev2.jar`
  - size `269,557` bytes;
  - SHA-256 `0f1cc8f2aa50da277c8b6bacb531d065ba7ecf489c9e406a2e15fa7c8a455044`.
- `Obsidian-0.3.0-phase3-dev2-sources.jar`
  - size `140,377` bytes;
  - SHA-256 `fc51609e5523620796bd78b6bd4c572e23958c8a920d1b241b48d9737380c497`.

These corrected hashes supersede the A-0096 package hashes for reference runtime testing.

## Result

`SUCCESS` for the shutdown-gate correction and exact hosted compile/package verification.

No reference-runtime claim is made yet.

## Next step

Use the corrected A-0097 package for the Phase 3 dev2 reference Vulkan run. Required final evidence remains:

- `phase3GateReady=true`;
- `productionWorkerIntegrationReady=true`;
- `productionSceneInstallStillSynchronous=false`;
- `productionWorkerSceneIntegration=true`;
- edit-driven and F3+T-driven worker-backed rebuilds;
- zero synchronous scene mesh builds;
- zero unsafe stale scene installs;
- zero queue-full rejection/worker failure;
- `workersClean=true`;
- `stagingClean=true`;
- `arenaClean=true`;
- `resourcesClean=true`;
- no shutdown abandonment;
- visual continuity;
- exit code 0.
