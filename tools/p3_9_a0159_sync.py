from pathlib import Path

state_path = Path('ai/CURRENT_STATE.md')
roadmap_path = Path('ai/MASTER_ROADMAP.md')
log_path = Path('ai/ATTEMPT_LOG.md')

state = state_path.read_text(encoding='utf-8')
old_top = """- **Active milestone: P3.9 — Partial remeshing (EXPERIMENTAL).**\n- P3.9 source work remains blocked until a new immutable contract freeze is recorded from synchronized P3.8-complete `main`.\n"""
new_top = """- **Active milestone: P3.9 — Partial remeshing (EXPERIMENTAL).**\n- Active branch: `phase3/partial-remeshing`, based on exact synchronized P3.8-complete main `169274b468d2a278d39043938efff19844bec9ba` (Build `33272073819` SUCCESS).\n- Frozen first P3.9 slice: **A-0159 / planned `0.3.0-phase3-dev16`** — shadow-only four fixed Y slices, exact block-local dirty provenance with mandatory full fallback, matched full-section control and pre-frozen benefit/complexity thresholds.\n- Dev16 production GPU emission/install remains unchanged; later partial GPU patching is blocked on successful shadow evidence and a separate contract.\n"""
if old_top not in state:
    raise SystemExit('CURRENT_STATE P3.9 top anchor mismatch')
state = state.replace(old_top, new_top, 1)
old_next = """P3.8 is merged and COMPLETE. From the exact synchronized P3.8-complete `main`, create the P3.9 experimental branch and freeze partial-remeshing in a new immutable attempt before any source change. The experiment must prove benefit against the recorded A-0158 full-section baseline while preserving every inherited correctness/lifetime gate and explicitly accounting for metadata, GPU-allocation and fragmentation complexity.\n\nDo not implement P3.9 before its contract freeze.\n"""
new_next = """A-0159 freezes the first P3.9 experiment before source changes. Implement dev16 in shadow mode only: exact bounded block-local dirty provenance; deterministic four-slice mask/dependency planning; shadow slice meshing/proofs over existing immutable captured truth; unselected-slice under-invalidation fingerprints; matched shadow/full-section timing/work/output telemetry; and the frozen decision gate. Keep production full-section GPU emission/install authoritative and unchanged.\n\nDo not implement partial GPU patching in dev16.\n"""
if old_next not in state:
    raise SystemExit('CURRENT_STATE immediate action anchor mismatch')
state = state.replace(old_next, new_next, 1)
state_path.write_text(state, encoding='utf-8')

roadmap = roadmap_path.read_text(encoding='utf-8')
old_p39 = """#### P3.9 — Partial remeshing — ACTIVE / EXPERIMENTAL\n\nOnly after full-section greedy meshing is stable and measured. Partial slice/subregion rebuilds must prove enough benefit against the recorded A-0158 full-section baseline to justify metadata, scheduling, GPU-allocation and fragmentation complexity. A new immutable contract freeze is mandatory before source changes; fallback to the proven full-section path remains authoritative until the experiment closes.\n"""
new_p39 = """#### P3.9 — Partial remeshing — ACTIVE / EXPERIMENTAL\n\nA-0159 freezes the first experiment as `0.3.0-phase3-dev16`: a **shadow-only fixed four-Y-slice decomposition** over the existing full-section production control. Exact block-local dirty provenance is required; unavailable/global lifecycle causes fall back to full-section. Selected slices are expanded by the proven one-block Y dependency, shadow output must pass permanent source/reference differential checks and prove unselected slices unchanged, and matched per-generation CPU/work/upload ratios are judged against pre-frozen thresholds. Dev16 changes no production GPU geometry/install path. Partial GPU patching is a later P3.9 slice only if this experiment proves sufficient benefit while keeping fixed metadata <=1 KiB/section and bounded geometry inflation.\n"""
if old_p39 not in roadmap:
    raise SystemExit('MASTER_ROADMAP P3.9 anchor mismatch')
roadmap = roadmap.replace(old_p39, new_p39, 1)
roadmap_path.write_text(roadmap, encoding='utf-8')

log = log_path.read_text(encoding='utf-8')
entry = "- **A-0159** — P3.9 dev16 partial-remeshing experiment contract freeze: from exact synchronized P3.8-complete main `169274b468d2a278d39043938efff19844bec9ba` / Build `33272073819`, freeze a non-render-changing shadow experiment with four fixed 4-block Y slices, exact block-local dirty provenance and mandatory full fallback, slice dependency expansion, permanent differential/unselected-slice correctness, matched full-section control, bounded telemetry and pre-frozen CPU/upload/geometry/metadata thresholds. Production GPU patching is explicitly out of scope. See `ai/attempts/A-0159-phase3-p3.9-partial-remesh-contract.md`."
if 'A-0159' not in log:
    if not log.endswith('\n'):
        log += '\n'
    log += entry + '\n'
    log_path.write_text(log, encoding='utf-8')
