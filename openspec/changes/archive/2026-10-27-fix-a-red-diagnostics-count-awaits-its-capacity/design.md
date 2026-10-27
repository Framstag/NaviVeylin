# Design — a red diagnostics count is read once the screen has measured its page

## The root cause

One mechanism, two effects of the same coroutine. `DiagnosticsScreen` publishes a page built from
`rowCapacity` (`auto/src/main/java/com/naviveylin/auto/DiagnosticsScreen.kt:126`), and that field is decided
twice: it starts at the Car App Library's default 6 (`:58`) and is replaced when `readRowCapacity()`
(`:81-88`), a coroutine on the screen's main-thread scope started **after** `loadEntries()`
(`:62-63`), returns from `Dispatchers.Default`. Its reader also writes one line into the same log file the
window is read from (`CarListCapacityReader.kt:76`).

The three failing cases each read a count that this coroutine still decides:

- `DiagnosticsScreenTest.theDiagnosticsListFitsTheHostsCapacity` and
  `DiagnosticsScreenTest.thePageRowOpensTheNextSliceOfTheWindow` stop waiting as soon as the entries are
  published, which is before the host's answer has replaced the default, so the page is `1 + 6 + 1 = 8` rows
  instead of `1 + 8 + 1 = 10`;
- `StartupScreensTest.diagnosticsScreenShowsNewestFirst` counts the window's total, and the screen's own
  measurement line — plus the same line from a screen an earlier case never released, since the scope dies
  only in `onDestroy` (`:47`, `:67-72`) — is in that file, so the total is 5 or more instead of 4.

## The single fix

Make each case own the state its count reads, and release the screen it starts:

1. The two counts await the page the host's capacity builds (`awaitRows(...) { titles.size == 1 + hostCapacity + 1 }`),
   which is the state they assert rather than a proxy of it.
2. The newest-first case awaits its own three entries and asserts the disclosure first and the three entries
   newest-first, over the rows that carry them — never over the window's total.
3. Every case that builds a `DiagnosticsScreen` releases it through the lifecycle sequence the host uses
   (`ON_CREATE`, `ON_START`, `ON_DESTROY`), so its scope is cancelled inside the case. `DESTROYED` cannot be
   reached from `INITIALIZED` in one step (`IllegalStateException: no event down from INITIALIZED`, measured),
   hence the three dispatches.

No production code changes: the screen's behaviour is what its capability requires — it presents at most
the host's capacity, and it corrects itself when the answer lands. What was wrong is that the cases read the
count before that.

## Blast radius

Four test classes (`grep -l "DiagnosticsScreen(" auto/src/test/java/com/naviveylin/auto/*.kt`), eight cases.
No production file, no fixture, no build script, no gradle property, no API, no ledger. The `:auto` suite is
the whole of it: `91 classes, 880 tests, 0 failures, 0 errors` after the change, against the red `test` job's
`880 tests completed, 3 failed`.

## Rollback

Revert the four test files and drop the added `unit-test-suite-runtime` requirement. Nothing else depends on
them; the tree returns to the base commit's behaviour, including the flake.

## Alternatives the evidence eliminated

- **Fix the screen instead — publish entries only after the capacity is known.** It would make the two counts
  structural, but it changes what the host sees (a `Loading` row where a bounded page is already correct) to
  repair a case that reads too early, and a case would then have to deadlock its own stub to observe the
  difference. The screen's contract — present at most the host's capacity, correct on the answer — is
  satisfied today.
- **Give the screen a test seam for its capacity (`DiagnosticsScreen(context, capacity = 8)`).** A production
  parameter exists only to make a case pass; the host's answer is what the case is about, and the fake host
  already carries it.
- **Give each case a unique log file in the Java temp dir** (the shape `StartupScreensTest` uses). That was
  tried by attribution once: the fixed path `java.io.tmpdir/diag-startup-test/app.log` is shared by two test
  JVMs, which is how the same case was read as `expected:<4> but was:<8>` on 2026-10-24
  (`openspec/changes/archive/2026-10-24-fix-buildsrc-compiles-on-the-daemon-jvm/tasks.md:88`). A unique path
  keeps a *file* apart but not the module-global target: `DiagnosticsLog` re-points one static on
  `initForTest`, so a screen a case never released still writes into whatever file the next case set. The
  release is what closes the shape.
- **Widen the diagnostics assertions everywhere.** #115 already closed that shape for whole-sink
  assertions; this change closes the same shape one level up, on the counts a screen's own page builds.
