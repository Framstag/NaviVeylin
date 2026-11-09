# Tasks

Reference: `proposal.md` for scope and the red run, `design.md` for why,
`specs/agent-issue-loop/spec.md` for the contract.

## 1. The red cases

Requirement: "The rate of items the run's own bound ended is printed beside the archive's" — scenarios "The run's
rate and the archive's are printed together, with what each item kept" and "A kept patch is priced with the files it
carries".

- [x] 1.1 Add `report#theBoundRateIsNamed`: a run of 11 items, 2 cut with `bound` (one keeping a 3-file patch), the
      archive beside it holding 17 mechanism-carrying records of which 3 are bound plus 5 written before the field —
      asserts `bound: 2 of 11 items (18.2 %)`, `archive: 3 of 17 items whose record names a mechanism (17.6 %)`, the
      two items named with what each kept, no closed item named, and the two empty shapes (`0 of 3 items (0.0 %)`,
      `archive: -`).
      Verify: on the base `2efea948` the case fails at the first assertion,
      `the run's rate, over the items it attempted: 'bound: 2 of 11 items (18.2 %)' not found` (`evidence/red-on-HEAD.log`).
- [x] 1.2 Add `state#aCutRecordsTheFilesItsPatchCarries`: three cuts — one naming a patch of three files, one with no
      path, one naming a path that does not resolve.
      Verify: on the base the case fails at
      `and the number of files that patch carries: expected '3', got 'null'` (`evidence/red-on-HEAD.log`).

## 2. The engine

- [x] 2.1 `tools/loop/report.sh`: `bound_share` (a share to one decimal, `-` where the population is empty), the
      archive read over `runs/` (or the project's `LOOP_RUNS_DIR`), and the `bound:` line beside the token band.
      Verify: `bash -n tools/loop/report.sh`; the assertion in 1.1; the live record of this tree's last run prints
      `bound: 6 of 9 items (66.7 %) · archive: 11 of 247 items whose record names a mechanism (4.5 %) …`.
- [x] 2.2 `tools/loop/state.sh`, `cmd_done`: record `leftoverFiles` — the `+++ ` lines of the patch `--leftover`
      names, read from the file, `null` where the path does not resolve — on the first record and on a second
      account of the same item.
      Verify: `bash -n tools/loop/state.sh`; the assertions in 1.2.

## 3. The rule's own words

- [x] 3.1 `guidelines/Backlog.recipes.md` §7b, beside the paragraph that measured the bound: the line, its
      class, the two denominators, `leftoverFiles`, and the fact that neither is a stop.
      Verify: `bash tools/look.sh 'leftoverFiles' --path guidelines` names §7b (`Backlog.recipes.md`).

## 4. Verification

- [x] 4.1 Structural check of every shell file the diff touches: `bash -n tools/loop/report.sh`,
      `bash -n tools/loop/state.sh`, `bash -n tools/loop/tests/loop-cases.sh`,
      `bash -n tools/loop/tests/loop-mutations.sh` — all clean, and `tools/check-doc-routes.sh` reports 329 section
      references resolve (`evidence/structural-checks.log`).
- [x] 4.2 The two cases green on the change (`evidence/green-on-change.log`).
- [x] 4.3 The cases the rule's surface owns still pass — `--group report` (25 cases), `--group state` (37 cases) and
      `run#childPastItsContextBoundIsCut` (`evidence/focused-cases.log`).
- [x] 4.4 One mutation per invariant and the whole set still applies: `--only '265.1 265.2'` red then green on the
      restore (`evidence/mutation-265.log`), `--check` 306 of 306 (`evidence/mutation-check.log`), and
      `tools/loop/measure.sh --change fix-a-bound-hit-rate-is-named` — 2 scenarios, 2 cases, none unnamed
      (`evidence/measure.txt`) — and the change's readiness is `LOOP-CHANGE-READY: ok` (`evidence/readiness.log`).
- [x] 4.5 The gate: `bash tools/loop/gate.sh --base main` — `LOOP-GATE-TREE: head=8640686da72ea67529dff7720b4b28f3e5311317 diff=6f5eccf9d90595dc`
      and `LOOP-GATE: tests=569 failures=0 errors=1 (…) — failing: tools/loop/tests/loop-selftest.sh --from-git main`.
      The one error is a step that overran its 1800 s bound, and it is waived: 385 of the 386 cases that selection
      names reported `PASS`, and the one that never returned — `run#aHupEndedRunRecordsItsStopAndUnpicksItsItem` —
      hangs identically on `main` in a throwaway worktree at `2efea948`, `timeout 300 … → exit=124` with no output
      there and on this change, which is the same reading the archived change `fix-a-transient-failure-gets-a-capped-retry`
      recorded of the same case (`evidence/waiver-gate-step-hup.txt`).
