# Obsidian Agent Instructions

Before changing this repository, read `ai/README.md` and follow its progressive required reading order. Start with `ai/context/ACTIVE_CONTEXT.json` as a non-authoritative bootstrap, verify `ai/CURRENT_STATE.md`, and rehydrate exact source truth as needed.

The `ai/` directory is the persistent project memory for:

- current implementation/runtime state;
- the canonical full product roadmap and planned features;
- operating/process rules;
- architectural/product decisions;
- experiment/research history;
- failures and superseded approaches;
- handoff information.

`ai/MASTER_ROADMAP.md` remains canonical long-range plan authority; load its relevant sections normally and its full governance/affected sections before roadmap or scope changes. `ai/REPOSITORY_HYGIENE.md` is mandatory for branch lifecycle/public surface. `ai/skills/context-governor/SKILL.md` is mandatory for context compression, handoff, external API/tool/MCP payload construction, and OAuth scope minimization.

After any meaningful engineering or roadmap attempt, update the relevant `ai/` records as described in `ai/OPERATING_MANUAL.md`, refresh/validate `ai/context/ACTIVE_CONTEXT.json` when material active truth changed, then run the repository/readme/release handoff checks in `ai/REPOSITORY_HYGIENE.md`.

Never silently delete a major roadmap feature or rewrite historical attempt evidence to match a newer plan. Preserve the reason in immutable attempts and/or durable decisions, then keep the roadmap itself clean as the best current plan.


## Context and external-call discipline

- Do not preload full historical attempts/tool output when the active capsule and targeted source retrieval are enough.
- Never treat compressed summaries as stronger evidence than the repository/API/runtime source they point to.
- Before external calls, send only the target IDs/fields/records/scopes required for that operation. Exclude unrelated conversation/project context.
- Prefer exact resource reads, field projection, filters and bounded ranges/pages.
- OAuth scopes must be least-privilege and incremental where supported; credentials must not be copied into AI continuity/context.
