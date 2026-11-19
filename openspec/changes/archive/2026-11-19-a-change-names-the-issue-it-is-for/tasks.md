# Tasks — an open change names the issue it is for

The base is `0560f439236ac4e798516317432d9b131bc63d49`. The gate is not this child's to run: the run hands this
item's one gate to the child after it, so the tasks that write the archive and take the gate result are that child's.
The evidence each line cites is copied into this change's `evidence/` by that child.

## 1. The declaration

- [x] 1.1 `openspec/changes/<name>/.openspec.yaml` carries `x-issue: <number | none>` on this change and on the 8
      other changes open at this base — 4 from the number their own proposal names, 5 declared `none` — verified by
      `tools/check-openspec-hygiene.sh --root .` reporting every open change and refusing none
      (`evidence/openspec-hygiene.log`: `a-case-name-resolves-to-one-harness=#895, …=#931, …=#950, …=#882`, the
      other five `=none`).
- [x] 1.2 `tools/issues/create.sh`'s "no change directory" refusal states the field beside `openspec new change`,
      so the person who files a change reads there what the pinned CLI cannot write for them (the scaffolder has no
      `--issue`). The message names the field the check reads, `'x-issue: <number>'` / `'x-issue: none'`
      (`evidence/create-sh-message.log`); no case asserts this message, so the message is its own evidence. The
      suite that drives this file still passes — `issue-tooling-selftest: all 177 cases passed`
      (`evidence/issue-tooling-selftest.log`).

## 2. The check

- [x] 2.1 `tools/check-openspec-hygiene.sh` reads every open change's `x-issue` and exits 1, naming both
      directories, when one number appears twice — the case
      `artifact-hygiene#twoOpenChangesNamingOneIssueAreRefused` (`green-hygiene-selftest.log`: all 10 cases passed)
      — and the base checker fails that case (`red-on-base.log`, transcribed as `evidence/red-on-base.xml`).
- [x] 2.2 The same pass refuses a change with neither the field nor a `fix-issue-<N>-` name, accepts
      `x-issue: none` as a declaration, and reads the number a `fix-issue-<N>-` name supplies — the case
      `artifact-hygiene#aChangeNamingNoIssueIsRefused` carries all three states, and its three arms fail on the base
      checker (`evidence/red-on-base.log`).
- [x] 2.3 The pass reports the change under the number its field declares, reading neither the directory name nor
      the proposal's prose — the case `artifact-hygiene#theIssueFieldIsReported`, whose tree names `#111` in its
      proposal and `x-issue: 928` in its field (`evidence/red-on-base.log` for the base's silence on it).

## 3. The pre-screen

- [x] 3.1 `tools/loop/prescreen.sh` runs that check over the source tree once per screen — its path read from
      `LOOP_ARTIFACT_CHECK` in `tools/loop/loop.conf`, never a literal — and refuses an item whose number two open
      changes carry, with both directories as the evidence and one comment on the item — the case
      `prescreen#anIssueTwoOpenChangesNameIsRefused`, whose control is one claim on the number refusing nothing
      (`evidence/green-prescreen-case.log`: `PASS`, and the 97 cases the prescreen path selects pass in
      `evidence/prescreen-cases.log`).

## 4. The requirement

- [x] 4.1 The delta carries "An open change names the issue it is for" with its three scenarios
      (`specs/documentation-ownership/spec.md`) and "The pre-screen refuses an item two open changes name" with its
      one (`specs/agent-issue-loop/spec.md`); `openspec validate a-change-names-the-issue-it-is-for --strict` answers
      `Change 'a-change-names-the-issue-it-is-for' is valid` (`evidence/openspec-validate.log`). The shipped
      `openspec/specs/` receive them when this change archives, which is the gate child's step.

## 5. The falsification

- [x] 5.1 One mutation per invariant, recorded in `tools/loop/tests/loop-mutations.sh` (`931.1` takes the item's
      reading out of the pre-screen, so the case's refusal never fires), each red for its own reason and green after
      the restore — `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0` (`evidence/mutation-931.1.log`), and
      every record in the set still applies to the file it names (`evidence/mutations-check.log`: all 425 cases).
- [x] 5.2 The three hygiene cases fail on the base checker (`git show <base>:tools/check-openspec-hygiene.sh` in a
      throwaway root, this tree's case file beside it) — 5 failed assertions over 3 cases, `tests="10" failures="5"`
      (`evidence/red-on-base.log`, transcribed as `evidence/red-on-base.xml`), so the guard is shown capable of
      failing before it is claimed to hold.

## 6. Not covered by this change

- [ ] 6.1 The two changes the finding was written from — `fix-a-case-wait-decided-by-its-record` (#928, a duplicate
      of shipped work) and `fix-a-bound-ended-rework-spends-no-decision-slot` (whose `MODIFIED` blocks omit scenarios
      the spec now holds) — are **not** repaired here. They are decided separately: one is duplicate work, the other
      needs its scenarios re-read against the shipped spec. This change only makes their class visible next time.
- [ ] 6.2 A change's spec baseline is not recorded, so a MODIFIED block written before a later archive still goes
      stale silently; this change does not fix that, it only stops two changes describing one item.
- [ ] 6.3 The project's own checkout (not the change root) holds 30 open change directories with no `x-issue` field,
      and the field is not in the diff (`.gitignore` keeps `openspec/changes/*/` out of git), so
      `tools/check-openspec-hygiene.sh` run there refuses them until that tree backfills itself. This change
      backfills the 9 open in *this* tree and states the consequence in `design.md`; the pipeline's checkout holds no
      open change, and the pre-screen reads only the collision finding.
