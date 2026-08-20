# A-0022 - Prepare validated dev2 for merge and handoff to staging work

**Date:** 2026-08-20  
**Objective:** Convert the successful dev2 runtime result into durable repository state and prepare the next Phase 1 milestone without publishing a misleading full Phase 1 release.  
**Action:** Marked dev2 runtime validation in `CURRENT_STATE.md`; added durable staging/backpressure policy in `DECISIONS.md`; moved new attempt logging to immutable `ai/attempts/` files because feature-branch self-append automation was not reliable; removed the temporary append helper; updated the operating manual; kept PR #4 as a development milestone intended for a `[no-release]` merge.  
**Result:** `SUCCESS` pending final CI/merge gate.  
**Intended effect:** Preserve the successful resource-lifetime evidence and make the next staging/upload work resumable without leaving temporary automation or forcing a public release.  
**Actual effect:** The repository now documents dev2 as runtime validated, records fence-gated reclamation and bounded staging as durable rules, and has a scalable immutable attempt-file format for future agents.  
**Evidence:** User dev2 runtime log with clean `1/1/0` shutdown; PR #4; `ai/attempts/A-0021-dev2-runtime-success.md`; current branch documentation commits.  
**Why:** Large whole-file append logs are awkward through repository content APIs and temporary self-mutating workflows add fragility. Immutable attempt files preserve append-only semantics while being easier for agents to create safely.  
**Side effects / lessons:** Keep `ai/ATTEMPT_LOG.md` as historical record and use `ai/attempts/` for new entries. Temporary diagnostic workflows should remain exceptional and be removed before merge.  
**Next action:** Run final CI on the exact PR head, merge PR #4 with `[no-release]`, create a fresh staging/upload branch from merged `main`, inspect exact Minecraft 26.2 upload/copy APIs, and implement the bounded upload validation milestone.
