# A-0166 — Phase 3 P3.9 dev18 pending-coalescing implementation/package

**Date:** 2026-08-29
**Result:** SUCCESS for implementation/package; reference runtime required
**Frozen contract:** A-0165
**Governing experiment thresholds:** A-0159 unchanged
**Version:** `0.3.0-phase3-dev18`

## Objective

Implement only the A-0165 shadow episode-state correction proven necessary by A-0164, validate it against Minecraft 26.2 in hosted CI, and produce an exact direct runtime JAR without changing production full-section rendering or any A-0159 threshold.

## Implementation

Dev18 changes only shadow partial-remesh episode admission/completion behavior:

- `PartialRemeshShadowRequest` now has immutable same-episode coalescing;
- coalescing preserves the original four pre-edit slice fingerprints exactly;
- same-section exact localized dirty masks are ORed together and exact edit counts are added with overflow rejection;
- an all-four-slice combined mask fails closed to full fallback;
- a different-section event cannot join an existing pending episode;
- global lifecycle, provenance ambiguity/overflow, X/Z halo/boundary and all-slice conditions still clear pending state and fall back full-section;
- an initial episode still requires an installed LIVE record plus previous slice truth exactly as dev17 did;
- a subsequent exact same-section event may widen the pending immutable request while the production scene is rebuilding;
- result completion now requires both exact episode id and exact final widened slice mask, so an older narrow worker result cannot close a widened episode;
- request self-tests cover same-slice coalescing, adjacent-slice union, fingerprint preservation, all-four rejection and edit-count overflow;
- the existing shadow/differential correctness proofs and all A-0159 threshold logic remain unchanged.

Explicitly unchanged:

- permanent P3.7 differential oracle;
- four fixed vertical slices and one-row Y dependency expansion;
- `ClientLevel.setBlocksDirty` provenance source;
- X/Z halo/boundary conservatism;
- production full-section invalidation/capture/worker mesh/upload/install/draw;
- greedy eligibility, render key, transport and geometry;
- worker count, priorities, queue policy and backpressure;
- shaders, pipelines, atlas/lightmap semantics;
- staging/device-arena/deferred-release lifetime;
- projected upload accounting and every A-0159 benefit threshold;
- partial GPU patching remains disabled.

## Hosted implementation helper

Initial temporary helper run `33274891473` stopped before compilation because the helper expected two lowercase `dev17` tokens in `ObsidianBootstrap.java` while the source contained one. This was a helper expectation error only; no implementation commit or package was produced.

The corrected temporary helper run `33274938786` then:

- applied the frozen source patch successfully;
- passed Java 25 / Gradle 9.5.1 `gradle build --stacktrace`;
- removed its temporary helper files;
- passed `git diff --cached --check`;
- committed the clean helper-free implementation as `d2f9ba02e6a5c1dc4e82d36ebff6d5477aef20e0`, tree `90f0a44bf811ac1c3dc6e0965ad7de8d01894693`.

## Package authority

A connector-authored same-tree validation commit was created from the clean implementation tree:

- exact package-validation head: `cfff336b1cb8ab18214d48af3521f65c4182acb3`;
- tree: `90f0a44bf811ac1c3dc6e0965ad7de8d01894693`;
- hosted Build: `33275004099`;
- Java 25 / Gradle 9.5.1: SUCCESS;
- Build: SUCCESS;
- artifact upload: SUCCESS;
- versioned release: SKIPPED.

Artifact `9721228593`:

- wrapper name: `obsidian-f6842472bab0be8575c5574ad5bc95d35f459f51`;
- wrapper size: `718,703` bytes;
- wrapper digest: `sha256:4cf6d7cd79e9ba2bdbdbafabc26f94bda1c10a3c85a52b4cb7336354f667271e`;
- canonical runtime JAR: `Obsidian-0.3.0-phase3-dev18.jar`;
- canonical runtime JAR size: `496,542` bytes;
- canonical runtime JAR SHA-256: `cb3065a172489f197ee3f3b988fe3f202a8079ee6bafb87516f24d65d7fdf8a1`;
- sources JAR size: `252,710` bytes;
- sources JAR SHA-256: `5c0ecfaebeacda6f6dd672156bb1a27bad8a205fc815984d9dea0ed3de31f329`.

## Runtime obligation

Reference runtime is required. Repeat the unchanged A-0159 workload using the exact canonical dev18 JAR:

1. wait for explicit dev18 P3.9 window arm after settled READY;
2. collect at least 16 ordinary one-slice episodes using interior X/Z edits and suitable local Y rows such as 1/5/9/13, allowing normal READY recovery;
3. collect at least 8 two-slice boundary/dependency episodes around local Y 3/4, 7/8 or 11/12;
4. make at least one quick same-section 3-5 edit burst before READY so dev18 must retain a coalesced pending episode;
5. perform post-arm F3+T and recover READY;
6. trigger a real scene recenter and recover READY;
7. continue until at least 32 completed localized episodes if admission permits;
8. exit normally and return the complete log.

Do not change thresholds after seeing the result. If dev18 reaches the frozen evidence volume and projected upload P50/P95 remains above 600/800 permille, that is valid evidence to reject/redesign the fixed four-slice strategy. If evidence volume is still insufficient, classify the exact remaining fallback causes before any further source change.
