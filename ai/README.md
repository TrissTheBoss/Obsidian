# Obsidian AI Continuity Directory

This directory is the persistent operating memory for AI agents and humans working on Obsidian.

Its purpose is to make the project resumable across conversations, models, agents, machines, and long gaps in development without relying on hidden chat context.

## Required reading order

Every agent taking over Obsidian should read these files before making changes:

1. `ai/CURRENT_STATE.md` - what exists right now, what is validated, and what is next.
2. `ai/OPERATING_MANUAL.md` - project goals, constraints, workflow, validation rules, and maintenance rules.
3. `ai/DECISIONS.md` - durable architectural and product decisions and why they were made.
4. `ai/ATTEMPT_LOG.md` - append-only record of experiments, failures, fixes, and results.

## Core rule

Do not rely on memory when the repository can contain the answer.

If an agent tries something that changes code, build behavior, runtime behavior, tooling, CI, release behavior, architecture, or a project assumption, it must record the attempt in `ATTEMPT_LOG.md` whether it succeeded or failed.

When a successful attempt changes the current truth of the project, also update `CURRENT_STATE.md`. When it creates or reverses a durable design choice, also update `DECISIONS.md`.

## Log discipline

- Keep failed attempts. Never rewrite history to make the project look cleaner than it was.
- Prefer evidence: commit SHAs, tags, build output, profiler captures, issue links, benchmark files, crash reports, or reproducible commands.
- State the intended effect, the actual result, and why the result happened when known.
- If the cause is not known, write `unknown` rather than guessing.
- Mark obsolete entries as `SUPERSEDED`; do not delete them.
- Never store credentials, access tokens, private keys, cookies, passwords, or other secrets here.

## Repository truth

The canonical repository is `TrissTheBoss/Obsidian`.

The canonical binaries are artifacts built by the repository CI/release workflow against the real Minecraft/Fabric dependency set. Locally mocked or manually assembled JARs are not release-authoritative unless a later decision explicitly changes this rule.
