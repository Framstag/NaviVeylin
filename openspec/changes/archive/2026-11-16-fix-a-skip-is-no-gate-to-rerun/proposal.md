# A skip is no gate to re-run

## Why

**Root cause.** The report's `re-run the gate for #N` arm (`tools/loop/report.sh:972` — comment `:961-968`, action line
`:971` — on the base commit `b865087f`) is one `jq` selection over the run's `.items`: every item whose result is not
`done`, `falsified` or `cut` and whose `metrics.gate` is empty. Those two conditions are a claim about the gate and
nothing about the item behind it, and the run records four shapes that satisfy them. Only one of those shapes had
something to gate:

| arm (`tools/loop/run.sh`) | result | `--change` recorded | gate result |
|---|---|---|---|
| closed (`:3964`) | `done` | `--change "$change"` | yes |
| blocked (`:4154`) | `blocked` | `--change "$change_claim"` | empty when the gate child left no tally |
| skip (`:4043`) | `skipped` | none | empty, by construction |
| falsified (`:3992`) | `falsified` | none | empty, by construction |

So the arm's own test was half of the predicate it needed, and the missing half is the change: it is the field that says
this item reached work a gate can measure. A `skipped` item — a worker that answered at the eligibility gate, planned
nothing and changed nothing — was handed a gate to re-run over a tree that never held a change.

**Evidence, verified against the tree and the run's own record (issue #881).** The item was filed from the run of
`2026-11-13T05:47:11Z`. Its record (`.pi/issue-loop/runs/2026-11-13T05:47:11Z.json` in the project tree) holds:

```
$ jq -r '.items[] | select(.issue==860 or .issue==706 or .issue==861)
    | [.issue,.result,.verdict,(.change//"-"),(.metrics.gate//"-")] | @tsv' \
    .pi/issue-loop/runs/2026-11-13T05:47:11Z.json
860	skipped	needs-decision	-	-
706	skipped	too-wide	-	-
861	skipped	needs-diagnosis	-	-
```

and their steps, which is how far each item got:

```
$ jq -r '[.steps[] | select(.issue==860 or .issue==706 or .issue==861)] | .[]
    | [.issue,.phase,.name,.status]|@tsv' .pi/issue-loop/runs/2026-11-13T05:47:11Z.json
860	A	start	ok
860	A	worker	ok
706	A	start	ok
706	A	worker	ok
861	A	start	ok
861	A	worker	ok
```

So each of the three held no `change`, no `metrics.gate` and no step past the worker — the report's arm read the second
of those as the gate a caller is owed. The arm's own selection, run over that record with `jq`, names `860 706 861`; with
the clause this change adds it names no item of that run at all.

**The item's own direction did not hold, and the change names what does.** The item asks for one condition beside the
existing gate-result test: that "the item's state holds a worker step", on the reading that "a skipped item holds
neither". A skipped item **does** hold a worker step — `tools/loop/run.sh:2836` records `step A --name worker` for every
worker child it starts, whatever that child answered, and the state above shows it on all three items. The field that
separates them is the change: the skip arm records none, and a gate over no change is the work that cannot exist.

**The red test.** `run#aSkipIsNoGateToRerun` (`tools/loop/tests/loop-cases.sh`) fails on the base commit `b865087f`,
with the case added and `tools/loop/report.sh` unmodified (`evidence/red-on-HEAD.log` and its JUnit carrier
`evidence/red-on-HEAD.xml`; tests=1 failures=1, 2026-11-16T01:52:04Z): the state it builds — two `skipped` items and one
blocked item that recorded a change — prints `re-run the gate for #21` and `re-run the gate for #23`, and the case's
first assertion refuses it. The same case is green on this tree (`evidence/green-case.log`, `evidence/green-case.xml`,
tests=1 failures=0, 2026-11-16T01:53:12Z), and its third and fourth assertions are the arm's other half: an item that
recorded a change and no gate result is still named, with the transcript beside its id.

**Two cases had to follow the arm.** `run#theGateColumnNamesThisItemsGateResult` built a skipped item and asserted the
line this change removes, so its two action assertions are realigned (`evidence/realigned-case.log`): the ungated item
is not named, and the item whose gate result its record carries is not named either. `report#anActionNamesAnItemPathThatExists`
reads the transcript path off the gate line, and its second item recorded no change — a fixture, not the arm — so that
fixture now records `--change fix-demo` and keeps covering the line's path (`evidence/fixture-case.log`).

**The owning capability and its guideline section.** The capability is `agent-issue-loop` — the requirement this change
adds is a statement about the report's action list. The owning guideline section is `guidelines/Backlog.recipes.md`
section 7c "The gate and its suite" (`:877`), whose paragraph on the empty gate field (`:925-932`) carried the claim this change
narrows and says so: the sentence that read "the item appears on the report's `re-run the gate for #N` line" now names
the change beside the field, and carries the 2026-11-13 measurement.

**Surface, counted over the tree this change lands in.** The rule's text lives in five live files; each count is the matches of
`re-run the gate` in that file, with the files named by `tools/look.sh 're-run the gate'` (every other match in the tree
is an archived change's own evidence, history this change neither reads nor rewrites): `tools/loop/tests/loop-cases.sh`
10 (the case that asserts the line and the realignments this change owes), `tools/loop/report.sh` 1 (the arm),
`tools/loop/tests/loop-mutations.sh` 1 (the comment of the #849 record whose case is one of the realigned — the record
itself is unchanged, and the case's gate-field assertion still catches it), `guidelines/Backlog.recipes.md` 1 (the
paragraph above), `openspec/specs/agent-issue-loop/spec.md` 4 (the requirements the report's action list already answers
to). So: 1 case added, 2 cases realigned, 1 arm narrowed, 1 guideline paragraph narrowed.

surface: 1 case added, 2 cases realigned, 1 arm narrowed, 1 guideline paragraph narrowed — `tools/look.sh 're-run the gate'`

## What changes

- `tools/loop/report.sh` — the arm's selector gains `select((.change // "") != "")`, one condition beside the existing
  gate-result test, with the comment that says which record carries a change and which does not. No state field, no
  engine contract.
- `tools/loop/tests/loop-cases.sh` — `run#aSkipIsNoGateToRerun` added (registered in the case list and the dispatcher,
  declaring its files); `run#theGateColumnNamesThisItemsGateResult`'s two action assertions realigned, because the
  skipped item it built no longer carries the line; `report#anActionNamesAnItemPathThatExists`'s second fixture given a
  `--change`, so the gate line it reads the transcript path off is still printed.
- `guidelines/Backlog.recipes.md` — the empty gate field's paragraph names the change beside the field and carries this
  item's measurement.

## Impact

Report output only: an action line that named work nobody can do stops being printed. Nothing that reads the state
changes, no field is added, and an item that did reach a change and recorded no gate result is named exactly as it was.
