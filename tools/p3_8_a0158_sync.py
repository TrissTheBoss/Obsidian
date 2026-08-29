from pathlib import Path

state_path = Path('ai/CURRENT_STATE.md')
log_path = Path('ai/ATTEMPT_LOG.md')

state = state_path.read_text(encoding='utf-8')
old_top = """- First dev15 reference benchmark runtime: **A-0157 PARTIAL only because the measured window had `benchmarkResourceReloadDelta=0`; no source/package defect.**\n- Exact package validation head: `e6f4b81903ddcdcb859d70a1a01c002a3f550e12`; Build workflow `33270995728` SUCCESS.\n- Canonical dev15 JAR: `Obsidian-0.3.0-phase3-dev15.jar`, 456,609 bytes, SHA-256 `eaad8132665e5f662ac30f5e71abbaff3d604f010e09ffd7aa82379c79a9ed65`.\n- Draft P3.8 PR: **#51**; reference benchmark runtime still required before promotion.\n- P3.9 partial remeshing remains out of scope until P3.8 is measured and closed.\n"""
new_top = """- First dev15 reference benchmark runtime: **A-0157 PARTIAL only because the measured window had `benchmarkResourceReloadDelta=0`; no source/package defect.**\n- Successful dev15 reference benchmark closure: **A-0158 SUCCESS / P3.8 PROMOTION-READY**; `meshingBenchmarkEvidenceReady=true` with measured resource reload, recenter, ordinary rebuild, concurrent worker pressure, coherent bounded percentiles and clean lifetime closure.\n- Exact package validation head: `e6f4b81903ddcdcb859d70a1a01c002a3f550e12`; Build workflow `33270995728` SUCCESS.\n- Canonical dev15 JAR: `Obsidian-0.3.0-phase3-dev15.jar`, 456,609 bytes, SHA-256 `eaad8132665e5f662ac30f5e71abbaff3d604f010e09ffd7aa82379c79a9ed65`.\n- Draft P3.8 PR: **#51**; runtime contract is closed by A-0158, exact synchronized promotion-head CI remains before merge.\n- P3.9 partial remeshing remains out of scope until P3.8 promotion merges and P3.9 is frozen in a new immutable attempt.\n"""
if old_top not in state:
    raise SystemExit('CURRENT_STATE top P3.8 anchor mismatch')
state = state.replace(old_top, new_top, 1)

marker = """A valid but slower-than-desired baseline is still valid P3.8 evidence; future optimization targets must be derived from recorded measurements rather than changing the workload after seeing results.\n\n## Durable foundation that remains authoritative\n"""
insert = """A valid but slower-than-desired baseline is still valid P3.8 evidence; future optimization targets must be derived from recorded measurements rather than changing the workload after seeing results.\n\n### A-0158 reference benchmark closure — SUCCESS / PROMOTION-READY\n\nThe exact same canonical dev15 JAR from A-0156 was rerun after A-0157. This time the measured window included the missing F3+T/resource-reload exercise and the complete frozen A-0154 runtime contract closed:\n\n- `meshingBenchmarkEvidenceReady=true`;\n- benchmark duration about `47.121 s`;\n- completed / retained / overflow samples `305 / 305 / 0`;\n- queue wait P50/P95/P99/max `25.7 / 50.5 / 80.0 / 3,683.9 us`;\n- full production-ticket execution mean/P50/P95/P99/max `1.313 / 1.001 / 2.664 / 4.432 / 14.408 ms`;\n- measured workload source quads/reference faces `178,238 / 71,606`;\n- merge candidates `43,239`, merged identities `3,305`, merged covered source faces `7,310`, faces saved `4,005`;\n- output quads `174,233`, vertex/index bytes `19,937,136 / 4,181,592`;\n- max queued/running jobs `1 / 2`, measured worker queue rejections `0`;\n- measured READY/core-dirty/resource-reload/recenter deltas `32 / 1,929 / 1 / 2`;\n- JVM GC count/time deltas `24 / 278 ms`; exact allocation bytes remain intentionally `not-portably-measured`;\n- inherited P3.7 differential proof `308/308` deterministic with real merged coverage and zero missing/duplicate/optimized-without-reference/real mismatches;\n- workers/staging/arena/resources clean; process exit code `0`.\n\nThis is the first trustworthy P3.8 reference baseline. No numerical threshold is retrofitted after seeing it. P3.8 may merge once the synchronized promotion head passes hosted Java 25 / Gradle 9.5.1 Build.\n\n## Durable foundation that remains authoritative\n"""
if marker not in state:
    raise SystemExit('CURRENT_STATE P3.8 section anchor mismatch')
state = state.replace(marker, insert, 1)

old_next = """A-0157 records the first dev15 runtime as PARTIAL with coherent benchmark/correctness/lifetime evidence and exactly one missing frozen exercise: `benchmarkResourceReloadDelta=0`. Rerun the exact same canonical dev15 JAR; after the measured-window arm line, perform F3+T and wait for READY, then also include a small ordinary rebuild, at least one real scene recenter, bounded worker pressure, and normal exit. Require `meshingBenchmarkEvidenceReady=true`; do not change code or waive A-0154.\n\nDo not implement P3.9 during P3.8.\n"""
new_next = """A-0158 closes the frozen P3.8 runtime contract on the exact same canonical dev15 JAR. Require hosted Java 25 / Gradle 9.5.1 Build on the exact synchronized P3.8 promotion head, merge PR #51 `[no-release]` without source/runtime change, then synchronize `main` to P3.8 COMPLETE / P3.9 ACTIVE. After main synchronization, freeze P3.9 partial-remeshing in a new immutable attempt before any P3.9 source change.\n\nDo not implement P3.9 before its contract freeze.\n"""
if old_next not in state:
    raise SystemExit('CURRENT_STATE immediate action anchor mismatch')
state = state.replace(old_next, new_next, 1)
state_path.write_text(state, encoding='utf-8')

log = log_path.read_text(encoding='utf-8')
entry = "- **A-0158** — P3.8 dev15 reference benchmark runtime promotion: exact same canonical dev15 JAR closes frozen A-0154 with `meshingBenchmarkEvidenceReady=true`; 305/305 retained samples with zero overflow, measured F3+T reload delta 1, recenter delta 2, real merged workload, queue/execution percentiles, worker pressure/GC evidence, inherited P3.7 exactness, clean lifetime and exit 0. P3.8 is promotion-ready; exact synchronized-head CI and merge remain. See `ai/attempts/A-0158-phase3-p3.8-dev15-runtime-promotion.md`."
if 'A-0158' not in log:
    if not log.endswith('\n'):
        log += '\n'
    log += entry + '\n'
    log_path.write_text(log, encoding='utf-8')
