# fix-kover-gradle10-deprecation

## Why

`guidelines/Build.md` §7 carried the Kover deprecation as a **known upstream issue** (backlog entry
`§14`, issue #55): "Kover 0.9.8 emits a Gradle deprecation warning on Gradle 9.6 (Project-object
dependency notation from its own internals) … This becomes a hard error in Gradle 10 — revisit when
upgrading either dependency."

**Verified in the tree on HEAD (2026-10-20, Gradle 9.6.1, Kover 0.9.8 pinned at
`build.gradle.kts:8`).** `./gradlew help --warning-mode all` prints one line:

```
Using a Project object as a dependency notation has been deprecated. This will fail with an error
in Gradle 10. Please use the project(String) method on DependencyHandler or the
createProjectDependency(String) method on DependencyFactory instead.
```

`-Dorg.gradle.deprecation.trace=true` puts the only stack frame inside the plugin, not in any script
here:

```
at kotlinx.kover.gradle.plugin.appliers.PrepareKoverKt.prepare(PrepareKover.kt:29)
at kotlinx.kover.gradle.plugin.KoverGradlePlugin.apply(KoverGradlePlugin.kt:29)
```

**Root cause, one sentence:** Kover 0.9.8's own plugin-apply path
(`PrepareKoverKt.prepare`, `PrepareKover.kt:29`) hands a `Project` object to
`DependencyHandler.add`, so the deprecation is emitted before any line of this repository runs.

**The fix is the pin, and the pin has a release that already carries it:** upstream issue #818 ("Gradle
9.6.0 deprecation warning") is fixed in Kover 0.9.9 — its changelog lists `#818 Fixed Gradle 9.6.0
deprecation warning`, commit `135968e`. Measured in this tree, counting the
`Project object as a dependency notation` lines from `./gradlew help --warning-mode all`:

| pinned Kover | warning lines |
|---|---|
| 0.9.8 (HEAD) | 1 |
| 0.9.9 | 0 |
| 0.9.11 | 0 |

**Red test on HEAD** — `KoverDeprecationBaselineTest` (`:app` unit tests, file
`app/src/test/java/com/naviveylin/build/KoverDeprecationBaselineTest.kt`), run with
`./gradlew :app:testMobileDebugUnitTest --tests 'com.naviveylin.build.KoverDeprecationBaselineTest'
-PforceTests --no-build-cache`:

- case `the pinned kover version clears the project-object deprecation` — fails:
  `build.gradle.kts must pin Kover 0.9.9 or newer (pinned: 0.9.8)`
- case `the coverage guideline names the pinned version and records no open upstream issue` — fails:
  `guidelines/Build.md §7 must not keep the Project-object deprecation as an open upstream issue once
  the pinned version clears it`
- XML: `TEST-com.naviveylin.build.KoverDeprecationBaselineTest.xml`, **tests=2 failures=2**,
  timestamp `2026-10-20T11:59:38.216Z` (`evidence/`).

**Owning capability:** `kover-aggregate-report` (its requirement "No deprecation regression from the
declaration" is the one that named Kover 0.9.8). **Owning guideline section:**
`guidelines/Build.md` §7 "Code coverage" (routed by `AGENTS.md`'s Documentation Map: "writing or
fixing a test — constraints, fork budget, coverage, the JNI stub" → `Build.md` §6, §7).

## What changes

- `build.gradle.kts`: the Kover plugin pin `0.9.8` → `0.9.9`, and the comment above the root `kover`
  configuration states what the pin buys instead of claiming a notation the code does not use.
- `guidelines/Build.md` §7: the engine table names 0.9.9, and the "Known upstream issue" paragraph
  becomes the measured record of the fix (which release cleared it, how it was measured, the guard
  test that holds it).
- `app/src/test/java/com/naviveylin/build/KoverDeprecationBaselineTest.kt`: the guard — two cases,
  the pinned version and the guideline's statement of it.
