The run announced review round 2 of this change (`review-round-2-requested.md`) and its reviewer was ended at the
item's turn bound — `LOOP-TIMEOUT: the child reached the recorded turn bound of 250 turns` — so no reviewer graded
this change and no `review-round-2-passed.md` exists. The branch's own record says so; a landing that carried a
`passed` file for a round no reviewer answered would be the lie the announcement was written to prevent.

The change was therefore reviewed and merged by hand, which is the path `tools/loop/worktree.sh:444` names for a
branch whose announcement was never answered. This file is the record of that pass.

## What was reviewed

Round 1's report (`.pi/issue-loop/items/704/reviewer-report-1.md`, BLOCKING + five notes) against the correction
round's tree (`337fa3ea`, `b47d9f6d`), read rather than trusted:

- the scenario's THEN now states the two halves the case asserts — the reading names that shell among the processes
  standing in the project, and places no process of that tree — in the delta and in the shipped spec
  (`openspec/specs/agent-issue-loop/spec.md:463`–`:472`, one text);
- the case stages the pid (`tools/loop-cases.sh:5350`–`:5395`): the shell the hook forks writes its own pid, the
  case asserts the pid is non-empty, then that the `LOOP-OUTSIDE-WRITE-ALIVE:` field names it, then `attributed=none`;
- `proposal.md`'s red stamp is the archived XML's (`2026-10-30T09:04:21Z`), its scenario name is the spec's, its What
  Changes lists the four `Case` lines; `evidence/red-on-head.sh --base` installs the base fixture and restores the
  tree's own copy on exit; the gate is dated from `gate.log` (`08:37:09Z`–`08:50:15Z`, `HEAD 27cf61cb`).

**No blocking finding.** The round-1 correction is answered where it was made, and nothing in the engine script,
the fixture's inert path or the guideline paragraph moved with it.

## What was re-run, on the merged tree

The branch was cut from `27cf61cb`; `main` was at `b2fb8695` (#703's landing, which edits the same
`guidelines/Backlog.md` paragraph). `main` was merged in first, so the numbers below are the ones the landing
carries.

| step | result |
|---|---|
| `bash evidence/red-on-head.sh --base` (base fixture installed and restored by the script) | `tests=1 failures=1`, `FAIL … 'demo-owned.txt' not found in:`, `2026-10-30T09:48:58Z`; `git status --short tools/loop/fixtures/pi` empty afterwards |
| `bash tools/loop-selftest.sh --case 'run#*rite*'` (the 8 cases round 1 selected) | 8 × `PASS` |
| the whole suite, branch tree | `loop-selftest: all 288 cases passed` |
| the whole suite, merged tree | `loop-selftest: all 289 cases passed` (287 at the base, +this change's case, +#703's) |
| `bash tools/check-openspec-hygiene.sh`, `check-doc-routes.sh`, `check-todo-references.sh` | exit 0, 169 section references resolve |
| `openspec validate --archived` | `✓ change/2026-10-30-fix-worker-own-shell-write-is-staged-and-guarded` |
| the merge's one conflict, `guidelines/Backlog.md` §7 | resolved by keeping #703's newer sentence (the run refuses a procps-less host at its own header *and* the suite refuses it) beside this change's case pointer; the merged paragraph reads as one argument |

## Notes a later reader may want

- The case's found-half assertion reads the sweep's clause, which is capped at five entries
  (`outside_write_clause 5`, `tools/loop/run.sh:993`): the staged shell is the last pid forked and therefore the last
  entry, so the shape is named as long as no more than five processes stand in the project. It is green in the
  full suite under `--jobs` and in five repeats (`repeat-round2.log`); a case that must survive a busier host would
  stage the shell as the *first* entry rather than add a sixth.
- `tasks.md` 3.1 names `--case 'run#*Write*'` while the archived run is `--case 'run#*rite*'` (the selection round 1
  used). Same group, two spellings; the selection is the one the evidence file records.
