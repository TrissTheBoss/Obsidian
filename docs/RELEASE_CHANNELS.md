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


## Distribution surfaces

GitHub remains Obsidian's canonical build/release authority. Modrinth is a user-facing mirror of the same versioned builds.

| Obsidian maturity | GitHub | Modrinth |
| --- | --- | --- |
| Stable checkpoint | normal Release | `release` |
| Preview with `dev`, `alpha`, or `preview` marker | Prerelease | `alpha` |
| Preview with `beta`, `rc`, or `pre` marker | Prerelease | `beta` |
| CI artifact only | Actions artifact | not published |

The Modrinth upload uses the exact source commit selected by the GitHub release/Preview flow, uploads the runtime JAR as primary and the sources JAR as supplementary, and marks the version as client-only Fabric for the exact Minecraft version from `gradle.properties`.

Automatic mirroring requires:

- repository Actions secret `MODRINTH_PAT`;
- `MODRINTH_PROJECT_ID`, stored as either a repository Actions variable or repository secret.

The PAT is never stored in repository files. The project ID is not sensitive, but secret-backed storage is supported. The manual **Publish Modrinth** workflow can accept a project-ID override for first-time setup or recovery.

The publish workflow checks whether the same Modrinth `version_number` already exists and leaves an existing version untouched instead of silently replacing it. When the authenticated version-list request returns HTTP 200, that lookup provides the duplicate guard. If Modrinth returns HTTP 404 for a hidden/draft project read path, the workflow proceeds to the pinned create-version action and lets VERSION_CREATE authorization decide the write; unexpected statuses other than 200/404 still fail closed. Because the flow is idempotent when the read path is available, an existing GitHub Release/Preview can be invoked again to backfill Modrinth when that GitHub release predates Modrinth integration.

The first end-to-end live mirror was proven by Publish Preview run `36434577563` / #5: `0.4.0-phase4-dev1` was uploaded as Modrinth `alpha` version ID `OhTxTs7Z` from exact Preview source `6af5174f054b272c65174fbf0c37d981a66aff31`.
