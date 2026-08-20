# Obsidian AI Operating Manual

This manual defines how an AI agent or human maintainer should continue Obsidian safely and consistently.

## Mission

Obsidian is a client-side Minecraft Java Fabric renderer project targeting Minecraft 26.2. Its purpose is to replace the vanilla terrain/world rendering path with a Vulkan-only, vendor-neutral renderer optimized first for frame pacing and worst-frame behavior, then for very large render distances and high average FPS.

The complete long-range product plan lives in `ai/MASTER_ROADMAP.md`. This manual defines **how to work on that plan safely**.

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

If a proposed roadmap edit changes these priorities materially, it is a product-scope change and must follow the Class D roadmap procedure in `MASTER_ROADMAP.md` rather than being treated as a normal documentation edit.

## Reference machine

- Windows 11
- AMD Radeon RX 6800 XT, 16 GB VRAM
- Ryzen 5 5600X
- 16 GB DDR4-2666
- VS Code

The reference machine is a primary runtime oracle during development, not a license to make the renderer AMD-specific.

## Continuity documents and authority boundaries

Before engineering work, understand the role of each file:

- `CURRENT_STATE.md` — current implementation/runtime truth and immediate next work.
- `MASTER_ROADMAP.md` — canonical future product/phase/feature plan.
- `OPERATING_MANUAL.md` — process and engineering discipline.
- `DECISIONS.md` — durable architectural/product rationale.
- `ATTEMPT_LOG.md` + `attempts/` — immutable evidence/history.
- source/CI artifact — actual implementation/binary truth.

Do not turn `CURRENT_STATE.md` into a copy of the roadmap. Do not use roadmap wording as evidence that a feature exists. Do not rewrite old attempt files to match a newer roadmap.

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

The detailed phase-by-phase implementation direction is in `MASTER_ROADMAP.md`.

## Vulkan/device ownership

Preserve Minecraft 26.2's active Vulkan `GpuDevice` and presentation ownership until a concrete requirement proves the public abstraction insufficient. Do not create a second Vulkan device/swapchain speculatively.

Backend-specific/native Vulkan access is an escalation path only after exact ownership, synchronization, and lifetime consequences are understood.

Current durable native-interoperability boundaries are described in `DECISIONS.md`; do not widen them because a low-level API is merely convenient.

## Synchronization and lifetime rules

- CPU frame advancement never proves GPU completion.
- Frame-context slots and serials are bookkeeping only.
- Resource reuse/destruction must be gated by a real completion primitive associated with the last submission that uses the resource, or another mechanism specifically proven equivalent.
- Normal frame processing must not intentionally wait for the GPU.
- Use zero-timeout completion polling where appropriate.
- Shutdown waits must be bounded and must not destroy resources known to still be in flight merely to make cleanup counters look clean.
- No routine `vkDeviceWaitIdle` or equivalent global idle behavior.
- Cross-stage producer/consumer memory hazards must be explicit rather than inferred from command order alone.

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
- Do not accept a performance change from one average-FPS number when lows, memory, visual semantics, or streaming behavior regressed.
- Synthetic probes prove primitives; real terrain/gameplay benchmarks are required before broad performance claims.

## Profiling direction

Eventually provide:

- CPU/GPU frame time.
- P50/P95/P99/P99.9/max.
- stage timings.
- queue sizes and latency.
- mesh/upload latency.
- RAM/VRAM usage.
- allocation/GC information.
- visible/culled section counts.
- draw/triangle counts.

Benchmark commands are planned around `/render benchmark start` / stop with JSON/CSV export or an equivalent final command surface.

The canonical metric/scenario list is maintained in `MASTER_ROADMAP.md`.

## Compatibility rules

Obsidian is intended to replace Sodium-class renderer ownership rather than stack with another complete renderer. Detect and clearly reject conflicting renderer/optimization mods where ownership would be undefined.

Initial compatibility is vanilla/Fabric-first. Broad shader-pack/resource-pack/modpack compatibility should not block renderer architecture work unless a decision explicitly changes the scope.

Unsupported render cases should be explicit and measurable rather than silently approximated into wrong output for the sake of compatibility counts.

## Development workflow

1. Read `ai/README.md` and follow its required order: `CURRENT_STATE.md`, `MASTER_ROADMAP.md`, this manual, `DECISIONS.md`, historical `ATTEMPT_LOG.md`, and the newest relevant files in `ai/attempts/`.
2. Work from repository truth, not remembered chat context.
3. Identify the roadmap phase/item the work advances. If the work does not fit the roadmap, decide whether it is a small implementation detail or an actual roadmap change before coding.
4. For unstable Minecraft renderer APIs, inspect the exact Minecraft 26.2 dependency resolved by Loom rather than guessing from another version.
5. Use a feature branch for each coherent milestone.
6. Keep PRs draft until compile validation and any required real-machine runtime validation pass.
7. GitHub CI against the real declared dependencies is the compile/package authority.
8. Do not publish a development milestone as a public release merely because it merged; use `[no-release]` when appropriate.
9. Remove temporary API-inspection/debug workflows once they have served their purpose.
10. Record every meaningful attempt whether it succeeds or fails.
11. Update `CURRENT_STATE.md` whenever project truth changes.
12. Update `DECISIONS.md` whenever a durable design/product choice changes.
13. Update `MASTER_ROADMAP.md` whenever long-range phase ordering, planned features, validation gates, experiments, compatibility/release direction, or roadmap status changes materially.
14. Synchronize material roadmap changes into the active PR/issue so reviewers understand why scope changed.
15. Before handoff, run the continuity consistency checklist below.

## Roadmap governance procedure

The full authoritative governance taxonomy is in `MASTER_ROADMAP.md` under **Roadmap governance: how this file may be changed**. This section describes the mandatory working procedure.

### Step 1 — Establish current truth before editing the plan

Read:

- active branch/PR/version from `CURRENT_STATE.md`;
- active/relevant decisions;
- latest attempts for the current milestone;
- current roadmap phase and dependent future phases.

Never alter the roadmap based solely on a chat suggestion or remembered design without checking whether repository evidence already superseded it.

### Step 2 — Classify the roadmap change

Use the roadmap's classes:

- **Class A:** status synchronization.
- **Class B:** detail refinement.
- **Class C:** roadmap restructuring.
- **Class D:** product priority/scope change.
- **Class E:** removal/rejection of a major feature.

The higher the class, the stronger the required evidence and continuity updates.

### Step 3 — Preserve reasoning before rewriting canonical plan text

When research, API inspection, profiling, a runtime failure, or a new product choice causes the change, create a new immutable attempt record.

For a durable architecture/product rule, append/supersede a decision in `DECISIONS.md`.

Only then rewrite the roadmap into its new clean canonical form. The roadmap can be edited for clarity because the immutable attempt/decision record preserves history.

### Step 4 — Never silently delete major scope

If a major planned feature is removed:

- first classify it `DEFERRED`, `REJECTED`, or `SUPERSEDED`;
- record the reason;
- identify any replacement;
- update dependent phases;
- preserve a durable reference to the old plan in attempts/decisions.

Do not simply delete the feature and leave future agents unable to discover that it was ever intended.

### Step 5 — Synchronize current state when the near-term plan changes

If a roadmap edit changes what is ACTIVE or what comes next, update `CURRENT_STATE.md` in the same coherent work.

Do not update `CURRENT_STATE.md` for every distant wording refinement if no present truth changed.

### Step 6 — Synchronize the active PR

If there is an active PR and the roadmap change materially changes that PR's purpose, exit criteria, or what follows it, update the PR body.

Avoid opening a second PR solely for roadmap docs that logically belong to the current coherent milestone.

### Step 7 — Validate evidence before changing status to COMPLETE

A roadmap item can become COMPLETE only when its contract is proven at the appropriate level.

Examples:

- compilation-only API adapter may need CI compile evidence;
- GPU synchronization path needs runtime/readback evidence;
- real terrain semantics need real-world validation;
- performance goals need benchmark evidence;
- vendor-neutral claims eventually need cross-vendor evidence.

Do not promote status from intent.

### Step 8 — Consistency review

After a roadmap edit, verify:

- priorities still agree with this manual;
- `CURRENT_STATE.md` does not contradict active phase/status;
- `DECISIONS.md` does not contain a newer conflicting rule;
- relevant attempt evidence exists;
- greedy meshing still follows D-0024 unless explicitly superseded;
- native Vulkan boundaries still follow the active decisions unless explicitly superseded;
- experimental features are not accidentally described as baseline;
- no major feature vanished without a record;
- the next concrete milestone remains clear.

## Attempt logging

Historical attempts are in `ai/ATTEMPT_LOG.md`.

New attempts should use immutable one-file-per-attempt entries under `ai/attempts/`, with globally monotonic IDs such as `A-0058-description.md`.

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

Roadmap research/restructuring is a meaningful attempt and should be logged just like API research when it materially changes engineering direction.

## Release/build rules

- Canonical binaries come from GitHub CI/release builds against real dependencies.
- A mocked/local JAR can be useful for internal logic but is not release compatibility evidence.
- Versioned development JARs may be distributed from CI artifacts for runtime validation before merge/release.
- Public releases should represent meaningful validated checkpoints.
- A documentation-only roadmap change does not change binary behavior; do not imply otherwise.

## Security

Never store or request passwords, PATs, SSH private keys, recovery codes, session cookies, or similar secrets in the repository or AI continuity files. Use authorized integrations/OAuth where available.

## Continuity consistency checklist

Before stopping a development session or handing off to another agent, verify:

- `CURRENT_STATE.md` accurately describes code/build/runtime truth;
- `MASTER_ROADMAP.md` accurately describes the current long-range plan and phase status;
- meaningful experiments/research/roadmap changes have immutable attempt records;
- durable new architecture/product choices are in `DECISIONS.md`;
- the active PR matches the real milestone scope;
- temporary diagnostic files/workflows are removed unless intentionally retained and documented;
- unvalidated behavior is labeled unvalidated rather than implied successful;
- completed roadmap items have evidence at the appropriate validation level;
- rejected/deferred/superseded major features retain a durable explanation;
- the next concrete action is explicit.

## Handoff definition of done

A handoff is not complete merely because the code compiles.

Before handoff:

- source/build state must be accurately represented in `CURRENT_STATE.md`;
- long-range scope/phase changes must be reflected in `MASTER_ROADMAP.md`;
- meaningful experiments must have an attempt record;
- durable architecture/product changes must be in `DECISIONS.md`;
- material roadmap edits must be described in the active PR/issue;
- temporary diagnostics should be removed unless intentionally retained;
- exact evidence gaps must be named;
- the next action must be clear enough that a different agent can continue without relying on chat history.
