# fix-the-scan-skips-sources-under-a-directory-named-build

## Why

Issue #223 asks for the guard the refused change `2026-10-20-fix-deprecated-quadratic-path-builder` left
half-done: the promise it states is wider than the files it reads. The deprecated call itself is already gone
from the tree — `app/src/main/java/com/naviveylin/ui/map/LocationMarkerOverlay.kt:165` reads
`path.quadraticTo(0f, -hPx, x1 * hPx, y1 * hPx)` — and the guard exists
(`app/src/test/java/com/naviveylin/build/ComposeApiDeprecationTest.kt`). The refusal of 2026-10-20
(`openspec/changes/archive/2026-10-20-fix-deprecated-quadratic-path-builder/evidence/review-failed-round-2.md`)
still stands on base `3f972aec`, because the guard's walk prunes **by directory name**.

**Root cause.** `app/src/test/java/com/naviveylin/build/ComposeApiDeprecationTest.kt:110` skipped every
directory named `build` (the `SKIPPED_TOOL_DIRECTORIES` set, then carrying `"build"`), and the project's own
`com.naviveylin.build` package directories are named exactly that — so the scan never read the 37 `.kt` files
they hold, its own guard file among them. The requirement it enforces
(`openspec/specs/build-jvm-toolchain/spec.md:108`) and the guideline paragraph that names it
(`guidelines/Build.md:1207`) both say "every `*.kt` the project owns".

**Red test on base `3f972aec`**, added as `app: ComposeApiDeprecationTest` "the scan reads the sources a
directory named build holds" — 2026-10-27T16:36:14.835Z, JUnit XML `tests=3 failures=1`
(`evidence/TEST-ComposeApiDeprecationTest-red.xml`):

```
java.lang.AssertionError: the scan must read every `*.kt` the project owns, including the sources a package
directory named `build` holds — a directory is Gradle output by the marker its project root writes, not by
its name (read 768, missing [app/src/test/java/com/naviveylin/build/ComposeApiDeprecationTest.kt,
buildSrc/src/main/kotlin/com/naviveylin/build/testing/BuildFlags.kt])
```

The same run measures the gap's size: the tree owns 805 `*.kt` files
(`find . -name '*.kt' | wc -l` → `805`), the scan reads 768, and the 37 it does not are all project sources
under a `com/naviveylin/build` package directory
(`find . -name '*.kt' | awk -F/ '{for(i=2;i<NF;i++) if($i=="build") {print; next}}' | wc -l` → `37`, none of
them under a Gradle output directory: `find app/build buildSrc/build build core/build auto/build -name '*.kt'
| wc -l` → `0`). So the promise is unearned for 37 files, and the paragraph's own count — "731 files"
(`guidelines/Build.md:1207`) — is a third number the tree does not carry.

**Owning capability:** `build-jvm-toolchain`, requirement "The project's own Kotlin sources call no
deprecated quadratic path builder" (`openspec/specs/build-jvm-toolchain/spec.md:105`), whose text already
promises the whole tree. **Owning guideline section:** `guidelines/Build.md`, the "Code coverage" section, its "Kotlin
deprecation baseline" paragraph (`guidelines/Build.md:1195`).

**Surface counted before the plan** — `grep -rn 'every `\*\.kt`' guidelines openspec/specs app/src` → 3 hits;
`grep -rln ComposeApiDeprecationTest guidelines openspec/specs app/src` → 3 files; `ls tools/*-cases.sh` → no
case names the guard:

surface: 1 case added, 2 cases extended, 2 documents realigned — `grep -rln ComposeApiDeprecationTest guidelines openspec/specs app/src` and `grep -rn 'every `*.kt`' guidelines openspec/specs app/src`

One case added, two cases extended (the first case's assertion message, the second case's probes because the
scan now reads the file they live in), two documents realigned (`guidelines/Build.md`, the "Code coverage"
section; `openspec/specs/build-jvm-toolchain/spec.md`), in the one file the scan is in. Nothing else asserts the
rule; `./tools/loop/measure.sh` prints the same `LOOP-MEASURE-SURFACE surface: 1 case added, 2 cases extended, 2
documents realigned`.

## What Changes

- `app/src/test/java/com/naviveylin/build/ComposeApiDeprecationTest.kt`: the walk prunes a `build` directory
  only when a Gradle project root writes into it (a `build.gradle.kts`/`build.gradle` in its parent), not by
  its name; the new case above holds that, and holds the prune half against a probe tree it builds itself —
  `build/` and `app/build/` beside a marker are pruned, `lib/build/` and the package directory
  `com/naviveylin/build/` are read. The case's own probe literals and its failure message are assembled from
  parts (`DEPRECATED = "quadratic" + "Bezier" + "To"`), because from now on the scan reads this file too and a
  source line here must not carry the spelling the case refuses.
- `guidelines/Build.md`, the "Code coverage" section: the paragraph's count becomes the number the scan prints (805 files), and it says
  what a `build` directory is pruned by.
- `openspec/specs/build-jvm-toolchain/spec.md`: the requirement gains the scenario "The sources a package
  directory named build holds are read", naming the new case.

## Spec

`openspec/specs/build-jvm-toolchain/spec.md`, MODIFIED requirement "The project's own Kotlin sources call no
deprecated quadratic path builder": its three scenarios each name one case of `ComposeApiDeprecationTest`.

## Cases and evidence

- `app: ComposeApiDeprecationTest` "the scan reads the sources a directory named build holds" — red on base
  `3f972aec` (`tests=3 failures=1`, `read 768`), green after the prune is fixed; its prune half is held against a
  probe tree the case builds, and one mutation — the `onEnter` without `isGradleOutput()` — reddens it
  (`tests=3 failures=1`, `2026-10-27T17:01:48.246Z`, `evidence/TEST-ComposeApiDeprecationTest-prune-red.xml`).
- `app: ComposeApiDeprecationTest` "no project Kotlin source calls a deprecated quadratic path builder" — green
  before and after; its `files=` count rises from 768 to 805 and its `findings=` stays 0, so the newly read
  sources carry no deprecated spelling.
- `app: ComposeApiDeprecationTest` "the scan is still detectable" — green before and after; the probes are
  assembled from parts, so the guard does not report itself.
- `./tools/loop/gate.sh` — the documented gate, once: exit 0, its own summary line reads
  `LOOP-GATE: tests=645 failures=0 errors=0` over its 23 suites, with each suite's time on the same line
  (`evidence/gate.log:1441`, `evidence/gate-summary.xml`, `evidence/gate-tallies/`).

## Impact

One test file (the guard), one guideline paragraph, one capability requirement scenario. No main source, no
native code, no dependency, no release version state, no CI workflow: the guard already runs inside the gate's
`:app` unit-test step.

Rollback: revert the commit. The scan returns to 768 files, the new case goes red, and the deprecated-call case
stays green — the promise is narrower than the one the spec states again, which is the intended signal, not
damage.
