# A-0074 - Phase 2 dev4 runtime machine success / visual oracle retest

Date: 2026-08-21
Status: MACHINE SUCCESS / HUMAN VISUAL RETEST REQUIRED
Milestone: Phase 2 P2.4 / 0.2.0-phase2-dev4
Branch: `phase2/lighting-ao-correctness`

## Runtime result

Reference hardware/runtime:

- Windows 11
- Prism Launcher 10.0.5
- Minecraft 26.2
- Fabric Loader 0.19.3
- Fabric API 0.158.0+26.2
- Java 25.0.1
- AMD Radeon RX 6800 XT
- Vulkan backend

The dev4 package loaded normally and completed the full six-pass lighting/AO probe.

Machine evidence from the Prism log:

- exact `obsidian 0.2.0-phase2-dev4` loaded on Vulkan;
- six sustained lit comparison passes completed;
- `lightingSectionResult=VERIFIED` at shutdown;
- `referenceFaces=637`, `materializedFaces=490`, `rejectedMaterialFaces=147`;
- `aoFaces=490`, `flatFaces=0`;
- observed block-light range `0..0` and sky-light range `0..15` for this sampled section;
- duplicate deterministic reference/material/lighting/drawable builds all passed;
- `worldReadsAfterLightingCapture=0`;
- `oneBlockHaloSufficient=true`;
- `pipelineValid=true`;
- `nativeGraphicsSeam=false`;
- `indexedIndirect=true`;
- `blockVertexFormat=true`;
- `blocksAtlasBound=true`;
- `lightmapBound=true`;
- `profilerOnlySubmissions=0`;
- staging submitted/reclaimed `399960/399960` bytes with zero backpressure and zero pending batches;
- arena allocations/retired/reclaimed `12/12/12`, used bytes `0`, one full 4 MiB free span, fragmentation `0`, no stale-handle rejections;
- indirect resources retired/released `6/6`, zero pending retirements;
- process exit code `0`.

## Human report

The tester reported that everything looked fine, but judging the comparison was difficult because the Obsidian overlay and the underlying vanilla block surfaces appeared to glitch/interfere together.

This is consistent with coplanar comparison geometry competing at effectively the same depth. It is a validation-presentation problem, not evidence of incorrect lighting semantics.

## Decision

Do not mark the human visual gate final from this run alone. Preserve the machine result as successful, then improve only the comparison presentation so the lighting/AO pattern is easier to judge.

The semantic oracle must remain unchanged:

- P2.1 exact reference geometry remains canonical;
- P2.3 exact texture/UV/tint identity remains canonical;
- P2.4 exact `BlockModelLighter` packed-light/AO/shade capture remains canonical;
- no lighting values, AO weights, UVs, indices or world placement semantics are to be changed.

The retest package may add a tiny outward face-normal offset to the **validation-only drawable positions** to avoid coplanar depth fighting. The offset must be explicit, deterministic and small enough that it cannot be confused with a renderer geometry claim.

## Retest gate

After the presentation-only change and exact-head CI, perform one reference-hardware visual retest. Success requires:

- no obvious coplanar flicker/z-fighting between the overlay and vanilla;
- the relative sky/block lighting pattern, face shade and AO corner darkening are readable and visually agree with vanilla, ignoring the deliberate 3/4 RGB comparison modulation;
- texture/UV identity remains correct;
- no new placement drift is introduced beyond the explicitly documented tiny validation-only normal offset;
- machine lifetime/determinism gates remain green.

Do not merge P2.4 until this retest is complete.
