# A-0032 - Phase 1 dev5 final CI and package verification

**Date:** 2026-08-20  
**Status:** SUCCESS  
**Version:** `0.1.0-phase1-dev5`

## Objective

Validate the cleaned/documented dev5 implementation against exact Minecraft 26.2 in GitHub Actions and verify the resulting binary before real-machine testing.

## Action

GitHub Actions built branch head `c2cead6b41e010f9a00e151ad8635acf80662f4e` using Java 25 and Gradle 9.5.1. Run `32371303571` completed successfully; build and artifact upload passed. The CI artifact was downloaded and inspected locally.

## Result

SUCCESS.

Verified artifact properties:

- JAR: `Obsidian-0.1.0-phase1-dev5.jar`;
- sources JAR present;
- `fabric.mod.json` reports `obsidian` version `0.1.0-phase1-dev5`;
- binary contains `FixedFrameGraph`, `GpuTimestampProfiler`, `FrameGraphCommandStream`, `FrameGraphProbe`, and `FrameCoordinator`;
- completed `DeviceArenaProbe` is absent from the binary;
- temporary API-inspection workflow is absent from the cleaned branch;
- test release publishing remains intentionally disabled for this development milestone.

The verified pre-continuity-package SHA-256 was `e62044f9556f97c90888ed2bcef36e784cb039126bd3c5cd10e358ed104bfe7e` for the main JAR. A final continuity-only head is re-run through CI before distribution so the canonical test artifact can correspond to the exact final PR head.

## Why

The project treats GitHub-hosted real-dependency builds as compile authority. Package inspection catches cases where source changes compile but stale/obsolete probe classes or incorrect version metadata remain in the delivered JAR.

## Next action

Run CI on the final continuity-only head, download that exact artifact, verify version/classes/checksums again, then distribute it for Windows 11 / RX 6800 XT Vulkan runtime validation. Keep PR #7 draft and unmerged until that test passes.