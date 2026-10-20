# fix-deprecated-quadratic-path-builder

## Why

Every `:app` compile of this tree prints, measured on base `3c1ecc78` (2026-10-20T13:20:05Z):

```
w: file:///home/tim/projects/NaviVeylin/.pi/worktrees/loop/app/src/main/java/com/naviveylin/ui/map/LocationMarkerOverlay.kt:165:14 'fun quadraticBezierTo(x1: Float, y1: Float, x2: Float, y2: Float): Unit' is deprecated. Use quadraticTo() for consistency with cubicTo().
```

**Root cause.** The location marker builds its rounded tip with the deprecated `Path.quadraticBezierTo`
(`app/src/main/java/com/naviveylin/ui/map/LocationMarkerOverlay.kt:165`, one call site in the project's own
Kotlin sources): the pinned Compose ui 1.7.6 deprecates that spelling in favour of `quadraticTo`, which takes
the same control and end points, and a later release removes it — so a call site nobody reads becomes a compile
error at the next dependency bump. Nothing in this build turns the warning into a failure: the gate's compile
log carries 109 `w:` lines on HEAD, most of them opt-in notices in `:auto` test sources.

**Red test on HEAD** (`3c1ecc78`), added as `app: ComposeApiDeprecationTest` "no project Kotlin source calls a
deprecated quadratic path builder": a scan of every `*.kt` the project owns, asserting zero deprecated
spellings (2026-10-20T13:20:05.766Z, JUnit XML `tests=2 failures=1`,
`evidence/TEST-ComposeApiDeprecationTest-red.xml`):

```
java.lang.AssertionError: a Kotlin source that builds a quadratic Bézier with `quadraticBezierTo`/
`relativeQuadraticBezierTo` must use `quadraticTo`/`relativeQuadraticTo` instead …
expected:<[]> but was:<[app/src/main/java/com/naviveylin/ui/map/LocationMarkerOverlay.kt:165:
path.quadraticBezierTo(0f, -hPx, x1 * hPx, y1 * hPx)]>
```

The same run prints the deprecation it is about (`files=731 findings=1`, log
`evidence/head-red-run.log`). The case is the absence the delta requires: one finding before, zero after. Its
second case, "the scan is still detectable", keeps the guard non-vacuous by asserting the scan *does* report
each deprecated spelling and does *not* report the kept spelling or a comment that names the deprecated one.

**Owning capability:** `build-jvm-toolchain` — the capability that already holds "The build's scripts use no
construct Gradle 10 removes"; this is the same rule for the Kotlin sources the same upgrade compiles.
**Owning guideline section:** `guidelines/Build.md` §7, its "Gradle deprecation baseline" paragraph, which
records every deprecation family of this build and the guard that keeps it out.

## What Changes

- `app/src/main/java/com/naviveylin/ui/map/LocationMarkerOverlay.kt:165`: the rounded tip is built with
  `quadraticTo(0f, -hPx, x1 * hPx, y1 * hPx)` — the same control point and the same end point, so the drawn
  outline is unchanged.
- `app/src/test/java/com/naviveylin/build/ComposeApiDeprecationTest.kt`: the two cases above.
- `guidelines/Build.md` §7: the Kotlin-side deprecation baseline recorded beside the Gradle one — the one
  project-owned call site is gone (with the compile line that measured it), no warning-as-error flag is set
  and why, and the guard case named.
- `openspec/specs/build-jvm-toolchain/spec.md`: the capability's Purpose gains the Kotlin-source half of the
  same rule, so the new requirement is not wider than the capability that carries it.

## Spec

`openspec/specs/build-jvm-toolchain/spec.md`, ADDED requirement "The project's own Kotlin sources call no
deprecated quadratic path builder", with two scenarios that each name one case of `ComposeApiDeprecationTest`.

## Cases and evidence

- `app: ComposeApiDeprecationTest` "no project Kotlin source calls a deprecated quadratic path builder" — red on
  HEAD (one finding), green after the rename.
- `app: ComposeApiDeprecationTest` "the scan is still detectable" — green on HEAD and after; the mutation-shaped
  half of the guard.
- `./gradlew :app:testMobileDebugUnitTest --tests … -PforceTests --no-build-cache` — the compile prints no
  `w:` line for `LocationMarkerOverlay.kt` after the rename, against the red run's line
  (`evidence/head-red-run.log`, `evidence/fixed-focused.log`).
- The documented gate: `./gradlew test -PforceTests --no-build-cache` (`evidence/`).

## Impact

One line of `:app` source, one new test file, one guideline paragraph, one capability spec requirement and its
Purpose clause. No native code, no manifest, no dependency, no release version state, no CI workflow: the guard
is a unit-test case, so the gate and CI's existing test step run it without new wiring. Additive: no public
surface, no behaviour change at build-output level — the same curve at the same points under the name the
pinned release keeps.

Rollback: revert the commit. The deprecated spelling returns, every build works again on Compose ui 1.7.6 and
prints the warning again, and the guard case goes red — the intended signal, not damage.
