# A-0227 - Bound GitHub Actions waiting and stale-status handling

**Date:** 2026-09-29  
**Result:** SUCCESS / PROCESS CORRECTION STAGED  
**Scope:** AI/tooling/process only; no renderer, package, roadmap scope, or release-channel behavior changed.

## Objective

Prevent Obsidian agent sessions from getting stuck while "waiting for CI" after GitHub Actions work has already completed or after the branch head has moved to a newer commit.

## Investigation

A-0218 previously fixed two Actions evidence problems:

- broad run-history retrieval produced large repeated payloads;
- the commit-workflow-runs connector helper can miss push-triggered runs because its current contract filters to pull-request-triggered runs.

That protocol bounded **retrieval size**, but it did not bound **waiting/liveness**. There was no hard maximum on repeated status observations, no moving-head rule, and no defined fallback when exact job state is terminal but the run wrapper remains nonterminal.

The latest P4.2 session rules out an actually-running GitHub workflow as the reason the session remained stuck:

- Publish Preview #12 / run `36606727229` completed SUCCESS at 2026-09-29T17:43:24Z;
- both publication jobs completed SUCCESS, including Modrinth mirror job `109537853446`;
- the final recorded PR #75 head is `1ef1874495d0999d894ebd296ae12d2206e501c3`;
- that exact final head passed Build #826 / run `36607234927` and Context Governor #50 / run `36607234704`;
- Build #826 completed at 2026-09-29T17:46:23Z and Context Governor #50 completed at 2026-09-29T17:45:55Z.

PR #75 also accumulated multiple continuity commits after source/package validation and after Preview publication. Because every new PR head can trigger new CI, an agent that begins waiting and then commits again can accidentally chase a moving authority target.

## Root cause

The process contract was incomplete.

The agent had guidance for selecting the correct run and minimizing payload size, but no explicit liveness invariant requiring it to stop polling. A stale/nonterminal run wrapper, repeated identical status response, or a newer branch head could therefore keep an agent in an open-ended observation loop even though useful work was already complete.

This is an agent/process failure mode, not evidence that the P4.2 build, GitHub Preview publication, or Modrinth upload remained unfinished.

## Correction

The Actions evidence protocol now requires:

1. capture/freeze the expected branch or PR head before exact-head CI gating;
2. if the head moves, abandon the old run as a gate and switch once to the new head;
3. never make more than **3** state observations for one exact run/head;
4. stop earlier after **2 identical nonterminal snapshots**;
5. never tight-loop or repeatedly re-list workflow history while an exact run ID is known;
6. when all exact jobs are terminal but the run wrapper is still nonterminal, perform one exact-run cross-check and then treat a persistent mismatch as stale run metadata rather than continuing to wait;
7. if the bounded observation budget expires while work is genuinely still nonterminal, preserve the exact run/head as an open obligation and return control instead of blocking the session.

A deterministic `actions-poll-decision` helper was added to Context Governor so the stop/switch/cross-check policy is regression-testable.

## Files changed

- `AGENTS.md`
- `ai/OPERATING_MANUAL.md`
- `ai/skills/context-governor/SKILL.md`
- `ai/skills/context-governor/references/github-actions-evidence.md`
- `ai/skills/context-governor/references/obsidian-integration.md`
- `ai/skills/context-governor/scripts/context_governor.py`
- `ai/skills/context-governor/scripts/test_context_governor.py`

## Continuity impact

This refines the existing D-0029 context/external-call discipline and A-0218 Actions evidence protocol. It does not change the active product milestone, P4.2 frozen contract, renderer source, package authority, or runtime gates.

The active P4.2 branch already contains the current product handoff and published dev2 evidence. This maintenance change therefore does not rewrite `ACTIVE_CONTEXT.json` or the public timeline.

## Next action

Require normal hosted Build + Context Governor validation on the maintenance PR. Merge `[no-release]` if green, then return product work to PR #75 / the published P4.2 dev2 reference-runtime exercise.
