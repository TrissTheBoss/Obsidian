# A-0242 - P4.3 dev3.1 safe-extra lineage implementation handoff

**Date:** 2026-10-03  
**Status:** `IMPLEMENTED / HOSTED CI REQUIRED`  
**Milestone:** Phase 4 P4.3  
**Version:** `0.4.0-phase4-dev3.1`

## Objective

Implement the frozen A-0241 correction for the dev3 safe-extra coarse lineage defect without weakening fine visibility correctness.

## Source changes

`HierarchicalFineVisibilityProbe` now maintains three distinct fine-visibility views for each sampled handoff:

1. **CPU-coarse baseline fine set** — fine-visible identities whose parent column was CPU coarse-visible;
2. **safe-extra lineage fine set** — fine-visible identities whose parent column exists only in the conservative GPU coarse-visible set;
3. **candidate-set expected fine set** — the union of the two above over the actual GPU-coarse candidate columns.

Every section enumerated from a GPU coarse-visible column now runs the exact P4.1/P4.3 six-plane + epsilon CPU fine classifier.

The GPU fine result must match the candidate-set expected fine set exactly.

## Hard-failure policy preserved

Still fatal:

- missing candidate-set expected fine identity;
- unexpected GPU fine identity not predicted by the CPU fine classifier;
- duplicate GPU fine identity;
- snapshot identity lookup failure;
- hierarchy/section lookup failure;
- stale serial misuse;
- candidate/capacity bounds failure;
- incoherent lineage accounting.

The invariant is explicitly checked:

`candidateSetExpectedFine == baselineFineVisible + safeExtraLineageFine`

## New telemetry

P4.3 now reports:

- cumulative `safeExtraCoarseColumns`;
- cumulative `safeExtraLineageFine`;
- last-sample safe-extra coarse columns;
- last baseline fine-visible count;
- last safe-extra lineage fine count;
- candidate-set expected fine count in PASS/mismatch logs;
- lineage accounting coherence.

Existing missing/unexpected/duplicate/fine-false-cull counters remain authoritative for true fine-classifier correctness.

## Scope unchanged

No change to:

- P4.2 coarse classification;
- P4.1 flat fine visibility;
- 8,192 section-probe/frame build bound;
- lifecycle truth;
- zero-timeout normal fence polling;
- production rendering;
- command compaction;
- temporal visibility;
- Hi-Z;
- indirect-count graphics;
- native graphics ownership.

## Version

`gradle.properties` now identifies:

`0.4.0-phase4-dev3.1`

## Next action

Require exact-head hosted Build + Context Governor. If green, publish an immutable dev3.1 Preview and run the narrow A-0241 render-distance-32+ retest. P4.3 remains DRAFT / DO NOT MERGE until the corrected runtime gate closes.
