# A-0212 - Secure Modrinth publishing integration

**Date:** 2026-09-28  
**Status:** `SUCCESS / INTEGRATED AND HOSTED-CI VALIDATED`  
**Validation head:** `c5c902fa666cba3d60985f7913d7ad15d324ce22`  
**Draft PR:** #67 `Add secure Modrinth publishing mirror`

## Objective

Integrate Modrinth as a user-facing distribution mirror using the GitHub Marketplace **Modrinth Publish** action while preserving GitHub as Obsidian's canonical build/release authority and keeping the Modrinth credential out of repository content.

## External action inspection

The selected Marketplace action is `cloudnode-pro/modrinth-publish`.

Reviewed release:

- Marketplace version: **v2.5.1**;
- exact commit: `203bc72a51ae39fba1194fc4097d9f0750c49bd3`;
- required inputs include token, project, version, loaders and files;
- supports game versions, release channel, changelog, primary file, supplementary file types, environment and version status;
- the upstream action posts the supplied project identifier as Modrinth `project_id` and sends the token only in the HTTP Authorization header.

Obsidian pins the exact commit rather than a floating `@v2` tag.

## Implemented workflow

New reusable/manual workflow:

`.github/workflows/publish-modrinth.yml`

Capabilities:

1. accepts an exact source branch/tag/SHA;
2. requires the Modrinth PAT through GitHub Actions secret `MODRINTH_PAT`;
3. resolves the Modrinth project ID from repository Actions variable `MODRINTH_PROJECT_ID`, with a manual one-run input override;
4. checks out and builds the exact selected source with Java 25 / Gradle 9.5.1;
5. resolves `mod_version` and `minecraft_version` from `gradle.properties`;
6. maps Obsidian release maturity to Modrinth:
   - `dev` / `alpha` / `preview` -> `alpha`;
   - `beta` / `rc` / `pre` -> `beta`;
   - otherwise -> `release`;
7. uploads the runtime JAR as primary and the sources JAR as `sources-jar`;
8. marks versions as Fabric / exact Minecraft version / `client_only`;
9. uses release-specific notes when present, otherwise extracts the matching CHANGELOG section, otherwise emits a bounded fallback changelog;
10. performs an authenticated Modrinth project/version lookup before upload;
11. leaves an existing matching `version_number` unchanged rather than replacing it;
12. writes only non-secret publication metadata to the Actions summary.

## Automatic integration

### Main versioned releases

`.github/workflows/build.yml` now records whether a new immutable GitHub release was actually created.

Only when:

- the GitHub release is new; and
- repository variable `MODRINTH_PROJECT_ID` is configured,

the workflow calls the reusable Modrinth publisher for the exact `github.sha`.

Documentation/continuity commits using `[no-release]` retain their existing behavior and do not publish.

### Preview publication

`.github/workflows/publish-preview.yml` now exports the exact selected source SHA and whether a new GitHub Preview prerelease was created.

Only a newly created Preview and a configured `MODRINTH_PROJECT_ID` trigger the Modrinth mirror.

If the variable is absent, GitHub publication still succeeds and an Actions summary explains that automatic Modrinth mirroring was skipped.

## Security boundary

No PAT value is present in source, workflow YAML, `/ai`, attempts, release notes or tool payloads used for repository mutation.

The workflow references the repository secret by name only.

Rules frozen in D-0031 / Repository Hygiene:

- never print or persist PAT values, prefixes, hashes or authorization headers;
- credential injection occurs only through GitHub Actions runtime secrets;
- the public project ID is a repository variable, not a secret;
- third-party publisher upgrades require a new reviewed/pinned commit;
- Modrinth publication cannot change promotion status or canonical package authority.

## Release policy

D-0031 establishes:

- GitHub CI/releases remain canonical source/package authority;
- Modrinth is a user-facing mirror;
- GitHub Stable/Preview maturity remains the project model;
- Modrinth `release`/`beta`/`alpha` values are only distribution-channel mappings;
- CI-only artifacts are never mirrored.

`docs/RELEASE_CHANNELS.md`, `ai/MASTER_ROADMAP.md`, `ai/REPOSITORY_HYGIENE.md`, `ai/CURRENT_STATE.md`, and the active Context Governor capsule were synchronized.

## Exact hosted validation

Validation head:

`c5c902fa666cba3d60985f7913d7ad15d324ce22`

Hosted checks:

- Context Governor run `36428088013` / **#10** — **SUCCESS**;
- normal Build run `36428088988` / **#779** — **SUCCESS**.

This proves the integration branch compiles/packages normally and the modified active context capsule passes the deterministic Context Governor checks.

## Deliberately unclaimed evidence

No actual Modrinth version was created during this validation.

A real upload requires a valid Modrinth project identifier. Automatic mirroring therefore remains gated on repository Actions variable `MODRINTH_PROJECT_ID`; manual workflow dispatch may instead receive a one-run project ID.

The Modrinth PAT is reported by the maintainer as configured in GitHub repository secrets, but its value is intentionally not retrievable or recorded by repository continuity tooling.

## Renderer/runtime impact

None.

This integration does not change:

- renderer source;
- runtime resources;
- P4.1 behavior;
- P4.1 package authority;
- frozen runtime/visual gates;
- Minecraft/Fabric/Java requirements;
- roadmap phase order.

## Result

Secure, idempotent Modrinth publication is integrated into the GitHub release system and hosted-CI validated without exposing the credential or weakening package/release authority.

## Next action

Synchronize `CURRENT_STATE.md` and `ACTIVE_CONTEXT.json` from integration-validation to hosted-CI-green/publish-pending-project-ID, validate the final evidence-only head, merge PR #67 `[no-release]`, then configure `MODRINTH_PROJECT_ID` and perform the first controlled Modrinth publication.
