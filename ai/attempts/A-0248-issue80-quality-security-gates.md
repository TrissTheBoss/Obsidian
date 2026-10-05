# A-0248 — Issue #80 quality/security gate implementation

**Date:** 2026-10-05  
**Objective:** Address the concrete repository gaps reported in issue #80 without changing renderer ownership or the active P4.4 feature work.  
**Result:** PARTIAL — implementation committed; exact-head hosted CI validation still required.

## Action

- Audited canonical remote state before mutation:
  - default branch: `main`;
  - maintenance base: `4ba408becfcde42456b9d8468106a3e0d27de049`;
  - active engineering PR remains #79.
- Added JUnit Jupiter unit-test infrastructure and focused tests for persistent section/column membership, capacity, slot reuse, identity refresh, conservative frustum classification, and bounded lifecycle overflow/drain behavior.
- Extracted lifecycle-ring and frustum-classifier logic into pure Java helpers used by production validation code.
- Added Checkstyle 14.3.0 with a deliberately conservative high-signal baseline.
- Added explicit CI unit-test and static-analysis steps before packaging.
- Replaced mutable external GitHub Action major tags with immutable full commit SHAs plus release-version comments across affected workflows.
- Preserved the already immutable Modrinth publisher pin and documented its release version inline.

## Revision/audit limitation closure

The original report could not establish Git state because its shell failed. This work uses canonical GitHub repository state and exact commit identities. Obsidian's own policy treats hosted GitHub Actions against the declared dependency set as compile/package authority.

The local shell available to this ChatGPT session cannot resolve github.com, so local clone/build execution is also unavailable. Exact-head hosted CI is therefore the validation path.

A separate state mismatch was observed: `main`'s `ai/CURRENT_STATE.md` still names merged PR #78 as active while #79 is actually open. PR #79 already modifies that file, so this maintenance branch intentionally avoids a conflicting continuity edit and records the discrepancy here.

## Intended effect

- Fast deterministic tests catch regressions in high-value pure renderer logic.
- CI fails on unit-test or Checkstyle regressions.
- Workflow dependencies no longer follow mutable major-version tags.
- Static analysis starts with an enforceable low-noise baseline that can expand deliberately.

## Evidence

- Issue: #80.
- Base `main`: `4ba408becfcde42456b9d8468106a3e0d27de049`.
- Immutable action pins:
  - checkout v4.4.0: `11d5960a326750d5838078e36cf38b85af677262`
  - setup-java v4.9.1: `cf277c60eb25467037889841efdb72551f06f6c3`
  - setup-python v5.6.0: `a26af69be951a213d495a4c3e4e4022e16d87065`
  - upload-artifact v4.6.2: `ea165f8d65b6e75b540449e92b4886f43607fa02`
  - download-artifact v4.3.0: `d3f86a106a0bac45b974a628896c90dbdf5c8093`
  - gradle/actions v4.4.4: `748248ddd2a24f49513d8f472f81c3a07d4d50e1`
  - Modrinth publisher remains `203bc72a51ae39fba1194fc4097d9f0750c49bd3` (v2.5.1).

## Boundaries

No production draw ownership, GPU synchronization, Vulkan ownership, visibility policy, version, or tester-binary semantics are intentionally changed. Hardware/runtime/visual validation remains a separate gate and is not replaced by unit tests.

## Next action

Open the maintenance PR, require exact-head Build CI to pass, diagnose only failed steps if necessary, then close issue #80 with the exact CI evidence.
