# fix-a-child-that-exits-zero-with-an-empty-answer — tasks

Parent requirement: `agent-issue-loop` — *"One command starts a budgeted run, with no dialogue"*, one scenario added
beside *"A child that never started is the run's fault, not the item's"*. Delta scenarios → cases in the table at the end.

## 1. The red case (before the fix)

- [x] 1.1 `run#aChildThatExitsZeroWithAnEmptyAnswerIsTheRunsFault` (`tools/loop-cases.sh`) — the stub exits 0 with an
      empty answer (no report block, no words of its own, no action in the tree), and the case asserts: one child was
      started, its answer is 0 bytes, the run ends with status 0, no item record exists, the recorded stop says
      "could not start" and names `0 B`, and no `agent/needs-decision`, no `status/on-hold`, no comment and no
      closure reach #21 while the in-progress mark comes off again.
- [x] 1.2 Red on the base: `bash tools/loop-selftest.sh --case run#aChildThatExitsZeroWithAnEmptyAnswerIsTheRunsFault`
      against `git archive 412ec81d` with only this change's `tools/loop-cases.sh` laid over it —
      `1 of 1 cases failed`, failing at *the item is not recorded as a blocked one: expected '0', got '1'*
      (`evidence/red-on-HEAD.xml`, `tests="1" failures="1"`, `evidence/red-on-HEAD.log`).

## 2. The fix

- [x] 2.1 `tools/loop/run.sh`: the guard at 1655 loses `[ "$worker_exit" -ne 0 ] && `, so the item's evidence — no
      verdict line, not ended by the run, tree at the base — decides the arm whatever status the child ended with
      (parent: the new scenario).
- [x] 2.2 The same arm names the answer's size in its stop reason (`… an answer of $answer_bytes B and no verdict
      line …`), so the exit-1-with-a-complaint and the exit-0-with-0-B shapes are told apart by a reader of the
      reason; the comment above it records both measurements (#212's 35-byte complaint, #742's 0-byte answer).
- [x] 2.3 The sibling case `run#childThatNeverStartedIsTheRunsFault` stays green unchanged: it asserts
      "could not start" and the child's own line, both of which the reworded reason still carries.

## 3. The mutations (one per case on the one line)

- [x] 3.1 `27.1` re-pointed at the reworded guard (its pattern quoted the old line) →
      `run#childThatNeverStartedIsTheRunsFault` red.
- [x] 3.2 `27.2` added with the same expression and `run#aChildThatExitsZeroWithAnEmptyAnswerIsTheRunsFault` red.
- [x] 3.3 Both went red when mutated and green after the restore, and `--check` reports every record applying
      (`evidence/loop-mutations.log`).

## 4. The gate

- [x] 4.1 `./tools/loop/gate.sh --base 412ec81d` — run **once**, exit 0. Its own line reads
      `LOOP-GATE: tests=782 failures=0 errors=0` and that pair is not retained as one XML, because it is the sum the
      gate computes over its 17 step tallies: each step's tally is under `app/build/test-results/loop-gate/` and is
      listed as a `LOOP-MEASURE-TALLY` line in `evidence/measure.txt`, while the case set's own pair
      (`tests=306 failures=0`) is carried by `tools_loop-selftest.sh_--from-git_412ec81d.xml` there. The measured
      clock is `LOOP-MEASURE-GATE seconds=721 suites=17` (`evidence/measure.txt`). The base is named because the
      run's own base commit is `412ec81d` and the gate's default (`merge-base HEAD origin/main` = `741603aa`, main's
      tip) would measure the previous item's landed work as this item's: the unrestricted plan owes the Gradle build
      and `gen-feature-list --check-classification`, the plan with this item's base owes neither (`gate.sh --plan`
      prints 21 lines against 17). Task 4.5 records what the narrower plan leaves out and why it is not this
      change's.
- [x] 4.2 The whole case set the delta owes, on its own: `bash tools/loop-selftest.sh --from-git 412ec81d` —
      **all 306 cases passed** in 6 min 11 s, offline, no token, no sandbox (`evidence/loop-selftest.log`);
      `LOOP-MEASURE-SUITE cases=306 cmd=tools/loop-selftest.sh --list` is the same set counted by the run's own
      measurement. No compiled file is touched — one shell guard, one case file, one mutation file and one
      guideline — so no Gradle task is owed; named exclusion: no build, no instrumentation test.
- [x] 4.3 `openspec validate fix-a-child-that-exits-zero-with-an-empty-answer --type change` reports the change
      valid (`evidence/openspec-validate.txt`), and `tools/check-openspec-hygiene.sh` passes on this tree.
- [x] 4.4 The scoped run before the gate: `bash tools/loop-selftest.sh --case '*hildThat*'` — both cases of this
      rule green (`evidence/green-cases.log`), and `--check` plus `--only "27.1 27.2"` on the mutations
      (`evidence/loop-mutations.log`).
- [x] 4.5 Named, not run: `gen-feature-list --check-classification` is red **in this worktree** — `unclassified: 2`,
      `auto/off-route-indicator` (owed by the open change `car-reroute-without-losing-the-map`) and
      `tooling-test-layout` (owed by `separate-tooling-test-code`), both delta specs of *other* open changes that
      live in this tree and whose classification their own changes owe
      (`guidelines/FeatureList.md`, "a capability is classified in the change that writes its delta"). It is
      owed for no path this change touches, and on the run's base commit it is green (measured: `git archive
      412ec81d` into a scratch tree, then the check → exit 0, `stale: 3` reported without failing).

## 5. The records

- [x] 5.1 This change: proposal, design, the delta on `agent-issue-loop`, these tasks, and `evidence/`.
- [x] 5.2 `guidelines/Backlog.recipes.md`, the section "Refusals, records and the landing" — the paragraph that stated the rule as "a worker that ended non-zero"
      now says the evidence decides and the status does not, naming #742.
- [x] 5.3 The rollback path named in `proposal.md` and `design.md`, and the two paths this change does not measure
      named in `design.md`'s "Not measured, and named".
- [x] 5.4 `bash tools/loop/evidence-check.sh openspec/changes/fix-a-child-that-exits-zero-with-an-empty-answer
      --root .` reports no dangling pointer (`evidence/evidence-check.txt`).

## 6. Delta scenarios → the case that exercises each

| delta scenario | case |
|---|---|
| The budget is arguments, not a prompt | `run#headerNamesTheLiveView` (unchanged) |
| The run stops on its own authority | `run#oneItemOrderIsFixed` (unchanged) |
| The floor the run refuses by is what its own items cost | `state#floorIsDerivedFromTheFinishedItemsWallTimes` (unchanged) |
| A run with no measurement keeps the configured minimum | `state#floorWithNoHistoryIsTheConfiguredMinimum` (unchanged) |
| A child that outlasts the configured wall-clock is ended and recorded | `run#childOutlastingTheWallClockIsBlocked` (unchanged) |
| A child that never started is the run's fault, not the item's | `run#childThatNeverStartedIsTheRunsFault` (unchanged) |
| A child whose answer arrived empty is the run's fault too | `run#aChildThatExitsZeroWithAnEmptyAnswerIsTheRunsFault` (new) |
