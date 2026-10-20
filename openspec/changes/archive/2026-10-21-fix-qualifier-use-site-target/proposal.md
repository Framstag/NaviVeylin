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
source sets. The eight remaining qualifier sites in the tree apply a qualifier to a Kotlin declaration without
warning, because all eight are *method parameters* (`AppModule.provideViewportStorage(@ApplicationContext
context: …)` and its siblings, `MapDownloadModule`, `LicenseModule`, `AddressBookRationaleStore`): a method
parameter has no backing property of its own, so nothing is ambiguous and the qualified form is already the
only meaning.

**Root cause, one sentence:** `NavigationNotificationController`'s constructor writes
`@ApplicationContext private val context: Context`, and a qualifier declared with `PARAMETER` and `FIELD`
targets applied to a constructor property without a use-site target is applied to the value parameter today
while a later Kotlin default would also apply it to the field — the twelve other injected-property sites in
the tree already say `@param:ApplicationContext`, so this one is a leftover of the pattern, not a decision.

**Red test on HEAD** — `QualifierUseSiteTargetTest` (`:app` unit tests, file
`app/src/test/java/com/naviveylin/build/QualifierUseSiteTargetTest.kt`), run with
`./gradlew :app:testMobileDebugUnitTest --tests 'com.naviveylin.build.QualifierUseSiteTargetTest'
-PforceTests --no-build-cache` (log `evidence/head-red-run.log`, `2 tests completed, 1 failed`):

- `no qualifier on an injected property relies on the implicit target` — fails, `expected:<[]> but
  was:<[:36: @ApplicationContext private val context: Context, in
  app/src/main/java/com/naviveylin/navigation/NavigationNotificationController.kt]>`, with
  `sources=716 findings=1` in `system-out`
- `the implicit target is still detectable` — passes on HEAD: it falsifies the detector, not the tree
- XML: `evidence/revert-check-mutated.xml` (`evidence/red-run-final.xml` is the same file), **tests=2
  failures=1**, timestamp `2026-10-20T22:46:46.741Z` — the revert check's mutation restores the exact HEAD
  line, so that run is the red run on the final revision of the guard (an earlier red run, whose XML was
  overwritten before it was copied, measured the same one-finding tree with the wider scan surface described
  under "What changes"; its console log is `evidence/head-red-run.log`).

**The annotation still has the intended effect** — measured, not assumed: the compiled class is unchanged.
`javap -v -p` on
`app/build/intermediates/built_in_kotlinc/mobileDebug/compileMobileDebugKotlin/classes/com/naviveylin/navigation/NavigationNotificationController.class`
in the bare state and in the `@param:` state differs only in the classfile header (`Last modified`,
`SHA-256 checksum`); `diff` of everything after the header is empty (`evidence/javap-diff.txt`), and both
states carry `RuntimeVisibleParameterAnnotations: parameter 0: dagger.hilt.android.qualifiers.ApplicationContext`
(`evidence/javap-constructor-bare.txt`, `evidence/javap-constructor-param.txt`). The compiler's own second
suggestion says as much: "To keep applying to the value parameter only, use the '@param:' annotation target" —
the change makes the current meaning explicit instead of choosing a new one.

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
  use-site target (`sources=716`), the second asserting the detector still refuses that form and accepts the
  targeted form, a bare method parameter and a comment. The libosmscout submodule's own Kotlin module is
  outside the scan on purpose: the project never patches the submodule, so a finding there would be a guard
  whose fix is forbidden.
- `guidelines/Build.md` §4: the warning bullet records the measured site and names the guard that holds the
  absence.

No production behaviour change: the compiled class is byte-identical below its header. No build script, no
dependency, no manifest, no native source, no test whose assertions move. Rollback: restore the bare
annotation — the guard's first case fails and names the file, so the regression is visible rather than silent.
