from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"missing anchor: {label}")
    return text.replace(old, new, 1)


# CURRENT_STATE
p = Path("ai/CURRENT_STATE.md")
text = p.read_text(encoding="utf-8")
old_top = """- **Active milestone: P3.7 — Differential correctness framework.**
- Active branch: `phase3/differential-correctness`.
- Active draft PR: **#49**.
- Frozen first P3.7 slice: A-0150.
- Current implementation/package checkpoint: **A-0151 / `0.3.0-phase3-dev14`**.
- Reference runtime differential gate: **REQUIRED before promotion**."""
new_top = """- **P3.7 — Differential correctness framework: PROMOTION-READY through `0.3.0-phase3-dev14`.**
- Promotion branch: `phase3/differential-correctness`.
- Promotion PR: **#49** (keep draft only until exact synchronized evidence-head CI passes).
- Frozen P3.7 contract: A-0150.
- Implementation/package checkpoint: A-0151.
- First runtime: A-0152 PARTIAL only because required scene recenter was not exercised.
- Successful reference-runtime closure: **A-0153**.
- **Next milestone after merge: P3.8 — Meshing benchmarks.**"""
text = replace_once(text, old_top, new_top, "current top status")
text = replace_once(
    text,
    "## ACTIVE: P3.7 — Differential correctness framework",
    "## P3.7 — Differential correctness framework — PROMOTION-READY",
    "current P3.7 heading",
)
anchor = "### Required dev14 reference runtime gate\n\nP3.7 is **not promotion-ready** until the reference run closes the frozen A-0150 contract."
closure = """### A-0152 first reference runtime — PARTIAL, no code defect

The first dev14 reference run passed the complete automated differential/lifetime gate but correctly remained PARTIAL because `cameraRecenterEvents=0` and `scene-recenter` was absent. A-0150 required actual section-boundary/recenter movement followed by READY, so no waiver was taken and no source/package change was made.

### A-0153 reference runtime closure — SUCCESS

The exact same canonical dev14 JAR was rerun and closes the complete frozen contract:

- all inherited gates through P3.6 plus `differentialCorrectnessEvidenceReady=true`;
- proof records/determinism `238 / 238`;
- reference faces `79,754`, mapped/unmapped/ambiguous `59,933 / 0 / 19,821`;
- source baked quads `165,638`;
- passthrough identities `154,306`;
- real merged candidates / expanded merged source faces `4,964 / 11,332`;
- material/direction/canonical-geometry checks exact `11,332 / 11,332` each;
- UV/ARGB/light checks exact `45,328 / 45,328` each;
- missing/duplicate/optimized-without-reference/real-mismatch all `0`;
- fixture self-tests `238 / 238`;
- `cameraRecenterEvents=5` and `scene-recenter` observed, with READY after recenter activity;
- resource reload events `2` and ordinary dirty/rebuild activity present;
- scene workers `244 / 244`, no cancellation/failure/rejection/join failure;
- unsafe stale installs and dropped lifecycle evidence `0`;
- staging `22,315,152 / 22,315,152` bytes reclaimed;
- arena allocations/retired/reclaimed `714 / 714 / 714`, used bytes `0`;
- resources retired/released `238 / 238`, pending `0`;
- process exit code `0`;
- differential geometry/shader/pipeline change flags remain `false`.

No new visual verdict was required because dev14 changes no renderer semantics. A-0153 authorizes P3.7 promotion; exact synchronized evidence-head CI and merge remain administrative gates before `COMPLETE` is recorded on `main`.

### Required dev14 reference runtime gate — CLOSED by A-0153

A-0153 satisfies the frozen A-0150 contract. The required final evidence included:"""
text = replace_once(text, anchor, closure, "runtime closure anchor")
text = text.replace(
    "The run must contain actual merged candidates/covered faces. Exercise initial READY, an ordinary block break/place rebuild and READY, F3+T/resource reload and READY, section-boundary movement/recenter and READY, then exit normally.",
    "A-0153 contained actual merged candidates/covered faces and exercised initial READY, ordinary block rebuild/READY, F3+T/resource reload/READY, section-boundary scene recenter/READY, then normal exit.",
    1,
)
text = text.replace(
    "If a real mismatch appears, do not weaken the independent/captured oracle or reinterpret the promotion gate. Preserve the deterministic fixture, record a new immutable attempt, classify the exact disagreement and make only the narrow correction.",
    "Future differential regressions must not weaken the independent/captured oracle. Preserve the deterministic fixture, record a new immutable attempt, classify the exact disagreement and make only the narrow correction.",
    1,
)
old_next = """## Immediate next action

Run the canonical `Obsidian-0.3.0-phase3-dev14.jar` on the reference Windows 11 / RX 6800 XT Vulkan system and complete the frozen A-0150 runtime exercise. Return the complete log. No new visual verdict is required unless an unexpected visual/rendering change is observed.

Keep PR #49 draft and P3.7 active until the automated reference-runtime evidence closes. Do not start P3.8 or P3.9."""
new_next = """## Immediate next action

Require hosted Java 25 / Gradle 9.5.1 Build success on the exact synchronized P3.7 evidence head, then promote and merge PR #49 `[no-release]` without source/evidence drift. After merge, synchronize `main` to P3.7 COMPLETE / P3.8 ACTIVE, create the P3.8 feature branch from that synchronized `main`, and freeze the meshing-benchmark/representative-workload contract in a new immutable attempt before implementation.

Do not consume P3.9 partial-remeshing scope during P3.8."""
text = replace_once(text, old_next, new_next, "current immediate next")
p.write_text(text, encoding="utf-8")


# MASTER_ROADMAP
p = Path("ai/MASTER_ROADMAP.md")
road = p.read_text(encoding="utf-8")
road = replace_once(
    road,
    "#### P3.7 — Differential correctness framework — ACTIVE",
    "#### P3.7 — Differential correctness framework — PROMOTION-READY",
    "roadmap P3.7 heading",
)
road = replace_once(
    road,
    "Run reference and optimized meshers on representative snapshots; expand greedy rectangles conceptually to covered faces; compare coverage/material/light/AO truth; preserve failing fixtures. Optimized output never becomes its own oracle.\n\n#### P3.8 — Meshing benchmarks — PLANNED",
    "Run reference and optimized meshers on representative snapshots; expand greedy rectangles conceptually to covered faces; compare coverage/material/light/AO truth; preserve failing fixtures. Optimized output never becomes its own oracle.\n\nA-0153 closes the frozen A-0150 dev14 runtime contract on the reference RX 6800 XT Vulkan path. The permanent differential proof reconstructed `165,638` frozen source quads across `238` installed records with `238/238` deterministic audits, real merged coverage (`4,964` candidates / `11,332` expanded source faces), exact material/direction/geometry/UV/ARGB/light matches, zero missing/duplicate/optimized-without-reference/real-mismatch counts, `238/238` fixture self-tests, five scene recenters with READY afterward, clean bounded lifetime and exit code 0. No renderer-semantic change or new visual verdict was required. Exact evidence-head CI and merge are the remaining administrative promotion steps.\n\n#### P3.8 — Meshing benchmarks — NEXT",
    "roadmap P3.7/P3.8 body",
)
road = road.replace(
    "- [ACTIVE] Differential correctness framework against the permanent independent reference oracle.",
    "- [PROMOTION-READY] Differential correctness framework against the permanent independent reference oracle.",
    1,
)
p.write_text(road, encoding="utf-8")


# ATTEMPT_LOG append-only index
p = Path("ai/ATTEMPT_LOG.md")
log = p.read_text(encoding="utf-8")
append = ""
if "## A-0152 - P3.7 dev14 reference runtime PARTIAL" not in log:
    append += """

---

## A-0152 - P3.7 dev14 reference runtime PARTIAL: recenter exercise missing

**Date:** 2026-08-29  
**Objective:** Evaluate the first dev14 reference runtime against the complete A-0150 contract.  
**Action:** Ran the canonical dev14 package through the differential/lifetime gate and requested lifecycle exercise.  
**Result:** `PARTIAL`.  
**Actual effect:** Automated differential/lifetime evidence passed exactly, but `cameraRecenterEvents=0` and `scene-recenter` was absent, so the frozen boundary/recenter exercise was not closed.  
**Evidence:** `ai/attempts/A-0152-phase3-p3.7-dev14-runtime-partial-recenter.md`.  
**Next action:** Rerun the exact same package with real scene recenter and READY afterward; do not change code or waive the contract.
"""
if "## A-0153 - P3.7 dev14 reference runtime promotion" not in log:
    append += """

---

## A-0153 - P3.7 dev14 reference runtime promotion

**Date:** 2026-08-29  
**Objective:** Close the remaining P3.7 runtime obligation with the exact same dev14 package.  
**Action:** Re-ran dev14 through ordinary rebuild, resource reload and real async scene recenter activity, then exited normally.  
**Result:** `SUCCESS`.  
**Actual effect:** `differentialCorrectnessEvidenceReady=true`; `238/238` deterministic installed proofs; real merged coverage; exact material/direction/geometry/UV/ARGB/light comparisons; zero coverage/reference/mismatch defects; five scene recenters with READY afterward; clean worker/staging/arena/resource lifetime; exit code 0.  
**Evidence:** `ai/attempts/A-0153-phase3-p3.7-dev14-runtime-promotion.md`; canonical dev14 SHA-256 `9d79b1de179768d5b872178564f708b42dab0d9cc8e99a0dd8f80bf10336bc39`.  
**Next action:** Require exact synchronized evidence-head CI, merge P3.7 `[no-release]`, then activate/freeze P3.8 benchmarks before implementation.
"""
if append:
    p.write_text(log.rstrip() + append + "\n", encoding="utf-8")
