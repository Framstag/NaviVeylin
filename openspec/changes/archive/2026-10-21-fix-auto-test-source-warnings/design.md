# Design

## Root cause

The `:auto` unit-test source set is compiled by the gate, but nothing holds it to the warning-free rule
`guidelines/Build.md` §4 states, so three constructs whose value the compiler can prove at the call site
survive in it — at `RendererTestRule.kt:64`, `RenderLoopSupervisorTest.kt:40` and `TestCarContext.kt:26`.

## The one fix

Name the type the source means, and hold the set to the rule with one scan:

| site | before | after |
|---|---|---|
| `RendererTestRule.track` | `fun <T : AutoMapRenderer> track(renderer: T): T` | `fun track(renderer: AutoMapRenderer): AutoMapRenderer` |
| `RenderLoopSupervisorTest.boom` | `runIteration(loop) { error(…) } == null` (block infers `Nothing`) | `runIteration<Unit>(loop) { error(…) } == null` |
| `TestCarContext` format args | `args[1] as Array<Any?>` (unchecked) | `(args.getOrNull(1) as? Array<*>)?.map { it }?.toTypedArray() ?: emptyArray()` |
| guard | — | `app/src/test/java/com/naviveylin/build/AutoTestSourceWarningBaselineTest.kt`, three cases |

No call site changes: every `track(…)` caller passes an `AutoMapRenderer` (including
`spyk(AutoMapRenderer(…))`, whose type is `AutoMapRenderer`), and `boom`'s return value keeps its meaning
(`true` = the supervisor swallowed the fault and the iteration produced a null result).

## Blast radius

Three `:auto` test files and one new `:app` test class. No main source, no production behaviour, no
manifest, no build script: the change cannot alter what the app does. The strings `TestCarContext` resolves
are unchanged — the copy through `map { it }` produces the same `Array<Any?>` the unchecked cast produced.

## Rollback

Revert the four files; the three diagnostics return and the guard's three cases go red again, which is the
red state recorded in `evidence/`.

## Alternatives the evidence eliminated

- **`@Suppress("UNCHECKED_CAST")` / `@Suppress("SENSELESS_COMPARISON")` on the call sites** — eliminated by
  the rule itself: `Build.md` §4 requires the warning to be reported, and a suppression keeps the construct
  invisible to every future reader and every future gate run.
- **Deleting `boom`'s `== null` comparison** — eliminated by the test's intent: the comparison is what
  asserts the fault was swallowed, so removing it would delete the assertion rather than the warning.
- **Keeping `args.size > 1` and widening only the cast** — eliminated by measurement: the guard's positive
  control shows a checked `as?` cast is the whole fix, and `getOrNull` carries the size test.
- **Enabling `allWarningsAsErrors` for the `:auto` test compilation instead of a scan** — eliminated by the
  measured warning set: the set still carries 49 warnings from the opt-in family and one deprecated field
  (issue #218/#219's families), 50 in total, so the build would fail on unrelated sites today.
- **Placing the guard in `auto/src/test`** — eliminated by the reading path: an `:auto` test's working
  directory is that module, so the main-source roots the bound rule resolves against (`app/src/main/java`,
  `core/src/main/java`) would be `../../` climbs, while the `:app` build-rule package reads the repository as
  `File("..")` the way `GradleScriptDeprecationTest` already does.
