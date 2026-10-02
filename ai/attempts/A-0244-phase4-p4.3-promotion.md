# A-0244 - Phase 4 P4.3 promotion closure

**Date:** 2026-10-03  
**Status:** `SUCCESS / P4.3 PROMOTED`  
**Milestone:** Phase 4 P4.3 hierarchy-fed fine section visibility  
**Version:** `0.4.0-phase4-dev3.1`

## Runtime closure

A-0243 closed every frozen A-0238/A-0241 runtime and visual gate on the exact published dev3.1 package.

Final corrected runtime:

- render distance 32;
- 2,671 completed P4.3 samples;
- snapshot lookup failures 0;
- missing fine 0;
- unexpected fine 0;
- duplicate fine 0;
- `gpuHierarchicalFineFalseCullCount=0`;
- safe-extra coarse columns 3;
- safe-extra lineage fine identities 1;
- `hardFailure=false`;
- `cameraOnlyFullSectionScan=false`;
- explicit human **Visual PASS**;
- P4.2/P4.1/P3.10/P3.5/P3.7/worker/staging/arena/resource inheritance clean;
- normal process exit code 0.

Canonical public runtime:

- source `3b38e63af1153d8ede4955da6cfc087cda5de423`;
- runtime SHA-256 `076dfdadea03d51fd46ea6804f492f73691c493a4c02cf8a3c84d8404f4ff6ef`;
- GitHub Preview `v0.4.0-phase4-dev3.1`;
- release ID `402223904`;
- Modrinth version ID `ImfQC3L5`.

## Promotion

Final PR #78 promotion head:

`3765fa1ee2416847a2df07cb175e01ebd0282c9d`

Exact-head validation:

- Build #854 / run `37078954235` — SUCCESS;
- Context Governor #75 / run `37078954108` — SUCCESS.

PR #78 merged as:

`4ba408becfcde42456b9d8468106a3e0d27de049`

Post-merge exact-main validation:

- Build #855 / run `37079294608` — SUCCESS;
- Context Governor #76 / run `37079294323` — SUCCESS;
- Repository Hygiene #38 / run `37079294312` — SUCCESS.

Repository Hygiene removed the merged PR branch, returning the repository to `main` only before P4.4 activation.

## Result

P4.3 is **COMPLETE**.

Promoted capability:

- bounded sampled hierarchy-fed fine section visibility;
- actual coarse-bounded fine candidate reduction measured at RD32;
- exact fine visible identity validation;
- conservative safe-extra lineage accounted separately from true fine mismatches;
- no camera-only full Java section scan;
- asynchronous completion-gated readback;
- no production draw ownership change;
- no native graphics expansion.

The P4.3 CPU/readback handoff remains intentionally a validation/control path. It is not production draw generation.

## Next action

Freeze P4.4 separately from validated `main`.

P4.4 may remove the **intermediate** CPU/readback dependency between coarse and fine GPU stages while retaining final sampled CPU validation. It must remain shadow-only and must not silently expand into command compaction, temporal visibility, Hi-Z, indirect-count graphics or production draw ownership.
