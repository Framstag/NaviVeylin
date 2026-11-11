# Design — the answer is the item's next phase

## Root cause

The branch rule asks git what the branch carries, and answers a person's question from it. `answer.sh --continue`
removes `agent/needs-decision` and `status/on-hold` and records the answer as a comment; nothing on the branch moves.
So the next pre-screen asks the same question again — the arm as it stands on the base `87c523cd`:

```
      case "$attempt_state" in
        unfinished|rework-cut)
          …
        *)
          verdict="$decision_verdict"
          reason="branch $attempt_branch carries a commit the run's base does not — a previous attempt's work is on it"
```

`tools/loop/prescreen.sh:405-411` on the base, `:443` onwards at this change's head. The labels go back on, a
second escalation comment is posted (the base's suppression at `:411` needs the label, and the answer removed it),
and the item is excluded from every later queue — there is no
arm anywhere that reads the answer, and `start-item` refuses the same branch, so no path reaches the work (#847).

## The fix

The rule stays what it is: what the branch carries decides. What changes is that it is decided **after** the owner's
answer, where the answer exists:

| input | read from | what it decides |
|---|---|---|
| the branch's state | `worktree.sh branch-state` (git, unchanged) | adoption, refusal, or work |
| the question the branch asks | the branch and its head commit | the attempt the question is about |
| the answer, and the question it quotes | the item's comments, one read per branch-carrying item | whether that question is answered |

Three pieces:

1. **The question names the attempt.** The reason becomes `branch <name> carries a commit the run's base does not — a
   previous attempt's work is on it (head <12 hex>)`. Without it, two successive attempts of one item produce two
   byte-identical questions, and any rule that reads "an answer about this branch" would execute the first attempt's
   answer over the second attempt's refusal — a rework nobody asked for, on a branch whose reviewer refused a
   different change.
2. **The answer is matched by the question it quotes.** `answer.sh` records the question beside the answer
   (`<!-- answers: … -->`, the marker #196 exists for); `branch_question_answered` reads the comments once, takes the
   last answer's quoted question (falling back to the nearest earlier non-answer comment, which is what the answer
   path itself does), and reads it as answered when it names this branch **and** this attempt's head. An answer
   recorded before a question named its attempt carries the branch alone and is read as an answer about the branch —
   there was no attempt identity to bind it to, and those are exactly the items stuck today (#830, #845, #832, #837).
3. **The answer is executed, durably.** The attempt's commits are kept under `loop/leftover/issue-<n>-<slug>-<utc>`
   and the item's branch is cut fresh at the next start — the shape a blocked attempt's branch already gets (#232).
   `worktree.sh set-aside <issue> <branch>` is that rename, and `start-item` now takes the leftover's name from the
   same `leftover_name` helper, so one place owns the namespace `prune-landed` sweeps. The rename is what makes the
   answer durable: the run's state is machine-local and replaced by the next `init`, while the ref is not, so the
   next run reads what was executed from git and not from a comment that would be re-read as an answer for ever.

An `adoptable` branch is deliberately not in this arm: its evidence admits it, the run merges it and closes the item,
and no answer was needed to get there. A dry run executes nothing (it writes no ref) and records the decision it
would otherwise be, so its report cannot claim a leftover that does not exist.

## Blast radius

- `tools/loop/prescreen.sh`: one helper (`branch_question_answered`), the `*)` arm's verdict, and its reason. The
  other arms (`unfinished`, `rework-cut`, `landed`, `absent`) are untouched.
- `tools/loop/worktree.sh`: `leftover_name` and `set_aside_branch` extracted, `start-item` reading the first, the new
  `set-aside` verb, and its usage line.
- `tools/loop/tests/loop-cases.sh`: 2 cases added (`prescreen#anAnsweredBranchIsWorkNotADecision`,
  `prescreen#anAnswerToAnotherAttemptDoesNotReworkTheBranch`), the suite 407 → 409.
- `tools/loop/tests/loop-mutations.sh`: record `274.1`, which drops the answer read and the rename.
- `guidelines/Backlog.recipes.md`, section 7d: the paragraph beside the adoption rule, extended with the answer's precedence.
- The spec delta: one requirement modified, two scenarios added.

## Alternatives eliminated

- **Read the decision label's absence as "answered".** Cheap, one `jq` over labels already read, but it cannot tell an
  item the owner answered from one a run was killed on after its review refused the change — the first is work, the
  second is a question the run never got to ask. It would set aside an attempt nobody has read.
- **Ask the answer for nothing and match the branch name only.** That is the ambiguity the head commit removes: an
  item refused, answered, reworked and refused again has two answers naming one branch, and the second refusal would
  be reworked unread.
- **Work the branch in place (a rework round on the attempt's own commits).** The larger change, and the one the
  branch rule refuses by design: `start-item` branches off the base, an archived change is the attempt's verdict, and
  the reviewer's findings live in it. Keeping the commits under a leftover name is the shape the loop already has for
  work it resumes, and nothing is thrown away.
- **Merge an answered attempt anyway.** The owner said "continue", not "land"; merging would put a change the
  reviewer refused onto the base on the strength of an answer about something else.

## Open decisions

none
