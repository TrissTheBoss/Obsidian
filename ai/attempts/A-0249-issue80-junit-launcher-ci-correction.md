# A-0249 — Issue #80 JUnit launcher CI correction

**Date:** 2026-10-05  
**Objective:** Diagnose and correct the first exact-head CI failure for PR #81.  
**Result:** PARTIAL — root cause corrected; replacement exact-head CI pending.

## Evidence

- PR #81 head before correction: `4211b5fd7536646cd86ab84991b2209b85361b05`.
- Build run `37305159414` / #862: **FAILURE**.
- Failed job: `111746948244`, step **Unit tests**.
- `:compileClientJava` and `:compileTestJava` both succeeded.
- `:test` failed before executing tests because Gradle 9.5.1 could not load the JUnit Platform launcher:
  `Failed to load JUnit Platform ... including the JUnit Platform launcher.`

## Correction

Add explicit test runtime dependency:

`org.junit.platform:junit-platform-launcher:6.1.3`

This does not change renderer code or runtime packaging semantics; it supplies the test process launcher required by Gradle's JUnit Platform integration.

## Next action

Require replacement exact-head Build CI to pass unit tests, static analysis, packaging, and artifact upload before PR #81 is marked ready or merged.
