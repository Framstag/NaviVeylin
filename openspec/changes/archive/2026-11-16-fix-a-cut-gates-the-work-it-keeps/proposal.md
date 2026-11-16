# A cut gates the work it keeps

## Why

**Root cause.** The cut arm (`tools/loop/run.sh:4113` — the `cut)` arm of the outcome switch, `run.sh:4133` its note,
`run.sh:4151` its `loop_state done`) records what a bound-ended child left and never reads it: the patch is named, the
tree is kept exactly as the child left it, and no gate is ever started on that tree — so the caller is owed a `resume`
action and nothing measured about the work that exists.

**Evidence, verified against the tree and the run's own record (issue #885).** The item was filed from the run of
`2026-11-13T05:47:11Z`. Its record (`.pi/issue-loop/runs/2026-11-13T05:47:11Z.json` in the project tree) holds:

```
$ jq -r '.items[] | select(.issue==862 or .issue==875)
    | [.issue,.result,.verdict,(.change//"-"),(.metrics.gate//"-"),(.leftoverFiles//"-"),(.stopReason//"-"),(.metrics.context//"-")]
    | @tsv' .pi/issue-loop/runs/2026-11-13T05:47:11Z.json
862	cut	cut			235	bound	200297
875	cut	cut	poi-name-filter		260	-	205503
```

Both items ended `cut` with 235 and 260 files of kept work, and `metrics.gate` is empty for both — 20 649 312 and
17 389 316 tokens, 38.0 Mtok with no verdict. The precedent for a patch applied by a later run is recorded beside the
continuation (`tools/loop/run.sh:2908`): such patches failed 2 of 2 because the base had moved under them between the
runs. So the work is the most expensive thing the run leaves behind, and the one thing nobody measures.

**The red test.** `run#aCutRunsItsGateOnTheKeptWork` (`tools/loop/tests/loop-cases.sh`) fails on the base commit
`2807ba611a1430188d8880552d30c68ff2458cdb` with the case added and `tools/loop/run.sh`/`tools/loop/state.sh` unmodified
(`evidence/red-case-on-HEAD.log`, and `evidence/red-on-HEAD.xml` transcribing that log's own tally — the shell runner
writes no JUnit XML of its own; tests=1 failures=1, the run that log records, 2026-11-16T02:38:18Z): the run cuts the
worker at the recorded context bound, cuts the one continuation the #851 ceiling allows, and the case's first
assertion — "the run recorded no gate on the work its cut kept" — refuses the silence. The same case is green on this
tree (`evidence/green-case.log` is its own `PASS` line, `evidence/cases-r2-green.log` its re-run), and the whole suite
the delta is owed passes beside it (`evidence/affected-cases-green.log`; `LOOP-MEASURE-SUITE cases=465`, 243 cases run,
1 128.79 s, all passed).

**The owning requirement** is the loop's own contract `openspec/specs/agent-issue-loop/spec.md`, "A child the run ended
itself leaves no commit" — the record a cut writes — read beside "A cut item is named to the caller as work to resume",
which owns what that record's note reaches. **The owning guideline section** is `guidelines/Backlog.recipes.md`, the
section headed "What an item costs, and the bounds on it", which is where the #851 continuation and the kept patch's record already
live; it carries this change's paragraph.

**Surface, counted before the plan was fixed** (`tools/look.sh 'gate_the_kept_work|cutGate|cut-gate'`, run over the
tree at the base commit plus this delta — the shape of a rule, not its first file):

```
LOOK: 29 matches in 8 files
  tools/loop/run.sh 5 · tools/loop/state.sh 4 · tools/loop/tests/loop-cases.sh 7 ·
  tools/loop/tests/loop-mutations.sh 1 · guidelines/Backlog.recipes.md 2 · the change's own two documents 9
surface: 2 engine files edited, 2 cases (1 added, 1 realigned), 1 mutation, 1 guideline section, 1 spec delta
```

## What changes

- `tools/loop/run.sh`: `gate_the_kept_work` — the gate the profile names, run in the change root on the tree the cut
  left, before the item is recorded — called by the `cut)` arm, with the result and its log recorded beside the cut.
- `tools/loop/state.sh`: `--cut-gate <log>` / `--cut-gate-result <result>`, recorded as `cutGate: {result, log}` on the
  item's record (`null` for every arm that ran no gate).
- `tools/loop/tests/loop-cases.sh`: `run#aCutRunsItsGateOnTheKeptWork`, and one assertion in
  `run#aCutWhosePatchDoesNotApplyIsLeftCut` for the cut that keeps no file.
- `tools/loop/tests/loop-mutations.sh`: `885.1`, the arm itself.
- `guidelines/Backlog.recipes.md`, the section headed "What an item costs, and the bounds on it": the paragraph that
  owns the rule.
- `openspec/changes/fix-a-cut-gates-the-work-it-keeps/specs/agent-issue-loop/spec.md`: the delta.

## What does not change

The item still ends `cut`, with the bound as its `stopReason`, no decision label and no closure: a gate result is not a
verdict, so incomplete work is not manufactured into `blocked`. The #851 ceiling stays one continuation per item, the
gate adds no child session (it is the profile's command, not a child), and `report.sh` is untouched — the note the
item's `resume #N` line already prints is what carries the result.
