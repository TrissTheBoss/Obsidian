---
name: context-governor
description: Control LLM working context and minimize data sent to tools, APIs, connectors, MCP servers, and OAuth flows. Use for long-running agent work, repository continuity, tool-heavy sessions, connector/API calls, authorization scope selection, handoffs, or any workflow where context bloat or unnecessary data disclosure is a risk. Automatically compact stale context into validated checkpoints with recovery pointers, rehydrate exact source truth when needed, and construct call-specific payloads that contain only the fields, records, scopes, and credentials transport required for the current operation.
---

# Context Governor

Keep the active context small without turning summaries into new truth, and keep every outbound call limited to the information required for that call.

## Operating loop

1. Establish the active goal and source-of-truth locations.
2. Build a small active capsule from current facts, durable constraints, exact identifiers, open obligations, and source pointers.
3. Before every external call, create a call capsule and send only its required payload.
4. Trigger compaction automatically when the working set becomes tool-output heavy, crosses the configured budget, changes subtask, or reaches handoff.
5. Validate the compacted checkpoint before discarding transient context.
6. Rehydrate from exact sources whenever a needed fact is absent, ambiguous, stale, or summarized too aggressively.
7. Preserve authoritative source material outside the active context; compression changes what is loaded, never what is true.

## Non-negotiable rules

- Treat source files, exact API responses, commits, artifacts, and user instructions as authority; summaries are indexes, not substitutes.
- Never compress away user requirements, safety/security boundaries, durable decisions, active blockers, exact package/commit identifiers, unresolved contradictions, or the next required action.
- Never send whole conversation history, unrelated memories, unrelated files, or broad retrieved records merely because they are available.
- Never copy credentials, tokens, cookies, private keys, or authorization headers from transcript/context into a tool payload. Let the connector/auth layer transport credentials. If a raw API genuinely requires a secret header, reference the secret through the runtime's secure credential mechanism and do not log it.
- For OAuth, request the narrowest action/resource scopes supported. Prefer incremental authorization when the provider supports it. OAuth authorization requests carry protocol/authentication data, not task documents or conversation content.
- For reads, prefer server-side filtering, field projection, ranges, exact IDs, pagination bounds, and metadata-first lookup before retrieving full objects.
- If uncertain whether a field is necessary, omit it first unless omission would make the operation invalid or unsafe. Re-request narrowly if required.
- Do not infer missing knowledge from a compressed summary when the source can be re-read.

## Context tiers

Use four tiers:

- **Tier 0 — invariants:** mission, user requirements, security boundaries, durable decisions. Keep tiny and sticky.
- **Tier 1 — active capsule:** current goal, current state, exact identifiers, blockers, next action, small working facts.
- **Tier 2 — recovery index:** topic -> exact source reference/locator/hash/commit. Keep pointers, not source bodies.
- **Tier 3 — cold truth:** full files, logs, PRs, API responses, historical attempts. Load only on demand.

Never promote Tier 1 text above the authority of Tier 3 source truth.

## Automatic compaction triggers

Compact without waiting for the user when any trigger fires:

- estimated active context exceeds the configured budget;
- more than 8 meaningful tool/API calls have accumulated since the last checkpoint;
- more than 3 large source documents or tool results are simultaneously active;
- a subtask or phase boundary is crossed;
- an external mutation changes repository/service truth materially;
- immediately before handoff;
- a tool result is fully processed and is re-fetchable by stable ID.

If the runtime exposes token counts, use them. Otherwise use the deterministic helper in `scripts/context_governor.py` and a rough 4 characters/token estimate.

## Two-phase compaction

Follow `references/checkpoint-schema.md`.

1. **Prepare:** extract must-preserve knowledge into the checkpoint schema. Replace processed/re-fetchable bulk with recovery pointers.
2. **Validate:** run `scripts/context_governor.py validate-checkpoint <checkpoint.json>`.
3. **Commit:** only after validation succeeds, stop carrying the replaced tool/file bodies forward.
4. **Rehydrate on demand:** follow the checkpoint's recovery pointer to the original source and refresh the active capsule.

If validation fails, retain the previous working set and repair the checkpoint. Never make space by silently deleting a required fact.

For structured context bundles, `compress-bundle` can automatically keep critical items and pointerize recoverable lower-priority items. If critical items alone exceed the budget, it returns `semantic_compaction_required=true` rather than dropping them.

## Call-specific minimization

Before each API/tool/MCP call, apply the **necessity test** from `references/call-minimization.md`:

- What exact operation is being performed?
- Which target resource/record is needed?
- Which identifiers are mandatory?
- Which fields are required by the tool schema or endpoint?
- Which data changes the result of this operation?
- Which response fields/rows/range are actually needed next?
- Which scopes/actions are needed now?
- What available context is explicitly not needed?

Construct an ephemeral call capsule containing only those answers. Do not forward the capsule itself if the endpoint only needs its payload fields.

For deterministic allowlist filtering or audit, run:

```bash
python scripts/context_governor.py minimize-call call.json spec.json
```

For OAuth scope audit, run:

```bash
python scripts/context_governor.py audit-scopes scope_request.json
```

A broader already-granted token does not justify requesting, retrieving, or sending broader data.

## Rehydration rules

Re-read source truth before acting when:

- the checkpoint marks a fact stale or uncertain;
- exact wording, numbers, IDs, hashes, dates, permissions, or gates matter;
- the task moves outside the active capsule's declared scope;
- two compacted facts conflict;
- a source may have changed since the checkpoint;
- a mutation is irreversible or externally visible.

Prefer targeted retrieval (exact file range, record ID, commit, endpoint projection) over reloading the entire corpus.

## Obsidian repository mode

When working in `TrissTheBoss/Obsidian`, also read `references/obsidian-integration.md`.

Use `ai/context/ACTIVE_CONTEXT.json` as a fast bootstrap capsule only after checking its source snapshot. It is never more authoritative than `ai/CURRENT_STATE.md`, `ai/MASTER_ROADMAP.md`, `ai/DECISIONS.md`, relevant immutable attempts, source, or CI/runtime evidence.

## Testing

Run the representative deterministic tests after changing this skill:

```bash
python scripts/test_context_governor.py
```

The tests must prove:

- critical knowledge survives budget pressure;
- every dropped/replaced item remains recoverable by pointer;
- checkpoint validation fails closed when a critical category/source pointer is missing;
- API payload minimization removes unrelated fields;
- secret-like fields are blocked unless explicitly transported through a secure secret path;
- OAuth scope audit detects excess scopes;
- the Obsidian active capsule passes schema/coverage validation.

## References

- `references/checkpoint-schema.md` — checkpoint fields, preservation categories, and two-phase validation.
- `references/call-minimization.md` — API/tool/MCP/OAuth minimization rules.
- `references/obsidian-integration.md` — exact `/ai` integration and bootstrap/recovery procedure.
- `references/research-notes.md` — external design inspirations and what was adopted or rejected.
