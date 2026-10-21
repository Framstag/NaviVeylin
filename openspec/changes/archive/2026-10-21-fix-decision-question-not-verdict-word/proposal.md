# fix-decision-question-not-verdict-word

## Why

Issue #243, found while answering #196 with `tools/issues/answer.sh` on 2026-10-21.

**The claim holds, at one expression.** The queue and the answer path both take "the question" to be the first
non-empty line of the item's last comment (`tools/issues/decisions.sh`, the `question:` field; `tools/issues/
answer.sh`, the `<!-- answers: … -->` marker). The run's own escalation comment does not begin with its question:

```
needs-decision

**The decision:** Should the new source-size row bound lines-per-file at a fixed first-cut number, ratchet at
today's maximum, or move straight to per-class boundaries — options in order: (1) … (2) … (3) …
```

**Measured on the item, not inferred.** The answer recorded on #196 on 2026-10-21 is followed by

```
<!-- answers: needs-decision -->
```

so what a person "answered" is the run's verdict word. `tools/issues/decisions.sh` prints the same token as the
question for every item whose escalation comment is shaped that way, which is every escalation the loop writes
(`run.sh` writes the verdict, a blank line, then `**The decision:** …`).

**Root cause, one sentence:** the question is read as "the first non-empty line" of the comment, and the run
writes its verdict word above its question, so the queue, the answer's binding and the work package's
`question:` line all name the verdict instead of the sentence.

**Consequence, and why it is not cosmetic.** `2026-10-21-fix-loop-dropped-recorded-decision` made the recorded
answer travel into the worker's package. The block it renders is `question:` beside `decision:`; with the verdict
word in the first and a bare `Decision: 2` in the second, the pair the change exists to make readable is
unreadable again — the exact defect that change closed, reached through the other half of the pair.

**Red tests on HEAD**, written before the predicate (logs under `evidence/`, `HEAD 8a45db6`):

```
  the question is the sentence the escalation asks: '**The decision:** ship the fixed cut' not found in: needs-decision
FAIL decisions#theQuestionIsNotTheVerdictWord
  quoting the escalation's question sentence: 'The\ decision' not found in: gh issue comment 76 --body
  $'Decision: ratchet above the current worst file\n<!-- answers: needs-decision -->'
FAIL answer#theAnswerQuotesTheQuestionNotTheVerdictWord
FAIL WorkPackage#questionIsNotTheVerdictWord
```

(`evidence/red-decisions-case.log`, `evidence/red-answer-case.log`, `evidence/red-workpackage-case.log`.) The
answer case's red line is the defect quoted from the tracker: the marker binds to `needs-decision`.

**Owning capability:** `decision-queue`, requirement "The queue names what awaits a decision and what each item
asks". **Owning guideline section:** `guidelines/Backlog.md` §5 "Who may write what" — the rule that keeps the
decision queue answerable, which now states what "the question" is (`AGENTS.md`'s Documentation Map routes
"the backlog itself, or the automation over it" there).

## What changes

- `tools/issues/lib.sh` — one jq program, `ISSUES_QUESTION_JQ`: the first line starting with `**The decision:**`,
  else the first non-empty line; and `issues_question_of` as its wrapper. The two shell readers call it.
- `tools/issues/decisions.sh` — the `question:` field of the queue reads that program instead of
  `map(select(length > 0)) | .[0]`.
- `tools/issues/answer.sh` — the `<!-- answers: … -->` marker reads the same program, so the answer binds to the
  sentence.
- `tools/issues/core/WorkPackage.java` — the fallback that pairs an older answer (one without a marker) with its
  question restates the rule in `questionLine`. It is a restatement because the core is standalone Java; the two
  are held together by asserting the same fixture in `decisions#theQuestionIsNotTheVerdictWord` and
  `WorkPackage#questionIsNotTheVerdictWord`.
- `tools/decision-question-mutations.json` — the revert-check set, one mutation per implementation, listed by the
  issue-tooling CI step.
- `guidelines/Backlog.md` §5 — one clause naming the rule.

## Impact

Additive and local: three readers change which line they pick from a comment they already read. No new fields, no
new `gh` call, no change to what an escalation writes. Rollback is the two mutations in
`tools/decision-question-mutations.json`, each of which turns its named case red.

Files: `tools/issues/lib.sh`, `tools/issues/decisions.sh`, `tools/issues/answer.sh`,
`tools/issues/core/WorkPackage.java`, `tools/issues/core/Cases.java`, `tools/issue-tooling-cases.sh`,
`tools/issues/fixtures/scenarios/verdict-question/issues.json`, `tools/decision-question-mutations.json`,
`.github/workflows/build.yml`, `guidelines/Backlog.md`.

**Gate: none owed.** No compiled file changes — the Java core is compiled by `tools/issue-tooling-selftest.sh`
itself — and no `tools/loop/**` file changes either, so the loop suite is not owed; the issue tooling's 116 cases
are the gate and they are green (`evidence/green-issue-tooling.log`, 2026-10-21T18:00:32Z).

## Alternatives eliminated

Recorded in `design.md`, "Alternatives the evidence eliminated": making the run write its comment
question-first, keeping one predicate in jq and calling it from Java, and matching the sentence by its
`**The decision:**` prefix anywhere in the comment.
