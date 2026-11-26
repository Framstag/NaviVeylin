# Tasks — one case's own log is not the change's gate

Spec: `agent-issue-loop`, the added requirement "A suite's log is the change's verification and one case's log is not"
(delta `specs/agent-issue-loop/spec.md`). Scope: `tools/loop/**` and one guideline paragraph, so the gate owes the shell
suites and the mutation check — `./tools/loop/gate.sh --plan --base 852983bf6fa4a89c3b3f365ffc9db8895cacfbbd` — and it
is run once, by this session.

## 1. The arm asks for a suite

- [x] 1.1 `tools/loop/worktree.sh`: the `*.log` arm of `verification_record_green` counts the distinct
  `^PASS <group>#<case>` verdicts and refuses a record stating fewer than two, keeping the `^FAIL ` refusal as it
  stands, with the reason stated above it.
  *Verified*: `worktree#aSingleCasesLogIsNotTheChangesGate` reads `archived-not-green` for a one-verdict log, and
  `worktree#aShellSuiteLogIsAVerificationRecord` still reads `adoptable` on a suite's log.

## 2. The state names the records it accepted

- [x] 2.1 `tools/loop/worktree.sh`: `carried_verification` accumulates the names it accepted and prints them
  comma-separated, instead of the evidence directory's `ls-tree` listing; empty when nothing was accepted.
  *Verified*: `worktree#aShellSuiteLogIsAVerificationRecord` asserts the evidence column is `green-case.log` while
  `red-proof.log` sits in the same directory, and that the adoption line names `· evidence green-case.log`;
  `worktree#aSingleCasesLogIsNotTheChangesGate` asserts an empty evidence column.

## 3. The cases and their records

- [x] 3.1 `tools/loop/tests/loop-cases.sh`: the new case `worktree#aSingleCasesLogIsNotTheChangesGate`, its rows in
  the registry (`LOOP_SHELL_CASES`, the `loop_run_shell_case` dispatch) and its `# files:` declaration.
  *Verified*: `driver#everyCaseDeclaresTheFilesItCanReach` passes over it (the gate's first step).
- [x] 3.2 `tools/loop/tests/loop-cases.sh`: `worktree#aShellSuiteLogIsAVerificationRecord` re-scoped to a suite's
  worth of verdicts with a red proof beside them, so the case keeps its statement and stops licensing a single
  case's log.
  *Verified*: the case reads `adoptable`, names `green-case.log` alone, and still refuses a log naming a failing
  case.
- [x] 3.3 The red proof on the base: the new case run with the production hunk reverted
  (`evidence/red-on-HEAD-aSingleCasesLogIsNotTheChangesGate.log` and its XML), and the same case green on the fix.
  *Verified*: `evidence/red-on-HEAD-aSingleCasesLogIsNotTheChangesGate.xml` states `tests="1" failures="1"`; the
  green run of the same command states `PASS`.

## 4. The guideline the delta owes

- [x] 4.1 `guidelines/Backlog.recipes.md`, the "Refusals, records and the landing" section: one paragraph stating what
  a green verification record is, that one case's log is not the change's gate, and that the adoption names the records
  it accepted.
  *Verified*: `tools/look.sh --section 'Refusals, records and the landing' --path guidelines/Backlog.recipes.md`
  prints the paragraph, and it names the two cases.

## 5. The gate

- [x] 5.1 Archive the change with the delta it merges, then run the gate once
  (`./tools/loop/gate.sh`) over the archived tree and copy the XMLs it leaves into this change's `evidence/` and into
  the item's evidence directory.
  *Verified*: the gate's own `LOOP-GATE:` line, quoted in the worker's report.
