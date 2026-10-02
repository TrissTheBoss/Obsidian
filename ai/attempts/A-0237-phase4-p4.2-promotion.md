# A-0237 - Phase 4 P4.2 promotion closure

**Date:** 2026-10-03  
**Status:** `SUCCESS / P4.2 PROMOTED`  
**Milestone:** Phase 4 P4.2 persistent chunk-column hierarchy + coarse GPU visibility  
**Version:** `0.4.0-phase4-dev2.2`

## Objective

Close Phase 4 P4.2 after the exact corrected dev2.2 Preview satisfied every frozen A-0224/A-0232 runtime and visual gate, then promote the tested implementation through a synchronized current-main branch.

## Runtime evidence

- A-0234 closed the exact-player-section recenter liveness defect and recorded explicit human **Visual PASS**.
- A-0235 closed the final corrected-package render-distance-32 scale gate:
  - 3,725 live columns;
  - 35,273 live sections;
  - 115 structural audits / 0 failures;
  - 3,650 completed coarse samples;
  - 0 missing coarse identities;
  - 0 duplicate coarse identities;
  - `gpuColumnFalseCullCount=0`;
  - clean P4.1 fine oracle;
  - clean P3.10/P3.5/P3.7/worker/staging/arena/resource evidence;
  - normal exit code 0.

Canonical public runtime:

- exact tested source: `c30c8686e65f7310ab047d88fadf2fe3b42d7415`;
- runtime JAR: `Obsidian-0.4.0-phase4-dev2.2.jar`;
- SHA-256: `571da7ea710d7e5376488b98b5e19e1edf005dd924a87b1db661948c79753d37`;
- GitHub Preview: `v0.4.0-phase4-dev2.2`;
- Modrinth version ID: `yDBeoQge`.

## Synchronized promotion

A-0236 rebuilt the tested renderer/version state on then-current `main` instead of force-merging diverged draft PR #75.

Promotion PR #77 final head:

`8f2a361ffec6b2ed198b965e8f82b8b4d4ed50a8`

Exact-head hosted validation:

- Build #840 / run `37075530851` — SUCCESS;
- Context Governor #62 / run `37075530618` — SUCCESS.

PR #77 merged `[no-release]` as:

`3b50f1e0fea11ae65c3e61689db20f03c8dd4766`

Post-merge exact-main validation:

- Build #841 / run `37076424763` — SUCCESS;
- Context Governor #63 / run `37076424599` — SUCCESS;
- Repository Hygiene #36 / run `37076424520` — SUCCESS.

Repository Hygiene returned the repository to **main only** before the next milestone branch was created.

## Result

P4.2 is **COMPLETE**.

Promoted capability:

- bounded persistent chunk-column hierarchy derived from exact P4.1 section lifecycle truth;
- exact membership/count/min-max/identity invariants;
- conservative camera-relative GPU coarse column frustum visibility;
- independent CPU coarse oracle;
- asynchronous readback;
- measured coarse candidate reduction at render distance 32;
- no camera-only full hierarchy scan;
- no production draw ownership change;
- no native graphics expansion.

P4.1 remains the fine section-level shadow control and P3.10 remains production SOLID/CUTOUT terrain draw owner.

## Next action

Freeze Phase 4 P4.3 as a separate milestone. Do not retroactively expand P4.2.

The next incremental dependency is to prove a hierarchy-fed **fine section visibility handoff** in shadow mode before temporal visibility, Hi-Z, production command compaction, or indirect-count graphics.
