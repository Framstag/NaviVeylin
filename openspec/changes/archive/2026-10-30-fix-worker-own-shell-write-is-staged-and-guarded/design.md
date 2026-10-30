# Design — fix-worker-own-shell-write-is-staged-and-guarded

## The root cause

`tools/loop/fixtures/pi:84` moves the stub child into the worktree — `cd "$LOOP_WORKTREE"` — *before* it runs the
case's action (`:86`), so every shell a case forks through `PI_STUB_ACTION` carries a worktree process among its
ancestors. `pid_left_the_worktree` (`tools/loop/run.sh:925`–`:937`) places a candidate by reading the working
directory of its **ancestors**, and its caller skips every candidate it cannot place (`:990`). A real child is
started in the project (`tools/loop/run.sh:1140`) and forks its own shells there, so the shape the rule
deliberately cannot place — a shell of the child's own tree standing in the project, never in the worktree — is
exactly the shape the fixture cannot stage. That is why the one case that pins placement
(`run#aWriterTheRunCanPlaceBlocksTheItem`) asserts `blocked`: its writer `cd`s into the project from an action
shell that is still standing in the worktree, which is not a writer a real child produces.

## The fix, one change

One opt-in hook in the fixture and one case that uses it:

- `tools/loop/fixtures/pi` gains `PI_STUB_PROJECT_SHELL`, a script run **before** the `cd "$LOOP_WORKTREE"`, and so
  in the directory the child was started in. It forks no new tree of its own: it is the shell of the child's own
  tree that stands in the project, which is the shape #704 names. Unset or pointing at no file, the fixture's
  behaviour is byte-for-byte what it was, so every existing case is untouched.
- `run#theWorkersOwnShellWriteIsRecordedAndNotPlaced` stages that shape through the hook, and asserts five things:
  the difference is recorded with its path, the item is `done`, the item is `closed`, the reading names the shell
  that wrote among the processes standing in the project, and the reading's `attributed` field is `none`. The fourth
  is the found half, and it is what a reader needs to believe the narrow rule does not cover this shape: the
  reading's `attributed=none` alone is printed identically by a run whose walk of the child's tree returned
  nothing, so the shell records its own pid and the reading has to name it. The fifth states the half the rule is
  about — found, and placed by nothing.
- The spec delta adds the scenario *A shell the child forks in the project is found and not placed*, naming
  exactly that case; `guidelines/Backlog.md`'s "The loop" section's sentence at `:322`–`:325` gains the case that pins the shape it
  names, next to the sentence itself, because the record half of the item landed with `db37a3a8` and only the
  guard was missing.

Nothing in `tools/loop/run.sh` changes. The narrow rule is the trade that made
`run#aPeerWriteWhileAChildStandsDoesNotDecideTheItem` possible, and a change that places this shape would reverse
the verdict #274/#286 refused (issue #288) — the item says so itself, and this change is the record and the guard
it asks for instead.

## The alternatives the evidence eliminated

- **Place the shape (narrow the exclusion to the child itself and not its shells).** Eliminated: it reverses the
  recorded verdict, which is what `run#aPeerWriteWhileAChildStandsDoesNotDecideTheItem` and mutation 12.11 exist to
  refuse.
- **Stage the shape through `PI_STUB_ACTION`, with a shell of the action that `cd`s into the project.** Eliminated
  by measurement, and the measurement is a case already in the suite: that is the writer
  `run#aWriterTheRunCanPlaceBlocksTheItem` stages, and the item comes back `blocked`, because the action shell is
  standing in the worktree and the walk reads ancestors and not the writer alone. Staging it this way would assert
  placement, not the loss of it.
- **A case over the guideline text (assert the sentence names the shapes).** Eliminated: the sentence landed with
  `db37a3a8`, so such a case is green on HEAD and proves nothing about the loop; the item asks for the guard on the
  behaviour, and this change leaves the sentence alone except for the pointer to it.
- **No case, only the guideline sentence.** Eliminated: the sentence is already there and the item still stands,
  because a sentence is not a refusal — nothing in the tree fails if a later change starts placing this shape.

## Blast radius

- `tools/loop/fixtures/pi`: one inert block plus two lines in its env header. Every case that runs a child reads
  it; none sets the new variable, so none sees a difference. `tools/portability-cases.sh` copies the fixture into
  a synthetic project, and copies a hook nobody set.
- `tools/loop-cases.sh`: one case added (three `demo-owned.txt` occurrences; the file carries 16, the base 13).
  The suite's declared count goes from 287 to 288, and `--list` is what states it.
- `guidelines/Backlog.md`'s "The loop" section: one clause naming the case. `tools/check-doc-routes.sh` resolves section references,
  and the sentence adds none.
- The spec: one scenario added to a requirement whose other scenarios are restated verbatim, which is what
  `openspec archive` merges.
- Nothing compiled, no Gradle case, no engine script, no verdict.

## Rollback

Revert the change's four files — the fixture block, the case and its two registration lines, the guideline clause,
and the change directory. Nothing else reads the hook, and the suite's count returns to 287 by itself, because the
count is read from `--list` and never written down.
