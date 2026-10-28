# Proposal

## Why

`./gradlew release` — the one command the release machine runs — failed after 3 s with neither AAB built:

```
> Task :app:checkClientApiInDexAutomotiveRelease FAILED
> No dex archives to scan under /home/tim/projects/NaviVeylin/app/build/intermediates/dex/automotiveRelease
  — the gate reads the variant's merged dex directories and they hold no `*.dex` file.
```

9 actionable tasks, 3 executed. The gate was in the graph and its scanned tree empty, i.e. it ran *before*
anything wrote that tree. `:app:checkClientApiInDex<Variant>` (spec `test-coverage` — the split's vendored half
reaches the packaged app, issue #691) depends on `dexBuilder<Variant>` and the three merge steps
(`mergeProjectDex*`, `mergeLibDex*`, `mergeExtDex*`). Both `release` variants set `isMinifyEnabled = true`
(`app/build.gradle.kts:231`), and AGP 9 runs neither for them: `./gradlew :app:tasks --all` lists no
`dexBuilder*Release` and no `merge*Dex*Release` at all, only `minify<variant>WithR8`. R8 writes the variant's
only dex, under the same tree the gate reads —
`app/build/intermediates/dex/automotiveRelease/minifyAutomotiveReleaseWithR8/classes.dex`, measured after
`:app:minifyAutomotiveReleaseWithR8` ran on 2026-10-28.

The gate's own change (`fix-client-api-in-dex`) verified one case, `:app:checkClientApiInDexMobileDebug`
(its `tasks.md` 4.4). The debug wiring was right and the release wiring was never exercised, so a guard that
was added *because* a green build shipped no app was itself absent from the release path: the red case below
is a `--dry-run` graph that shows the gate alone, `:app:checkClientApiInDexAutomotiveRelease SKIPPED`, and
nothing else.

## What Changes

- **The gate names its variant's dex producer, per shape.** A plain variant's producer set stays the dex
  builder and the three merge steps; a minified variant's adds `minify<Variant>WithR8`. The set is a superset
  filtered against the tasks that exist (`tasks.matching`), so the same wiring serves both shapes without a
  condition on `isMinifyEnabled` — a second copy of the flavour's minification state in the gate is a second
  thing to keep in step.
- **A shell case asserts the graph, per variant.** `tools/check-dex-gate-graph-selftest.sh` (6 cases, each one
  `--dry-run`, no build, no network) fails when a variant's gate does not wait for a task of its own shape, and
  when a `bundle<Release>` graph holds the gate but not its producer. Wired into CI beside
  `tools/check-jvm-test-graph-selftest.sh`, below 'Install Android SDK'.
- **No change to what the gate reads** (the whole `intermediates/dex/<variant>` tree, `class_defs` only) and none
  to the required class set, which still comes from the two source roots.

Not in scope: the release version state (`app/release-version.properties` is machine-local and the failed run
already bumped `versionCode`), the `automotiveReleaseRuntimeClasspath`/`mobileReleaseRuntimeClasspath`
configuration-time resolution warnings the same run printed, and the gate's cost.

## Capabilities

`test-coverage` — the gate's existence is specified there; its *producer* is the part that was missing.

## Impact

- `app/build.gradle.kts` — the `clientApiDexVariants` loop: the dependency set, and the comment that named only
  the merge tasks.
- `tools/check-dex-gate-graph-selftest.sh` — new, 6 cases.
- `.github/workflows/build.yml` — one step, "Check the dex gate's producer".
- `guidelines/Design.md` §5 bullet "Both provenances reach the app, and the dex says so" — names the case file
  beside the gate it already describes.
- No Kotlin, native, resource, manifest or dependency change; nothing rebuilds as a result of the wiring.

Rollback: restore the merge-only dependency set. The gate then reads whatever tree the previous build left
behind on a developer machine and fails on an empty one, which is the reported defect.

Guidelines: `guidelines/Build.md` §2 (a delta's verification follows the changed files — this one changes the
build script, so it runs the two release gates and the `release` command itself), §6f (diagnosing a red run),
§7 (why the gate is a typed task with declared inputs rather than script-level logic).
