# A-0058 - Phase 2 dev1 real-section runtime success

**Date:** 2026-08-20  
**Result:** SUCCESS  
**Milestone:** Phase 2 dev1 - immutable real-section snapshot/reference oracle  
**Version:** `0.2.0-phase2-dev1`  
**Branch:** `phase2/real-section-reference`  
**PR:** #12  
**Reference machine:** Windows 11, Prism Launcher 10.0.5, Minecraft 26.2, Fabric Loader 0.19.3, Fabric API 0.158.0+26.2, Java 25.0.1, AMD Radeon RX 6800 XT

## Objective

Runtime-validate the first Phase 2 milestone on real Minecraft terrain data: capture one loaded section plus a one-block halo into immutable primitive state, build the deterministic conservative reference-face oracle without live-world reads, move the real canonical face stream through the validated staging/device-arena path, verify it by GPU readback, and preserve normal vanilla terrain/world behavior.

## Exact tested build

- packaged version: `0.2.0-phase2-dev1`;
- documented source head for the verified binary: `80a518653e034200532eb0c75033b0726ef8edd5`;
- package CI run: `32418949171`;
- artifact ID: `9424977392`;
- main JAR SHA-256: `3ab8c5437b73789a00ff90d0a87d0af87921bcc85a2498db96b39b3f4d7b2a23`;
- sources SHA-256: `69f6fb34b700ee965897569afb028a8e531e1228e6368f9ff554f972fbfe90f0`.

The later roadmap/continuity commits on PR #12 were documentation-only and did not change Java/source behavior from the packaged dev1 binary.

## Runtime result

SUCCESS.

Startup/runtime environment:

- correct `obsidian 0.2.0-phase2-dev1` loaded;
- Minecraft selected Vulkan;
- GPU: AMD Radeon RX 6800 XT;
- driver: `1.4.315 AMD proprietary driver 26.7.1 (AMD proprietary shader compiler)`;
- Vulkan capabilities included Synchronization2, indirect draw, multi-draw indirect and persistent mapping;
- Phase 2 frame coordinator armed with three context slots, 262144-byte staging capacity and 524288-byte device arena capacity;
- vanilla terrain explicitly remained active.

## Real-section snapshot and reference oracle

After the player/world/chunks became available, Obsidian captured section `(-2,4,5)` and submitted the real reference stream.

Observed snapshot invariants:

- sampledCells = `5832` (18x18x18);
- interiorCells = `4096`;
- interiorAir = `3954`;
- interiorSupported = `13`;
- interiorUnsupported = `129`;
- `3954 + 13 + 129 = 4096`;
- supportedCells > 0;
- snapshot fingerprint = `16894373850408670304`;
- snapshot time = `1411100 ns`.

Observed reference-mesh invariants:

- faceCount = `27`;
- quadCount = `27`;
- vertexCount = `108`;
- indexCount = `162`;
- blockedByUnsupportedFaces = `28`;
- mesh fingerprint = `16640427735610179256`;
- mesh time = `805600 ns`;
- faceBytes/referenceBytes = `216` = `27 * 8`;
- deterministicBuilds = `2`;
- `worldReadsAfterSnapshot=0`.

The exact section, counts, fingerprints and timings are world-dependent; their role here is internal consistency and evidence that a real immutable snapshot/reference stream was processed successfully.

## GPU upload/readback and lifetime result

The reference stream passed the validated GPU ownership path:

- usefulSubmissions = `1`;
- profilerOnlySubmissions = `0`;
- gpuVerifiedBytes = `216`;
- staging submitted/reclaimed = `216/216` bytes;
- staging high-water = `216` bytes;
- staging backpressure events = `0`;
- pending upload batches = `0`;
- arena allocations = `1`;
- arena high-water = `216` bytes;
- arena retired/reclaimed = `1/1`;
- arena used bytes after completion = `0`;
- arena free spans = `1`;
- largest free span = `524288` bytes;
- fragmentation = `0` permille;
- pending arena retirement batches = `0`;
- generic pending retirements = `0`.

The verification occurred through the normal nonblocking completion path. The log's `after 0 frame(s)` means the tiny submission had completed by the later poll in the same frame iteration; frame count was not used as a completion primitive.

## World/shutdown result

- integrated server started normally;
- player joined the single-player world normally;
- vanilla terrain remained active as intended;
- normal Mojang Dynamic Transforms / Chunk Sections UBO growth was visible during world entry and is not attributed to an Obsidian failure;
- final coordinator state was `sectionReferenceResult=VERIFIED`;
- no upload, arena-retirement or generic-retirement work remained pending;
- process exited with code `0`.

The logger rendered frame totals such as `3.933` / `5.884` with locale grouping; this is consistent with earlier validation logs and should not be read as fractional frame counts.

## Intended effect

Prove that Phase 2 can cross from synthetic GPU validation into real Minecraft section data while preserving immutable snapshot semantics, a permanent independent correctness oracle, bounded upload/arena ownership, and normal vanilla rendering behavior.

## Actual effect

All dev1 runtime success criteria passed. The first real-section snapshot/reference oracle is now validated on the reference RX 6800 XT machine.

This closes roadmap item P2.1 and establishes the independent oracle that later optimized terrain meshing must be judged against.

## Next action

1. Synchronize `CURRENT_STATE.md` and `MASTER_ROADMAP.md` from P2.1 ACTIVE to COMPLETE.
2. Update PR #12 with this runtime evidence and the roadmap/governance work already present on the branch.
3. Run final CI on the runtime-evidence/status head.
4. Promote and merge PR #12 with `[no-release]`.
5. Begin P2.2 from merged `main`: inspect exact Minecraft 26.2 material/model/sprite/light APIs and implement the first drawable real section for the same conservative supported subset, while vanilla terrain remains the visual comparison oracle.
