# Tasks — what ran, and what it printed (2026-11-05)

Every command below ran in the change root `/home/tim/projects/NaviVeylin/.pi/worktrees/loop`.

## 1.1–1.3, 2.1 — the engine and its recorded numbers

```
$ bash -n tools/loop/run.sh          # exit 0
$ bash -n tools/loop-cases.sh        # exit 0
$ bash tools/loop-mutations.sh --check
loop-mutations: all 235 cases passed — every selected record applies to the file it names   # exit 0
$ grep -n '^LOOP_ITEM_CONTEXT' tools/loop/loop.conf
134:LOOP_ITEM_CONTEXT_WARN=143124
135:LOOP_ITEM_CONTEXT_MAX=200000
```

## 2.2–2.3 — the guidelines

```
$ ./tools/check-doc-routes.sh
check-doc-routes: all 329 section references resolve, all 5 skill references resolve   # exit 0
$ ./tools/check-portability.sh
check-portability: OK — 56 engines and 7 templates name no project value (116 value(s) read from the project)   # exit 0
$ ./tools/check-todo-references.sh
check-todo-references: 66 citation(s) resolve through the recorded table   # exit 0
$ ./tools/check-openspec-hygiene.sh
OpenSpec artifacts are consistent: 5 open change(s) carry task markers, …   # exit 0
```

## 3.1 — the cases are registered

```
$ ./tools/loop-selftest.sh --list | tail -4
run#childPastItsTurnBoundIsBlocked
run#theRecordedZeroKeepsTheTurnBoundOff
run#childPastItsContextBoundIsBlocked
run#theRecordedZeroKeepsTheContextBoundOff
$ ./tools/loop-selftest.sh --case 'driver#everyCaseDeclaresTheFilesItCanReach'
PASS driver#everyCaseDeclaresTheFilesItCanReach
```

## 3.2 — the red cases are green, and the turn pair is unchanged

```
$ ./tools/loop-selftest.sh --case 'run#childPastItsContextBoundIsBlocked'
PASS run#childPastItsContextBoundIsBlocked
$ ./tools/loop-selftest.sh --case 'run#theRecordedZeroKeepsTheContextBoundOff'
PASS run#theRecordedZeroKeepsTheContextBoundOff
$ ./tools/loop-selftest.sh --case 'run#childPastItsTurnBoundIsBlocked'
PASS run#childPastItsTurnBoundIsBlocked
$ ./tools/loop-selftest.sh --case 'run#theRecordedZeroKeepsTheTurnBoundOff'
PASS run#theRecordedZeroKeepsTheTurnBoundOff
```

The same two new cases on the base commit `122cc2f9`, before the fix, are `evidence/red-on-head.log`:
one fails on the missing header field, and one on a run still waiting 60 s after the child's stream said
210 000 tokens against a recorded bound of 200 000.

## 4.1 — the checks the pathspec owes

`evidence/measure.txt` is `./tools/loop/measure.sh --change fix-a-child-is-bounded-by-its-context --base 122cc2f9`:
`paths=5 insertions=284 deletions=7`, `LOOP-MEASURE-SUITE cases=316` (314 before this change's two),
`LOOP-MEASURE-CHANGE … scenarios=2 cases=2 unnamed=0`. The checks it names are the gate's, not this session's: the
gate was deferred to a fresh child by the run, so this session ran the focused ones above (the suite's own cases
for what it changed, the mutation set that must still apply to `run.sh`, and the four structural checks on the
files it edited) and stopped at the diff.

## 4.2 — nothing is archived here

```
$ git status --porcelain -uall
 M guidelines/Backlog.recipes.md
 M guidelines/BacklogTooling.md
 M tools/loop-cases.sh
 M tools/loop/loop.conf
 M tools/loop/run.sh
```

`openspec/changes/fix-a-child-is-bounded-by-its-context/` is untracked by design (`.gitignore:76` ignores
`openspec/changes/*/`; the archive step adds it). No archive, no gate, no `LOOP-GATE` line from this session.

## 5.1–5.3 — the pre-gate round's corrections, and the gate that followed

The round answered `VERDICT: FAIL` with one blocking finding after the session above stopped
(`evidence/pre-gate-review.md`); the run that took the round was ended by the turn bound while applying the
corrections, so they were applied by the session that finished the item. What was applied, declined and removed is
`evidence/review-corrections.md`.

```
$ bash -n tools/loop/run.sh                     # exit 0
$ ./tools/loop-selftest.sh --case 'run#aReviewerEndedAtItsTurnBoundIsNotARefusal'
PASS run#aReviewerEndedAtItsTurnBoundIsNotARefusal
$ ./tools/loop-selftest.sh --case 'run#childPastItsContextBoundIsBlocked'
PASS run#childPastItsContextBoundIsBlocked
$ ./tools/loop-selftest.sh --case 'run#theRecordedZeroKeepsTheContextBoundOff'
PASS run#theRecordedZeroKeepsTheContextBoundOff
$ ./tools/loop-selftest.sh --case 'run#childPastItsTurnBoundIsBlocked'
PASS run#childPastItsTurnBoundIsBlocked
$ ./tools/loop-selftest.sh --case 'run#theRecordedZeroKeepsTheTurnBoundOff'
PASS run#theRecordedZeroKeepsTheTurnBoundOff
```

`evidence/green-review-case.log` is that selection with its timestamp. The reviewer's grep for the removed claims
prints nothing:

```
$ grep -rn "every provider this project runs sits at\|megabytes per item\|40 k or 240 k\|red-bound-on-head" \
    tools/loop/run.sh tools/loop/loop.conf guidelines/Backlog.recipes.md guidelines/Backlog.md guidelines/BacklogTooling.md
$ echo $?
1
```

The delta now carries the two `## MODIFIED Requirements` blocks (generated from
`openspec/specs/agent-issue-loop/spec.md` line for line, so every scenario of both requirements is repeated
verbatim), and the ADDED requirement's body is 498 characters:

```
$ openspec validate fix-a-child-is-bounded-by-its-context --strict
Change 'fix-a-child-is-bounded-by-its-context' is valid
```

## The gate, on the corrected tree

```
$ ./tools/loop/gate.sh --base 122cc2f9
LOOP-GATE: tests=772 failures=2 errors=0 (tools/loop-selftest.sh --from-git 122cc2f9 1012s, … ) — failing: tools/loop-selftest.sh --from-git 122cc2f9
$ exit=1
```

No build ran and none is owed (no compiled path). Of 316 cases, 314 passed; the two failures are attributed in
`evidence/gate-attribution.md` — `gate#theMapCoversEveryPipelineStep`, red on the base commit's own inputs, and
`run#theWorkersOwnShellWriteIsRecordedAndNotPlaced`, which passed four times in isolation on the same tree and lost
its 8-second window to a once-a-second sweep inside a suite that ran 2.5× slower than the same suite earlier that
day. The log is `evidence/gate.log`, the roll-up and the excerpts `evidence/gate-summary.txt`, the step tallies
`evidence/gate-tallies/`.
