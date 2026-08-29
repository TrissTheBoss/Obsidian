from pathlib import Path

state = Path('ai/CURRENT_STATE.md')
text = state.read_text()
old = '- Dev15 implementation: **NOT STARTED**; contract freeze precedes source changes.\n'
new = '- Dev15 implementation: **IMPLEMENTED; hosted package CI pending.**\n- Dev15 implementation review checkpoint: **A-0155**.\n'
if text.count(old) != 1:
    raise SystemExit('CURRENT_STATE dev15 status anchor mismatch')
text = text.replace(old, new, 1)
old2 = 'Implement the frozen A-0154 dev15 measurement slice on `phase3/meshing-benchmarks`: one bounded primitive percentile/window telemetry component, integrate queue-wait/full-ticket sampling at existing boundaries, expose representative workload/scratch/GC/utilization evidence, add `meshingBenchmarkEvidenceReady`, bump to `0.3.0-phase3-dev15`, then open a draft P3.8 PR and require exact Java 25 / Gradle 9.5.1 package CI before runtime handoff.\n\nDo not implement P3.9 during P3.8.\n'
new2 = 'Dev15 is implemented under frozen A-0154 and reviewed in A-0155. Obtain hosted exact package CI for the current P3.8 implementation, record the canonical direct dev15 JAR, then run the frozen representative benchmark exercise: settled measured-window arm, multiple ordinary rebuilds with READY recovery, F3+T with READY recovery, actual scene recenter with READY recovery, bounded concurrent worker pressure, and normal exit. Do not promote P3.8 from compilation alone.\n\nDo not implement P3.9 during P3.8.\n'
if text.count(old2) != 1:
    raise SystemExit('CURRENT_STATE immediate action anchor mismatch')
state.write_text(text.replace(old2, new2, 1))

log = Path('ai/ATTEMPT_LOG.md')
text = log.read_text()
entry = '- **A-0155** — P3.8 dev15 benchmark implementation review: measurement-only full-section worker instrumentation implemented under frozen A-0154; fixed primitive percentile window, workload/output identity, GC/scratch/pressure evidence and layered runtime gate added; hosted package CI and reference runtime remain pending. See `ai/attempts/A-0155-phase3-p3.8-dev15-implementation-review.md`.\n'
if 'A-0155' not in text:
    log.write_text(text.rstrip() + '\n' + entry)

print('A-0155 continuity synchronized')
