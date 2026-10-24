# Review round 1 → the corrections, and what each one rests on

The reviewer refused the change with two BLOCKING findings and three UNPROVEN claims, all of the same kind:
a number the change quotes that no archived artifact carries. The fix itself was not questioned — both
scenarios red then green with their exact assertions and timestamps, the falsification red for the right
reason, the restore clean, the delta `ADDED` with two cases, the shipped spec merged. Nothing shipped is
reverted: the production code, the strings, the two cases and the merged requirement are unchanged, and this
round edits three markdown files of the change and adds two evidence files (this note and the grep transcript).
No compiled file is touched, so this round runs no
build and no suite; the gate below is round 1's, quoted from the artifact that carries it.

## BLOCKING — task 6 quoted three tallies, and none of them survives

The round-1 task 6 said `./gradlew :auto:testDebugUnitTest` → `tests=877 failures=0 errors=0`, `EXIT=0`
`(evidence/focused-green-module.log)`, and the gate as `LOOP-GATE: tests=481 failures=0 (…)`, `EXIT=0`
`(evidence/gate.log, started 2026-10-22T10:35:20Z)`, "with the gradle half of the same gate at 5 769 tests / 0
failures across `app` (574 XMLs), `auto` (86), `core` (60) and `osmscout-client-java` (8)".

Neither cited artifact carries its half — two commands, quoted:

```
$ grep -n 'BUILD\|EXIT=' evidence/focused-green-module.log
84:BUILD SUCCESSFUL in 31s
$ wc -l < evidence/gate.log          # 9 `gate:` lines plus one summary line, nothing else
10
$ head -1 evidence/gate.log
gate: ./gradlew test -PforceTests --no-build-cache
$ tail -1 evidence/gate.log
LOOP-GATE: tests=481 failures=0 (./gradlew test …, tools/check-doc-routes.sh)
```

`focused-green-module.log` has no case count and no exit line (`grep -c 'EXIT=' *` → 0 in both logs);
`gate.log` has no start time, no exit code and
no gradle tally. The `877` and the `5 769`/`574/86/60/8` set were read out of `auto/build/test-results/` and
`*/build/test-results/` after the run — the reviewer's finding, not a quote of an artifact — and the round-1
gate overwrote both directories, so nothing in the tree can restore them. `grep -l '877\|5 769\|10:35:20'
evidence/*` matches no file but this note, which names them only to correct them.

Correction — task 6 now quotes the two lines that do survive, and nothing else: `BUILD SUCCESSFUL in 31s`
(`evidence/focused-green-module.log`) and `LOOP-GATE: tests=481 failures=0 (…)` (`evidence/gate.log`). It also
says what makes the second one decisive, cited in the tree rather than asserted: `tools/loop/gate.sh:139-143`
prints the summary in that form only on the path where every suite exited 0, and prints `— failing:<suites>`
with `exit 1` otherwise. The `EXIT=0` and the start time are gone with the numbers they qualified.

## UNPROVEN — the surface line quoted a grep nobody archived

`proposal.md` and `design.md` both ended their `surface:` line with "→ 12 matches in 5 files". No transcript of
that pre-fix grep was archived, and it cannot be repeated from the archived tree: the pre-fix counts existed
only in the tree before the deletion.

Correction — the number is replaced by the measurement the archived tree does carry: the same search after the
fix matches 7 lines in 3 files, all of them this change's own (the two cases and the requirement the delta
adds), and no shell case, fixture, prompt or shipped-spec line matched then or now. The transcript with the
command and its UTC stamp is `evidence/surface-after.log`; both documents cite it. The pre-fix count is not
re-quoted in another shape — a second unrepeatable number would have repeated the defect.

## UNPROVEN — the post-fix grep claim in the round-1 worker report

The report's `LOOP-MEASURED` said "after the fix the same grep matches only the two cases that assert the
absence", while its own command included `openspec/specs/`, which now matches three lines of the new
requirement. Corrected in this round's report and in `evidence/surface-after.log`: 7 matches in 3 files, and the
absence case matches on its KDoc line as well as its two body lines.

## What still stands, and what this round did not touch

- `tasks.md` tasks 1–5, 7 and 8, and their artifacts, are as round 1 left them; the reviewer re-checked them by
  reading and they hold.
- The reviewer's four NOTEs are not corrections of a false claim and are left alone: the `values-de` half of the
  deletion has no case (verified clean by grep, recorded as `everyEnglishStringHasGermanTranslation` checks
  `values ⊆ values-de` only), the red transcript's stack line is `StartupScreensTest.kt:81` against the shipped
  case's line 75, `review-1.diff` is the run's cumulative diff rather than this change's, and `LOOP-GATE:
  tests=481` is the eight shell suites' tally rather than a gradle count. The fourth is task 7's: its two
  transcripts carry no command line and no clock either, so their `EXIT=1`/`EXIT=0` are read off `FAILED` and
  `BUILD SUCCESSFUL`, which is decisive for a gradle run and is where a reader will find them.
- The shipped spec `openspec/specs/route-calculation-feedback/spec.md` and the merged requirement are unchanged.
