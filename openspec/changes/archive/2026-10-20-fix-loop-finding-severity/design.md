# Design — fix-loop-finding-severity

## Root cause, and what it leaves to be decided

The loop has one severity for a review finding. `run.sh` asks one question of a round —

```sh
# tools/loop/run.sh, before this change
if grep -q '^VERDICT: PASS' "$item_dir/reviewer-report-$round.md" 2>/dev/null; then
  review_verdict="PASS"; break
fi
```

— and every other answer is a refusal: another round inside the budget, then `blocked` and escalated. The
reviewer's contract asks the same single question (`VERDICT: PASS|FAIL`) over eight ungraded checklist items,
so nothing in either half of the exchange carries a *weight*. `LOOP-CORRECTIONS-ONLY` is the closest thing and
it grades the *shape* of a refusal, not its weight: a deleted claim the spec depends on is subtractive and
blocking at once.

Item #126 is the measurement: 83 minutes, three rounds of 16.2 / 13.4 / 15.6 KB (the findings were rewritten, not
closed), a green gate (5 680 tests, 0 failures), a clean revert-check, a reviewer recommendation to merge, and
`blocked` — its own escalation says every finding was a narrowing or a corrected pointer. The failing case is
mechanical and does not need a model: `run#aZeroBlockingRefusalClosesWithItsNotes` fails against the engine at
`dabf314` (`expected 'done', got 'blocked'`, `evidence/red-zero-blocking.log`) and passes with the fix
(`evidence/case-green.log`).

So the decision the root cause leaves is narrow: **by what rule does a round's refusal have a weight the run can
read?** Not "should a reviewer be trusted" — it is a separate, fresh, read-only process either way.

## The alternatives the root-cause evidence eliminated

- **Trust any `FAIL` after round two** (downgrade the last round to a pass). Eliminated: it loses the blocking
  findings with the notes. A change whose scenario no case exercises, or whose gate XML is missing, would close
  on the strength of the round count, and the third reviewer would be decorative. #126 does not need the third
  round to be ignored; it needs the findings it actually produced to be graded.
- **The reviewer returns a score or a confidence number** (`LOOP-SEVERITY: 0.7`). A number without a rule: the
  contract never says what a value means, no case can decide the run's action from it, and the run would have to
  invent a threshold — a magic number, which `openspec/config.yaml`'s archive checks forbid and which no
  evidence in #126 supports. A count of graded findings is the same mechanism with the rule written down.
- **The run reads the prose.** This is the option the loop already tried: `LOOP-CORRECTIONS-ONLY` is a hand-made
  keyword over free text, and #126's own escalation *was* the prose that said every finding was a narrowing and
  recommended merge — the run still blocked it, because prose has no shape `sed` can read. Extending that
  approach (grep for "narrowing", "pointer", "wording") makes the loop's decision a vocabulary test on a
  document it cannot interpret; measured on #187, the same keyword bought a third round rather than a close.
- **Extend `LOOP-CORRECTIONS-ONLY` to mean "non-blocking".** Eliminated by the definitions: subtractive and
  blocking are independent. A deleted claim the spec depends on is strictly subtractive and must block, and
  #126's three rounds were all subtractive-shaped while its escalation calls them all non-blocking — one line
  cannot carry both facts.
- **Escalate every refusal and let a person sort it.** That is what happened: 83 minutes, an escalation, and a
  decision the owner still owes. It moves the cost from the loop to the owner and leaves a mergeable change
  blocked, which is the defect this change exists for.
- **Let the worker judge the findings.** The writer may not be the reviewer (invariant 2, "one writer per tree");
  a worker that can dismiss a finding can dismiss the one that catches it. No case can assert the absence of a
  dismissal.

## What the fix does, and why each part is where it is

**D1 — the reviewer grades and counts; the run reads a number.** `reviewer.md` gains the two prefixes and the
`LOOP-BLOCKING: <n>` line. The grading vocabulary is exactly the three things a change can depend on (the
contract, the scenario-to-case trace, the gate and red-case evidence) plus "everything else", so a reviewer
never has to invent a scale: a finding it cannot grade is blocking.

**D2 — the count decides, not the word.** A `VERDICT: PASS` that counts blocking findings contradicts itself and
is read as a refusal: the count is the machine-readable line, the verdict is the sentence beside it. The prompt
says `PASS` only at 0, so a well-formed report never exercises this; it exists so that a malformed one fails
closed rather than open.

**D3 — a report with no count line is a refusal.** Every report written before this rule has no such line, and
the run has no way to tell an older report from a quiet one. Defaulting the other way would let an old-shaped
review pass silently, which is strictly worse than today. The case `run#reviewRoundsCapAtTwo` — which was already
in the suite, with a bare `VERDICT: FAIL` — pins it.

**D4 — a zero count is a pass, and the notes are recorded.** `run.sh` sets `review_verdict="NOTES"` and breaks;
the closure branch carries the round's `NOTE:` lines into the comment it already posts (`labels.sh close
--reason`) and into the state note it already records (`state.sh done --note`). Those are the two places the loop
already records evidence, so nothing new is invented and no new label is written. The notes are the reviewer's
own lines, verbatim, so the run cannot paraphrase them.

**D5 — which wins where `LOOP-CORRECTIONS-ONLY` and the count meet: the count.** A zero-count report is not a
refusal, and `LOOP-CORRECTIONS-ONLY` describes the shape of a refusal; it presupposes one. So the line is read
only on a refusal (count ≥ 1, or no count at all), and a report carrying both a zero count and
`LOOP-CORRECTIONS-ONLY: yes` closes with its notes instead of buying a round. This is a narrowing of #187's rule,
not a reversal: a report that does refuse and whose findings are all subtractive still gets its extra round, and
`run#aCorrectiveRefusalEarnsOneMoreRound` still passes. The alternative — let the corrections line win — would
keep exactly the behaviour #126 measured: three rounds of rewording, then a block.

**D6 — the count line sits immediately above the verdict.** The reviewer writes the number before the word it
licenses, which is the order the prompt asks for; the run reads both by name (`report_field` takes the last
match), so line order is not load-bearing and a report that puts them the other way round is still read
correctly.

## Risks

| risk | why it is acceptable |
|---|---|
| a reviewer under-grades real blocking findings to force a close | it could already write `VERDICT: PASS`; the difference is that the grade is now a number on a line and the notes land in the item's comment, so a reader sees what was set aside. The prompt makes an ungraded finding blocking. |
| the run closes on a `FAIL` a person would have wanted | the notes are on the item, the report is in the item's evidence directory, and the change is still on its branch — nothing is lost, and the merge is reversible |
| an old-shaped report passes silently | it cannot: no count line reads as blocking (D3) |
| a finding written over several lines loses its tail in the note extraction | the extraction takes one `NOTE:` line per finding, which is what the prompt asks for; the whole report stays on disk and is named in the evidence path |
| notes bloat the closure comment | #126's full reports were 13–16 KB for the whole round; the `NOTE:` subset is a handful of lines, and the comment already carries the evidence paths |

## Threading, lifecycle and verification

No new component, no new thread and no new writer: `blocking_count` and `review_notes` only read a file the
reviewer already wrote, and the two writes they influence (the closure comment and the state note) happen in the
closure branch, between children, where invariant 2 puts every wrapper write. `run.sh` stays the only writer of
the state and the only git actor.

Verification — the change touches no compiled file, so the gate is the loop's own self-test and the structural
checks (`guidelines/Build.md` §2, the scope block):

- `bash tools/loop-selftest.sh --case 'run#aZeroBlockingRefusalClosesWithItsNotes'` — red on the HEAD engine
  (`evidence/red-zero-blocking.log`), green with the fix (`evidence/case-green.log`): the item closes, one round
  was taken, no second reviewer prompt exists, and both `NOTE:` lines are in the state note and in the recorded
  closure comment.
- `bash tools/loop-selftest.sh --case 'run#aBlockingRefusalStillTakesTheRoundTwoPath'` — `LOOP-BLOCKING: 2` takes
  the round-2 path, the second refusal blocks, 4 child invocations, nothing closed.
- `run#reviewRoundsCapAtTwo` (no count line), `run#metricsRecordedPerItem` (a bare `PASS`),
  `run#aCorrectiveRefusalEarnsOneMoreRound` (the extension still works), `prompt#reviewerCarriesTheEightItems`
  (the prompt's own assertions) and `run#engineNamesNoProjectFact` (portability) — all green in the same log.
- `bash tools/check-portability.sh`, `openspec validate fix-loop-finding-severity`, `openspec doctor`.
- No compiled file changes, so there is no build, no unit-test run and no device step; `git status --short
  app/ core/ auto/` stays empty.
