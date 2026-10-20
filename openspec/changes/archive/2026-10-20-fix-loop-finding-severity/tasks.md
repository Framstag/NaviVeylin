# Tasks — fix-loop-finding-severity

Parent spec: `agent-issue-loop`, the ADDED requirement *A review finding is blocking or it is not, and the round
counts* (`specs/agent-issue-loop/spec.md`). Every task names the requirement or scenario it serves.

## 1. The red case (before the fix)

- [x] 1.1 Write `run#aZeroBlockingRefusalClosesWithItsNotes` in `tools/loop-cases.sh`, registered in
      `LOOP_SHELL_CASES` and in `loop_run_shell_case`: the stub reviewer answers `VERDICT: FAIL` with
      `LOOP-BLOCKING: 0`, two `NOTE:` lines and `LOOP-CORRECTIONS-ONLY: yes`; the case asserts the item closes,
      one round was taken, no `reviewer-prompt-2.md` exists, and both notes are in the state note and in the
      recorded closure comment. Serves scenario *A finding that blocks nothing does not refuse the round*.
      **Verify**: against the engine at HEAD (`LOOP_ENGINE` pointed at a copy with `git show
      HEAD:tools/loop/run.sh`) the case fails — `evidence/red-zero-blocking.log`, exit 1,
      `a refusal with no blocking finding does not block the item: expected 'done', got 'blocked'`.
- [x] 1.2 Write `run#aBlockingRefusalStillTakesTheRoundTwoPath`, registered the same way: `LOOP-BLOCKING: 2`
      takes the round-2 path, the second refusal blocks, four child invocations, nothing closed. Serves scenario
      *A blocking finding refuses the change as it always has*. **Verify**: it is a green guard rather than a red
      case, because the path it asserts is deliberately unchanged — it turns red only if 2.3's new branch
      swallows a refusal or 2.4's closure starts on one (`evidence/case-green.log`).

## 2. The fix

- [x] 2.1 `tools/loop/prompts/reviewer.md`: the `BLOCKING:` / `NOTE:` prefixes with their definitions, the
      `LOOP-BLOCKING: <n>` line above `VERDICT: PASS|FAIL` with `PASS` allowed only at 0, and one clause stating
      that `LOOP-CORRECTIONS-ONLY` describes a refusal a zero-count report is not. Serves the requirement's
      grade-and-count clause. **Verify**: `prompt#reviewerCarriesTheEightItems` still passes and the rendered
      prompt carries the new lines (`evidence/case-green.log`).
- [x] 2.2 `tools/loop/run.sh`: `blocking_count` (the count, empty when the line is absent or malformed) and
      `review_notes` (the `NOTE:` findings as bullets, or the report's path when it graded none) beside
      `report_field`. Serves the requirement's "the run refuses on the count" clause.
      **Verify** (structural): `bash -n tools/loop/run.sh` clean; `run#metricsRecordedPerItem` still passes.
- [x] 2.3 `tools/loop/run.sh`, the round loop: `VERDICT: PASS` breaks only when the count is absent or 0; a
      count of 0 breaks as `NOTES` and keeps the round's findings; everything else falls through to the round
      cap, the `LOOP-CORRECTIONS-ONLY` extension and the round-2 worker exactly as before. Serves scenarios
      *A finding that blocks nothing does not refuse the round*, *A report that states no count is read as a
      refusal*. **Verify**: `run#aZeroBlockingRefusalClosesWithItsNotes` green, `run#reviewRoundsCapAtTwo` green,
      `run#aCorrectiveRefusalEarnsOneMoreRound` green (`evidence/case-green.log`).
- [x] 2.4 `tools/loop/run.sh`, the closure branch: the `NOTE:` findings reach the item — appended to the reason
      the closure comment carries and recorded as the state note — and the state note is empty otherwise, so a
      plain pass records nothing new. Serves scenario *An unchanged pass still passes* and the notes clause.
      **Verify**: the closure case asserts the note text in `.items[0].note` and in the recorded `gh issue
      comment 21` invocation (`evidence/case-green.log`); `run#metricsRecordedPerItem` asserts a pass still
      closes with one round.
- [x] 2.5 `.pi/skills/fix-loop/SKILL.md`: "How a finding is graded" (the four line-shapes, what the run does with
      each, and that the count wins over `LOOP-CORRECTIONS-ONLY` because that line describes a refusal), the
      `--max-review-rounds` row and the failure-handling row for a zero-blocking refusal. Serves the delta's
      grading vocabulary, so the document and the code agree. **Verify** (structural): `git diff
      .pi/skills/fix-loop/SKILL.md` names the two tables and the new subsection; the wording names the same
      four cases the spec does.

## 3. Verification

- [x] 3.1 Run the cases this change can move, and only those — the orchestrator runs the whole suite once on the
      merged tree (`guidelines/Build.md` §2, "iterate with focused suites and gate once"). **Verify**:
      `bash tools/loop-selftest.sh --case '<each new case>'`, plus `--case 'run#engineNamesNoProjectFact'`,
      `--case 'run#reviewRoundsCapAtTwo'`, `--case 'run#metricsRecordedPerItem'`, `--case
      'run#aCorrectiveRefusalEarnsOneMoreRound'` and `--case 'prompt#reviewerCarriesTheEightItems'` — all `PASS`
      in `evidence/case-green.log`, offline, no token, no device.
- [x] 3.2 Confirm the change needs no build: no Kotlin, Java, C++, Gradle or resource file is touched.
      **Verify** (structural): `git show --stat` of the commits lists `tools/loop/run.sh`,
      `tools/loop/prompts/reviewer.md`, `.pi/skills/fix-loop/SKILL.md`, `tools/loop-cases.sh` and the archived
      change only.
- [x] 3.3 Portability: the new code and prompt text name no project value. **Verify**: `bash
      tools/check-portability.sh` exits 0 and `run#engineNamesNoProjectFact` passes
      (`evidence/portability-validate-doctor.log`, `evidence/case-green.log`).
- [x] 3.4 `openspec validate fix-loop-finding-severity` and `openspec doctor` clean. **Verify**:
      `evidence/portability-validate-doctor.log`.

## 4. Guideline and document update

- [x] 4.1 Guideline update: none owed. `guidelines/Build.md` §2 owns the iteration protocol the loop runs and
      does not grade review findings; `guidelines/Backlog.md` §5 owns who may write what, not what a refusal
      means; `AGENTS.md` "Backlog maintenance" names the loop's skills and gate only. **Verify** (structural):
      `git status --short guidelines/ AGENTS.md` is empty.
- [x] 4.2 `.pi/skills/fix-loop/SKILL.md` updated in 2.5; no other document repeats the review contract (one home
      per rule — `REFERENCE.md`, "the phase contract lives in the prompt").
