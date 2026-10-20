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
   proves it: identical below the classfile header, `RuntimeVisibleParameterAnnotations: parameter 0:
   dagger.hilt.android.qualifiers.ApplicationContext` in both states
   (`evidence/javap-diff.txt`, `evidence/javap-constructor-{bare,param}.txt`).
2. `app/src/test/java/com/naviveylin/build/QualifierUseSiteTargetTest.kt` — the guard:
   - `no qualifier on an injected property relies on the implicit target` walks the six Kotlin source sets of
     `app`, `auto` and `core` and asserts the finding list is empty. Before the fix it holds exactly one
     finding, the controller's line 36 (measured: `sources=716 findings=1`).
   - `the implicit target is still detectable` runs the same detector over eight literal lines — three bare
     property sites it must name, and `@param:`, `@field:`, a bare *method* parameter, a comment and a
     constructor with no qualified parameter it must leave alone. Without it a scan that read nothing would
     pass, which is the vacuous green #219's review refused.
3. `guidelines/Build.md` §4 — one sentence in the warnings bullet: the site, the count, the guard's name.

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
  flavors. The classfile diff above is the measurement the chosen form is free.
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
`private|internal|public|protected`, so a `lateinit`/`override` property carrying a qualifier would not be
matched — a class-body site of that shape draws Kotlin's *field*-to-property message, not the value-parameter
one this requirement is about, and the tree has no such site: on HEAD
`probe-qualifier-targets.py` (in the item's evidence directory) reported
`findings_whole_file == findings_in_constructor == 1`, so no qualifier sits on a property outside a
constructor. The pattern is per line, so an annotation whose arguments span lines is invisible to it — no
qualifier in the tree does that.
