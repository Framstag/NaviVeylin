# Design — the closure's claim is read against the base branch

## The root cause

`tools/issues/close.sh` writes the settled state and posts the reason and evidence, and it never asks git anything:
on the base `76fbe7ea` the file is 210 lines and holds no git call (`git show 76fbe7ea:tools/issues/close.sh |
wc -l` → `210`; `git show 76fbe7ea:tools/issues/close.sh | grep -c '\bgit\b'` → `0`). The evidence a loop closure
cites is the item's **branch** — its archived change, its gate
tallies, the branch's commits — and a branch is exactly what the base may not have. Measured over the run of
2026-10-30 (`git branch --no-merged main` at `699e9889`): six branches carried work `main` did not have, five of
them belonged to items the tracker already showed `CLOSED status/fixed-by`, and two had to be re-landed by hand (the
item's record names `evidence/reland-2026-10-30.md` on #44 and #296 — the item's own measurement, not this change's).

The mechanism that would have caught it exists four times in this tree and never at the closure:
`tools/loop/state.sh:473` (`set-base` refuses a commit the base branch does not carry), `tools/loop/worktree.sh`
(`merge-item`, `adopt-item`, `branch-state`), `tools/loop/loop-clean.sh:257`, and the cases that pin them. Each asks
"is this commit on the base"; none of them is the write that makes the claim.

## The single fix

One flag, one comparison, in the one function whose purpose the claim is:

- **`close.sh --landed <ref>`** — the commit the closure rests on. A completed closure that names one resolves it
  (`git rev-parse --verify --quiet "${landed}^{commit}"`, so a branch name is accepted and reported as the branch the
  caller wrote) and refuses with exit 3 / `not-on-base` when `git merge-base --is-ancestor` does not find it on the
  base branch. The refusal is printed before anything is written: no comment, no label, no close. A ref that does
  not resolve and a base branch this tree cannot resolve are the same refusal with their own detail — neither is a
  landing that is owed, and a reason code nobody can tell apart would report both as one.
- **The base branch is a profile value** (`baseBranch`), because "which branch is the base" is a name of this
  project's and an engine names none of them (I13, `guidelines/Backlog.md` §4). Where a profile records none, the
  repository's own default branch (`refs/remotes/origin/HEAD`) is asked instead — a project that has not written the
  key is still checked against the branch it merges into.
- **The loop hands the commit over, and asks first** — `labels.sh close --landed <ref>` builds the closure's
  arguments and checks the commit (`loop_landed_on_base`, `tools/loop/lib.sh`) *before* it writes `status/fixed-by`,
  then forwards `--landed`; `run.sh` passes the commit its landing made at both closure sites. The order is the
  finding the pre-gate round made: `close.sh` does its own check before every write, but the loop reaches it only
  after its own state write, so the closure's refusal landed on an item that already read `status/fixed-by` —
  settled, per `prescreen.sh`, and therefore never revisited, which is the item's defect rather than its fix. The
  loop asks the same question of the same profile value before that write, so a refused closure writes nothing.
  `merged_sha` is already the commit: `merge_committed_branch` re-reads the base after the merge and the record
  stores it as `merged.sha` (#709). The closure is written *before* `state.sh done` records it, which is why the
  caller hands over the value the record will carry rather than the closure reading it back out of a record that
  does not have it yet. `--landed` on any verdict but `close` is a usage error: another verdict claims no landing,
  and a flag silently ignored is a claim the caller believes was read.

Which branch name each engine reads is not a second copy of one fact: the loop's check reads `loop_base_ref`
(`loop.conf`'s `LOOP_BASE_REF`), the branch the loop's own merges advance and every other loop engine already reads
(`state.sh`, `worktree.sh`, `run.sh`, `loop-clean.sh`); the closure engine reads its own profile's `baseBranch`,
because the issue tooling is a tool this project ports and it reads no loop configuration (`I13`). A tree that names
only one still works: the profile falls back to the repository's default branch, and the loop's configuration always
carries a value.

The check decides only whether the claim may be written. It does not close, reopen, label, merge or replay anything,
and a closure that names no commit is untouched — the same reason a `--not-planned` closure refuses `--landed`: the
verdict is that nothing landed, so a commit beside it is a usage error rather than a claim to check.

## The alternatives the evidence eliminated

- **Compare the branch name the evidence names.** A branch name says nothing about the base; the whole defect is
  branches whose names read as landed. `worktree.sh branch-state` already has to read content for that reason
  (#201/#229), and the loop records a commit (`merged.sha`, #709) precisely because a name is not the claim.
- **Make the loop's landing refuse instead of the closure.** `merge-item` and `adopt-item` already refuse what they
  cannot merge; the five closures happened because the *closure* path never went through them — a hand-landed
  cherry-pick and a run that closed on its own branch both reached `close.sh` with nothing compared. Guarding the
  merge does not guard the claim.
- **Make `--landed` mandatory.** It refuses every closure that legitimately has no landing to name — a person's
  not-planned retirement, a sensor's metric closure — and buys nothing: the claim those closures make is not a claim
  about a commit.
- **Check the commit against the run's recorded base (`state.run.base`) instead of the branch.** The run's base is a
  commit the run recorded, which is the same kind of memory as the branch name; `set-base` already refuses to move
  it off the branch for that reason, so the branch is the authority and the recorded commit is derived from it.

## Open decisions

none

Where the base branch name comes from is not a fork the evidence leaves open: it is a name of this project's, so the
profile carries it (I13) and the engine reads it, with the repository's own default branch as the fallback for a
profile that records none. `--landed` optional is the item's own scope — "the commit its evidence names" — and not a
choice between two designs.

## Blast radius

- `tools/issues/close.sh` — one option, one block before the settled-state resolution, one line of usage. Callers
  that pass no `--landed` are byte-for-byte unaffected; the exit codes are unchanged (`2` usage, `3` policy).
- `tools/tooling.json` — one key (`baseBranch`) and a sentence in the profile's own comment. Nothing reads a key set
  as closed, and `check-portability.sh` reads `.roles` values, not this one, so no value becomes forbidden in an
  engine that does not name it.
- `tools/loop/labels.sh` — one option, its guard, and the closure's arguments built above the state write so the
  commit can be checked before the claim is recorded.
- `tools/loop/lib.sh` — one function (`loop_landed_on_base`, four answers): the loop's own ask of the same question,
  because the closure's refusal is too late for the state the loop writes first.
- `tools/loop/run.sh` — two call sites, each one guarded append; `merged_sha` and `base` are already in scope at
  both. No case asserts those two appends (tasks.md 5), so no delta claims them.
- Two case files, two mutation records (A1.6 in `close-mutations.json`; 99.1 in `loop-mutations.sh`, which mutates
  `tools/loop/lib.sh`), the two capability deltas, the guideline sections — and nothing compiled, so this change owes
  no build.

## Rollback

Remove the loop's check and the flag pass-throughs; the refusal inside `close.sh` is then unreachable and it behaves
as it did, because an absent `--landed` is a closure with no commit to compare. Nothing is recorded differently while
the check passes, so a tree closed under this change carries no state that the rollback has to undo.
