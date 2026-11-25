# Tasks — a review round that named nothing buys no round after it

No compiled file is touched: the delta is one reader, one guard, one bound with its derivation, two declarations, two
test suites, one guideline paragraph and one requirement already in the shipped spec. The structural checks of the
files edited stand in for a build, and the focused selections stand in for the suite, which the parent runs once over
the branch (`guidelines/Build.md` §2, the scope block).

## The case, first — red on the base before any production change

- [x] `tools/loop/tests/loop-cases.sh`: the case `run#aReviewThatNamedNothingTakesNoSecondRound` — its body, its row in
  `LOOP_SHELL_CASES` and its dispatch arm — with three arms: a refusal that names a `BLOCKING:` line keeps the round
  it pays for, a report that states `LOOP-BLOCKING: 1` and names nothing takes none (and says so on the item, with
  `refused` as its mechanism and the escalation label), and a report with no count line at all refuses in one round.
  *Verified:* all of it on the fixed source (`evidence/green.log`, the first block).
- [x] The case checked out alone over the base — `git checkout 4d1dea06 -- tools/loop/tests/loop-cases.sh
  tools/loop/tests/loop-mutations.sh` — and run: `bash tools/loop/tests/loop-selftest.sh --case
  'run#aReviewThatNamedNothingTakesNoSecondRound'` prints `a report that counted a finding and named none bought a
  second review round`, `loop-selftest: 1 of 1 cases failed`, exit 1.
  *Verified:* `evidence/red-on-base.log`, first block, verbatim. The two files were then restored to the base and
  `git status --porcelain -uall` was empty.
- [x] The correction's own arm, added by this landing: a report whose finding is written `**BLOCKING: …**` still takes
  its round — the shape `1042/reviewer-report-1-restart.md` and `999/reviewer-report-2.md` write theirs in.
  *Verified:* red against the reviewed parse `'^BLOCKING:'` (`evidence/red-on-base.log`, second block), green against
  the shipped one (`evidence/green.log`, first block).

## The reader, and the guard

- [x] `tools/loop/evidence.sh`: `blocking_findings()` — the report's `BLOCKING:` lines through `report_lines`, so a
  fenced closing block is read as the lines it states (#793), and the markdown a reviewer may wrap the whole line in
  is taken off: `grep -cE '^[[:space:]>*_]*BLOCKING:'`. The function's comment states why the count is the proxy and
  the line the pointer, and names the two archive reports that moved the parse.
  *Verified:* `blocking_findings 1042/reviewer-report-1-restart.md` reads `2` where `grep -c '^BLOCKING:'` reads `0`,
  and `999/reviewer-report-2.md` reads `1` and `0`; `118/reviewer-report-1.md` reads `3` and `3`
  (`evidence/measure.log`, "The parse the guard is keyed on").
- [x] `tools/loop/run.sh`: the guard — `if [ "$(blocking_findings "$reviewer_report")" -eq 0 ]; then` — sits **before**
  the corrective arm, so a corrections-only refusal that names nothing earns no extra round either; `refusal_named_nothing`
  is declared with the round's own `round_cap` and reaches the item's reason at the refuse arm.
  *Verified:* `run#aReviewThatNamedNothingTakesNoSecondRound` (both shapes), `run#aCorrectiveRefusalEarnsOneMoreRound`
  and `run#aBlockingRefusalStillTakesTheRoundTwoPath` all `PASS` (`evidence/green.log`).
- [x] The sentence that justified the guard is corrected everywhere it was written — `run.sh`'s comment, the case's
  comment, the mutation's comment and the shipped spec's scenario prose. The reviewed claim ("#1042's round 1 states
  `LOOP-BLOCKING: 1`, writes no `BLOCKING:` line, and is followed by a round that spends the bound on a change none of
  its lines refused") is false in both halves; what replaces it is what the archive shows: every `VERDICT: FAIL`
  report on record carries a `BLOCKING:` line, at line start or emphasis-wrapped.
  *Verified:* the archive walk in `evidence/measure.log` — 20 failing reports, 18 at line start, 2 emphasis-only,
  none without a finding, 0 `BLOCKING:` lines inside a fence.

## The bound, and its derivation

- [x] `tools/loop/loop.conf`: `LOOP_CHILD_TURNS_MAX_REVIEWER=81`, the median of the archive's 37 verdict-ended reviewer
  sessions of 65, with the figure, the population, the percentile line, the window and the command that re-derives it
  in the key's own comment; the paragraph above it names the reviewer's as the one value that is not a p95; the
  restart paragraph drops the literal "110 turns" the old figure had written into it.
  *Verified:* `run#eachChildIsBoundedByItsOwnRolesTurns` and `run#reviewBudgetIsARecordedValue` `PASS`, and the
  derivation is re-derived read-only in `evidence/measure.log` (65 sessions, 37 with a `VERDICT:` line, 28 without,
  20 at exactly 110, median 81).
- [x] `LOOP_CHILD_TURNS_MAX_REVIEWER_PRE_GATE` stays 110, and its bullet says why it does not inherit 81: twelve
  censored sessions say nothing about what a pre-gate round needs, so the one key whose number is a placeholder keeps
  the figure the review round carried before this change.
  *Verified:* the value and its bullet are in the file as the reviewed commit writes them; `run#…` header case that
  prints every role's figure `PASS` (`evidence/green.log`).
- [x] `guidelines/Backlog.recipes.md`: the reviewer's row now reads 81 with its median and population, the pre-gate row
  states the inheritance it does not take, and the "one value is an experiment" paragraph becomes "two values are not
  a p95" with the reviewer's median argued beside the rework round's experiment.
  *Verified:* the row and the paragraph are in the diff; `tools/check-doc-values.sh` reads the table against the
  profile at the parent's gate.

## The declarations

- [x] `tools/loop/layout.tsv`: `blocking_findings` added to the row of the file that defines it (`evidence.sh`), and
  `tools/loop/shared.tsv`: one row `blocking_findings  evidence.sh  run.sh`.
  *Verified:* `driver#anOwnedFunctionIsInItsOwnersFile` reports the same two base rows and no row for the new function,
  `driver#everyEngineScriptIsInTheTable`, `driver#noEngineFileExceedsItsBound` and `driver#theSourceOrderIsDeclared`
  `PASS` (`evidence/green.log`).

## The mutation suite

- [x] `tools/loop/tests/loop-mutations.sh`: record `1085.1` removes the guard (`if false; then`) and names
  `run#aReviewThatNamedNothingTakesNoSecondRound` as the case that must notice; record `58.1` follows the line it
  quotes, which now starts `block refused "${refusal_named_nothing:-…`.
  *Verified:* `bash tools/loop/tests/loop-mutations.sh --only 1085.1` — `RED 1085.1`, `restore … green again`,
  `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0`, exit 0 (`evidence/green.log`).

## The focused runs, and what is excluded

- [x] The eight cases this change moves:
  `run#aReviewThatNamedNothingTakesNoSecondRound`, `run#eachChildIsBoundedByItsOwnRolesTurns`,
  `run#aReviewerEndedAtItsTurnBoundIsNotARefusal`, `run#aCorrectiveRefusalEarnsOneMoreRound`,
  `run#aBlockingRefusalStillTakesTheRoundTwoPath`, `run#reviewBudgetIsARecordedValue`,
  `run#aCutReportIsRetriedInsteadOfRefused`, `run#theStartAfterTheRunsOwnEndedChildIsNotRefused` — each `PASS`, exit 0.
- [x] `bash -n` over `tools/loop/run.sh`, `tools/loop/evidence.sh`, `tools/loop/loop.conf`,
  `tools/loop/tests/loop-cases.sh` and `tools/loop/tests/loop-mutations.sh` — each exit 0.
- [x] `bash tools/loop/tests/loop-mutations-selftest.sh` — `all 39 cases passed`, exit 0 (30 s, so it was paid).
- [x] `driver#everyCaseDeclaresTheFilesItCanReach` — `PASS`: the case's `# files:` declaration still covers the body
  this landing added to it.
- [x] Name the red this change carries: `driver#anOwnedFunctionIsInItsOwnersFile` is red at the base `ae2373cb`
  **itself**, run in a detached worktree of that commit, with the same two rows
  (`report.sh defines action_add and its row does not own it`, `state.sh defines cmd_publish_refused and its row does
  not own it`); this change adds no row to it — the function it declares is in its owner's row.
  *Verified:* `evidence/green.log`, "The red this change carries and does not cause".
- [x] Exclusions, named: no build and no full suite — the delta touches no compiled file, so the structural checks
  above stand in and the parent gates the branch once. No device verification: nothing here reaches a device. No
  archive-driven spec merge: `--skip-specs`, per "Archive" below.

## Archive

- [x] `openspec validate fix-a-round-that-named-nothing-buys-no-round --strict`, then
  `openspec archive fix-a-round-that-named-nothing-buys-no-round --yes --skip-specs`, which moves this directory to
  `openspec/changes/archive/2026-11-25-fix-a-round-that-named-nothing-buys-no-round/` and leaves `openspec/specs/`
  untouched (the requirement's text is the fix's own and is already shipped).
  *Verified:* `git status --porcelain openspec/specs/` is empty after the archive, and `evidence/` travels with the
  directory.
