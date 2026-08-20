# Obsidian Current State

Last updated: 2026-08-20

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Current public release: `v0.0.2-phase0`
- Current merged development baseline: Phase 1 dev8, merge commit `68e9710ff964a44165122c5d85c0d559e4698b11`
- Active development branch: `phase1/visibility-compaction`
- Active draft PR: #11, `Phase 1: GPU visibility and indirect compaction`
- Current development version: `0.1.0-phase1-dev9`
- Dev9 status: **implementation compile-clean; final exact-head CI/package and RX 6800 XT runtime validation pending**

## Reference runtime

- Windows 11
- Prism Launcher 10.0.5
- Minecraft 26.2
- Fabric Loader 0.19.3
- Fabric API 0.158.0+26.2
- Java 25.0.1
- AMD Radeon RX 6800 XT, 16 GB VRAM
- Ryzen 5 5600X
- 16 GB DDR4-2666
- Vulkan driver observed: `1.4.315 AMD proprietary driver 26.7.1 (AMD proprietary shader compiler)`

## Completed milestones

Phase 0 and Phase 1 dev1-dev8 are runtime validated and merged with development `[no-release]` semantics.

### Phase 1 dev8 - VALIDATED / merged PR #10

Runtime proved the first compute-generated indexed-indirect path on the reference RX 6800 XT:

- graphPasses=4, executedMask=15;
- one useful submission, zero profiler-only submissions;
- one native Vulkan compute dispatch;
- GPU generated two native indexed-indirect command records;
- public Blaze3D consumed both commands successfully;
- `nativeComputeSeam=true`, `nativeGraphicsSeam=false`;
- CPU staging 84/84 bytes, high-water 92;
- both triangle pixels verified plus black corner;
- arena retired/reclaimed 2/2 and fully coalesced;
- world entry and clean process exit code 0.

Runtime evidence: `ai/attempts/A-0047-dev8-runtime-success.md`.
Merge commit: `68e9710ff964a44165122c5d85c0d559e4698b11`.

## Phase 1 dev9 - ACTIVE / draft PR #11

Goal: prove GPU scene visibility and indirect-command compaction before real Minecraft terrain enters the renderer.

### Exact indirect-count finding

Minecraft 26.2 public Blaze3D exposes fixed-count indirect drawing only:

- `RenderPass.drawIndexedIndirect(GpuBufferSlice, int)`;
- `DeviceFeatures.drawIndirect` and `multiDrawIndirect`;
- no public count-buffer draw;
- no `drawIndirectCount` feature field;
- VulkanRenderPass uses `vkCmdDrawIndexedIndirect`.

Vulkan has `vkCmdDrawIndexedIndirectCount`, but consuming it now would widen Obsidian into native graphics/render-pass recording. Dev9 therefore keeps `nativeGraphicsSeam=false`: compute writes a GPU visible count, compacts visible commands to the front and fully zeros unused command slots. Public Blaze3D draws the fixed maximum; zero-tail commands emit no geometry. The GPU count is still read back and verified for future Phase 4 use.

Evidence: `ai/attempts/A-0049-dev9-indirect-count-api-inspection.md`.

### Dev9 implementation

Validation graph:

1. `visibility-scene-upload`
2. `visibility-compact`
3. `visibility-indirect-draw`
4. `visibility-readback`

Four physical triangle candidates use center X values `-0.75, -0.25, +0.25, +0.75`. The validation visibility test keeps the inner pair only.

GPU scene record:

- uint firstIndex;
- float centerX;
- float centerY;
- uint reserved;
- 16 bytes each, 64 bytes total.

Geometry:

- 12 vertices = 144 bytes;
- 12 uint16 indices = 24 bytes;
- expected device-arena high-water = 168 bytes.

CPU staging:

- vertices 144 bytes;
- indices 24 bytes;
- scene records 64 bytes;
- submitted payload = 232 bytes;
- expected aligned staging high-water = 240 bytes.

GPU compacted output:

- four 20-byte indexed-indirect command slots;
- uint visibleCount at byte offset 80;
- 84 bytes total, entirely GPU written;
- expected visibleCount=2;
- slots 0-1 contain the two inner candidates in either atomic order;
- slots 2-3 are all-zero commands.

Public graphics performs one `drawIndexedIndirect(..., 4)` call. Expected flags are `nativeComputeSeam=true`, `nativeGraphicsSeam=false`, `indirectCountConsumed=false`.

Deterministic 32x32 offscreen pixel oracle:

- visible-left `(12,16)` magenta;
- visible-right `(20,16)` magenta;
- culled-left `(4,16)` black;
- culled-right `(28,16)` black;
- corner `(0,0)` black.

Output readback additionally verifies `visibleCount=2`, both compacted visible commands and both zero tail commands.

### Dev9 synchronization

Two explicit Synchronization2 producer/consumer edges:

- scene upload: TRANSFER/TRANSFER_WRITE -> COMPUTE_SHADER/SHADER_STORAGE_READ;
- compacted output: COMPUTE_SHADER/SHADER_STORAGE_WRITE -> DRAW_INDIRECT/INDIRECT_COMMAND_READ plus TRANSFER/TRANSFER_READ for validation readback.

### Compile status

- complete implementation compiled successfully against exact Minecraft 26.2/LWJGL;
- completed dev8 probe/generator/storage classes removed;
- temporary dev9 API-inspection workflow removed;
- clean source-only head `88c83a59ed83978134b13f0038038126bd5da2fc` passed GitHub Actions run `32415578830` with build and artifact upload SUCCESS and release SKIPPED;
- implementation record: `ai/attempts/A-0050-dev9-visibility-compaction-implementation.md`.

## Proven architecture boundary

`Minecraft Vulkan device/queue/presentation -> FrameCoordinator -> bounded staging -> generation-safe device geometry arena -> FixedFrameGraph -> one Minecraft-owned submission -> narrow native Vulkan compute/storage -> GPU visibility + command compaction -> explicit hazards -> public Blaze3D fixed-count indexed-indirect graphics -> deterministic pixel/command readback -> completion-gated reclamation`

Obsidian still does not create a second device, queue or swapchain and does not own native graphics/presentation.

## Terrain meshing roadmap

Greedy meshing remains required under D-0024. Research: `ai/attempts/A-0038-greedy-meshing-roadmap-research.md`.

- **Phase 2:** one real section correctly; immutable snapshot + neighbor halo + simple reference mesher/differential oracle.
- **Phase 3:** production worker-local binary/bitmask greedy meshing with reusable scratch and render-correct merge keys covering material/layer/tint/light/AO/UV/special state.
- **Phase 4+:** production GPU visibility/compaction consumes those reduced section meshes. Native indirect-count consumption may be revisited only if measured worthwhile and integrated without uncontrolled graphics-backend spread.

## Immediate next action

1. Append the dev9 fixed-count/zero-tail decision to the durable decision ledger.
2. Update PR #11 with exact implementation and runtime invariants.
3. Run final CI on the exact documented head.
4. Inspect/package the exact CI-built `0.1.0-phase1-dev9` JAR and checksums.
5. Runtime-test on the reference RX 6800 XT.
6. Keep PR #11 draft/unmerged until that runtime validation passes.

## Relevant durable decisions

D-0014 through D-0026 remain active. D-0024 keeps binary/bitmask greedy meshing as the Phase 3 production CPU mesher target.