# Obsidian Decision Ledger

This file records durable decisions. Do not delete old decisions when they change; append a new decision that supersedes the old one.

## D-0001 - Vulkan only

**Status:** ACTIVE  
**Decision:** Obsidian targets Vulkan only. No OpenGL fallback is planned for the current roadmap.  
**Why:** The project targets modern systems and Minecraft 26.2 is already moving toward a Vulkan-capable Blaze3D backend. Supporting two low-level graphics paths would increase complexity and slow the renderer-replacement work.  
**Effect:** Architecture, testing, debugging, and optimization can assume Vulkan-class explicit GPU concepts.

## D-0002 - Vendor-neutral baseline

**Status:** ACTIVE  
**Decision:** The main renderer must not depend on NVIDIA-, AMD-, or Intel-specific code paths.  
**Why:** The user wants modern hardware support without vendor-specific renderer implementations.  
**Effect:** Prefer capability-based Vulkan features. Vendor extensions may be researched only as optional experiments if a future decision allows them.

## D-0003 - Frame pacing before headline FPS

**Status:** ACTIVE  
**Decision:** Optimize 1% and 0.1% lows before average FPS.  
**Why:** The user explicitly ranked lows as the most important result.  
**Effect:** Background meshing, streaming, uploads, defragmentation, and other work may deliberately leave CPU/GPU headroom when doing so improves worst-frame behavior.

## D-0004 - Large render distance is a core workload

**Status:** ACTIVE  
**Decision:** Design for 32 chunks as a normal baseline and scale substantially beyond it.  
**Why:** High render distance is a primary project goal, not an edge case.  
**Effect:** Avoid designs whose frame-critical CPU cost scales linearly with every loaded/visible section when GPU/hierarchical approaches can replace that work.

## D-0005 - Minimal visual difference, not vanilla-method fidelity

**Status:** ACTIVE  
**Decision:** Preserve intended visual quality, but do not preserve inefficient vanilla rendering methods or obvious vanilla rendering bugs merely for pixel-identical output.  
**Why:** Optimized algorithms can legitimately produce small ordering/numerical differences.  
**Effect:** No hidden visual-quality cuts in the default renderer; experimental approximations must be explicit.

## D-0006 - Obsidian replaces the optimization renderer stack

**Status:** ACTIVE  
**Decision:** Obsidian is intended to replace Sodium rather than coexist with it, and eventually make separate immediate-render/culling optimization mods unnecessary.  
**Why:** Stacking complete renderer replacements creates ownership conflicts and prevents a coherent end-to-end architecture.  
**Effect:** Detect incompatible renderer mods and fail clearly instead of allowing undefined combinations.

## D-0007 - Vanilla/Fabric-first compatibility scope

**Status:** ACTIVE  
**Decision:** Initial compatibility targets are vanilla Minecraft and ordinary Fabric usage, not Iris/shader packs, perfect exotic resource-pack/model behavior, or massive modpacks.  
**Why:** The renderer is still foundational and performance architecture is the first priority.  
**Effect:** Build clean compatibility boundaries, but do not block core work on broad ecosystem support yet.

## D-0008 - GPU-driven terrain direction

**Status:** ACTIVE  
**Decision:** The preferred long-term terrain architecture is asynchronous compact CPU meshing plus large GPU arenas, GPU visibility/culling, draw compaction, and indirect rendering.  
**Why:** This attacks frame-critical Java traversal and submission overhead, especially at high render distances and during rapid camera movement.  
**Effect:** Organize scene data and allocators so later GPU-driven visibility can be added without rewriting the entire terrain representation.

## D-0009 - Mesh shaders/work graphs are experimental, not baseline

**Status:** ACTIVE  
**Decision:** Do not make mesh shaders, work graphs, or similar bleeding-edge features requirements for the core renderer.  
**Why:** The vendor-neutral compute + indirect path provides a broader, more stable foundation.  
**Effect:** Advanced paths must prove a measurable win before becoming preferred automatically.

## D-0010 - Java/LWJGL first, native code only with evidence

**Status:** ACTIVE  
**Decision:** Start in Java/LWJGL; introduce native components only after profiling identifies a clear benefit that justifies deployment/debugging complexity.  
**Why:** JNI/native code adds lifecycle and crash complexity and should not be speculative.

## D-0011 - GitHub CI is compile/release authority

**Status:** ACTIVE  
**Decision:** The canonical release artifacts are built by GitHub CI against the real declared Minecraft/Fabric dependencies.  
**Why:** The first local Phase 0 JAR was mock-compiled and did not catch a real Minecraft 26.2 API mismatch; hosted CI did.  
**Effect:** A locally mocked JAR is never enough evidence for release compatibility.

## D-0012 - Minecraft 26.2 device metadata comes from DeviceInfo

**Status:** ACTIVE  
**Decision:** Phase 0 capability reporting uses `GpuDevice.getDeviceInfo()` and the returned `DeviceInfo`/`DeviceLimits`/`DeviceFeatures` records.  
**Why:** The exact 26.2 classes resolved by Loom showed that older direct `GpuDevice` getters do not exist.  
**Effect:** Future agents should inspect exact-version APIs before using remembered renderer interfaces.

## D-0013 - AI continuity is repository state

**Status:** ACTIVE  
**Decision:** The `ai/` directory is the persistent handoff/operating memory for future agents.  
**Why:** Chat context is not a durable project artifact and different agents/models must be able to continue from repository truth.  
**Effect:** Every meaningful experiment is logged; current truth and durable decisions are kept synchronized with code changes.

## D-0014 - Profiling must not create routine extra GPU submissions

**Status:** ACTIVE  
**Decision:** Obsidian must not implement normal per-frame GPU profiling by creating dedicated command encoders/submissions at both frame boundaries.  
**Why:** Exact Minecraft 26.2 inspection showed timestamp writes are encoded through `CommandEncoder` and become GPU work through explicit `submit()`. Adding profiler-only submissions every frame could damage frame pacing and contaminate the measurement itself.  
**Effect:** The initial Phase 1 validation uses one one-shot timestamp submission only. Long-term GPU timestamps must be integrated into command streams Obsidian already owns or into an existing submission path whose ownership/synchronization has been verified.

## D-0015 - Preserve Minecraft Vulkan device ownership until evidence requires deeper takeover

**Status:** ACTIVE  
**Decision:** Phase 1 will continue using Minecraft 26.2's active `GpuDevice` and frame lifecycle rather than creating a second Vulkan device/swapchain. Reach into backend-specific Vulkan internals only when a concrete renderer requirement cannot be met through the public abstraction and the ownership/synchronization consequences have been inspected first.  
**Why:** `0.1.0-phase1-dev1` proved on the real RX 6800 XT machine that Obsidian can observe `Minecraft.renderFrame`, submit controlled GPU commands through the existing device, retrieve timestamp results without an explicit blocking wait, enter a world, and shut down cleanly. A competing device/swapchain would add substantial lifetime, synchronization, presentation, and compatibility risk without a demonstrated need yet.  
**Effect:** The next Phase 1 work should build frame contexts, resource retirement, staging, and profiling around the proven Minecraft-owned device boundary. Native/backend-specific access remains an evidence-driven escalation path, not the default architecture.

## D-0016 - GPU resource reclamation is completion-gated, never frame-count-gated

**Status:** ACTIVE  
**Decision:** Frame-context rotation and frame serials are bookkeeping only. Obsidian may reclaim or destroy a resource only after a GPU completion primitive associated with the last submission that uses it reports completion, or after another synchronization mechanism has been specifically proven equivalent for that resource.  
**Why:** CPU frame advancement does not prove that the GPU has finished consuming commands/resources from previous frames. Dev2 validated `GpuFence.awaitCompletion(0L)` as a safe nonblocking steady-state completion check on Minecraft 26.2's Vulkan device.  
**Effect:** Deferred destruction, staging-ring reclamation, arena frees, descriptor reuse, and future upload-region reuse must be tied to real completion state. Ring slot reuse alone must never release GPU-owned memory.

## D-0017 - Staging/upload memory must be bounded and backpressured

**Status:** ACTIVE  
**Decision:** The default upload path will use a fixed-capacity staging arena/ring with explicit reclamation after GPU completion. When insufficient safe space exists, the system must apply bounded backpressure or defer uploads rather than allocate unbounded temporary upload buffers.  
**Why:** Chunk streaming at large render distances can produce bursts that would otherwise create allocation spikes, memory growth, and frame-time instability. The project's first priority is tail latency, not maximizing instantaneous upload throughput at any cost.  
**Effect:** Phase 1 upload work must expose capacity/high-water/backpressure metrics, batch copy commands where practical, and make upload admission sensitive to currently reclaimable staging capacity.

## D-0018 - Do not use Mojang MappableRingBuffer as Obsidian's hot-path staging policy

**Status:** ACTIVE  
**Decision:** Obsidian may use Minecraft 26.2's public `GpuBuffer`, persistent mapping, copy, and fence abstractions, but it will not delegate hot-path upload admission/reuse policy to Mojang's `StagingBuffer.PersistentlyMapped` / `MappableRingBuffer` implementation.  
**Why:** Exact 26.2 bytecode inspection showed `MappableRingBuffer.currentBuffer()` waits with `GpuFence.awaitCompletion(Long.MAX_VALUE)` when a rotated slot is still busy. That correctness strategy can turn upload-ring reuse into an effectively unbounded render-thread stall, directly conflicting with Obsidian's tail-latency priorities.  
**Effect:** Obsidian owns a fixed-capacity persistently mapped staging ring with explicit nonblocking fence polling and backpressure. When safe space is unavailable, upload work is deferred rather than waiting indefinitely. Mojang's low-level device/buffer API remains the backend boundary.

## D-0019 - Geometry arenas use non-mapped device-preferred backing buffers

**Status:** ACTIVE  
**Decision:** Obsidian geometry arenas use non-mapped `GpuBuffer` backing storage and receive data through the staging system.  
**Why:** Exact Minecraft 26.2 `VulkanGpuBuffer.Direct` inspection showed VMA starts from an automatic device-preferred policy and adds host-visible/coherent requirements only for map usages. Avoiding mapping flags therefore preserves the backend's device-preferred path while keeping CPU writes in the bounded staging subsystem.  
**Effect:** Portable documentation says device-preferred rather than guaranteeing literal discrete VRAM. Geometry/metadata arenas should not be host-mapped by default.

## D-0020 - Arena allocation identity is slot plus generation, never raw offset

**Status:** ACTIVE  
**Decision:** GPU arena allocations are referenced through stable handles containing slot/generation identity and state validation. Raw byte offsets are data locations, not ownership tokens.  
**Why:** Dev4 deliberately freed B, reused the exact same physical offset and metadata slot for D, advanced the generation, and successfully rejected the old B handle. Without generation validation, stale scene metadata could silently reference another chunk's geometry after reuse.  
**Effect:** All future scene/database references to arena allocations must retain generation-safe handles or an equivalent validated identity mechanism.

## D-0021 - Frame graph profiling timestamps live inside owned command streams

**Status:** ACTIVE  
**Decision:** Starting with Phase 1 dev5, GPU timestamp ranges for normal profiling must be encoded around work inside Obsidian-owned command streams/submissions. The frame graph may expose timestamped pass ranges, but it must not create extra submissions solely to obtain profiler samples.  
**Why:** Dev1 established timestamp capability, while dev3/dev4 established real owned upload/copy submissions. Obsidian now has a natural place to measure GPU work without contaminating frame pacing with profiler-only queue submissions.  
**Effect:** Dev5 should introduce fixed-capacity graph/pass metadata, submission-count metrics, nonblocking timestamp result polling, and a validation graph whose profiling is part of the same command stream that performs useful copy/validation work.