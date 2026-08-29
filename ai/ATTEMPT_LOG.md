# Obsidian Attempt Log

This is an append-only engineering history. Record meaningful attempts whether they succeed or fail. Do not delete failed entries; append a later entry that supersedes or explains them.

Result vocabulary: `SUCCESS`, `PARTIAL`, `FAILED`, `REVERTED`, `SUPERSEDED`.

---

## A-0001 - Build Phase 0 in a mocked local environment

**Date:** 2026-08-20 session
**Objective:** Produce the first Fabric 26.2 Phase 0 JAR and validate the bootstrap shape before real Minecraft testing.
**Action:** Created the Phase 0 project, compiled a JAR against temporary/mocked API stubs, and smoke-tested bootstrap/capability extraction with a mocked RX 6800 XT device.
**Result:** `PARTIAL` / later `SUPERSEDED` as compatibility evidence.
**Intended effect:** Catch basic Java/package/bootstrap errors and produce an early artifact.
**Actual effect:** The local mock confirmed internal control flow and packaging but falsely suggested the Minecraft-facing API calls were valid for 26.2.
**Why:** The mock represented an older/assumed `GpuDevice` API shape and was not the exact Loom-resolved Minecraft 26.2 class.
**Lesson:** Mock compilation is useful for internal logic but is not valid proof of Minecraft API compatibility. Exact dependency builds are mandatory.
**Superseded by:** A-0008 and A-0009.

---

## A-0002 - Read GitHub repository through the connected app while access was incomplete

**Date:** 2026-08-20 session
**Objective:** Establish GitHub as the canonical Obsidian repository.
**Action:** Queried `TrissTheBoss/Obsidian` through the GitHub integration.
**Result:** `FAILED`.
**Actual effect:** GitHub returned `404 Not Found` for the repository through the integration.
**Why:** At that point the repository was private/not accessible to the integration.
**Next action:** User made the repository public / corrected access.

---

## A-0003 - Re-check repository after visibility/access change

**Date:** 2026-08-20 session
**Objective:** Confirm the GitHub integration could see the canonical repository.
**Action:** Queried repository metadata again.
**Result:** `SUCCESS`.
**Evidence:** The connector returned `TrissTheBoss/Obsidian`, default branch `main`, public visibility, and repository-level admin/maintain/push/pull/triage permissions.
**Effect:** GitHub could be used as the project source of truth.

---

## A-0004 - First repository write through GitHub integration

**Date:** 2026-08-20 session
**Objective:** Seed the empty repository with Phase 0 source.
**Action:** Attempted to create the initial `README.md` on `main`.
**Result:** `FAILED`.
**Evidence:** GitHub returned `403 Resource not accessible by integration`.
**Why:** The account/repository was readable, but the installed integration did not yet have repository Contents write authority. Repository-owner permissions alone were not enough.
**Next action:** User updated/reconnected GitHub integration permissions.

---

## A-0005 - Retry repository write after permission fix

**Date:** 2026-08-20 session
**Objective:** Verify GitHub Contents write authority and initialize the repository.
**Action:** Created the initial `README.md`.
**Result:** `SUCCESS`.
**Evidence:** Commit `4023013c793a9cc0abc384b417c24d57eadd611d`.
**Effect:** Confirmed write access was fixed and allowed normal repository publication work.

---

## A-0006 - Publish initial Phase 0 source and CI/release automation

**Date:** 2026-08-20 session
**Objective:** Move Phase 0 into GitHub, make clean hosted builds authoritative, and automatically publish a release JAR/checksums.
**Action:** Created a Phase 0 baseline branch/tree/commit, added build/release workflow, README, changelog, source, VS Code configuration, and advanced `main`.
**Result:** `PARTIAL`.
**Evidence:** Baseline tree commit `013255da09603fed31933e082186a7dffca44ffb` was published; source files were readable on `main`. The expected release tag did not appear initially.
**Why partial:** Repository publication succeeded, but the real hosted build had not yet passed, so release completion could not be claimed.
**Next action:** Diagnose GitHub-hosted build rather than assuming CI was merely delayed.

---

## A-0007 - Add temporary self-reporting CI diagnostic

**Date:** 2026-08-20 session
**Objective:** Determine whether GitHub Actions was running and, if so, capture the exact build failure despite limited workflow-run visibility in the connector.
**Action:** Added a temporary workflow that ran the real Gradle build and committed a bounded tail of its output to `docs/ci-diagnostic.txt`.
**Result:** `SUCCESS` as a diagnostic; build itself `FAILED`.
**Evidence:** Diagnostic commit lineage included `04db084b39d310c97985306171d4022ffda51782`. The log recorded `build_exit_code=1` and eight `cannot find symbol` errors in `MojangVulkanBridge`.
**Failure cause found:** Minecraft 26.2 `GpuDevice` did not have the assumed direct methods `getBackendName`, `getVendor`, `getRenderer`, `getVersion`, `getImplementationInformation`, `getEnabledExtensions`, `getMaxTextureSize`, or `getUniformOffsetAlignment`.
**Effect:** Proved GitHub Actions was functioning and localized the problem to stale Minecraft API assumptions.

---

## A-0008 - Inspect exact Minecraft 26.2 GPU API on the hosted runner

**Date:** 2026-08-20 session
**Objective:** Replace guessed/old API assumptions with the exact classes resolved by Fabric Loom for Minecraft 26.2.
**Action:** Modified the temporary CI probe to locate Loom's resolved Minecraft client JAR and run `javap` on the GPU-system classes.
**Result:** `SUCCESS`.
**Evidence:** Diagnostic commit `24bbe55b91adce8960f7eb2a3484b648b85737ab`.
**Findings:**

- `GpuDevice` exposes `getDeviceInfo()`.
- `DeviceInfo` is a record with `name`, `vendorName`, `driverInfo`, `backendName`, `timestampPeriod`, `limits`, `features`, `underlyingExtensions`, `hintsAndWorkarounds`, and `type`.
- `DeviceLimits` provides `maxTextureSize`, `minUniformOffsetAlignment`, `maxMemoryAllocationSize`, multi-draw limits, anisotropy, and color attachment limits.
- `DeviceFeatures` exposes several draw/mapping capabilities.

**Why it worked:** It interrogated the exact dependency artifact used by the failing build instead of relying on external memory or another Minecraft version.

---

## A-0009 - Migrate Phase 0 capability capture to Minecraft 26.2 DeviceInfo

**Date:** 2026-08-20 session
**Objective:** Make Phase 0 compile against the real Minecraft 26.2 API and retain useful GPU capability logging.
**Action:** Reworked `MojangVulkanBridge`/`GpuCapabilities`/bootstrap logging to use `device.getDeviceInfo()`, `DeviceInfo`, `DeviceLimits`, and `DeviceFeatures`. Added a no-release safety gate while diagnostics were active.
**Result:** `SUCCESS`.
**Evidence:** Temporary hosted full-build report for commit `c688520cb8229753582fd30e9f107e758e9a3e02` recorded `build_exit_code=0`, `BUILD SUCCESSFUL`, successful `compileClientJava`, JAR creation, sources JAR creation, and build/assemble tasks.
**Why it worked:** The code now matched the exact Minecraft 26.2 class signatures resolved by Loom.
**Lesson:** Exact-version bytecode/API inspection is an accepted fallback when normal source visibility is insufficient.

---

## A-0010 - Remove temporary diagnostics and publish clean Phase 0 release

**Date:** 2026-08-20 session
**Objective:** Leave `main` clean and publish only the validated Phase 0 state.
**Action:** Removed the temporary diagnostic workflow and `docs/ci-diagnostic.txt`, advanced `main` to the clean commit, and allowed the normal release workflow to run.
**Result:** `SUCCESS`.
**Evidence:** Clean commit `0c2edec8057668870b523cdcd7e8b5005892ed48`; tag `v0.0.1-phase0` resolved to the same tree/commit state (`compare` reported identical).
**Effect:** Phase 0 source and release are canonical in GitHub.
**Important qualification:** Hosted compile/package validation is complete; real Windows 11 Minecraft runtime validation is still pending until logged separately.

---

## A-0011 - Add persistent AI continuity system

**Date:** 2026-08-20
**Objective:** Make Obsidian resumable by another model/agent without relying on private chat memory.
**Action:** Added `ai/README.md`, `ai/OPERATING_MANUAL.md`, `ai/CURRENT_STATE.md`, `ai/DECISIONS.md`, and this append-only attempt log.
**Result:** `SUCCESS` once merged/published to `main`.
**Intended effect:** Preserve operating constraints, architecture decisions, current truth, failed experiments, successful fixes, and handoff instructions as repository artifacts.
**Why:** Development continuity is part of project reliability; lost reasoning causes repeated failures and orphaned architecture.

---

## A-0012 - First real Windows 11 runtime test of v0.0.1-phase0

**Date:** 2026-08-20
**Objective:** Validate the GitHub-built Phase 0 JAR on the reference Windows 11 / RX 6800 XT system.
**Action:** User launched Minecraft 26.2 through Prism Launcher 10.0.5 with Fabric Loader 0.19.3, Fabric API 0.158.0+26.2, Java 25.0.1, and `Obsidian-0.0.1-phase0`.
**Result:** `PARTIAL`; runtime reached Obsidian backend validation, then intentionally crashed.
**Intended effect:** Confirm Fabric/bootstrap/device detection and reach the title screen using the Vulkan backend.
**Actual effect:** Fabric loaded Obsidian and `earlyInitialize()` ran. Minecraft detected the AMD Radeon RX 6800 XT, but initialized the **OpenGL** backend. Obsidian then threw `IllegalStateException` because 0.0.1 treated every non-Vulkan backend as fatal.
**Evidence:** User launch log timestamped 2026-08-20 01:36:38-01:36:50. Relevant observations: `obsidian 0.0.1-phase0` loaded; Obsidian early bootstrap logged; Minecraft logged `Using graphics backend OpenGL`; crash message was `Obsidian is Vulkan-only, but Minecraft initialized backend 'OpenGL'`; system details showed Windows 11, Ryzen 5 5600X, Radeon RX 6800 XT with ~16 GB VRAM, Java 25.0.1, and `vulkan-1.dll` loaded.
**Why:** Minecraft 26.2 release currently defaults to OpenGL. Obsidian's fatal check ran before the player could reach Video Settings and opt into `Prefer Vulkan (Experimental)`. The GPU/Vulkan loader presence indicates this was a bootstrap UX/control-flow problem, not evidence that the machine lacks Vulkan.
**Side effects / lessons:** A Vulkan-only renderer must distinguish "renderer cannot activate this session" from "Minecraft must crash." During bootstrap/configuration, OpenGL should be allowed to reach settings while Obsidian remains inactive. Only Vulkan may publish the renderer bridge as ready.
**Next action:** Patch as `0.0.2-phase0`, then retest with `Prefer Vulkan (Experimental)` selected.

---

## A-0013 - Prepare nonfatal backend-mismatch patch and version-aware releases

**Date:** 2026-08-20
**Objective:** Fix the 0.0.1 startup dead-end and make patch releases maintainable.
**Action:** On branch `fix/non-vulkan-bootstrap`, changed bootstrap flow so it inspects the active device through a temporary candidate bridge, leaves the public bridge unset on non-Vulkan, logs instructions, and returns without crashing. Removed the `failOnNonVulkan` config field so existing 0.0.1 config files cannot preserve fatal behavior. Bumped `mod_version` to `0.0.2-phase0` and made GitHub release automation derive artifact/tag names from `mod_version`.
**Result:** `PARTIAL` until hosted CI and the second real runtime test complete.
**Intended effect:** Let users reach Video Settings on Minecraft's default OpenGL backend, then activate Obsidian only after restarting with Vulkan.
**Actual effect:** Source and documentation prepared; hosted build/runtime evidence still pending at the time of this entry.
**Why:** Directly addresses A-0012 without weakening the Vulkan-only renderer architecture.
**Next action:** Run GitHub pull-request CI, publish `v0.0.2-phase0` after a clean build, then retest on the reference machine with Vulkan selected.

---

## A-0014 - Validate, merge, and release v0.0.2-phase0

**Date:** 2026-08-20
**Objective:** Prove the nonfatal backend patch compiles against the real Minecraft 26.2 dependency set and publish it as the next canonical Phase 0 test build.
**Action:** Opened PR #1 from `fix/non-vulkan-bootstrap`, ran the repository's Java 25 / Gradle 9.5.1 GitHub Actions workflow, promoted the PR from draft after CI, squash-merged it to `main`, and allowed the new version-aware release job to publish the version from `gradle.properties`.
**Result:** `SUCCESS` for hosted build/merge/release; real Vulkan runtime validation remains pending.
**Intended effect:** Replace the 0.0.1 startup dead-end with a testable 0.0.2 release while preserving Vulkan-only renderer activation.
**Actual effect:** Pull-request workflow run `32314279287` completed with conclusion `success`. PR #1 merged as commit `5ffac551e921eb7c90eacf2236071f92027aaef5`. Tag/release ref `v0.0.2-phase0` resolves to the patched repository state.
**Evidence:** PR #1 `Fix Phase 0 OpenGL startup dead-end`; CI run `32314279287`; merge commit `5ffac551e921eb7c90eacf2236071f92027aaef5`; tag `v0.0.2-phase0`.
**Why it worked:** The patch only changes bootstrap control flow/config/release automation and compiles cleanly against the already-verified 26.2 `DeviceInfo` API.
**Side effects / lessons:** Release automation should remain version-derived; do not hardcode artifact/tag names for subsequent phases. Backend mismatch is a recoverable configuration state until Obsidian actually owns Vulkan-only renderer work.
**Next action:** User should run `v0.0.2-phase0`, set Minecraft to **Prefer Vulkan (Experimental)**, restart, and provide the resulting log. Phase 1 should not begin until that Vulkan-active path reaches the title screen/world successfully.

---

## Template for the next attempt

Copy this section and replace placeholders. Do not edit previous entries.

```text
## A-XXXX - Short descriptive title

**Date:** YYYY-MM-DD
**Objective:** What should this attempt change or prove?
**Action:** What was actually changed/run/tested?
**Result:** SUCCESS | PARTIAL | FAILED | REVERTED | SUPERSEDED
**Intended effect:** What behavior/performance/correctness outcome was expected?
**Actual effect:** What happened?
**Evidence:** Commit/tag/build/profile/log/crash-report/benchmark identifiers.
**Why:** Known root cause or `unknown`.
**Side effects / lessons:** Anything future agents should retain.
**Next action:** Concrete next step.
```

---

## A-0015 - Successful real Vulkan runtime validation of v0.0.2-phase0

**Date:** 2026-08-20
**Objective:** Complete Phase 0 by proving the real Vulkan-active path on the reference Windows 11 / RX 6800 XT system.
**Action:** User launched `Obsidian-0.0.2-phase0` with Minecraft 26.2, Fabric Loader 0.19.3, Fabric API 0.158.0+26.2, Java 25.0.1, and Minecraft configured to Vulkan; then entered a new single-player world and exited normally.
**Result:** `SUCCESS`.
**Intended effect:** Prove that Obsidian can attach to Minecraft's real Vulkan `GpuDevice`, read capabilities, survive resource/world loading, and shut down cleanly.
**Actual effect:** Minecraft reported Vulkan on the AMD Radeon RX 6800 XT; Obsidian logged `Attached to Vulkan backend: Vulkan`, identified the GPU as `DISCRETE`, reported driver/capability data, entered a world successfully, and the process exited with code 0.
**Evidence:** User runtime log at 2026-08-20 01:46 local time. Reported driver `1.4.315 AMD proprietary driver 26.7.1`; exposed Vulkan extensions included `VK_KHR_synchronization2`, `VK_KHR_dynamic_rendering`, `VK_KHR_swapchain`, `VK_KHR_surface`, `VK_KHR_win32_surface`, `VK_KHR_push_descriptor`, and `VK_EXT_debug_utils`. Device features reported indirect drawing, multi-draw indirect, and persistent mapping.
**Why it worked:** The 0.0.2 patch allowed the user to select Vulkan first, and the Phase 0 bridge used the exact Minecraft 26.2 `DeviceInfo` API validated in CI.
**Side effects / lessons:** Phase 0 is complete. Mojang's Vulkan renderer also logged geometric growth of Dynamic Transforms and Chunk Sections UBO capacities during world entry; this is an observation to profile later, not proof of a performance defect.
**Next action:** Begin Phase 1 frame/GPU infrastructure.

---

## A-0016 - Inspect exact Minecraft 26.2 frame and GPU command APIs

**Date:** 2026-08-20
**Objective:** Identify a safe first frame lifecycle seam and the exact timestamp/submission interfaces before writing Phase 1 code.
**Action:** Created a temporary PR-only GitHub Actions workflow on `phase1/frame-foundation`, resolved the exact Loom Minecraft 26.2 client JAR, and used `javap` on `Minecraft`, `GameRenderer`, `RenderSystem`, `GpuDevice`, `GpuQueryPool`, `GpuQuery`, `TimerQuery`, and `CommandEncoder`. The temporary workflow was removed after inspection.
**Result:** `SUCCESS`.
**Intended effect:** Eliminate guessed renderer API design and choose a first milestone that does not introduce recurring submission overhead.
**Actual effect:** Confirmed `Minecraft.renderFrame(boolean)` as a whole-frame seam; `GpuDevice.createTimestampQueryPool(int)` and `createCommandEncoder()`; `CommandEncoder.writeTimestamp(...)` and explicit `submit()`; and nonblocking `GpuQueryPool.getValue(int)` returning `OptionalLong`.
**Evidence:** Draft PR #3; Phase 1 API Inspect workflow runs `32314947722` and `32315046100`; inspection artifacts `9387730765` and `9387757437`.
**Why it worked:** The probe interrogated the exact build dependency rather than relying on mappings or memory from another version.
**Side effects / lessons:** A dedicated start/end timestamp encoder every frame would require extra submissions and could distort frame pacing. Per-frame GPU timing must eventually be integrated into an existing/owned command stream.
**Next action:** Implement a one-shot GPU submission probe plus allocation-free CPU frame timing around `Minecraft.renderFrame`.

---

## A-0017 - Implement first Phase 1 frame foundation

**Date:** 2026-08-20
**Objective:** Give Obsidian a real render-frame lifecycle root and prove compile-time access to controlled, non-visual GPU command submission without replacing terrain yet.
**Action:** Added `FrameCoordinator`, `FrameTimings`, `GpuSubmissionProbe`, and `MinecraftFrameMixin`; attached the coordinator only after Vulkan bootstrap; added shutdown cleanup; changed version logging to derive the mod version; and marked the branch build `0.1.0-phase1-dev1`. The GPU probe writes two timestamp commands in one command encoder and performs one submission total, then polls results asynchronously on later frames.
**Result:** `PARTIAL` pending real runtime validation; hosted compile/build is `SUCCESS`.
**Intended effect:** Establish the lifecycle home for future frame contexts/profiling/resource retirement while proving Obsidian can submit GPU commands without visible rendering changes or per-frame submission overhead.
**Actual effect:** GitHub Actions compiled the exact code successfully; the temporary API-inspection workflow was removed and the clean implementation head also built successfully. Runtime behavior of the new mixin/query submission is not yet proven on the reference machine.
**Evidence:** Draft PR #3; implementation branch `phase1/frame-foundation`; clean code head `10a6e979a2cbfd5b8531ac98fb6cf3f00907d7aa`; Build workflow run `32315268985` concluded `success`.
**Why:** The implementation stays within confirmed Minecraft 26.2 public APIs and limits the validation probe to one submission.
**Side effects / lessons:** CPU frame history uses a fixed primitive ring (2048 samples) so the timing foundation does not allocate per frame. GPU query polling is only for the one-shot development probe.
**Next action:** Build the final documented branch head and test `0.1.0-phase1-dev1` on the real Vulkan machine. Confirm exactly one probe submission, asynchronous completion, world entry, and clean shutdown before merging Phase 1 foundation.

---

## A-0018 - Runtime-validate Phase 1 dev1 controlled GPU submission

**Date:** 2026-08-20
**Objective:** Prove the first Phase 1 frame lifecycle and controlled GPU command path on the real Windows 11 / RX 6800 XT Vulkan machine before merging PR #3.
**Action:** User launched `Obsidian-0.1.0-phase1-dev1` with Minecraft 26.2, Fabric Loader 0.19.3, Fabric API 0.158.0+26.2, Java 25.0.1, and the Vulkan backend; entered a single-player world; then exited Minecraft normally.
**Result:** `SUCCESS`.
**Intended effect:** Confirm the `Minecraft.renderFrame(boolean)` mixin is valid at runtime, the fixed CPU frame ring runs continuously, Obsidian can submit exactly one non-visual timestamp command buffer through Minecraft's real `GpuDevice`, query completion without an explicit blocking wait, and cleanly destroy its Phase 1 resources.
**Actual effect:** Obsidian armed the Phase 1 frame foundation, activated a 2048-sample frame timing ring, submitted the GPU probe once on frame 1, and a later nonblocking poll during the same frame iteration returned both timestamp values (`20938905848` and `20938905908`, delta 60 ticks). The player entered a world successfully. The coordinator remained active for 2107 frames and closed during normal shutdown; process exit code was 0.
**Evidence:** User Prism Launcher log dated 2026-08-20 13:12 local time. Relevant log sequence: `obsidian 0.1.0-phase1-dev1`; Vulkan RX 6800 XT; `Phase 1 frame coordinator active`; `Phase 1 GPU probe submitted on frame 1`; `Phase 1 GPU probe completed on frame 1 after 0 frame(s)`; world join; `Phase 1 frame coordinator closed after 2.107 frame(s)`; exit code 0.
**Why it worked:** The implementation uses exact Minecraft 26.2 public GPU interfaces validated in CI and does not create a competing Vulkan device. The query result was available by the later poll in frame 1; there is no explicit GPU wait in the probe.
**Side effects / lessons:** This validates controlled GPU submission and lifecycle ownership, not terrain rendering. The `after 0 frame(s)` message means completion was observed within the same frame iteration, not that Obsidian performed a blocking wait. Keep routine GPU profiling integrated into owned/existing command streams rather than adding profiler-only submissions.
**Next action:** Merge the validated frame/GPU foundation, then continue Phase 1 with rotating frame contexts, GPU-completion tracking, deferred resource destruction, and bounded upload/staging infrastructure.

---

## A-0019 - Inspect exact Minecraft 26.2 GPU resource and fence APIs

**Date:** 2026-08-20
**Objective:** Determine the exact public resource/fence semantics available for safe deferred GPU destruction before implementing Phase 1 dev2.
**Action:** On branch `phase1/resource-lifetime`, added a temporary GitHub Actions `javap` probe for `GpuFence`, `GpuBuffer`, `GpuBufferSlice`, `CommandEncoder`, and `GpuDevice` using the exact Loom-resolved Minecraft 26.2 client JAR; removed the probe workflow after the artifact was captured.
**Result:** `SUCCESS`.
**Intended effect:** Avoid guessing whether frame rotation, fences, mappings, or buffer closure could safely support resource retirement through Minecraft's public GPU abstraction.
**Actual effect:** Confirmed `CommandEncoder.createFence()`, `GpuFence.awaitCompletion(long timeoutNanos)`, `GpuDevice.createBuffer(...)`, `GpuBuffer.isClosed()/close()/slice(...)`, and `CommandEncoder.writeToBuffer(...)`.
**Evidence:** Draft PR #4; resource API inspection workflow run `32363250144`; inspection artifact `9404319017`; temporary workflow removed in commit `4487cdf67d49c0224673119cadbd8ac99078c613`.
**Why it worked:** The probe interrogated the exact dependency artifact used by the project rather than relying on another Minecraft version.
**Side effects / lessons:** CPU frame progression cannot be used as proof of GPU completion. Routine retirement can use `awaitCompletion(0L)` as a zero-timeout completion check. A bounded shutdown wait is acceptable, but unsafe destruction of an in-flight resource is not.
**Next action:** Implement preallocated frame contexts plus fence-gated deferred destruction and validate them with a tiny non-visual GPU buffer.

---

## A-0020 - Implement Phase 1 dev2 frame contexts and fence-gated resource lifetime

**Date:** 2026-08-20
**Objective:** Establish safe GPU resource retirement before Obsidian begins owning terrain buffers or large upload arenas.
**Action:** Added `FrameContext`, `FrameContextRing`, `DeferredReleaseQueue`, and `GpuResourceLifetimeProbe`; integrated them into `FrameCoordinator`; removed the completed dev1 timestamp probe; bumped the development version to `0.1.0-phase1-dev2`. The one-shot probe allocates a 64-byte `USAGE_COPY_DST` GPU buffer, writes a small payload, creates a fence, submits once, retires the resource immediately, and lets the queue close it only after the fence reports completion.
**Result:** `PARTIAL` pending real RX 6800 XT runtime validation; hosted compile/build is `SUCCESS`.
**Intended effect:** Prove that Obsidian can rotate CPU frame contexts without conflating them with GPU completion, then safely defer destruction of a resource used by submitted GPU work without a routine render-thread wait.
**Actual effect:** Exact Minecraft 26.2 compilation passed. Steady-state queue polling uses `GpuFence.awaitCompletion(0L)` only; frame contexts are preallocated; queue storage begins at 64 entries and grows only when exhausted; shutdown uses a bounded 2-second completion budget and refuses unsafe destruction if completion cannot be established.
**Evidence:** Draft PR #4 `Phase 1: frame contexts and resource lifetime`; code head `4487cdf67d49c0224673119cadbd8ac99078c613`; Build workflow run `32363597789` succeeded. Documentation-clean head `ef5cf554cec3d0dd781c5dd7efbe1f36c0a0657d` also passed Build workflow run `32363830404`. CI artifact `9404522308`.
**Why:** The design uses exact 26.2 public completion/resource APIs and keeps the normal frame path nonblocking. It deliberately treats frame serials as bookkeeping only.
**Side effects / lessons:** The validation probe still adds one extra submission total, only to prove lifetime semantics. `DeferredReleaseQueue` currently assumes one ordered submission domain; if Obsidian later owns multiple Vulkan queues, use separate completion domains or a generalized retirement model.
**Next action:** User should test `0.1.0-phase1-dev2` on the reference Vulkan machine. Do not merge PR #4 until the log proves one retirement is released after fence completion, a world can be entered, and normal shutdown reports zero pending retirements.

---

## A-0152 - P3.7 dev14 reference runtime PARTIAL: recenter exercise missing

**Date:** 2026-08-29
**Objective:** Evaluate the first dev14 reference runtime against the complete A-0150 contract.
**Action:** Ran the canonical dev14 package through the differential/lifetime gate and requested lifecycle exercise.
**Result:** `PARTIAL`.
**Actual effect:** Automated differential/lifetime evidence passed exactly, but `cameraRecenterEvents=0` and `scene-recenter` was absent, so the frozen boundary/recenter exercise was not closed.
**Evidence:** `ai/attempts/A-0152-phase3-p3.7-dev14-runtime-partial-recenter.md`.
**Next action:** Rerun the exact same package with real scene recenter and READY afterward; do not change code or waive the contract.


---

## A-0153 - P3.7 dev14 reference runtime promotion

**Date:** 2026-08-29
**Objective:** Close the remaining P3.7 runtime obligation with the exact same dev14 package.
**Action:** Re-ran dev14 through ordinary rebuild, resource reload and real async scene recenter activity, then exited normally.
**Result:** `SUCCESS`.
**Actual effect:** `differentialCorrectnessEvidenceReady=true`; `238/238` deterministic installed proofs; real merged coverage; exact material/direction/geometry/UV/ARGB/light comparisons; zero coverage/reference/mismatch defects; five scene recenters with READY afterward; clean worker/staging/arena/resource lifetime; exit code 0.
**Evidence:** `ai/attempts/A-0153-phase3-p3.7-dev14-runtime-promotion.md`; canonical dev14 SHA-256 `9d79b1de179768d5b872178564f708b42dab0d9cc8e99a0dd8f80bf10336bc39`.
**Next action:** Require exact synchronized evidence-head CI, merge P3.7 `[no-release]`, then activate/freeze P3.8 benchmarks before implementation.

---

## A-0154 - P3.8 dev15 meshing benchmark contract freeze

**Date:** 2026-08-29
**Objective:** Freeze the first P3.8 representative full-section meshing benchmark slice before source changes.
**Action:** Inspected existing production worker timing/pressure/scratch telemetry and defined a bounded percentile/window contract for queue wait, full-ticket execution, workload identity, output bytes, scratch/GC/utilization evidence and representative lifecycle exercise.
**Result:** `SUCCESS` / `PLAN FROZEN`.
**Actual effect:** P3.8 dev15 is constrained to measurement-only instrumentation over the real production path; no numerical pass threshold is invented before baseline capture, all P3.7 correctness/lifetime gates remain mandatory, and P3.9 partial remeshing is explicitly excluded.
**Evidence:** `ai/attempts/A-0154-phase3-p3.8-meshing-benchmark-contract.md`.
**Next action:** Implement bounded dev15 telemetry, exact package CI, then run the representative benchmark contract.
- **A-0155** — P3.8 dev15 benchmark implementation review: measurement-only full-section worker instrumentation implemented under frozen A-0154; fixed primitive percentile window, workload/output identity, GC/scratch/pressure evidence and layered runtime gate added; hosted package CI and reference runtime remain pending. See `ai/attempts/A-0155-phase3-p3.8-dev15-implementation-review.md`.
- **A-0156** — P3.8 dev15 package/runtime handoff: exact validation head `e6f4b81903ddcdcb859d70a1a01c002a3f550e12` passed hosted Build `33270995728`; canonical direct dev15 JAR is 456,609 bytes, SHA-256 `eaad8132665e5f662ac30f5e71abbaff3d604f010e09ffd7aa82379c79a9ed65`; reference benchmark runtime remains required and PR #51 stays draft. See `ai/attempts/A-0156-phase3-p3.8-dev15-package-runtime-handoff.md`.
- **A-0157** — P3.8 dev15 first reference benchmark runtime PARTIAL: bounded collector/accounting, percentiles, real merged workload, ordinary rebuilds, seven recenters, concurrent pressure, inherited P3.7 correctness and clean lifetime all passed, but `benchmarkResourceReloadDelta=0`, so `meshingBenchmarkEvidenceReady=false` correctly blocked promotion. No source/package defect; rerun the exact same dev15 JAR with F3+T after the measured-window arm and READY recovery. See `ai/attempts/A-0157-phase3-p3.8-dev15-runtime-partial-resource-reload.md`.
- **A-0158** — P3.8 dev15 reference benchmark runtime promotion: exact same canonical dev15 JAR closes frozen A-0154 with `meshingBenchmarkEvidenceReady=true`; 305/305 retained samples with zero overflow, measured F3+T reload delta 1, recenter delta 2, real merged workload, queue/execution percentiles, worker pressure/GC evidence, inherited P3.7 exactness, clean lifetime and exit 0. P3.8 is promotion-ready; exact synchronized-head CI and merge remain. See `ai/attempts/A-0158-phase3-p3.8-dev15-runtime-promotion.md`.
- **A-0159** — P3.9 dev16 partial-remeshing experiment contract freeze: from exact synchronized P3.8-complete main `169274b468d2a278d39043938efff19844bec9ba` / Build `33272073819`, freeze a non-render-changing shadow experiment with four fixed 4-block Y slices, exact block-local dirty provenance and mandatory full fallback, slice dependency expansion, permanent differential/unselected-slice correctness, matched full-section control, bounded telemetry and pre-frozen CPU/upload/geometry/metadata thresholds. Production GPU patching is explicitly out of scope. See `ai/attempts/A-0159-phase3-p3.9-partial-remesh-contract.md`.
- **A-0160** — P3.9 dev16 shadow partial-remeshing implementation/package: A-0159 implemented without production render/GPU-install changes; corrected helper Build `33272993694` compiled the clean source; exact clean package head `9b5930a24c8bd1841c474a03f67407231e11bc65` passed hosted Build `33273077105`; canonical direct dev16 JAR is 490,250 bytes, SHA-256 `b14640ab1a397561371564e6b3c38b93b105e481be6a32b8172b8448de701ffd`. Reference runtime/threshold decision remains required; PR #53 stays draft. See `ai/attempts/A-0160-phase3-p3.9-dev16-implementation-package.md`.
---

## A-0161 - P3.9 dev16 reference runtime failed frozen shadow gate

**Date:** 2026-08-29
**Objective:** Evaluate the exact canonical dev16 shadow partial-remeshing package against frozen A-0159.
**Action:** User ran the coherent reference workload with ordinary edits, F3+T, real scene recenter activity and normal exit.
**Result:** `FAILED`.
**Actual effect:** All inherited production/P3.7/P3.8/lifetime gates remained green, but dev16 completed only 6 localized episodes with 150 fallbacks and recorded 5/6 exact shadow episodes with one mandatory correctness failure. CPU/upload benefit evidence was also insufficient/poor, but evidence volume was too low to reject the four-slice strategy solely on ratios.
**Evidence:** `ai/attempts/A-0161-phase3-p3.9-dev16-reference-runtime-failed.md`; canonical dev16 SHA-256 `b14640ab1a397561371564e6b3c38b93b105e481be6a32b8172b8448de701ffd`.
**Lesson:** Do not rerun unchanged dev16. Retain exact first-failure and per-fallback diagnostics, and correct only source defects proven against permanent P3.7 semantics.
**Next action:** A-0162 dev17 diagnostic/correction freeze.

---

## A-0162 - P3.9 dev17 diagnostic/correction contract freeze

**Date:** 2026-08-29
**Objective:** Freeze a bounded correction/diagnostic slice before changing dev16 source.
**Action:** Proved dev16's selected-slice visibility/reference equality was stronger than permanent P3.7 semantics; froze dev17 to restore P3.7 semantics and add bounded fallback/first-failure diagnostics while leaving four slices, provenance, admission and every A-0159 threshold unchanged.
**Result:** `SUCCESS` / `PLAN FROZEN`.
**Evidence:** `ai/attempts/A-0162-phase3-p3.9-dev17-diagnostic-correction-contract.md`.
**Next action:** Implement only the frozen corrections, exact hosted package CI, then reference runtime.

---

## A-0163 - P3.9 dev17 diagnostic/correction implementation and package

**Date:** 2026-08-29
**Objective:** Produce the exact dev17 runtime package under A-0162 without changing production rendering or frozen A-0159 thresholds.
**Action:** Restored permanent P3.7 selected-reference semantics, corrected Minecraft-direction to binary-direction comparison, added exact per-fallback counters and a bounded first-failure fixture, retained unchanged admission/production paths, and validated the exact package in hosted CI.
**Result:** `SUCCESS` for implementation/package; reference runtime required.
**Evidence:** `ai/attempts/A-0163-phase3-p3.9-dev17-diagnostic-correction-package.md`; exact validation head `bce641ff08353035d6012fb5c5f5d8c06918da41`, Build `33274284466`, artifact `9721025599`; canonical dev17 JAR 495,236 bytes, SHA-256 `4f8d58251f29742afbc67d95e33a884ea72849fe099a225b154af19616ef7904`.
**Next action:** Run the exact dev17 JAR through the unchanged A-0159 workload; use the new diagnostics for any remaining correctness/admission failure rather than retuning thresholds.
---

## A-0164 - P3.9 dev17 reference runtime partial

**Date:** 2026-08-29
**Objective:** Evaluate the exact canonical dev17 diagnostic/correction package against unchanged A-0159.
**Action:** Reference runtime exercised localized edits, F3+T, real recenter and normal shutdown.
**Result:** `PARTIAL`.
**Actual effect:** Dev17 closed correctness at 19/19 exact with zero correctness, unselected-change or determinism failures; all inherited and lifetime gates remained green. Evidence volume remained short at 19/32 localized, 14/16 one-slice, 5/8 two-slice and 0/1 coalesced. Fallback diagnostics showed provenance=0, multi-section=0, halo/boundary=7, pending=9 and not-LIVE=19. CPU thresholds passed; projected upload remained 1000/1000 permille but cannot formally reject the strategy before frozen evidence volume closes.
**Evidence:** `ai/attempts/A-0164-phase3-p3.9-dev17-reference-runtime-partial.md`.
**Lesson:** Do not rerun unchanged dev17; correct the shadow episode state machine so exact same-section edits can survive pending/non-LIVE rebuild intervals.

---

## A-0165 - P3.9 dev18 pending-coalescing contract freeze

**Date:** 2026-08-29
**Objective:** Freeze the smallest correction justified by A-0164 before source changes.
**Action:** Allow only exact same-section localized dirty provenance to widen one pending immutable request across rebuild generations while preserving original pre-edit fingerprints; keep global/ambiguous/cross-section/halo/all-slice fallback behavior and every A-0159 threshold unchanged.
**Result:** `SUCCESS` / `PLAN FROZEN`.
**Evidence:** `ai/attempts/A-0165-phase3-p3.9-dev18-pending-coalescing-contract.md`.
**Next action:** Implement exactly the frozen correction, package in hosted CI, then run the unchanged reference workload.

---

## A-0166 - P3.9 dev18 pending-coalescing implementation and package

**Date:** 2026-08-29
**Objective:** Produce the exact dev18 runtime package without changing production rendering or frozen A-0159 thresholds.
**Action:** Added immutable pending request coalescing with original-fingerprint retention, exact mask/edit union, all-four/overflow rejection and final result id+mask matching; production paths and benefit accounting remain unchanged.
**Result:** `SUCCESS` for implementation/package; reference runtime required.
**Evidence:** `ai/attempts/A-0166-phase3-p3.9-dev18-pending-coalescing-package.md`; exact validation head `cfff336b1cb8ab18214d48af3521f65c4182acb3`, Build `33275004099`, artifact `9721228593`; canonical dev18 JAR 496,542 bytes, SHA-256 `cb3065a172489f197ee3f3b988fe3f202a8079ee6bafb87516f24d65d7fdf8a1`.
**Next action:** Run the exact dev18 package through unchanged A-0159. If evidence volume closes and upload P50/P95 remains above 600/800 permille, reject/redesign the fixed four-slice strategy rather than retuning thresholds.

---

## A-0171 - dev19 provenance diagnostic runtime

**Date:** 2026-08-29
**Objective:** Decompose the dominant dev18 provenance fallback bucket without changing admission behavior.
**Action:** Ran canonical `0.3.0-phase3-dev19` on the reference Windows 11 / RX 6800 XT Vulkan system using the short diagnostic workload.
**Result:** `SUCCESS` for the diagnostic objective; P3.9 remains experimental.
**Evidence:** `ai/attempts/A-0171-phase3-p3.9-dev19-provenance-runtime.md`; final diagnostic observed `40` provenance fallbacks: missing/empty `40`, off-render-thread `0`, overflow flag/events `0/0`, other `0`. First fixture was `SCANNING`, center known, pending exact episode present, drain count/flags `0/0`. Inherited P3.5-P3.7/lifetime gates remained green; P3.7 `389/389`, zero real mismatches, clean exit `0`.
**Lesson:** The dominant loss is lifecycle/provenance causal alignment, not bridge capacity or thread ownership. Do not rerun unchanged dev19.
**Next action:** Inspect exact Minecraft 26.2 dirty call shape under an immutable dev20 investigation contract.

---

## A-0172 - dev20 lifecycle/provenance causal-correlation contract

**Date:** 2026-08-29
**Objective:** Freeze a fail-closed investigation/correction scope for the missing/empty provenance path.
**Action:** Froze exact-version bytecode inspection first, permitting behavior correction only if a bounded causal rule is proven.
**Result:** `SUCCESS` / `PLAN FROZEN`.
**Evidence:** `ai/attempts/A-0172-phase3-p3.9-dev20-causal-correlation-contract.md`.
**Lesson:** Never preserve an empty invalidation merely because a pending exact episode exists; uncorrelated dirties must continue to fallback.
**Next action:** Inspect exact 26.2 `ClientLevel.setBlocksDirty` and `LevelExtractor` dirty paths on hosted Java 25 / Gradle 9.5.1.

---

## A-0173 - dev20 exact Minecraft 26.2 dirty call-shape result

**Date:** 2026-08-29
**Objective:** Explain the observed `54` plus later small dirty-event pattern before changing P3.9 behavior.
**Action:** Used hosted exact Minecraft 26.2 bytecode inspection after `compileClientJava` against Loom's resolved client JAR.
**Result:** `SUCCESS` for A-0172 Stage 1; correction not yet authorized.
**Evidence:** `ai/attempts/A-0173-phase3-p3.9-dev20-exact-callshape-result.md`; successful probe runs `33276918411` and `33276966330`. Exact bytecode shows one render-relevant block dirty expands to `3x3x3 = 27` section-dirty calls; common `54` batches are two synchronous block-dirty callbacks. A later next-frame `+1` cannot belong to that already-returned synchronous fan-out.
**Lesson:** ClientLevel TAIL ordering does not itself explain the later empty provenance. The small later event is a distinct section-dirty path whose origin must be identified.
**Next action:** Trace the outermost `LevelExtractor` origin of tracked-scene-relevant section-dirty calls without changing fallback behavior.

---

## A-0174 - dev20 section-dirty origin tracer contract

**Date:** 2026-08-29
**Objective:** Freeze a bounded diagnostic that identifies the origin of the distinct later section-dirty path.
**Action:** Froze fixed primitive outermost-scope classification for exact block, block range, neighbor range, section range, single section, and unclassified origins.
**Result:** `SUCCESS` / `PLAN FROZEN`.
**Evidence:** `ai/attempts/A-0174-phase3-p3.9-dev20-section-dirty-origin-tracer-contract.md`.
**Lesson:** Only tracked-scene-relevant dirty events may participate in correlation; unrelated world streaming must remain excluded.
**Next action:** Package the diagnostic-only tracer and run one short reference workload.

---

## A-0175 - dev20 section-dirty origin tracer implementation/package

**Date:** 2026-08-29
**Objective:** Build a canonical dev20 caller-origin diagnostic without modifying P3.9 admission or any A-0159 threshold.
**Action:** Added fixed primitive origin counters/per-drain deltas/fixtures and diagnostic Mixin scopes, then built on hosted Java 25 / Gradle 9.5.1.
**Result:** `SUCCESS` for implementation/package; short reference runtime required.
**Evidence:** `ai/attempts/A-0175-phase3-p3.9-dev20-origin-tracer-package.md`; exact implementation `ffbe60535607a242ef6b2c03c8c44066e69a63ac`, tree `9131bd8d1edf5440ae8a52817b820b17866fa2e4`, Build `33277303655` SUCCESS, artifact `9721898429`; canonical JAR 511,074 bytes, SHA-256 `690b24cd6bb34e47b3b85159eda365da3e1f76f95d41b51f0b1298fc093ed2f3`.
**Next action:** Short dev20 runtime: ~6 ordinary safe-interior edits, ~3 Y-boundary edits, one same-section burst, F3+T, one recenter, normal exit; inspect the three final dev20 origin/correlation lines.

---

## A-0176 - dev20 section-dirty origin runtime

**Date:** 2026-08-30
**Objective:** Identify the exact outer section-dirty origin paired with dev19 missing/empty provenance fallbacks.
**Action:** Ran canonical dev20 caller-origin diagnostic on the reference Windows 11 / RX 6800 XT Vulkan system.
**Result:** `SUCCESS` for diagnostic objective; P3.9 remains experimental.
**Evidence:** `ai/attempts/A-0176-phase3-p3.9-dev20-origin-runtime.md`; provenance fallbacks `21/21` missing/empty and all `21/21` outer `SINGLE_SECTION` only; exact/range/neighbor/mixed/unclassified involvement zero; first event `(58,4,-4)`, dirtyFromPlayer false, SCANNING, center known, pending exact episode present. Inherited P3.5-P3.8/lifetime gates green; P3.7 `194/194`, zero real mismatches, exit `0`.
**Lesson:** Do not preserve generic SINGLE_SECTION yet; caller semantics must be inspected first.
**Next action:** Inspect exact Minecraft 26.2 callers of public `LevelExtractor.setSectionDirty(III)`.

---

## A-0177 - dev21 public single-section caller contract

**Date:** 2026-08-30
**Objective:** Freeze fail-closed exact caller inspection before any P3.9 admission correction.
**Action:** Froze hosted exact-bytecode Stage 1 and prohibited generic SINGLE_SECTION preservation.
**Result:** `SUCCESS` / `PLAN FROZEN`.
**Evidence:** `ai/attempts/A-0177-phase3-p3.9-dev21-single-section-caller-contract.md`.
**Next action:** Enumerate exact MC26.2 public single-section callers and their semantics.

---

## A-0178 - dev21 exact public single-section caller result

**Date:** 2026-08-30
**Objective:** Determine whether dev20 SINGLE_SECTION fallbacks can be safely treated as derivatives of exact edits.
**Action:** Inspected exact Minecraft 26.2 client bytecode on hosted Java 25 / Gradle 9.5.1.
**Result:** `SUCCESS` for Stage 1; behavior correction not yet authorized.
**Evidence:** `ai/attempts/A-0178-phase3-p3.9-dev21-single-section-caller-result.md`; runs `33277863920` and `33277944526` SUCCESS. External callers are `ClientChunkCache.onLightUpdate`, one supplied section, and `ClientPacketListener.handleChunksBiomes`, a broad 3x3 chunk-neighborhood across full section-Y.
**Lesson:** Generic SINGLE_SECTION preservation is unsafe because biome invalidation shares the sink.
**Next action:** Distinguish LIGHT_UPDATE versus BIOME_PACKET at runtime on the actual provenance fallback drains.

---

## A-0179 - dev21 caller-specific tracer contract

**Date:** 2026-08-30
**Objective:** Freeze bounded caller-specific correlation without changing the existing fallback decision.
**Action:** Froze `LIGHT_UPDATE`, `BIOME_PACKET`, and `OTHER_SINGLE_SECTION` caller scopes plus per-drain/fallback correlation.
**Result:** `SUCCESS` / `PLAN FROZEN`.
**Evidence:** `ai/attempts/A-0179-phase3-p3.9-dev21-caller-specific-tracer-contract.md`.
**Next action:** Package dev21 and run one short diagnostic workload.

---

## A-0180 - dev21 caller-specific tracer implementation/package

**Date:** 2026-08-30
**Objective:** Produce the exact dev21 caller-specific diagnostic while preserving all A-0159 behavior and thresholds.
**Action:** Added bounded caller scopes/correlation, wired only after accepted lifecycle relevance and outer SINGLE_SECTION classification, and built on hosted Java 25 / Gradle 9.5.1.
**Result:** `SUCCESS` for implementation/package; short reference runtime required.
**Evidence:** `ai/attempts/A-0180-phase3-p3.9-dev21-caller-specific-tracer-package.md`; exact implementation `28d14510991eddd05fb68eed2cb806b63a4ff0eb`, tree `31ba14ce0256090a0055236126afe649e5f3bd84`, Build `33278257773` SUCCESS, artifact `9722180208`; canonical JAR 518,959 bytes, SHA-256 `9b0e103de085c3f35fac3a3c245f8577c362df1e7c2925bb21c96c107fd7621a`.
**Next action:** Short dev21 runtime. If all provenance fallbacks are still outer SINGLE_SECTION and caller correlation is LIGHT_UPDATE only, freeze a separate exact-section light-update preservation correction; otherwise redesign/capture the independent cause.

---

## A-0181 - dev21 caller-specific runtime

**Date:** 2026-08-30
**Objective:** Determine whether the missing/empty provenance fallback population is exclusively the exact Minecraft 26.2 light-update caller.
**Action:** Ran canonical dev21 on the reference Windows / RX 6800 XT Vulkan system.
**Result:** `SUCCESS` for the diagnostic objective; P3.9 remains experimental.
**Evidence:** `ai/attempts/A-0181-phase3-p3.9-dev21-caller-runtime.md`; outer correlation `43/43 SINGLE_SECTION`; caller correlation `43/43 LIGHT_UPDATE`, biome/other/mixed/unavailable `0`; caller totals `117/117` light, cross-thread/overflow `0/0`; P3.7 `312/312`, zero real mismatches, clean lifetime/exit `0`.
**Lesson:** The observed empty-provenance follower is caller-pure light invalidation, but generic SINGLE_SECTION preservation remains unsafe because biome invalidation shares the public sink.
**Next action:** Freeze one exact-section light-update-only shadow preservation correction.

---

## A-0182 - dev22 exact-section light-update preservation contract

**Date:** 2026-08-30
**Objective:** Freeze the final fail-closed correction pass before concluding the fixed four-slice P3.9 experiment.
**Action:** Froze preservation only for an already-pending exact episode when an empty/unflagged provenance drain corresponds entirely to same-section LIGHT_UPDATE lifecycle events for exactly the pending section.
**Result:** `SUCCESS` / `PLAN FROZEN`.
**Evidence:** `ai/attempts/A-0182-phase3-p3.9-dev22-light-update-preservation-contract.md`.
**Lesson:** Production full-section invalidation continues; only the shadow pending request may survive. Generic single-section, biome, wrong-section, mixed, unavailable, cross-thread or overflow cases remain fail-closed.
**Next action:** Package dev22 and run one full unchanged A-0159 closure workload.

---

## A-0183 - dev22 exact-section light-update preservation implementation/package

**Date:** 2026-08-30
**Objective:** Implement and package the final A-0182-authorized shadow-only correction without changing production rendering or frozen thresholds.
**Action:** Added fixed primitive same-section light interval proof plus a post-provenance-drain exception that cancels only the shadow fallback when every A-0182 condition is satisfied.
**Result:** `SUCCESS` for implementation/package; full A-0159 closure runtime required.
**Evidence:** `ai/attempts/A-0183-phase3-p3.9-dev22-light-update-preservation-package.md`; exact implementation `177081d5b8605439f66d70ffca481c0044e62add`, tree `9fadf0e62b7833f7676dc067e7b4cab40ae19805`, Build `33279229989` SUCCESS, artifact `9722466081`; canonical JAR `524452` bytes, SHA-256 `ec0574c7d24a521eed3de13b5c7efc23f54d501c6c8915c597a283f9296a3f27`.
**Next action:** Full frozen A-0159 closure run. PASS or formal benefit REJECT/DEFER both move the project to full production opaque/cutout terrain rendering replacement; do not retune thresholds or automatically implement partial GPU patching.

---

## A-0184 - dev22 reference runtime did not arm

**Date:** 2026-08-30
**Objective:** Run the canonical dev22 binary through the full frozen A-0159 closure workload.
**Action:** User ran dev22 on the reference Windows / RX 6800 XT Vulkan system and returned the complete log.
**Result:** `PARTIAL` — non-decisive; the benchmark/P3.9 window never armed.
**Evidence:** `ai/attempts/A-0184-phase3-p3.9-dev22-reference-runtime-not-armed.md`; `partialRemeshWindowArmed=false`; completed/observed/retained `0/0/0`; P3.6 had 385/385 deterministic proofs but `strictTJunctionPoints=0` and `junctionBearingTransformProofRecords=0`; all 385 P3.7 proof contents remained exact with zero missing/duplicate/optimized-without-reference/real mismatches; clean lifetime and exit `0`.
**Why:** FrameCoordinator arms the benchmark/P3.9 window only after `differentialCorrectnessEvidenceReady()`. That readiness inherits the P3.6 requirement for at least one strict T-junction and one junction-bearing transform proof. The selected scene supplied neither witness.
**Side effects / lessons:** This is scene-selection/gate-population failure, not a dev22 correction failure. No source change, threshold change or new JAR is justified. Do not begin the closure workload until the two P3.9 arming log lines are visible.
**Next action:** Rerun the same dev22 JAR in a geometrically richer section, establish P3.6 readiness first, wait for both P3.9 arming lines, then perform the full >=32/16/8/coalesced/reload/recenter closure workload. PASS or formal benefit REJECT/DEFER then moves directly to production opaque/cutout terrain replacement.
