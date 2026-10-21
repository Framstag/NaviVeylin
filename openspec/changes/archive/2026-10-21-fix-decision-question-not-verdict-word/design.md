# Design — fix-decision-question-not-verdict-word

## Root cause

The question is read as `(.body | split("\n") | map(select(length > 0)) | .[0])` — the first non-empty line —
in `tools/issues/decisions.sh` and, since `fix-loop-dropped-recorded-decision`, in `tools/issues/answer.sh`.
The run writes its escalation as

```
<verdict>

**The decision:** <the sentence>
<options and their costs>
```

so for every escalation the loop writes, that expression yields the verdict word. Measured on #196's answer:
`<!-- answers: needs-decision -->`.

## The fix

One jq program, one place:

```jq
split("\n")
  | map(select(test("\\S")))
  | (map(select(startswith("**The decision:**"))) | .[0]) // .[0] // ""
```

`tools/issues/lib.sh` owns it as `ISSUES_QUESTION_JQ`, with `issues_question_of <comment-json>` as the wrapper.
`decisions.sh` splices the program into its existing single `jq` pass; `answer.sh` calls the wrapper on the item's
last comment. `WorkPackage.java` restates it in `questionLine`, because the core is deliberately standalone Java
(compiled by its own self-test, no shell dependency) — and both restatements are pinned by cases asserting the
same fixture, so a drift between them is red rather than silent.

The fallback stays: a comment that carries no `**The decision:**` line is still read by its first non-empty line,
which is what the whole corpus of earlier escalations and the plain comments in the fixtures need. The rule is
therefore additive to the predicate, not a replacement of it.

## Blast radius

`tools/issues/lib.sh` (program + wrapper), `decisions.sh` (one expression), `answer.sh` (one expression),
`WorkPackage.java` (`questionLine`, used by the pairing fallback), `Cases.java` (one case),
`tools/issue-tooling-cases.sh` (two cases), one new fixture scenario, `tools/decision-question-mutations.json`
(the revert-check set), `.github/workflows/build.yml` (the set listed by the issue-tooling step),
`guidelines/Backlog.md` §5 (one clause). No `tools/loop/**` change: the run writes the escalation, it does not
read one.

## Risk

- **A comment whose first line is a legitimate sentence but which also contains `**The decision:**` later.** The
  marker wins, by construction: the run only writes that marker for the sentence it asks. A person quoting the
  marker in a note is read as a question — acceptable, because the escalation's own marker is the only one whose
  position the tooling controls, and the fallback covers everything else.
- **`decisions.sh` splicing a multi-line program into its jq argument.** Verified by the case rather than by
  reading: the program is a single jq expression across lines, and the queue's `--json`
  (`decisions#theQuestionIsNotTheVerdictWord`) plus its human form
  (`decisions#listsTheQueueAndItsQuestions`) both pass.
- **The Java restatement drifting from the shell program.** That is what the two cases are for: both assert the
  same fixture (`needs-decision` / `**The decision:** ship the fixed cut …`) and both are named by mutation A1.1
  and A1.2 respectively.

## Alternatives the evidence eliminated

- **Make the run write the question first** (`**The decision:** …` on line one, the verdict below). Eliminated by
  the corpus, not by taste: the escalation's first line is also what `decisions.sh` printed before this change,
  and every item escalated so far carries the verdict-first shape — the reader must work for those items whatever
  the run does next. Changing the writer would fix new comments and leave the queue unreadable for old ones; this
  change fixes both and needs no writer change. (It remains available later as a simplification, once no
  verdict-first comment is open.)
- **Keep one predicate in jq and have the Java core call the shell.** Eliminated by the core's design: it runs as
  a single-file Java program (`javac --release 17`, then `java -cp …`), with no shell dependency, and the issue
  tooling's self-test exercises it that way. A restatement with a case on each side is cheaper than a new
  boundary and is the same choice the two existing `escape` implementations already make.
- **Match `**The decision:**` anywhere in the comment** rather than only at the start of a line. Eliminated by
  the writing side: the run writes it as its own line, and an anywhere-match would also pick a quotation of the
  marker inside a person's prose, where the start-of-line rule does not.
- **Read the item's last *escalation* by its verdict line and take the line after the blank.** Eliminated
  because it depends on the escalation's exact shape (verdict, blank, sentence) that a person's comment and a
  reviewer's note do not share, while the marker is written by the run itself and is checked by the same case.
- **Fix it in the answer path only** (leave the queue printing the verdict). Eliminated by the requirement that
  is now red in both: they are the same defect read by two tools, and the queue is how a person finds the item to
  answer at all.

## Verification

- `bash tools/issue-tooling-selftest.sh --case …` for the three new cases: red on HEAD
  (`evidence/red-*.log`), green after the predicate.
- `bash tools/issue-tooling-selftest.sh`: all 116 cases pass
  (`evidence/green-issue-tooling.log`, 2026-10-21T18:00:32Z).
- `bash tools/mutate.sh --mutations tools/decision-question-mutations.json`: `A1.1` and `A1.2` each red under
  their mutation and green after the restore (`evidence/mutations-A1.log`).
- No Gradle gate and no loop suite: no compiled file and no `tools/loop/**` file changes.

## Rollback

Revert the two readers and the Java restatement. Mutation `A1.1` is the shell half and `A1.2` the Java half, so
the rollback is versioned and each half is red on its own case.
