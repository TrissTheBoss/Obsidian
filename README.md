<div align="center">

<pre>
  ____  ____   _____ _____ _____  _____          _   _ 
 / __ \|  _ \ / ____|_   _|  __ \|_   _|   /\   | \ | |
| |  | | |_) | (___   | | | |  | | | |    /  \  |  \| |
| |  | |  _ < \___ \  | | | |  | | | |   / /\ \ | . ` |
| |__| | |_) |____) |_| |_| |__| |_| |_  / ____ \| |\  |
 \____/|____/|_____/|_____|_____/|_____|/_/    \_\_| \_|
</pre>

# Obsidian

**A Vulkan-only Minecraft Java renderer focused on frame pacing, scalable terrain rendering, and GPU-driven visibility.**

[![Build](https://github.com/TrissTheBoss/Obsidian/actions/workflows/build.yml/badge.svg)](https://github.com/TrissTheBoss/Obsidian/actions/workflows/build.yml)
![Minecraft](https://img.shields.io/badge/Minecraft-26.2-62B47A)
![Java](https://img.shields.io/badge/Java-25-E76F00)
![Fabric](https://img.shields.io/badge/Fabric-0.19.3-DBD0B4)
![Renderer](https://img.shields.io/badge/Renderer-Vulkan--only-7B68EE)
![Status](https://img.shields.io/badge/status-experimental-orange)

[Releases](https://github.com/TrissTheBoss/Obsidian/releases) ·
[Roadmap](ai/MASTER_ROADMAP.md) ·
[Current state](ai/CURRENT_STATE.md) ·
[Contributing](CONTRIBUTING.md)

</div>

> [!WARNING]
> **Obsidian is experimental renderer software.** Preview builds may be incomplete or not production ready. Use a dedicated Minecraft profile, keep backups of important worlds, and read the release/test notes before installing a development build.

## Current status

| | Status |
| --- | --- |
| **Product phase** | **Phase 4 — GPU-driven visibility at real-world scale** |
| **Validated production baseline** | Phase 3 P3.10 opaque/cutout terrain replacement |
| **Current tester build** | `0.4.0-phase4-dev1` — P4.1 shadow large-scene GPU visibility |
| **Minecraft** | 26.2 |
| **Fabric Loader baseline** | 0.19.3 |
| **Java** | 25 |
| **Graphics backend** | Vulkan only |

P4.1 is intentionally **shadow-only**: the proven Phase 3 terrain path still owns production terrain rendering while the new persistent large-scene database and GPU frustum/compaction path are validated beside it.

For exact source SHAs, CI authority, test gates, and the current handoff, read [`ai/CURRENT_STATE.md`](ai/CURRENT_STATE.md).

## Getting a build

Obsidian uses three publication levels:

- **Stable checkpoint** — the most validated public checkpoint at that time.
- **Preview** — GitHub **Prerelease** for active testers; may be incomplete or not production ready.
- **CI artifact** — short-lived Actions output used for engineering evidence.

See [`docs/RELEASE_CHANNELS.md`](docs/RELEASE_CHANNELS.md) for the full policy.

### Test/install

1. Use **Minecraft 26.2**, **Fabric Loader 0.19.3**, and **Java 25**.
2. Download the build requested by the active test/release notes.
3. Place the Obsidian JAR in the instance `mods` folder.
4. In Minecraft, select **Prefer Vulkan (Experimental)** as the graphics API and restart when required.
5. Do not stack Obsidian with another complete terrain renderer/optimization mod unless compatibility is explicitly documented.

## What Obsidian is optimizing for

Obsidian prioritizes renderer behavior in this order:

1. strong **1% / 0.1% lows** and consistent frame pacing;
2. smooth chunk loading and camera movement;
3. large render distances as a normal workload;
4. high average FPS without hiding visual regressions;
5. bounded RAM/VRAM use and explicit GPU lifetime/synchronization;
6. vendor-neutral Vulkan architecture.

The project is correctness-first: performance experiments do not get promoted by weakening visual, lifecycle, synchronization, or differential-correctness gates after measurement.

## Architecture direction

```text
Minecraft / Fabric
       |
immutable render extraction
       |
renderer scene database
       |
+-----------------------------+
| async CPU mesh system       |
| GPU scene + visibility      |
+-----------------------------+
       |
bounded uploads / GPU arenas
       |
GPU culling + draw compaction
       |
indirect terrain rendering
       |
Vulkan render graph
       |
     screen
```

Minecraft keeps presentation/device ownership. Native Vulkan interop is kept narrow and evidence-driven rather than turning Obsidian into a second device/swapchain owner.

## Roadmap

| Phase | Focus | Status |
| --- | --- | --- |
| **0** | bootstrap / backend validation | Complete |
| **1** | Vulkan frame, memory, indirect and visibility foundations | Complete |
| **2** | real-section semantics and correctness reference | Complete |
| **3** | asynchronous CPU meshing, greedy output, production terrain replacement | Complete |
| **4** | GPU-driven visibility at real-world scale | **Active** |
| **Later** | broader production scaling, advanced visibility/LOD/render-graph work | Planned |

The full evidence-driven plan lives in [`ai/MASTER_ROADMAP.md`](ai/MASTER_ROADMAP.md).

## Development and repository organization

Obsidian keeps a persistent engineering memory under [`ai/`](ai/README.md). It records current truth, roadmap, decisions, failed experiments, exact package authority, and handoff state so development can resume without relying on hidden chat history.

Repository hygiene is explicit:

- `main` is the only permanent branch;
- active PR branches are temporary working state;
- merged/closed branches are deleted after their evidence is recorded;
- tags/releases and `ai/attempts/` preserve history;
- tester-facing development JARs should be published as GitHub **Prereleases**.

See [`ai/REPOSITORY_HYGIENE.md`](ai/REPOSITORY_HYGIENE.md) and [`CONTRIBUTING.md`](CONTRIBUTING.md).

## Project documents

- [`ai/CURRENT_STATE.md`](ai/CURRENT_STATE.md) — exact active implementation/runtime truth and next action.
- [`ai/MASTER_ROADMAP.md`](ai/MASTER_ROADMAP.md) — canonical long-range product plan and phase gates.
- [`ai/OPERATING_MANUAL.md`](ai/OPERATING_MANUAL.md) — engineering and validation procedure.
- [`ai/REPOSITORY_HYGIENE.md`](ai/REPOSITORY_HYGIENE.md) — branch, README, release, and public-surface rules.
- [`ai/DECISIONS.md`](ai/DECISIONS.md) — durable architecture/product decisions.
- [`ai/attempts/`](ai/attempts/) — immutable experiment and validation evidence.
- [`docs/RELEASE_CHANNELS.md`](docs/RELEASE_CHANNELS.md) — Stable / Preview / CI publication model.

---

<div align="center">

**Frame pacing first. Scale second. FPS without cheating.**

</div>
