# Why this change's gate is red (round 2)

The gate of this tree measured this diff and reported `LOOP-GATE: tests=1293 failures=3 errors=1`
(`gate.log`, last line). Every red in that tally is pre-existing and none of them is this change's
delta:

- `driver#anOwnedFunctionIsInItsOwnersFile` (`gate.log:1175`) — the layout table's `owns` cell for
  `report.sh` and `state.sh`, two files this change does not touch (`layout.tsv:22` is the only row
  this change moved, and it moved the bound of `evidence-check.sh`, whose `owns` cell is unchanged).
- `run#aQuotaHoldPausesTheRunAndResumesIt` (`gate.log:1515`) and `run#theQuotaSwitchIsExplicit`
  (`gate.log:1733`) — the run's quota cases, in `tools/loop/tests/loop-cases.sh`, untouched here.
- the one error is `tools/check-openspec-hygiene.sh` (`gate.log:1867-1870`): four untracked change
  directories of other items (`fix-an-adopted-closure-says-so`, `fix-an-adoption-is-recorded-as-one`,
  `fix-a-sensor-filing-carries-the-rows-type`, `fix-the-checkpoint-splits-the-items-chain`), none of
  them this change's.

The gate could not measure a clean base tree to tell red from new: `LOOP-GATE-BASERED-UNMEASURED`
(`gate.log:1758`, `gate.log:1874`) — "no clean tree at fa8c6aadd933 to measure it in, so its red stays
with the item". The round-1 review of the same tree recorded the same five reds as red at base
(`.pi/issue-loop/items/974/gate.log:1750`).

What this change's own arms did: `bash tools/loop/evidence-check.sh --selftest` prints **23** `ok`
arms and `evidence-selftest: PASS` (`selftest-after.log`), and the loop suite's own selection of this
change's cases is green — `ok a route resolves against the document's own heading`,
`ok a § the document does not have is a false pointer`,
`ok the refusal names the document's section list`,
`ok a dotted sub-number heading carries no id`,
`ok the dotted sub-number is not in the document's section list`,
`ok a heading with neither shape separator carries no id`,
`ok a report with no route line warns rather than failing`,
`ok and the same report fails once the project reads the route as required`,
`ok a paragraph quoting the form is not a route line` (`gate.log`, the `evidence-check.sh --selftest`
step).
