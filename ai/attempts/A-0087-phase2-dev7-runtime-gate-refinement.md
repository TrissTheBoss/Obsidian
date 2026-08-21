# A-0087 - Phase 2 dev7 runtime gate refinement: recenter is mandatory, chunk counters diagnostic

Status: **SUCCESS / VALIDATION CONTRACT REFINED BEFORE REFERENCE HANDOFF**

Date: 2026-08-21
Branch: `phase2/multi-section-scene`
PR: #27

## Trigger

The first dev7 implementation made both scene recenter and nonzero chunk load/unload counters mandatory for `sceneGateReady`.

Before handing the package to the reference machine, review of the implemented ownership model showed that requirement was unnecessarily timing-sensitive: dev7 deliberately rebinds its tracked scene center as the player moves. Chunk lifecycle events remain correctness-relevant for the currently bound 5x5 halo domain, but a recenter can move the validity domain before the old window's chunks unload and after the new center's required chunks have already loaded.

Therefore nonzero chunk event counters are not a deterministic proof of P2.7 scene correctness.

## Roadmap alignment

P2.7's canonical contract is multi-section scene integration:

- several neighboring real sections simultaneously;
- persistent scene records;
- stable camera movement;
- correct shared borders;
- bounded rebuild/upload behavior;
- retirement of one-shot probe assumptions.

P2.6 separately owns the mandatory proof that the exact Minecraft chunk load/unload hooks are observed for a fixed tracked lifecycle domain. P2.6 is still unmerged specifically because its corrected retest did not exercise that final coverage.

Making dev7 duplicate P2.6's chunk-counter closure gate would conflate two milestones and make dev7 success depend on event timing relative to scene recenter rather than on the P2.7 ownership behavior under test.

## Refinement

Dev7 continues to observe, filter, count, log and invalidate on relevant chunk load/unload events within the tracked 5x5 halo. No hook or event behavior is removed.

The dev7 shutdown machine gate now requires:

- the local multi-section scene proof to become ready;
- at least two scene-ready transitions;
- at least one scene rebuild;
- at least one real camera-driven scene recenter;
- nonzero exact section-dirty events;
- nonzero successful resource-reload events;
- `droppedLifecycleEvents=0`;
- zero stale scene installs;
- zero underlying probe stale installs;
- complete staging, device-arena and deferred-resource reclamation.

`chunkLoadEvents` and `chunkUnloadEvents` remain reported diagnostics and still invalidate the scene when they occur, but are not mandatory for P2.7 closure.

Runtime instructions were updated to make the distinction explicit: move far enough to force a scene recenter; P2.6 separately owns the mandatory exact chunk-lifecycle gate.

## Effect

This does not weaken the P2.6 dependency. PR #27 remains stacked and cannot merge before PR #25 closes its own required fixed-target chunk unload/load runtime evidence.

This refinement makes the P2.7 test deterministic with respect to the architecture it actually owns: multi-record persistence, recenter, border correctness, bounded admission and safe replacement.

This attempt is immutable once committed.
