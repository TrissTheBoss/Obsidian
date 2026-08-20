# A-0031 - Phase 1 dev5 frame graph and integrated profiler implementation

**Date:** 2026-08-20  
**Status:** COMPILE VALIDATED; RUNTIME PENDING  
**Version:** `0.1.0-phase1-dev5`

## Objective

Establish Obsidian-owned orchestration before terrain rendering: fixed graph metadata, declared pass dependencies, deliberate command-stream ownership, CPU pass timings, GPU timestamp ranges embedded in useful work, submission metrics, nonblocking query polling, and deterministic data-order validation.

## Implementation

### `FixedFrameGraph`

- fixed maximum of 16 passes;
- pass names/dependency masks defined during initialization;
- primitive arrays hold CPU begin/last/total timing and execution counts;
- execution uses bit masks and primitive state rather than allocating graph nodes;
- dependencies must reference earlier-defined passes and are checked before execution;
- graph completion requires every defined pass to execute.

### `GpuTimestampProfiler`

- owns a timestamp query pool sized at two queries per pass;
- encodes start/end timestamps directly into the useful command encoder;
- converts timestamp ticks through `DeviceInfo.timestampPeriod()`;
- query-result polling occurs only after useful submission completion and never requests a wait;
- tracks poll count and unavailable polls;
- dev5 validation uses one sample only. The exact API supports slot reset/reuse, but repeated production polling must account for the public API's result-wrapper allocation.

### `FrameGraphCommandStream`

- begins through the existing bounded `StagingUploadArena` batch admission path;
- owns one `CommandEncoder` for the graph execution;
- pass start/end couples dependency validation, CPU timing, and timestamp writes;
- uploads and dependent copies are recorded into that same encoder;
- submission occurs exactly once through `StagingUploadArena.submitBatch`, which owns the useful submission fence and staging reclamation;
- no profiler-only submission is created;
- profiler results are polled only after that useful batch is known complete.

### `FrameGraphProbe`

Two-pass nonvisual validation graph:

1. `validation-upload`: stage a deterministic 256-byte payload into GPU destination range `[0,256)`.
2. `validation-dependent-copy`: depends on pass 0 and copies that GPU range to `[512,768)` in the same command stream.

Both passes have timestamp ranges in the same submission. After the same useful submission fence completes, timestamp availability is polled and both destination regions are mapped/read to verify deterministic bytes.

Expected successful validation properties:

- graph passes = 2;
- executed mask = 3;
- one declared dependency;
- useful submissions = 1;
- profiler-only submissions = 0;
- staging submitted/reclaimed payload = 256 bytes;
- staging high-water = 256 bytes;
- staging backpressure = 0;
- deterministic verified bytes = 512 (source range plus dependent-copy range);
- query results resolve without a blocking wait;
- CPU/GPU pass timing values are reported but are hardware/run dependent;
- device arena remains initialized but unused by this probe, so its usage/allocation counters should remain zero and its one full free span should remain intact.

## Compile result

The first implementation compiled successfully in GitHub Actions against Java 25, Gradle 9.5.1, and exact Minecraft 26.2. A cleanup pass then removed the completed dev4 probe and temporary inspection workflow and improved shutdown logging to preserve the pre-close graph result. Final exact-head CI is still required before distributing the test JAR.

## Why

Dev1 proved timestamps; dev3/dev4 proved useful owned submissions and memory. Dev5 connects those foundations so future passes can be profiled without contaminating frame pacing with measurement-only queue submissions.

## Next action

Run final exact-head CI, package the CI-built `0.1.0-phase1-dev5` JAR, and test on the reference Windows 11 / RX 6800 XT Vulkan machine. Keep PR #7 draft until the graph ordering, data verification, integrated timestamp result, world entry, and clean shutdown are runtime validated.