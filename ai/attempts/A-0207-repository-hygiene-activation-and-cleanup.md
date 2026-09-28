# A-0207 - Repository hygiene activation, branch cleanup and Preview publication

**Date:** 2026-09-28  
**Status:** `SUCCESS` / **REPOSITORY HYGIENE SYSTEM ACTIVE**  
**Base maintenance merge:** `e4f00867ea2df5b70eb21e2f0003ad36950fb9d8`

## Objective

Close A-0206 by proving the new repository-hygiene, public README and Preview release system on the real repository rather than leaving it as unvalidated process text.

## Maintenance promotion

- PR #58 `Repository hygiene, README and preview release system` exact final head: `1044a8846a5e602665e717e7cbeea63de47b4e2e`;
- hosted Build #762 / run `36400318760`: **SUCCESS** on that exact head;
- squash merge used explicit `[no-release]` title, so process/docs/automation maintenance did not publish a renderer release;
- merge commit: `e4f00867ea2df5b70eb21e2f0003ad36950fb9d8`;
- post-merge Build #763 / run `36400552489`: **SUCCESS**.

An earlier maintenance Build #760 failed only at artifact upload because an escaped GitHub expression produced an invalid artifact name containing a backslash. The Gradle build itself passed. The workflow syntax was corrected before the final exact-head Build #762; no renderer source was involved.

## Real branch cleanup proof

Pre-policy audit: **56 branches**.

Repository Hygiene run `36400552449` / #1 on the maintenance merge: **SUCCESS**.

After the sweep completed, the repository had exactly **2 branches**:

- `main`;
- `phase4/p4.1-persistent-scene-visibility` — preserved because PR #57 is open.

This proves the guarded cleanup preserved the default branch and active PR head while removing merged/closed/legacy stale branches. The normal `<=5` branch target is satisfied.

## Public surface proof

`main` now contains:

- refreshed Phase 4 root `README.md` with compact Obsidian ASCII identity, badges, maturity warning, current tester status, compatibility, install/test instructions, architecture, roadmap and contributor links;
- `ai/REPOSITORY_HYGIENE.md` as mandatory branch/README/release governance;
- `docs/RELEASE_CHANNELS.md` explaining Stable / Preview / CI channels;
- `CONTRIBUTING.md` with concise branch/validation/documentation rules;
- D-0028 and synchronized Phase 4 roadmap/current-state continuity;
- A-0202 through A-0206 on the default-branch continuity lineage.

## Preview release proof

An audited `/publish-preview` command was posted on active PR #57 after the workflow reached `main`.

Publish Preview run `36400684893` / #1: **SUCCESS**.

Published GitHub Prerelease:

- tag: `v0.4.0-phase4-dev1`;
- title: `Obsidian 0.4.0-phase4-dev1 - Preview`;
- prerelease: `true`;
- draft: `false`;
- exact release target: PR #57 continuity head `6af5174f054b272c65174fbf0c37d981a66aff31`;
- runtime JAR: `Obsidian-0.4.0-phase4-dev1.jar`, 493,355 bytes, SHA-256 `8bf25f3aa6bfe6fb4392044ba0973692fc6a24fd9948f3fea51cf55b12e157f3`;
- sources JAR: 255,354 bytes, SHA-256 `55b9a7ce230db01b74c38d58023f91739ec74a0262344b4d6b40eaab4c17e03d`;
- `SHA256SUMS`: SHA-256 `ed83ccb0f9f96a76af74c2c23058ce4beca0ad7a28c56e2b1f8fea5b965b0101`.

The release target differs from the original A-0205 package-authority commit only by `ai/CURRENT_STATE.md` and A-0205 itself; no renderer/source/resource file changed. The rebuilt release JAR is therefore a new CI-produced binary from an unchanged renderer tree, but its byte hash is distinct from the original A-0205 Actions artifact. Keep both identities explicit rather than treating the hashes as interchangeable.

Original A-0205 canonical Actions-handoff binary remains:

- exact package source: `fd58b9f2e915462f665b7d85f5d993456d5f930e`;
- JAR size 493,377 bytes;
- SHA-256 `39c4bb4932bd6e7c00a4190c3514ef29eb926c337bba488f9a04bbef27120458`.

For future tester discovery, the GitHub Preview prerelease is the preferred public download surface. Runtime promotion evidence must still identify the exact binary actually tested.

## Result

A-0206 is closed successfully. Repository hygiene is no longer an aspirational rule: branch cleanup, post-merge CI, public README synchronization and Preview prerelease publication all executed successfully on GitHub.

P4.1 itself is **not promoted** by this repository-maintenance work. PR #57 remains draft and the frozen P4.1 reference runtime + human visual test is still required.

## Next action

Merge this evidence synchronization `[no-release]`, allow Repository Hygiene to delete this short-lived maintenance branch, verify the repository returns to `main` + the active P4.1 branch, then resume the exact P4.1 dev1 reference runtime handoff.
