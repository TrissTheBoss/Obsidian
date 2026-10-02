# A-0239 - Phase 4 P4.3 dev3 implementation handoff

**Date:** 2026-10-03  
**Status:** `IMPLEMENTED / HOSTED CI REQUIRED`  
**Milestone:** Phase 4 P4.3 hierarchy-fed fine section visibility  
**Version:** `0.4.0-phase4-dev3`  
**Branch:** `phase4/p4.3-hierarchy-fed-fine-visibility`

## Objective

Implement the frozen A-0238 sampled hierarchy-to-fine validation path without changing production rendering ownership.

## Source changes

### Exact section lookup

`PersistentSectionScene` now exposes a non-mutating exact coordinate lookup:

`sectionSlot(x,y,z)`

It uses the existing persistent `SectionPos` lookup map and does not change lifecycle truth or allocation behavior.

### P4.3 hierarchical fine validator

New class:

`HierarchicalFineVisibilityProbe`

The validator:

- owns a separate validation-only `VulkanLargeSceneVisibilityProbe`;
- accepts only completed conservative P4.2 coarse samples;
- copies the CPU-coarse-visible identity set for the independent fine oracle;
- maps GPU-coarse-visible identities to persistent column slots through a snapshot-scoped lookup;
- incrementally enumerates only exact occupied sections of those GPU-visible columns;
- resolves exact section identities through `PersistentSectionScene.sectionSlot(...)`;
- builds at most **8,192 section probes per frame**;
- classifies CPU fine visibility only for CPU-coarse-visible columns;
- dispatches the existing proven fine GPU classifier over the GPU-coarse-bounded candidates using the exact captured coarse-sample camera/frustum;
- compares final GPU fine identities against the CPU expected fine set;
- fails closed on missing/unexpected/duplicate fine identities or lookup inconsistency;
- uses zero-timeout normal fence polling and bounded completion-gated shutdown.

No full section-slot scan is added to camera-only frames.

### P4.2 handoff integration

`LargeSceneColumnHierarchyProbe` now:

- maintains a snapshot-scoped column identity -> persistent slot lookup only while the hierarchy snapshot is built/changed;
- polls the P4.3 validator independently each frame;
- starts a P4.3 fine sample only after the P4.2 coarse sample is conservative;
- passes the exact captured camera section/local coordinates, frustum planes and scene/hierarchy serials;
- propagates P4.3 hard failure into the shadow hierarchy failure state;
- closes the P4.3 validator through the same renderer lifecycle.

P4.2 coarse identity output and correctness accounting remain unchanged.

## Telemetry

P4.3 reports:

- samples started/completed/aborted stale/deferred;
- candidate-build probes/frames;
- snapshot lookup failures;
- upload bytes;
- missing/unexpected/duplicate fine identities;
- `gpuHierarchicalFineFalseCullCount`;
- coarse CPU/GPU visible columns;
- flat section candidates;
- hierarchy-fed fine candidates;
- candidate ratio;
- final fine-visible count;
- readback pending high-water;
- buffer sizes;
- hard failure/shutdown state;
- explicit false flags for full camera-only section scan, production ownership change, native graphics expansion, command compaction, temporal visibility and Hi-Z.

## Version identity

`gradle.properties` is now:

`0.4.0-phase4-dev3`

The bootstrap diagnostic identifies P4.3 dev3 and preserves the ownership boundary:

- P4.2 hierarchy/coarse GPU visibility is promoted foundation;
- P4.1 is the flat fine shadow control;
- P3.10 remains the only production SOLID/CUTOUT terrain draw owner.

## Scope preserved

Not added:

- production draw-list consumption;
- same-frame production GPU-to-GPU command generation;
- native graphics ownership;
- indirect-count graphics;
- temporal visibility;
- Hi-Z;
- region hierarchy;
- LOD;
- mesher or partial-remeshing changes.

## Next action

Run exact-head hosted Build + Context Governor on the draft P4.3 PR. Fix only compile/contract defects if they appear. Package/publish an immutable dev3 Preview only after the source head is CI-green.
