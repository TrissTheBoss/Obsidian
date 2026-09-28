# Checkpoint Schema

## Purpose

A checkpoint is a compact working-memory replacement with explicit provenance. It is not authoritative truth. Its job is to preserve what must remain immediately available and make everything else recoverable.

## Required JSON shape

```json
{
  "schema_version": 1,
  "active_goal": "...",
  "scope_boundary": "...",
  "source_snapshot": {
    "created_at": "ISO-8601 timestamp",
    "repo_or_system": "identifier",
    "authority_refs": ["stable source references"]
  },
  "coverage_manifest": [
    "user_requirements",
    "current_state",
    "durable_decisions",
    "open_obligations",
    "exact_identifiers",
    "security_boundaries"
  ],
  "facts": [{"id":"F1","text":"...","source_ref":"..."}],
  "decisions": [{"id":"D1","text":"...","source_ref":"..."}],
  "open_obligations": [{"id":"O1","text":"...","source_ref":"..."}],
  "exact_identifiers": [{"id":"I1","text":"...","source_ref":"..."}],
  "security_boundaries": [{"id":"S1","text":"...","source_ref":"..."}],
  "recovery_index": [
    {"topic":"...","source_ref":"...","locator":"...","content_sha256":"optional"}
  ],
  "discarded_ids": ["bulk-tool-17"],
  "next_action": "..."
}
```

## Must-preserve categories

The validator requires explicit coverage for:

1. `user_requirements`
2. `current_state`
3. `durable_decisions`
4. `open_obligations`
5. `exact_identifiers`
6. `security_boundaries`

A category may be represented by an empty array only when the checkpoint explicitly states that no item exists and provides a source proving that state. For normal project work, keep at least one current-state, exact-identifier, and open-obligation item.

## Source references

Prefer stable, re-readable references:

- repository path + exact commit SHA + heading/line range;
- PR/issue/release/run ID;
- API resource ID + endpoint/field projection;
- uploaded file ID + line range;
- durable URL + version/date;
- exact artifact hash.

Avoid references like "earlier in chat" when a repository/file/API source exists.

## Two-phase commit rule

1. Build candidate checkpoint.
2. Validate schema and source coverage.
3. Verify every discarded ID is represented in `recovery_index` or explicitly classified as disposable/re-fetchable with a stable source.
4. Only then clear/reduce old context.

If a fact cannot be recovered and might matter later, keep it in the checkpoint.

## Loss detection

Before handoff or high-impact mutation, ask these recovery questions:

- What is the current goal and immediate next action?
- What exact branch/PR/version/commit/artifact is active?
- What user requirements or validation gates must not change?
- What decisions constrain the implementation?
- What is still unproven or blocked?
- Where can the full evidence for each answer be reloaded?

If any answer is absent or source-less, the checkpoint is incomplete.
