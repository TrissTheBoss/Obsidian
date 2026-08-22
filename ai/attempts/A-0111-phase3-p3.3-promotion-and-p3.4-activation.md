# A-0111 - P3.3 promotion and P3.4 activation

**Date:** 2026-08-22  
**Target branch:** `main`  
**Result:** `SUCCESS`

## Runtime prerequisite

A-0110 records the successful `0.3.0-phase3-dev5` reference run and the user's positive visual regression verdict (`everything looks right`). Final runtime closure included:

- `phase3GateReady=true`;
- `schedulerEvidenceReady=true`;
- `binaryVisibilityEvidenceReady=true`;
- `greedyRectangleEvidenceReady=true`;
- `hardFailure=false`;
- 162/162 worker jobs completed;
- zero queue-full rejection/failure/shutdown-join failure;
- rectangle builds `162`;
- topology rectangles `21,286` covering exactly `48,261` P3.2 visible faces;
- `26,975` source faces saved, `558` permille / **55.8%** topology reduction;
- exact per-direction rectangle coverage equals the six P3.2 visibility totals;
- retained bytes `85,144 = 21,286 * 4`;
- primary mask audits/matches `162/162`;
- rectangle determinism audits/matches `4/4`;
- independent rectangle/reference audits/matches `4/4`;
- zero dropped lifecycle events / unsafe stale scene installs;
- workers/staging/arena/resources clean;
- process exit code `0`.

Dev5 explicitly kept `greedyRectangleGpuEmission=false` and `renderCorrectMergeKeyComplete=false`; therefore the runtime closes P3.3 topology extraction without claiming production greedy rendering.

## Exact CI prerequisite

- canonical runtime source/package head `75a35de6b073ca0d9bce013c43f2043d37f9b79a`: run `32599625494` passed Java 25 / Gradle 9.5.1 build + artifact upload; release skipped;
- evidence/package documentation head `b1e768c34f940ab3ec2b97da4d9e2f21d9b45e65`: run `32599693103` passed build + artifact upload; release skipped;
- final runtime-evidence PR head `ae0c220019257b1c016bd432a5e7fed464c35816`: run `32600198475` passed build + artifact upload; release skipped.

## Promotion

Standing user merge authorization applied after all frozen gates passed.

PR #37 — Phase 3 dev5 correctness-first greedy rectangle sidecar — was marked ready and merged into `main` using `[no-release]` protection.

Merge commit:

- `34caa19a9de70ba8e0395a2992180f3a24a3f7aa`.

## Closure judgment

P3.3 is COMPLETE and merged because it proved:

1. deterministic primitive greedy rectangle extraction from the proven P3.2 directional masks;
2. exact no-missing/no-extra/no-overlap face-set coverage on every production build;
3. exact independent-oracle equivalence on audit cadence;
4. substantial measured topology reduction on real terrain;
5. bounded reusable worker scratch and compact retained records;
6. unchanged scheduler/generation/event/resource/completion-gated lifetime correctness;
7. positive human visual regression validation;
8. no premature GPU greedy emission or merge-key completeness claim.

## Next milestone

**P3.4 — render-correct merge key is now ACTIVE.**

P3.4 is the checkpoint that determines whether topology-adjacent canonical faces are genuinely render-equivalent and therefore safe to merge. The production key must preserve every output-affecting property available in the renderer-owned baked data, including at least:

- face direction/orientation;
- render layer;
- material/sprite identity;
- tint/color state;
- sky and block light;
- four-corner AO/shade state and diagonal choice where relevant;
- UV mapping/behavior and any repeat/stretch constraints;
- supported special/fluid/model-specific distinctions.

Faces may merge only when all required properties agree. Block/state identity alone is never sufficient. Arbitrary generalized baked-model geometry remains exact passthrough unless a supported canonical face can be proven equivalent to the conservative P3.2/P3.3 topology face and assigned the complete key.

The correctness-first first P3.4 slice should build/validate merge-key identity and mergeability as a sidecar/differential product before allowing any key-aware greedy geometry to replace `BakedSectionMesh` output. Any later P3.4 step that changes emitted GPU geometry requires renewed runtime and human visual validation.

## Release discipline

The P3.3 merge and this activation record use `[no-release]`. Public release intent remains unchanged until a separate release decision.