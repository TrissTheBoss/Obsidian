# A-0247 - Phase 4 P4.4 dev4 source-head CI/package handoff

**Date:** 2026-10-03  
**Status:** `SUCCESS / SOURCE HEAD CI-GREEN; FINAL CONTINUITY HEAD REQUIRED`  
**Milestone:** Phase 4 P4.4 GPU-resident hierarchy-fed fine visibility  
**Version:** `0.4.0-phase4-dev4`  
**Source head:** `ff6dc56e4635df58cc4204a4ccc78f8cd1b8b53a`  
**Draft PR:** #79

## Hosted build

Build #856 / run `37079835614` completed **SUCCESS** on the exact source head.

Java/Gradle authority:

- Java 25;
- Gradle 9.5.1;
- checkout/build/artifact upload all successful.

## Artifact authority

Artifact:

- ID `11258123493`;
- wrapper name `obsidian-61e0b840529ce9d09b0f38e8e4378bd3bba6c6d9`;
- wrapper size 802,233 bytes;
- wrapper digest `sha256:c7b9c3d92811f30e0b958a6304aea116419c82179717d56888a8d8fe1424847b`.

Runtime:

- `Obsidian-0.4.0-phase4-dev4.jar`;
- **548,408 bytes**;
- SHA-256 **`4422a77c4a8637b0abac27dcbf76f72d987cbb96cc1d77987f104f6de16a919b`**.

Sources:

- `Obsidian-0.4.0-phase4-dev4-sources.jar`;
- **285,606 bytes**;
- SHA-256 **`a0a1b78cc474c84d6801a366b22b80026519022fc81edbc654aa53f462281e5f`**.

## Interpretation

The complete P4.4 dev4 renderer/source implementation compiles successfully against the real declared Minecraft 26.2 / Fabric / Java 25 dependency surface.

This is source-head build authority, not yet final Preview publication authority.

The branch still needs continuity synchronization. After continuity-only commits:

1. freeze the exact final PR #79 head;
2. require hosted Build + Context Governor on that exact head;
3. confirm the final runtime/sources JARs are byte-identical to this source-head artifact;
4. publish immutable dev4 Preview;
5. execute the A-0245 RD32+ runtime/visual contract.

Do not change renderer source while performing the continuity/package transition unless a new exact defect is found.
