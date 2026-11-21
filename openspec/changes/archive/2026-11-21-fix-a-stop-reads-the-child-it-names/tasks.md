# Tasks — a stop that names a child reads that child's own write

Reference: `proposal.md` for the evidence, `design.md` for the single fix and the alternatives it eliminated,
`guidelines/Backlog.recipes.md:1225` "Refusals, records and the landing" for the section that owns the clause.

## 1. The reader takes the reading of the child a reason names

- [x] 1.1 `tools/loop/evidence.sh:419` — `item_outside_write_clause` takes an optional reading. An argument **replaces**
  the order (`worker_write_note` → `gate_write_note` → `outside_write_note`) instead of seeding it, so an empty
  argument is no clause at all; no argument keeps today's order, which is what the two arms that name no child read.
  Verify: `run#aStopWhoseChildWroteNothingGetsNoClause` is green (`evidence/focused-cases-991.log`), and the two
  no-name arms keep their clause: `run#anEmptyTreeWithAnOutsideWriteNamesThePaths` and
  `run#childClaimWithoutCommitIsBlocked` are green in the same run.
- [x] 1.2 `tools/loop/run.sh:1962` — the run's own could-not-start stop hands the clause the last child's own reading,
  `"${outside_write_note:-}"`, the one every `run_child` re-sets (`tools/loop/child.sh:627`).
  Verify: `run#aStopThatNamesAChildReadsThatChildsOwnWrite` is green (`evidence/focused-cases-991.log`).

## 2. The two cases, one per half of the rule

- [x] 2.1 `tools/loop/tests/loop-cases.sh` — `run#aStopThatNamesAChildReadsThatChildsOwnWrite`: the change child writes
  `demo-owned.txt` into the project's own tree and commits; the gate child answers nothing, rewinds the branch to the
  base it was handed and writes `demo-gate-<n>.txt` there; `LOOP_RETRY_MAX=0` so the stop is reached in one pass. The
  reason names `the gate worker` and `demo-gate-`, never `demo-owned.txt`.
- [x] 2.2 `tools/loop/tests/loop-cases.sh` — `run#aStopWhoseChildWroteNothingGetsNoClause`: the same stop with a gate
  child that rewinds and writes nothing outside its tree; the reason names the gate worker and carries no clause.
  Verify both: red on HEAD, `1 of 1 cases failed` each, with the failing assertion quoted
  (`evidence/red-on-HEAD-991.log`), green on the change (`evidence/focused-cases-991.log`).
- [x] 2.3 The `# files:` declaration each case carries at its first line stays what the case can reach; the one case
  that reads it is `driver#everyCaseDeclaresTheFilesItCanReach`, green in the same focused run.

## 3. The two mutation records

- [x] 3.1 `tools/loop/tests/loop-mutations.sh` — record `991.1` drops the argument the stop passes (so the clause falls
  back to the change child's reading) and record `991.2` makes the guard fall back (so an empty reading of the named
  child is answered with an earlier child's). One case each: 991.1 →
  `run#aStopThatNamesAChildReadsThatChildsOwnWrite`, 991.2 → `run#aStopWhoseChildWroteNothingGetsNoClause`.
  Verify: `tools/loop/tests/loop-mutations.sh --only '991.1 991.2'` — both RED, both restore green on this tree
  (`evidence/mutation-991.log`, `red-when-mutated: 2 · not-noticed: 0 · restore-failures: 0`, 2026-11-21T18:17Z);
  `--check --only '991.1 991.2'` also answers "every selected record applies to the file it names".

## 4. The rule the change states

- [x] 4.1 `guidelines/Backlog.recipes.md:1225` "Refusals, records and the landing" — the paragraph that owns the
  clause states which reading each site reads:
  the two arms that name no child keep the order, and the run's own stop carries the reading taken around the child its
  sentence names, empty or not.
  Verify: `tools/look.sh '^## ' --path guidelines/Backlog.recipes.md` lists "Refusals, records and the landing" at
  `:1225` as the heading of the section the paragraph sits in.
- [x] 4.2 `openspec/changes/fix-a-stop-reads-the-child-it-names/specs/agent-issue-loop/spec.md` — one ADDED
  requirement, "A reason that names one child carries that child's reading", whose two scenarios name exactly the two
  cases; #983's requirement for an empty tree is left as it stands.
  Verify: `./tools/loop/measure.sh --change fix-a-stop-reads-the-child-it-names` prints
  `LOOP-MEASURE-CHANGE fix-a-stop-reads-the-child-it-names scenarios=2 cases=2 unnamed=0`
  (`evidence/measure.log`), and `openspec validate fix-a-stop-reads-the-child-it-names --strict` answers "is valid"
  (`evidence/structural-checks.log`).

## 5. The checks that stand in, and the gate that is not this session's

- [x] 5.1 No compiled file is in this delta, so no build stands in for it: the structural checks of the files it edits
  do. `openspec validate fix-a-stop-reads-the-child-it-names --strict` answers "is valid", and
  `tools/check-openspec-hygiene.sh` reports five **other** open changes' missing `x-issue:` (this change's
  `.openspec.yaml` carries `x-issue: 991`) — none for this change (`evidence/structural-checks.log`).
- [x] 5.2 The gate is **not** this session's: the run defers it to a fresh child that reads this diff first, completes
  the change (its pointers, its delta and its archive) and runs `./tools/loop/gate.sh` over that completed tree. No
  gate number is claimed here, and `LOOP-DIFF-READY` is what this session prints.
