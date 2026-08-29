from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"missing anchor: {label}")
    return text.replace(old, new, 1)


p = Path("ai/CURRENT_STATE.md")
text = p.read_text(encoding="utf-8")
old_top = """- **P3.7 — Differential correctness framework: PROMOTION-READY through `0.3.0-phase3-dev14`.**
- Promotion branch: `phase3/differential-correctness`.
- Promotion PR: **#49** (keep draft only until exact synchronized evidence-head CI passes).
- Frozen P3.7 contract: A-0150.
- Implementation/package checkpoint: A-0151.
- First runtime: A-0152 PARTIAL only because required scene recenter was not exercised.
- Successful reference-runtime closure: **A-0153**.
- **Next milestone after merge: P3.8 — Meshing benchmarks.**"""
new_top = """- **P3.7 — Differential correctness framework: COMPLETE through `0.3.0-phase3-dev14`.**
- P3.7 promotion: PR #50, `[no-release]` merge `e1e0c583160bd2a36a2fd42a969bf35e5697591b` from exact validated head `a63dce386cbee215007f127e7ba801dc3218eb91`.
- Frozen P3.7 contract: A-0150.
- Implementation/package checkpoint: A-0151.
- First runtime: A-0152 PARTIAL only because required scene recenter was not exercised.
- Successful reference-runtime closure: A-0153.
- **Active milestone: P3.8 — Meshing benchmarks.**
- P3.9 partial remeshing remains out of scope until P3.8 is measured and closed."""
text = replace_once(text, old_top, new_top, "main top status")
text = replace_once(
    text,
    "## P3.7 — Differential correctness framework — PROMOTION-READY",
    "## P3.7 — Differential correctness framework — COMPLETE",
    "current P3.7 heading",
)
text = text.replace(
    "No new visual verdict was required because dev14 changes no renderer semantics. A-0153 authorizes P3.7 promotion; exact synchronized evidence-head CI and merge remain administrative gates before `COMPLETE` is recorded on `main`.",
    "No new visual verdict was required because dev14 changes no renderer semantics. Exact synchronized evidence head `a63dce386cbee215007f127e7ba801dc3218eb91` passed hosted Build workflow `33265069030`, and non-draft workaround PR #50 merged it `[no-release]` as `e1e0c583160bd2a36a2fd42a969bf35e5697591b` after the known ready-for-review connector failure on draft PR #49.",
    1,
)
old_next = """## Immediate next action

Require hosted Java 25 / Gradle 9.5.1 Build success on the exact synchronized P3.7 evidence head, then promote and merge PR #49 `[no-release]` without source/evidence drift. After merge, synchronize `main` to P3.7 COMPLETE / P3.8 ACTIVE, create the P3.8 feature branch from that synchronized `main`, and freeze the meshing-benchmark/representative-workload contract in a new immutable attempt before implementation.

Do not consume P3.9 partial-remeshing scope during P3.8."""
new_next = """## Immediate next action

Create the P3.8 feature branch from this synchronized `main`, then freeze the meshing-benchmark/representative-workload contract in a new immutable attempt before any implementation change. The benchmark slice must measure real worker meshing cost and tail percentiles under representative immutable snapshots/workload churn without consuming P3.9 partial-remeshing scope.

Do not implement P3.9 during P3.8."""
text = replace_once(text, old_next, new_next, "main immediate next")
p.write_text(text, encoding="utf-8")

p = Path("ai/MASTER_ROADMAP.md")
road = p.read_text(encoding="utf-8")
road = replace_once(
    road,
    "#### P3.7 — Differential correctness framework — PROMOTION-READY",
    "#### P3.7 — Differential correctness framework — COMPLETE",
    "roadmap P3.7 status",
)
road = replace_once(
    road,
    "Exact evidence-head CI and merge are the remaining administrative promotion steps.",
    "Exact evidence head `a63dce386cbee215007f127e7ba801dc3218eb91` passed hosted Build workflow `33265069030`; promotion PR #50 merged `[no-release]` as `e1e0c583160bd2a36a2fd42a969bf35e5697591b`. P3.7 is complete.",
    "roadmap P3.7 closure",
)
road = replace_once(
    road,
    "#### P3.8 — Meshing benchmarks — NEXT",
    "#### P3.8 — Meshing benchmarks — ACTIVE",
    "roadmap P3.8 status",
)
road = road.replace(
    "- [PROMOTION-READY] Differential correctness framework against the permanent independent reference oracle.",
    "- [COMPLETE] Differential correctness framework against the permanent independent reference oracle.",
    1,
)
p.write_text(road, encoding="utf-8")
