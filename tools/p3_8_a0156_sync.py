from pathlib import Path

state = Path('ai/CURRENT_STATE.md')
text = state.read_text()
old = '- Dev15 implementation: **IMPLEMENTED; hosted package CI pending.**\n- Dev15 implementation review checkpoint: **A-0155**.\n'
new = '- Dev15 implementation: **IMPLEMENTED; exact hosted package CI GREEN.**\n- Dev15 implementation review checkpoint: **A-0155**.\n- Dev15 package/runtime handoff: **A-0156**.\n- Exact package validation head: `e6f4b81903ddcdcb859d70a1a01c002a3f550e12`; Build workflow `33270995728` SUCCESS.\n- Canonical dev15 JAR: `Obsidian-0.3.0-phase3-dev15.jar`, 456,609 bytes, SHA-256 `eaad8132665e5f662ac30f5e71abbaff3d604f010e09ffd7aa82379c79a9ed65`.\n- Draft P3.8 PR: **#51**; reference benchmark runtime still required before promotion.\n'
if text.count(old) != 1:
    raise SystemExit('CURRENT_STATE package status anchor mismatch')
text = text.replace(old, new, 1)
old2 = 'Dev15 is implemented under frozen A-0154 and reviewed in A-0155. Obtain hosted exact package CI for the current P3.8 implementation, record the canonical direct dev15 JAR, then run the frozen representative benchmark exercise: settled measured-window arm, multiple ordinary rebuilds with READY recovery, F3+T with READY recovery, actual scene recenter with READY recovery, bounded concurrent worker pressure, and normal exit. Do not promote P3.8 from compilation alone.\n\nDo not implement P3.9 during P3.8.\n'
new2 = 'A-0156 records exact hosted dev15 package authority. Run the canonical direct dev15 JAR through the frozen representative benchmark exercise: wait for settled measured-window arm, perform multiple ordinary rebuilds with READY recovery, F3+T with READY recovery, actual scene recenter with READY recovery, create bounded concurrent worker pressure, wait for the benchmark gate when possible, exit normally, and return the complete log. Do not promote P3.8 from package CI alone.\n\nDo not implement P3.9 during P3.8.\n'
if text.count(old2) != 1:
    raise SystemExit('CURRENT_STATE runtime action anchor mismatch')
state.write_text(text.replace(old2, new2, 1))

log = Path('ai/ATTEMPT_LOG.md')
text = log.read_text()
entry = '- **A-0156** — P3.8 dev15 package/runtime handoff: exact validation head `e6f4b81903ddcdcb859d70a1a01c002a3f550e12` passed hosted Build `33270995728`; canonical direct dev15 JAR is 456,609 bytes, SHA-256 `eaad8132665e5f662ac30f5e71abbaff3d604f010e09ffd7aa82379c79a9ed65`; reference benchmark runtime remains required and PR #51 stays draft. See `ai/attempts/A-0156-phase3-p3.8-dev15-package-runtime-handoff.md`.\n'
if 'A-0156' not in text:
    log.write_text(text.rstrip() + '\n' + entry)

print('A-0156 package handoff continuity synchronized')
