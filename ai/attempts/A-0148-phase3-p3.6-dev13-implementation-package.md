# A-0148 - P3.6 dev13 T-junction evidence implementation and package checkpoint

**Date:** 2026-08-29  
**Objective:** Implement the frozen A-0147 non-geometry-changing T-junction evidence slice and prove it compiles/packages against the exact Minecraft 26.2 dependency set before reference runtime validation.  
**Action:** Added bounded immutable `TJunctionTopologyProof`, integrated deterministic proof construction into completed worker results, aggregated proof/transform evidence only after generation-safe LIVE install/draw, added final P3.6 coordinator/runtime diagnostics, and bumped the development version to `0.3.0-phase3-dev13`. No emitted geometry, shader, pipeline, vertex/index format, atlas/lightmap, draw-class, native graphics, staging, arena, resource-lifetime or live-world ownership semantics changed.  
**Result:** `SUCCESS` for implementation + exact hosted compile/package checkpoint. Reference runtime and targeted visual/raster evidence remain required; PR #47 stays draft.

## Exact implementation build

Implementation head compiled by the normal pull-request Build workflow:

- source head: `1504c87c3ed42dc4b4c49a1cdbdb61c4b5d8c6fc`
- workflow: `33262626441`
- Java 25 / Gradle 9.5.1 build: **SUCCESS**
- artifact upload: **SUCCESS**
- versioned release publishing: not applicable on pull request

Artifact:

- id `9717691386`
- wrapper `obsidian-5ccc041bcabe45408c9051749aa75ea9c7dde9d2`
- wrapper size `611,209` bytes
- wrapper digest `sha256:2654a9e94b5b183ed3ff302f758ab566e3b4ee09a72ed3bcf58c9a7c30185067`

Direct runtime JAR:

- `Obsidian-0.3.0-phase3-dev13.jar`
- size **419,659 bytes**
- SHA-256 **`44f7d9bec8979ddad8eb741b7024ed7ff1cb921d70cb6baff98e2a147956adc7`**

Sources JAR:

- `Obsidian-0.3.0-phase3-dev13-sources.jar`
- size **217,731 bytes**
- SHA-256 **`013aa35a35b349ef00aaedbb117c0de9ab5031788b6f5ca7d995fe486d59ea8b`**

## Implemented P3.6 proof path

### Actual emitted topology only

`TJunctionTopologyProof` consumes `RenderMergeCandidates` plus the dev10 `RepeatAwareTransportProof`. It therefore sees exactly the candidate identities eligible for the dev11 merged emission path, not raw P3.3 topology rectangles or generalized passthrough geometry.

Each transport record is required to reference one unique candidate. Candidate geometry is decoded from exact packed integer direction/plane/u/v/width/height fields.

### Strict T-junction definition

The proof uses a fixed 6-direction × 16-plane × 17×17 section-edge lattice represented with primitive row bitmasks.

For each actual emitted merged rectangle:

- its four edge endpoints are marked exactly;
- strict edge-interior lattice coordinates are marked exactly;
- a strict T-junction point is an emitted endpoint that intersects another emitted edge's strict interior on the same direction and face plane.

This excludes ordinary shared corners by construction. All decisions use integer identity; no epsilon comparison is used.

Per candidate the proof also requires:

- direction in the six supported directions;
- plane in `0..15`;
- rectangle edge bounds in the legal section-local `0..16` lattice;
- positive width/height;
- exact integer-lattice construction.

Scratch is bounded/reused per worker; proof output retains summaries only.

### Transactional determinism

Each completed worker job builds the T-junction proof **twice** before publishing its ticket and requires `contentEquals` equality. Cancellation is checked around the new sidecar boundary. No standalone global upstream telemetry is used to infer proof completion.

The completed ticket carries the proof alongside the already-proven mesh sidecars. Scene aggregation occurs only after the ticket has survived generation/event-sequence validation and reached LIVE install, so a cancelled/stale result cannot become P3.6 scene evidence.

### Camera-relative transform evidence

The LIVE record reuses the existing draw-transform path. P3.6 validates that recorded relative translation equals the exact recomputation:

`sectionOrigin - firstDrawCameraPosition`

in double precision, with finite float conversion only after that subtraction. A junction-bearing proof must also reach this real draw-transform path before the P3.6 runtime gate can arm.

### No rendering-semantic change

Dev13 deliberately leaves unchanged:

- render-key/UV/light/color eligibility;
- transport-safe candidate set;
- suppression/replacement accounting;
- merged and passthrough vertex positions;
- the common canonical face comparison offset;
- vertex/index formats;
- repeat-aware shaders;
- graphics pipelines;
- block atlas/lightmap bindings;
- four fixed indexed-indirect draw classes;
- native Vulkan seam scope;
- staging/arena/resource lifetime;
- render-thread capture/GPU ownership and `workerWorldReadsAfterCapture=0`.

## Runtime gate

The new scene/final gate is `tJunctionPolicyEvidenceReady=true`.

In addition to every prior P3.5/dev11 gate, runtime must prove:

- installed topology proof records > 0 and exactly equal installed records;
- proof determinism audits/matches exact;
- emitted merged candidates > 0;
- strict edge-interior lattice points > 0;
- **strict detected T-junction points > 0**;
- exact bounds, plane/direction and integer-lattice check identities;
- camera-relative transform evidence on LIVE drawn records;
- at least one junction-bearing record actually reaches the transform/draw path;
- zero unsafe stale installs and dropped lifecycle evidence;
- geometry/shader/pipeline change flags remain false;
- workers/staging/arena/resources close cleanly;
- normal process exit code 0.

Targeted visual instructions are emitted only after this automated gate is true, preventing a visual PASS from being claimed on a scene with no proven strict junctions.

## Roadmap synchronization

After the source build, Class-A roadmap synchronization marked P3.5 COMPLETE and P3.6 ACTIVE, added the A-0147 dev13 contract and preserved P3.7+ ordering. That synchronization changed documentation only. The final branch head still requires one normal hosted Build after the synchronization/attempt records are complete; this attempt commit intentionally triggers that final build.

## Runtime next action

Keep PR #47 draft. After the final synchronized head Build is green, use the direct dev13 JAR on the reference machine. Let automated logs reach `tJunctionPolicyEvidenceReady=true` before judging visuals, then exercise stationary/moving camera, slow/fast rotation, grazing angles, section-boundary/recenter movement, ordinary block rebuild and F3+T. Report explicit PASS/FAIL for cracks, pinholes, flickering seams, z-fighting/double edges or camera-motion-dependent gaps.
