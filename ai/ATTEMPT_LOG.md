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
