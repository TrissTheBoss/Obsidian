# A-0104 - Phase 3 P3.2 binary visibility implementation and exact package

**Date:** 2026-08-22  
**Branch:** `phase3/bitmask-visibility-masks`  
**Canonical PR:** #36 against `main`  
**Version:** `0.3.0-phase3-dev4`  
**Result:** `SUCCESS` for implementation / exact CI / package verification; reference runtime still required.

## Objective

Implement the A-0103 P3.2 binary/bitmask visibility contract as a production-worker sidecar while preserving the validated generalized `BakedSectionMesh` rendering path and keeping P3.3 greedy rectangle extraction out of scope.

## Binary visibility representation

Added `BinarySectionVisibility`, a pure immutable-output topology primitive built only from `SectionSnapshot`.

Representation:

- six direction masks in permanent `ReferenceFaceMesh` order: WEST, EAST, DOWN, UP, NORTH, SOUTH;
- one bit per interior 16^3 source cell for each direction;
- 4,096 bits / 64 `long` words per direction;
- 384 retained words total = 3,072 bytes per complete six-direction mask set;
- deterministic bit index `((y * 16) + z) * 16 + x`;
- four consecutive 16-bit X rows packed per retained `long`.

Visibility semantics exactly match the conservative independent oracle:

`source == SUPPORTED_FULL_CUBE && neighbor == AIR`.

`UNSUPPORTED` neighbors suppress a face and are counted exactly rather than approximated.

## Worker-local reusable scratch

Each mesh worker now owns `BinarySectionVisibility.BuildScratch`:

- 18x18 supported-row masks;
- 18x18 air-row masks;
- all halo classification is read only from the immutable `SectionSnapshot`;
- no live Minecraft world/model/light/resource reads are introduced;
- retained visibility output owns only the compact directional bitsets/counts/fingerprint.

Mask generation uses machine-word row shifts/intersections for +/-X and neighboring row intersections for +/-Y and +/-Z.

## Correctness validation

Every primary mask build performs a scalar self-validation against the immutable snapshot during this correctness-first dev4 milestone.

On the existing worker determinism cadence (first local completion and then every 64 local completions), the worker also:

1. builds a second binary mask and requires exact `contentEquals` determinism;
2. independently builds the permanent simple `ReferenceFaceMesh` from the immutable snapshot;
3. validates the reference against the snapshot;
4. validates mask/reference exact set equivalence via:
   - equal visible face count;
   - equal unsupported-neighbor blocked-face count;
   - inclusion of every independently emitted reference face in the corresponding optimized directional mask.

Equal count plus complete reference inclusion proves that the optimized mask has neither missing nor extra faces for the conservative cube oracle.

The reference oracle does not call or share the optimized mask algorithm.

## Production worker integration

Every real `SectionMeshWorkerPool` job now builds the binary visibility sidecar before the existing generalized `BakedSectionMesh`.

A completed ticket publishes both:

- `BinarySectionVisibility visibility` — P3.2 topology sidecar;
- `BakedSectionMesh mesh` — unchanged validated production drawable output.

The render/GPU path still consumes `BakedSectionMesh`; dev4 therefore cannot silently change geometry while the new topology path is being validated.

New worker metrics include:

- visibility builds;
- total/max visible faces;
- WEST/EAST/DOWN/UP/NORTH/SOUTH face totals;
- total retained visibility bytes;
- total/max visibility build time;
- visibility scratch uses/high-water supported rows;
- visibility determinism audits/matches;
- independent reference audits/matches.

## Runtime gate

`FrameCoordinator` now logs `binaryVisibilityEvidenceReady` separately from the existing P3.1 gates.

It requires:

- existing `phase3GateReady=true`;
- nonzero visibility builds and visible faces;
- at least one primary mask for every completed worker job;
- exact six-direction sum == total visible faces;
- exactly 3,072 retained bytes per primary mask build;
- reusable visibility scratch use;
- nonzero deterministic audits with matches == audits;
- nonzero independent reference audits with matches == audits;
- clean workers, staging, arena and deferred resources.

The final log explicitly identifies `binaryVisibilitySidecarIntegrated=true` and `greedyRectangleEmission=false`.

The historical fixed-anchor P2.6 proof remains reported but is not a P3.2 runtime requirement; that dependency was already closed in A-0101.

## Exact CI evidence

CI progression:

- pure primitive head `677d5d962415084d35b6f124e95c84a5cc3ab8e6`: exact run `32583490387`, build + artifact upload success;
- worker-integrated head `d641dc2c0035895e0cd3b529375424ec0aa89a1a`: exact run `32583621620`, Java 25 / Gradle 9.5.1 build + artifact upload success, release skipped;
- canonical bootstrap-synchronized code head `ab394076853d2647340c8eb4f2983ec842823938`: exact run `32583676238`, Java 25 / Gradle 9.5.1 build + artifact upload success, release skipped.

Final artifact:

- artifact id `9478459893`;
- GitHub artifact name `obsidian-0948d8608e4b0d6759c000541d7f2249ffbdbb80`;
- wrapper digest `sha256:09c68667008fa5d1071f298926b80801b6cf4031054c4a7159078efdc998260b`;
- wrapper size `415,034` bytes.

## Canonical runtime package

`Obsidian-0.3.0-phase3-dev4.jar`

- size: `285,246` bytes;
- SHA-256: `93211c45bae44f927fc3946c30ec336d3ad41ea6a015992f395ab669b9a8d14e`.

Sources JAR:

- size: `148,663` bytes;
- SHA-256: `d75f495e9731d90266ef4334f8e8ef16fa0136441557e1b7756359ff81f4f346`.

Packaged `fabric.mod.json` verified:

- version `0.3.0-phase3-dev4`;
- Minecraft `~26.2`;
- Java `>=25`.

Package inspection confirmed:

- `BinarySectionVisibility.class`;
- `BinarySectionVisibility$BuildScratch.class`;
- updated `SectionMeshWorkerPool` worker/ticket classes;
- updated `FrameCoordinator`.

## Reference runtime required

Use the canonical dev4 JAR on the reference Vulkan system:

1. enter ordinary surface terrain and wait for the async 3x3 scene to become READY;
2. visually inspect for missing/duplicate/stale geometry;
3. break/place blocks and wait for a READY rebuild;
4. perform F3+T and wait for another READY rebuild;
5. optional normal movement/recenter is useful but the old long-distance fixed-anchor unload/return sequence is not required again;
6. exit normally and preserve the complete Prism log.

Required P3.2 closure evidence:

- `phase3GateReady=true`;
- `schedulerEvidenceReady=true`;
- `binaryVisibilityEvidenceReady=true`;
- `binaryVisibilitySidecarIntegrated=true`;
- `greedyRectangleEmission=false`;
- visibility builds > 0;
- visibility total faces > 0;
- six direction totals sum exactly to total faces;
- retained bytes == builds * 3,072;
- visibility scratch uses >= primary builds;
- determinism audits > 0 and matches == audits;
- reference audits > 0 and matches == audits;
- zero worker queue-full rejection / worker failure / shutdown join failure;
- existing async scene lifecycle/stale-install safety remains green;
- `workersClean=true`, `stagingClean=true`, `arenaClean=true`, `resourcesClean=true`;
- process exit code 0.

Individual directional totals are not required to all be nonzero in every terrain sample; exact total accounting and reference equivalence are the correctness gates.

## Deliberate non-goals

Still not P3.2:

- greedy rectangle extraction or merged quad emission;
- replacement of the generalized production drawable path;
- final material/light/AO merge-key construction;
- arbitrary model-quad merging;
- fluids/translucency;
- partial remeshing;
- worker-thread live-world capture.

## Next action

Run the canonical direct dev4 JAR and evaluate the complete shutdown log. Do not mark P3.2 complete or begin P3.3 until that runtime evidence is recorded.