# Obsidian Active Context Layer

This directory is the compact bootstrap layer for LLM agents. It exists to reduce repeated context loading without replacing repository truth.

## Authority

`ACTIVE_CONTEXT.json` is **not authoritative**. It is a validated cache/index over:

- `ai/CURRENT_STATE.md`;
- `ai/MASTER_ROADMAP.md`;
- `ai/OPERATING_MANUAL.md`;
- `ai/REPOSITORY_HYGIENE.md`;
- `ai/DECISIONS.md`;
- relevant immutable attempts;
- source, PR, CI, release, and runtime evidence.

If the capsule conflicts with any newer authoritative source, the source wins and the capsule must be refreshed.

## Bootstrap use

1. Read `ACTIVE_CONTEXT.json` first to identify the active goal, exact identifiers, constraints, and recovery pointers.
2. Read `ai/CURRENT_STATE.md` to verify current truth.
3. Rehydrate only the roadmap/decision/attempt/source sections needed for the task.
4. For roadmap/scope changes, load the roadmap governance section before editing.
5. For high-impact/external mutations, re-read exact IDs/SHAs/permissions from source immediately before the call.

## Automatic compression

Use `ai/skills/context-governor/SKILL.md`.

Compression is two-phase: prepare a checkpoint, validate coverage/provenance, then drop re-fetchable bulk. A summary never becomes the sole copy of needed knowledge.

Validate this capsule with:

```bash
python3 ai/skills/context-governor/scripts/context_governor.py validate-checkpoint ai/context/ACTIVE_CONTEXT.json
```

## Refresh policy

Refresh the capsule when current project truth materially changes: active milestone, branch/PR, package authority, runtime gate, durable active constraint, next action, or handoff.

Do not churn it for unrelated wording-only changes. If an authoritative file changes without needing a capsule update, record why in the PR/attempt when that fact would otherwise be ambiguous.
