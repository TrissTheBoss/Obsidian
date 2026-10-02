# A-0230 - P4.2 dev2.1 CI package handoff

**Date:** 2026-09-29  
**Status:** `SUCCESS / CI-GREEN DEV2.1 PACKAGE READY FOR PREVIEW`  
**Milestone:** Phase 4 P4.2 inherited P3.10 vertical recenter correction  
**Version:** `0.4.0-phase4-dev2.1`  
**Exact head:** `e65d69099e95738788ee08691adb93548223e525`  
**Draft PR:** #75

## Objective

Establish exact hosted package authority for the A-0229 exact-player-section recenter correction before publishing a new immutable Preview.

## Implemented correction

The managed P3.10 3x3x1 scene now:

- binds initially to the player's exact current loaded section;
- recenters horizontally or vertically to the player's exact current loaded section;
- uses `SectionSnapshot.tryCaptureSection(x,y,z)` as the non-loading readiness check;
- leaves `SectionSnapshot.tryCaptureNearPlayer()` unchanged for older validation probes that need an “interesting” mixed-content target;
- logs `exactPlayerSectionAnchor=true` on initial bind and explicit previous/player/center coordinates on recenter.

P4.2 hierarchy/classifier behavior, P4.1 fine visibility, P3.10 production ownership, meshing semantics, native graphics ownership and command consumption remain unchanged.

## Hosted CI

Exact head:

`e65d69099e95738788ee08691adb93548223e525`

Hosted checks:

- Build run `36632412941` / **#830** — SUCCESS;
- Context Governor run `36632412419` / **#54** — SUCCESS.

## Hosted artifact authority

Build #830 artifact:

- artifact ID: `11063360860`;
- wrapper: `obsidian-e4867484fe16d133061a82dc7917a2f08a5d9f97`;
- wrapper size: `758,671` bytes;
- wrapper digest: `sha256:701079c6b97103879bb3b6c1fd36842e83b7263168b086aa3eddfebc4b6ae72b`.

Direct runtime JAR extracted from the hosted artifact:

- `Obsidian-0.4.0-phase4-dev2.1.jar`;
- size: **519,998 bytes**;
- SHA-256: **`306b945361b3dd1e5920cce6784fdd133f2cff5c30543911f89f4ca882e2cbdb`**.

Sources JAR:

- `Obsidian-0.4.0-phase4-dev2.1-sources.jar`;
- size: **269,665 bytes**;
- SHA-256: **`434a07578f5f6b3704dc57870fd585b4bba09a355156132a7fd56a338ea5655f`**.

## Runtime handoff

Publish this exact head as GitHub Preview `v0.4.0-phase4-dev2.1` and mirror it to Modrinth.

Reference retest must focus on:

1. render distance **32+** for the main scale sample when practical;
2. ascend through multiple vertical section boundaries in the same X/Z area;
3. descend again;
4. verify the managed overlay/scene follows the player's exact section instead of remaining below;
5. horizontal recenter;
6. F3+T;
7. leave/re-enter;
8. normal exit;
9. explicit human Visual PASS/FAIL.

P4.2 remains draft/do-not-merge until the corrected package closes A-0229 and A-0224 runtime gates.
