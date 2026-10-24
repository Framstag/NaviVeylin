# Evidence — fix-loop-child-outlives-its-deadline (issue #227)

Base: `49dfd7db` (`origin/main`, 2026-10-23). The change touches no compiled file, so the verification it owes is
the loop's own shell gate (`tools/loop/gate.sh`) and `openspec validate --strict`; no Gradle run is claimed.

## 1. The red case on HEAD

`run#childIsEndedAtTheRunsDeadline`, added to `tools/loop-cases.sh`, run with the HEAD `tools/loop/run.sh`
restored (`git stash push -- tools/loop/run.sh`), 2026-10-24T08:09Z:

```
bash tools/loop-selftest.sh --case 'run#childIsEndedAtTheRunsDeadline'
  the run was still waiting 60 s after its own recorded deadline passed
FAIL run#childIsEndedAtTheRunsDeadline
loop-selftest: 1 of 1 cases failed
```

- `evidence/red-HEAD.log` — the run's own output.
- `evidence/red-HEAD.xml` — the tally derived from that summary line, in the shape the gate writes:
  `tests="1" failures="1" errors="0"`.

The same case is green on the fixed tree (`PASS run#childIsEndedAtTheRunsDeadline`, quoted in the gate log below).

## 2. Why the case cannot pass on HEAD for a wrong reason

The fixture's child blocks on a fifo and is never released, the child cap is set to 3 600 s in the profile the
case hands the run, and only the run's *recorded* deadline is moved two seconds away. A run that reads the
deadline ends the child and exits; a run that does not stays in the poll until the case's own 60 s bound, which
is what the red log shows. A shorter `--minutes` cannot be used: `state.sh next` refuses the item against the
item floor before any child starts, which is the run behaving as designed.

## 3. Mutation — the invariant, falsified and restored

`tools/loop-mutations.sh --only '23.1 23.2'`:

- `23.2` — the deadline branch replaced by `if false; then` → `RED 23.2 run#childIsEndedAtTheRunsDeadline`,
  `restore 23.2 … green again` (`evidence/mutations-23.log`).
- `23.1` — the cap branch, whose comparison line the fix rewrote, realigned and re-run: `RED 23.1
  run#childOutlastingTheWallClockIsBlocked`, `restore 23.1 … green again`, `red-when-mutated: 1 · not-noticed: 0
  · restore-failures: 0` (`evidence/mutations-23-1.log`).

A first pass at `23.1` reported `RESTORE-FAILED … stayed red after the restore` while the host was shared with
other suite runs; the case re-ran green on the clean tree by hand (`PASS
run#childOutlastingTheWallClockIsBlocked`) and then again under the harness, which is the record above. The flake
is the pre-existing 10-second bound that case gives the run, not a change of behaviour.

`23.3` — a mutation of the `stop_run` the deadline arm writes — was **removed** from the set: the case did not
notice it, and it cannot, because `state.sh next` overwrites that stop with the deadline it names on the
ordinary path. The line stays in the engine for the run that is killed between the child and `next`; no case
reaches that window deterministically, and the comment in `tools/loop-mutations.sh` says so instead of a record
that would pass by asserting an adjacent thing.

## 3a. The snapshot the independent reviewer read

The reviewer read a frozen copy of the change (`.pi/tmp/review/`) taken while `tools/loop-mutations.sh` was
running, so that copy carried mutation `12.2`'s `cd "$worktree"` line in `run.sh` — a mutant, not the change. The
reviewer caught it from the gate's own evidence (`PASS run#childStartsInTheProjectRoot`, and `--check`'s "all 133
cases passed — every selected record applies", which `12.2`'s pattern would fail), and the working tree keeps
`cd "$LOOP_ROOT"`. The review raised two blocking artifact contradictions, both corrected here: the removed
`23.3` record is no longer claimed, and the spec's second scenario no longer claims the deadline outranks the
item cap.

`tools/loop/gate.sh` from the change root on the pre-merge tree, 2026-10-24T08:11Z–08:35Z
(`LOOP-GATE-PROFILE: …/issue-227/tools/loop/loop.conf`; the suite's own scratch runs are
`.pi/tmp/loop-selftest…/repo/.pi/issue-loop/runs/2026-10-24T08:11:46Z.json` … `08:31:07Z`). The run kept as
`evidence/gate-premerge.log` is that one; `evidence/gate.log` and `evidence/gate-tallies/` are the re-run on the
merged tip, below.

```
LOOP-GATE: tests=377 failures=1 errors=0 (tools/loop-selftest.sh --from-git 49dfd7db13a763bf6674471dedbbb56bdd9e4b52 1317s, bash tools/loop-mutations.sh --check 1s, tools/loop-mutations-selftest.sh 183s, tools/check-doc-routes.sh 0s, tools/check-doc-routes-selftest.sh 1s, tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list 0s, tools/check-todo-references.sh 1s) — failing: tools/loop-selftest.sh …
```

- `evidence/gate.log` — the gate's own output, including `PASS run#childIsEndedAtTheRunsDeadline` and
  `PASS run#childOutlastingTheWallClockIsBlocked`.
- `evidence/gate-tallies/` — the seven per-suite tallies: `210/1` (selftest), `133/0` (mutations `--check`),
  `34/0` (mutations self-test), `0/0` (`tools/check-doc-routes.sh`), `0/0`
  (`tools/check-doc-routes-selftest.sh`), `0/0` (`tools/mutate.sh --list`), `0/0`
  (`tools/check-todo-references.sh`). Their sum is the gate's 377 cases and 1 failure.

The one failure is **not this change's**: `run#aWriterTheRunCanPlaceBlocksTheItem` fails identically with the
HEAD `tools/loop/run.sh` restored (`git stash push -- tools/loop/run.sh`) — `evidence/attribute.log` records the
pair, and `evidence/writer-flake.log` records four consecutive failures on the fixed tree, so it is not a flake.
It is the outside-write attribution on `origin/main`'s tip — the item `#288` ("the outside-write case fails on a
clean tree"), which has its own unmerged fix branch — and this change touches no attribution code.
`run#childOutlastingTheWallClockIsBlocked`, which this change's poll rewrite does touch, passed in the gate and
again by hand.

### The gate re-run on the merged tip

`origin/main` moved by five commits while this change was made (buildSrc's JVM, the suite's own teardown, an
executable bit); the change was merged onto that tip (`9398a016`, auto-merged through `tools/loop-cases.sh` and
`tools/loop-mutations.sh` with no conflict) and the gate re-run there:

```
LOOP-GATE: tests=379 failures=2 errors=0 (tools/loop-selftest.sh --from-git a0ca0f35… 1330s, bash tools/loop-mutations.sh --check 1s, tools/loop-mutations-selftest.sh 192s, tools/check-doc-routes.sh 1s, tools/check-doc-routes-selftest.sh 3s, tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list 0s, tools/check-todo-references.sh 2s)
```

Tallies: `211/2` (selftest, the 210 of the pre-merge run plus `#233`'s case), `134/0` (`--check`), `34/0`, four
`0/0` suites — 379 and 2. The two failures are the pre-existing `#288` case above and
`run#childOutlastingTheWallClockIsBlocked`, which is a **bound of that case, not of this change**: five runs on the
pre-change `run.sh` (`git show 49dfd7db:tools/loop/run.sh`) gave 4 PASS / 1 FAIL, five runs with the change gave
4 PASS / 1 FAIL at the same host load (`evidence/cap-case-repeat-head.log`, `evidence/cap-case-repeat.log`). The
case reads the run's console 10 s after the cap fires; on a host carrying other suites the run's teardown (the
sweep, the escalation, the report) sometimes needs longer, before and after this change alike.

One record's judgement is blocked by that same pre-existing red: `12.10` mutates the in-time reading and its case
is `run#aWriterTheRunCanPlaceBlocksTheItem`, so the harness reports `RESTORE-FAILED 12.10 … stayed red after the
restore` in `evidence/mutation-run-sh.log` — the case was red before the record ran. Its pattern still applies
(`tools/loop-mutations.sh --check` selected 133 records with no `MUTATION-DID-NOT-APPLY`).

## 3b. The full mutation set of the engine file this change touches

`tools/loop-mutations.sh --files tools/loop/run.sh` (the set derived from the file, not from the diff):
`red-when-mutated: 58 · not-noticed: 0 · restore-failures: 3` (`evidence/mutation-run-sh.log`). Both records this
change adds or realigns are in it and behaved: `RED 23.1 / restore … green again`, `RED 23.2 / restore … green
again`.

The three restore failures, each read rather than waved away:

- `12.10` — its case is `run#aWriterTheRunCanPlaceBlocksTheItem`, red before the record ran (#288).
- `34.1` and `34.2` — both cases are `run#prescreenAnnouncesItselfBeforeItReturns`, which fails on this host
  after the gate that passed it: its first assertion reads the console 1.5 s after the run starts, and on this
  shared host the copied engine has not reached the pre-screen by then. It fails **identically with HEAD's
  `run.sh`** restored (`git stash push -- tools/loop/run.sh`), so it is not this change
  (`evidence/prescreen-attribute.log`), and the gate ran it green (`gate.log:374`).

## 3c. The post-review correction

The independent review (task 7a) raised one code note: the blocked reason of a rework worker the *cap* ended was
reused for a rework worker the *deadline* ends (`tools/loop/run.sh:1386`, whose text named the cap). The wording
now names both clocks and keeps the child's own `LOOP-TIMEOUT` text, which the deadline arm names. The one-line
change is after the gate, so the cases it can reach were re-run and are quoted in
`evidence/post-review-cases.log`: `PASS run#childIsEndedAtTheRunsDeadline`, `PASS
run#childOutlastingTheWallClockIsBlocked`, `PASS run#treeOfAChildTheCapEndedIsNotCommitted`, `PASS
run#treeOfACapEndedReworkOfARefusingRoundIsNotCommitted`, and `tools/loop-mutations.sh --check` — "all 133 cases
passed — every selected record applies". The same log's fifth case,
`run#prescreenAnnouncesItselfBeforeItReturns`, failed there for the pre-existing reason §3b records: it fails
identically with HEAD's `run.sh` (`evidence/prescreen-attribute.log`).
