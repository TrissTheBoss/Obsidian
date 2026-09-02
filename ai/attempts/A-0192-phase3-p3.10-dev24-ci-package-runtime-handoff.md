# A-0192 — Phase 3 P3.10 dev24 CI/package/runtime handoff

Date: 2026-09-02
Status: **SUCCESS / RUNTIME HANDOFF**
Parent: A-0191
Target: `0.3.0-phase3-dev24`
Branch: `phase3/p3.10-production-terrain-replacement`

## Objective

Close the hosted-CI/package side of the frozen A-0191 production opaque/cutout replacement canary and hand the exact direct JAR to the reference-machine runtime/visual gate without changing renderer source or weakening any contract requirement.

## Source state

Current branch head before this continuity-only checkpoint:

- commit `416662fb4dc132e1622f87349219a813e150c90f`
- parent `b00e6e3e4020f6170f89ca843f7cb9aeea8b72e7`
- commit message `[no-release] Fix dev24 bootstrap banner syntax`
- source version `0.3.0-phase3-dev24`

A-0191 remains the frozen canary contract. No renderer-source change is made by this attempt.

## Hosted CI

Exact head `416662fb4dc132e1622f87349219a813e150c90f` passed GitHub Actions Build:

- workflow run `33334749141`
- run number `701`
- conclusion `success`
- artifact id `9738683436`
- artifact wrapper `obsidian-2744752d5b3fe6e6f33550452a1f3717646b65dd`
- wrapper size `678,084` bytes
- wrapper digest `sha256:9bf17f5a04853d4602cef853373b15c7fed36b5af2745a9ec473bb23f72852ff`

The wrapper was downloaded and inspected only to identify the direct versioned runtime JAR; the wrapper itself is not the runtime handoff artifact.

## Canonical dev24 runtime package

Direct runtime JAR extracted from the exact green hosted artifact:

- `Obsidian-0.3.0-phase3-dev24.jar`
- size **466,364 bytes**
- SHA-256 **`d6585db05b67b815f30a64cc64d767f88e3cb2608b1593f63b746bee92b3d690`**

Sources JAR:

- `Obsidian-0.3.0-phase3-dev24-sources.jar`
- size `240,057` bytes
- SHA-256 `03991b9c668ce61873e6858a7066b2eb83ce3ce649befd96e8a97cff6f8f3c56`

## Runtime handoff contract

Use the direct versioned JAR above, never the Actions ZIP wrapper.

The reference runtime must satisfy every A-0191 gate. At minimum exercise and retain logs/evidence for:

1. initial world load until replacement becomes active;
2. real SOLID suppression and matching Obsidian replacement execution;
3. real CUTOUT suppression and matching replacement execution;
4. at least one interval where the managed record is unavailable/not-LIVE and vanilla fallback remains visible;
5. an ordinary terrain edit followed by rebuild and replacement recovery;
6. `F3+T` resource reload, observing fallback during invalidation and replacement recovery afterward;
7. a real section/scene recenter followed by replacement recovery;
8. normal game exit so worker/staging/arena/deferred-resource closure is logged.

Required automated closure remains exactly A-0191:

- SOLID suppression/execution > 0;
- CUTOUT suppression/execution > 0;
- exact suppression == execution accounting;
- duplicate/overflow/stale-plan/unclaimed/revalidation failures all zero;
- production-coordinate and exact-color flags true;
- post-world comparison draw disabled true;
- same-OPAQUE-pass flag true;
- native graphics expansion false;
- permanent P3.7 missing/duplicate/optimized-without-reference/real mismatch all zero;
- worker world reads after capture `0`;
- synchronous scene mesh builds `0`;
- unsafe stale installs `0`;
- queue/staging/arena/deferred lifetime clean;
- process exit `0`.

Human visual PASS is still mandatory for opaque terrain, cutout vegetation, boundaries, camera motion, edits, reload and recenter. Any hole, duplicate/z-fighting terrain, 1/512 comparison shift, 75% comparison tint, UV-repeat regression, tint/light/AO regression, crack/pinhole, cutout-alpha regression, depth-order regression or stale popping blocks promotion.

## Result

**SUCCESS / RUNTIME HANDOFF.**

The exact dev24 canary compiles/packages successfully on hosted CI and the direct runtime JAR identity is frozen above. No runtime or visual success is claimed here. PR #55 must remain DRAFT / DO NOT MERGE until the reference runtime and explicit human visual gate close in a later immutable attempt.

## Next action

Run the exact direct dev24 JAR on the reference Windows 11 / RX 6800 XT Vulkan environment under the A-0191 sequence, return the complete relevant log plus explicit visual verdict, then record the runtime result without weakening the frozen contract.
