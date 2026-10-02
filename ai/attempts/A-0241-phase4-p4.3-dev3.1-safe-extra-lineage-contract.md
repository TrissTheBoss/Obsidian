# A-0241 - P4.3 dev3.1 safe-extra lineage correction contract

**Date:** 2026-10-03  
**Status:** `FROZEN BEFORE SOURCE CHANGE`  
**Milestone:** Phase 4 P4.3  
**Target version:** `0.4.0-phase4-dev3.1`

## Objective

Correct the P4.3 validation oracle exposed by A-0240 without weakening fine visibility correctness.

## Problem

P4.2 intentionally accepts GPU-only conservative coarse columns because its CPU oracle includes Minecraft `Frustum.isVisible(...)` while the GPU uses extracted planes + epsilon.

P4.3 dev3 built candidates from GPU coarse-visible columns but built the CPU expected fine set only from CPU coarse-visible columns.

That makes a fine-visible section from a GPU-only safe coarse column appear as a fatal `unexpectedFine`, even though it is conservative and consistent with the P4.1/P4.3 fine plane policy.

## Frozen correction

For each section enumerated from the actual **GPU coarse-visible candidate columns**:

1. run the exact P4.1/P4.3 CPU fine plane classifier;
2. if fine-visible/ambiguous, add its identity to the **candidate-set expected fine set**;
3. if its parent column is also CPU coarse-visible, add it to the **CPU-coarse baseline fine set**;
4. otherwise record it as a **safe-extra coarse lineage fine identity**.

The GPU fine output must match the candidate-set expected fine set exactly.

Required hard-failure conditions remain:

- any missing candidate-set expected fine identity;
- any unexpected identity not predicted by the candidate-set CPU fine oracle;
- any duplicate GPU fine identity;
- any snapshot/section lookup inconsistency;
- any stale-serial use;
- any capacity/bounds failure.

## Baseline accounting

Keep the original CPU-coarse baseline so telemetry can distinguish:

- baseline fine-visible identities;
- safe-extra coarse columns;
- safe-extra coarse lineage fine identities;
- total candidate-set expected fine identities.

Required invariant:

`candidateSetExpectedFine = baselineFineVisible + safeExtraLineageFine`

and the GPU fine set must equal `candidateSetExpectedFine` exactly.

A safe-extra lineage identity is **not** counted as `unexpectedFine`.

## Required telemetry

Add/retain at least:

- `safeExtraCoarseColumns`;
- `safeExtraLineageFine`;
- `baselineFineVisible`;
- candidate-set expected fine visible;
- GPU fine visible;
- missing/unexpected/duplicate fine;
- `gpuHierarchicalFineFalseCullCount`;
- candidate counts/reduction;
- snapshot lookup failures;
- build probes/frames;
- readback pending high-water;
- hard failure;
- unchanged ownership/scope flags.

## Scope preserved

Do not change:

- P4.2 coarse classifier behavior;
- P4.1 flat fine classifier;
- production terrain rendering;
- candidate build budget;
- asynchronous fence/readback model;
- lifecycle truth;
- command compaction;
- temporal visibility;
- Hi-Z;
- native graphics ownership;
- indirect-count graphics.

## Runtime closure gate

The corrected dev3.1 package must reproduce the prior boundary case under render distance 32+ and finish with:

- non-zero completed P4.3 samples;
- zero snapshot lookup failures;
- zero missing fine;
- zero true unexpected fine;
- zero duplicate fine;
- `gpuHierarchicalFineFalseCullCount=0`;
- safe-extra lineage counters allowed to be non-zero;
- P4.2 missing/duplicate/false-cull counters zero;
- P4.1 exact;
- inherited production/lifetime gates clean;
- normal exit;
- human Visual PASS unless a new visual regression appears.

Only after this corrected package closes the gate may P4.3 promote and the next Phase 4 milestone be frozen.
