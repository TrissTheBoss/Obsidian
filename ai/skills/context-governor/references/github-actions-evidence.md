# GitHub Actions Evidence Retrieval Protocol

This is the provider-specific extension of the Context Governor for GitHub Actions evidence in `TrissTheBoss/Obsidian`.

## Goal

Retrieve enough CI evidence to identify the authoritative run, job outcome, and failure cause without flooding the active context with repeated repository metadata or full logs.

## Required sequence

### 1. Bounded run discovery

Query only a small candidate set.

- Normal default: 5 runs.
- Default ceiling: 10 runs; exceed it only for a named historical/audit reason.
- Use server-side filters such as failure/status/branch/event when the available endpoint supports them.
- If an exact run ID is already known, skip discovery entirely.

Immediately reduce every discovered run to:

- `id`
- `name`
- `run_number`
- `head_sha`
- `event`
- `status`
- `conclusion`
- `created_at`
- `updated_at`

Repository, actor, triggering-actor, URL-template, and other nested objects are Tier 3 data unless a specific diagnosis requires them.

### 2. Do not lose push builds

The currently available commit-workflow-runs connector helper filters to pull-request-triggered runs.

Therefore:

- never use an empty result from that helper as proof that a push-triggered build does not exist;
- for push evidence, use bounded repository/workflow run discovery or an exact run endpoint;
- if the helper contract changes in the future, verify the new contract before relaxing this rule.

### 3. Exact run -> jobs

Once the relevant run ID is known, stop re-listing workflow history.

Retrieve the exact run's jobs and retain only job ID/name/status/conclusion plus failed-step identity when applicable. Successful step-by-step detail is normally unnecessary after success is established.

### 4. Failure-first diagnosis

Order diagnostic attention as:

1. failed job;
2. failed step inside that job;
3. exact failed-job log only if the step summary is insufficient;
4. artifacts or additional successful-job evidence only when a named validation contract requires them.

Do not bulk-fetch every job log "just in case."

### 5. Logs remain cold evidence

A workflow log is Tier 3/re-fetchable evidence. After extracting the error/failure cause:

- keep the exact run ID and job ID;
- keep a short diagnosis and the minimum relevant error anchor;
- pointerize/discard the rest of the raw log from active context.

If logs are unusually large, prefer provider-side ranges/search when available. If only full-log retrieval exists, fetch one failed job at a time.

## Compact evidence record

A durable/active summary should contain the exact run/job identifiers, compact run metadata, failed step identity, diagnosis, and a recovery pointer such as `github:actions/run/<run-id>/job/<job-id>`. Do not copy actor/repository objects or the full log into continuity files.

## Workflow support

The main Build workflow emits a final `OBSIDIAN_CI_EVIDENCE` line and GitHub step summary with run ID/attempt, event, SHA/ref, compile outcome, and artifact-upload outcome. This is a compact orientation aid; GitHub's exact run/job API remains authoritative.

