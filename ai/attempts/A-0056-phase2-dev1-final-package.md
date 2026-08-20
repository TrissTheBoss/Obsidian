# A-0056 - Phase 2 dev1 final CI/package verification

Date: 2026-08-20
Status: SUCCESS
Milestone: Phase 2 dev1 immutable real-section snapshot/reference oracle
Version: `0.2.0-phase2-dev1`

## Exact documented code head

- Branch: `phase2/real-section-reference`
- Draft PR: #12
- Exact documented source head before this package-record commit: `80a518653e034200532eb0c75033b0726ef8edd5`
- GitHub Actions run: `32418949171`
- Artifact ID: `9424977392`
- Build: SUCCESS on Java 25 / Gradle 9.5.1
- Artifact upload: SUCCESS
- Versioned release: SKIPPED as intended

## Artifact inspection

CI artifact contained:

- `Obsidian-0.2.0-phase2-dev1.jar`
- `Obsidian-0.2.0-phase2-dev1-sources.jar`

Packaged `fabric.mod.json` reports:

- id: `obsidian`
- version: `0.2.0-phase2-dev1`
- name: `Obsidian`

Required packaged classes present:

- `dev/obsidian/render/terrain/SectionSnapshot.class`
- `dev/obsidian/render/terrain/ReferenceFaceMesh.class`
- `dev/obsidian/render/terrain/RealSectionReferenceProbe.class`
- `dev/obsidian/render/frame/FrameCoordinator.class`
- reusable `dev/obsidian/render/vulkan/VulkanVisibilityCompactor.class`

Completed dev9 `VisibilityCompactionProbe.class` is absent.

## SHA-256

Main JAR:

`3ab8c5437b73789a00ff90d0a87d0af87921bcc85a2498db96b39b3f4d7b2a23`

Sources JAR:

`69f6fb34b700ee965897569afb028a8e531e1228e6368f9ff554f972fbfe90f0`

## Interpretation

The binary handed to the user is built by canonical GitHub CI from the exact documented Phase 2 dev1 source state. It is ready for the first runtime test that depends on actual loaded Minecraft section data rather than synthetic Phase 1 geometry.

PR #12 remains draft/unmerged until the reference RX 6800 XT runtime test proves snapshot capture, deterministic reference face generation, GPU upload/readback verification, safe reclamation, normal world entry and clean shutdown.