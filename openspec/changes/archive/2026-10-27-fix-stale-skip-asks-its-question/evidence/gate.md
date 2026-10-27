# The gate, quoted

Run once, from the change root, 2026-10-27: `./tools/loop/gate.sh` (log: `<item>/gate.log`).

```
LOOP-GATE: tests=767 failures=0 errors=1 (./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache 260s, tools/loop-selftest.sh --from-git 6f61cffe4f82e91c38349f5a6787c28011466387 434s, bash tools/loop-mutations.sh --check 2s, tools/loop-mutations-selftest.sh 336s, tools/check-portability-selftest.sh 25s, tools/check-compose-sandbox-rule.sh 1s, tools/check-compose-sandbox-rule-selftest.sh 0s, tools/gen-feature-list-selftest.sh 17s, bash tools/gen-feature-list.sh --check-classification 4s, tools/check-doc-routes.sh 0s, tools/check-doc-routes-selftest.sh 1s, tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list 0s, tools/check-todo-references.sh 1s, tools/check-openspec-hygiene.sh 1s, tools/check-openspec-hygiene-selftest.sh 0s, tools/check-portability.sh 1s, tools/mutate-selftest.sh 0s, tools/mutate.sh --mutations tools/portability-mutations.json --list 0s, tools/issue-tooling-selftest.sh 71s, tools/mutate.sh --mutations tools/status-mutations.json --list 0s, tools/mutate.sh --mutations tools/create-mutations.json --list 0s, tools/mutate.sh --mutations tools/close-mutations.json --list 0s, tools/mutate.sh --mutations tools/duplicates-mutations.json --list 1s, tools/mutate.sh --mutations tools/decision-question-mutations.json --list 0s) — failing: ./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache
```

The suites this change's own pathspec owes, each from its tally under `gate-tallies/` (`gate-summary.xml` carries
only the aggregate the line above states, with the per-suite sum beside it, because `gate.sh` writes no aggregate
tally of its own):

```
tools/loop-selftest.sh --from-git <base>   tests="256" failures="0" errors="0" time="434"
bash tools/loop-mutations.sh --check       tests="198" failures="0" errors="0" time="2"
tools/loop-mutations-selftest.sh           tests="34" failures="0" errors="0" time="336"
tools/mutate-selftest.sh                   tests="0" failures="0" errors="0" time="0"
tools/check-portability.sh                 tests="0" failures="0" errors="0" time="1"
tools/check-portability-selftest.sh        tests="29" failures="0" errors="0" time="25"
tools/check-doc-routes.sh                  tests="0" failures="0" errors="0" time="0"
tools/check-openspec-hygiene.sh            tests="0" failures="0" errors="0" time="1"
tools/gen-feature-list-selftest.sh         tests="119" failures="0" errors="0" time="17"
tools/issue-tooling-selftest.sh            tests="125" failures="0" errors="0" time="71"
```

## The one error, attributed

The change touches no compiled file — `tools/loop/*.sh`, `tools/loop/prompts/*.md`, `tools/loop-cases.sh`,
`tools/loop-mutations.sh` and the change's own prose — so the build is not part of its delta. The gate ran the
build anyway because it derived its base from the loop's recorded base (`6f61cffe…`, 364 paths including
`app/src`), not from this item's base. Its single failure is in a class this change does not reach:

```
:app:testMobileDebugUnitTest
BasemapSectionComposeTest > installedShowsDeleteButton FAILED
    android.view.ViewRootImpl$CalledFromWrongThreadException: Only the original thread that created a view hierarchy can touch its views. Expected: SDK 36 Main Thread Calling: DefaultDispatcher-worker-12 @coroutine#16689
2126 tests completed, 1 failed
```

Attribution, per `guidelines/Build.md` §4d:

1. the class alone — `./gradlew :app:testMobileDebugUnitTest --tests
   'com.naviveylin.ui.mapmanager.BasemapSectionComposeTest' -PforceTests --no-build-cache` → `BUILD SUCCESSFUL in 17s`;
2. the change adds no test class, so there is no "with the change's classes" set to run beside it;
3. the shape is a recorded one: a wrong-thread `requestLayout` in `BasemapSectionComposeTest` under a loaded
   one-fork suite is the flake tombstoned as §26 in
   `openspec/changes/archive/2026-10-17-migrate-todo-to-github-issues/tombstones.json`, and it is outside every
   path this change touches.

Both numbers stand in this file, as the guideline asks: the full-suite run failed 1 of 2126 in `:app`'s mobile
suite, and the class alone is green. The residual a person may want is a real issue in that test's threading, not
in this change.

## After the gate

One prose edit followed the gate run: `guidelines/Backlog.md` §5 gained the rule this change implements (a stale
skip asks its own question, and is never parked on a bare hold), and the archived `proposal.md` names that rule.
No code, case or mutation record changed after the gate. The checks that read prose were re-run on the final tree,
2026-10-27:

```
check-doc-routes: all 154 section references resolve, all 4 skill references resolve
check-todo-references: 203 line(s) scanned, 63 distinct citation(s), 162 in the recorded table
tools/check-openspec-hygiene.sh: OpenSpec artifacts are consistent: 0 open change(s) ...
tools/gen-feature-list.sh --check-classification: (exit 0)
tools/check-portability.sh: (exit 0)
tools/loop/evidence-check.sh <archived change> --root .: evidence-check: PASS
```

## Correction, round 2 (2026-10-27)

Two sentences above are withdrawn rather than left standing:

- "The suites this change's own pathspec owes" is wrong about the list it introduces: the gate ran 24 tallies,
  and the ten quoted are the ones that carry cases. The other fourteen are the gate's `--list` and `--check`
  steps, which run none.
- `BUILD SUCCESSFUL in 17s` — the class-alone attribution that followed the failing build — is carried by no log
  in this record, so it is not evidence and is withdrawn here; the failing class and its line stay as they were,
  quoted from the log.
- The `evidence-check` line above is now carried by numbers: `gate-round2.md` quotes its pass with the C2 pair
  count and the C5 resolution count.

The correction round's own gate run — the one that carries the labels and case edits — is `gate-round2.md`,
`gate-round2-summary.xml` and `gate-round2-tallies/`, with `errors=0` against this run's single error.
