# A-0083 - Phase 2 dev6 runtime event-bridge correction

Status: **PARTIAL RUNTIME SUCCESS / HUMAN VISUAL PASS / MACHINE GATE FAIL / CORRECTION IMPLEMENTED + CI SUCCESS / RETEST REQUIRED**

Date: 2026-08-21
Branch: `phase2/section-lifecycle-rebuild`
PR: #25
Tested package: `Obsidian-0.2.0-phase2-dev6-lifecycle-test.jar`
Tested package SHA-256: `3a0fb21a8c39d82d7f9a779523921213b198bc296711ba7b83a81fbcfd368c8e`

## Human result

The user reported that everything worked visually. The lifecycle overlay rebuilt repeatedly without a reported stale-geometry visual defect.

That human result is retained as positive evidence, but the package does **not** close P2.6 because the complete machine log failed the explicit lifecycle gate.

## Runtime facts from the complete Prism log

Environment:

- Minecraft 26.2;
- Fabric Loader 0.19.3;
- Fabric API 0.158.0+26.2;
- Java 25.0.1;
- Vulkan backend;
- AMD Radeon RX 6800 XT;
- Obsidian `0.2.0-phase2-dev6`.

Positive evidence:

- initial generation installed and persistent drawing became active;
- repeated exact `section-dirty` invalidation occurred;
- more than three rebuilds were installed (`installCount=33`, `rebuildInstalls=32` at shutdown);
- resource reload was observed (`resourceReloadEvents=1`);
- generation identity was carried capture -> build -> upload -> install;
- deterministic cube/generalized/drawable builds remained true;
- `worldReadsAfterGeneralizedCapture=0`;
- public SOLID/CUTOUT indexed-indirect rendering remained active;
- stale draw suppression and completion-gated replacement remained true;
- staging fully reclaimed (`2,873,776 / 2,873,776` bytes);
- arena allocations retired/reclaimed `66 / 66`, used bytes `0`, one full free span, fragmentation `0`;
- deferred resources retired/released `33 / 33`, pending `0`;
- process exited with code `0`.

Machine-gate failure:

- shutdown reported `lifecycleGateReady=false`;
- `chunkLoadEvents=0`;
- `chunkUnloadEvents=0`;
- `droppedLifecycleEvents=391374`;
- `observedReasons` included `event-overflow`;
- the run generated 183 renderer generation advances and 183 invalidation batches in only 7,832 frames, including a large startup overflow storm before the tracked section was bound.

## Root cause

`SectionLifecycleEvents` used one 256-entry ring for every observed lifecycle event. The exact `LevelExtractor.setSectionDirty(IIIZ)` sink emits dirtiness for all vanilla render sections, so ordinary startup/chunk streaming generated far more than 256 events between drains. Rare chunk load/unload events could be overwritten before consumption.

The same global ring sequence was used as the probe stale-build token. Therefore dirty events for completely unrelated sections could advance the sequence and falsely stale the one tracked generation.

The hook selection in A-0081 remains correct; the defect was solely renderer-side event transport/validity scoping.

## Correction

`SectionLifecycleEvents` was replaced with an allocation-free target-scoped coalescing bridge:

- no per-event ring and therefore no payload overwrite path;
- before a tracked section exists, section/chunk churn is ignored for renderer validity while world/resource events remain relevant;
- once the coordinator has a tracked section, only exact dirty-sink events for that section advance validity;
- only chunk load/unload events in the tracked section's 3x3 X/Z halo neighborhood advance validity;
- world replacement always advances validity and immediately unbinds the previous target;
- successful resource reload always advances validity;
- sticky cumulative counters are drained by delta, coalescing arbitrarily many relevant events without loss;
- `latestSequence()` is now the sequence of events relevant to the tracked renderer validity domain rather than every dirty section in the world;
- a pending world-change event wins over the coordinator's old target during drain so stale coordinates cannot be rebound for one frame;
- `REASON_OVERFLOW` remains only as an explicit internal safety-invalidation reason; the event bridge itself has no bounded queue that can overflow.

No A-0081 Minecraft hook, geometry/material/light/cutout path, Vulkan ownership rule, or public graphics path changed.

## Corrected CI/package evidence

Corrected implementation head: `6e01a8c9a5a9018eb427b42c0c1ad15fc92769d4`.

GitHub Actions run `32499586294`:

- Java 25 / Gradle 9.5.1: SUCCESS;
- Build: SUCCESS;
- Upload build artifacts: SUCCESS;
- Publish versioned release: SKIPPED.

Artifact ID: `9452971102`.

Corrected package hashes:

- `Obsidian-0.2.0-phase2-dev6.jar`: `486db6d80490170961d5a98debcb691e440e775e433283ab50b30c894821feb8`;
- sources: `786d3c2f54bdc5f1e29b6ff10f5d4009eba7398dc215606fec3bbd4e86354f0e`.

Package metadata remains exactly `obsidian 0.2.0-phase2-dev6`.

Packaged bytecode confirms `SectionLifecycleEvents` now contains target identity, `relevantSequence`, sticky dirty/load/unload/world/resource counters and target-neighborhood filtering, with no 256-entry event-ring fields.

## Required retest

P2.6 remains unmerged until a corrected-package reference run proves:

- initial install;
- at least three edit-driven rebuild installs;
- successful resource reload invalidation + rebuild;
- nonzero tracked-neighborhood `chunkUnloadEvents` and `chunkLoadEvents` after travel away and return;
- `droppedLifecycleEvents=0`;
- no overflow reason emitted by the event bridge;
- deterministic P2.5 capture/build semantics preserved;
- full completion-gated reclamation;
- process exit `0`;
- shutdown `lifecycleGateReady=true`.

The user's standing dev6 merge authorization remains valid once this corrected runtime gate passes.

This attempt is immutable once committed.
