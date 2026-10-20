## Root cause

Each of the ten task registrations in the project's own Kotlin DSL scripts uses the delegated-property form:

```kotlin
val checkHardcodedStrings by tasks.registering {
    …
}
```

Kotlin's delegate resolution asks `TaskContainer` for a `provideDelegate` operator, which Gradle 9.6.1
deprecates and Gradle 10 removes: the property's name is the only place the task name exists, and the
configure block is handed to the container through that operator. Ten registrations — `app/build.gradle.kts:377,
406, 444, 474, 606, 734`, `auto/build.gradle.kts:118, 138`, `core/build.gradle.kts:104, 125` — emit the same
warning once per configuration, which is the banner every build prints.

One mechanism, not two. The second warning the same configuration prints, `Using a Project object as a
dependency notation`, was measured out of this change's scope: with the three `kover(project(":x"))`
declarations at `build.gradle.kts:23-25` commented out, the warning is still emitted
(`evidence/kover-probe-lines-removed.txt`), so it comes from the Kover 0.9.8 plugin's internals — the
misattribution `guidelines/Build.md` §7 already records. It stays, documented.

## The single fix

Rewrite the ten registrations in the named form:

```kotlin
val checkHardcodedStrings = tasks.register("checkHardcodedStrings") {
    …
}
```

The name is the one the delegate derived from the property name, the configure block is unchanged, and the
value is the same `TaskProvider<Task>` a delegate produced — so every task path in CI
(`:app:checkHardcodedStrings`, `:app:mergeNativeSbom`, `:app:downloadSbomCli`, …) resolves exactly as before. Nothing else in those scripts changes: no configuration
moved out of a block, no `doLast` closure rewritten, no input or output declaration touched.

## Blast radius

`app/build.gradle.kts` (six sites), `auto/build.gradle.kts` (two), `core/build.gradle.kts` (two), one new test
file (`app/src/test/java/com/naviveylin/build/GradleScriptDeprecationTest.kt`), `guidelines/Build.md` §7 (one
paragraph), the spec delta. No application source, no native code, no manifest, no dependency, no release
version state, no CI workflow: the guard is a unit-test case, so the gate and CI's existing test step run it
without new wiring.

## What the evidence eliminated

- **A `check` task that greps the scripts, wired the way `checkHardcodedStrings` is.** Eliminated twice over:
  it would be registered *in* the scripts it scans, so the scan cannot run before the configuration that emits
  the warning, and each new script-level `DefaultTask` registration is one more of the eight configuration-cache
  problems `guidelines/Build.md` §7 records — a guard that costs the capability it protects. An `:app` unit test
  already runs in the documented gate and in CI's test step, with no new wiring at all.
- **Leave the sites, pin Gradle 9, revisit at the upgrade.** Eliminated: this is the only project-owned family
  left (measured: ten of the eleven warnings), so the whole cost of the eventual upgrade is one mechanical
  rewrite that the tree can pay now, with a red case that proves it. Pinning is not a fix and the pin is not
  ours to set for the plugins' own Gradle 10 timing.
- **Silence it with `--warning-mode none` / `org.gradle.warning.mode`.** Eliminated: it hides plugin warnings
  and our own alike, leaves the construct in place (so Gradle 10 still fails), and removes the measurement the
  next sweep would need. The item asks for a sweep, not for quiet output.
- **Rewrite `kover(project(":app"))` to a non-project notation as part of the same change.** Eliminated by
  measurement, not by preference: the warning survives with those lines removed, so a rewrite there would
  change a working declaration for no warning, on a false premise. Recorded as plugin-owned, as §7 already
  does.

Honest cost of the fix: none at task level. The one new file is a test that reads 8 small files (measured:
`scripts=8 findings=10` in the red run, 0.47 s for the failing case), and it stays inside the fork budget the
module suite already declares.

## Rollback

Revert the commit. The delegate form returns and every build works again on Gradle 9.6.1, printing the ten
warnings; the guard case then goes red, which is the signal that the sweep is owed, not damage. No state to
undo: the change writes nothing outside the repository, and no task name, path or output moves.
