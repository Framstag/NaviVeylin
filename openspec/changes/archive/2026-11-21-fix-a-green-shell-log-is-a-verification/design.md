# Design — one predicate, two shapes of the same measurement

## Root cause

`carried_verification` (`tools/loop/worktree.sh:492`) decides a finished attempt's green proof from the archived
change's `evidence/` directory, and it decides it in one shape: a `*.xml` testsuite with `tests="[1-9]"`,
`failures="0"` and no `errors`. A `tools/**` item's gate is the shell suites, and the record such a gate leaves is
the suite's console log — `PASS <group>#<case>` and the exit status — so the reading answers "nothing" for the class
the loop works most. The predicate is not wrong about what green means; it is wrong about the shape green arrives in.

## The single fix

`carried_verification` iterates the evidence files and reads each by its shape, instead of returning early when the
directory holds no XML:

- `*.xml` — unchanged: `tests="[1-9]"`, `failures="0"`, no `errors="[1-9]"`.
- `*.log` — a line matching `^PASS <group>#<case>` (the verdict line
  `tools/loop/tests/loop-selftest.sh:996-998` prints) and no line matching `^FAIL ` (`:1003-1005`).
- anything else — not a verification record; skipped.

One passing file makes the change green, exactly as one passing XML did, and a log naming a failing case is the red
proof and never a verification.

**Why the `PASS` line and not `exit=0`.** `tools/loop/gate.sh:24-26` derives each step's JUnit tally from the suite's
summary line and, where a suite prints none, from the failing cases the suite named — the `PASS`/`FAIL` lines are
that source, so the log shape is the same measurement the tally carries, not a claim beside it. A bare `exit=0` is
any command's exit status and would admit a log that measured nothing. The `#948` record carries no summary line
(`--case` runs print none, `tools/loop/tests/loop-selftest.sh:1025-1028`), so a summary-only predicate would refuse
one of the three branches this item measured.

**Why a `.log` and not any evidence file.** The console log is the shape the class leaves
(`evidence/green-case.log`, `evidence/green-on-change.log`, `evidence/gate.log`); a `*.md` record is the run's prose
about a round, and reading it for a verdict line would let a quotation of one pass as a run of it.

## Blast radius

- `tools/loop/worktree.sh`: the comment above `carried_verification` and its body. No new function, so
  `tools/loop/layout.tsv:35` keeps its row and the file stays inside its bound (1 144 lines before the change, 1 160
  after, bound 1 414).
- `tools/loop/tests/loop-cases.sh`: one case and its two registrations (`LOOP_SHELL_CASES`, the dispatch table). The
  18 fixtures that write an XML stand-in and the case
  `run#doesNotMergeAnAttemptWithoutAGreenVerification` are untouched and still hold: the delta widens a reading, it
  retires no shape.
- `openspec/specs/agent-issue-loop/spec.md`: one ADDED requirement with one scenario, through the change's delta.
- Nothing else calls `carried_verification` with a different expectation: `branch_state_of` (`:569`),
  `cmd_branch_state` (`:589`) and `cmd_adopt_item` (`:692`) all treat a non-empty answer as green and an empty one
  as not, and the answer is still the list of the change's evidence files.

## Rollback

Revert the change: `carried_verification` returns to the XML-only predicate, and every branch that carries a log
reads `archived-not-green` again — the state the tree is in today. Nothing is written by the reading, so a revert
leaves no tree that needs a second step.

## Alternatives the evidence eliminated

- **Wait for the end-item copy of the gate's tally (`#979`, `2026-11-20-fix-end-item-copies-the-gates-tally`, on
  branch `fix/issue-979-a-finished-attempt-whose-green-proof-is` and not in this base).** It is unmerged, and even
  landed it fixes the *producer* for items archived after it: a branch already parked is a commit history, so the
  three branches this item measured, and every `tools/**` item of the runs before it, would stay
  `archived-not-green`. Both halves are owed; this change is the reading half and touches no producer.
- **Read a `LOOP-GATE:` line from an evidence log.** `tools/loop/run.sh:2086` checks a report's gate claim against the tally
  directory, and that line is a report's claim, not a suite's verdict: it would make a branch green on the run's
  prose about a gate rather than on a case that ran. The `PASS` line is printed by the runner, one line per case it
  finished.
- **Have the archiving step copy the gate's tally into the change's `evidence/`.** The archive is written by the
  step **before** the gate (issue #837), so at archive time the item's own tally does not exist yet, and a copy
  written after the gate would be a change directory the gate never measured. It also cannot help a branch already
  parked: its archive commit is history.
- **Rewrite the parked branches' evidence to carry XML.** Three branches of one run, and every `tools/**` item is
  the same shape: a convention only a person remembers is the defect, not the fix.
- **A summary-line predicate instead of the `PASS` line.** Refused above: a `--case` run prints no summary, and
  `evidence/green-case.log` on `#948` is exactly that shape.
