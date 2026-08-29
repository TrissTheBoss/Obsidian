# Obsidian

[![Build](https://github.com/TrissTheBoss/Obsidian/actions/workflows/build.yml/badge.svg)](https://github.com/TrissTheBoss/Obsidian/actions/workflows/build.yml)

Obsidian is an experimental Vulkan-only Minecraft Java 26.2 Fabric renderer project focused first on frame pacing and large render-distance scalability.

Current development phase: Phase 3

Project planning and continuity:

- [`ai/MASTER_ROADMAP.md`](ai/MASTER_ROADMAP.md) - canonical full roadmap, planned features, phase gates, experiments, performance/compatibility/release strategy, and roadmap-change procedure.
- [`ai/CURRENT_STATE.md`](ai/CURRENT_STATE.md) - exact active branch/version/milestone and current validation state.
- [`ai/README.md`](ai/README.md) - required reading order and continuity-system guide.
- [`ai/OPERATING_MANUAL.md`](ai/OPERATING_MANUAL.md) - engineering, validation, handoff, and roadmap-governance procedure.
- [`ai/DECISIONS.md`](ai/DECISIONS.md) - durable architectural/product decisions and rationale.

Planned production terrain meshing uses a worker-local **binary/bitmask greedy mesher**, differential-tested against the simple Phase 2 reference oracle rather than serving as its own correctness source.
