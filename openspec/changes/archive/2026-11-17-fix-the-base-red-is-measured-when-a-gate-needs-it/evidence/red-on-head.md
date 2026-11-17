# Red on HEAD, green on the change

The case this change adds is `gate#theBaseStateIsMeasuredWhenTheGateFirstNeedsIt`. It was run against a detached
worktree of `HEAD` (`e133fc90`), with only the new case copied into that tree, so the engine under test is HEAD's:

```
$ git worktree add -q --detach /tmp/red-on-head-944 HEAD
$ cp tools/loop/tests/loop-cases.sh /tmp/red-on-head-944/tools/loop/tests/loop-cases.sh
$ cd /tmp/red-on-head-944 && bash tools/loop/tests/loop-selftest.sh \
    --case 'gate#theBaseStateIsMeasuredWhenTheGateFirstNeedsIt'
…
gate: ./suite-one
FAIL notes#aCaseThatWasRed
notes: 1 of 2 cases failed
gate: ./suite-one — 0s
gate: ./suite-two
notes: all 1 cases passed
gate: ./suite-two — 0s
LOOP-GATE: tests=3 failures=1 errors=0 (./suite-one 0s, ./suite-two 0s) — failing: ./suite-one:
  expected '0', got '1'
FAIL gate#theBaseStateIsMeasuredWhenTheGateFirstNeedsIt
loop-selftest: 1 of 1 cases failed
```

That is the defect stated as a number: the item's gate ran three cases, charged the one failing case the **base**
carried to the item, and exited red on it — the base's red is the item's. On this change the same case is green:

```
$ bash tools/loop/tests/loop-selftest.sh --case 'gate#theBaseStateIsMeasuredWhenTheGateFirstNeedsIt'
PASS gate#theBaseStateIsMeasuredWhenTheGateFirstNeedsIt
```

and it is green inside the gate's own selection as well (`evidence/gate.md`, `PASS`.

## The second defect, measured before the fix

`state.sh` refused the run's state from the run's own worktree, so the record #921 writes had no reader:

```
$ cd .pi/worktrees/loop
$ LOOP_STATE_FILE=/home/tim/projects/NaviVeylin/.pi/issue-loop/run.json bash tools/loop/state.sh get run.baseRed
state: no run state at /home/tim/projects/NaviVeylin/.pi/worktrees/loop/.pi/issue-loop/run.json — run 'init' first
exit=2
$ jq -c '.run.baseRedBase, (.run.baseRed|keys), .run.baseRedUsed' /home/tim/projects/NaviVeylin/.pi/issue-loop/run.json
"5330313b7cefea2b1d61d0b4f11ae0e62779c49d"
["tools/loop/tests/loop-selftest.sh --from-git 5330313b7cefea2b1d61d0b4f11ae0e62779c49d"]
null
```

The record held a red step and `run.baseRedUsed` was `null`: no gate had ever subtracted anything. After the change the
case asserts the same write from a linked worktree (`.run.baseRedUsed.steps` holds `"./suite-one":1`).
