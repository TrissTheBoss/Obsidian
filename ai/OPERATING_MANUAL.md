# Obsidian AI Operating Manual

## 1. Mission

Obsidian is a Minecraft Java 26.2 Fabric rendering-engine replacement focused on modern systems and Vulkan only.

The primary engineering objective is excellent frame consistency, especially 1% and 0.1% lows. Secondary objectives are smooth chunk streaming, scaling to large render distances, and high average FPS without deliberate visual degradation.

## 2. Locked project constraints

Current user-approved constraints:

- OS target for primary development/testing: Windows 11.
- Editor preference: VS Code.
- Project name: Obsidian.
- Minecraft target: Java Edition 26.2.
- Mod loader: Fabric.
- Graphics API: Vulkan only. Do not spend engineering time on an OpenGL fallback unless this decision is explicitly changed.
- Hardware target: modern systems, vendor-neutral.
- Reference test hardware: AMD Radeon RX 6800 XT, Ryzen 5 5600X, 16 GB DDR4-2666.
- Do not create NVIDIA-specific or AMD-specific renderer implementations as the baseline.
- Iris/shader-pack compatibility is not a current requirement.
- Perfect custom resource-pack/model compatibility is not a current requirement.
- Primary compatibility target is vanilla + ordinary Fabric client usage, not giant modpacks.
- Obsidian replaces Sodium rather than running beside it.
- The long-term goal is to subsume the useful rendering-side work of Sodium-style terrain optimization, ImmediatelyFast-style immediate batching, and conservative entity/visibility culling.
- Normal operating range starts around 32 chunks and should scale substantially higher.
- Visual output should have minimal differences, but pixel-perfect reproduction of inefficient vanilla methods is not required.
- Fix obvious vanilla renderer bugs instead of reproducing them intentionally.
- Experimental renderer features are allowed behind explicit toggles/auto capability checks.
- Native code is allowed if profiling proves it worthwhile, but Java/LWJGL is preferred until evidence says otherwise.
- Public release is an eventual goal. Keep code modular, documented, and non-orphaned now even though broad public compatibility is not yet the priority.

Priority order:

1. 1% / 0.1% lows and frame pacing.
2. Smooth chunk loading/streaming.
3. Very large render-distance scaling.
4. Average FPS.
5. Sensible RAM/VRAM use.

## 3. Architectural direction

The stable core should remain vendor-neutral and backend-focused rather than brand-focused.

Preferred long-term terrain path:

`Minecraft state -> compact scene extraction -> asynchronous CPU meshing -> large device-local GPU arenas -> GPU visibility/culling -> compacted indirect draw commands -> Vulkan render passes`

Baseline GPU-driven techniques should favor broadly available Vulkan functionality such as compute culling, indirect indexed drawing, indirect draw counts, large buffer arenas, staging rings, and explicit synchronization.

Mesh shaders, work graphs, experimental transparency systems, partial remeshing, and other bleeding-edge paths should remain optional until benchmarking demonstrates a real win with acceptable correctness.

The render thread must not become the place where tens of thousands of sections are walked, allocated, rebuilt, uploaded individually, or converted into many driver submissions every frame.

## 4. Engineering rules

### 4.1 Exact-version API rule

Do not assume Minecraft/Fabric renderer APIs from older versions, documentation for another mapping set, or memory.

Before depending on a nontrivial Minecraft 26.2 renderer API, validate it against the exact dependencies resolved by the project. A clean GitHub Actions build is the minimum compile-time truth source.

The Phase 0 failure documented in `ATTEMPT_LOG.md` exists specifically because an older `GpuDevice` API shape was assumed. Do not repeat that class of mistake.

### 4.2 Performance evidence rule

Do not call an optimization successful because it sounds efficient.

For meaningful renderer changes, collect evidence appropriate to the change:

- CPU frame time
- GPU frame time
- 1% low and 0.1% low
- allocation rate / GC
- chunk mesh latency
- upload latency and bandwidth
- draw count
- visible/candidate/culled section counts
- VRAM and RAM use
- worst-frame behavior while rotating, sprinting, flying, teleporting, or loading new terrain

Prefer before/after captures using the same scene and settings.

### 4.3 Visual-correctness rule

Optimization may change implementation and ordering details, but should not deliberately lower normal visual quality for FPS.

Do not silently add:

- dynamic resolution
- reduced internal resolution
- hidden render-distance reduction
- entity-distance reduction
- particle-count reduction
- texture-resolution reduction
- forced terrain LOD
- incorrect face/occlusion culling
- visibly broken transparency

Any approximation with visible tradeoffs belongs behind an explicit experimental option and must be logged.

### 4.4 Frame-pacing rule

Do not maximize background utilization at the expense of the current frame.

Chunk meshing, upload work, cache maintenance, defragmentation, and other background work should eventually be governed by frame-pressure feedback. Leaving CPU headroom is acceptable when it improves 1% lows.

### 4.5 Memory rule

The reference machine has 16 GB system RAM and 16 GB GPU VRAM. Avoid redundant long-lived Java-side mesh copies. Prefer bounded/recyclable CPU data and large suballocated device-local GPU arenas.

### 4.6 Synchronization rule

Avoid routine whole-device waits. Resource lifetime and transfer synchronization should be explicit, pipelined, and timeline/fence-driven where supported by the active Minecraft/Vulkan architecture.

## 5. Repository workflow

Canonical repository: `TrissTheBoss/Obsidian`.

For meaningful changes:

1. Read this directory first.
2. Inspect current `main` and relevant source/CI files.
3. Create a focused feature/fix branch from current `main`.
4. Make the smallest coherent change that can answer the current engineering question.
5. Build against the exact Minecraft 26.2/Fabric toolchain.
6. Record the attempt in `ai/ATTEMPT_LOG.md` even if it failed.
7. Update `ai/CURRENT_STATE.md` if the project truth changed.
8. Update `ai/DECISIONS.md` if a durable design decision changed.
9. Remove temporary probes/debug workflows once no longer needed.
10. Advance/publish only validated changes.

Do not leave unexplained diagnostic files or temporary CI workflows on `main`.

## 6. CI and release rules

The GitHub-hosted build is authoritative for compatibility with the declared project dependencies.

Current build baseline:

- Java 25
- Gradle 9.5.1
- Fabric Loom 1.17.x / project-pinned version
- Minecraft 26.2
- Fabric Loader 0.19.3+

Release rules:

- Do not publish a release from a known failing commit.
- Diagnostic commits should use the repository's no-release convention where applicable.
- Release JARs and source JARs should come from CI, not from an unrelated mock environment.
- Attach/checksum release artifacts.
- Keep release notes factual about what is and is not implemented.

## 7. Logging procedure for every meaningful attempt

Append a new entry to `ai/ATTEMPT_LOG.md` with:

- ID
- date/time if known
- objective / intended effect
- files/systems touched
- exact action or hypothesis
- result: SUCCESS / PARTIAL / FAILED / REVERTED / SUPERSEDED
- evidence
- why it worked or failed
- side effects / lessons
- next action

If an experiment is later disproven, append a new entry referencing the old ID. Do not rewrite the old result.

## 8. Handoff procedure

Before ending a substantial work session, ensure `CURRENT_STATE.md` answers:

- What version/phase are we at?
- What is known to compile?
- What is known to run on real Minecraft, if anything?
- What is not implemented yet?
- What is the next concrete engineering milestone?
- What active risks/blockers exist?
- Which branch/commit/tag is canonical?

A new agent should be able to start useful work after reading only the `ai/` directory plus the source files relevant to the next task.

## 9. Security

Never commit credentials, GitHub tokens, passwords, SSH private keys, session cookies, recovery codes, private personal data, or other secrets to this directory or the repository.
