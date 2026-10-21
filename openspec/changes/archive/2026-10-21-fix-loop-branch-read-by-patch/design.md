# Design — fix-loop-branch-read-by-patch

## The root cause, and what it explains

One mechanism: **the loop asks git whether the base's commit objects contain a branch's commits, when the question
it means is whether the base's patches contain them.** Three readers ask it — `worktree.sh`
(`cmd_start_item`, `merge-base --is-ancestor`), `prescreen.sh` (`previous_attempt_branch_of`, the same call on the
branch-name prefix) and `report.sh` (the action list, `rev-list --count "$base..$branch"`) — and one landing
convention breaks all three: the loop lands an item's work as `fix: … (#N)` plus `— archive with its evidence`,
which is a **new** commit. A landed branch is then never an ancestor of the base and always counts as "commits the
base does not have", so the item is refused for ever at the start and spent as a decision slot by the pre-screen,
and the report names the leftover as work to land.

Measured (`probe-landed-branch.sh`, which prints no timestamp; its run is
`.pi/issue-loop/items/228/probe-landed-branch.txt`): one branch, `rev-list --count main..branch` → 1,
`merge-base --is-ancestor` → not an ancestor, `git cherry main branch` → **0 unlanded**. The three cases in
`evidence/red/` show each reader's symptom: exit 5 at the start, `needs-decision` from the pre-screen, and a
`land …  1 commit(s)` action in the report.

## The single fix

`tools/loop/lib.sh` gains one helper, and the three readers ask it:

```
loop_unlanded_commits <repo> <base> <branch>   #  <sha> <subject> per unlanded patch; 1 when git cannot answer
```

`git cherry <base> <branch>` marks `-` for a commit whose patch the base already carries and `+` otherwise, which
is exactly the question. The helper prints the `+` commits and returns non-zero only when `git cherry` itself
fails, so "git cannot say" never reads as "nothing to land".

- `cmd_start_item`: nothing unlanded → reset the branch onto the recorded base and print `reused` (the branch it
  resets carries nothing the base lacks — by patch — so nothing is thrown away); otherwise refuse as before, naming
  the unlanded commits. `git cherry` failing refuses too: the branch was not read, so starting on it would be a
  guess.
- `previous_attempt_branch_of`: the prefix's branch is the previous attempt only while it carries an unlanded
  patch; a branch git cannot compare is still read as carrying work, which keeps the pre-screen's refusal on the
  safe side. That second answer is code, not a scenario: no case asserts it.
- The report's branch loop: the count is the number of unlanded patches; a branch with none is skipped. The
  failure path keeps the `ahead=1` it had, so a branch git cannot read is still named rather than counted as
  zero; that path is code too, and no scenario asserts it either.

Blast radius: three call sites, one helper, no signature, flag, exit code, state field, label or report line
changes. The adoption path (`cmd_merge_previous_attempt`), the review-failure refusal, the stale-base refusal, the
two commits of `end-item` and the merge base bookkeeping are untouched — and the adoption path cannot be reached
by a landed branch anyway, because `carried_change` requires the branch to add an archived change whose directory
the base does not already hold.

## Alternatives the evidence eliminated

1. **Keep ancestry and delete the branch when the run lands the work.** Eliminated: the tooling has no delete path
   by design (`grep -rn 'push --delete\|branch -D' tools/` is empty — the run never destroys a branch, so a
   previous attempt's work cannot be lost by a script), and the branches this item measured were landed *by hand*
   from the tracker, which no code path of the run can hook. The defect would survive every manual landing.
2. **Compare the branch tip against the base's log by commit subject (`--grep '(#N)'`).** Eliminated: the subject
   is a string a person writes, not a property git computes; a re-landed item carries the id twice (measured:
   `tools/issues/duplicates.sh:16` records that "today's landing practice cherry-picks"), and #228's own evidence
   shows three different subjects (`(#213)`, `(#224)`, `(#56)`) for one landed branch. A subject match is neither
   necessary nor sufficient.
3. **Treat any branch whose tip is not an ancestor as landable and let the caller decide.** Eliminated: that is the
   current behaviour stated as a policy, and it is what spends a decision slot per run on a branch that needs no
   decision — the item's own bound is `0`.
4. **Squash-merge every leftover onto the base so ancestry agrees again.** Eliminated: it rewrites the base under
   a run whose state records that base commit (`check_base` refuses a moved base, D4) and it lands work nobody
   reviewed; the report's "a branch is an action because of what it *carries*" rule exists to let a person land,
   not the run.

No option remains that is both compatible with the no-delete rule and decidable from git, so gate 3 holds: one
fix follows from the measured mechanism.

## Threading, lifecycle, and the project's design principles

None applies. This is shell tooling read by `tools/loop-selftest.sh` and started by `tools/loop/run.sh`; no
Android component, dispatcher, coroutine, ViewModel, native call or lifecycle is involved. The helper is a pure
function of three git refs and one repository path, and it is called on the same thread as the readers it replaces.

## Verification

- **Red on HEAD `2e732f0`**, one case per surface, with the failure message quoted in `proposal.md` and the run
  copied to `evidence/red/`:
  `worktree#reusesABranchWhoseWorkAlreadyLanded`, `prescreen#landedBranchIsNotADecision`,
  `report#landedBranchIsNoAction`.
- **Green on this change**: the three cases, the four cases that own the same lines
  (`worktree#reusesABranchThatCarriesNoWorkOfItsOwn`, `worktree#refusesABranchCarryingAPreviousAttempt`,
  `prescreen#previousAttemptBranchIsADecision`, `report#actionsNameDecisionAndBranch`) and the full offline suite
  `tools/loop-selftest.sh` — the gate for `tools/loop/**`. No Gradle gate applies: the change touches no compiled
  file.
- **Falsification (revert-check)**: one mutation per site restores the ancestry question (33.1 in the helper, 33.2
  in the pre-screen, 33.3 in the report) and the named case must go red, restore, green. The three mutations that
  owned the lines this change rewrote (12.5, 12.6, 13.1) are updated to the new lines and re-run.
- **No on-device check**: there is no UI, no rendering and no coordinate-free diagnostics line to read; the
  report's own text is the observable, and the cases assert it.

## Rollback

Revert this commit. The three readers go back to ancestry, the landed branch is refused again, the pre-screen's
`needs-decision` returns and the report names the leftover again; the three cases fail with the messages recorded
in `evidence/red/`. Nothing on disk needs repair: a reused branch held nothing the base lacked, and a branch the
report stops naming still exists for a person.
