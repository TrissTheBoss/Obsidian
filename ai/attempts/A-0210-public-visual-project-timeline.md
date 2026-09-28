# A-0210 - Public visual project timeline and documentation navigation

**Date:** 2026-09-28  
**Status:** `SUCCESS / DOCUMENTATION-ONLY CHANGE`

## Objective

Add a detailed visual development timeline to Obsidian's public GitHub surface without changing renderer code, runtime behavior, package authority, roadmap scope, or validation gates.

The requested GitHub repository overview already exposes GitHub-managed **README** and **Contributing** tabs. GitHub does not provide an arbitrary custom community-file tab label for a project-specific timeline, so the repository uses the closest supported public surface:

- a root-level `TIMELINE.md` page;
- a prominent **Timeline** link in the README landing-page navigation;
- matching navigation in `CONTRIBUTING.md`;
- a root file that is immediately visible in the repository file list.

## Implemented page

`TIMELINE.md` includes:

1. a Mermaid-rendered phase rail for Phases 0-12;
2. a detailed Mermaid chronological milestone graph;
3. an evidence-linked milestone table covering roadmap creation, Phase 2/3 milestones, P3.9 rejection/deferment, P3.10 promotion, Phase 4 activation, repository hygiene, and Context Governor activation;
4. a focused **Where the project is now** section for P4.1;
5. a rendered Phase 4 -> Phase 12 future horizon;
6. links back to current state, roadmap, operating manual, decisions, attempts, README and contributing guidance.

The timeline explicitly states that it is a public visual summary. `ai/CURRENT_STATE.md` remains authoritative for current implementation/runtime/package/handoff truth, while `ai/MASTER_ROADMAP.md` remains authoritative for planned scope and phase ordering.

## Public navigation

The README landing-page navigation now includes:

`Releases · Timeline · Roadmap · Current state · Contributing`

The Project documents section also links `TIMELINE.md` directly.

`CONTRIBUTING.md` received a matching documentation navigation row so users can move between the public pages without returning to the file list.

## Continuity

`ai/CURRENT_STATE.md` now records `TIMELINE.md` as part of the maintained public repository surface.

No `ai/context/ACTIVE_CONTEXT.json` refresh is required because the active milestone, branch/PR identity, runtime package, validation gates, exact renderer identifiers, and immediate P4.1 next action are unchanged.

## Renderer/runtime impact

None.

No files under `src/`, build configuration, workflows, runtime resources, renderer package authority, P4.1 source, or release assets are changed by this work.

## Result

Obsidian now has a first-class detailed graphical project-history page reachable directly from the repository landing page and cross-linked from contributor documentation, while preserving the distinction between visual public history and authoritative engineering state.
