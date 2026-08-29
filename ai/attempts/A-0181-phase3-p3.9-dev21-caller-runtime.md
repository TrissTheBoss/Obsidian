# A-0181 — Phase 3 P3.9 dev21 caller-specific runtime

Date: 2026-08-30
Status: SUCCESS for diagnostic objective; P3.9 remains experimental

## Objective

Determine whether the missing/empty provenance fallbacks isolated by A-0171/A-0176 are exclusively the exact Minecraft 26.2 light-update caller, or whether biome/other single-section callers participate.

## Runtime authority

Canonical package: `Obsidian-0.3.0-phase3-dev21.jar`

- implementation commit `28d14510991eddd05fb68eed2cb806b63a4ff0eb`
- implementation tree `31ba14ce0256090a0055236126afe649e5f3bd84`
- Build `33278257773` SUCCESS
- artifact `9722180208`
- JAR size `518,959` bytes
- SHA-256 `9b0e103de085c3f35fac3a3c245f8577c362df1e7c2925bb21c96c107fd7621a`

Reference runtime: Windows / Java 25.0.1 / Minecraft 26.2 / Fabric Loader 0.19.3 / Vulkan / AMD Radeon RX 6800 XT / AMD proprietary 26.8.1.

## Decisive evidence

Dev20 outer-origin correlation:

- provenance fallbacks classified: `43`
- `singleSectionOnly=43`
- exact-block/range-neighbor/mixed/no-relevant/unclassified: `0`
- accounting coherent: `true`
- first fallback: SCANNING, center known, pending exact episode present, provenance drain `0/0`, first event section `(69,4,1)`.

Dev21 caller totals:

- relevant single-section caller events: `117`
- `lightUpdate=117`
- `biomePacket=0`
- `other=0`
- scope cross-thread events: `0`
- scope overflow events: `0`
- self-test: `true`.

Dev21 provenance/caller correlation:

- provenance fallbacks classified: `43`
- `lightUpdateOnly=43`
- `biomePacketOnly=0`
- `otherOnly=0`
- `mixed=0`
- `unavailable=0`
- accounting coherent: `true`
- first fallback had two relevant lifecycle events and exactly two relevant LIGHT_UPDATE events for section `(69,4,1)`.

Thus the complete observed missing/empty provenance fallback population is caller-pure LIGHT_UPDATE on this reference workload. Generic SINGLE_SECTION preservation remains forbidden because A-0178 proved biome invalidation shares the public sink.

## Safety evidence

Permanent correctness remained exact in the final proof collectors:

- P3.5 border/halo proof records `312`, exact outward/reference/shared-border agreement, worker world reads after capture `0`, synchronous scene mesh builds `0`, unsafe stale installs `0`;
- P3.6 determinism `312/312`, zero transform failures;
- P3.7 differential `312/312`, missing `0`, duplicate `0`, optimized-without-reference `0`, real mismatches `0`, fixture self-tests `312/312`;
- worker queue-full rejections `0`, worker failures `0`, shutdown join failures `0`;
- staging/arena/resources closed cleanly and process exit code `0`.

The final FrameCoordinator readiness bitmap is not promotion evidence for this short diagnostic run; several inherited readiness booleans were false after the multi-session/reload diagnostic workload. The permanent per-proof correctness collectors above remained exact. This attempt therefore closes only the caller-classification objective.

## Decision

A-0179 diagnostic objective is satisfied. Do not rerun unchanged dev21.

The evidence authorizes freezing a narrow shadow-only correction that may preserve an already-pending exact partial-remesh episode across an otherwise-empty provenance drain only when the accepted lifecycle interval is proven LIGHT_UPDATE-only, all accepted light updates are for exactly the pending section, and fail-closed thread/overflow/accounting conditions hold. No generic SINGLE_SECTION preservation is allowed.

## Next action

Freeze A-0182 before source changes. Package dev22 with the narrow light-update preservation rule, unchanged A-0159 thresholds and no partial GPU patching. The next runtime should be the full frozen-volume A-0159 closure workload so P3.9 can be concluded rather than extended indefinitely.