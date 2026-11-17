# The three records, mutated and restored

```
$ bash tools/loop/tests/loop-mutations.sh --check --only "921.1 944.1 944.2"
loop-mutations: selected 3 of 409 records (labels: 921.1 944.1 944.2)
loop-mutations: all 3 cases passed — every selected record applies to the file it names

$ bash tools/loop/tests/loop-mutations.sh --only "921.1 944.1 944.2" --timeout 400
loop-mutations: selected 3 of 409 records (labels: 921.1 944.1 944.2)
RED    921.1  gate#aBaseRedStepIsSubtractedAndNamed   (the subtraction of the step-and-case pairs a base already red
       for, so a carrier the change never wrote goes back into the item tally, the gate exits red on it and no line
       names what left (issue #921))
       … a step red on the base and red for no new case is not a red gate: …
restore 921.1 gate#aBaseRedStepIsSubtractedAndNamed green again
RED    944.1  gate#theBaseStateIsMeasuredWhenTheGateFirstNeedsIt   (the measurement a gate takes of the step it has
       just found red, …)
       … the gate measured the step's base state and carried the red it found: …
restore 944.1 gate#theBaseStateIsMeasuredWhenTheGateFirstNeedsIt green again
RED    944.2  gate#theBaseStateIsMeasuredWhenTheGateFirstNeedsIt   (a worktree of the run's own repository as a tree
       the run's state belongs to, …)
       … the gate measured the step's base state and carried the red it found: …
restore 944.2 gate#theBaseStateIsMeasuredWhenTheGateFirstNeedsIt green again

red-when-mutated: 3 · not-noticed: 0 · restore-failures: 0
```

Each record removes one arm and reds the case on the assertion that arm is about:

- `944.1` replaces the measure-on-miss call with `:` — nothing is measured, the base's red is charged to the item.
- `944.2` turns the worktree half of `loop_path_outside_tree` into "still outside" — the gate in the item's worktree
  resolves no record, so nothing is measured and nothing is subtracted.
- `921.1` (moved onto the line this change leaves) makes the subtraction of a held record unreachable — the measure
  call still runs, so this record fails on the subtraction's own assertion, not on a missing line.
