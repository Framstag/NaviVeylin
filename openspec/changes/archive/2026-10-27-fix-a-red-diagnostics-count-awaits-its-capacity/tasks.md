# Tasks — a red diagnostics count is read once the screen has measured its page

## 1. The red run on the base tree

- [x] 1.1 Verify the item against the tree: the CI run it names (`gh run view --job 121789440446 --log`,
      `880 tests completed, 3 failed`) and the code it blames — `DiagnosticsScreen.kt:58` (capacity starts at
      the library default), `:62-63` (the capacity coroutine starts after the entry load), `:126` (the page is
      built from that field), `CarListCapacityReader.kt:76` (the reader logs its own line into the window's
      file). Verified, and not fixed on the base commit.
- [x] 1.2 Reproduce the two conditions the CI run met, with the assertions untouched: the fake host's answer
      held back (`answerGate.await()` in the capacity stub of `DiagnosticsScreenTest`) and one diagnostics
      line logged after `StartupScreensTest`'s three entries. Verification:
      `11 tests completed, 3 failed` — `DiagnosticsScreenTest` `XML tests=5 failures=2
      (2026-10-27T05:57:08.201Z)` with `expected:<10> but was:<8>` twice, `StartupScreensTest` `XML tests=6
      failures=1 (2026-10-27T05:57:16.698Z)` with `expected:<4> but was:<5>`
      (`evidence/red-on-head/`, `evidence/red-command.log`).
- [x] 1.3 Count the surface before the plan was fixed: 4 test classes constructing a `DiagnosticsScreen`,
      8 cases, 2 total-count assertions, 57 existing scenarios in `unit-test-suite-runtime` (commands in the
      proposal's `surface:` line).

## 2. The change

- [x] 2.1 `DiagnosticsScreenTest`: both counts await the page the host's capacity builds — `awaitRows(...) {
      titles.size == 1 + hostCapacity + 1 }` in `theDiagnosticsListFitsTheHostsCapacity` and in both awaits of
      `thePageRowOpensTheNextSliceOfTheWindow`.
- [x] 2.2 `DiagnosticsScreenTest`: every screen a case builds and every page its page row opens is released
      in teardown (`openScreen`, `release`, `@After`), the lifecycle sequence being `ON_CREATE`, `ON_START`,
      `ON_DESTROY`.
- [x] 2.3 `StartupScreensTest.diagnosticsScreenShowsNewestFirst`: await the case's own three entries, assert
      the disclosure first and the three entries newest-first over the rows that carry them, keep the logged
      line as the reproduction, and release the screen.
- [x] 2.4 `GermanRenderingTest` and `TemplateFaultIsolationTest`: release the screen each case builds.

## 3. Verification

- [x] 3.1 The same two reproductions, in place, on the change: `DiagnosticsScreenTest` `XML tests=5
      failures=0 (2026-10-27T05:58:45.814Z)`, `StartupScreensTest` `XML tests=6 failures=0
      (2026-10-27T05:58:56.862Z)` (`evidence/green-on-change/`).
- [x] 3.2 The module suite the red `test` job failed: `91 classes, 880 tests, 0 failures, 0 errors`
      (`evidence/auto-module-tally.txt`).
- [x] 3.3 The project's gate, once: `./tools/loop/gate.sh` (`evidence/gate.log`).
- [x] 3.4 `bash tools/loop/evidence-check.sh openspec/changes/2026-10-27-fix-a-red-diagnostics-count-awaits-its-capacity --root .`
      resolves every pointer (`evidence/evidence-check.log`).
- [x] 3.5 Archived under `openspec/changes/archive/`, the requirement merged into
      `openspec/specs/unit-test-suite-runtime/spec.md`.
