## Root cause

`app/src/main/java/com/naviveylin/ui/map/LocationMarkerOverlay.kt:165` builds the marker's rounded tip with

```kotlin
path.quadraticBezierTo(0f, -hPx, x1 * hPx, y1 * hPx)
```

`androidx.compose.ui.graphics.Path.quadraticBezierTo` is deprecated in the pinned Compose ui 1.7.6
(`compose-bom:2024.12.01`, `app/build.gradle.kts:1484`) with "Use quadraticTo() for consistency with cubicTo()":
the `Bezier` infix was never carried by `cubicTo`, so the quadratic spelling is the odd one. The replacement
takes the same `(x1, y1, x2, y2)` control and end points. A later Compose release removes the deprecated name,
at which point this line stops compiling — and nothing in this build would catch it earlier: no warning is an
error, and the compile that measured it prints 28 `w:` lines of its own (`evidence/head-red-run.log`), the
post-fix gate's 53 — nearly all opt-in notices in `:auto` test sources (`evidence/gate.log`).

One mechanism, not two. The incremental compile of the fix re-runs only the edited file and prints no other
deprecation for it (`evidence/fixed-focused.log`; a clean `:app` main-source compile still prints
`NavigationNotificationController.kt:36:5`, `evidence/head-red-run.log:142`), and the project's own
`*.kt` tree carries exactly one deprecated quadratic spelling — measured by the new scan: `files=731
findings=1` (`evidence/TEST-ComposeApiDeprecationTest-red.xml`).

## The single fix

Rename the call to the kept spelling:

```kotlin
path.quadraticTo(0f, -hPx, x1 * hPx, y1 * hPx)
```

Same control point, same end point, same `Path` — the drawn outline is unchanged, so nothing in
`gps-location-marker`'s geometry requirements moves and no pixel measurement is owed. The rest of the change is
the guard the delta needs: an `:app` unit-test case that scans every `*.kt` the project owns for both
deprecated spellings and prints `files=`/`findings=`, plus its detectability case, and the paragraph in
`guidelines/Build.md` §7 that records this family beside the Gradle one.

## Blast radius

One line of `:app` main source (`LocationMarkerOverlay.kt`), one new test file
(`app/src/test/java/com/naviveylin/build/ComposeApiDeprecationTest.kt`), `guidelines/Build.md` §7 (one
paragraph), `openspec/specs/build-jvm-toolchain/spec.md` (one requirement and one Purpose clause). No native
code, no manifest, no resource, no dependency, no release version state, no CI workflow: the guard runs inside
the gate's existing `:app` unit-test step.

## What the evidence eliminated

- **Turn Kotlin warnings into errors (`allWarningsAsErrors = true`, or `-Werror` on the Kotlin compiles).**
  Eliminated by measurement: the gate's compile log carries 53 `w:` lines and the guard's own red run 28 —
  mostly `@ExperimentalCoroutinesApi` opt-in notices in `:auto` test sources, plus
  `Condition is always 'true'` and a Java `field defaults` deprecation. Flipping the flag fails the suite on
  warnings that have nothing to do with this item, so it is not one fix and it would trade a silent warning for
  a broken gate.
- **Leave the call site and wait for the removal to fail the build.** Eliminated: the deprecation is the item,
  the fix is behaviour-identical, and waiting buys a compile error at an unrelated dependency bump — with no
  case that would have caught it. A deferred removal is also the reversal of nothing recorded, so it needs no
  owner decision to refuse.
- **Rely on the compiler warning as the guard (change the call, add no case).** Eliminated: the delta would
  then be unenforceable and the next contributor could reintroduce the
  spelling with nothing failing. One warning among the compile's 53 is not read, so the scan is the only
  host-decidable form of the absence.
- **A Gradle `check` task that greps the sources, wired beside `checkHardcodedStrings`.** Eliminated twice
  over: each new script-level `DefaultTask` registration is one more of the configuration-cache problems
  `guidelines/Build.md` §7 records, and the `:app` unit test already runs in the gate and in CI's test step
  with no new wiring. The same reasoning `GradleScriptDeprecationTest` records.
- **Assert the deprecation from the compile output instead of from the source.** Eliminated: a JVM unit test
  cannot observe another compilation's stderr, which is why the existing deprecation guard reads repository
  files rather than the build log. The source scan measures the same property (`the spelling is absent`) from
  the artifact the compiler reads.

Honest cost of the fix: the scan reads 731 small `.kt` files once per `:app` suite — measured 0.48 s for the
failing case in the red run, inside the fork budget the module suite already declares.

## Verification

- The red case: `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.build.ComposeApiDeprecationTest"
  -PforceTests --no-build-cache` on HEAD → `tests=2 failures=1`, `files=731 findings=1`, and the log's own
  `w:` line for `LocationMarkerOverlay.kt:165`.
- The green case, the affected cases and the gate once: `./gradlew test -PforceTests --no-build-cache`.
- The compile line is gone: the fixed focused log contains no `w:` line for `LocationMarkerOverlay.kt`.
- No device check: the change touches no pixel-producing geometry (the curve's control points are identical),
  so the phone/car marker pixel measurements of `gps-location-marker` are unaffected.

## Rollback

Revert the commit. The deprecated spelling returns, every build works again on Compose ui 1.7.6, the warning
comes back, and the guard case goes red — the intended signal that the rename is owed, not damage. No state to
undo: nothing is written outside the repository and no dependency, task or output moves.
