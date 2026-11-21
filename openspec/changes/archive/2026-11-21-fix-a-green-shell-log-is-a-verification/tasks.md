- [x] 1. Write the red case first, in `tools/loop/tests/loop-cases.sh`: `worktree#aShellSuiteLogIsAVerificationRecord`,
  registered in `LOOP_SHELL_CASES` and the dispatch table, with its `# files:` declaration. Verification: on the base
  commit it fails with `expected 'adoptable', got 'archived-not-green'`
  (`tools/loop/tests/loop-selftest.sh --case worktree#aShellSuiteLogIsAVerificationRecord`,
  `evidence/red-on-base.log`).
- [x] 2. Widen the record's predicate in `tools/loop/worktree.sh`: `carried_verification` reads each evidence file by
  its shape — the JUnit testsuite as today, and a `*.log` with a `^PASS <group>#<case>` line and no `^FAIL ` line —
  and the "no XML, then nothing" early return goes. Verification: the case passes,
  `PASS worktree#aShellSuiteLogIsAVerificationRecord` (`evidence/green-case.log`).
- [x] 3. Run the cases the delta reaches: `--group worktree`, `--group driver`, `--group prescreen`, `--case
  '*Adopt*'` and `--case '*GreenVerification*'`. Verification: green, and the numbers recorded in
  `evidence/focused-cases.log`.
- [x] 4. Write the delta — this requirement and its one scenario — in
  `openspec/changes/fix-a-green-shell-log-is-a-verification/specs/agent-issue-loop/spec.md`, one scenario and one
  case. Verification: `./tools/loop/measure.sh --change fix-a-green-shell-log-is-a-verification` reports
  `scenarios=1 cases=1 unnamed=0`.
- [x] 5. Run the structural checks this delta owes. It edits no compiled file, so it owes no build: the delta's own
  files are shell cases, this change's spec delta and an engine script, and the checks that read them here are
  `tools/check-openspec-hygiene.sh` (the change's artifacts) and `bash tools/gen-feature-list.sh
  --check-classification` (the requirement's capability is already classified, so the check is what proves it). The
  classification check is green (exit 0). The hygiene check is red (exit 1) over five **open changes another run left
  in this worktree** — `a-case-name-resolves-to-one-harness`, `run-event-stream-is-typed`,
  `settings-taxonomy-and-surface`, `the-bounds-spend-and-budget-are-typed`, `the-print-sites-are-converted`, each
  missing its `x-issue:` line — and over nothing of this change's. It is recorded and not repaired here: another
  item's change directory is not this one's to write. Both outputs are in `evidence/structural-checks.log`, and the
  gate's own steps over the same paths are the gate child's.
- [x] 6. Grade the diff against the three predicates the reviewer reads — each `THEN` clause against
  `worktree#aShellSuiteLogIsAVerificationRecord`'s assertions, every pointer in this change against the tree it names,
  and every artifact it cites against the file that exists — and leave the change unarchived for the gate child.
