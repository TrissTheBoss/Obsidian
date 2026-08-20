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
