# A-0024 - Inspect exact Minecraft 26.2 staging and mapping semantics

**Date:** 2026-08-20  
**Objective:** Determine whether Minecraft 26.2's built-in staging abstractions can satisfy Obsidian's bounded/nonblocking upload requirements or whether Obsidian needs its own staging policy.  
**Action:** Used a temporary PR-only GitHub Actions workflow to inspect the exact Loom-resolved 26.2 bytecode for `GpuBuffer`, `GpuBufferSlice.MappedView`, `CommandEncoder`, `DeviceFeatures`, `StagingBuffer` and its nested classes, `UberGpuBuffer`, and `MappableRingBuffer`. The workflow was removed after inspection.  
**Result:** `SUCCESS`.  
**Intended effect:** Establish exact map/write/copy/fence semantics and avoid guessing about hidden synchronization in Mojang's staging implementation.  
**Actual effect:** Confirmed persistent write mapping with `GpuBuffer.map(false, true)`, `MAP_WRITE | COPY_SRC` staging usage, source-first `CommandEncoder.copyToBuffer(sourceSlice, destinationSlice)`, and fence creation through the encoder. `StagingBuffer.tryAppend` returns `null` when its current write buffer lacks space. However, `StagingBuffer.PersistentlyMapped` uses `MappableRingBuffer`, whose `currentBuffer()` calls `GpuFence.awaitCompletion(Long.MAX_VALUE)` when cycling back to a busy ring slot.  
**Evidence:** PR #5 staging API inspection runs and artifacts; exact bytecode showed `MappableRingBuffer` has three buffers/fences and an effectively unbounded wait on buffer reuse.  
**Why:** Mojang's helper is designed to ensure correctness through a small rotating ring, but its reuse policy may stall rather than expose backpressure to the caller.  
**Side effects / lessons:** Obsidian should reuse Minecraft's low-level public `GpuBuffer`/mapping/copy/fence abstraction, but own staging admission/reclamation policy so the render thread can defer work instead of waiting indefinitely.  
**Next action:** Implement a fixed-capacity persistently mapped Obsidian staging ring with virtual cursors, zero-timeout fence polling, explicit backpressure, copy batching, and metrics.
