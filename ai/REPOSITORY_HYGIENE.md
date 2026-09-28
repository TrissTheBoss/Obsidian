# Obsidian Repository Hygiene and Public Surface Policy

This file defines how Obsidian keeps its GitHub repository clean, current, understandable, and useful to both humans and AI agents.

It is part of the mandatory AI operating system. Engineering safety rules still live in `OPERATING_MANUAL.md`; this file is authoritative for branch lifecycle, repository presentation, README/TIMELINE freshness, and release-channel hygiene.

## Repository shape

The normal repository should have:

- one permanent branch: `main`;
- one branch per active pull request;
- only exceptional short-lived inspection or maintenance branches;
- tags/releases for published checkpoints;
- `ai/attempts/` for immutable engineering history.

**Branches are working state, not archives.** A completed or rejected experiment is preserved by its PR, exact commit SHA, attempt record, decision/roadmap record when relevant, and release/tag when a binary was published. It does not need a permanent branch.

### Branch-count service level

Normal target: **5 or fewer branches total**.

If the repository exceeds 5 branches at handoff time, the agent must either clean it up or explain the temporary exception in `CURRENT_STATE.md`.

## Branch lifecycle

### Permanent

- `main` is the only permanent branch.
- Do not create permanent phase archives such as `phase2-final` or `phase3-old`.

### Active engineering

Use descriptive names such as:

- `phase4/p4.2-gpu-draw-compaction`
- `fix/<short-problem>`
- `maintenance/<short-purpose>`
- `docs/<short-purpose>`

An engineering branch expected to live beyond one work session should have an open PR. The PR is the visible home for status, scope, and review.

### Temporary inspection

Use `inspect/<topic>` for exact API/bytecode/tooling inspection.

Rules:

1. record the result in an immutable attempt;
2. remove temporary workflows/files;
3. close the inspection PR if one exists;
4. delete the branch within the same handoff, normally within 24 hours.

### Completion

When a PR is merged or intentionally closed:

1. ensure meaningful evidence is preserved in `ai/attempts/`;
2. synchronize `CURRENT_STATE.md`, roadmap, decisions, and release notes as required;
3. delete the head branch the same day;
4. never keep a branch merely because it contains interesting history.

The repository-hygiene workflow automatically deletes same-repository PR heads after close/merge and performs a conservative stale-branch sweep on `main`.

### Safety exclusions

Never automatically delete:

- `main`;
- a protected branch;
- the head of an open PR.

A branch with unique work and no PR must not be deleted until its useful result is either merged, recorded as an immutable attempt/decision, or explicitly classified as disposable diagnostic work.

## Handoff hygiene checklist

Every handoff must check:

- active PR status matches `CURRENT_STATE.md`;
- temporary inspection/debug workflows are gone;
- completed/closed PR branches are gone or queued for automatic deletion;
- branch count is at or moving toward the <=5 target;
- README top-level status is current;
- `TIMELINE.md` reflects material completed/active/rejected/deferred milestones and the real current phase position;
- the correct public release channel exists for the latest testable binary;
- exact package authority remains recorded in `CURRENT_STATE.md` and the relevant attempt.

## README maintenance contract

The root `README.md` is the public landing page, not a historical log. It must remain accurate and attractive.

### Update the README in the same coherent change when any of these change

- active product phase or milestone;
- supported Minecraft/Fabric/Java/backend requirements;
- installation/test instructions;
- stable or preview release channel;
- major user-visible capability or limitation;
- public architecture summary;
- contributor workflow;
- project maturity/warning level.

### Required README structure

Keep the top of the README useful without scrolling through internal history:

1. compact visual identity / project name;
2. one-sentence product description;
3. build/status badges;
4. prominent maturity warning;
5. current phase and release channels;
6. compatibility requirements;
7. quick-start/test instructions;
8. concise architecture and goals;
9. roadmap/development links;
10. contributing and continuity links.

Rules:

- Keep detailed attempt IDs, benchmark dumps, and long historical explanations out of the public README.
- Link to `CURRENT_STATE.md`, `MASTER_ROADMAP.md`, and release-channel docs instead.
- Do not claim a feature is production-ready because it exists on an experimental branch.
- Do not leave a stale phase/version at the top of the README after a promotion or test-package handoff.
- Decoration is welcome when it remains readable in light/dark GitHub themes and does not hide status/warnings.
- Prefer a compact ASCII/Unicode logo, badges, tables, and clear sections over large marketing images.

## Timeline maintenance contract

The root `TIMELINE.md` is the public visual history/index. It is **not** authoritative for current engineering truth or future scope; `CURRENT_STATE.md`, `MASTER_ROADMAP.md`, durable decisions, immutable attempts, source, CI/release, and runtime evidence remain authoritative in their own domains.

### Update the timeline in the same coherent change when any of these happen

- a product phase or named milestone becomes ACTIVE, COMPLETE, REJECTED, DEFERRED, or SUPERSEDED;
- a production-path milestone is promoted, rolled back, or replaced;
- a milestone reaches its first public/runtime-test handoff or a new validated public checkpoint materially changes the project arc;
- a major repository/process capability changes how the project is developed or handed off, such as release-channel governance, continuity infrastructure, or another durable project-wide operating rule;
- roadmap phase order or major planned scope changes materially;
- an evidence link, PR/merge identity, or milestone description shown on the timeline becomes incorrect and needs correction.

Do **not** churn the timeline for every attempt, small bug fix, wording-only documentation edit, internal diagnostic, or continuity-only commit that does not change the public development story.

### Required timeline structure

Keep `TIMELINE.md` useful as a visual history rather than a second canonical roadmap:

1. a high-level phase/status rail;
2. a chronological visual milestone path;
3. evidence-linked milestone entries;
4. a clearly marked **current position**;
5. a future-phase horizon sourced from the canonical roadmap;
6. an explicit note that the timeline is a public summary/index, not authority;
7. navigation back to README, current state, roadmap, and contributor documentation.

Rules:

- Historical milestones already shown should not silently disappear. Correct or supersede them with evidence when needed.
- Do not label a milestone COMPLETE without the same evidence required by the canonical roadmap/current-state contract.
- Do not present planned work as implemented.
- When roadmap wording and the timeline disagree, fix the timeline from authoritative sources rather than treating the timeline as a tie-breaker.
- Prefer concise milestone summaries plus links to authoritative attempts/PRs over copying large evidence dumps.
- Keep Mermaid/Markdown rendering readable on GitHub light and dark themes.
- Update `CURRENT_STATE.md` only when current truth changes; timeline maintenance alone does not manufacture a current-state change.

## Release channels

Obsidian uses three publication levels.

### 1. Stable checkpoint

GitHub **Release**, not marked prerelease.

Meaning: the most validated public checkpoint for the project at that time. Because Obsidian itself is still experimental, "stable checkpoint" does not automatically mean general production readiness; release notes must state the real maturity level.

Use for:

- completed validated phases;
- meaningful production-path milestones that passed their frozen runtime/visual gates;
- versions intentionally designated as the public default.

The GitHub `latest` release should remain this channel.

### 2. Preview / test build

GitHub **Prerelease**.

Meaning: CI-green binary intentionally published for testers before promotion. It may be incomplete, experimental, or unsafe for normal play.

Publish a preview when:

- a milestone reaches its first real runtime-test handoff;
- a failed runtime test produces a meaningful corrected JAR that needs retesting;
- a tester-facing binary would otherwise exist only as a hard-to-find Actions artifact.

Do not publish a new prerelease for documentation-only/no-op changes.

Preview versions must include an obvious marker such as `dev`, `alpha`, `beta`, `rc`, or `preview`, and the release must be marked GitHub prerelease.

Every preview release must state:

- **NOT PRODUCTION READY** when that is true;
- exact source commit;
- target Minecraft/Fabric/Java/backend;
- what the tester should exercise;
- known limitations;
- whether visual behavior is expected to change;
- checksums for binary artifacts.

### 3. CI artifact

Every normal CI build may produce a short-lived Actions artifact.

CI artifacts are implementation evidence and convenient internal handoff objects. They are not a substitute for a discoverable Preview prerelease once a build is intentionally handed to external/manual testing.

## Release cadence

During active development:

- publish at least one Preview prerelease for each milestone that reaches real-machine testing;
- publish corrected Preview prereleases when the tester must install a new binary;
- promote a Stable checkpoint after the milestone/phase's required validation and merge criteria close;
- use `[no-release]` for documentation, continuity, repository-maintenance, or evidence-only merges that do not create a new tester-facing binary;
- if a testable JAR is handed off but intentionally not published as a Preview prerelease, record why in `CURRENT_STATE.md`.

Do not create releases merely to make the release count larger. Each release should correspond to a real test or validated checkpoint.

## Version and release automation

The main build workflow classifies versions containing `dev`, `alpha`, `beta`, `rc`, or `preview` as Preview and publishes them as GitHub prereleases when release publication is enabled.

`.github/workflows/publish-preview.yml` allows a maintainer to publish a CI-built Preview directly from an active branch before merge. It refuses version strings that do not look like prereleases.

`.github/workflows/publish-modrinth.yml` is the reusable/manual Modrinth mirror. Automatic GitHub release/Preview publication calls it only when repository variable `MODRINTH_PROJECT_ID` is configured. It rebuilds the exact selected source, verifies the expected runtime/sources JARs, checks Modrinth for an existing matching version number, and then publishes through the pinned `cloudnode-pro/modrinth-publish` action. GitHub remains package authority; Modrinth is a distribution mirror.

A release tag is immutable package identity. If a tester-facing binary changes, bump the version rather than replacing the asset behind an existing version.

### Modrinth credential boundary

- Never commit a Modrinth PAT, token value, authorization header, or derived credential to source, workflow YAML, logs, release notes, attempts, or continuity files.
- GitHub Actions injects the PAT from repository secret `MODRINTH_PAT`; only the secret name may appear in repository text.
- `MODRINTH_PROJECT_ID` is a non-secret repository Actions variable used for automatic mirroring. Manual dispatch may supply a one-run project-ID override.
- Do not print secret lengths, prefixes, hashes, or values for debugging. Validate presence only by success/failure.
- Keep third-party publishing actions pinned to an exact reviewed commit and record upgrades as a new attempt.
- Modrinth publication must never change promotion status, canonical package hashes, or the frozen runtime/visual contract.

## Public documentation ownership

- `README.md` — concise public landing page.
- `TIMELINE.md` — maintained public visual history/index; non-authoritative summary of milestone progression and current phase position.
- `docs/RELEASE_CHANNELS.md` — public explanation of Stable/Preview/CI channels.
- `CHANGELOG.md` — notable user-facing changes, not every attempt.
- `ai/CURRENT_STATE.md` — exact engineering truth and current handoff.
- `ai/MASTER_ROADMAP.md` — long-range plan.
- `ai/OPERATING_MANUAL.md` — engineering process.
- this file — repository presentation/hygiene policy.
- `ai/attempts/` — immutable detailed evidence.

## Inspiration and rationale

This policy deliberately follows common successful open-source patterns:

- a focused README that leads with what the project is and how to get/install it;
- detailed contributor/process material separated from the landing page;
- explicit warnings around development builds;
- releases/tags as distribution history instead of long-lived branch archives;
- automatic branch cleanup after PR completion.

The goal is not to copy another repository's branding. It is to keep Obsidian immediately understandable while preserving its unusually strong evidence/continuity discipline.
