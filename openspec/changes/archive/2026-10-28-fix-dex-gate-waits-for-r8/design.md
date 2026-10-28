# Design

## Context

The gate (`buildSrc/.../dex/CheckClientApiInDex.kt`) is a typed task whose `@InputFiles` is the variant's whole
`build/intermediates/dex/<variant>` tree; it has no outputs, so it runs whenever it is in the graph. What it
lacks is a *producer* for the tree on a minified variant. AGP 9 has two dex shapes:

| variant | tasks that write `intermediates/dex/<variant>` |
|---|---|
| `*Debug` (`isMinifyEnabled = false`) | `dexBuilder<V>`, `mergeProjectDex<V>/…`, `mergeLibDex<V>/…`, `mergeExtDex<V>/…` |
| `*Release` (`isMinifyEnabled = true`) | `minify<V>WithR8` only (`minify<V>WithR8/classes.dex`) |

`./gradlew :app:tasks --all` lists no `dexBuilder*Release` and no `merge*Dex*Release`: the names the wiring used
do not exist on the shape the release machine builds.

## Goals / Non-Goals

Goals: the gate waits for whatever writes its variant's dex, on all four variants, and a check fails when it
does not. Non-goals: how the gate finds classes (unchanged), what it requires (unchanged), what it costs.

## Decisions

**D1 — one superset of producer names, filtered against the tasks that exist.** The alternative is to branch on
`isMinifyEnabled` (or on `variant.buildType`), which copies the flavour's minification state into the gate and
breaks silently the day a build type flips. Names a variant does not have are dropped by
`tasks.matching { it.name in dexProducers }`, which stays lazy: the reference is resolved when the graph is
built, after AGP registered the variant tasks — the same mechanism that made the debug wiring work on HEAD.

**D2 — the guard reads the graph, not the dex.** A test that builds an AAB to prove a dependency would cost a
native build per case; `--dry-run` prints exactly the graph an invocation asks for and costs a configuration
(~1 s warm). `tools/check-jvm-test-graph-selftest.sh` is the precedent (issue #270).

**D3 — the case ids are per variant, not per shape.** `mobileReleaseGateFollowsR8` and
`automotiveReleaseGateFollowsR8` are separate cases because the report failed on the automotive one and a
rename of a flavour's variant would otherwise hide which gate lost its producer. `--case` runs one, so a
revert-check costs one Gradle invocation.

## Risks / Trade-offs

- The superset also names `minify<V>WithR8` on a debug variant, where the task does not exist. Harmless by
  construction (`tasks.matching`), and the selftest's debug cases fail if a debug graph ever holds that task —
  i.e. if a build type flips without the wiring being revisited.
- The selftest configures `:app`, so it needs the Android SDK; CI runs it below 'Install Android SDK', as the
  JVM-only probe does (issue #198 for the failure text that costs a round).

## Verification

`:app:checkClientApiInDex{Automotive,Mobile}Release` green; `./gradlew release` green with both AABs;
`tools/check-dex-gate-graph-selftest.sh` 6/6; the two release cases red with the merge-only set restored.
`tasks.md` carries the commands and their measured times.
