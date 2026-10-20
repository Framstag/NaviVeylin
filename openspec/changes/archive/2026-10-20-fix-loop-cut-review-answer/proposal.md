# fix-loop-cut-review-answer

## Why

A review report that ends mid-sentence is read as a refusal, so an item whose work passed is recorded
`blocked` and escalated, and a person is asked to decide about a change nothing is wrong with (issue #224).

**The entry, verified in the tree at the base commit `bf555c3`.** Item #81 reached review round 2 on
2026-10-20 and its reviewer report ended mid-word — the last line of
`.pi/issue-loop/items/81/reviewer-report-2.md` is `… and the post-restore gre` — while the report's own text
recorded all seven delta scenarios as PASS, round 1's blocking finding as closed, the gate as a real execution
and the revert-check as clean. The run recorded the item `blocked`
(`2026-10-20-fix-jni-stub-sandbox-rule failed round 2`) and escalated it. The child's transcript carries no
truncation marker (`stopReason` is `stop` 12 times, `toolUse` otherwise), so the answer was cut on its way to the
file the run reads.

**Root cause, one sentence.** `run.sh` decides a review round from two lines it greps out of the report and has no
term for a report that arrived incomplete, so "no `LOOP-BLOCKING` line" — the shape an older review and a cut
answer share — is the only reading it has, and that reading is a refusal:

- `tools/loop/run.sh:792` (base commit) — `run_child … "$item_dir/reviewer-report-$round.md"`: the round's
  report is whatever the child's answer reached, and nothing about it is checked before it is trusted.
- `tools/loop/run.sh:805-815` — `blocking=$(blocking_count …)`; no count and no `^VERDICT: PASS` falls through
  to the refusal path, which is the same reading `run#reviewRoundsCapAtTwo` relies on for a report that states no
  count.
- `tools/loop/run.sh:849` — `blocked_reason="the change failed its last review round …"`: the item is recorded as
  a change its reviewer refused, which no reviewer did, and the `*)` arm of the outcome chain escalates it.

**Red test, on the base commit `bf555c3`** (offline shell gate, so no JUnit XML exists for it — the case count is
the tally; `evidence/red/red-on-head.txt`, 2026-10-20T17:11:35Z→17:11:46Z):

```
  an answer cut in transport does not block the item: expected 'done', got 'blocked'
FAIL run#aCutReportIsRetriedInsteadOfRefused
```

i.e. `bash tools/loop-selftest.sh --case 'run#aCutReportIsRetriedInsteadOfRefused'` → **tests=1 failures=1**. The
case drives the real `run.sh` against the stub child, whose first reviewer answer is cut exactly as the incident's
was (no closing `VERDICT:`, no final newline) and whose retry arrives whole. The second case,
`run#aReportCutTwiceBlocksAsTheTransportsFault`, is red on the base engine for the same reason the incident was
recorded — `and the reason is the transport, not a refusal: 'cut short twice' not found in: the change failed its
last review round (2 of 2) — a decision is needed, not another attempt` (`evidence/red/`).

**Owning requirement.** `agent-issue-loop`, "A review finding is blocking or it is not, and the round counts"
(`openspec/specs/agent-issue-loop/spec.md:689` at the base commit). Its scenario "A report that states no count
is read as a refusal" states what the run does with a report it cannot grade; what it lacked is the sibling case
the incident measured, where the report is not quiet but incomplete.

**Owning guideline section.** `guidelines/Backlog.md` §7 "The loop" — "What it does with a child that never
started" (the run's own fault is recorded as the run's, not the item's) and "What it does when it cannot
proceed". It stated what a run does with a child that never started and said nothing about an answer that never
arrived.

## What Changes

- `tools/loop/run.sh`: one helper, `answer_complete <file> <closing-line regex>` — a report is complete when it
  ends with the newline the child's adapter prints after every answer and carries the closing line its prompt
  asks for last. Applied to the reviewer's report inside the round loop: an incomplete answer is recorded on
  stderr with the report's last line, the **same round** is retried exactly once (`reviewer-prompt-<n>-retry.md`
  → `reviewer-report-<n>-retry.md`, the cut attempt kept beside it), and the retried answer decides the round. A
  second incomplete answer blocks the item with the reason *the reviewer's report was cut short twice*, records
  no failed review on the branch, and buys no second worker round. A round the run itself ended is not retried: a
  reviewer report carrying `LOOP-TIMEOUT` is read exactly as it was (the run's own end is already named).
- `tools/loop/run.sh`: `render_reviewer_prompt <out>`, so both attempts render the same contract and the same
  diff, and the round's decisions read `$reviewer_report` instead of the first attempt's path.
- `tools/loop-cases.sh`: `run#aCutReportIsRetriedInsteadOfRefused` and
  `run#aReportCutTwiceBlocksAsTheTransportsFault`, registered in the `--list` array and the dispatch map.
- `tools/loop-mutations.sh`: mutation `31.1`, which removes the retry and makes the first case red again
  (revert-check for the new invariant).
- `guidelines/Backlog.md` §7: what a run does with an answer that arrived cut, with the 2026-10-20 measurement.
- `openspec/specs/agent-issue-loop/spec.md`: the requirement restated with two scenarios added.

## Impact

| file | module / area |
|---|---|
| `tools/loop/run.sh` | the run's only writer: the reviewer round and two helpers |
| `tools/loop-cases.sh` | the loop's offline self-test cases (`tools/loop-selftest.sh`) |
| `tools/loop-mutations.sh` | the shell gate's mutation set (`revert-check`) |
| `guidelines/Backlog.md` | §7 "The loop" |
| `openspec/specs/agent-issue-loop/spec.md` | the loop's contract |

No compiled file is touched — no Kotlin, Java, C++, manifest, resource or Gradle file — so `guidelines/Build.md`
§2's scope block applies: the project's own gate for `tools/loop/**` is `tools/loop-selftest.sh` (offline, no
token, no model, no device), not `./gradlew test -PforceTests --no-build-cache`. Exclusion stated: the JVM gate is
not run for a delta no JVM module compiles.

**Additive to the contract, but it changes one recorded decision.** The archive entry
`2026-10-20-fix-loop-child-that-never-started`, design D3, decided *not* to touch a reviewer that ends without a
`VERDICT:` line, on the ground that the item was read and a block on it names a true state. That is right for a
reviewer that refused or died, and wrong for an answer the transport cut: the round produced no verdict at all.
`design.md` D3 reverses that decision for the incomplete case only and records why the evidence it was made from
did not cover this one. Rollback is `git revert` of the one commit; the retry path is self-contained and
`answer_complete` then has no caller.

**Spec delta.** `agent-issue-loop` — **MODIFIED** "A review finding is blocking or it is not, and the round
counts": the statement is restated verbatim, the no-count scenario gains one `AND` clause (a report with no
`VERDICT:` line is not read by that rule), and two scenarios are added ("A report that arrived cut is retried,
not read as a refusal", "Two cut answers in one round block it as the transport's fault"); every other scenario
is restated verbatim. The contract of the new scenarios therefore lives where OpenSpec keeps it — in the
scenarios — rather than as a longer statement: a statement over 500 characters is a validation warning, and a
longer statement buys a warning, not a stronger rule.
