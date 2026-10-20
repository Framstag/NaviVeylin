# Design — fix-kover-gradle10-deprecation

## Root cause

Kover 0.9.8's plugin-apply path adds a `Project`-object dependency to its own `kover` configuration:
`KoverGradlePlugin.apply` → `PrepareKoverKt.prepare` (`PrepareKover.kt:29`) →
`DependencyHandler.add` → `DependencyProjectNotationConverter.convert`, which Gradle 9.6 reports as
"The `Project` object as a dependency notation" deprecation and rejects in Gradle 10. The add happens
while the plugin is applied, before any project script statement runs, so no line of this repository
can reach it: the plugin version is the only lever.

Evidence: `guidelines/Build.md` §7's own "Known upstream issue" paragraph, and the deprecation trace
quoted verbatim in `proposal.md`'s `## Why` (produced with
`./gradlew help --warning-mode all -Dorg.gradle.deprecation.trace=true`).

## The fix

One edit sets the lever: the Kover plugin pin in `build.gradle.kts` moves `0.9.8` → `0.9.9`, the
release whose changelog entry for upstream issue #818 reads `Fixed Gradle 9.6.0 deprecation warning`.
Nothing else in the build changes: the root `kover` configuration, the module-level `kover { }` blocks
and the report filters are untouched.

Two supporting edits keep the record honest and the pin enforced:

- `guidelines/Build.md` §7's engine table names 0.9.9 and the "Known upstream issue" paragraph becomes
  the upstream-attributed record of the fix — the release and issue the changelog names, and the guard
  test that holds it.
- `KoverDeprecationBaselineTest` (`:app` unit tests) reads the pin out of `build.gradle.kts` and the
  §7 section out of `guidelines/Build.md`, so a downgrade and a guideline drift each fail a host test.

## What the guard test can and cannot decide

A build's deprecation output is not reachable from a JUnit case: no host test can configure the
project it runs inside. The case therefore asserts the one thing the deprecation *is* a function of —
the pinned release — against `0.9.9`, the threshold `proposal.md` names from upstream issue #818. That
is a proxy, and it is stated as one here: the claim about the warning's absence is the upstream
changelog's, quoted in §7 and in the proposal, not a test. The proxy is falsifiable in the useful
direction — reverting the pin to 0.9.8 fails both cases — and it cannot pass for a release that still
emitted the warning, because the threshold is the release the changelog names as the fix.

## Alternatives the evidence eliminated

- **Silence it from our scripts.** Eliminated by the stack trace: the only frame is inside
  `kotlinx.kover.gradle.plugin.appliers.PrepareKoverKt`, reached from `KoverGradlePlugin.apply`. A
  script-level rewrite (string notation, `dependencies.create(...)`) changes which lines *we* add, not
  what the plugin adds while it is applied.
- **`--warning-mode none` / suppressing deprecations in `gradle.properties`.** Eliminated as a fix:
  the call still fails in Gradle 10; suppressing a warning that becomes an error only hides the
  deadline.
- **Upgrading Gradle instead.** Eliminated as wider than the item: the wrapper is deliberately on
  9.6.1, and a wrapper bump changes every task's behaviour for a warning whose cause is a plugin pin.
- **Waiting for the Gradle 10 upgrade** (the paragraph's own instruction) is what this change
  executes: the condition "newer Kover" has arrived, and the item's remaining work is the pin.

## Blast radius

- `build.gradle.kts` (root): one version string, one comment.
- `guidelines/Build.md` §7: the engine table row and the former known-issue paragraph.
- One new test class in `:app`; it reads two repository files and touches no app code.
- Coverage behaviour: 0.9.8 → 0.9.9 is a patch release whose changelog carries `#818`.
  The aggregation graph is unchanged by construction — the root `kover` configuration and its
  `kover(project(...))` entries are not touched — and this change archived no capture of a
  `:koverExternalArtifacts` resolution run. The report tasks themselves are not run by
  this change's gate (`./gradlew test`); that exclusion is recorded in `tasks.md`.

## Rollback

Set the pin back to `0.9.8` (and, if the guideline is to be left as it was, restore §7's paragraph).
Both guard cases fail, which is the point: the rollback is a visible regression, not a silent one.
