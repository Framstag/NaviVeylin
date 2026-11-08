# Gate attribution — `tools/check-rule-routes.sh` (errors=1), 2026-11-08

The gate line this item was handed:

    LOOP-GATE: tests=953 failures=0 errors=1 (Gradle JVM test + loop engine self-tests + tools/*.sh shell suites — failing: tools/check-rule-routes.sh)

The aggregate behind that line is retained as
`evidence/gate-tally/LOOP-GATE-aggregate.xml` — the awk-summed `tests=`/`failures=`/`errors=`
attributes of the gate's own step tallies in `app/build/test-results/loop-gate/`, which comes to
`tests="953" failures="0" errors="1"` (gate.sh writes no aggregate XML of its own; each step's
tally XML is beside it under `evidence/gate-tally/`).

The failing suite's own result, copied verbatim from the gate run as
`evidence/gate-tally/tools_check-rule-routes.sh.xml` (source: the gate child's output,
`.pi/issue-loop/items/113/gate-TEST-check-rule-routes.xml`):

    <testsuite name="tools/check-rule-routes.sh" tests="0" failures="0" errors="1" time="1">
      <system-out>::error file=tools/doc-routes.json::the rule "the car's reroute wait is a surface strip, the phone's is a card tint." in UI.md is reached by no glob — a rule no change is told to obey check-rule-routes: 0 glob(s) match no tracked path, 6 rule(s) are reached by no glob, 0 rule(s) state no name check-rule-routes: 0 document(s) are matched by no glob, 0 guideline route(s) carry a document their glob </system-out>
    </testsuite>

So the only red tally is `errors=1`, in `tools/check-rule-routes.sh`; every other step of the
gate is `failures="0" errors="0"` (the Gradle step's own wrapper,
`evidence/gate-tally/gradlew_test_-PforceTests_-PjvmTestsOnly_--no-build-cache.xml`, reads
`BUILD SUCCESSFUL in 4m 30s`).

## What the failing step measures

`tools/check-rule-routes.sh` (default mode) reads exactly four inputs: `tools/tooling.json`
`docRoutes`, the map it names (`tools/doc-routes.json`), the `**Rule:` lines of
`guidelines/*.md`, and `git ls-files` for the glob-reached tracked paths. It prints one
invariant per fact; the red is `6 rule(s) are reached by no glob`, first named as the rule
"the car's reroute wait is a surface strip, the phone's is a card tint." in `guidelines/UI.md`
(a reroute-navigation rule at `guidelines/UI.md:97`).

## What proves it was red before this work

Run from the change root at base `d91d8bbafdc6e886b74e3d2f1208f300a2584209`:

    $ git diff --stat d91d8bbafdc6e886b74e3d2f1208f300a2584209 -- guidelines tools/doc-routes.json tools/tooling.json
    (no output — all three inputs are byte-identical to base)

    $ git diff --name-only d91d8bbafdc6e886b74e3d2f1208f300a2584209
    auto/src/main/java/com/naviveylin/auto/PoiResultsScreen.kt
    auto/src/test/java/com/framstag/libosmscout/client/FakeMapScreenClient.kt
    auto/src/test/java/com/naviveylin/auto/PoiResultsScreenTest.kt

    $ tools/look.sh "the car's reroute wait is a surface strip" --path guidelines/UI.md
    LOOK: 1 matches in 1 files ... guidelines/UI.md:97

This change edits three Kotlin files and nothing else: no `guidelines/` document, no route
map, no `tools/` script, and it adds no `**Rule:` line. Every input the failing check reads is
the base's own file, so the failing rule existed unreached on HEAD before this work, and the
gate's own tally for the glob side agrees with the change's innocence —
`0 glob(s) match no tracked path`, i.e. the test file this change added orphans no route.

## What proves it stays red after it

The check is a pure function of the four inputs named above, and this change changes none of
them (`git diff` over `guidelines`, `tools/doc-routes.json`, `tools/tooling.json` is empty; the
tracked-path delta is three Kotlin files, which the tally shows orphan nothing). Same inputs →
same verdict on base and on this change. The failing case — an unreached reroute rule in
`guidelines/UI.md` — is outside every path this change edits (`PoiResultsScreen.kt`, its test,
the fake client), and the change's own specs (`car-list-row-limit`, `auto-search`) are answered
by the gate's green Gradle step.

## Waiver

    LOOP-GATE-WAIVED: the single red tally is errors=1 in tools/check-rule-routes.sh — an unreached reroute rule in guidelines/UI.md (a file byte-identical to base d91d8bba, alongside tools/doc-routes.json and tools/tooling.json, while this change edits only three Kotlin files), so the step was red before this work and stays red after it
    LOOP-GATE-WAIVED-SUITE: tools/check-rule-routes.sh
