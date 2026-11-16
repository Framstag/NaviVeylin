# Tasks — the derivation models the function, the loop and the quoted run

Spec: `agent-issue-loop` (delta `specs/agent-issue-loop/spec.md`). No compiled file changes, so no Gradle build is
owed (`guidelines/Build.md` §2): the structural check of the files this delta edits — `bash -n` on the edited scripts
and `openspec validate fix-a-local-is-not-a-shared-name --strict` — stands in for the build, and the gate for
`tools/loop/**` is `tools/loop/tests/loop-selftest.sh`.

## 1. The two cases, red on the base

- [x] 1.1 `driver#aLocalIsNotAShareOfAnotherFilesName` in `tools/loop/tests/loop-cases.sh`: a fixture whose `a.sh`
  assigns a name at column 0 while `b.sh` declares it `local` in the function that reads it, and a second fixture
  whose reader binds the name as its `for` loop variable. Both assert `layout_shared_awk` reports nothing. Register
  it in the case list and the dispatch table; declaration `# files: tools/loop/ tools/loop/shared.tsv`.
  **Verified** red in a clone of the base `d794277b` carrying only this case body and the base's derivation:
  `evidence/red-on-HEAD.log` and its JUnit carrier `evidence/red-on-HEAD.xml` (`tests=1 failures=1`,
  2026-11-16T00:01:25Z), the assertion answering `expected '', got 'shared_thing\ta.sh\tb.sh'`.
- [x] 1.2 `driver#aQuotedProgramIsNotAShellRead`: a fixture whose reader mentions `$name` on the second line of a
  single-quoted program opened on the first, and closed on it. Asserts the derivation reports nothing.
  **Verified** red the same way and in the same run — `expected '', got 'col\ta.sh\tb.sh'` (2026-11-16T00:01:25Z,
  `evidence/red-on-HEAD.log`, `evidence/red-on-HEAD.xml`). The name is on the run's *second* line, because a name on
  the first line is dropped by a model that reads one line at a time and would pass without the carry
  (`evidence/revert-checks-local-and-quoted.log`, 5.2 below).

## 2. The derivation

- [x] 2.1 `layout_shared_awk`: `drop_single_quoted()` with a per-file state, used by both passes in place of
  `scanned()`'s per-line `gsub`, and `line_for_scan()` cutting the comment before and after the runs.
  **Verified** by the two cases above passing on this tree (`evidence/focused-cases.log`, 2026-11-16T00:02:12Z) and
  by 5.2: with the run's state reset per line the quoted-program case reds.
- [x] 2.2 `layout_shared_awk`: the function scope — `fn` from a column-0 `name() {`, cleared by a column-0 `}`;
  `own[fn, name]` from `local` lines and from `for` bindings; the mention pass skips a name the current function owns.
  **Verified** by the same green run and by 5.1: with the skip line deleted the `local`/`for` case reds.
- [x] 2.3 The header above the helpers (`:16944`-`:16954`) and `shared.tsv`'s header state the function scope, the
  quoted run and the handoff, so the code and the prose are one rule.
  **Verified** by reading the two headers against the model: `own` bullet at `:16952`-`:16954`, `read` bullet's
  "however many lines it spans" at `:16949`, and `shared.tsv`'s `read by` paragraph.

## 3. The declaration follows the model

- [x] 3.1 Run `driver#theSharedNamesAreDeclared` against the base's engine with the fix applied, and record its
  refusals in the change's evidence (`evidence/shared-rows.log`) — the rows that moved are named by the case, not
  guessed.
  **Verified**: `evidence/shared-rows.log` is the case's own refusals on the base engine with the model applied, and
  `PASS driver#theSharedNamesAreDeclared` on the tree after the rows moved (`evidence/focused-cases.log`).
- [x] 3.2 `tools/loop/shared.tsv`: remove the rows for `merge_refused_branch` and `merge_refused_paths`, and every
  other row the run of 3.1 names, and narrow the reader list of the rows whose readers are now their own `local`s.
  Verify: the same case exits 0 with nothing removed from the engine.
  **Verified**: 16 rows removed and 11 rows' reader lists narrowed — `git diff --stat -- tools/loop/shared.tsv`
  answers `1 file changed, 18 insertions(+), 29 deletions(-)`, and no engine script is in the change's diff — and the
  case passes on that tree (`evidence/focused-cases.log`, 2026-11-16T00:02:12Z).

## 4. Documents and evidence

- [x] 4.1 `tools/loop/report.sh:259`-`:261`: delete the comment that says a static reader counts the two `archived`s
  as one name, since the fix makes it false.
  **Verified** `bash -n tools/loop/report.sh` exits 0 (`evidence/structural-checks.log`, 2026-11-15T23:59:38Z) and
  `openspec validate fix-a-local-is-not-a-shared-name --strict` answers `Change ... is valid`.
- [x] 4.2 The four shared-name cases green together with the two new ones and the declaration case.
  **Verified** in one transcript — `PASS` for `driver#aLocalIsNotAShareOfAnotherFilesName`,
  `driver#aQuotedProgramIsNotAShellRead`, `driver#theSharedNamesAreDeclared`, `driver#anUndeclaredReadIsRefused`,
  `driver#aNameAssignedTwiceIsRefused` and `driver#everyCaseDeclaresTheFilesItCanReach`
  (`evidence/focused-cases.log`, 2026-11-16T00:02:12Z).

## 5. Verification

- [x] 5.1 Revert-check `driver#aLocalIsNotAShareOfAnotherFilesName`: delete the mention pass's skip line and the case
  must fail; restore, forced green.
  **Verified** `FAIL ... expected '', got 'shared_thing\ta.sh\tb.sh'` (exit 1), restore byte-identical by sha256, then
  `PASS` (exit 0) — `evidence/revert-checks-local-and-quoted.log`, 2026-11-16T00:00:58Z.
- [x] 5.2 Revert-check `driver#aQuotedProgramIsNotAShellRead`: reset the run's state per line and the case must fail;
  restore, forced green.
  **Verified** `FAIL ... expected '', got 'col\ta.sh\tb.sh'` (exit 1), restore byte-identical, then `PASS` (exit 0) —
  same transcript, 2026-11-16T00:00:58Z.
- [x] 5.3 Revert-check `driver#theSharedNamesAreDeclared`: delete one row of `shared.tsv` and the case must fail
  naming it; restore, forced green.
  **Verified** `FAIL ... shared.tsv: issue is read by prescreen.sh, phase.sh, report.sh, run.sh and no row declares
  it` (exit 1), then `PASS` after the row was restored — `evidence/revert-checks.log`, 2026-11-15T23:56:39Z.
- [ ] 5.4 The gate the change owes (`tools/loop/tests/loop-selftest.sh`, full selection) is the gate child's run: this
  item's gate is deferred to it (issue #837), so no gate line is printed here and this box is the gate child's to
  close.

--- Rules in scope for this file ---
[From: .pi/worktrees/loop/AGENTS.md]
[Rule body omitted; authoritative source: /home/tim/projects/NaviVeylin/.pi/worktrees/loop/AGENTS.md]
