# fix-a-closure-names-a-commit-the-base-lacks

## Why

The item is #715. A closure writes the settled state and posts its reason, and the evidence a loop closure cites is
the item's branch — its archived change and its gate tallies. **No path compared that evidence with the base
branch.** `tools/issues/close.sh` on the base is 210 lines and holds no git call at all (`git show
76fbe7ea:tools/issues/close.sh | grep -c '\bgit\b'` → `0`), and the branch's own commit is what each of the five
unearned closures cited: #44's
comment names `4c2e3871` on `fix/issue-44-phone-diagnostic-locale`, a commit `main` did not have.

**Root cause, in one sentence:** a completed closure records `status/fixed-by` without comparing the commit its
evidence names with the base branch, so "the item is fixed" and "the tree carries the fix" are two claims that no
single write has to make true.

**The red case, on the base `76fbe7eaca3e77e398fecb37b957fa798f1e161f`** — two suites, one case each, both cases
written and registered before any production line, both suites run with the engines untouched (`2026-11-07T19:56:29Z`):

```
$ bash tools/issue-tooling-selftest.sh --case close#refusesAClosureTheBaseDoesNotCarry
  a closure naming a commit the base does not carry is a policy refusal: close: unknown argument --landed: expected '3', got '2'
FAIL close#refusesAClosureTheBaseDoesNotCarry

$ tools/loop-selftest.sh --case 'labels#closeRefusesACommitTheBaseDoesNotCarry'
labels#closeRefusesACommitTheBaseDoesNotCarry|   a closure whose commit the base does not carry is a policy refusal: loop: labels: unknown argument --landed: expected '3', got '2'
FAIL labels#closeRefusesACommitTheBaseDoesNotCarry
loop-selftest: 1 of 1 cases failed
```

`evidence/red-on-HEAD.xml` is those two runs transcribed into `testsuites` (`tests=2 failures=2 errors=0`), because
the shell runners write no JUnit XML; `evidence/red-close-tooling.log` and `evidence/red-labels-loop.log` are the
runners' own transcripts.

**Owning requirement:** `openspec/specs/issue-backlog/spec.md:152` "Closure is the only terminal state, and deletion
is not offered" — it owns what a closure is and what it must carry, and now owes the commit its claim is read
against; and `openspec/specs/agent-issue-loop/spec.md:1430` "The item is closed on a base that carries its fix" — it
owns the loop's landing, whose closure is the write that was never checked.

**Owning guideline section:** `guidelines/Backlog.md` §4 "The invariants" (the table at `:93`, invariant I4 "closure
is the only terminal state and carries evidence") — the change adds I19, the claim the closure's evidence has to be
true of; and `guidelines/Backlog.recipes.md`:806 "Refusals, records and the landing", whose paragraph already says
`status/fixed-by` and "the fix is in the tree" are one claim and now owes the half that reads it.

surface: 2 cases added (one per suite: `tools/loop-selftest.sh --list | wc -l` → `368`,
`bash tools/issue-tooling-selftest.sh --list | wc -l` → `164`), 0 cases rewritten, 0 fixtures realigned, 2 mutation
records added (`bash tools/loop-mutations.sh --list | wc -l` → `284`; `bash tools/mutate.sh --mutations
tools/close-mutations.json --list | wc -l` → `7`) — `./tools/look.sh 'merge-base --is-ancestor' --files` reports 31
files and names every place the same question is already asked (`tools/loop/state.sh`, `tools/loop/worktree.sh`,
`tools/loop/loop-clean.sh`, the loop's cases and mutations, the guidelines and the archived changes), so the check is
written beside a question this tree has answered four times and never at the closure; 1 requirement added in
`issue-backlog` with 1 scenario, 1 requirement added in `agent-issue-loop` with 1 scenario, 1 guideline section
extended in two documents (`Backlog.md` §4 gains I19, `Backlog.recipes.md`:820's landing paragraph gains one
sentence).

## What Changes

1. **`tools/issues/close.sh` gains `--landed <ref>`** — the commit a closure's claim rests on. When the closure is
   a completed fix and the ref is given, the commit is resolved with `git rev-parse` and the call is refused (exit
   3, `not-on-base`) unless `git merge-base --is-ancestor` finds it on the base branch. The base branch is the
   profile's `baseBranch`, falling back to the repository's own default branch where the profile records none. The
   refusal names the ref, the base branch and the landing step, and it closes nothing: it decides whether the claim
   may be written. A `--not-planned` closure asserts no landing and refuses `--landed` as a usage error. Two answers
   are the same refusal rather than a second one: a base branch this tree cannot resolve, and a ref that does not
   resolve, are a claim the check cannot read, and the detail says which of the two it was.
2. **`tools/tooling.json` declares `baseBranch`** — the branch name is this project's, so it is a profile value and
   not a literal in the engine (I13).
3. **`tools/loop/labels.sh` asks the question before it writes the state**, through `loop_landed_on_base`
   (`tools/loop/lib.sh`), and then forwards `--landed` to the closure (`close` verdict). The order is the finding the
   pre-gate round made: `close.sh` refuses after the loop has written `status/fixed-by`, which left the refused item
   settled on a commit the base does not carry, so the same check runs first and a refused closure writes nothing.
   `--landed` on a verdict the loop does not close on is a usage error (exit 2): another verdict claims no landing.
   **`tools/loop/run.sh`** passes the commit its landing made at both closure sites: `merge_committed_branch` re-reads
   the base after the merge and `merged_sha` is that commit — the same value the item's record stores as `merged.sha`
   — and the adoption path passes the base it re-read after merging the finished attempt. No case exercises those two
   appends, so neither delta claims that half.
4. **Two cases, one per suite**, each asserting the refusal and then the closure on a commit the base carries:
   `close#refusesAClosureTheBaseDoesNotCarry` (which also asserts the landing step the refusal names, a ref that does
   not resolve, that nothing is written for either refusal, that a closure naming no commit is unchanged, and that
   `--landed` beside `--not-planned` is a usage error) and `labels#closeRefusesACommitTheBaseDoesNotCarry` (which also
   asserts the commit and the base branch the refusal names, that the refused verdict wrote neither a close nor a
   state edit, and that a verdict which claims no landing refuses the flag).
5. **Two mutation records** (`close-mutations.json` A1.6, `loop-mutations.sh` 99.1): each removes one half — the tooling's
   ancestry check, and the loop's own read of the base before it settles the item (which leaves the state written and
   the closure refused after it) — and each named case goes red and green again.

## What this does not do

- It does not scan past closures or re-land a branch: a settled item is not queued and this change revisits none of
  the six branches the item measured. It makes the *next* closure's claim true or refused.
- It does not make `--landed` mandatory. A closure that names no commit keeps the behaviour it had — a person's
  closure, a sensor's, a not-planned retirement — because the check needs a commit to compare and such a closure
  offers none; requiring it would refuse the paths that legitimately have no landing, and the loop's own two call
  sites are the ones that do have one.
- It does not close, reopen or re-label anything itself, and it does not decide whether a commit *is* the fix — only
  whether the base branch contains it.
