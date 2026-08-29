from pathlib import Path

p = Path('ai/MASTER_ROADMAP.md')
s = p.read_text()

old = '''#### P3.5 — Border/halo correctness — ACTIVE

Validate face visibility, light/AO and rebuild invalidation across section boundaries with no worker-thread live-world reads.

P3.5 must begin by freezing an exact correctness/runtime contract against current snapshot/halo capture, cross-section visibility, supported light/AO semantics, neighbor dirty propagation, generation identity, worker inputs and the already-proven dev11 hybrid emission path. Existing greedy eligibility may remain equal or narrow when border proof is insufficient; it must never widen correctness assumptions silently.

#### P3.6 — T-junction policy — PLANNED

Default to greedy topology unless real Vulkan hardware shows cracks. Prefer stable positions and targeted mitigation/splitting over globally abandoning greedy meshing. P3.4 emission work identified and exercised its immediate raster obligations without declaring this broader policy complete.
'''
new = '''#### P3.5 — Border/halo correctness — COMPLETE

Validated as corrected `0.3.0-phase3-dev12.1`; promotion PR #46 merge `1f34b3e4819b4eaa3a8fa474b09570a2e049b15a`.

A-0146 closed the frozen A-0142 contract on the reference RX 6800 XT Vulkan system: all inherited gates through dev11 remained true, `borderHaloCorrectnessEvidenceReady=true`, 248 installed deterministic proofs produced `380,928 / 380,928 / 380,928` outward/binary/reference matches, shared-border comparison was `167,936 / 167,936`, halo-only and vertical dirty dependencies were exercised, worker world reads remained zero, stale installs remained zero, lifetime closed cleanly and process exit was 0. A-0145's cancellation-aware exact residual accounting remains the durable correction for legitimate stage-boundary cancellation.

#### P3.6 — T-junction policy — ACTIVE

A-0147 freezes `0.3.0-phase3-dev13` as a non-geometry-changing evidence slice. The first task is to prove the actual emitted dev10-safe merged topology rather than preemptively split all greedy quads. Detect strict same-facing/coplanar merged/merged T-junctions in exact section-local integer coordinates, prove bounds/lattice/plane identities and camera-relative section transforms, then deliberately visually inspect a runtime that is proven to contain real junctions.

If real detected T-junctions render without cracks/pinholes/flickering seams on the reference Vulkan path, record no baseline mitigation required for the proven path and retain a cross-vendor/scale revisit hook. If artifacts are observed, prefer targeted raster-safe mitigation or selective splitting before any broader topology change. Any geometry-changing mitigation requires a separately frozen slice and renewed explicit visual/runtime validation. D-0024 remains authoritative.
'''
if old not in s:
    raise SystemExit('P3.5/P3.6 roadmap block not found')
s = s.replace(old, new, 1)

s = s.replace('- [ACTIVE] Border/halo visibility, light/AO and rebuild-invalidation correctness.',
              '- [COMPLETE] Border/halo visibility, light/AO and rebuild-invalidation correctness.\n- [ACTIVE] Evidence-driven T-junction topology/raster policy.', 1)

marker = '## 16. Roadmap revision log\n\n'
entry = '''### 2026-08-29 — P3.5 completion and P3.6 activation\n\n- A-0146 closed corrected dev12.1 P3.5 runtime with every frozen gate true, exact border/reference/shared-border agreement, halo-only + vertical dependency exercise, clean lifetime and process exit 0;\n- exact synchronized P3.5 evidence head passed workflow `33262044878`;\n- draft PR #45 was superseded only because the connected ready-for-review mutation still uses invalid `Repository.fullDatabaseId`; non-draft PR #46 promoted the exact same green head as `[no-release]` merge `1f34b3e4819b4eaa3a8fa474b09570a2e049b15a`;\n- marked P3.5 COMPLETE;\n- A-0147 activated P3.6 and froze dev13 as a non-geometry-changing proof of actual emitted strict T-junction topology plus camera-relative transform evidence before deciding whether mitigation is justified;\n- P3.7+ phase order remains unchanged.\n\n'''
if marker not in s:
    raise SystemExit('revision log marker not found')
s = s.replace(marker, marker + entry, 1)

old_pos = '''- **P3.4: COMPLETE through `0.3.0-phase3-dev11`, promotion PR #44.**
- **P3.5: ACTIVE — border/halo correctness.**
- P3.6-P3.9 remain PLANNED/EXPERIMENTAL as marked.
'''
new_pos = '''- **P3.4: COMPLETE through `0.3.0-phase3-dev11`, promotion PR #44.**
- **P3.5: COMPLETE through corrected `0.3.0-phase3-dev12.1`, promotion PR #46.**
- **P3.6: ACTIVE — evidence-driven T-junction policy, dev13 contract frozen in A-0147.**
- P3.7-P3.9 remain PLANNED/EXPERIMENTAL as marked.
'''
if old_pos not in s:
    raise SystemExit('immediate roadmap position block not found')
s = s.replace(old_pos, new_pos, 1)

p.write_text(s)
