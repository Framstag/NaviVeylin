# Tasks — a closure's claim is read against the base branch

Every verification below was run in the change root `/home/tim/projects/NaviVeylin/.pi/worktrees/loop`, on the base
`76fbe7eaca3e77e398fecb37b957fa798f1e161f`. No compiled file is touched — the change is three shell engines, one
profile key, two case files, two mutation records and the documents — so the shell suites are the whole gate for it
and no build is owed.

- [x] 1. **Write the two red cases first, and register them.**
  `close#refusesAClosureTheBaseDoesNotCarry` builds a throwaway repository whose branch carries a commit the base
  branch does not, and asserts the refusal (exit 3, `not-on-base`, the commit, the base branch and the landing step
  named, nothing written), a ref that does not resolve, that `--landed` beside `--not-planned` is a usage error, the
  closure on a commit the base carries, and that a closure naming no commit is unchanged;
  `labels#closeRefusesACommitTheBaseDoesNotCarry` asserts the same through the loop's `close` verdict, plus that a
  verdict which claims no landing refuses the flag. Both are registered in the case list and the dispatch.
  - verified: on the base engines both fail — `evidence/red-close-tooling.log` and `evidence/red-labels-loop.log`,
    both taken `2026-11-07T19:56:29Z`, transcribed to `evidence/red-on-HEAD.xml` (`tests=2 failures=2 errors=0`).
- [x] 2. **`tools/issues/close.sh`: `--landed <ref>` and the refusal.** Parsed and refused beside `--not-planned`
  (exit 2, a usage error) before the profile is resolved; then, after the gate and the ownership scope and before
  the settled state is resolved or anything is written, resolved with `git rev-parse --verify --quiet
  "${landed}^{commit}"` and read against the base branch with `git merge-base --is-ancestor` (exit 3, `not-on-base`).
  The base branch is the profile's `baseBranch`, falling back to the repository's own default branch
  (`refs/remotes/origin/HEAD`, as the short `origin/<branch>` name). A base branch the tree cannot resolve and a ref
  that does not resolve are the same reason code with their own detail: both are a claim this check cannot read, not
  a landing that is owed.
  - verified: `bash tools/issue-tooling-selftest.sh --case close#refusesAClosureTheBaseDoesNotCarry` → `PASS`.
- [x] 3. **`tools/tooling.json`: `baseBranch`**, so the branch name stays a project fact and no engine names it
  (I13).
  - verified: `bash tools/check-portability.sh` → `OK — 56 engines and 7 templates name no project value (118
    value(s) read from the project)`; the profile is valid JSON (`jq -e .`).
- [x] 4. **`tools/loop/labels.sh` asks the question before it writes the state, then forwards `--landed`.**
  `loop_landed_on_base` (`tools/loop/lib.sh`, four answers: on the base, off it, the ref does not resolve, the base
  branch does not resolve) is asked *before* the `update.sh` write, so a refused closure writes nothing at all — no
  state, no comment, no close — and exits 3 like the closure's own refusal. `--landed` on any verdict but `close` is
  a usage error (exit 2), because another verdict claims no landing to read.
  - verified: `tools/loop-selftest.sh --case 'labels#closeRefusesACommitTheBaseDoesNotCarry'` → `PASS`;
    `tools/loop-selftest.sh --group labels` → `all 9 cases passed` — `evidence/suite-loop-labels.log`.
- [x] 5. **`tools/loop/run.sh` hands the commit its landing made to both closure sites** — `--landed "$base"` on the
  adoption path (`adopt_finished_attempt`, the base re-read after the merge) and `--landed "$merged_sha"` on the
  run's own (`merge_committed_branch` sets `merged_sha="$base"`). Each is one guarded append beside the `close_args`
  it belongs to.
  - verified: the three cases that write a closure through those sites stay green —
    `run#mergesItsCommittedBranchBeforeClosing`, `run#mergesAFinishedAttemptAndClosesTheItem`,
    `run#doesNotCloseOnAMergeTheBaseCannotTake` → `PASS`.
  - **not verified by any case:** the two appends themselves. The three run cases assert the merged base, the merge
    message and `run.base`, and stay green with either append dropped, so nothing in this record shows a run's own
    closure *carrying* the commit it landed. That is why neither capability delta claims it: the deltas are the
    engine's refusal and the loop's own ask, which their cases assert.
- [x] 6. **One mutation per half.** `close-mutations.json` A1.6 removes the tooling's ancestry check;
  `loop-mutations.sh` 99.1 makes the loop's own read answer "on the base" — the state write then lands and the
  closure is refused after it, which is the half-written item this change exists to prevent.
  - verified: `bash tools/mutate.sh --mutations tools/close-mutations.json --only A1.6` → `RED … GREEN`,
    `evidence/mutation-close-A1.6.log`; `bash tools/loop-mutations.sh --only 99.1` → `RED … restore … green again`
    (`2026-11-07T20:00:06Z — a refused closure wrote to the tracker: gh issue edit 21 --add-label status/fixed-by …`),
    `evidence/mutation-labels-99.1.log`; `bash tools/loop-mutations.sh --check` → `all 284 cases passed`.
- [x] 7. **The neighbouring cases of both engines, unchanged.** Every `close#` case of the issue tooling and the
  whole issue tooling suite are green, and the loop's `labels` group with the new case in it.
  - verified: `bash tools/issue-tooling-selftest.sh` → `all 164 cases passed (JDK 27, offline, no token, HEAD
    76fbe7ea, tools d68b800a0ee3, 2026-11-07T19:58:54Z)` — `evidence/suite-issue-tooling.log`;
    `tools/loop-selftest.sh --case 'driver#everyCaseDeclaresTheFilesItCanReach'` → `PASS`.
- [x] 8. **The owning guideline sections carry the rule.** `guidelines/Backlog.md` §4's invariant table gains I19
  (the claim a closure's evidence has to be true of, with its falsifying observation and the two cases);
  `guidelines/Backlog.recipes.md`:820's landing paragraph gains the half that reads the claim.
  - verified: the row is `guidelines/Backlog.md:113`, the sentence
    `guidelines/Backlog.recipes.md:820`; `tools/check-doc-routes.sh` → `all 329 section references resolve, all 7
    skill references resolve`.
- [x] 9. **Grade the diff against the reader's three predicates** (`openspec/specs/agent-issue-loop/spec.md`, "A
  review finding is blocking or it is not"). Every clause of the `issue-backlog` delta is asserted by the case that
  names it, `close#refusesAClosureTheBaseDoesNotCarry`: the refusal and its reason, the ref, the base branch and the
  landing step it names, that nothing is written, the same call on a commit the base carries, the closure that names
  no commit, and the reason beside `--not-planned`. Every clause of the `agent-issue-loop` delta is asserted by
  `labels#closeRefusesACommitTheBaseDoesNotCarry`: the refusal, the commit and the base branch named, and that no
  state edit and no close was written. Every claim of prose points at a hunk or was withdrawn — task 5's
  "not verified" note is what keeps the run's two appends out of both deltas.
  - verified: `bash tools/check-portability.sh` → `OK`; `tools/check-openspec-hygiene.sh` → `consistent`;
    `tools/check-change-readiness.sh openspec/changes/fix-a-closure-names-a-commit-the-base-lacks` →
    `LOOP-CHANGE-READY: ok`; `tools/check-doc-routes.sh` → `all 329 section references resolve`.
