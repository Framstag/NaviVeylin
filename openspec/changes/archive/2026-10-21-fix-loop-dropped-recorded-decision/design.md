# Design — fix-loop-dropped-recorded-decision

## Root cause

Two omissions on one path, each sufficient to lose the answer:

1. `tools/loop/lib.sh` `loop_issue_json` asked the tracker for `number,title,body,labels,author,state`. `gh`'s
   `--json` returns exactly the named fields, so the item's comments were never fetched.
2. `tools/issues/core/WorkPackage.render` emitted `title` and `body` — the class's own javadoc claims "title,
   body and comments", the code carried two of the three.

`answer.sh` is not part of the defect: it recorded `Decision: 2` and performed the transition
(`status/on-hold` → `status/open`, decision label removed) two seconds later, both visible on the item's
timeline. The next worker started from the package alone (`0` occurrences of `gh issue view` in its event
stream), read a body that still said "Bounds to settle …", re-ran the eligibility gate and asked the same fork
again with a fresh, differently-ordered option list.

## The fix

**A second delimited block, carrying the answer and the question.** `WorkPackage.render` appends

```
<<<ITEM_DECISION
recorded: 2026-10-21T04:52:39Z
question: **The decision:** which bound and which unit does the new size row ship with — options in order: …
decision: 2
ITEM_DECISION
```

and, for an item nobody answered, `<<<ITEM_DECISION` / `recorded: none` / `ITEM_DECISION` — present rather than
omitted, so a worker knows the field exists and is empty instead of having to guess whether its package was
built by a version that cannot carry one.

**Which comment is the answer.** The first line starting with `Decision:` — the one marker `answer.sh` writes.
The last such comment wins: a later answer supersedes an earlier one, and the block is one item's current
policy, not its history.

**Which question it answered.** Read from the answer's own `<!-- answers: … -->` marker, which `answer.sh` now
writes. An answer written before that marker existed (the item this change is about) falls back to the nearest
earlier comment that is not itself an answer, whose first non-empty line is the question — the same convention
`tools/issues/decisions.sh:47-49` already uses to print the queue. The marker is preferred because a person may
write a note between question and answer, and because a later escalation sits between them in this very item's
history.

**Bounds and escaping.** Both fields are escaped against *both* blocks' delimiters (`<<<ISSUE_TEXT`/`ISSUE_TEXT`
and `<<<ITEM_DECISION`/`ITEM_DECISION`) so no comment can open or close a block it does not own, and each is
clipped at 2 000 characters with the clip named in the text. Clipping rather than refusing is deliberate: the
body refuses when it is oversized, because the body *is* the claim; the decision block is a record beside the
claim, and an item nobody can work because one comment is long is worse than a quoted question that says it was
cut.

**Who asks for the comments.** Only the work-package read. `loop_issue_json <n> --comments`; the pre-screen reads
candidates by the hundred and needs none, so its per-candidate payload is unchanged.

**Why the fixture `gh` had to become honest.** The stub returned the whole fixture object whatever `--json`
named, so a caller that forgot a field was green anyway — the same blindness that let this defect ship. `issue
view` now projects the named fields, exactly as the CLI does. The full loop suite (143 cases) and the issue
suite (113 cases) are green with it, so no caller was relying on unrequested fields.

## Blast radius

`WorkPackage.java` (+ the block, escaping generalised to both delimiter pairs), `Cases.java` (two cases),
`tools/issues/fixtures/gh` (field projection), `answer.sh` (the marker line, one extra field on its existing
read), `tools/issue-tooling-cases.sh` (one case), `tools/loop/lib.sh` (optional flag), `tools/loop/run.sh` (one
call), `tools/loop/prompts/worker.md` (one section), `tools/loop-cases.sh` (two cases), a new fixture scenario,
`tools/loop-mutations.sh` (mutation 36.1), `guidelines/Backlog.md` §5 (one rule). No Kotlin, no C++, no Gradle
task, no UI.

## Risk

- **A package grows by up to ~4 KiB.** Bounded by the clip; the body's own bound is untouched.
- **A comment can impersonate a decision.** Any tracker comment whose first line starts with `Decision:` is
  read as one — the tracker allows anyone to comment. That is the same trust the escalation already places in
  the comment stream (the queue prints whatever the last comment says), and the author gate in the pre-screen
  is on the *item's* author. The block is data in the prompt, not an instruction, and the prompt now says so.
- **Field projection in the fixture could redden unrelated cases** if a caller relied on a field it did not
  request. Measured, not assumed: the full suites are green, and the two cases that read an item (`close.sh`,
  `reopen.sh`) name every field they use.
- **The marker is an addition to what `answer.sh` writes.** `decisions.sh` prints the first line only, so the
  queue's output is unchanged; a reader that greps the comment for `Decision:` still matches.

## Alternatives the evidence eliminated

- **Render every comment of the item.** Eliminated by the item's own history: after the 08:48 re-escalation the
  last comment is a *question again*, so "the comments" would hand the worker the fork it must not re-ask, at
  full length, with the answer buried two comments earlier. The block names the one comment that is an answer
  and the one that stood as its question.
- **Carry the whole answer thread as a transcript.** Eliminated by the same measurement: an item can be
  escalated repeatedly (`#110` was escalated three times with one conclusion), so a transcript is unbounded and
  the reader still has to pick the live decision. The last answer *is* the live policy.
- **Record the question in `answer.sh` only, and leave the package alone.** Eliminated by the event stream:
  the child never read the item (`0` `gh issue view`), so a better answer comment is still invisible. Both
  halves are needed — the marker makes the pairing exact, the block makes it arrive.
- **Let the worker's judgement handle numbering** (a prompt rule alone, no package change). Eliminated by the
  timeline: the second escalation's options were numbered from 1 and the recorded `2` therefore meant something
  else. A rule that cannot see the earlier list cannot enforce it; the package is the only place the earlier
  list can travel.
- **Refuse the package when the decision block is oversized.** Eliminated as a worse failure: the body refuses
  because it is the claim, while a long answer comment would block an item that is otherwise perfectly workable.
  The clip names itself in the rendered text, so nothing is silently shortened.

## Verification

- `bash tools/issue-tooling-selftest.sh --case …` for the three new cases: red on HEAD (2026-10-21T17:13:32Z),
  green after the fix (`evidence/red-*.log`, and the full run in `evidence/green-issue-tooling.log`, 113 cases,
  2026-10-21T17:21:16Z).
- `bash tools/loop-selftest.sh`: the two new loop cases red on HEAD (`evidence/red-loop-case.log`), the full
  suite green afterwards — 143 cases (`evidence/green-loop.log`).
- `bash tools/loop-mutations.sh --only 36.1`: the flag removed, `run#workPackageCarriesTheRecordedDecision` red,
  restore, green (`evidence/mutation-36.1.log`).
- No Gradle gate: no compiled file is touched (the issue core is compiled and run by its own self-test).

## Rollback

Revert the block and the flag. Mutation `36.1` is the flag's half; the four red cases are the other half — each
fails again with its message, so the omission cannot return silently.
