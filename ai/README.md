# Obsidian AI Continuity Directory

This directory is the persistent operating memory for AI agents and humans working on Obsidian.

Its purpose is to make the project resumable across conversations, models, agents, machines, and long gaps in development without relying on hidden chat context.

## Progressive required reading order

Every agent must preserve the same authority hierarchy without preloading the entire history.

1. Read `ai/context/ACTIVE_CONTEXT.json` as a **non-authoritative bootstrap index**. It identifies the active goal, exact identifiers, active constraints, open obligations, and recovery pointers.
2. Read `ai/CURRENT_STATE.md` to verify authoritative current truth before engineering or external mutations.
3. Read the **relevant section(s)** of `ai/MASTER_ROADMAP.md`. Before changing phase order, scope, validation gates, feature status, compatibility, or release strategy, read the full Roadmap Governance section and all affected roadmap sections.
4. Read the relevant process constraints from `ai/OPERATING_MANUAL.md`, `ai/REPOSITORY_HYGIENE.md`, and `ai/skills/context-governor/SKILL.md`.
5. Rehydrate only the durable decisions and immutable attempts referenced by the active task/capsule.
6. Expand into `ai/ATTEMPT_LOG.md` and older `ai/attempts/` only for provenance, regression analysis, conflicting evidence, or roadmap/decision changes.

This is **progressive loading, not weaker governance**. `ACTIVE_CONTEXT.json` is a cache/index and never overrides `CURRENT_STATE.md`, the roadmap, decisions, immutable attempts, source, CI, release, or runtime evidence. If a needed fact is absent, stale, ambiguous, or exact wording/identifiers matter, re-read the authoritative source.

For broad project audits, roadmap restructuring, or handoffs where the active capsule indicates unresolved contradictions, load more of the canonical documents as needed rather than relying on the compact layer.

## What each continuity file is authoritative for

Use the documents for different questions rather than treating them as interchangeable:

- **What is true right now?** -> `CURRENT_STATE.md`.
- **What are we trying to build over the life of the project?** -> `MASTER_ROADMAP.md`.
- **How do we keep LLM context bounded and external calls data-minimal without losing recoverability?** -> `context/ACTIVE_CONTEXT.json` + `skills/context-governor/SKILL.md`.
- **How must the GitHub repository, branches, README, and releases be kept clean?** -> `REPOSITORY_HYGIENE.md`.
- **Why did we choose this architecture/product direction?** -> `DECISIONS.md`.
- **What exactly was tried and what happened?** -> `ATTEMPT_LOG.md` and `attempts/`.
- **What code actually exists?** -> source + exact commit/branch.
- **What binary is authoritative?** -> GitHub CI/release artifact for the exact validated commit.

If these disagree, do not guess. Inspect timestamps/commit history and reconcile them. A newer active durable decision overrides stale roadmap wording until the roadmap is synchronized; actual source/runtime evidence overrides a roadmap claim that something is already implemented.

## Core rule

Do not rely on memory when the repository can contain the answer.

If an agent tries something that changes code, build behavior, runtime behavior, tooling, CI, release behavior, architecture, a project assumption, or a meaningful roadmap direction, it must record the attempt whether it succeeded or failed.

Older attempts live in `ATTEMPT_LOG.md`. New attempts should be created as immutable files under `ai/attempts/` using names such as `A-0058-short-description.md`. This avoids replacing a large history file merely to append one entry and makes concurrent agent work safer.

When a successful attempt changes the current truth of the project, also update `CURRENT_STATE.md`. When it creates or reverses a durable design choice, also update `DECISIONS.md`. When it changes the long-range plan, phase ordering, product feature set, validation gates, experiments, or release/compatibility strategy, update `MASTER_ROADMAP.md` according to its Roadmap Governance section. When it changes public status, branch lifecycle, README content, or tester-facing release behavior, also apply `REPOSITORY_HYGIENE.md`. When active milestone/branch/package/gates/next-action truth changes, refresh and validate `context/ACTIVE_CONTEXT.json` so future agents can bootstrap narrowly without losing recovery pointers.

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
