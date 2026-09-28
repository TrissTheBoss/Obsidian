# A-0209 - Context Governor hosted validation and activation

**Date:** 2026-09-28  
**Status:** `SUCCESS` / **CONTEXT GOVERNOR ACTIVE AND PROVEN**  
**Integration merge:** `623dc5904fae5e3d0be7d90d13236ee99be32b37`

## Objective

Close A-0208 by proving the Context Governor integration through exact-head hosted CI, `[no-release]` merge, post-merge validation on `main`, and repository-hygiene cleanup.

## Exact integration evidence

PR #61: `Add Context Governor for recoverable LLM context and least-data API calls`.

Exact integration head:

`16cde38d8a96f377687f448b11d7f2d05033df90`

Hosted exact-head gates:

- normal Build run `36404017997` / **#768** — **SUCCESS**;
- Context Governor run `36404018090` / **#1** — **SUCCESS**.

The Context Governor workflow executed the deterministic regression suite and active-capsule validation on the exact PR head.

PR #61 squash-merged with explicit `[no-release]` as:

`623dc5904fae5e3d0be7d90d13236ee99be32b37`

No renderer source or P4.1 runtime package behavior changed.

## Post-merge evidence

On exact merged `main` commit `623dc5904fae5e3d0be7d90d13236ee99be32b37`:

- Context Governor run `36404117650` / **#2** — **SUCCESS**;
- normal Build run `36404117683` / **#769** — **SUCCESS**;
- Repository Hygiene run `36404117664` / **#8** — **SUCCESS**.

After Repository Hygiene completed, the repository returned to exactly two branches:

- `main`;
- `phase4/p4.1-persistent-scene-visibility`.

## Proven behavior

The integration now enforces and tests:

1. critical user/project knowledge survives context-budget pressure;
2. lower-priority processed/re-fetchable bulk becomes a recovery pointer rather than disappearing;
3. critical-over-budget compaction fails closed and preserves the critical item;
4. compact checkpoints require all six preservation classes and source provenance;
5. discarded IDs must map to recovery-index entries;
6. API/tool payloads are allowlisted to task-necessary fields and unrelated context is dropped;
7. credential-like modeled fields are blocked unless explicitly designated as secure transport;
8. ordinary non-secret parameters such as `max_tokens` are not falsely blocked;
9. OAuth scope audit detects missing/excess scopes and computes incremental scope deltas;
10. Obsidian's active context capsule validates as a non-authoritative, rehydratable index.

## Active policy

D-0029 is now active on `main`.

Agents should normally:

- bootstrap from `ai/context/ACTIVE_CONTEXT.json`;
- verify `ai/CURRENT_STATE.md`;
- rehydrate only relevant roadmap/process/decision/attempt/source sections;
- re-read exact authoritative IDs/SHAs/hashes/permissions/gates before high-impact external mutations;
- keep external API/tool/MCP payloads call-specific and data-minimal;
- use least-privilege/incremental OAuth authorization where supported;
- keep credentials/tokens out of durable/model context.

If the compact layer and source disagree, source truth wins.

## Standalone skill package

The standalone `context-governor` skill passed the Skill validator and deterministic 7-test suite before packaging.

Final clean `skill.zip`:

- size: **16,090 bytes**;
- SHA-256: **`148e6911419cea13da42b1a78a183fa0cb7e9e84614804587aad98bd39e58bda`**;
- contains no `__pycache__` or `.pyc` files.

## Result

The Context Governor is no longer only a design proposal. It is integrated into the repository's AI continuity system, tested locally and in hosted CI, validated again on merged `main`, and protected by ongoing CI.

P4.1 remains the active engineering milestone and PR #57 remains draft. The next renderer action is still the frozen dev1 reference-machine runtime/visual test.

## Next action

Synchronize `ai/CURRENT_STATE.md` and `ai/context/ACTIVE_CONTEXT.json` to this final activation evidence, merge the evidence-only PR `[no-release]`, let Repository Hygiene remove the temporary branch, then resume P4.1 runtime validation.
