from pathlib import Path

state = Path('ai/CURRENT_STATE.md')
text = state.read_text()
old = '- Dev15 package/runtime handoff: **A-0156**.\n- Exact package validation head: `e6f4b81903ddcdcb859d70a1a01c002a3f550e12`; Build workflow `33270995728` SUCCESS.\n'
new = '- Dev15 package/runtime handoff: **A-0156**.\n- First dev15 reference benchmark runtime: **A-0157 PARTIAL only because the measured window had `benchmarkResourceReloadDelta=0`; no source/package defect.**\n- Exact package validation head: `e6f4b81903ddcdcb859d70a1a01c002a3f550e12`; Build workflow `33270995728` SUCCESS.\n'
if text.count(old) != 1:
    raise SystemExit('CURRENT_STATE package anchor mismatch')
text = text.replace(old, new, 1)
old2 = 'A-0156 records exact hosted dev15 package authority. Run the canonical direct dev15 JAR through the frozen representative benchmark exercise: wait for settled measured-window arm, perform multiple ordinary rebuilds with READY recovery, F3+T with READY recovery, actual scene recenter with READY recovery, create bounded concurrent worker pressure, wait for the benchmark gate when possible, exit normally, and return the complete log. Do not promote P3.8 from package CI alone.\n'
new2 = 'A-0157 records the first dev15 runtime as PARTIAL with coherent benchmark/correctness/lifetime evidence and exactly one missing frozen exercise: `benchmarkResourceReloadDelta=0`. Rerun the exact same canonical dev15 JAR; after the measured-window arm line, perform F3+T and wait for READY, then also include a small ordinary rebuild, at least one real scene recenter, bounded worker pressure, and normal exit. Require `meshingBenchmarkEvidenceReady=true`; do not change code or waive A-0154.\n'
if text.count(old2) != 1:
    raise SystemExit('CURRENT_STATE immediate action anchor mismatch')
state.write_text(text.replace(old2, new2, 1))

log = Path('ai/ATTEMPT_LOG.md')
text = log.read_text()
entry = '- **A-0157** — P3.8 dev15 first reference benchmark runtime PARTIAL: bounded collector/accounting, percentiles, real merged workload, ordinary rebuilds, seven recenters, concurrent pressure, inherited P3.7 correctness and clean lifetime all passed, but `benchmarkResourceReloadDelta=0`, so `meshingBenchmarkEvidenceReady=false` correctly blocked promotion. No source/package defect; rerun the exact same dev15 JAR with F3+T after the measured-window arm and READY recovery. See `ai/attempts/A-0157-phase3-p3.8-dev15-runtime-partial-resource-reload.md`.\n'
if 'A-0157' not in text:
    log.write_text(text.rstrip() + '\n' + entry)

print('A-0157 continuity synchronized')
