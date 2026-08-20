# Obsidian AI Continuity Directory

This directory is the persistent operating memory for AI agents and humans working on Obsidian.

Its purpose is to make the project resumable across conversations, models, agents, machines, and long gaps in development without relying on hidden chat context.

## Required reading order

Every agent taking over Obsidian should read these files before making changes:

1. `ai/CURRENT_STATE.md` - what exists right now, what is validated, what branch/PR/version is active, and what the immediate next action is.
2. `ai/MASTER_ROADMAP.md` - the canonical long-range product plan: phases, planned features, architecture direction, validation gates, experiments, compatibility/release strategy, and the formal procedure for altering the roadmap.
3. `ai/OPERATING_MANUAL.md` - project goals, constraints, engineering workflow, validation rules, handoff rules, and roadmap-maintenance procedure.
4. `ai/DECISIONS.md` - durable architectural and product decisions and why they were made.
5. `ai/ATTEMPT_LOG.md` - historical append-only record through the original log format.
6. `ai/attempts/` - immutable one-file-per-attempt continuation for experiments, validations, research, failures, roadmap research, and architecture work.

Do not skip `MASTER_ROADMAP.md` merely because `CURRENT_STATE.md` is up to date. `CURRENT_STATE.md` is intentionally about the present; the roadmap is where the complete intended product and sequencing live.

## What each continuity file is authoritative for

Use the documents for different questions rather than treating them as interchangeable:

- **What is true right now?** -> `CURRENT_STATE.md`.
- **What are we trying to build over the life of the project?** -> `MASTER_ROADMAP.md`.
- **Why did we choose this architecture/product direction?** -> `DECISIONS.md`.
- **What exactly was tried and what happened?** -> `ATTEMPT_LOG.md` and `attempts/`.
- **What code actually exists?** -> source + exact commit/branch.
- **What binary is authoritative?** -> GitHub CI/release artifact for the exact validated commit.

If these disagree, do not guess. Inspect timestamps/commit history and reconcile them. A newer active durable decision overrides stale roadmap wording until the roadmap is synchronized; actual source/runtime evidence overrides a roadmap claim that something is already implemented.

## Core rule

Do not rely on memory when the repository can contain the answer.

If an agent tries something that changes code, build behavior, runtime behavior, tooling, CI, release behavior, architecture, a project assumption, or a meaningful roadmap direction, it must record the attempt whether it succeeded or failed.

Older attempts live in `ATTEMPT_LOG.md`. New attempts should be created as immutable files under `ai/attempts/` using names such as `A-0058-short-description.md`. This avoids replacing a large history file merely to append one entry and makes concurrent agent work safer.

When a successful attempt changes the current truth of the project, also update `CURRENT_STATE.md`. When it creates or reverses a durable design choice, also update `DECISIONS.md`. When it changes the long-range plan, phase ordering, product feature set, validation gates, experiments, or release/compatibility strategy, update `MASTER_ROADMAP.md` according to its Roadmap Governance section.

## Roadmap discipline

`MASTER_ROADMAP.md` is editable canonical plan state, **not** an append-only history file. It should stay readable as the best current plan while history/reasons remain durable elsewhere.

Before altering it:

1. Read the Roadmap Governance section in `MASTER_ROADMAP.md`.
2. Classify the change as status synchronization, detail refinement, restructuring, product priority/scope change, or major feature removal/rejection.
3. Preserve evidence/reasoning in a new immutable attempt when substantive research or planning caused the change.
4. Add or supersede a durable decision for major architecture/product-policy changes.
5. Synchronize `CURRENT_STATE.md` when the active/current/next milestone changes.
6. Mention material roadmap changes in the active PR/issue.
7. Never silently delete a major promised/planned feature or rewrite history to make a previous plan disappear.
8. Never mark a roadmap feature COMPLETE without the evidence appropriate to that feature's contract.

A full copy of the procedure lives in `MASTER_ROADMAP.md`; `OPERATING_MANUAL.md` defines how it fits into normal engineering/handoff work.

## Log discipline

- Keep failed attempts. Never rewrite history to make the project look cleaner than it was.
- Attempt IDs remain globally monotonic across `ATTEMPT_LOG.md` and `ai/attempts/`.
- Once an attempt file is committed, treat it as immutable. If later evidence changes the interpretation, create a new attempt that supersedes it.
- Prefer evidence: commit SHAs, tags, build output, profiler captures, issue links, benchmark files, crash reports, reproducible commands, or named public research references when an architecture decision comes from external study.
- State the intended effect, the actual result, and why the result happened when known.
- If the cause is not known, write `unknown` rather than guessing.
- Mark obsolete conclusions as `SUPERSEDED` in a later attempt; do not delete historical evidence.
- Never store credentials, access tokens, private keys, cookies, passwords, or other secrets here.

## Repository truth

The canonical repository is `TrissTheBoss/Obsidian`.

The canonical binaries are artifacts built by the repository CI/release workflow against the real Minecraft/Fabric dependency set. Locally mocked or manually assembled JARs are not release-authoritative unless a later decision explicitly changes this rule.
