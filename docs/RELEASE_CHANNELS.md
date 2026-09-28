# Obsidian Release Channels

Obsidian is under active renderer development. GitHub releases are split into clear channels so testers can find builds without confusing experimental binaries with validated checkpoints.

| Channel | GitHub type | Intended audience | Meaning |
| --- | --- | --- | --- |
| **Stable checkpoint** | Release | users/testers wanting the most validated public build | Passed the milestone's required validation. The project may still be experimental overall. |
| **Preview** | Prerelease | active testers | CI-green test build. May be incomplete or not production ready. |
| **CI artifact** | Actions artifact | maintainers / short-lived debugging | Ephemeral build evidence; not the preferred long-term tester download. |

## Preview builds

Preview releases use versions containing markers such as `dev`, `alpha`, `beta`, `rc`, or `preview`.

A Preview release should always identify:

- exact source commit;
- target Minecraft, Fabric Loader and Java versions;
- Vulkan/backend requirement;
- test purpose;
- known limitations;
- whether visual behavior should differ from the previous validated baseline;
- SHA-256 checksums.

> [!WARNING]
> A GitHub **Prerelease** is a test build. Unless the release explicitly says otherwise, assume it is **not production ready** and keep a known-good Minecraft instance/profile available.

## Stable checkpoints

Stable checkpoints are normal GitHub Releases and are eligible to be the repository's `latest` release.

For Obsidian, "stable checkpoint" means **validated relative to its frozen milestone contract**. It does not imply the entire renderer has reached a final 1.0 production state.

## Cadence

During active development, each milestone that reaches real-machine testing should publish at least one Preview prerelease. Meaningful corrected JARs should receive a new preview version rather than silently replacing an existing tag.

Documentation-only, evidence-only, and repository-maintenance changes use `[no-release]`.

See `ai/REPOSITORY_HYGIENE.md` for the authoritative maintainer rules.
