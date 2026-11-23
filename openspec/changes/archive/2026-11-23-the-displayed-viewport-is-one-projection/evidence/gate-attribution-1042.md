# Gate red attribution — item #1042 (phase F)

The gate ran once; this record reads its log, it does not re-run it. Gate line verbatim (`items/1042/gate.log:400`):

    LOOP-GATE: tests=51 failures=1 errors=0 (./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache 220s, tools/check-compose-sandbox-rule.sh 0s, tools/tests/check-compose-sandbox-rule-selftest.sh 0s, bash tools/gen-feature-list.sh --check-classification 3s, tools/check-doc-values.sh 0s, tools/tests/check-doc-values-selftest.sh 0s, tools/check-doc-routes.sh 1s, tools/tests/check-doc-routes-selftest.sh 8s, tools/mutate.sh --mutations tools/tests/check-doc-routes-mutations.json --list 0s, tools/check-todo-references.sh 2s, tools/check-openspec-hygiene.sh 1s, tools/tests/check-openspec-hygiene-selftest.sh 1s) — failing: ./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache — base-red: tools/check-openspec-hygiene.sh:1

## What this change touches

`git diff --name-only 45e855d49ee8d36bcc45a7bf14ae087bbf85eb85`, run in the change root:

    app/src/main/java/com/naviveylin/ui/map/MapCanvasMapBand.kt
    app/src/main/java/com/naviveylin/ui/map/MapCanvasScreenParts.kt
    app/src/test/java/com/naviveylin/ui/map/MarkerDisplayShiftTest.kt
    guidelines/MapFrames.md
    guidelines/MapFrames.why.md
    openspec/specs/smooth-follow/spec.md

## Red 1 — `failures=1`: `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache`

The one failing case is `com.naviveylin.auto.SearchHistoryScreenTest.thePageRowOpensTheNextSliceOfTheHistory`,
`AssertionError: no row titled More (3) in [query-1, query-2, query-3, query-4, query-5, query-6, More (5)]`
(`gate.log:221-254`, copied verbatim into this directory as `gate-red-auto-search-history.log`).

**Not red before this work.** The gate measured this very step on the base commit: `gate.log:272-273` names the
measure at `45e855d49ee8` and records `0 case(s) red`; the measurement's own output in
`.pi/issue-loop/base-red.log:43610-43615` is `BUILD SUCCESSFUL in 7m 17s` followed by
`LOOP-BASERED: tests=0 failures=0 errors=0 (./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache 437s) — failing:none`.

**Green after this work — a flake.** The same class, run alone in the change root after the gate, exits green:
`evidence/green-auto-search-history-rerun.xml` — `tests="2" failures="0" errors="0" timestamp="2026-11-23T11:28:12.013Z"`,
against the gate's own failing run at `2026-11-23T11:11:58.710Z`. That is the run's flake shape
(`openspec/specs/agent-issue-loop/spec.md`, `run#aFlakedCaseIsOffTheBlockedPath`: red tally, named case, rerun in the
item's worktree exits 0).

**Why this change cannot answer for it.** The diff above contains no file under `auto/`, and `auto/build.gradle.kts:196-199`
declares the module's project dependencies as `:core` and `:osmscout-client-java` only — neither is in the diff, so no
byte this change writes is reachable from `SearchHistoryScreenTest`.

## Red 2 — `base-red: tools/check-openspec-hygiene.sh:1`

**Red before this work.** The step exits 1 on the base commit: `gate.log:394-396` measures it at `45e855d49ee8` and
records `LOOP-GATE-BASERED:tools/check-openspec-hygiene.sh:1`; the measurement's output in
`.pi/issue-loop/base-red.log:43617` is headed `# 2026-11-23T11:21:38Z — tools/check-openspec-hygiene.sh on
45e855d49ee8, gate exit 1`, and its `::error` lines name `openspec/changes/al-prefixed-loop-skills/.openspec.yaml`,
`dedupe-stylesheet-loads`, `fix-a-case-wait-decided-by-its-record`, `fix-car-follow-reengage-render`,
`fix-compound-name-matching` — every one another change's open `.openspec.yaml` missing `x-issue:`.

**Stays red after this work.** This gate itself printed the same class of error on the same other changes
(`gate.log:388-390`: `fix-a-rejected-publish-is-recorded`, `fix-a-sensor-filing-carries-the-rows-type`,
`fix-the-checkpoint-splits-the-items-chain`).

**Why this change cannot answer for it.** The diff carries no `.openspec.yaml` (the six files above), and this
change's own record is archived as `2026-11-23-the-displayed-viewport-is-one-projection` — a name the check does not
flag.

## The clause this close is waived with

    LOOP-GATE-WAIVED: the gate's one failure and its base-red step are both off this change's paths — com.naviveylin.auto.SearchHistoryScreenTest.thePageRowOpensTheNextSliceOfTheHistory failed only in the gate run: the same step measured green on base 45e855d49ee8 (gate.log:273, base-red.log:43614-43615 failing:none) and the class reran green in the change root after the gate (evidence/green-auto-search-history-rerun.xml, tests=2 failures=0), a flake by run#aFlakedCaseIsOffTheBlockedPath, and the diff touches no auto/ file while auto/build.gradle.kts:196-199 reaches only :core and :osmscout-client-java; and tools/check-openspec-hygiene.sh was already red at that base (LOOP-GATE-BASERED:tools/check-openspec-hygiene.sh:1, base-red.log:43617) on other changes' un-x-issue'd .openspec.yaml files, a set this six-file diff cannot join.
    LOOP-GATE-WAIVED-SUITE: ./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache tools/check-openspec-hygiene.sh
