# Tasks — the era read from the change's own file

## 1. The case, before the fix

- [x] 1.1 `tools/tests/check-openspec-hygiene-selftest.sh` — add
  `artifact-hygiene#aChangeWrittenBeforeTheFieldIsReported` with its three arms (pre-era reported, the same tree a
  day after the boundary refused, no `created:` refused), and move the two `created:` fixtures of
  `#aChangeNamingNoIssueIsRefused` to `2026-11-20`. Parent: `documentation-ownership`, "An open change names the
  issue it is for" — scenario "A change written before the field was required is reported".
  Verify: `bash tools/tests/check-openspec-hygiene-selftest.sh --case 'artifact-hygiene#aChangeWrittenBeforeTheFieldIsReported'`
  is red on the base checker (`evidence/red-on-base-single.log`, `.xml`: `tests=1 failures=1`,
  2026-11-22T20:14:10Z), and the base reading of the whole case file is `1 of 12 cases failed`
  (`evidence/red-on-base.log`).

## 2. The check

- [x] 2.1 `tools/check-openspec-hygiene.sh` — read `created:` from the change's own `.openspec.yaml` (`:126`), hold
  the boundary in one constant (`:48`), report a change whose `created:` is earlier instead of refusing it
  (`:131-132`), print the era's line with the directory and the date it read (`:155-156`), and state the era in the
  class's header comment (`:111-113`). Parent: the same requirement. Verify: the check exits 0 over the tree's tracked
  open set and names the five pre-era directories (`evidence/check-tracked-open.log`); over this worktree it exits 1
  on the one open change that states no era and belongs to a concurrent session (`evidence/check.log`); no compiled
  file is touched, so this is the structural check of the file it edits and no build is owed
  (`guidelines/Build.md` §2, the scope block).
- [x] 2.2 Verify the case file passes over the repaired check.
  Verify: `bash tools/tests/check-openspec-hygiene-selftest.sh` — all 12 cases (`evidence/green-selftest.log`).

## 3. The invariant, falsified once

- [x] 3.1 Invert the era comparison in `tools/check-openspec-hygiene.sh`, run
  `bash tools/tests/check-openspec-hygiene-selftest.sh --case 'artifact-hygiene#aChangeWrittenBeforeTheFieldIsReported'`,
  require the failure this change's case names, restore the file, and require the case green again.
  Verify: `evidence/mutation.log`.

## 4. The artifacts

- [x] 4.1 `openspec/specs/documentation-ownership/spec.md` — MODIFIED requirement "An open change names the issue it
  is for": the body gains the era, and the new scenario names the case of task 1.1. Verify:
  `openspec validate fix-the-issue-field-honours-the-era-before-it --strict` exits 0
  (`evidence/openspec-validate.log`), and `tools/check-case-names.sh` resolves every case name the delta uses
  (`evidence/case-names.log`).
- [x] 4.2 `proposal.md`, `design.md`, this file — the numbers quoted are the ones the evidence files carry, and every
  `path:line` they cite is read from the tree after the edit. Verify: the two files the change touches are the two the
  gate plans (`bash tools/loop/gate.sh --plan --base f826cd7a5e4952fc0e5331a32dcea0cb67baa99a`).

## 5. Handover

- [x] 5.1 Leave the change unarchived for the gate child (the gate is not this session's), with its evidence beside
  it. Verify: the diff is the two files and the change directory, and `bash tools/check-openspec-hygiene.sh` exits 0
  over the tree's tracked open set before handover (`evidence/check-tracked-open.log`), while this worktree's own
  reading exits 1 on the concurrent session's directory that states no era (`evidence/check.log`).

## 6. The change child's own phase, taken by the continuation session

- [x] 6.1 `proposal.md` — the `surface:` line the readiness checker reads, carrying the count and the command that
  produced it, and `design.md` — its `## Open decisions` declaration and its rollback. Verify:
  `bash tools/check-change-readiness.sh openspec/changes/fix-the-issue-field-honours-the-era-before-it` is
  `LOOP-CHANGE-READY: ok` (`evidence/readiness.log`, re-taken after the shape landed).
- [x] 6.2 Archive the change with the delta it merges (`openspec archive --yes`), so the tree the gate measures and
  the tree a round reads are one tree. Verify: `openspec/changes/archive/<date>-fix-the-issue-field-honours-the-era-before-it/tasks.md`
  carries this line, and the gate's own log is `evidence/gate.log`.
