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
