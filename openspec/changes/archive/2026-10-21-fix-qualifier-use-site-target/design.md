# Design — a qualifier on an injected property names its use-site target

## Root cause

`NavigationNotificationController` (`app/src/main/java/com/naviveylin/navigation/NavigationNotificationController.kt:36`)
declares its constructor dependency as

```kotlin
@ApplicationContext private val context: Context,
```

`@ApplicationContext` is a `javax.inject.Qualifier`-annotated Java annotation whose targets include both
`PARAMETER` and `FIELD`. Applied to a constructor property with no use-site target, Kotlin applies it to the
value parameter today and warns that a later default will also apply it to the field, offering two escapes:
opt into both targets with `-Xannotation-default-target=param-property`, or state the target that is meant
today. The rest of the tree already states it: twelve other injected properties in `:app` write
`@param:ApplicationContext`, so the one bare site is a leftover of the pattern, not a decision (measured:
`grep -rn "@param:ApplicationContext private val" app/src/main auto/src/main core/src/main` → 13 after this
change, 12 before it, plus the bare one).

One mechanism, not two. The same compile prints a second warning family from a different file
(`LocationMarkerOverlay.kt:165`, `quadraticBezierTo` is deprecated), which no qualifier and no use-site target
touches; it belongs to its own item under aggregate #65.

## The single fix

Two edits and one case:

1. `NavigationNotificationController.kt:36` — `@ApplicationContext` → `@param:ApplicationContext`. The
   compiler's own second suggestion names this as the form that keeps today's meaning, and the compiled class
   proves it: two compilations of that class, one from each form, produced the same 6225 bytes and the same
   SHA-256 (`evidence/classfile-bare.class`, `evidence/classfile-param.class`), so their `javap -v -p` dumps
   are identical, `RuntimeVisibleParameterAnnotations: parameter 0:
   dagger.hilt.android.qualifiers.ApplicationContext` included (`evidence/javap-diff-round2.txt`).
2. `app/src/test/java/com/naviveylin/build/QualifierUseSiteTargetTest.kt` — the guard:
   - `no qualifier on an injected property relies on the implicit target` walks the six Kotlin source sets of
     `app`, `auto` and `core` and asserts the finding list is empty. Before the fix it holds exactly one
     finding, the controller's line 36 (measured: `sources=719 findings=1`).
   - `the implicit target is still detectable` runs the same detector over eight literal lines — three bare
     property sites it must name, and `@param:`, `@field:`, a bare *method* parameter, a comment and a
     constructor with no qualified parameter it must leave alone. Without it a scan that read nothing would
     pass, which is the vacuous green #219's review refused.
3. `guidelines/Build.md` §4 — one sentence in the warnings bullet: the site, the count, the guard's name.

Two properties of the guard are part of the fix, because the first round got both wrong:

- **The surface is the six roots, walked directly.** The first version walked the repository and pruned every
  directory named `build`, `.git`, `.gradle`, `.idea`, `.pi`, `node_modules`. The project's source-scan guards
  live in the package `com.naviveylin.build`, so the `build` prune silently excluded this file,
  `GradleScriptDeprecationTest` and `KoverDeprecationBaselineTest`: 716 files scanned of 719 in the six roots
  (`evidence/probe-guard-surface.txt` measured 719 for the root walk). A guard that cannot see the package it
  lives in is the vacuous green it exists to prevent, so the walk now starts at `SCANNED_SOURCE_SETS` and the
  case asserts each root exists (a lost root would otherwise shrink the surface silently).
- **The fixtures are assembled from parts.** With the surface restored the file is inside its own scan, and a
  fixture written whole (`"@ApplicationContext private val context: Context,"`) is a finding of the guard it
  feeds — three of them, which is what the first review measured. Each fixture therefore concatenates the
  qualifier off the rest (`"    @" + "ApplicationContext private val context: Context,"`), so the source text
  cannot match while the value it produces still does. The same reason keeps the two probe scripts out of the
  guard: a probe is evidence, not a fixture.

## Blast radius

One production line, one new test file, one guideline sentence, the spec delta. Nothing else: no build
script, no dependency, no manifest, no native source, no threading, lifecycle or DI-graph change. The
qualifier still lands on the value parameter (byte-identical class), so no injection site moves.

## Rollback

Revert the change commit. The requirement is ADDED, so removing it restores the previous contract with no
compensating delta; restoring the bare annotation alone makes the guard's first case fail and name the file.

## What the evidence eliminated

- **`-Xannotation-default-target=param-property` in the compiler arguments.** Eliminated by the compiler's
  own message: that flag makes every unqualified annotation apply to *both* the value parameter and the
  field, which is the future default this change refuses — a project-wide semantic change to silence one
  site, and it would change annotation placement in the classfile for every unqualified annotation in both
  flavors. The two-state classfile measurement above is what shows the chosen form is free.
- **A project-wide `allWarningsAsErrors` (or a per-diagnostic error).** Eliminated by the same compile's other
  warnings — the `:app` main compile still prints `LocationMarkerOverlay.kt:165`'s deprecation, and the four
  unit-test task families print the ones #218/#219 own — so error-ing this diagnostic means error-ing
  families this change does not fix, or maintaining a per-diagnostic allowlist that the next Kotlin renames.
- **Suppressing the warning on the file.** Eliminated because the item's premise is that the site *has* an
  unambiguous meaning; suppressing leaves the meaning unstated, and the fix is one annotation prefix, not a
  re-specification.
- **Scanning on the annotation's declared targets (reflect over the jars) instead of a qualifier name set.**
  Eliminated as a guard a unit test cannot hold without loading Hilt's and jsr330's annotation classes and
  duplicating `kotlin.annotation.AnnotationTarget` mapping, for a family the project applies with exactly
  three names. The name set is monotone in the safe direction: a new qualifier name in the tree is caught by
  review of this list, while every name already on it stays covered.

## Why the guard is a source scan

No host test can configure the module it runs inside, so no case can assert "the compiler emitted no
`value parameter only` warning" — the same bound `GradleScriptDeprecationTest` and #219's guard state. The
case therefore asserts the condition that warning is a function of: the bare form is absent from the sources.
It reads `//` line comments out (`a comment that names the refused form` is a fixture case), and it matches
`@Qualifier … [modifiers] val|var`, so a targeted annotation (`@param:`) never matches — the use-site target is
lowercase and the qualifier name begins uppercase, which is what the `@param:`/`@field:` fixture lines pin.

What it cannot see, and why that is enough here: the modifier list the pattern accepts is
`private|internal|public|protected`, so a `lateinit` or `override` property carrying a qualifier would not be
matched — a class-body site of that shape would draw Kotlin's *field*-to-property message, not the
value-parameter one this requirement is about, and the tree has no such site: a wider probe over the same six
source sets, matching a qualifier applied to *any* `val`/`var` with any modifier list and skipping comment
lines, reports `scanned=719 code_findings=0` (`evidence/probe-property-qualifier-sites.txt`; the one comment
line it reports is this guard's own KDoc naming the refused form). The pattern is per line, so an annotation
whose arguments span lines is invisible to it — no qualifier in the tree does that.

## Round 2 — what the first review changed, and what it did not

The first review refused the change on one measured contradiction: the guard's own fixture lines are findings
of the pattern it scans for, so `findings=0` could not come from the committed source, and the recorded
`javap` dumps were the same classfile twice, so "unchanged" was unmeasured. Both were re-taken:

- **The fixtures.** The review's reading was right about the committed text and wrong about the run: the first
  guard's repository walk pruned the `com.naviveylin/build` package, so the file was never scanned and
  `findings=0` was genuinely what that run printed — a green that held because the surface was wrong, which is
  the more serious of the two defects. Fixing the surface alone would have made the guard red on its own
  tree; the fixtures are assembled from parts in the same pass. Red and green were re-run on the final source
  (`evidence/red-run-round2.xml`, `evidence/green-run-round2.xml`), each `sources=719`, `findings=1`/`0`.
- **The classfile.** The first pair of dumps differed in nothing, header included, because both were taken from
  one state. The re-taken measurement compiles the class from the bare form and then from the `@param:` form,
  records the source line at each capture, and keeps both classfiles: same size, same SHA-256, dumps identical
  (`evidence/classfile-state-{bare,param}.txt`, `evidence/classfile-bare.class`,
  `evidence/classfile-param.class`, `evidence/javap-diff-round2.txt`).

What the review did not change: the production fix (still one line), the requirement, the guideline section,
and the eliminated alternatives. Three notes were also applied — the eight remaining qualifier sites are value
parameters that declare no property (seven function parameters, one constructor parameter at
`AddressBookRationaleStore.kt:15`) rather than "method parameters"; the first scenario's `THEN` now stops at
what the case asserts instead of promising a compiler log; and the pointer to the change's own delta is the
archive path.
