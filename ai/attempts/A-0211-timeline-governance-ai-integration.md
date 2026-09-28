# A-0211 - Integrate public timeline maintenance into the AI continuity system

**Date:** 2026-09-28  
**Status:** `SUCCESS` / **TIMELINE GOVERNANCE INTEGRATED**  
**Validation head:** `86eacdbdc16a5fe545c0827975f36310c762ce7b`

## Objective

Make root `TIMELINE.md` a maintained part of Obsidian's mandatory `/ai` operating system rather than a one-time public documentation page.

The timeline must remain useful across future LLM sessions without becoming a competing source of engineering truth.

## Policy integration

### Repository hygiene

`ai/REPOSITORY_HYGIENE.md` now owns the **Timeline maintenance contract**.

The timeline must be synchronized when the material development story changes, including:

- phase or named-milestone activation/completion;
- promotion, rollback, rejection, deferment, or supersession;
- first tester/runtime handoffs or validated checkpoints that materially change the project arc;
- major project-wide operating capabilities such as release/continuity infrastructure;
- material roadmap-arc changes;
- corrections to evidence links or identities already displayed.

The contract also explicitly prevents churn for ordinary attempts, small fixes, diagnostics, wording-only edits, and continuity-only commits that do not change the public development story.

Required structure remains:

- high-level phase/status rail;
- chronological visual milestone path;
- evidence-linked milestone entries;
- explicit current position;
- roadmap-derived future horizon;
- authority disclaimer;
- public documentation navigation.

### Operating manual

`ai/OPERATING_MANUAL.md` now:

- identifies `TIMELINE.md` as a non-authoritative public history/index;
- requires timeline synchronization when the maintenance triggers fire;
- checks timeline freshness in the continuity consistency checklist;
- includes timeline freshness in the handoff definition of done.

### AI bootstrap

`ai/README.md` now tells future agents:

- `REPOSITORY_HYGIENE.md` governs public timeline freshness;
- `TIMELINE.md` is a non-authoritative visual project-history summary;
- material milestone/history transitions require timeline synchronization.

### Durable decision

D-0030 makes the maintenance rule durable:

> Maintain the public project timeline as a governed non-authoritative history index.

The timeline never overrides `CURRENT_STATE.md`, `MASTER_ROADMAP.md`, durable decisions, immutable attempts, source, CI/release, or runtime evidence.

### Active Context Governor capsule

`ai/context/ACTIVE_CONTEXT.json` now includes:

- the D-0030 durable constraint;
- the fact that `TIMELINE.md` is governed and non-authoritative;
- a recovery pointer to the exact timeline maintenance policy;
- corrected Context Governor recovery references that now point to merged `main` evidence instead of the deleted `maintenance/context-governor-evidence` branch.

The active P4.1 goal, exact runtime package identities, promotion gates, and next action are unchanged.

### Timeline self-description

Root `TIMELINE.md` now links directly back to D-0030 / the repository-hygiene maintenance contract so readers and agents can discover how freshness is governed.

## Exact hosted validation

Draft PR #64 exact validation head:

`86eacdbdc16a5fe545c0827975f36310c762ce7b`

Hosted gates:

- Context Governor run `36416585386` / **#5** — **SUCCESS**;
- normal Build run `36416585287` / **#774** — **SUCCESS**.

The Context Governor run validates the refreshed active capsule and deterministic governor behavior on the exact policy head.

## Scope / runtime impact

This work changes documentation and AI continuity policy only.

It does **not** change:

- renderer source;
- runtime resources;
- P4.1 behavior;
- package authority;
- frozen runtime/visual gates;
- roadmap phase order or feature scope;
- release assets.

## Result

Timeline keeping is now part of Obsidian's durable AI operating system. Future agents have explicit triggers, authority boundaries, recovery pointers, and handoff checks that require the public visual history to remain synchronized without treating it as canonical engineering truth.

## Next action

Merge PR #64 `[no-release]` after the final attempt-only head remains CI-green, let Repository Hygiene remove the temporary branch, then continue the unchanged P4.1 reference runtime handoff.
