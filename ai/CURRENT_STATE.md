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
- Dev9 status: **compile/package validated; final docs-only CI and RX 6800 XT runtime validation pending**

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

## Completed through Phase 1 dev8

Phase 0 and Phase 1 dev1-dev8 are runtime validated and merged with `[no-release]` development semantics.

Dev8 runtime proved GPU compute can author two native indexed-indirect commands, synchronize them explicitly with Sync2, and have public Blaze3D render both commands correctly while Minecraft retains device/queue/graphics/presentation ownership. Runtime evidence: `ai/attempts/A-0047-dev8-runtime-success.md`. Merge commit: `68e9710ff964a44165122c5d85c0d559e4698b11`.

## Phase 1 dev9 - GPU visibility/indirect compaction

Goal: prove the producer side of future GPU-driven terrain visibility before rendering a real Minecraft section.

Validation graph:

1. `visibility-scene-upload`
2. `visibility-compact`
3. `visibility-indirect-draw`
4. `visibility-readback`

Four triangle candidates are physically on-screen at center X values `-0.75, -0.25, +0.25, +0.75`. Compute keeps the inner pair only using `abs(centerX) <= 0.50 && abs(centerY) <= 0.80`.

### Scene/geometry data

- candidate record: `uint firstIndex`, `float centerX`, `float centerY`, `uint reserved` = 16 bytes;
- four candidate records = 64 bytes;
- 12 position vertices = 144 bytes;
- 12 uint16 indices = 24 bytes;
- expected device-arena high-water = 168 bytes;
- CPU staging payload = 232 bytes;
- expected aligned staging high-water = 240 bytes.

### GPU output

- four 20-byte `VkDrawIndexedIndirectCommand` slots = 80 bytes;
- uint visibleCount at offset 80;
- output total = 84 bytes and is entirely GPU-written;
- expected visibleCount=2;
- front slots 0-1 contain the inner candidate commands in either atomic order;
- tail slots 2-3 must be fully zero.

### Graphics ownership / indirect count

Exact Minecraft 26.2 inspection found no public count-buffer indirect draw and no `drawIndirectCount` field in `DeviceFeatures`; public Vulkan-backed rendering uses `vkCmdDrawIndexedIndirect`.

D-0027 therefore keeps `nativeGraphicsSeam=false`. Public Blaze3D draws four command slots while the GPU-zeroed tail emits no geometry. The GPU visible count remains a real validated output for a future Phase 4 optimization. `vkCmdDrawIndexedIndirectCount` may be revisited only if profiling justifies integrating that native graphics command into Minecraft-owned render-pass state.

Inspection evidence: `ai/attempts/A-0049-dev9-indirect-count-api-inspection.md`.

### Synchronization

- scene upload: TRANSFER / TRANSFER_WRITE -> COMPUTE_SHADER / SHADER_STORAGE_READ;
- output consumption: COMPUTE_SHADER / SHADER_STORAGE_WRITE -> DRAW_INDIRECT / INDIRECT_COMMAND_READ plus TRANSFER / TRANSFER_READ for readback.

### Deterministic runtime oracle

Private 32x32 RGBA8 target:

- visible-left `(12,16)` magenta;
- visible-right `(20,16)` magenta;
- culled-left `(4,16)` black;
- culled-right `(28,16)` black;
- corner `(0,0)` black;
- expected `pixelsVerified=5`.

Output readback requires `visibleCount=2`, the two expected visible commands in the compacted front and two fully zero tail commands (`compactedCommandsVerified=4`).

### Compile/package status

- implementation record: `ai/attempts/A-0050-dev9-visibility-compaction-implementation.md`;
- clean source-only head `88c83a59ed83978134b13f0038038126bd5da2fc` passed run `32415578830`;
- exact documented package head `68e4a331074577de8f7b52006b0362b13a6df25d` passed run `32415959954` with build + artifact upload SUCCESS and release SKIPPED;
- artifact ID `9423907764`;
- package verification: `ai/attempts/A-0051-dev9-final-package-verification.md`;
- main JAR SHA-256: `c133229502f4969aa1f894aa09c404d20c06d833ceb11ba26312cf3e6f6ce6de`;
- sources JAR SHA-256: `c44438ba09dfa4161fa6cbe1c0f74df4b7e25bf6927bfa78fa41ded921c0fc55`;
- packaged version is `0.1.0-phase1-dev9`;
- packaged classes include `VisibilityCompactionProbe`, `VulkanVisibilityCompactor`, `VulkanInteropBuffer`, `FrameCoordinator`;
- completed dev8 one-shot classes are absent.

## Proven architecture boundary

`Minecraft Vulkan device/queue/presentation -> bounded staging -> generation-safe geometry arena -> frame graph -> one Minecraft-owned submission -> narrow native compute/storage -> GPU scene visibility + atomic command compaction + visible count -> explicit Sync2 hazards -> public Blaze3D fixed-count indirect graphics -> pixel/command readback -> completion-gated reclamation`

No second device/queue/swapchain is created and native graphics/presentation ownership remains out of scope.

## Terrain meshing roadmap

D-0024 remains active:

- **Phase 2:** one real section correctly, immutable snapshot + neighbor halo + simple reference mesher/differential oracle;
- **Phase 3:** production worker-local binary/bitmask greedy mesher with reusable scratch and render-correct merge keys covering material/layer/tint/light/AO/UV/special state;
- **Phase 4+:** scale the dev9 visibility/compaction shape over the reduced section meshes.

Research: `ai/attempts/A-0038-greedy-meshing-roadmap-research.md`.

## Immediate next action

1. Final-CI the documentation-only head created after A-0051/CURRENT_STATE synchronization.
2. Confirm its JAR is byte-identical or re-verify the newer artifact if not.
3. Hand `0.1.0-phase1-dev9` to the user for the RX 6800 XT runtime test.
4. Keep PR #11 draft and unmerged until runtime validation succeeds.

## Relevant durable decisions

D-0014 through D-0027 remain active. D-0024 governs greedy meshing; D-0025/D-0026 constrain native compute interop and hazards; D-0027 preserves public fixed-count graphics for baseline visibility compaction.