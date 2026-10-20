# fix-qualifier-use-site-target

## Why

Issue #220 (one entry of the aggregate #65): one Kotlin source applies an injection qualifier to an injected
property without a use-site target, and Kotlin warns that the meaning of the site is provisional.

Verified on HEAD `3211d4d` on 2026-10-20 by compiling both `:app` flavors' main source set with warnings on —
`./gradlew :app:compileMobileDebugKotlin :app:compileAutomotiveDebugKotlin --rerun-tasks --no-build-cache
-Pkotlin.incremental=false` (log `evidence/head-compile-warnings.log`):

```
w: …/app/src/main/java/com/naviveylin/navigation/NavigationNotificationController.kt:36:5
   This annotation is currently applied to the value parameter only, but in the future it will also be
   applied to field.
- To opt in to applying to both value parameter and field, add '-Xannotation-default-target=param-property'
  to your compiler arguments.
- To keep applying to the value parameter only, use the '@param:' annotation target.
```

The warning is printed twice in that log, once from `:app:compileMobileDebugKotlin` and once from
`:app:compileAutomotiveDebugKotlin`: `:app`'s two flavors compile the same `app/src/main/java` tree, so one
source site yields the item's "2 warnings in one compile" — the same arithmetic #219 recorded for the test
source sets. The eight remaining qualifier sites in the tree apply a qualifier to a *value parameter that
declares no property* — seven of them function parameters (`AppModule.provideViewportStorage(@ApplicationContext
context: …)` and its siblings in `MapDownloadModule`, `LicenseModule`) and one a constructor parameter
(`AddressBookRationaleStore.kt:15`, `@ApplicationContext context: Context` with no `val`): such a parameter
has no backing field of its own, so nothing is ambiguous and the qualified form is already the only meaning.

**Root cause, one sentence:** `NavigationNotificationController`'s constructor writes
`@ApplicationContext private val context: Context`, and a qualifier declared with `PARAMETER` and `FIELD`
targets applied to a constructor property without a use-site target is applied to the value parameter today
while a later Kotlin default would also apply it to the field — the twelve other injected-property sites in
the tree already say `@param:ApplicationContext`, so this one is a leftover of the pattern, not a decision.

**Red test** — `QualifierUseSiteTargetTest` (`:app` unit tests, file
`app/src/test/java/com/naviveylin/build/QualifierUseSiteTargetTest.kt`), run with
`./gradlew :app:testMobileDebugUnitTest --tests 'com.naviveylin.build.QualifierUseSiteTargetTest'
-PforceTests --no-build-cache`. The red state is produced by the revert check, which restores HEAD's exact
bare line (`evidence/revert-checked.md`, `evidence/red-run-round2.log`):

- `no qualifier on an injected property relies on the implicit target` — fails, `expected:<[]> but
  was:<[:36: @ApplicationContext private val context: Context, in
  app/src/main/java/com/naviveylin/navigation/NavigationNotificationController.kt]>`, with
  `sources=719 findings=1` in `system-out`
- `the implicit target is still detectable` — passes in the red run: it falsifies the detector, not the tree
- XML: `evidence/red-run-round2.xml`, **tests=2 failures=1**, timestamp `2026-10-20T23:02:07.005Z`. The same
  two cases on the committed tree: `evidence/green-run-round2.xml`, **tests=2 failures=0**,
  `sources=719 findings=0`, `2026-10-20T23:03:45.120Z`.

**The annotation still has the intended effect** — measured, not assumed: the compiled class is byte-identical
in the two states. `:app:compileMobileDebugKotlin` was compiled from the bare form and then from the
`@param:` form, and each capture records the source line at the moment the classfile was taken
(`evidence/classfile-state-bare.txt`, `evidence/classfile-state-param.txt`): two different compilations —
classfile `Last modified` `22:56:32` and `22:56:50` — produced the same 6225 bytes and the same SHA-256
`cd6edefe2556e83d67f8ba34d6304456cfc1760399ae391a4eb614fb01db90b5` (`evidence/classfile-bare.class`,
`evidence/classfile-param.class`), so `diff` of the two `javap -v -p` dumps is empty
(`evidence/javap-diff-round2.txt`, 0 bytes) and both carry
`RuntimeVisibleParameterAnnotations: parameter 0: dagger.hilt.android.qualifiers.ApplicationContext`
(`evidence/javap-constructor-bare-round2.txt`, `evidence/javap-constructor-param-round2.txt`). The compiler's
own second suggestion says as much: "To keep applying to the value parameter only, use the '@param:'
annotation target" — the change makes the current meaning explicit instead of choosing a new one.

**Owning capability:** `build-test-gate` — the requirement this change adds is "An injected property states
its qualifier's use-site target", the source-set half of the gate's result evaluation. **Owning guideline
section:** `guidelines/Build.md` §4 "Result evaluation", whose bullet reads "Warnings: the build must have no
warnings — report them, don't ignore" (routed by `AGENTS.md`'s Documentation Map: "build, run, force a rerun,
read a gate result → `Build.md` §2, §3, §4").

## What changes

- `app/src/main/java/com/naviveylin/navigation/NavigationNotificationController.kt:36`:
  `@ApplicationContext` → `@param:ApplicationContext`.
- `app/src/test/java/com/naviveylin/build/QualifierUseSiteTargetTest.kt`: the guard — two cases, the first
  asserting that no source of the six Kotlin source sets the gate compiles (`app`, `auto` and `core`, `main`
  and `test`) applies `@ApplicationContext`/`@ActivityContext`/`@Named` to a `val`/`var` declaration without a
  use-site target (`sources=719`), the second asserting the detector still refuses that form and accepts the
  targeted form, a bare method parameter and a comment. Two properties of the guard are part of the fix, not
  incidental:
  - it walks the six source-set roots directly instead of pruning a repository walk by directory name, because
    the project's source-scan guards live in the package `com.naviveylin.build` — a name-based `build` prune
    drops that whole directory, which holds this file and its two siblings (the six roots hold 719 Kotlin
    files, `evidence/probe-guard-surface.txt`; the pruned walk reported `sources=716`, and 719 − 3 = 716 is
    that package);
  - its own fixtures are assembled from parts (`"    @" + "ApplicationContext private val …"`), because the
    file is inside the surface it scans: written whole, its three bare fixtures are findings of the guard they
    feed.

  The libosmscout submodule's own Kotlin module is outside the six roots on purpose: the project never patches
  the submodule, so a finding there would be a guard whose fix is forbidden.
- `guidelines/Build.md` §4: the warning bullet records the measured site, the post-fix compile and the guard
  that holds the absence.

No production behaviour change: the compiled class is byte-identical. No build script, no dependency, no
manifest, no native source, no test whose assertions move. Rollback: restore the bare annotation — the guard's
first case fails and names the file, so the regression is visible rather than silent.
