# A-0218 — Compact GitHub Actions evidence retrieval policy

**Date:** 2026-09-29  
**Result:** SUCCESS  
**Scope:** AI/tooling/process only; no renderer or package behavior changed.

## Objective

Make GitHub Actions evidence retrieval reliable and context-bounded after live connector testing exposed two different failure modes: push-triggered builds could be missed by a PR-only commit-run helper, and broad run-history retrieval returned very large repeated payloads.

## Evidence that motivated the rule

- Build #799 / run `36457867861` exists for head `0b6e7f84b0ae0e1b3f3cf2d802e866ceff92dcd4`, event `push`, conclusion `success`.
- The connector's commit-workflow-runs helper returned zero runs for that same SHA because its current contract filters to pull-request-triggered runs.
- The general run list did find Build #799, and exact jobs retrieval returned the build/release/Modrinth job outcomes.
- Broad run-list payloads observed in this session were about 12,621 characters for 1 run, 62,605 for 5, and 125,494 for 10 because nested actor/repository metadata repeats.
- The successful Java/Gradle job log alone was about 37,555 characters.
- Failed Build #761 / run `36400212815` was diagnosable with the failure-first path: exact jobs identified `Upload build artifacts` as the failed step, and only failed job `108856026302` needed a log fetch to expose the invalid backslash in the artifact name.

## Action

Integrated a provider-specific Actions evidence protocol into `AGENTS.md`, the operating manual, the Context Governor skill/references, and the Obsidian integration rules.

The required sequence is now: bounded run discovery -> immediate compact projection -> exact run jobs -> failed job/step first -> failed-job logs only -> pointerize bulky raw responses.

The main Build workflow now emits a compact CI evidence summary, and changes to `build.yml` are included in the Context Governor workflow's path filter so external-call/evidence policy validation runs alongside relevant workflow changes.

## Validation and continuity impact

- Existing Context Governor deterministic tests remain the regression gate for core minimization/continuity behavior.
- Normal GitHub Build + Context Governor hosted CI remain the merge gates for this maintenance change.
- No renderer source, runtime contract, roadmap phase/order/status, or package identity changed.
- The active P4.1 handoff and draft PR #57 remain unchanged.
- `ACTIVE_CONTEXT.json` does not need refresh because its active goal, exact identifiers, blockers, security boundaries, and next action are unchanged; this is a refinement under the already-active D-0029 external-call minimization rule.
- `TIMELINE.md` does not need churn because this is a targeted tooling/process refinement, not a public product milestone or roadmap-arc change.

## Next action

Open the maintenance PR and require normal Build + Context Governor validation before merge. Merge as `[no-release]` if CI is green.

