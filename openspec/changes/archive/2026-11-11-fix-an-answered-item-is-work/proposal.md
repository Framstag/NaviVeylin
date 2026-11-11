# An answered item is worked, not asked the same question again

## Why

**Root cause, in one sentence:** the branch rule decides an item by what its branch carries, and it reads that
decision as a question a person owes even after that person has answered it — because the answer takes the decision
**label** off (`tools/issues/answer.sh`) while the branch stays on the item, so the next pre-screen reaches its `*`
arm (`tools/loop/prescreen.sh:443` at this change's head, `:405` on the base, reason `branch <name> carries a commit
the run's base does not — a previous attempt's work is on it`) and writes `agent/needs-decision` and
`status/on-hold` back with a fresh escalation comment.

**The measured state.** Item #830, 2026-11-10: `tools/issues/answer.sh 830 --continue` recorded its answer at
05:40:15Z quoting the question it answered, and removed both labels; the run started at 08:20:31Z wrote both back and
posted `branch fix/issue-830-… carries a commit the run's base does not — a previous attempt's work is on it`, which
is that arm's reason verbatim (`tools/loop/prescreen.sh:407` on the base, `:474` at this change's head). The label
was provably gone before that read, because a second escalation
is suppressed while it is present (the base's `tools/loop/prescreen.sh:411` sets `already_escalated`) and the answer
comment
exists. The same pre-screen named #845 `review-refused`, #832 and #837 `archived-not-green` under the identical
reason — four items of the 25 it excluded. #830's owner then merged the branch by hand, because no answer could make
the run do it.

**The red case, on the base `87c523cd`** (`bash tools/loop/tests/loop-selftest.sh --case
'prescreen#anAnsweredBranchIsWorkNotADecision' --jobs 1`, 2026-11-11T16:13:04Z):

```
prescreen#anAnsweredBranchIsWorkNotADecision|   an item whose owner answered its branch question is work, not a decision asked a second time: expected 'eligible', got 'needs-decision'
FAIL prescreen#anAnsweredBranchIsWorkNotADecision
loop-selftest: 1 of 1 cases failed
```

`evidence/red-on-HEAD.xml` is the tally of that run (`tests=1 failures=1 errors=0`), kept beside
`evidence/red-on-HEAD.log` because the shell runner writes no JUnit XML; the same case after the engine change is
`evidence/green-on-change.log` and `.xml` (2026-11-11T16:17:01Z, both new cases green), and
`tools/loop/tests/loop-mutations.sh --only 274.1` re-creates the red state from the changed tree and restores it
(`evidence/mutation-274.1.log`: `RED 274.1`, `restore 274.1 … green again`, `red-when-mutated: 1 · not-noticed: 0 ·
restore-failures: 0`, 2026-11-11T16:17:09Z).

**Owning requirement:** `openspec/specs/agent-issue-loop/spec.md`, "Version control belongs to the run's script, not
to the agent" (`:306`) — modified: it said a branch whose evidence does not admit adoption is refused and escalated,
and it now says that is true until the owner answers, whose answer the run executes.

**Owning guideline section:** `guidelines/Backlog.recipes.md`, section 7d "Refusals, records and the landing" — the adoption
paragraph that states which branch is the owner's and which is work; the answer's precedence is the clause added to
it.

surface: 2 cases added, 2 engine scripts (`prescreen.sh`, `worktree.sh`) and 1 guideline section touched, 2 scenarios
added to 1 requirement, 1 mutation record; counted with `bash tools/loop/tests/loop-selftest.sh --list | sed -n
'$='` (409 cases), `tools/look.sh '^#### Scenario' --path openspec/specs/agent-issue-loop/spec.md` and
`tools/look.sh 'review-refused|archived-not-green|review-unanswered'` — the outputs of the two `look.sh` calls live
only in the item's event stream, so no number they printed is claimed here (pre-gate review).

## What Changes

1. **The pre-screen reads the answer it is about to re-ask.** When the branch's state is one whose evidence does not
   admit adoption (`review-refused`, `review-unanswered`, `archived-not-green`), the pre-screen asks the tracker for
   that item's comments — one read, only for the items that carry such a branch — and matches the question each
   recorded answer quotes (`<!-- answers: … -->`) against the question the branch states.
2. **The branch question names the attempt it is about.** The reason gains the branch's head commit
   (`… a previous attempt's work is on it (head <12 hex>)`), so an answer binds to an attempt and not merely to a
   branch name: an answer to an earlier attempt's question leaves the current attempt's question standing.
3. **The answer is executed where it is read.** The attempt's commits are kept under a `loop/leftover/issue-…` name
   by a new `worktree.sh set-aside` verb — the same rename `start-item` already makes of a blocked attempt's branch,
   now with one owner of the leftover's name — and the item is queued as work, to be worked again on its own branch,
   cut fresh from the run's base.
4. **A rename git refuses stays the question.** When the branch cannot be set aside (another worktree stands on it),
   the verdict stays the decision and the reason says the recorded answer was not executed, so a set-aside that did
   not happen cannot read as one that did.

## What this does not do

- It does not rework the attempt in place. The reviewer's findings stay on the leftover branch's archived change, and
  the item starts a fresh attempt from the base — the resume path is #754's, and nothing here replaces it.
- It does not touch the adoption path: a branch that *is* admissible is still merged and its item closed, and an
  answer recorded on such an item changes nothing about that.
- It does not change `answer.sh`, the labels, the decision cap or the escalation wording beyond the attempt's head
  in the branch reason.
