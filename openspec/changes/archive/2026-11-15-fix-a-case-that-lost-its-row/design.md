# Design — a case that loses its row is refused

## The defect, in one mechanism

`LOOP_SHELL_CASES` is the declared set, and every question the driver can be asked after it derives that set
(`tools/loop/tests/loop-selftest.sh:455`) — `--list` (`:577`), `--case`, `--group`, `--files`, `--from-git` — is a
filter over it. The case bodies and
the dispatch arms are never read by the driver: it sources `tools/loop/tests/loop-cases.sh` for the functions
(`:454`) and derives the declared set from one call (`:455`). A row that a delta *replaces* instead of inserting
therefore removes a name from the only enumeration there is, while the body (`:13672`) and the arm (`:1241`) stay
exactly as they were — the case is unreachable, nothing refuses it, and every count the project keeps stays green
(issue #929: bodies 453 → 459, mutation ids 384 = 384, rows 449 → 454, arms 449 → 455).

## The fix, and only that

One block in the driver and one case, no engine script:

- `tools/loop/tests/loop-selftest.sh:457` — after `shell_cases=$(loop_shell_cases)`, one `awk` reads the cases file
  that was just sourced and derives the names it defines: a body is `^case_<group>_<Name>() {`, an arm is
  `'<group>#<Name>') fn=case_<group>_<Name> ;;`. It compares them with the declared set and prints one line per
  disagreement — a body with no row, a row with no body or no arm, an arm with no row, an arm whose function is not
  the body its name derives — and the driver exits 1 naming them, before `--list` and before any case runs.
- `tools/loop/tests/loop-cases.sh` — `driver#aCaseWithNoSelectionRowIsRefused`, registered in `LOOP_SHELL_CASES`
  (`:430`) and in the dispatcher arm (`:1179`), builds the shape the commit wrote — two bodies, two arms, one row — in
  a nested suite and asserts the refusal, its message and that no case ran.

Three shapes are not this suite's own and are not read, because they are how the suite's own nested fixtures are
written: a cases file that defines no body at all (it dispatches by convention, `loop_run_shell_case` overridden), a
body or an arm inside a heredoc (the nested suite that heredoc writes), and the four `stub#owns*` bodies, which sit
inside a nested fixture's own quoted `LOOP_SHELL_CASES` string and are exempt by name. A file with bodies but no arm
table is held only to its bodies and its rows.

## Alternatives the evidence eliminated

- **Comparing counts.** The item's own measurement is this option's refutation: a swap moves the row count and the
  body count by nothing (453 → 459 bodies are exactly the six bodies the five branches added, ids 384 = 384), so a
  count is a check that reads green over the defect it exists for.
- **Comparing against a base commit** (`--from-git <base>`). A legitimate deletion of a case is indistinguishable from
  a lost row by that comparison, and the base is not always reachable — the three sets are in the working tree and are
  the question, so nothing outside it is read.
- **Refusing at the arm** (`no such shell case`, the existing refusal). It fires only when a *filter* names a case that
  is not declared, which is the opposite direction: the defect leaves a body nothing names, and no filter ever names a
  case nobody selected.
- **A static check in a case instead of in the driver.** A case that reads the three sets over the repository would
  have to be selected to run, and the delta that loses a row is the one whose selection nobody looks at; the driver
  owes the comparison on every invocation, `--list` included, because it is the selection it is about to answer.

## Blast radius

Two files, both the suite's own: the driver (one block, 69 lines with its comment) and the cases file (one case, its
row and its arm, 43 lines). The declared case count goes from 461 to 462 — the case's own addition — and no mutation
record is added: the harness applies a record to the **engine** copy the record names (`tools/loop/tests/loop-mutations.sh:1623`),
and the driver the case runs is copied from `$LOOP_PROJECT_ROOT/tools/loop/tests/`
(`tools/loop/tests/loop-cases.sh:1445`), which no record can reach — so a record here would be reported `MUTATION-DID-NOT-APPLY` or `the case did not notice` and would prove nothing. What
holds the check instead is the case's own red-on-base run, which is the shape the commit wrote.

No engine script, no profile entry, no spec text moved, no fixture realigned: the suite's nested fixtures define no
case body at all, so the comparison does not see them (`--group driver`, all 32 cases, green).

## Rollback

Revert the block in `tools/loop/tests/loop-selftest.sh` and the case's three registration points in
`tools/loop/tests/loop-cases.sh`: the driver is then what it was at `d794277b`, and the declared case count returns to
461. Nothing else reads either hunk.
