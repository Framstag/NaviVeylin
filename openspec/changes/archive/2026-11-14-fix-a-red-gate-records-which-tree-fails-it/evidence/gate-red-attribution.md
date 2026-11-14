# Gate red attribution — item #853, phase F (tail child, 2026-11-13)

Gate line, quoted from the first process and never re-run:

```
LOOP-GATE: tests=1078 failures=2 errors=1 (Gradle JVM build gate + loop engine self-tests/mutations + doc/tooling shell suites, 26 steps — failing: ./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache, tools/loop/tests/loop-mutations-selftest.sh) — not retained as one JUnit XML: this is the aggregate verdict, and each of the 26 step's own tally is under `evidence/gate-tallies/`
```

Two steps named. One is attributed below; the other is not attributable from what the gate retained, and it is on
this change's paths — so the verdict is `blocked`, not a close, and no waiver clause is written.

## Step 1 — `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache`, errors=1: attributed, outside this change

- Failing case and its exception, read from this item's own gate console
  (`items/853/gate-console.log:232`–`:262`): `MapManagerSourceSwitchPlanTest >
  aSwitchThatKeepsTheTargetsBasemapKeepsItRegistered FAILED` —
  `java.lang.NoClassDefFoundError: Could not initialize class sun.security.ssl.NamedGroup$SupportedGroups`, thrown
  while the case opens a real HTTPS connection (`HttpUrlFetcher.kt:65`). Environment/JDK TLS class init, not a
  product assertion.
- This change edits no compiled file (`tasks.md`: "No build task: this change touches no compiled file") — the
  delta is `tools/loop/*.sh`, `tools/loop/tests/*`, `guidelines/BuildEvidence.md` and one spec delta. The Kotlin
  app is outside the paths this change edits.
- Red before this work, same test, same exception:
  - `evidence/pre-existing-red-mapmanager-2026-11-06.xml` (copy of
    `openspec/changes/archive/2026-11-06-motorway-destination-hint/evidence/TEST-com.naviveylin.ui.mapmanager.MapManagerSourceSwitchPlanTest.xml`:
    `tests="9" failures="1"`, the failing testcase `aSwitchThatKeepsTheTargetsBasemapKeepsItRegistered`, failure
    message the same `NoClassDefFoundError`), also copied into `items/853/`.
  - `openspec/changes/archive/2026-11-06-motorway-destination-hint/evidence/gate.log:227`,
    `openspec/changes/archive/2026-11-07-fix-report-splits-the-spend-by-phase/evidence/gate.log:179`,
    `openspec/changes/archive/2026-10-21-fix-follow-blit-anchor-call-site/evidence/gate/gate-round2.log:2013` — the
    same line on three earlier trees.
- Stays red after this work: this gate's own tally
  (`evidence/gate-tallies/gradlew_test_-PforceTests_-PjvmTestsOnly_--no-build-cache.xml`, `errors="1"`; copy in
  `items/853/gate-gradlew.xml`).

A clause waiving *this* step alone would read: the Gradle JVM step fails on
`MapManagerSourceSwitchPlanTest > aSwitchThatKeepsTheTargetsBasemapKeepsItRegistered` with
`NoClassDefFoundError: sun.security.ssl.NamedGroup$SupportedGroups`, red on the 2026-10-21 / 11-06 / 11-07 trees
and red here, on a test outside any path this change edits. It is written here for the record; it is **not** used
as a `LOOP-GATE-WAIVED`, because the item does not close (step 2 below).

## Step 2 — `tools/loop/tests/loop-mutations-selftest.sh`, tests=34 failures=2: not attributable, on this change's paths

Failing output, read from `items/853/worker-tail-report-gate-output.txt` (and `evidence/gate.log:1488`, `:1497`,
`:1501`):

```
FAIL a file set runs the records its file owns: expected [0] got [1]
FAIL a git diff names the records its file owns: expected [0] got [1]
loop-mutations-selftest: 2 of 34 cases failed — the mutation harness does not hold its own guards
```

What the gate retained is enough to name the *kind* of failure, not the record:

- Every other check of the suite passed (`evidence/gate.log:1484`–`:1500`): the declared set, the file set's
  listing, `selected N of 358 records`, `exactly the owned records ran` (the `RED` line count equals the records
  owning `tools/loop/state.sh`), `no record outside the file set ran`, and `… the copy is byte-identical to the
  pristine one again` after both runs.
- Therefore the harness printed neither `MUTATION-DID-NOT-APPLY` nor `GREEN … the case did not notice` — either
  would have shortened the `RED` count and failed `exactly the owned records ran`. The only remaining exit-1 path
  is `failed ≥ 1` (`tools/loop/tests/loop-mutations.sh:1486`, `:1531`–`:1533`): `RESTORE-FAILED <label> <case>
  stayed red after the restore` — one record owning `tools/loop/state.sh` had its case stay red after the file was
  restored byte-identical.
- Which record/case: not retained anywhere. The selftest captures the harness's whole stdout into
  `out=$(…)` and prints only its own ok/FAIL lines (`tools/loop/tests/loop-mutations-selftest.sh:150`, `:152`).

Why this sits on the change's own paths: the file set is `tools/loop/state.sh`, and the suite runs over
`tools/loop/*` — exactly the files this change edits (`tasks.md` 2.1–2.5: `gate.sh`, `run.sh`, `state.sh`,
`report.sh`, plus `loop-cases.sh` and the 853 mutation records in `loop-mutations.sh`).

Why no proof of "red before this work" exists:

- `loop-mutations-selftest: all 34 cases passed` one day before the base — `items/856/worker-gate-report.md`,
  gate started `2026-11-12T19:05:11Z`, head `ecdfbe88…`, step `loop-mutations-selftest 34/34`.
- No measurement of this suite at the run's base `647b289a53e9cf17064c5f341ef8621ee3c3de02` exists in the tree.
- The change's own `evidence/focused-from-git.xml` shows the cases green on this tree (`tests=426 failures=0`),
  which contradicts a permanent case red and leaves an environment/flake explanation untested either way.

So: green at 2026-11-12, red with this change sitting on those paths, the failing record not named by any retained
output, and no base measurement. The clause that would close over it cannot be written from the tree — a waiver
only one transcript holds is a red nobody counts — hence `LOOP-VERDICT: blocked` and `LOOP-DECISION` below.

Reason for the verdict (gate line + the failing output read from
`items/853/worker-tail-report-gate-output.txt`): the gate line above; `FAIL a file set runs the records its file
owns: expected [0] got [1]` and `FAIL a git diff names the records its file owns: expected [0] got [1]`,
`loop-mutations-selftest: 2 of 34 cases failed`.

No tree edit was made after the gate; nothing committed, nothing staged, tracker untouched.
