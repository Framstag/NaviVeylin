# The gate's two failures, and why neither is this change's

The gate ran once on the corrected tree (`./tools/loop/gate.sh --base 122cc2f9` in the item's worktree), its log is
`evidence/gate.log`, its step tallies `evidence/gate-tallies/`, and its roll-up `evidence/gate-summary.txt`. It came
back with two failures in `tools/loop-selftest.sh --from-git 122cc2f9` (316 cases, 1 012 s) — one red on the base
commit's own inputs, one a timing flake on a case this change does not touch. This file is the attribution.

## What the gate reported

- Base: `122cc2f9`, the run's own base. The gate ran no build, and owed none: this change touches no compiled file,
  so `LOOP_GATE_COMPILED_PATTERNS` selects nothing (`guidelines/Build.md` §2, the scope block).
- Failing step: `tools/loop-selftest.sh --from-git 122cc2f9`. Its tally
  (`evidence/gate-tallies/tools_loop-selftest.sh_--from-git_122cc2f9.xml`, `tests="316" failures="2" errors="0"`,
  1 012 s) is 316 cases, two failures, no errors. The gate's roll-up is `gate.log:1375`.
- The two failing cases, verbatim from `gate.log:374` and `:499`, with the suite's verdict at `:907`:

  ```
  FAIL gate#theMapCoversEveryPipelineStep
  FAIL run#theWorkersOwnShellWriteIsRecordedAndNotPlaced
  loop-selftest: 2 of 316 cases failed
  ```

- The item's own cases are green in that same run: `PASS run#aReviewerEndedAtItsTurnBoundIsNotARefusal`
  (`gate.log:471`), `PASS run#childPastItsTurnBoundIsBlocked` (`:903`), `PASS run#theRecordedZeroKeepsTheTurnBoundOff`
  (`:904`), `PASS run#childPastItsContextBoundIsBlocked` (`:905`), `PASS run#theRecordedZeroKeepsTheContextBoundOff`
  (`:906`).

## Failure 1 — `gate#theMapCoversEveryPipelineStep`, red on the base commit's own inputs

### What the case reads

`case_gate_theMapCoversEveryPipelineStep` (`tools/loop-cases.sh:3721-3757`) compares two things and nothing else: the
`bash`/`sh` commands of the workflow's `Check …` steps (the pipeline pinned by `LOOP_GATE_PIPELINE`,
`.github/workflows/build.yml`) against the command side of `LOOP_GATE_SUITES` (`tools/loop/loop.conf:485`). It reads
no other file and no other key.

### What this change did to those two inputs

Nothing, mechanically:

```
$ git diff --name-only 122cc2f9 -- .github/workflows        # empty
$ git rev-parse 122cc2f9:.github/workflows/build.yml HEAD:.github/workflows/build.yml
6fddab6b8ec9aa4d75f2c936af81d79a1ea4d495
6fddab6b8ec9aa4d75f2c936af81d79a1ea4d495
$ sed -n 's/^LOOP_GATE_SUITES="\(.*\)"/\1/p' tools/loop/loop.conf | git hash-object --stdin
f1278a8f049fd237eadac9223b3edb78c7fac603
$ git show 122cc2f9:tools/loop/loop.conf | sed -n 's/^LOOP_GATE_SUITES="\(.*\)"/\1/p' | git hash-object --stdin
f1278a8f049fd237eadac9223b3edb78c7fac603
```

— the two hashes are equal: this change's `loop.conf` hunk adds
`LOOP_ITEM_CONTEXT_WARN` / `LOOP_ITEM_CONTEXT_MAX` below `LOOP_ITEM_TURNS_MAX` and touches no other line.

### What proves it was red before this work

`evidence/gate-map-covers-base-inputs.txt` runs the case's own extractor over the **base blobs**
(`git show 122cc2f9:.github/workflows/build.yml`, `git show 122cc2f9:tools/loop/loop.conf`) and prints the same line
the gate printed: `tools/loop-selftest.sh --group "$g"` is a command of the pipeline's Check steps that no row of the
suite map carries. The workflow was byte-identical at the base and at the tree the gate ran on, so the case could not
have been green there either. It is a defect of the gate map, owned by whatever moves the offline shell gates into
legs — not by a change to `run.sh`'s bounds.

## Failure 2 — `run#theWorkersOwnShellWriteIsRecordedAndNotPlaced`, a load-induced flake

### What the case stages, and what it reads

The case (`tools/loop-cases.sh:5732-5782`) stages a shell that writes into the child's project and sleeps **8 s**
(`$d/project-shell.sh`), then asserts that the reading prints that shell's own pid in
`LOOP-OUTSIDE-WRITE-ALIVE:` — the half that separates "found and placed by nothing" from "never found at all". The
reading is one sweep per second while the child runs. The case's assertion is therefore a race against an 8-second
window whose reader is a once-a-second sweep, and the failure it printed is the window having closed before the
sweep ran:

```
run#theWorkersOwnShellWriteIsRecordedAndNotPlaced|   the reading names the shell that wrote among the processes standing in the project — the shape was found: ' 1908523 ' not found in: LOOP-OUTSIDE-WRITE-ALIVE: (none) · attributed=none
```

### Why it is a flake and not a regression

Four runs of exactly this case on the same tree, three of them made for this attribution:

```
$ ./tools/loop-selftest.sh --case 'run#theWorkersOwnShellWriteIsRecordedAndNotPlaced'   # x4
PASS run#theWorkersOwnShellWriteIsRecordedAndNotPlaced   (1st: before the gate)
PASS run#theWorkersOwnShellWriteIsRecordedAndNotPlaced   (2nd)
PASS run#theWorkersOwnShellWriteIsRecordedAndNotPlaced   (3rd)
PASS run#theWorkersOwnShellWriteIsRecordedAndNotPlaced   (4th)
```

The suite it failed in took **1 012 s**, 2.5× the 402 s the same 316 cases took in this item's earlier gate run
(`evidence/gate-summary.txt`, the earlier roll-up), so the machine was heavily loaded while the case ran: a
once-a-second sweep against an 8-second sleep loses that race when the scheduler is contended, and the staged shell
is then gone by the time the reading lands. Nothing in this change touches the outside-write walk, the heartbeat,
`pid_left_the_worktree` or the fixture that stages the shell — this change's diff in `run.sh` is `child_context`, the
two keys' validation, one header field pair, the two decisions in `run_child`'s wait and one `LOOP-TIMEOUT` arm.

### What that means for this archive

Two failures, both attributed and neither caused by this change; 314 of 316 cases green, including every case this
change adds and the two shipped cases whose wording it corrected. The flake is recorded here rather than repaired
because a change to the case's 8-second window is a change to a case this item does not own (`guidelines/Build.md`
§2a: the item owes the verification its roster names).
