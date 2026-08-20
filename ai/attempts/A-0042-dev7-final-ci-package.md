# A-0042 - Phase 1 dev7 final CI/package verification

**Date:** 2026-08-20  
**Status:** SUCCESS  
**Version:** `0.1.0-phase1-dev7`

## Objective

Validate the documented/clean dev7 implementation against exact Minecraft 26.2 in GitHub Actions and inspect the canonical test artifact before real-machine runtime testing.

## Action

GitHub Actions built exact branch head `b8c5cf6e3cce723e093c8a99773d926c23be2ef2` in run `32377335366` using Java 25 / Gradle 9.5.1. Build and artifact upload succeeded. Artifact `9409587313` was downloaded and inspected.

## Result

SUCCESS.

Verified package:

- `Obsidian-0.1.0-phase1-dev7.jar` = 60537 bytes;
- `Obsidian-0.1.0-phase1-dev7-sources.jar` = 35242 bytes;
- `fabric.mod.json` reports `obsidian` version `0.1.0-phase1-dev7`;
- `ArenaIndirectDrawProbe` is present;
- `IndexedIndirectCommandBuffer` is present;
- `FrameGraphCommandStream` is present;
- completed `FirstDrawProbe` is absent;
- temporary indirect API workflow is absent from the clean source branch.

SHA-256:

- main JAR: `f853af323be163c818e6969a82d78a486228c01c419251f401ca568c38d1bfe3`;
- sources JAR: `33adcfabf0d20e4df72a40fc11787cf40c00582f1fb9b9f385bd2ee203697c5d`.

## Intended effect

Ensure the binary distributed for RX 6800 XT validation exactly contains the dev7 indirect-draw implementation and no stale milestone/debug artifacts.

## Actual effect

Package inspection passed. A final CI run on this continuity-only head is still performed so PR #9 itself remains green at the exact repository handoff state; no Java/source behavior changes follow this verified artifact.

## Next action

Run final CI on the continuity-only head. If green, distribute the verified dev7 JAR and keep PR #9 draft/unmerged until the real Vulkan/world test passes.