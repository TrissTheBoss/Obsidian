# A-0114 - Phase 3 P3.4 dev6 runtime behavior healthy but shutdown tail missing

**Date:** 2026-08-22  
**Branch:** `phase3/render-correct-merge-key`  
**Canonical PR:** #38 against `main`  
**Version:** `0.3.0-phase3-dev6`  
**Result:** `PARTIAL` — observed gameplay/runtime behavior is healthy and the human visual regression check passed, but the submitted Prism paste ends before the frozen shutdown gate is printed.

## Objective

Validate A-0112/A-0113 on the reference Windows 11 / Radeon RX 6800 XT Vulkan system and close dev6 only if the complete final `FrameCoordinator.close()` evidence plus launcher exit code are recorded.

## Runtime package

The user ran the canonical CI-built package:

- `Obsidian-0.3.0-phase3-dev6.jar`;
- size `308,439` bytes;
- SHA-256 `2d2664d1eb6fc844cf70cefabb11400752da20866f4e1f1a79ca3873ea55019a`.

The launch log identifies Minecraft 26.2, Fabric Loader 0.19.3, Java 25.0.1, Vulkan, and AMD Radeon RX 6800 XT.

## Positive runtime observations

The supplied log records all intended pre-shutdown exercise classes:

- dev6 P3.4 bootstrap and frame-coordinator activation;
- Vulkan attachment on the reference RX 6800 XT;
- initial 3x3 async scene READY with 9 live records;
- repeated section-dirty invalidation/rebuild cycles returning to READY;
- F3+T resource reload followed by another READY scene;
- ordinary scene recenter movement across multiple centers;
- production worker installs with `synchronousSceneMeshBuilds=0` and `worldReadsAfterGeneralizedCapture=0` in the visible runtime lines;
- final visible READY state before shutdown activity reached generation 45 with 225 record/worker-result installs and 9 live records;
- normal single-player server shutdown sequence began and world data was saved.

The user explicitly reported that the visuals **look fine**. Because dev6 does not change GPU-emitted geometry, this satisfies the intended human visual regression guard for the observed session.

## Evidence gap

The pasted log does **not** contain the required final client shutdown tail. It ends after the server shutdown/world-save sequence with:

- a final `world-change` scene invalidation at generation 46, center `unbound`;
- `Clipboard copy at: 22 Aug 2026 23:56:13 +0200`.

The paste contains no later:

- `[Render thread/INFO]: Stopping!`;
- `Phase 3 dev6 P3.4 frame coordinator closed after ...` line;
- actual final `renderMergeKeyEvidenceReady=...` result and render-key counters;
- final worker/staging/arena/resource cleanliness values;
- `Process exited with code 0` launcher line.

The earlier runtime-instruction line naming `renderMergeKeyEvidenceReady=true` is only the requested gate description and is **not** runtime closure evidence.

## Frozen gate remains unwaived

A-0112 requires real reference runtime closure including:

- `phase3GateReady=true`;
- `schedulerEvidenceReady=true`;
- `binaryVisibilityEvidenceReady=true`;
- `greedyRectangleEvidenceReady=true`;
- `renderMergeKeyEvidenceReady=true`;
- exact render-key visible/eligible/unmapped/ambiguous accounting;
- nonzero unique mappings and recognized canonical baked quads;
- both same-key and different-key adjacency observations;
- exactly 49,152 retained render-key bytes per build;
- matching nonzero render-key determinism audits;
- zero worker failure/rejection/shutdown-join failure;
- clean workers/staging/arena/resources;
- normal process exit code 0.

Those final values cannot be inferred from the truncated paste and are therefore not claimed.

## Merge decision

PR #38 remains **unmerged** despite standing user merge authorization. Authorization removes the need to ask again; it does not waive A-0112's completion gate.

No code defect is established by this run, and no binary change is required from the available evidence. The same canonical dev6 JAR remains the correct package for closure. Only a complete Prism tail captured after the Minecraft process has fully exited is needed to determine whether the frozen runtime gate passed.

## Next action

Obtain the complete final Prism output after process exit. If the final coordinator line and exit code satisfy A-0112 exactly, record a new immutable runtime-success attempt, run evidence-head CI, merge PR #38 with `[no-release]`, and continue with the next P3.4 slice rather than jumping directly to P3.5.