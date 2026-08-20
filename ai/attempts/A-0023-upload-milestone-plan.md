# A-0023 - Define the Phase 1 staging/upload milestone

**Date:** 2026-08-20  
**Objective:** Define the next engineering checkpoint after dev2 before implementation begins.  
**Action:** Locked the next milestone around bounded host-visible staging, batched GPU copies, fence-gated ring-space reclamation, backpressure, and a non-visual deterministic upload/copy validation workload.  
**Result:** `SUCCESS` as planning/architecture definition.  
**Intended effect:** Prevent terrain work from starting before upload ownership and reclamation behavior are proven on the real Vulkan backend.  
**Actual effect:** The staging path now has an explicit success criterion in `CURRENT_STATE.md`, an explicit bounded/backpressure rule in `DECISIONS.md`, and implementation constraints in the operating manual.  
**Evidence:** Dev2 runtime success; D-0017; current Phase 1 state documentation.  
**Why:** Large render-distance terrain streaming will generate bursty upload pressure. A renderer optimized for 1%/0.1% lows needs bounded admission and completion-gated reuse before that pressure exists.  
**Side effects / lessons:** The first upload test should remain non-visual and tiny; it is a correctness/lifetime milestone, not a throughput benchmark yet.  
**Next action:** After merging PR #4, inspect exact Minecraft 26.2 mapping/copy APIs on a new branch and implement the smallest bounded staging ring that can prove allocation, copy batching, completion, reclamation, and backpressure semantics.
