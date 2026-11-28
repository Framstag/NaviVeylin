# The gate this change owes, and what it answered

A delta that touches no compiled file runs no build and no Gradle suite; it names the structural checks it ran
instead. This change is nine paths under `tools/loop/` and one guideline companion, so its gate is the loop's own:

| check | what it answers | result |
|---|---|---|
| `tools/loop/tests/loop-selftest.sh` (whole set, 582 cases) | the loop's behaviour | 581 of 582 green in 635 s, then 580 of 582 in 794 s — neither failure this change's, see below |
| `tools/loop/tests/loop-mutations.sh --check` | every declared mutation record still applies to the file it names | all 472 records pass |
| `tools/loop/tests/loop-mutations-selftest.sh` | the harness's own guards, over a copy of the engine | all 40 cases pass |
| `tools/loop/evidence-check.sh <this change> --root .` | the change's evidence pointers, and its own route line | PASS |
| `tools/check-portability.sh` + `tools/tests/check-portability-selftest.sh` | no project value in an engine | 67 files clean, 31 of 31 cases pass |
| `openspec validate the-route-share-is-printed --strict` | the artifact pair, the requirement bound, the scenario cases | valid |
| `tools/check-doc-routes.sh` | the guideline section and skill references the routing table makes | 357 section references, 8 skill references |
| `tools/check-case-names.sh` | a shipped spec's `**Case**:` line resolves to a harness | **red, and not by this change** — see below |

## The two reds, and why neither is this change's

- `usage#noUnguardedValueFlagRemains` names `tools/route-hints.sh` and `tools/route-steps.sh`. Both are untracked in
  this working tree and neither is in this change's diff: a second session is working in the same tree. The first
  pool-run of the suite was green on this case before those files appeared.
- `driver#aDeclarationNamingAnUnknownFileFails` and `run#aCutRunsItsGateOnTheKeptWork` each reddened once, in different
  runs under the driver's pool, and each passes alone and in its own group afterwards — `suite-full-cases-rerun.log`.
  A machine a second session is running its own suites on is the reading that fits; the two runs' logs are kept
  beside this note so the numbers are checkable rather than asserted.
- `tools/check-case-names.sh` refuses two scenario lines of `openspec/specs/auto-smooth-follow/spec.md` that cite
  `auto#theCarOverlayOffsetIsTheFramesShift`, which no harness lists. That spec and the case lists are untouched by
  this change (`case-names.log`).

## What was mutated, and what noticed

- `1113.1` makes `loop_route_resolves` answer true for every id: `report#theRouteShareIsPrinted` reddens on
  `route: 2 of 2 item(s)` where it asserts `1 of 2`, and `check#aRouteSectionResolvesAgainstItsDocument` reddens on
  the arm that refuses a section the document does not carry — one mutation, both readers, and both green again after
  a byte-for-byte restore (`mutation-one-record-two-readers.log`).
- `974.1` and `974.2` were restated, `974.1` onto the comparison's new home in `lib.sh`, because the line it quoted
  moved (`mutations.log`).
- The three new cases fail on the base commit's `report.sh` for the line that is not there rather than for arithmetic
  (`base-red.log`), and pass on the change's tree (`cases-green.log`).
