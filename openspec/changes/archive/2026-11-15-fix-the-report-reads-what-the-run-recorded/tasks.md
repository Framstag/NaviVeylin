# Tasks — the report states what the run recorded, not what it can re-derive

The implementation of this change landed before the change was written, as commit `86fec1a5` (merged `cda0fc5b`).
These tasks record what that commit does, where each part of it is, and what the change cannot show.

## 1. The window is the recorded stop minus the recorded start

- [x] 1.1 `tools/loop/report.sh:168-171`: `window_secs` reads `.run.start.startedAt` and `.run.stop.when`, so the span
  the trend block prints is one the state holds. A run whose stop the state does not carry prints a dash with the
  reason, not a span dated from the clock the report ran at. — *spec: "The report states whether the loop is getting
  better", scenario "A figure the record does not hold is absent with its reason".*

## 2. The two figures the run held and no line printed

- [x] 2.1 `tools/loop/report.sh:596-644`: `gate share:` — a `jq` sum over the items that recorded a gate, each item's
  gate bucket over its own `metrics.wallSeconds`, in tenths, with the worst two items named. Tenths because the run's
  own figures, 60.4 % (#865) and 58.8 % (#864), are told apart only there. An item that recorded no gate is in no rate;
  a run of none prints a dash. — *spec: scenario "The trend block names the gate share and the outside window".*
- [x] 2.2 `tools/loop/report.sh:645-659`: `outside items:` — the recorded window minus the items' walls, with the share
  of the window that time is, so the figure the reader was left to subtract is printed beside the two figures it is the
  difference of. — *spec: same scenario.*
- [x] 2.3 `tools/loop/report.sh:762`: both lines are printed in the trend block beside the phase line, and the block
  says the sums it takes are its own arithmetic over recorded numbers. This is the derivation the requirement keeps,
  and it is named where it is printed. — *verified by the new case's assertions at `:14576-14583` and `:14590-14602`.*

## 3. The refusal is recorded where the merge met it

- [x] 3.1 `tools/loop/run.sh:486-487`, `:513-521`, `:542-545`: `merge_committed_branch` keeps the branch it could not
  land and the lines after `onto the base:` that name the paths; the arm where the base could not be recorded keeps the
  branch alone. — *spec: "An item a merge refused names its landing", paragraph 2.*
- [x] 3.2 `tools/loop/run.sh:4145-4165`: the blocked arm hands both to `state.sh done` as `--merge-refused-branch` and
  one `--merge-refused-path` per path, and clears them after the call so the next item cannot inherit a refusal.

## 4. The state writes what it was handed, or refuses it

- [x] 4.1 `tools/loop/state.sh:731-732`: the two arguments; `:924-928`: the record built as
  `{branch, paths}` with the empty lines dropped; `:952`, `:996`: written beside `merged:`.
- [x] 4.2 `tools/loop/state.sh:824-840`: the three refusals — a path with no branch, a branch on a record whose stop is
  not `unmergeable`, a path carrying a line break — each naming what it refused, so a refusal cannot enter the record
  half-formed. — *verified by the new case's two negative arms at `:13392-13399`.*

## 5. The report reads the record, and says when it cannot

- [x] 5.1 `tools/loop/report.sh:837-893`: `refusal_kind` is `record`, `absent` or `empty`; on `record` the branch is the
  one the state carries and the paths are its list, printed verbatim; on `absent`/`empty` the block falls back to the
  note and appends "this state records no refusal — its branch and paths are re-derived from the note, not read from a
  record" (`:892-893`), which is the pre-field state's line and nobody else's. — *spec: scenario "The refused landing
  is read from the record".*

## 6. The cases

- [x] 6.1 `tools/loop/tests/loop-cases.sh:14542`: new case
  `report#theTrendBlockNamesTheGateShareAndTheOutsideWindow` — two items whose gate buckets and walls derive 58.8 % over
  2 items with the per-item shares 64 % and 53.6 %, a recorded 300 s window 275 s of which is outside every item, and
  three absences told apart: no item spent time in a gate, the state records no window, the state was written before the
  items recorded phase durations. — *spec: scenarios "The trend block names the gate share and the outside window" and
  "A figure the record does not hold is absent with its reason".*
- [x] 6.2 `tools/loop/tests/loop-cases.sh:13357`: new case `report#theRefusedLandingIsReadFromTheRecord` — a record that
  carries one branch and two paths beside a note that names a different branch and another path; the printed landing is
  the record's, the note's branch and path are printed nowhere, the line does not claim to be re-derived, and the item
  is one line. — *spec: scenario "The refused landing is read from the record".*
- [x] 6.3 `tools/loop/tests/loop-cases.sh:13283`: `report#anUnmergeableItemNamesItsLanding` extended for the fallback:
  a state with no refusal record prints the note's landing and says on the line that it is re-derived (`:13346-13347`),
  which is the one case the fallback is for. — *spec: same scenario, last clause.*
- [x] 6.4 Registry entries `tools/loop/tests/loop-cases.sh:372`, `:394-395` (the case set) and `:1120`, `:1139-1140`
  (the dispatcher arms), so each case runs by name and in the pool. — *verified by
  `driver#everyCaseIsNamedInTheRegistry`.*

## 7. The falsification

- [x] 7.1 `tools/loop/tests/loop-mutations.sh:1360`: `883.1` — the window read as the clock since the run started
  instead of the stop the state recorded, so a run reports a span no record holds and the figure dates itself from the
  moment the report ran. Names `report#theTrendBlockNamesTheGateShareAndTheOutsideWindow`.
- [x] 7.2 `tools/loop/tests/loop-mutations.sh:1361`: `883.2` — the refused landing read back out of the note rather than
  the record, so the branch and the paths a caller is handed are a second reading of prose. Names
  `report#theRefusedLandingIsReadFromTheRecord`.
- [x] 7.3 `tools/loop/tests/loop-mutations.sh:1362`: `883.3` — the arm of `state.sh` that writes the refusal given to it
  removed, so the branch and the paths a block was given are recorded as no refusal at all and the report re-derives
  them from the note. Names `report#theRefusedLandingIsReadFromTheRecord`.

## 8. The rule, where its readers route it

- [x] 8.1 `guidelines/Backlog.recipes.md:749-761`: one paragraph — the gate share and the outside-items window are read
  from the record, not from a log — with the arithmetic of both figures, the tenths, and the refusal record that keeps
  the landing from being a second reading of prose. — *verified by the paragraph's presence beside the phase line's
  rule in the recipe document the loop's procedure section routes to.*

## 9. The requirements

- [x] 9.1 `openspec/changes/fix-the-report-reads-what-the-run-recorded/specs/agent-issue-loop/spec.md`: two `MODIFIED`
  blocks — "The report states whether the loop is getting better" (`openspec/specs/agent-issue-loop/spec.md:3083`) and
  "An item a merge refused names its landing" (`:4838`) — each shipped requirement carried verbatim, its shipped
  scenarios repeated verbatim and in order, then the paragraph and the scenarios that own the record. — *verified by
  `openspec validate fix-the-report-reads-what-the-run-recorded --strict` (`evidence/openspec-validate.txt`).*

## 10. The gate, and what it does and does not say

- [x] 10.1 The gate that landed `86fec1a5` is the loop's own run, and its per-suite tally is **not retained** in this
  checkout: the run record is gitignored and machine-local, and this change was written after the run. No figure is
  quoted here, because a tally no artifact carries is the claim `tools/loop/evidence-check.sh`'s C2 exists to refuse. A
  reader who needs it re-runs `bash tools/loop/tests/loop-selftest.sh`.
- [x] 10.2 What this change could take instead, and took: the line map of both figures and the refusal path, the two
  case bodies with their assertions, the two negative arms of `state.sh`, the three mutation records verbatim from the
  suite's own set, and the structural checks of 11.1.

## 11. The checks this change owes

- [x] 11.1 `openspec validate fix-the-report-reads-what-the-run-recorded --strict`, `openspec doctor`,
  `bash tools/check-doc-routes.sh`, `bash tools/check-openspec-hygiene.sh`, `bash -n tools/loop/report.sh`,
  `bash -n tools/loop/state.sh`, `bash -n tools/loop/run.sh` and `bash tools/loop/evidence-check.sh <this change>` —
  each re-runnable and recorded in `evidence/structural-checks.txt` with its verdict.
- [x] 11.2 The delta touches no compiled file — three engine shell scripts, their case file and mutation set, and one
  guideline — so this change runs no build, no Android test and no native step.

## 12. What this change does not cover

- [x] 12.1 Not covered: the exit-5 landing refusal arm of `run.sh` — the path where the merge is refused before the run
  reaches the blocked arm that hands the refusal to `state.sh done` (`tools/loop/run.sh:481`, `:510-547`) — has **no case of its
  own**. `report#theRefusedLandingIsReadFromTheRecord` writes the refusal through `state.sh done` directly, and the HUP
  and unmergeable cases assert the run's own note, not the record a refusal written on an exit-5 path would produce. A
  reader may not read the two cases as covering that arm.
- [x] 12.2 Not covered: the two arithmetic lines stay derivations. `gate share:` and `outside items:` are subtractions
  over recorded fields, taken at report time; this change names them as such in the trend block rather than recording
  them at run time, because a recorded share would be a figure the engine writes for a reader it does not have.
- [x] 12.3 Not covered: a run whose state was written before `mergeRefused` existed keeps the note's landing, said to be
  re-derived. That is the fallback `883.2` and `883.3` each leave reachable, and it is not removed here — a state
  written before the field is a state whose runs this code cannot re-write.
