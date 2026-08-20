# Obsidian AI Operating Manual

This manual defines how an AI agent or human maintainer should continue Obsidian safely and consistently.

## Mission

Obsidian is a client-side Minecraft Java Fabric renderer project targeting Minecraft 26.2. Its purpose is to replace the vanilla terrain/world rendering path with a Vulkan-only, vendor-neutral renderer optimized first for frame pacing and worst-frame behavior, then for very large render distances and high average FPS.

## Primary priorities

1. Exceptional 1% and 0.1% lows.
2. Smooth chunk loading and camera movement.
3. 32 chunks as a normal baseline workload.
4. Scale to 64/96/128+ render distances.
5. High average FPS.
6. Reasonable RAM/VRAM use.
7. Minimal visual differences from intended vanilla output.
8. Fix obvious vanilla rendering defects rather than deliberately reproducing them.
9. Vulkan only.
10. Vendor-neutral baseline.
11. Vanilla/Fabric-first compatibility.
12. Clean architecture suitable for eventual public release.

Not current priorities: OpenGL fallback, Iris/shader-pack compatibility, old hardware, vendor-specific renderer backends, giant modpacks, or pixel-identical reproduction of inefficient vanilla behavior.

## Reference machine

- Windows 11
- AMD Radeon RX 6800 XT, 16 GB VRAM
- Ryzen 5 5600X
- 16 GB DDR4-2666
- VS Code

## Architecture direction

Long-term data flow:

`Minecraft/Fabric -> extraction -> world scene database -> CPU mesh system + GPU scene system -> async uploads + visibility compute -> draw compaction -> indirect rendering -> Vulkan render graph -> screen`

Core constraints:

- The render thread coordinates work but must not walk enormous chunk lists, allocate hot-path objects, perform many tiny uploads, or issue thousands of Java-side draw calls every frame.
- Keep terrain data in large GPU arenas with explicit suballocation.
- Prefer GPU visibility/culling and indirect draw generation.
- Camera turns should mostly update camera constants and let GPU work recompute visibility.
- Avoid routine device-wide waits.
- Background work must yield to frame-critical work when frame-time pressure rises.

## Vulkan/device ownership

Preserve Minecraft 26.2's active Vulkan `GpuDevice` and presentation ownership until a concrete requirement proves the public abstraction insufficient. Do not create a second Vulkan device/swapchain speculatively.

Backend-specific/native Vulkan access is an escalation path only after exact ownership, synchronization, and lifetime consequences are understood.

## Synchronization and lifetime rules

- CPU frame advancement never proves GPU completion.
- Frame-context slots and serials are bookkeeping only.
- Resource reuse/destruction must be gated by a real completion primitive associated with the last submission that uses the resource, or another mechanism specifically proven equivalent.
- Normal frame processing must not intentionally wait for the GPU.
- Use zero-timeout completion polling where appropriate.
- Shutdown waits must be bounded and must not destroy resources known to still be in flight merely to make cleanup counters look clean.
- No routine `vkDeviceWaitIdle` or equivalent global idle behavior.

## Upload/staging rules

- Upload staging must be bounded.
- Prefer fixed-capacity persistent host-visible staging storage with suballocation/ring semantics.
- Reclaim staging space only after GPU completion.
- Batch copies rather than making many tiny submissions.
- When staging capacity is exhausted, apply controlled backpressure/defer work rather than allocating unbounded temporary buffers.
- Record upload capacity, high-water usage, bytes staged/submitted/reclaimed, and backpressure events.

## Performance rules

- Tail latency wins over headline FPS.
- Do not introduce a profiler implementation that changes submission behavior enough to contaminate the measured frame pacing.
- Avoid per-frame allocation in foundational hot paths.
- Large render distance must be treated as a design workload, not an afterthought.
- No hidden visual-quality degradation to manufacture benchmark wins.

## Profiling direction

Eventually provide:

- CPU/GPU frame time.
- P95/P99/P99.9/max.
- stage timings.
- queue sizes and latency.
- mesh/upload latency.
- RAM/VRAM usage.
- allocation/GC information.
- visible/culled section counts.
- draw/triangle counts.

Benchmark commands are planned around `/render benchmark start` / stop with JSON/CSV export.

## Compatibility rules

Obsidian is intended to replace Sodium-class renderer ownership rather than stack with another complete renderer. Detect and clearly reject conflicting renderer/optimization mods where ownership would be undefined.

Initial compatibility is vanilla/Fabric-first. Broad shader-pack/resource-pack/modpack compatibility should not block renderer architecture work unless a decision explicitly changes the scope.

## Development workflow

1. Read `ai/README.md`, `CURRENT_STATE.md`, this manual, `DECISIONS.md`, historical `ATTEMPT_LOG.md`, and the newest files in `ai/attempts/`.
2. Work from repository truth, not remembered chat context.
3. For unstable Minecraft renderer APIs, inspect the exact Minecraft 26.2 dependency resolved by Loom rather than guessing from another version.
4. Use a feature branch for each coherent milestone.
5. Keep PRs draft until compile validation and any required real-machine runtime validation pass.
6. GitHub CI against the real declared dependencies is the compile/package authority.
7. Do not publish a development milestone as a public release merely because it merged; use `[no-release]` when appropriate.
8. Remove temporary API-inspection/debug workflows once they have served their purpose.
9. Record every meaningful attempt whether it succeeds or fails.
10. Update `CURRENT_STATE.md` whenever project truth changes and `DECISIONS.md` whenever a durable design choice changes.

## Attempt logging

Historical attempts are in `ai/ATTEMPT_LOG.md`.

New attempts should use immutable one-file-per-attempt entries under `ai/attempts/`, with globally monotonic IDs such as `A-0021-description.md`.

Each attempt should state:

- Date.
- Objective.
- Action.
- Result (`SUCCESS`, `PARTIAL`, `FAILED`, `REVERTED`, or `SUPERSEDED`).
- Intended effect.
- Actual effect.
- Evidence.
- Why/root cause when known.
- Side effects/lessons.
- Next action.

Do not delete failed attempts. If later evidence changes the conclusion, add a new attempt that supersedes the old one.

## Release/build rules

- Canonical binaries come from GitHub CI/release builds against real dependencies.
- A mocked/local JAR can be useful for internal logic but is not release compatibility evidence.
- Versioned development JARs may be distributed from CI artifacts for runtime validation before merge/release.
- Public releases should represent meaningful validated checkpoints.

## Security

Never store or request passwords, PATs, SSH private keys, recovery codes, session cookies, or similar secrets in the repository or AI continuity files. Use authorized integrations/OAuth where available.

## Handoff definition of done

Before stopping a development session or handing off to another agent:

- code/build state must be accurately represented in `CURRENT_STATE.md`;
- meaningful experiments must have an attempt record;
- durable new architecture choices must be in `DECISIONS.md`;
- temporary diagnostic files/workflows should be removed unless intentionally retained and documented;
- unvalidated behavior must be labeled as unvalidated rather than implied successful;
- the next concrete action should be explicit.
