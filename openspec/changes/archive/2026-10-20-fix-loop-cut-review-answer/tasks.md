# Tasks — fix-loop-cut-review-answer

## 1. The red cases first

- [x] 1.1 `tools/loop-cases.sh`: write `run#aCutReportIsRetriedInsteadOfRefused`, registered in the `--list`
      array and the dispatch map (spec: "A review finding is blocking or it is not, and the round counts" —
      scenario "A report that arrived cut is retried, not read as a refusal"). The stub reviewer's round-1 and
      round-2 answers are cut exactly as the incident's was — text that stops mid-word, no `VERDICT:`, no final
      newline — and the retry after round 1 arrives whole. Verify it is red on the base commit `bf555c3`:
      `bash tools/loop-selftest.sh --case 'run#aCutReportIsRetriedInsteadOfRefused'` →
      `an answer cut in transport does not block the item: expected 'done', got 'blocked'`,
      `evidence/red/red-on-head.txt` (tests=1 failures=1, 2026-10-20T17:11:35Z→17:11:46Z).
- [x] 1.2 `tools/loop-cases.sh`: write `run#aReportCutTwiceBlocksAsTheTransportsFault`, same two registries
      (spec: scenario "Two cut answers in one round block it as the transport's fault"), asserting the block's
      reason, that no worker round-2 prompt exists, and that no `review-failed-round-*.md` reached the change.
      Verify it is red on the base engine too —
      `LOOP_ENGINE=<tools/loop at bf555c3 with run.sh from HEAD> bash tools/loop-selftest.sh --case
      'run#aReportCutTwiceBlocksAsTheTransportsFault'` → `'cut short twice' not found in: the change failed its
      last review round (2 of 2) — a decision is needed, not another attempt`, `evidence/red/`.

## 2. The fix

- [x] 2.1 `tools/loop/run.sh`: `answer_complete <file> <closing-line regex>` — non-empty, last byte is a newline,
      carries the closing line (spec: the new scenario's WHEN). `render_reviewer_prompt <out>` so both attempts
      render the same contract and diff. Verify: `bash -n tools/loop/run.sh`.
- [x] 2.2 `tools/loop/run.sh`: the retry block inside the round loop — an incomplete reviewer report (and no
      `LOOP-TIMEOUT`) prints `run: the round <n> answer was cut short (<last line>) — retrying the round once`,
      renders `reviewer-prompt-<n>-retry.md`, runs it, and the round's later reads take `$reviewer_report`
      (spec: the new scenario's THEN). Verify with `--case 'run#aCutReportIsRetriedInsteadOfRefused'` green and
      the round's other readings unchanged: `--case 'run#reviewRoundsCapAtTwo'`,
      `--case 'run#aBlockingRefusalStillTakesTheRoundTwoPath'`, `--case 'run#aZeroBlockingRefusalClosesWithItsNotes'`,
      `--case 'run#aCorrectiveRefusalEarnsOneMoreRound'`, `--case 'run#reviewBudgetIsARecordedValue'`,
      `--case 'run#metricsRecordedPerItem'`, `--case 'run#plainTextChildStillReports'` —
      `evidence/green/affected-cases.txt`.
- [x] 2.3 `tools/loop/run.sh`: the second incomplete answer blocks with *the reviewer's report was cut short
      twice*, records no failed review on the branch and takes no worker round (spec: the second new scenario).
      Verify with `--case 'run#aReportCutTwiceBlocksAsTheTransportsFault'` green, and that a round the run ended
      is still read as before: `--case 'run#childOutlastingTheWallClockIsBlocked'`.

## 3. Revert-check the new invariant

- [x] 3.1 `tools/loop-mutations.sh`: mutation `31.1` turns the retry's condition into `if false &&`, so the cut
      answer is read as a refusal again and `run#aCutReportIsRetriedInsteadOfRefused` must go red; restore and
      re-run it green — `evidence/green/mutation-31.1.txt` (`red-when-mutated: 1 · not-noticed: 0 ·
      restore-failures: 0`).

## 4. The documents that state the rule

- [x] 4.1 `guidelines/Backlog.md` §7 "The loop": what a run does with an answer that arrived cut, with the
      2026-10-20 measurement, beside "What it does with a child that never started" (spec: both new scenarios).
      Verify: `grep -n "arrived cut" guidelines/Backlog.md`.
- [x] 4.2 `openspec/changes/fix-loop-cut-review-answer/specs/agent-issue-loop/spec.md`: the requirement restated
      verbatim, the no-count scenario's `AND` clause and the two new scenarios (the contract of an incomplete
      answer lives in the scenarios: the statement stays verbatim, because a statement over 500 characters is a
      validation warning). Verify: `openspec validate agent-issue-loop --type spec` →
      `Specification 'agent-issue-loop' is valid`, with only the pre-existing long-statement warning for
      "Closure follows a passing review and carries evidence".

## 5. Structural checks — the scope block (no compiled file is touched)

- [x] 5.1 `bash -n` on `tools/loop/run.sh`, `tools/loop-cases.sh`, `tools/loop-mutations.sh`;
      `bash tools/check-portability.sh` (43 engines, 2 templates, no project value named); `openspec validate …
      --strict`; `openspec doctor` — `evidence/green/validation.txt` (2026-10-20T17:25:54Z).
- [x] 5.2 The offline shell suite (`tools/loop-selftest.sh`, the gate that owns `tools/loop/**`) run once over
      the whole set of the **final** tree — `evidence/green/selftest.txt`: **126 of 127 cases pass**
      (2026-10-20T17:41:23Z→17:50:09Z), and the one red (`worktree#keepsByDefaultAndRemovesOnRequest`) is
      pre-existing and independent of this change, proved in `evidence/green/PRE-EXISTING-RED.txt`: the same
      failure and the same message with the base engine (`tools/loop/run.sh` restored from `HEAD`,
      `answer_complete` absent), and `worktree.sh` — where the assertion lives — is not in the diff (issue #215).
      `evidence/green/affected-cases.txt` is the scoped iteration record taken before the retry's own
      `LOOP-TIMEOUT` guard was added (19 of 19 green); that guard is covered by the final full run, which
      includes all 19 and the two new cases.
- [x] 5.3 No Gradle and no device: this change touches no Kotlin, Java, C++, manifest or resource file, so
      `guidelines/Build.md` §2's scope block names these structural checks in place of `./gradlew test
      -PforceTests --no-build-cache`. Exclusion stated: the JVM gate is not run for a delta no JVM module
      compiles.
