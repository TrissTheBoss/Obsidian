# A-0139 — Phase 3 P3.4 dev11 cleaned package checkpoint

**Date:** 2026-08-29  
**Result:** SUCCESS — cleaned implementation/evidence head CI green; reference runtime still required at this checkpoint.

## Scope
Canonicalize the dev11 repeat-aware greedy GPU emission canary after removing one unused intermediate worker-evidence helper. The helper was never referenced by the scheduler or runtime gate; removing it does not change the intended dev11 architecture or frozen A-0137 contract.

## Cleaned branch head
- branch: `phase3/repeat-aware-gpu-emission`
- cleaned head: `fe8bf06b1bdb1dbdcc4169ab720fc20be23a5af1`

## Hosted CI
Workflow `33216879856`:
- Java 25 / Gradle 9.5.1 build: SUCCESS
- artifact upload: SUCCESS
- versioned release publishing: SKIPPED

Artifact:
- id `9703689084`
- wrapper `obsidian-9ea051a3c260074bf8be5a1e11b8238e4003c302`
- wrapper size `579,630` bytes
- artifact digest `sha256:dd0bb18a6bdd063c4d89d04bef0f0f2a44233f50b6a032fe0ea74b500779a5e1`

## Canonical direct runtime JAR
- `Obsidian-0.3.0-phase3-dev11.jar`
- size **399,361 bytes**
- SHA-256 **`89520af731dbfb48c35071de809d75db1f0c98cdd289e123a9c77f2bacc46418`**

Cleaned sources JAR:
- size `205,751` bytes
- SHA-256 `e093f435d9c956187a3009d9058f8e4c764b5c2d6ee5236d828affc1edd3f4e5`

## Package status
The cleaned artifact retains:
- version `0.3.0-phase3-dev11`;
- Minecraft `~26.2` / Java `>=25` client target;
- deterministic `RepeatAwareGreedyMesh`;
- 60-byte merged public Blaze3D vertex format;
- namespaced repeat-aware vertex/fragment shaders;
- worker-side dev10 transport proof embedding and hybrid determinism validation;
- render-thread-only hybrid install/upload/draw/retirement;
- four fixed indexed-indirect classes: passthrough/merged x SOLID/CUTOUT;
- `RepeatAwareGreedyEmissionEvidence` runtime gate;
- exact BakedSectionMesh worker oracle and fallback.

No native Vulkan graphics seam expansion was introduced.

## Promotion boundary at this checkpoint
PR #43 remains draft/unmerged until both frozen gates close:
1. `repeatAwareGreedyEmissionEvidenceReady=true` and all prior automated gates in a complete reference shutdown; and
2. explicit human visual PASS on real Vulkan hardware.

Standing Phase 3 merge authorization applies once both gates pass.