# A-0183 — Phase 3 P3.9 dev22 exact-section light-update preservation package

Date: 2026-08-30
Status: SUCCESS for implementation/package; full A-0159 closure runtime required

## Objective

Implement the one A-0182-authorized shadow-only correction after A-0181 proved every observed missing/empty provenance fallback was caller-pure LIGHT_UPDATE.

## Implementation

Dev22 adds `PartialRemeshLightUpdatePreservation`, a fixed-primitive proof collector that:

- observes only accepted outer SINGLE_SECTION events while inside exact `ClientChunkCache.onLightUpdate(...)` scope;
- retains only lifecycle-interval light count, same-section identity and bounded thread/overflow state;
- requires an empty/unflagged exact provenance drain;
- requires lifecycle relevant count == relevant light-update count > 0;
- requires all interval light events to identify one section exactly equal to the already-pending partial-remesh episode section;
- rejects wrong-section, multi-section, non-light, mixed, unavailable, cross-thread, overflow and count-mismatch cases;
- never changes the pending request id, original fingerprints, slice mask or edit count.

`AsyncMultiSectionSceneProbeLightUpdateMixin` injects immediately after the existing provenance drain. Only when the complete proof above passes does it cancel `preparePartialRemeshEpisode(...)` before the existing provenance fallback clears the pending shadow request. The caller `beginFrame(...)` continues normally, so production full-section invalidation/rebuild is unchanged.

No production mesh/upload/install/draw path, worker behavior, arena/staging lifetime, shader/pipeline, atlas/lightmap behavior, native Vulkan behavior, greedy eligibility, permanent P3.7 oracle or A-0159 threshold changed. Partial GPU patching remains disabled.

## Hosted validation

Exact implementation/package head:

- commit `177081d5b8605439f66d70ffca481c0044e62add`
- tree `9fadf0e62b7833f7676dc067e7b4cab40ae19805`
- Build `33279229989`: Java 25 / Gradle 9.5.1 SUCCESS
- Build step SUCCESS
- artifact upload SUCCESS
- release SKIPPED
- artifact id `9722466081`
- wrapper digest `sha256:d2d9b720562a86d8b3d2972e25d187bb76e8e74732fe282889038d825cc31227`

Canonical runtime JAR:

- `Obsidian-0.3.0-phase3-dev22.jar`
- `524,452` bytes
- SHA-256 `ec0574c7d24a521eed3de13b5c7efc23f54d501c6c8915c597a283f9296a3f27`

Sources JAR:

- `Obsidian-0.3.0-phase3-dev22-sources.jar`
- `271,008` bytes
- SHA-256 `da1499574481812db91ab2df1e5e9b02e3a7619e18ff8abd5013895c420655ad`

Package inspection confirms the runtime JAR embeds version `0.3.0-phase3-dev22` and contains:

- `PartialRemeshLightUpdatePreservation.class`
- `AsyncMultiSectionSceneProbeLightUpdateMixin.class`
- updated `ClientChunkCacheDiagnosticMixin.class`
- updated `LevelExtractorMixin.class`.

## Runtime decision gate

The next reference run is the full unchanged A-0159 closure workload, not another diagnostic sample.

Pass: close P3.9 as experimental SUCCESS and move to full production opaque/cutout terrain replacement; do not automatically implement partial GPU patching.

Benefit failure at full evidence volume: formally REJECT/DEFER the fixed four-slice strategy without threshold retuning and move to full production opaque/cutout terrain replacement.

Correctness failure: use the first failure fixture; only one clearly evidence-required safety correction may be considered, otherwise REJECT/DEFER P3.9. Do not reopen broad provenance research.
