# A-0030 - Phase 1 dev5 frame-graph/timestamp API inspection

**Date:** 2026-08-20  
**Status:** SUCCESS  
**Target:** exact Loom-resolved Minecraft 26.2 GPU query/command APIs

## Objective

Determine the exact timestamp-query, availability, conversion, reset/reuse, and command-encoder semantics required to put GPU profiling inside Obsidian-owned useful command streams without profiler-only submissions or blocking waits.

## Actions

Temporary GitHub Actions inspection workflows ran `javap` against the exact Minecraft 26.2 client JAR resolved by Fabric Loom. The first probe used an incorrect remembered query package; the jar class listing exposed the correct classes in `com.mojang.blaze3d.systems`. A corrected pass inspected `GpuQueryPool`, `GpuQuery`, `TimerQuery`, `CommandEncoder`, `GpuDevice`, the Vulkan query implementation, and finally `VulkanCommandEncoder` itself. The temporary workflow was removed after the findings were captured.

## Result

SUCCESS.

Exact API findings:

- `GpuDevice.createTimestampQueryPool(int)` returns `com.mojang.blaze3d.systems.GpuQueryPool`.
- `GpuQueryPool` exposes `size()`, `getValue(int)`, `getValues(int,int)`, and `close()`.
- `CommandEncoder.writeTimestamp(GpuQueryPool,int)` records timestamp work in an encoder.
- `DeviceInfo.timestampPeriod()` provides the tick-to-nanosecond scale.
- Minecraft's Vulkan query implementation requests availability data from `vkGetQueryPoolResults` and does not set the Vulkan WAIT flag; unavailable results are represented as empty optionals rather than blocking.
- `VulkanCommandEncoder.writeTimestamp` resets the exact query slot before each write using `vkResetQueryPool`, then records `vkCmdWriteTimestamp2KHR`. Query slots can therefore be reused without a separate reset submission on this backend.
- `GpuQueryPool.getValues` constructs an `OptionalLong[]` and optional wrappers for available values. The public API is nonblocking, but repeated per-frame polling would create Java allocations.
- The public Java index guard appears permissive at the upper edge in bytecode; Obsidian must still enforce the valid Vulkan range `0 <= index < pool.size()` itself.

## Intended effect

Allow GPU pass timings to be embedded in the same submission as useful Obsidian work, with nonblocking result collection and no profiler-only queue submissions.

## Actual effect

The exact API supports the intended architecture. The important caveat is Java allocation in the public query-result wrapper path, so production sampling must be bounded/sparse unless profiling proves a backend-specific raw result path is justified.

## Lessons

1. Query types are in `com.mojang.blaze3d.systems`, not the buffer package.
2. Availability polling is preferable to waiting; missing samples are acceptable.
3. Timestamp query reset is already performed by the Vulkan command encoder before writes.
4. Public nonblocking APIs can still be unsuitable for every-frame hot paths if they allocate.
5. Exact bytecode inspection remains mandatory for unstable/current Minecraft renderer APIs.

## Evidence

GitHub-hosted inspection artifacts were generated from the exact Minecraft 26.2 Loom dependency. The final Vulkan inspection explicitly included `VulkanCommandEncoder` reset/write bytecode. Temporary inspection workflow removed afterward.

## Next action

Implement dev5 as a fixed-capacity graph plus owned command-stream wrapper, with one useful validation submission containing an upload pass, a dependent GPU copy pass, and timestamp ranges around both. Keep runtime probe one-shot for clean evidence.