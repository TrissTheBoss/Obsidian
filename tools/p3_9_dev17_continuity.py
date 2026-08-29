from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

current = ROOT / "ai/CURRENT_STATE.md"
text = current.read_text(encoding="utf-8")
old = """- Frozen first P3.9 slice: **A-0159 / `0.3.0-phase3-dev16`** — shadow-only four fixed Y slices, exact block-local dirty provenance with mandatory full fallback, matched full-section control and pre-frozen benefit/complexity thresholds.\n- Dev16 implementation/package checkpoint: **A-0160 SUCCESS for implementation/package; reference runtime required.**\n- Exact clean package-validation head `9b5930a24c8bd1841c474a03f67407231e11bc65` passed hosted Build `33273077105`; canonical dev16 JAR is `Obsidian-0.3.0-phase3-dev16.jar`, 490,250 bytes, SHA-256 `b14640ab1a397561371564e6b3c38b93b105e481be6a32b8172b8448de701ffd`.\n- Draft P3.9 PR: **#53**; keep draft until the exact A-0159 reference runtime proves or rejects the frozen experiment thresholds.\n- Dev16 production GPU emission/install remains unchanged; later partial GPU patching is blocked on successful shadow evidence and a separate contract.\n"""
new = """- Frozen first P3.9 slice: **A-0159** — shadow-only four fixed Y slices, exact block-local dirty provenance with mandatory full fallback, matched full-section control and pre-frozen benefit/complexity thresholds; these thresholds remain unchanged.\n- Dev16 implementation/package checkpoint: **A-0160 SUCCESS** for implementation/package.\n- Dev16 reference runtime: **A-0161 FAILED** under frozen A-0159: only 6 localized episodes / 150 fallbacks and one shadow correctness failure; all inherited production/P3.7/P3.8/lifetime gates remained green.\n- Dev17 diagnostic/correction contract: **A-0162 SUCCESS / PLAN FROZEN**. It permits only bounded diagnostics plus correction of source defects proven against permanent P3.7 semantics; provenance surface, admission policy, four-slice layout and every A-0159 threshold are unchanged.\n- Dev17 implementation/package checkpoint: **A-0163 SUCCESS for implementation/package; reference runtime required.** Two dev16 shadow-only correctness defects are corrected: bidirectional visibility/reference equality stronger than permanent P3.7, and raw Minecraft `Direction.ordinal()` vs Obsidian binary-direction comparison.\n- Exact corrected package-validation head `bce641ff08353035d6012fb5c5f5d8c06918da41` / tree `1d6c9a17f089c25f6d70ad9706ba626b2c98eae4` passed hosted Build `33274284466`; canonical dev17 JAR is `Obsidian-0.3.0-phase3-dev17.jar`, 495,236 bytes, SHA-256 `4f8d58251f29742afbc67d95e33a884ea72849fe099a225b154af19616ef7904`.\n- Dev17 final closure now retains exact per-reason fallback counts and the first failed episode fixture (section/episode/mask/edit-count/code/name/index/determinism).\n- Draft P3.9 PR: **#53**; keep draft until the exact dev17 reference runtime closes or rejects A-0159.\n- Dev17 production full-section capture/worker/GPU emission/install/draw remains unchanged; later partial GPU patching is blocked on successful shadow evidence and a separate freeze.\n"""
if old not in text:
    raise RuntimeError("CURRENT_STATE P3.9 handoff block not found exactly")
current.write_text(text.replace(old, new, 1), encoding="utf-8")

attempt = ROOT / "ai/ATTEMPT_LOG.md"
log = attempt.read_text(encoding="utf-8")
append = []
if "## A-0161 -" not in log:
    append.append("""
---

## A-0161 - P3.9 dev16 reference runtime failed frozen shadow gate

**Date:** 2026-08-29  
**Objective:** Evaluate the exact canonical dev16 shadow partial-remeshing package against frozen A-0159.  
**Action:** User ran the coherent reference workload with ordinary edits, F3+T, real scene recenter activity and normal exit.  
**Result:** `FAILED`.  
**Actual effect:** All inherited production/P3.7/P3.8/lifetime gates remained green, but dev16 completed only 6 localized episodes with 150 fallbacks and recorded 5/6 exact shadow episodes with one mandatory correctness failure. CPU/upload benefit evidence was also insufficient/poor, but evidence volume was too low to reject the four-slice strategy solely on ratios.  
**Evidence:** `ai/attempts/A-0161-phase3-p3.9-dev16-reference-runtime-failed.md`; canonical dev16 SHA-256 `b14640ab1a397561371564e6b3c38b93b105e481be6a32b8172b8448de701ffd`.  
**Lesson:** Do not rerun unchanged dev16. Retain exact first-failure and per-fallback diagnostics, and correct only source defects proven against permanent P3.7 semantics.  
**Next action:** A-0162 dev17 diagnostic/correction freeze.
""")
if "## A-0162 -" not in log:
    append.append("""
---

## A-0162 - P3.9 dev17 diagnostic/correction contract freeze

**Date:** 2026-08-29  
**Objective:** Freeze a bounded correction/diagnostic slice before changing dev16 source.  
**Action:** Proved dev16's selected-slice visibility/reference equality was stronger than permanent P3.7 semantics; froze dev17 to restore P3.7 semantics and add bounded fallback/first-failure diagnostics while leaving four slices, provenance, admission and every A-0159 threshold unchanged.  
**Result:** `SUCCESS` / `PLAN FROZEN`.  
**Evidence:** `ai/attempts/A-0162-phase3-p3.9-dev17-diagnostic-correction-contract.md`.  
**Next action:** Implement only the frozen corrections, exact hosted package CI, then reference runtime.
""")
if "## A-0163 -" not in log:
    append.append("""
---

## A-0163 - P3.9 dev17 diagnostic/correction implementation and package

**Date:** 2026-08-29  
**Objective:** Produce the exact dev17 runtime package under A-0162 without changing production rendering or frozen A-0159 thresholds.  
**Action:** Restored permanent P3.7 selected-reference semantics, corrected Minecraft-direction to binary-direction comparison, added exact per-fallback counters and a bounded first-failure fixture, retained unchanged admission/production paths, and validated the exact package in hosted CI.  
**Result:** `SUCCESS` for implementation/package; reference runtime required.  
**Evidence:** `ai/attempts/A-0163-phase3-p3.9-dev17-diagnostic-correction-package.md`; exact validation head `bce641ff08353035d6012fb5c5f5d8c06918da41`, Build `33274284466`, artifact `9721025599`; canonical dev17 JAR 495,236 bytes, SHA-256 `4f8d58251f29742afbc67d95e33a884ea72849fe099a225b154af19616ef7904`.  
**Next action:** Run the exact dev17 JAR through the unchanged A-0159 workload; use the new diagnostics for any remaining correctness/admission failure rather than retuning thresholds.
""")
if append:
    attempt.write_text(log.rstrip() + "\n" + "".join(append).lstrip() + "\n", encoding="utf-8")

print("dev17 continuity synchronized")
