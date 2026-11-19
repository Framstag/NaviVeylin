# The gate for this change's own diff, and the one case it does not own

Written 2026-11-19, for the console beside this file (`gate-911-round3.log`) and the base measurement
(`base-red-6b0e01a7-loop-selftest-from-git.log`).

## The verdict this change carries

- `LOOP-GATE-BASE: 6b0e01a7 (from --base)`
- `LOOP-GATE-TREE: head=7ee8cc36a870db4e732c1b67b9589be3db2c6cba diff=8bb77c6e3cef431b`
- `LOOP-GATE: tests=1259 failures=1 errors=0 (…) — failing: tools/loop/tests/loop-selftest.sh --from-git 6b0e01a7`
- the failing case: `run#aHupEndedRunRecordsItsStopAndUnpicksItsItem` —
  "a run that took HUP did not end inside 4800 ticks (30 s for each of the 16 case(s) the pool starts beside
  this one; status 124) — its stop was '' and its item ''"

This is the first console whose `LOOP-GATE-TREE` names the tree under review, and it is the one the round-2
review's blocking finding asked for: the two earlier greens measured `diff=56cec705c37d268b` (the
pre-correction tree) and `diff=729aacbdb34c424d` (an empty diff, doc checks only). Every step of this gate
except the one named above passed.

## Why the one red is not this change's

- **The case is the base's, measured.** The same step was run in a clean detached worktree at the item's base
  `6b0e01a7`, with nothing of this change in it: `loop-selftest: 1 of 479 cases failed — 884.43 s`, and the one
  failing case is `run#aHupEndedRunRecordsItsStopAndUnpicksItsItem` with the same message
  (`base-red-6b0e01a7-loop-selftest-from-git.log`). A case red on the base is not charged to the delta
  (`guidelines/BuildEvidence.md` §4d, "a red the base carried is subtracted from the tally, by pair"), and this
  measurement is that pair without the run state that would let `gate.sh` subtract it automatically.
- **The change does not touch it.** The delta edits `tools/issues/check-metadata.sh`, `tools/issues/create.sh`,
  `tools/issues/lib.sh`, the issue tooling's fixtures and cases, and two prose files of this archived change.
  `tools/loop/tests/loop-cases.sh`, where the case lives, is not in the delta (`git diff --stat 6b0e01a7..HEAD`).
- **It passes when it is run alone.** Two focused reruns in this same tree, on the same day, both printed
  `PASS run#aHupEndedRunRecordsItsStopAndUnpicksItsItem`. The case is a timing case — the HUP trap of a run
  started under load — and its own history is the same shape (`guidelines/Backlog.recipes.md`, "the clock a case
  keeps is the pool's width, not a duration the host measured", issues #928 and #949).

## A third attempt, and why its console is not evidence

A run of the same gate with the profile pinned to a serial pool (`LOOP_CONF` pointing at a copy of `loop.conf`
whose only difference is `LOOP_GATE_POOL_JOBS=1`) was taken and then **quarantined**
(`gate-911-round5-INVALID-pinned-conf.log`, outside the archive). It is not evidence for this change: the pin
leaks into the suite's own foreign-project cases, which build a synthetic project and read the caller's profile
— three of them (`foreign#toolchainRunsEndToEnd`, `foreign#emptyGateCommandStillRunsThePhases`,
`porting#everyCommandInTheRecipeRuns`) pass under the tree's own profile and fail under the pinned one. Its
console names itself "pinned; not the profile beside this engine", which is the fact a reader needs to discard
it.

## Falsification, kept from the round it was taken in

`revert-checks-911.log` — each of the sixth invariant's mutations turns its named case red and the restore
green. The declared mutation set (`tools/issues/tests/metadata-mutations.sh`) still carries no entry for the
sixth invariant; that NOTE is recorded in the round-2 review and deliberately left out of this landing.
