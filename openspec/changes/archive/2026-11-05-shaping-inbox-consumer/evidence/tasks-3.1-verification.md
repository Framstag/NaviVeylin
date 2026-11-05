# Task 3.1's verification line, corrected against the delta

`tasks.md` 3.1 states the check as `tools/issue-tooling-selftest.sh --list | grep -c 'shaping#theConsumer'` = 3.
That command prints **2**, and the delta is why: the third case's name is fixed by the change's own
`specs/shaping-inbox/spec.md` line `**Case**: shaping#theApprovedReshapeLeavesThroughTheExistingWriters`, which does
not begin with `theConsumer`. The case was not renamed — the scenario's `**Case**:` is the authority, and a case a
scenario does not name is a planning defect.

The equivalent check, run at 2026-11-05T10:49Z from the change root:

```
$ bash tools/issue-tooling-selftest.sh --list | grep -c 'shaping#'
12
$ bash tools/issue-tooling-selftest.sh --list | grep -Ec 'shaping#(theConsumer|theApproved)'
3
$ grep -c 'shaping#' tools/issue-tooling-cases.sh        # the array plus the dispatch row name each case twice
```

9 shaping cases existed before; 12 are listed after — the three the change adds. `tools/loop/measure.sh --change
shaping-inbox-consumer` prints `LOOP-MEASURE-CHANGE shaping-inbox-consumer scenarios=3 cases=3 unnamed=0`, which is
the same count taken by the change's own measuring command rather than by a prefix guess.

**Applied at the gate (2026-11-05T11:5xZ)**: `tasks.md` 3.1 now states
`bash tools/issue-tooling-selftest.sh --list | grep -Ec 'shaping#(theConsumer|theApproved)'` is 3, and `proposal.md`'s
`surface:` line names the same command instead of the `shaping#theConsumer` prefix.
