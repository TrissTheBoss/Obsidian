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

## D-0022 - Production timestamp result collection is bounded and sampled

**Status:** ACTIVE  
**Decision:** The production profiler must not call Minecraft 26.2's public `GpuQueryPool.getValues()` on every rendered frame by default. Result collection should be sampled/bounded initially, and missed/unavailable samples are preferable to waiting or producing persistent allocation pressure.  
**Why:** Exact dev5 bytecode inspection showed the Vulkan query path is nonblocking and availability-based, but the public Java API constructs an `OptionalLong[]` plus result wrappers. A profiler intended to protect 1%/0.1% lows must not quietly add routine garbage collection pressure.  
**Effect:** Dev5 may use a one-shot result read for validation. Later continuous profiling should use a configurable low-frequency sampling policy first; backend-specific/raw allocation-free result access should only be introduced if profiling shows the public wrapper allocation is materially harmful and the deeper ownership cost is justified.

## D-0023 - Keep the initial graphics path on public Blaze3D

**Status:** ACTIVE  
**Decision:** Obsidian's initial graphics-pipeline/render-pass implementation stays on Minecraft 26.2's public Blaze3D `GpuDevice`/`CommandEncoder`/`RenderPass` abstraction. Native Vulkan/backend access is added only for a concrete missing capability or a measured performance problem that cannot be solved through the public path.  
**Why:** Dev6 exact API/backend inspection and the RX 6800 XT runtime test proved custom shader precompile, pipeline caching, render attachments, indexed drawing, readback and integrated timestamps through the public Vulkan-backed API.  
**Effect:** Avoid unnecessary backend coupling while preserving an evidence-driven escape hatch for future compute/indirect/descriptor features.

## D-0024 - Greedy meshing is the default terrain meshing strategy; target a binary/bitmask implementation

**Status:** ACTIVE  
**Decision:** Obsidian's production CPU terrain mesher will use greedy face merging as its default geometry-reduction strategy. After the one-chunk correctness path is established, the performance target is a worker-local binary/bitmask greedy mesher rather than a naive per-face emission loop or allocation-heavy 2D mask implementation.  
**Why:** Classic greedy meshing drastically reduces quad count on planar voxel surfaces while remaining linear in the voxel/slice work. Modern binary-greedy implementations accelerate visibility masks and rectangle extraction with machine-word bit operations and can produce compact quad records. This directly supports Obsidian's large-render-distance and tail-latency goals. Greedy merging also integrates cleanly with voxel ambient occlusion when faces merge only when all visual merge attributes agree.  
**Correctness requirements:** A merge key must include every property that can change the rendered result across a face: face orientation, material/sprite or texture identity, render layer, tint/color state, light values, AO corner pattern/diagonal choice, UV behavior, fluid/special-face state, and any model-specific attributes required by the supported block class. Faces with incompatible attributes must never merge merely because their blocks match. Chunk/section snapshots need neighbor padding/halo data so boundary visibility/AO can be computed without synchronous world reads.  
**T-junction policy:** Greedy meshes naturally create T-junctions. Do not expand every quad into a globally conforming mesh by default; that would throw away much of the simplicity/performance win. Use stable local/eye-relative positions and validate real Vulkan hardware for cracks. If artifacts are observed, prefer a targeted rasterization-safe mitigation or selective splitting rather than abandoning greedy meshing globally.  
**Effect on roadmap:** Phase 1 remains GPU infrastructure. Phase 2 proves one real chunk/section correctly with a simple reference mesher if useful for correctness. Phase 3 introduces the production asynchronous CPU mesh system and must implement/benchmark binary greedy meshing, worker-local reusable scratch, merge-key correctness, AO-aware diagonal selection, border/halo handling, and mesh-size/build-time metrics before scaling terrain throughput. Keep a simple reference mesher for differential correctness tests even after greedy becomes production.

## D-0025 - Native Vulkan interop is permitted only for the compute/storage capability absent from public Blaze3D 26.2

**Status:** ACTIVE  
**Decision:** Obsidian may use a narrow Vulkan-backend seam for compute shader compilation/pipeline creation, storage-buffer allocation, dispatch and the required synchronization when Minecraft 26.2 public Blaze3D cannot express that work. Minecraft continues to own the VkDevice, queues, submission lifecycle, swapchain/presentation and normal graphics passes.  
**Why:** Dev8 exact inspection found no public `ComputePass`, compute pipeline type, compute shader stage, storage-buffer usage or dispatch operation. The Vulkan backend nevertheless exposes the existing `VulkanDevice`, `VulkanCommandEncoder` and public `VulkanGpuBuffer` boundary needed to inject compute without creating a second renderer/device. This is the first concrete missing capability that satisfies D-0023's evidence threshold.  
**Effect:** Backend interop lives behind isolated classes/Mixin accessors and must not spread into ordinary renderer code. Public Blaze3D remains the preferred graphics path. A broader native takeover requires a separate measured/functional justification.

## D-0026 - Compute-written indirect commands require an explicit compute-write to indirect-read synchronization edge

**Status:** ACTIVE  
**Decision:** Whenever Obsidian compute writes a buffer that a following indexed-indirect draw consumes, command ordering alone is insufficient as the synchronization contract. Record an explicit Vulkan Synchronization2 dependency from compute shader storage writes to draw-indirect command reads.  
**Why:** The intended producer/consumer relationship crosses pipeline stages and memory access domains. Dev8 records `COMPUTE_SHADER + SHADER_STORAGE_WRITE` as the source and `DRAW_INDIRECT + INDIRECT_COMMAND_READ` as the destination before handing control back to the public graphics pass.  
**Effect:** Future GPU visibility/compaction passes must declare and implement equivalent resource hazards in the render graph. Barrier scope may be narrowed/optimized after profiling, but it must never be omitted merely because the commands share one encoder/submission.

## D-0027 - Baseline GPU visibility compaction keeps public fixed-count graphics and zeroes the indirect tail

**Status:** ACTIVE  
**Decision:** Until a measured need justifies native graphics/render-pass integration, Obsidian's baseline GPU visibility/compaction path will keep graphics on public Blaze3D. Compute writes a compacted indirect command list plus a visible draw count, and fully zeroes every unused tail command. Public `drawIndexedIndirect(..., maxSlots)` may then consume a fixed maximum safely because zero-index-count tail records emit no geometry. The GPU-visible count remains a first-class output even when the current graphics call does not consume it.  
**Why:** Exact Minecraft 26.2 inspection found no public count-buffer indirect draw and no `drawIndirectCount` field in `DeviceFeatures`; the Vulkan backend's public path records `vkCmdDrawIndexedIndirect`. Vulkan core 1.2 provides `vkCmdDrawIndexedIndirectCount`, but that graphics command must be integrated inside render-pass/dynamic-rendering state. Widening the native seam solely for the Phase 1 validation count would duplicate graphics ownership before profiling proves the benefit. Dev9 can validate the hard producer side—scene visibility, atomic compaction, count generation, zero-tail correctness and explicit synchronization—without doing so.  
**Effect:** Phase 1 dev9 uses `nativeComputeSeam=true`, `nativeGraphicsSeam=false`, and records `indirectCountConsumed=false`. Phase 4 may revisit native indirect-count consumption if fixed-capacity zero-tail command fetch/dispatch overhead is measured to matter. Such a change requires a separate decision covering graphics-state ownership, feature gating and fallback behavior.
## D-0028 - Treat branches as disposable work state and publish tester builds through explicit release channels

**Status:** ACTIVE  
**Decision:** `main` is Obsidian's only permanent branch. Feature, fix, inspection, docs, and maintenance branches are temporary working state and should be deleted after their PR is merged/closed once useful evidence is preserved. Historical engineering truth belongs in immutable `ai/attempts/`, durable decisions/roadmap state, closed/merged PRs, exact commit SHAs, and tags/releases rather than a growing branch archive. Tester-facing CI-green development binaries use GitHub **Prereleases** (Preview channel); validated public checkpoints use normal Releases; ephemeral CI artifacts remain build evidence. The root README is a maintained public surface and must be synchronized when phase, compatibility, install/test instructions, release channel, or major public capability changes.  
**Why:** By 2026-09-28 the repository had 56 branches while only `main` and the active P4.1 PR branch were needed. Long-lived milestone/inspection branches made the GitHub page harder to navigate without adding historical safety because the project already records exact evidence in `/ai` and PRs. GitHub recommends deleting merged/stale branches and supports prereleases as a native way to distinguish development builds from normal releases. Well-maintained open-source projects also separate concise README onboarding from detailed contributor/process documentation.  
**Effect:** `ai/REPOSITORY_HYGIENE.md` is mandatory process policy. Repository automation removes closed internal PR heads and performs a one-time legacy stale-branch sweep while preserving `main`, protected branches, and open PR heads. Normal branch-count target is <=5. CI release automation classifies development version markers as prereleases, and an explicit Preview workflow can publish an active internal PR build before merge. `[no-release]` remains appropriate for docs/continuity/evidence/maintenance-only merges.

## D-0029 - LLM context is tiered, recoverable, and external calls are data-minimized

**Status:** ACTIVE  
**Decision:** Obsidian's AI operating model uses a small tiered active context rather than preloading the complete continuity corpus. `ai/context/ACTIVE_CONTEXT.json` is a non-authoritative bootstrap/index containing current must-preserve facts and exact recovery pointers. Automatic compaction is two-phase: extract required knowledge and recovery references, validate completeness/provenance, then discard only transient/re-fetchable bulk. If critical knowledge cannot fit, compaction fails closed rather than dropping it. Before API/tool/MCP calls, agents send only the fields/records/identifiers necessary for that operation and request only the response projection/range needed next. OAuth/authorization requests use least-privilege action/resource scopes and incremental authorization where supported; credentials remain transport secrets outside durable/model context whenever the platform permits. Before high-impact mutations, agents rehydrate exact IDs/SHAs/permissions/gates from source truth.  
**Why:** Long-running tool-heavy sessions can crowd authoritative requirements and exact identifiers out of the working window, while forwarding broad context or broad authorization scopes increases privacy, security, cost, and accidental-disclosure risk. Obsidian already has durable source truth in `/ai`, source, PR/CI/release/runtime evidence, so a validated pointer-based working set can reduce context without making a model-generated summary authoritative. This design draws on virtual/tiered context approaches, explicit context editing/tool-result clearing, OAuth least privilege/incremental consent, and API field projection.  
**Effect:** `ai/skills/context-governor/SKILL.md` and `ai/context/ACTIVE_CONTEXT.json` become mandatory parts of the AI continuity system. CI runs deterministic regression tests for critical-fact preservation, fail-closed compression, recovery pointers, payload allowlisting, secret-field blocking, OAuth scope minimization, and active-capsule validation. `ai/README.md` uses progressive loading: verify current truth and rehydrate relevant canonical sections instead of automatically loading every historical file.



## D-0030 - Maintain the public project timeline as a governed non-authoritative history index

**Status:** ACTIVE  
**Decision:** Root `TIMELINE.md` is a maintained public surface and must be synchronized when the material development story changes: phase/milestone activation or completion, promotion/rejection/deferment/supersession, major project-wide operating capabilities, tester-facing milestone handoffs that materially change the project arc, or material roadmap-arc changes. The timeline is a visual/history index only and never overrides `CURRENT_STATE.md`, `MASTER_ROADMAP.md`, durable decisions, immutable attempts, source, CI/release, or runtime evidence.  
**Why:** The repository now exposes a graphical development history intended to help humans understand how Obsidian reached its current state. Without an explicit maintenance contract, that page would quickly become stale and could misrepresent active status or historical progression. Treating it as governed public documentation preserves its usefulness while avoiding a second competing source of truth.  
**Effect:** `ai/REPOSITORY_HYGIENE.md` defines exact update triggers, non-churn rules, required timeline structure, and handoff checks. `ai/OPERATING_MANUAL.md` requires timeline synchronization when those triggers fire. `ai/README.md` identifies the timeline as a non-authoritative public index. Future agents must correct `TIMELINE.md` from authoritative evidence when conflicts appear rather than using it to decide engineering truth.


## D-0031 - Mirror public builds to Modrinth without changing package authority

**Status:** ACTIVE  
**Decision:** Modrinth is a user-facing distribution mirror for Obsidian releases, while GitHub CI/release evidence remains the canonical source/package authority. Successful GitHub tester-facing release flows may mirror automatically to Modrinth from the exact source commit after the GitHub release step succeeds, including idempotent backfill when the GitHub release already existed before Modrinth mirroring was enabled. Development versions containing `dev`, `alpha`, or `preview` publish as Modrinth `alpha`; `beta`, `rc`, or `pre` publish as Modrinth `beta`; validated versions without a prerelease marker publish as Modrinth `release`. CI-only artifacts are not mirrored. The runtime JAR is primary and the sources JAR is supplementary.  
**Security:** The Modrinth PAT is never committed, copied into `/ai`, or placed in workflow source. GitHub Actions injects it from repository secret `MODRINTH_PAT` (secret names are case-insensitive). The Modrinth project identifier may be supplied by repository variable `MODRINTH_PROJECT_ID`, repository secret `MODRINTH_PROJECT_ID`, or a manual workflow input for recovery/first-time setup. Storing the non-secret project ID as a secret is supported when repository configuration is organized that way. External publication code is pinned to the exact `cloudnode-pro/modrinth-publish` v2.5.1 commit rather than a floating tag.  
**Reliability:** Publication checks Modrinth for an existing matching `version_number` before creating a version; existing versions are left unchanged. An authenticated list-versions HTTP 200 remains the duplicate-check path. HTTP 404 is treated as unknown/not-listed and may proceed to the pinned VERSION_CREATE action so hidden/draft read visibility does not block a permitted write; other unexpected lookup statuses fail closed. Manual `Publish Modrinth` dispatch can rebuild and publish an exact branch/tag/SHA when automatic mirroring was unavailable or intentionally skipped.  
**Effect:** GitHub Stable/Preview terminology and validation gates remain unchanged. Modrinth channel labels are a distribution mapping, not a new maturity model. A Modrinth upload never promotes a milestone or supersedes the exact GitHub/CI package identities recorded in `CURRENT_STATE.md` and immutable attempts.
