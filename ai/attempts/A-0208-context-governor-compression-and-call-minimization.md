# A-0208 - Context Governor: recoverable LLM context compression and least-data external calls

**Date:** 2026-09-28  
**Status:** `PARTIAL` / **IMPLEMENTED AND LOCALLY TESTED / HOSTED CI + MERGE REQUIRED**  
**Branch:** `maintenance/context-governor`

## Objective

Add a reusable LLM operating skill and repository-native context layer that keeps long-running Obsidian work within a bounded active context while preserving every critical requirement through exact recovery pointers, and that minimizes data/authorization sent to external APIs, connectors, MCP servers, and OAuth flows.

The design must reduce context/tool-output bloat without allowing a summary to become the only copy of needed knowledge.

## External design research

The implementation combines proven ideas rather than inventing an isolated memory model:

- **MemGPT / virtual context:** tier a small active working set over colder external truth and explicitly rehydrate on demand.
- **Anthropic context editing/compaction:** distinguish summarization from clearing re-fetchable tool results; preserve recent/important items and use structured memory separately.
- **OAuth Security BCP (RFC 9700):** restrict privileges/audience/resources/actions to what the current use case needs.
- **Google OAuth incremental authorization:** request additional scopes in context as capabilities are actually needed.
- **MCP authorization guidance:** bind authorization to the intended resource/tool boundary rather than treating an authenticated session as blanket data authority.
- **Microsoft Graph best practices/query parameters:** retrieve only required data with field projection, filters, paging, and minimal response representations.
- **AWS IAM least privilege:** grant only permissions required for the task.

Research links and adopted/rejected details are recorded in `ai/skills/context-governor/references/research-notes.md`.

## Implemented architecture

### Four-tier working context

1. **Tier 0 — invariants:** user requirements, security boundaries, durable decisions.
2. **Tier 1 — active capsule:** current goal/state, exact identifiers, blockers/open obligations, next action.
3. **Tier 2 — recovery index:** stable source pointers/locators/hashes without full bodies.
4. **Tier 3 — cold truth:** repository files, logs, PRs, CI/release/runtime evidence and older immutable attempts.

Tier 1 never outranks Tier 3 source truth.

### Automatic compaction

Compaction triggers when the active working set exceeds budget, after substantial tool/API activity, when too many large sources/results are active, at subtask/phase boundaries, after material external mutations, before handoff, or once a processed tool result is re-fetchable by stable ID.

Compaction is two-phase:

1. prepare a checkpoint containing every must-preserve knowledge class plus recovery pointers;
2. validate schema, provenance and recoverability;
3. only after validation succeeds, stop carrying re-fetchable/transient bulk;
4. rehydrate exact source truth on demand.

If critical knowledge alone exceeds the budget, the helper returns `semantic_compaction_required=true` rather than discarding it.

Required preservation classes:

- user requirements;
- current state;
- durable decisions;
- open obligations/blockers;
- exact identifiers/hashes/commits/package authority;
- security boundaries.

### External call minimization

Before each API/tool/MCP call, construct an ephemeral necessity set and send only:

- exact operation/target resource;
- mandatory identifiers;
- schema/result-changing request fields;
- safe concurrency/version/idempotency fields;
- minimal response projection/range needed for the next step;
- minimum required auth action/resource scopes.

Explicitly exclude unrelated conversation history, memories, files, previous tool output, model reasoning, and merely-available records.

OAuth is treated as a privilege protocol, not a task-data transport channel. Prefer incremental/step-up authorization. Credentials/tokens/cookies/private keys must not enter durable AI context and are blocked by the deterministic modeled-payload helper unless an explicit secure transport path is declared.

## Repository integration

Added:

- `ai/skills/context-governor/SKILL.md`;
- skill references for checkpoint schema, call minimization, Obsidian integration and research notes;
- deterministic `context_governor.py` helper;
- deterministic regression test suite;
- `ai/context/ACTIVE_CONTEXT.json` as a non-authoritative compact bootstrap capsule;
- `ai/context/README.md`;
- `.github/workflows/context-governor.yml`.

Updated:

- `ai/README.md` from unconditional full-history preload to progressive loading with exact-source rehydration;
- `ai/OPERATING_MANUAL.md` with context/call/OAuth governance;
- `AGENTS.md` with mandatory Context Governor discipline;
- `ai/DECISIONS.md` with D-0029.

P4.1 renderer/runtime truth is intentionally unchanged.

## Local deterministic tests

Representative local test suite result:

- PASS `test_compression_and_recovery`;
- PASS `test_semantic_compaction_fail_closed`;
- PASS `test_call_minimization`;
- PASS `test_secret_block`;
- PASS `test_nonsecret_token_parameter_allowed`;
- PASS `test_scope_audit`;
- PASS `test_checkpoint_validation`;
- **PASS all 7 Context Governor tests**.

The skill validator also passed.

One regression was caught during development: an overly broad secret-name detector would have treated ordinary `max_tokens` as a credential field. The matcher was narrowed to credential-bearing names such as `access_token`, `refresh_token`, `api_key`, `client_secret`, authorization/cookie/private-key/password forms, and a regression test now protects the distinction.

The initial `ai/context/ACTIVE_CONTEXT.json` passes the checkpoint validator and includes exact recovery entries for the full roadmap/process/decision/P4 contract/package/repository-history sources that are intentionally not held in the compact working set.

## Skill packaging

The standalone ChatGPT-style `context-governor` skill validates successfully and is packageable as `skill.zip`. Generated cache files are excluded before final handoff.

## Remaining gate

Do not mark this attempt final SUCCESS until:

1. normal hosted Build CI passes on the exact integration head;
2. the new Context Governor workflow passes its regression suite and active-capsule validation on the exact integration head;
3. the maintenance PR merges `[no-release]`;
4. post-merge Context Governor validation succeeds on `main`;
5. repository hygiene removes the short-lived maintenance branch.

## Next action

Run hosted CI through the maintenance PR. If both Build and Context Governor are green, merge `[no-release]`, record final activation evidence in a new immutable attempt, refresh the active capsule/source snapshot as needed, and keep P4.1 runtime validation as the engineering handoff.
