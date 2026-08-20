# A-0025 - Implement Phase 1 dev3 bounded staging/upload foundation

**Date:** 2026-08-20  
**Objective:** Build the first Obsidian-owned bounded upload path before terrain starts producing geometry.  
**Action:** Added `StagingUploadArena` and `GpuUploadProbe`; integrated them into `FrameCoordinator`; removed the completed dev2 lifetime probe; bumped the development version to `0.1.0-phase1-dev3`; removed the temporary staging API workflow after inspection.  
**Result:** `PARTIAL`: hosted compile/build `SUCCESS`, real Vulkan runtime validation pending.  
**Intended effect:** Prove a fixed-capacity persistently mapped staging ring can admit uploads without unbounded allocation, encode multiple copies into one submission, reject excess capacity through backpressure, reclaim ring space only after a real fence completes, and verify deterministic copied bytes.  
**Actual effect:** The implementation uses a 256 KiB validation staging buffer with `MAP_WRITE | COPY_SRC`, 16-byte alignment, monotonic virtual write/reclaim cursors, a fixed 64-entry in-flight batch table, zero-timeout steady-state fence polling, a two-copy one-submission validation batch, an intentional full-capacity rejection, and read-mapped destination verification after completion. GitHub Actions compiled the clean code and uploaded artifacts successfully.  
**Evidence:** Draft PR #5; clean implementation branch `phase1/staging-upload`; Java 25 / Gradle 9.5.1 Build workflow for the dev3 implementation completed build and artifact upload successfully.  
**Why:** Exact 26.2 APIs provide the necessary low-level primitives while Obsidian keeps control over when to defer work instead of blocking on buffer reuse.  
**Side effects / lessons:** The Blaze3D `GpuBuffer.slice(...)` abstraction creates slice records per encoded copy; this is acceptable for the current correctness milestone but should be profiled before high-volume terrain uploads. Persistent mapping is currently required by dev3; a future capability decision may add a bounded non-persistent fallback if needed.  
**Next action:** Run `0.1.0-phase1-dev3` on the reference RX 6800 XT Vulkan instance and confirm: two copied regions verify correctly, exactly one backpressure event occurs, submitted/reclaimed bytes are 256, no upload batches remain pending, world entry succeeds, and shutdown is clean.
