# Tasks — fix-worker-own-shell-write-is-staged-and-guarded

## 1. Stage the shape the fixture cannot stage

- [x] 1.1 `tools/loop/fixtures/pi`: add `PI_STUB_PROJECT_SHELL` — a script run before the `cd "$LOOP_WORKTREE"`,
      so in the project the child was started in — and name it in the fixture's env header.
      Verify: `bash -n tools/loop/fixtures/pi` and `grep -n 'PI_STUB_PROJECT_SHELL' tools/loop/fixtures/pi` (three
      lines: the env header's entry and the block's two). No existing case sets it, so no existing case changes.
- [x] 1.2 `tools/loop-cases.sh`: `run#theWorkersOwnShellWriteIsRecordedAndNotPlaced`, registered in the case list
      and the dispatch, staging the shape through the hook and asserting `done`, `closed`, the recorded path and
      `attributed=none` (task 4.1 adds the reading's found half).
      Verify: `bash tools/loop-selftest.sh --case run#theWorkersOwnShellWriteIsRecordedAndNotPlaced` — it is red
      on HEAD's content (`evidence/red-on-head.xml`, `tests="1" failures="1"`) and green once 1.1 lands.

## 2. Record where the reader is

- [x] 2.1 `guidelines/Backlog.md`'s "The loop" section: the sentence at `:322`–`:325` names the case that pins the worker's-own-shell
      shape it names, so a reader of the rule is routed to the test of it.
      Verify: `tools/check-doc-routes.sh` still exits 0 (the clause adds no section reference), and
      `grep -n 'theWorkersOwnShellWriteIsRecordedAndNotPlaced' guidelines/Backlog.md` names one line.
- [x] 2.2 The spec delta's scenario names exactly one case, and the requirement it modifies restates the shipped
      scenarios verbatim apart from the four `Case` lines it adds — the archive merge carries those into the shipped
      spec, so the archived copy is verbatim.
      Verify: `openspec validate fix-worker-own-shell-write-is-staged-and-guarded --strict` exits 0 (run while the
      change was open; afterwards the delta and the shipped spec are the same text at
      `openspec/specs/agent-issue-loop/spec.md:460`).

## 3. Verify the change

- [x] 3.1 The cases the delta touches, run focused, all green:
      `bash tools/loop-selftest.sh --case 'run#*Write*'` and the placement/peer pair
      (`run#aWriterTheRunCanPlaceBlocksTheItem`, `run#aPeerWriteWhileAChildStandsDoesNotDecideTheItem`), which are
      the two verdicts this change must not move.
- [x] 3.2 The project's gate, once, from the change root: `./tools/loop/gate.sh`.
      Verify: the `LOOP-GATE:` line names `failures=0 errors=0`, and the tally XMLs the change cites are copied
      into `evidence/`.
- [x] 3.3 `bash tools/loop/evidence-check.sh openspec/changes/fix-worker-own-shell-write-is-staged-and-guarded --root .`
      reports no dangling pointer.
- [x] 3.4 Archive with `openspec archive`, confirm the directory is under `openspec/changes/archive/` and that the
      new scenario merged into `openspec/specs/agent-issue-loop/spec.md`.

No compiled file is touched, so no build task stands here: the structural check of the fixture
(`bash -n`, 1.1) and the case that runs it (1.2) are what a build would otherwise stand in for.

## 4. Correction round 1 (the review of round 1 refused the change)

- [x] 4.1 `tools/loop-cases.sh`: the shell the hook forks writes its own pid, and the case asserts the reading's
      `LOOP-OUTSIDE-WRITE-ALIVE:` field names it before it reads `attributed=none` — the found half round 1 found
      unasserted.
      Verify: `bash evidence/red-on-head.sh --base` → `tests=1 failures=1` (`evidence/red-on-head-round2.xml`);
      `bash evidence/red-on-head.sh --tree` → `tests=1 failures=0` (`evidence/green-case-round2.xml`).
- [x] 4.2 The scenario's THEN states the two halves the case asserts — the reading names the shell and places none
      of that tree — in the delta and in the shipped spec.
      Verify: `grep -n 'that shell among the processes' openspec/specs/agent-issue-loop/spec.md` names one line.
- [x] 4.3 `evidence/red-on-head.sh --base` installs the base commit's fixture and restores the tree's own copy on
      exit, so the red half is repeatable from the archive; `proposal.md`'s red timestamp, its scenario name and its
      What Changes list match the archived record; the gate is dated from its own log.
      Verify: after `--base` ran, `git status --short tools/loop/fixtures/pi` prints nothing.
- [x] 4.4 The affected group, run focused: `bash tools/loop-selftest.sh --case 'run#*rite*'` — the 8 cases round 1
      selected, all `PASS` (`evidence/green-cases-round2.log`). No gate: this round touches no engine script, no
      fixture and no compiled file (4.1's red and green runs are the test-source scope).
