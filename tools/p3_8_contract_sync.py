from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"missing anchor: {label}")
    return text.replace(old, new, 1)


p = Path("ai/CURRENT_STATE.md")
text = p.read_text(encoding="utf-8")
old_top = """- **Active milestone: P3.8 — Meshing benchmarks.**
- P3.9 partial remeshing remains out of scope until P3.8 is measured and closed."""
new_top = """- **Active milestone: P3.8 — Meshing benchmarks.**
- Active branch: `phase3/meshing-benchmarks`.
- Frozen first P3.8 slice: **A-0154 / planned `0.3.0-phase3-dev15`**.
- Dev15 implementation: **NOT STARTED**; contract freeze precedes source changes.
- P3.9 partial remeshing remains out of scope until P3.8 is measured and closed."""
text = replace_once(text, old_top, new_top, "P3.8 top status")
text = replace_once(
    text,
    "P3.8 meshing benchmarks and P3.9 partial remeshing remain out of scope.",
    "P3.7 closure remains authoritative. P3.9 partial remeshing remains out of scope while P3.8 is active.",
    "stale scope line",
)
anchor = "## Durable foundation that remains authoritative"
section = """## ACTIVE: P3.8 — Meshing benchmarks

A-0154 freezes the first P3.8 slice as a non-render-changing measurement baseline for the actual full-section production worker path. Planned version: `0.3.0-phase3-dev15`.

Source truth before implementation:

- `SectionMeshWorkerPool.Ticket` already records enqueue/start/end timestamps and exposes queue wait plus full execution time;
- the pool already retains totals/maxima for queue wait/execution, per-stage build time, output bytes, queue pressure, priority, steals/cancellations and reusable scratch high-water;
- the missing baseline capability is bounded distribution/percentile evidence plus explicit workload/window identity, not a second benchmark-only mesher;
- P3.7 differential correctness remains part of the measured production cost and must stay green during benchmarking.

Frozen dev15 requirements:

- bounded primitive P50/P95/P99/max queue-wait and full-ticket execution telemetry;
- labeled warm-up versus measured benchmark windows armed only after settled READY;
- representative ordinary rebuild, resource reload and real scene-recenter/traversal activity;
- workload identity including source quads/reference faces/rectangles/merge candidates/passthrough+merged identities/faces saved/output vertex+index bytes;
- scratch high-water, GC deltas where portable, worker utilization/queue pressure and exact retained/overflow sample accounting;
- deterministic synthetic collector self-test without injecting fake runtime evidence;
- every inherited gate through P3.7 remains mandatory;
- no performance pass/fail threshold is invented before the first trustworthy baseline exists;
- no P3.9 partial remeshing, merge-policy tuning, worker-count tuning, graphics change or benchmark-only simplification.

A valid but slower-than-desired baseline is still valid P3.8 evidence; future optimization targets must be derived from recorded measurements rather than changing the workload after seeing results.

""" + anchor
text = replace_once(text, anchor, section, "P3.8 section insertion")
old_next = """## Immediate next action

Create the P3.8 feature branch from this synchronized `main`, then freeze the meshing-benchmark/representative-workload contract in a new immutable attempt before any implementation change. The benchmark slice must measure real worker meshing cost and tail percentiles under representative immutable snapshots/workload churn without consuming P3.9 partial-remeshing scope.

Do not implement P3.9 during P3.8."""
new_next = """## Immediate next action

Implement the frozen A-0154 dev15 measurement slice on `phase3/meshing-benchmarks`: one bounded primitive percentile/window telemetry component, integrate queue-wait/full-ticket sampling at existing boundaries, expose representative workload/scratch/GC/utilization evidence, add `meshingBenchmarkEvidenceReady`, bump to `0.3.0-phase3-dev15`, then open a draft P3.8 PR and require exact Java 25 / Gradle 9.5.1 package CI before runtime handoff.

Do not implement P3.9 during P3.8."""
text = replace_once(text, old_next, new_next, "P3.8 immediate next")
p.write_text(text, encoding="utf-8")

p = Path("ai/ATTEMPT_LOG.md")
log = p.read_text(encoding="utf-8")
if "## A-0154 - P3.8 dev15 meshing benchmark contract freeze" not in log:
    log = log.rstrip() + """

---

## A-0154 - P3.8 dev15 meshing benchmark contract freeze

**Date:** 2026-08-29  
**Objective:** Freeze the first P3.8 representative full-section meshing benchmark slice before source changes.  
**Action:** Inspected existing production worker timing/pressure/scratch telemetry and defined a bounded percentile/window contract for queue wait, full-ticket execution, workload identity, output bytes, scratch/GC/utilization evidence and representative lifecycle exercise.  
**Result:** `SUCCESS` / `PLAN FROZEN`.  
**Actual effect:** P3.8 dev15 is constrained to measurement-only instrumentation over the real production path; no numerical pass threshold is invented before baseline capture, all P3.7 correctness/lifetime gates remain mandatory, and P3.9 partial remeshing is explicitly excluded.  
**Evidence:** `ai/attempts/A-0154-phase3-p3.8-meshing-benchmark-contract.md`.  
**Next action:** Implement bounded dev15 telemetry, exact package CI, then run the representative benchmark contract.
""" + "\n"
    p.write_text(log, encoding="utf-8")
