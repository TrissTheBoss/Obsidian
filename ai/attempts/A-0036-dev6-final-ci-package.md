# A-0036 - Phase 1 dev6 final CI/package validation

**Date:** 2026-08-20  
**Status:** SUCCESS - hosted compile/package; runtime still pending  
**Version:** `0.1.0-phase1-dev6`

## Objective

Validate and inspect the exact documented dev6 build artifact before distributing it for the real Windows 11 / RX 6800 XT Vulkan test.

## Action

Ran the repository's canonical GitHub Actions build on documented head `d647bae130a3083a4e1eed314735835a6c4b60bd` after implementation, lifecycle review, API evidence, attempt records, D-0023, and current-state synchronization.

GitHub Actions run: `32374265973`.

The Java 25 / Gradle 9.5.1 build completed successfully and the build artifact uploaded successfully. Downloaded artifact ID `9408381494` and inspected the packaged JARs.

## Result

SUCCESS.

Artifact contents:

- `Obsidian-0.1.0-phase1-dev6.jar`;
- `Obsidian-0.1.0-phase1-dev6-sources.jar`.

Main JAR verification:

- `fabric.mod.json` reports id `obsidian` and version `0.1.0-phase1-dev6`;
- contains `dev/obsidian/render/draw/FirstDrawProbe.class` and its state enum;
- contains reusable `FrameGraphCommandStream.class` and `GpuTimestampProfiler.class`;
- completed `FrameGraphProbe` and `DeviceArenaProbe` classes are absent;
- main JAR SHA-256: `f1e5ea835f55e5eb7b0868801e10e0f80eee327fbd870cc25935467674282f7b`;
- sources JAR SHA-256: `2babc7b9a2fa426aff2819d0175172ba2da3980407acef519100fea317656fff`.

## Intended effect

Ensure the user receives the exact CI-built dev6 binary matching the clean branch state, not an intermediate or locally mocked artifact.

## Actual effect

The artifact matches the intended dev6 first-draw implementation and version. Hosted compile/package validation is complete. Graphical runtime validation remains the only missing gate.

## Evidence

- branch `phase1/first-draw`;
- draft PR #8;
- head `d647bae130a3083a4e1eed314735835a6c4b60bd`;
- GitHub Actions run `32374265973`;
- artifact ID `9408381494`;
- checksums above.

## Next action

Run one final CI check after this continuity-only commit/current-state finalization, download the exact resulting artifact, confirm it remains `0.1.0-phase1-dev6`, and distribute it. Real-machine success then requires shader/pipeline creation, one offscreen indexed draw, deterministic center/corner pixels, embedded timestamp results, staging 42/42 payload with 54-byte high-water, world entry, clean shutdown, and exit code 0. Keep PR #8 draft/unmerged until runtime success.