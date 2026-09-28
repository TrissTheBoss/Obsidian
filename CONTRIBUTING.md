# Contributing to Obsidian

<div align="center">

[README](README.md) · [Timeline](TIMELINE.md) · [Roadmap](ai/MASTER_ROADMAP.md) · [Current state](ai/CURRENT_STATE.md) · **Contributing**

</div>

Obsidian is an experimental Minecraft 26.2 Fabric renderer with strict correctness and runtime-evidence requirements.

## Before changing code

1. Read `ai/README.md` and follow its required reading order.
2. Check `ai/CURRENT_STATE.md` for the active milestone and exact handoff.
3. Check `ai/MASTER_ROADMAP.md` before changing scope or phase ordering.
4. Use exact Minecraft 26.2/Fabric APIs; do not guess renderer internals from another version.

## Branches and pull requests

- `main` is the only permanent branch.
- Use one descriptive branch per coherent change.
- Open a draft PR for milestone work that spans more than one session.
- Close/delete temporary inspection branches after their evidence is recorded.
- Branches are not archives; history belongs in PRs, tags/releases, and `ai/attempts/`.

See `ai/REPOSITORY_HYGIENE.md` for the full branch/release/README policy.

## Validation

GitHub CI against the declared Java/Minecraft/Fabric dependencies is the compile/package authority. Renderer behavior that depends on a real Vulkan runtime must also satisfy the relevant real-machine evidence contract before promotion.

Do not weaken frozen correctness or performance gates after observing a failure. Record the failed attempt, determine the cause, and create a new bounded correction.

## Documentation

A meaningful engineering attempt needs an immutable record under `ai/attempts/`. Synchronize `CURRENT_STATE.md`, decisions, roadmap, README, release notes, and PR status whenever their truth changes.

## Releases

Tester-facing development JARs should normally be published as GitHub **Prereleases**. Validated public checkpoints use normal Releases. CI artifacts remain useful for short-lived internal evidence.

See `docs/RELEASE_CHANNELS.md`.
