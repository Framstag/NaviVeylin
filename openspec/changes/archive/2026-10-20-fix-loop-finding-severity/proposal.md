# Proposal — fix-loop-finding-severity

## Why

### The defect

`tools/loop/run.sh` decides a review round from a word. It reads `LOOP-VERDICT` (`PASS` / `FAIL` /
`CORRECTIONS-ONLY` …) and `LOOP-CORRECTIONS-ONLY`, and every `FAIL` is the same refusal: it earns another round
inside the budget, and a `FAIL` at the cap blocks the item and escalates it. A reviewer that flags a misnamed
constant and one that flags a scenario no case exercises therefore cost the item exactly the same thing.

Item #126 paid for it: 83 minutes, three review rounds (`reviewer-report-1.md` 16.2 KB, `-2` 13.4 KB, `-3`
15.6 KB — the findings were rewritten, not closed), then `agent/needs-decision` + `status/on-hold`, while its
own escalation states that *every finding is a narrowing or a corrected pointer* and recommends the merge, with
code, gate (5 680 tests, 0 failures) and the revert-check all green. No change reached `main`, and the loop had
no vocabulary to say so.

### Root cause, and the evidence that settles it

The reviewer's contract (`tools/loop/prompts/reviewer.md`) grades nothing: it lists eight checklist items and
asks for `VERDICT: PASS|FAIL`, and the run's reading of that report has exactly one severity —
`grep -q '^VERDICT: PASS'`, then "blocked". The one other machine-readable line, `LOOP-CORRECTIONS-ONLY`,
describes the *shape* of a refusal (subtractive findings) and not its *weight*: a deleted claim the spec
depends on is subtractive and blocking at the same time, and #126's three rounds were all of that shape. So
there is no value the reviewer can report, and no value the run can read, that separates "this text is wrong"
from "this change is wrong".

The same defect is visible without a model: on the engine at `dabf314`, a reviewer report that ends
`VERDICT: FAIL` with every finding graded as a note blocks the item. That is the failing case of this change —
`run#aZeroBlockingRefusalClosesWithItsNotes` (`tools/loop-cases.sh`), red against the HEAD engine
(`evidence/red-zero-blocking.log`, exit 1: `a refusal with no blocking finding does not block the item:
expected 'done', got 'blocked'`) and green with the fix (`evidence/case-green.log`, exit 0). Green there also
means: one review round was taken, no `reviewer-prompt-2.md` exists, and the `NOTE:` findings are on the item.

Files the defect lives in: `tools/loop/prompts/reviewer.md` (the ungraded checklist), `tools/loop/run.sh`
(the `LOOP-VERDICT` / `LOOP-CORRECTIONS-ONLY` branches), `.pi/skills/fix-loop/SKILL.md` (the review contract and
the failure-handling table, both silent on severity).

### Owning requirement, and the gap

`agent-issue-loop` — "Each item is worked by a fresh process, reviewed by a different one". Its scenario *Review
rounds are bounded, and a corrective refusal earns one more* says what a failure costs and nothing says what a
*failure* is, so a wording correction and a broken scenario entered the same branch. This change ADDs one
requirement, "A review finding is blocking or it is not, and the round counts"; no existing requirement's text
moves.

One fix follows from this root cause and no second fix is defensible: the reviewer already writes the findings,
so it grades them, and the run reads one number instead of a word. The alternatives the evidence eliminated are
named in `design.md`.

## What Changes

- `tools/loop/prompts/reviewer.md`: every finding is prefixed `BLOCKING:` or `NOTE:` (blocking = the contract is
  contradicted, a scenario is not exercised by the case that names it, the gate or red-case evidence is missing
  or false; note = wording, a pointer, a naming, an ordering, anything the change does not depend on), and the
  report ends with `LOOP-BLOCKING: <n>` immediately above `VERDICT: PASS|FAIL`, `PASS` allowed only at 0.
- `tools/loop/run.sh`: two helpers — `blocking_count` (the count, empty when the line is absent or malformed) and
  `review_notes` (the `NOTE:` findings as bullets, or the report's path) — and one branch: a refusal whose count
  is 0 is a pass whose notes are recorded, the item closes, and the notes reach the item's closure comment and
  its state note. A refusal with a count of 1 or more behaves exactly as today. A report with no count line
  keeps today's conservative reading.
- `.pi/skills/fix-loop/SKILL.md`: the review contract gains "How a finding is graded" (the four line-shapes and
  what the run does with each, and which line wins when `LOOP-CORRECTIONS-ONLY` and the count disagree), and the
  failure-handling table gains the zero-blocking row.
- `tools/loop-cases.sh`: two cases, `run#aZeroBlockingRefusalClosesWithItsNotes` and
  `run#aBlockingRefusalStillTakesTheRoundTwoPath`, registered in `LOOP_SHELL_CASES` and in
  `loop_run_shell_case`.

Additive: no flag, exit status, state field, verdict, label or worktree path changes, and no existing report line
moves — `LOOP-BLOCKING` is a new line in the reviewer's report and a new read in the run. Rollback: revert
`tools/loop/run.sh`, `tools/loop/prompts/reviewer.md`, `.pi/skills/fix-loop/SKILL.md` and `tools/loop-cases.sh`;
a `FAIL` is then a refusal again, whatever it counts, with nothing else changed.

## Capabilities

### New Capabilities

None.

### Added Requirements

- `agent-issue-loop`: one new requirement, "A review finding is blocking or it is not, and the round counts",
  with four scenarios — a refusal that counts nothing closes and records its notes, a refusal that counts
  something refuses as before, a report with no count line is read as a refusal, and an unchanged `VERDICT:
  PASS` still passes. Each names the one case that exercises it. No existing requirement is modified, deleted or
  reordered, and *Review rounds are bounded, and a corrective refusal earns one more* keeps its text: this
  requirement says what a failed round is, that scenario says what one costs.

## Impact

- `tools/loop/run.sh` — `blocking_count` and `review_notes` beside `report_field`, one branch in the round loop,
  and the closure branch's `--reason` / `--note`. The budget, the round cap, the `LOOP-CORRECTIONS-ONLY`
  extension, the reviewer-wrote check, the branch, the labels and the escalation are untouched.
- `tools/loop/prompts/reviewer.md` — the finding prefixes, the count line, and one clause stating that
  `LOOP-CORRECTIONS-ONLY` describes a refusal this report is not.
- `.pi/skills/fix-loop/SKILL.md` — the new subsection and the two table rows.
- `tools/loop-cases.sh` — the two cases, in both the list and the dispatcher.
- `openspec/specs/agent-issue-loop/spec.md` — gains the ADDED requirement at archive time; this change's delta is
  the only place it is written now.
- No app module, manifest, resource, stylesheet or native file is touched; no phone or car variant is affected, so
  the parity requirement of `guidelines/UI.md` §1 does not apply.
- Guidelines affected: none. `guidelines/Build.md` §2 owns the iteration protocol the loop runs and does not
  grade review findings; `guidelines/Backlog.md` §5 names who may write what, not what a refusal means.
  `AGENTS.md` "Backlog maintenance" names the loop's skills and its gate only. The loop's operating document is
  `.pi/skills/fix-loop/SKILL.md`, which is updated here.
- Portability: `tools/check-portability.sh` and the case `run#engineNamesNoProjectFact` must stay clean — the new
  code and prompt text name no project value (the line names `LOOP-BLOCKING`, `BLOCKING:` and `NOTE:`, which are
  the loop's own vocabulary).

## Scope

Tooling only — the backlog loop that closes defect-shaped items. Not a feature of the app, of the Android Auto
integration or of the phone UI.
