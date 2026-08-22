# A-0080 - Phase 2 dev6 section lifecycle and rebuild plan

Status: **ACTIVE / PLAN FROZEN BEFORE IMPLEMENTATION**

Date: 2026-08-21
Branch: `phase2/section-lifecycle-rebuild`
Target version: `0.2.0-phase2-dev6`

## Goal

Replace the Phase 2 validation probes' pass-based recapture behavior with an event-driven, versioned lifecycle for one real section that proves correct load, update, neighbor-border invalidation, resource reload, rebuild replacement and unload behavior.

This milestone is about lifecycle correctness and bounded responsiveness, not production meshing throughput or persistent multi-section scene scale.

## Proven foundation retained

P2.1-P2.5 remain correctness foundations and must not be weakened:

- immutable section + one-block halo snapshots;
- independent permanent cube oracle;
- exact generalized vanilla MODEL capture through `ModelBlockRenderer -> BlockQuadOutput`;
- exact SOLID/CUTOUT material, UV, tint, light and AO semantics for the accepted dev5 domain;
- public BLOCK/lightmap indexed-indirect drawing;
- bounded staging and generation-safe device arena;
- completion-gated replacement/destruction;
- `nativeGraphicsSeam=false` unless a new concrete public capability gap is proven.

## Exact Minecraft 26.2 inspection required first

Before choosing mixins/events, inspect the exact Loom-resolved client implementation for:

- how `ClientLevel`/client chunk cache receives and applies full chunk/section load data;
- chunk/section unload/removal path and render-section invalidation consequences;
- client block-state update path(s), including network and local update entry points;
- light-engine notification/update paths that cause section render dirtiness;
- vanilla neighbor/border dirty propagation for block and light changes;
- `LevelRenderer` / section dispatcher APIs that mark sections dirty and how neighbor sections are selected;
- render-resource/model reload lifecycle and exact reload listener/resource-epoch seams;
- world replacement/disconnect lifecycle;
- thread affinity for each relevant callback;
- whether Fabric API exposes an exact stable event for any required seam or whether a narrow mixin is necessary.

Do not rely on older-version names or Sodium/Fabric assumptions.

## Required dev6 architecture

### 1. Renderer-owned lifecycle identity

Introduce a minimal one-section lifecycle record with:

- section coordinates;
- monotonically increasing generation/version;
- current dirty reasons/flags;
- current immutable snapshot/capture identity;
- current live GPU allocation identity;
- pending replacement identity if applicable;
- unloaded/invalid state.

Generation must be carried from dirtying/capture through build/upload/install. A result whose generation no longer equals the section's current generation must be rejected before becoming live.

### 2. Event-driven invalidation

A relevant event must mark the tracked section dirty immediately rather than waiting for a timed comparison pass.

Required dirty causes:

- block state update inside the section;
- block update on a neighboring section border that can affect the tracked section's face culling/AO/light halo;
- light change affecting the section/halo;
- section/chunk load/reload;
- section/chunk unload;
- model/resource reload;
- world replacement/disconnect.

Dirty causes must be counted separately for diagnostics.

### 3. Neighbor-border rule

Because the immutable capture uses a one-block halo, a block/light change on a section boundary can invalidate more than the section containing the changed cell.

The first proof must use a conservative exact rule: dirty the changed section and every face/edge/corner neighboring section whose one-block halo can include the changed coordinate. Do not optimize this propagation until correctness is proven.

### 4. Versioned rebuild/install

For the tracked section:

1. dirty event increments generation;
2. next bounded render-thread opportunity captures exact immutable input for that generation;
3. pure generalized mesh build consumes only immutable capture;
4. upload uses existing bounded staging/device arena;
5. before installation/draw, generation is checked again;
6. stale build/upload output is discarded/retired without becoming live;
7. valid replacement becomes live atomically at renderer-record level;
8. old live GPU geometry/indirect resources retire only behind GPU completion.

Do not destroy/reuse the old allocation merely because a newer CPU generation exists.

### 5. Unload behavior

On tracked section/chunk unload or world teardown:

- invalidate/increment generation;
- prevent any older pending result from installing;
- stop drawing the section promptly;
- retire live GPU resources completion-gated;
- end with zero pending ownership after bounded cleanup/shutdown.

### 6. Resource reload behavior

The existing model/atlas resource epoch remains part of capture validity. A resource reload must dirty/rebuild the tracked section and prevent pre-reload captured geometry from installing or continuing to draw against a new epoch.

## Validation probe shape

Dev6 may still track one section so P2.7 retains multi-section scene ownership, but unlike dev5 it must keep that section alive across multiple real changes rather than recreating a one-shot probe for six static passes.

Required runtime interaction sequence should include at least:

1. initial section install;
2. block break/place inside tracked section -> dirty -> rebuilt generation visible promptly;
3. border edit or movement to a tracked boundary case -> neighbor invalidation observed;
4. multiple rapid edits before rebuild completion -> generation advances and stale result(s) rejected or superseded safely;
5. section/chunk unload or world exit -> live resources retired safely;
6. if practical in one run, resource reload -> generation/epoch invalidation + reinstall.

If exact 26.2 APIs make one of these unsafe to automate, keep the user action explicit and log machine-verifiable counters.

## Required metrics/logging

At minimum:

- current generation;
- generations issued/captured/built/uploaded/installed;
- dirty events by reason;
- rebuild requests/coalesced events;
- stale captures/builds/uploads rejected;
- live replacements;
- unload invalidations;
- resource reload invalidations;
- time from dirty event to valid install (ns/ms);
- snapshot/generalized/drawable fingerprint per installed generation;
- world reads after generalized capture = 0;
- arena allocations/retired/reclaimed;
- staging submitted/reclaimed/backpressure;
- resource retire/release counts;
- pending ownership at shutdown;
- profiler-only submissions = 0.

## Runtime success gate

Reference RX 6800 XT validation must demonstrate:

- dev6 loads on Vulkan;
- initial live generation installs and draws correctly;
- a real block edit produces a new generation without waiting for a timed pass reset;
- visible stale-overlay duration is bounded to the event-driven rebuild latency and the next valid installed generation matches the edit;
- rapid repeated edits do not let stale generations replace newer state;
- neighbor-border invalidation is observed for a boundary-relevant edit;
- unload/world exit prevents stale reinstall and fully retires GPU ownership;
- resource epoch mismatch/reload cannot keep old geometry live against new model/atlas resources;
- completion-gated lifetime remains clean;
- no unbounded queues/allocations or waits;
- process exits 0;
- human visual review finds no persistent stale/duplicate/missing geometry after rebuilds.

## Deliberate boundary

Not dev6:

- persistent several-section scene database (P2.7);
- production worker pool / async greedy mesher (Phase 3);
- partial remeshing optimization;
- global vanilla terrain replacement;
- translucent/fluid terrain (Phase 6);
- performance claims beyond bounded lifecycle behavior.

The dev6 design should nevertheless carry generation identity in a form that can later cross async worker boundaries without redesigning the ownership model.

## Merge gate

Dev6 requires exact API evidence, implementation/package CI, reference runtime validation, human lifecycle visual validation where applicable, and fresh explicit user merge authorization. The user's authorization to merge dev5 does not automatically authorize dev6.

This attempt is immutable once committed.
