# Obsidian `/ai` Integration

## Bootstrap order

For a normal continuation, do not preload the entire history.

1. Read `ai/context/ACTIVE_CONTEXT.json` and verify its source snapshot against current `main`/active PR state.
2. Read `ai/CURRENT_STATE.md` for authoritative current truth.
3. Read only the relevant roadmap section from `ai/MASTER_ROADMAP.md` unless broad roadmap work is requested.
4. Read `ai/OPERATING_MANUAL.md`, `ai/REPOSITORY_HYGIENE.md`, and this skill's core rules as process constraints.
5. Retrieve only the durable decisions and immutable attempts referenced by the active capsule/task.
6. Expand into older history only when a conflict, regression, provenance question, or roadmap change requires it.

This refines the existing required reading discipline by using progressive loading. It does not downgrade any file's authority.

## Active capsule

`ai/context/ACTIVE_CONTEXT.json` is the fast context layer. It should contain:

- active phase/milestone;
- active branch/PR;
- current package/runtime identities;
- current validation state and blockers;
- durable constraints relevant to the next action;
- repository/process state that materially affects work;
- exact source pointers for each compacted fact;
- next action.

Keep it small. Do not copy entire attempt files into it.

## Refresh triggers

Refresh the active capsule when:

- `CURRENT_STATE.md` changes materially;
- active branch/PR/version/package authority changes;
- a runtime/visual gate closes or fails;
- a durable decision affecting active work changes;
- the active milestone/next action changes;
- before handoff.

The capsule may be updated in the same PR/commit as the source continuity change. If a source changed but the capsule did not need changing, document why in the attempt/PR rather than forcing meaningless churn.

## API/connector behavior in Obsidian

Examples:

- GitHub file read: send repository, exact path/ref, and encoding; do not send the full task description.
- GitHub file update: send repository, path, exact content, message, branch, and current blob SHA only.
- PR lookup: request the exact PR number rather than searching all PRs when known.
- Workflow evidence: request the exact run/job IDs once discovered rather than re-listing all runs.
- Uploaded log analysis: retrieve targeted ranges/queries rather than loading unrelated project documents.

## Handoff

At handoff, the active capsule must pass `validate-checkpoint`. It remains an acceleration layer only; another agent must be able to rehydrate every critical claim from the referenced repository/API evidence.
