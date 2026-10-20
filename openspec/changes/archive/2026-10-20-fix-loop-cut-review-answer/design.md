# Design — fix-loop-cut-review-answer

## D1 — The discriminating fact is the answer's shape

The root cause is that the round is decided from two greps and one count, so every report the run cannot grade
lands on the same reading: a refusal. Three facts could separate an incomplete answer from a quiet or a refusing
one, and the measurement leaves only one.

**Alternative A — recognise the cut by how the text ends semantically** (a sentence that stops mid-word, an
unclosed code fence). **Refused:** it is a judgement about prose, it differs per language, and it is exactly the
class of "another program's prose" rule that `run#engineNamesNoProjectFact` and `tools/check-portability.sh`
exist to keep out of the engine. The measured report ended `… and the post-restore gre` — a real answer cut one
letter short reads exactly like a model that chose a strange word.

**Alternative B — have the adapter tell the run the answer was truncated.** The adapter is the only process that
knows what it wrote. **Impossible, and already checked:** the adapter holds the last completed assistant message
and prints it; the reported transcript carries no truncation marker at all (`stopReason` is `stop` 12 times,
`toolUse` otherwise), so the adapter has nothing to report — the loss happened on the way to the file, which is
the one place neither the child nor the adapter can observe. A project whose configured command is plain text has
no adapter at all (`run#plainTextChildStillReports`), so a fact only the adapter can report would be missing
exactly where the engine claims to need no second code path.

**Alternative C — treat any report without a count as an incomplete answer.** That is the current refusal reading
inverted. **Refused:** it would make an older-shaped review pass on a retry it never earned, and the recorded
decision of `run#reviewRoundsCapAtTwo` — no count means refusal, so no review passes by saying nothing — would be
reversed for a case that does not need it.

**Chosen: two shape facts the run can read itself, without a model or a project fact.**

1. the adapter prints exactly one newline after every answer it hands the run (`.pi/…/reviewer-report-*.md` ends
   with `\n` after a complete answer, and the run's own `LOOP-TIMEOUT` / `LOOP-OUTSIDE-WRITE` lines are appended
   with one), so a file whose last byte is not a newline was never handed over whole;
2. the prompt asks for the closing line last — `LOOP-BLOCKING: <n>` then `VERDICT: PASS|FAIL` — so a complete
   answer always carries a `VERDICT:` line, whatever it refuses.

`answer_complete <file> <closing-line regex>` is those two facts. A report that has both is decided exactly as
before; a report that lacks either arrived cut.

| report | has `VERDICT:` | ends `\n` | reading |
|---|---|---|---|
| the incident's round 2 | no | no | cut → retried |
| `VERDICT: FAIL`, no count (`run#reviewRoundsCapAtTwo`) | yes | yes | refusal, as before |
| `VERDICT: PASS` (`run#metricsRecordedPerItem`) | yes | yes | pass, as before |
| a reviewer the run ended (`LOOP-TIMEOUT`) | no | yes | **not retried** — the run's own end is already named |
| an empty answer (no text at all) | no | no | cut → retried |

## D2 — What the run does with it: one retry of the same round

The fault is the transport's, so the record is the run's — but the run cannot finish the item either, so it does
the cheapest thing that answers the question it actually has: it asks the same round again, once, with the same
prompt contract and the same diff, and decides on the answer that arrived. The retry is not a second review
round: `metrics.reviewRounds` stays at the round's number, the round budget is untouched, and no worker round is
spent. The cut attempt is kept (`reviewer-report-<n>.md`) beside the retry (`reviewer-report-<n>-retry.md`)
rather than overwritten, because it is the only evidence that the transport lost something — the incident's
report was 11 428 bytes of text and a person had to read it to learn nothing was wrong with the work.

A **second** cut answer is not a hiccup the run can wait out, and it is not the item's fault either: the round
produced no verdict, so the item is recorded `blocked` with the reason *the reviewer's report was cut short
twice*, no worker round is taken, and no `review-failure` marker is written into the change on the branch. The
block's escalation then asks the question the run can actually pose (`run#blockedItemAsksItsOwnQuestion` —
retry the item, or abandon it), instead of naming a refusal no reviewer made.

**Why exactly one retry, and not a loop.** A retry is bounded by the child wall-clock, and the run's budget is
the scarcest thing it owns; an unbounded retry on a broken transport spends the run inside one item, which is the
failure `LOOP_CHILD_MAX_SECONDS` exists to prevent (`#203`). One retry buys the case the incident measured — a
single answer lost on the wire — and stops.

**Why a timeout is not retried.** A reviewer the run ended already says so in its own report (`LOOP-TIMEOUT`,
appended by `run_child`), so the absence of a verdict there is explained by something the run did, not by
something the transport lost. Retrying it would spend a second full wall-clock on a child that has just proved it
can hang. The same guard sits on the retry: a retried attempt the run itself ended is read exactly as the first
one was, so the reason a blocked item carries never blames the transport for the run's own cap.

## D3 — Why the earlier decision is reversed for this case only

`2026-10-20-fix-loop-child-that-never-started`, design D3, decided the reviewer path stays as it was: *a reviewer
that dies without a `VERDICT:` line still ends its round as a failed review, because the item **was** read — its
work is in the tree, its change is archived — so a block on it names a true state*. That reasoning holds for a
reviewer that refused, and for one that died after reading: those rounds produced a judgement about the change.
It does not hold for an answer the run never received, which is the case the same design left open (*"If a
reviewer that never started is ever measured, it is its own item with its own red case"*). This is that item, with
that red case.

What is *not* reversed: a reviewer that ends with a `VERDICT:` line — a refusal, a zero-count pass, a report with
a verdict and no count — is read exactly as before. The worker path is untouched: a worker that writes no
`LOOP-VERDICT` is classified by the item's own evidence (`#212`) and that decision stands.

## D4 — The scope stops at the reviewer's answer

The same shape check would apply to the worker's report, and it is deliberately not applied. A worker without a
verdict already has a discriminator the run reads itself (its exit status, the tree, `#212`), its report is not
what decides a second phase, and a retried worker would re-do work rather than re-ask a question — the retry is
cheap here precisely because a review round is read-only. Widening it would double the change's surface for a
case nobody measured.

## Blast radius

- `tools/loop/run.sh` — two new helpers (`answer_complete`, `render_reviewer_prompt`), one `reviewer_report`
  variable per round, one retry block inside the round loop, and the round's later reads (`blocking_count`, the
  `VERDICT: PASS` grep, `review_notes`, `LOOP-CORRECTIONS-ONLY`, `findings`, and the blocked arm's
  `LOOP-DECISION` read) take `$reviewer_report` instead of the first attempt's path. No other statement changes.
- `tools/loop-cases.sh` — two cases, in both registries.
- `tools/loop-mutations.sh` — one mutation (`31.1`).
- `guidelines/Backlog.md` §7 and the capability spec — the rule and its measurement.

The engine names no project value and no agent fact: `run#engineNamesNoProjectFact` and
`tools/check-portability.sh` are unchanged and still report the same count.

## Rollback

`git revert` of the one commit. The retry block is self-contained; removing it restores the previous round
decision byte for byte, `answer_complete` then has no caller, and every report the paths read is the first
attempt's again. Nothing recorded in `run.json`, no label and no branch depends on the retry having run: a retried
round records the same fields a first attempt would.
