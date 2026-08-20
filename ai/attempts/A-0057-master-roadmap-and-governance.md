# A-0057 - Canonical master roadmap and governance

**Date:** 2026-08-20  
**Result:** SUCCESS

## Objective

Create one canonical repository document containing Obsidian's full long-range roadmap, planned renderer architecture, feature inventory, validation gates, experimental features, performance/benchmark strategy, compatibility scope, release direction, and explicit procedures for changing that plan.

The project already had strong current-state, decision, and attempt records, but long-range planning was distributed across chat history, `CURRENT_STATE.md`, individual decisions, PR bodies, and attempt files. That made it possible for a future agent to understand current implementation truth while still missing the complete intended product.

## Action

Added `ai/MASTER_ROADMAP.md` as the canonical future-plan document.

The new roadmap defines:

- product mission and priority ordering;
- reference workloads and target scaling behavior;
- roadmap status vocabulary;
- complete architecture north star;
- render-thread, synchronization, memory, and profiling constraints;
- detailed Phase 0 through Phase 12 plan;
- Phase 2 correctness milestones;
- Phase 3 worker-local binary/bitmask greedy meshing and differential validation;
- Phase 4 real-scale GPU visibility/compaction;
- adaptive scheduling/frame pacing;
- transparency/fluids;
- entities;
- block entities;
- particles/weather;
- UI/text/immediate rendering;
- experimental feature program;
- stabilization/public-release readiness;
- feature inventory across renderer domains;
- performance metrics and benchmark validity rules;
- memory strategy;
- compatibility strategy;
- testing ladder;
- release strategy;
- experimental auto-disable direction;
- roadmap dependency rules;
- formal roadmap change governance.

The governance section explicitly separates roadmap edits into:

- Class A: status synchronization;
- Class B: detail refinement;
- Class C: roadmap restructuring;
- Class D: product priority/scope change;
- Class E: removal/rejection of major features.

It specifies when an edit must also update `CURRENT_STATE.md`, create a new immutable attempt, add/supersede a durable decision, and synchronize the active PR/issue.

It also forbids silently deleting major planned features and forbids marking roadmap items complete without the level of evidence required by the actual feature contract.

## Intended effect

Make Obsidian resumable not only at the implementation level but at the **product strategy level**. A future agent should be able to answer all of the following from repository truth:

- What is Obsidian ultimately intended to become?
- What features are planned?
- Why are phases ordered this way?
- Where does greedy meshing belong?
- What is experimental versus baseline?
- What are the required validation gates?
- How should large-render-distance performance be measured?
- What features are explicitly out of scope today?
- What must be updated when the roadmap changes?
- How can a major feature be deferred, rejected, moved, or superseded without losing project history?

## Actual effect

`ai/MASTER_ROADMAP.md` now provides one canonical long-range plan with detailed phase/feature/governance coverage. It preserves the previously established ordering where Phase 2 builds the reference correctness model, Phase 3 implements production binary/bitmask greedy meshing, and later GPU visibility phases scale over those meshes.

No Java/source behavior changed in this attempt.

## Evidence

- New canonical file: `ai/MASTER_ROADMAP.md`.
- Created on active branch `phase2/real-section-reference` while PR #12 is still the coherent active Phase 2 workstream.
- Roadmap creation commit: `c5d7f8ec6508264facec7160387c1e9490de9178`.

## Why this structure

A single giant "project memory" file would mix four different kinds of truth and become unreliable:

1. current implementation state;
2. future intentions;
3. durable reasoning;
4. historical evidence.

The chosen structure keeps those separate:

- `CURRENT_STATE.md` = current truth;
- `MASTER_ROADMAP.md` = future plan;
- `DECISIONS.md` = durable reasoning;
- attempts = evidence/history.

This makes the roadmap editable as planning changes without rewriting the historical record.

## Side effects / lessons

- Roadmap status must never be treated as proof of implementation.
- `CURRENT_STATE.md` should stay concise and not duplicate the complete roadmap.
- Major roadmap edits require evidence/governance discipline just like architecture changes.
- The roadmap itself can be rewritten for clarity because historical rationale belongs in immutable attempts and durable decisions.

## Next action

Update `ai/README.md`, `ai/OPERATING_MANUAL.md`, and root `AGENTS.md` so the new master roadmap is mandatory reading and its alteration procedure is part of normal handoff/development workflow. Synchronize PR #12's continuity note and let normal CI validate the documentation-only branch head.