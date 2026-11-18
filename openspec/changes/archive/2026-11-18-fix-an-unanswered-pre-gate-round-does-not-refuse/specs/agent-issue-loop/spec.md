# agent-issue-loop delta

## MODIFIED Requirements

### Requirement: A review finding is blocking or it is not, and the round counts

A review round SHALL grade every finding as **blocking** or **note**, and state one machine-readable count of
its blocking findings (`LOOP-BLOCKING: <n>`). Blocking means the contract is contradicted, a scenario is not
exercised by the case that names it, or the gate or red-case evidence is missing or false; a note is anything
the change does not depend on. The run SHALL refuse on that count, not on the verdict's word.

#### Scenario: A finding that blocks nothing does not refuse the round

- **WHEN** a review round reports `VERDICT: FAIL` and counts zero blocking findings
- **THEN** the change is not refused: the item closes, exactly one round was taken, and the round's `NOTE:`
  findings are recorded on the item — in its closure comment and in its state note — so a note is recorded
  rather than absorbed
- **AND** a report that also carries `LOOP-CORRECTIONS-ONLY: yes` earns no extra round for it, because that line
  describes the shape of a refusal and there is no refusal to describe
- **Case**: `run#aZeroBlockingRefusalClosesWithItsNotes`

#### Scenario: A blocking finding refuses the change as it always has

- **WHEN** a review round counts one or more blocking findings
- **THEN** the round is a refusal: another round is taken within the budget, and a second refusal records the item
  `blocked`, escalates it and writes the reviewer's `LOOP-DECISION` into the escalation
- **Case**: `run#aBlockingRefusalStillTakesTheRoundTwoPath`

#### Scenario: A report that states no count is read as a refusal

- **WHEN** a reviewer report carries no `LOOP-BLOCKING` line, as every report written before this rule does
- **THEN** the round is read as blocking whatever its verdict says, because a run cannot tell an older review from
  a quiet one, so no review can pass by saying nothing
- **AND** a report that carries no closing `VERDICT:` line either is not read by this rule at all: it arrived
  cut, and the retry below establishes whether there was anything to grade
- **Case**: `run#reviewRoundsCapAtTwo`

#### Scenario: An unchanged pass still passes

- **WHEN** a review round reports `VERDICT: PASS` with no count line or a count of zero
- **THEN** the item closes exactly as before, with no notes recorded and no second round taken
- **Case**: `run#metricsRecordedPerItem`

#### Scenario: A report that arrived cut is retried, not read as a refusal

An answer can reach the file the run decides from short: on 2026-10-20 an item's round-2 report stopped
mid-word (`… and the post-restore gre`, 6 067 bytes, ending on the newline the child's adapter appends) while its
own text recorded all seven scenarios PASS, and the run blocked and escalated work that had passed (issue #224).
The shape of the answer is the discriminator, and it is checkable without a model: the child's adapter prints one
newline after every answer it hands the run, and the prompt asks for a `VERDICT: PASS|FAIL` line in the report's
closing block. The missing line is the fact that caught the measured incident; the missing newline covers an
answer whose tail was lost outright.

- **WHEN** a review round's report does not carry the `VERDICT: PASS|FAIL` line its prompt asks for last, or does
  not end with the newline the adapter prints after an answer, and the round's child was not ended by the run's
  own wall-clock (`LOOP-TIMEOUT`)
- **THEN** the run states that the answer was cut short, naming the report's last line, and asks the same round
  once more — the same prompt contract and the same diff, written to `reviewer-prompt-<n>-retry.md`, with the cut
  attempt kept beside the retry as `reviewer-report-<n>.md` and `reviewer-report-<n>-retry.md` — and the retried
  answer decides the round, so the retry spends no second review round and no worker round and the item is not
  escalated
- **Case**: `run#aCutReportIsRetriedInsteadOfRefused`

#### Scenario: Two cut answers in one round block it as the transport's fault

- **WHEN** the retried answer is cut as well, in a round **after** the gate
- **THEN** the item is recorded `blocked` with the reason that the reviewer's report was cut short twice — that
  round produced no verdict at all, so no reviewer refused the change — no worker round is taken for it, and no
  review-failure marker is written into the change on the branch, because a later run must not refuse to adopt a
  change no reviewer refused. The round **before** the gate is not this case: it may refuse nothing, so its second
  cut is recorded `unanswered` and the gate child supplies the item's verdict (issue #953)
- **Case**: `run#aReportCutTwiceBlocksAsTheTransportsFault`

### Requirement: The diff is read before the gate, and a fresh child runs the one gate

An item's gate SHALL be run by a child of its own, after the diff has been read by a reviewer that is not the child
that wrote it, so a finding a reader can make from the diff is not paid for by a gate and a second full pass.

#### Scenario: The round is read before the gate child is started

- **WHEN** a run with the pre-gate review on reads a change child that ended at the diff (`LOOP-DIFF-READY`) and left
  no gate line
- **THEN** the run commits that diff, starts a reviewer on it, and starts the gate child only after that round
  answered — the reviewer's round is observable before the gate is reached, and the item still spends one gate child
- **Case**: `run#thePreGateReviewRunsBeforeTheGate`

#### Scenario: The findings reach the child that runs the gate

- **WHEN** the round before the gate refuses the diff with a blocking finding
- **THEN** the findings are appended to the gate child's prompt, which applies them in one pass before running the
  gate, and no rework worker is started for them because the item was not refused
- **Case**: `run#thePreGateReviewRunsBeforeTheGate`

#### Scenario: The round before the gate costs no round of the budget

- **WHEN** the round before the gate refuses the diff and the item's change then passes the round after the gate
- **THEN** the item records one review round, because `--max-review-rounds` bounds the rounds after the gate and the
  round before it refuses nothing
- **Case**: `run#thePreGateReviewRunsBeforeTheGate`

#### Scenario: A round the run cut before the gate is left for the next run

- **WHEN** the round before the gate never answers because a bound the run enforces ended it — the run's deadline, or
  its role's turn bound or the item's context bound both before **and** after the one restart this run gives it
- **THEN** the item is recorded `cut` with that round named as the one before the gate, no gate child is started on a
  diff nobody read, and the round after the gate is not asked either — while a round the bound ended once is
  restarted first, in a fresh session with the findings it had written, and its answer is the round's
- **Case**: `run#aPreGateReviewEndedAtItsBoundNamesIt`

#### Scenario: A round the transport cut twice is unanswered, and the gate child decides

- **WHEN** the round before the gate answers nothing at all — its report arrives without the closing `VERDICT:` line
  twice, and no bound the run enforces ended either attempt (as two reviewer children of the run of
  2026-11-17T10:58Z did, answering the run with a question)
- **THEN** the round is recorded `unanswered` — neither a pass nor a refusal, and not the `cut-short` a bound leaves —
  its count is recorded, the step on the item says the round answered nothing, the gate child is started on the diff
  it never read and told the round handed nothing forward, and the item's outcome becomes the gate child's, which is
  the item's only verdict: no reason names a reviewer that refused a change nobody read, and no round after the gate
  is asked on an item the gate blocked
- **Case**: `run#aPreGateReviewCutTwiceIsUnansweredAndTheGateDecides`

#### Scenario: A change child that ran the gate itself is read as it always was

- **WHEN** the change child's report carries a gate line and no `LOOP-DIFF-READY`
- **THEN** the run starts no round before the gate and no gate child, because a gate the run did not run is not
  re-run and a round before a gate that has already run has nothing to read
- **Case**: `run#childUsageIsRecordedPerItem`, `run#announcesAndRecordsTheReviewRound`

#### Scenario: A phase's checklist is the prompt of the child that performs it

- **WHEN** the phase state is printed for phase E
- **THEN** the checklist it prints is the gate child's own prompt and the call names that file, because the run
  defers the gate to that child
- **Case**: `phase#oneCallPrintsThePhaseState`

### Requirement: An item the run's own bound cut is recorded `cut`, not `blocked`

An item a bound the run enforces ended SHALL be recorded `cut`: the run's deadline or a role's turn bound
ending a review round, and the role's turn bound, the context bound, the child clock, a vanished writer or the run's deadline ending the item's own child.
It SHALL carry `bound` as its stop reason, with the key the bound is recorded in, whenever a bound the run enforces
ended it — a review round as much as the item's own child, because the bound is the run's and the item is what it
ended — so the mechanism's spend is totalled beside the result, SHALL write no
decision label and no comment, SHALL NOT enter the two-blocked streak `state.sh next` stops on, and SHALL be named on
a line of its own in the report and counted there as a cut rather than a block, so no reader needs the mechanism's
vocabulary to tell work from a refusal. The work an ended child left SHALL stay where the child left it, SHALL be
named in the item's own record, and SHALL NOT be committed.
The round a review bound ended SHALL be written on the branch as a cut with its announcement withdrawn, and SHALL stay
named in the item's own record as its own fact — the round, the key and the figure — beside the class it now shares
with the item. A state a
tree wrote before `cut` carried a bound keeps that `blocked` + `bound` pair out of the streak, while a pre-mechanism
pair with no stop reason still stops the run, and an answer the *transport* cut twice is not this case either: no bound
ended it, so it is recorded `unanswered` and the gate child supplies the item's verdict (issue #953).
The reason a bound-ended round leaves on the item SHALL name the bound that ended it and the figure it reached,
quoted from the child's own `LOOP-TIMEOUT` line, and SHALL NOT offer the two bounds in general as the answer: a
person reading the item tells the turn bound from the context bound, and how far past it the child ran, without
a transcript they were not handed (issue #829).
The class SHALL name the bound it stands for, as `boundKey` and `boundValue` beside it, and SHALL carry neither where
no configuration records a value for what ended the child — the item's own clock, the run's deadline and a vanished
writer are the class alone (issue #923).
A round the run ended SHALL record its own ending in the round's own field beside the item's class — the round, the
key and the figure, taken from the round's own `LOOP-TIMEOUT` line where the item's class carries no key of its own —
and the report SHALL count those rounds on a line of its own rather than folding them into the item's rate.

#### Scenario: A round the turn bound ended leaves no decision behind

- **WHEN** an item's tail writes `LOOP-VERDICT: closed`, its gate answered and its change is archived, and the run
  then ends review round 1 at the reviewer's role's turn bound
- **THEN** the item is recorded `cut` with `bound` as its stop reason and its round marked cut, no
  `agent/needs-decision` label is written for it
  and the report's decision list does not hold it, the branch carries `review-round-1-cut.md` and no
  `review-round-1-requested.md`, and `branch-state` reads `adoptable` — work the next run finishes, not a question
  only its owner can answer
- **Case**: `run#aReviewerEndedAtItsTurnBoundIsNotARefusal`

#### Scenario: A child the turn bound ended is recorded cut, not blocked

- **WHEN** an item's own child reaches its role's recorded turn bound and the run ends it there
- **THEN** the item is recorded `cut` with `bound` as its stop reason and the count in its note, the report counts it
  as a cut and not as a block and names it on the run's own bound line, no `agent/needs-decision` label and no
  retry-or-abandon comment is written for it, and nothing is closed
- **Case**: `run#childPastItsTurnBoundIsCut`

#### Scenario: A child the item's own wall clock ended is recorded cut, not blocked

- **WHEN** the wall-clock cap ends an item's worker that has left changes in the change root
- **THEN** the item is recorded `cut` with `bound` as its stop reason, the run commits none of the work — the change
  root's `HEAD` is still the base commit — the tree still carries what the child left, the item's note names the
  patch it was written to and says the attempt is the next run's to resume, and no decision label is written for it
- **Case**: `run#childOutlastingTheWallClockIsCut`, `run#treeOfAChildTheCapEndedIsNotCommitted`

#### Scenario: A child the run's own deadline ended is recorded cut

- **WHEN** the run's deadline passes while an item's child runs and the run ends it there
- **THEN** the item is recorded `cut` with `bound` as its stop reason and the `LOOP-TIMEOUT` transcript it left, no
  decision label is written for it, and the run records the deadline as its own stop reason
- **Case**: `run#childIsEndedAtTheRunsDeadline`

#### Scenario: Two consecutive cuts do not stop the run

- **WHEN** two items in a row are recorded `cut` and the queue still holds a candidate
- **THEN** `state.sh next` hands out that candidate, a block that follows a cut is the first block of its own pair,
  and two blocks in a row still stop the run
- **Case**: `state#nextHonoursEveryStopCondition`

#### Scenario: A block at a bound holds no pair

- **WHEN** two items in a row carry a bound — recorded `blocked` with `bound` as their stop reason by a state a tree
  wrote before `cut`, or `cut` with `bound` by this run — and the queue still holds a candidate
- **THEN** `state.sh next` hands out that candidate, because what the pair is read for is a refusal's prediction
  about the next item — the same gate, the same provider, the same tree — and a bound predicts nothing about an
  item that starts a fresh worker on a fresh tree; a refusal after a bound cuts is the first block of its own pair,
  and a state written before the mechanism existed, whose two `blocked` items carry no stop reason, still stops the
  run as it always did
- **Case**: `state#aBlockAtTheRunsBoundIsNoRefusal`

#### Scenario: A review round the turn bound cut names the bound and its figure

- **WHEN** the run ends review round 1 at the reviewer's role's turn bound of 3 turns, after the child took 3 turns
- **THEN** the reason on the item names the turn bound, the figure it reached and the key it is recorded in
  (`LOOP_CHILD_TURNS_MAX_REVIEWER`), quoted from the round's own `LOOP-TIMEOUT` line, beside the round, the cut and
  the sentence that leaves the change for the next run, and the item records that bound as its stop reason
- **Case**: `run#aReviewerEndedAtItsTurnBoundIsNotARefusal`

#### Scenario: The round before the gate names the bound that cut it

- **WHEN** the round before the gate is ended at the reviewer's turn bound and its transcript carries the bound,
  the key and the count
- **THEN** the item is recorded `cut` with `bound` as its stop reason and that reason naming the turn bound, the
  figure and the key, no gate child is started on the unread diff, and no round after the gate is asked
- **Case**: `run#aPreGateReviewEndedAtItsBoundNamesIt`

#### Scenario: A bound-ended item records the key and the figure it was cut at

- **WHEN** an item's own worker takes three turns past a worker turn bound recorded as 3 in `loop.conf`, and the run
  ends it there
- **THEN** the item is recorded `cut` with `bound` as its stop reason and `boundKey` `LOOP_CHILD_TURNS_MAX_WORKER`
  with `boundValue` `3`, read from the child's own `LOOP-TIMEOUT` line, the state's own table prints
  `bound LOOP_CHILD_TURNS_MAX_WORKER=3` in the record's `stop` cell, and the report names `#21` on its line of the
  items a bound cut short — so the archive can be totalled by the bound and not only by the class
- **Case**: `run#aBoundEndedItemRecordsItsBound`

#### Scenario: A round the run ended records its own ending beside the item's class

- **WHEN** the reviewer takes its third turn past a reviewer turn bound recorded as 3 and the run ends review
  round 1 there
- **THEN** the item is recorded `cut`, `metrics.roundBound` carries the round (`1`, or `pre-gate` for the round
  before the gate), the key `LOOP_CHILD_TURNS_MAX_REVIEWER` and the figure `3`, the record's `round` column prints
  `round 1 ended at LOOP_CHILD_TURNS_MAX_REVIEWER=3`, and the report prints
  `rounds a bound ended: 1 of 1 item(s) · #21 1 LOOP_CHILD_TURNS_MAX_REVIEWER=3` beside `bound: 1 of 1 items` —
  two figures, neither folded into the other
- **Case**: `run#aRoundTheRunEndedRecordsItsBound`

#### Scenario: A record written before the two fields is read by the bound its note names

- **WHEN** the report reads a state written before `boundKey` and `roundBound` existed — a record carrying `bound`
  with its figure in the note, and a record carrying `cut` with `cut-short` and the reviewer's key in the note
- **THEN** the first prints the bound it was cut at in its `stop` cell and the second prints its round in the `round`
  column, both read by the one derivation `state.sh` and `report.sh` share, which tells a round from the item's own
  cut by the reviewer's role in the key and the round's own verdict rather than by the fields being absent
- **Case**: none — no case pins the derivation: it was read on the committed archive when `cdf0648c` landed, and the
  commit records that it is pinned by no case

### Requirement: A review round a bound ended is restarted in the run that cut it

A round a role's turn bound or the item's context bound ended SHALL be restarted once in the same run, in a fresh
session on the same bounded input, with what the cut round had written handed to it; only a second bound SHALL be
a `cut`. `LOOP_REVIEW_RESTART_MAX` SHALL bound the restarts one round takes (`0` or absent = none), a restart SHALL
NOT be started past the run's deadline and SHALL NOT count a second round, and it SHALL be recorded with the round,
the bound and the cut round's report.

#### Scenario: A round a bound ended is restarted and answers

- **WHEN** a review round ends at its role's turn bound with no `VERDICT:` and no refusal — an unfinished reading
  rather than an objection
- **THEN** the run starts the round again in a fresh session, its prompt carries the cut round's `BLOCKING:`/`NOTE:`
  lines, the bound's own line and the report of the round before the gate when one ran, the item closes on the
  restart's answer, and the round count stays at one because a restart is the same round read twice
- **Case**: `run#aBoundCutRoundIsRestartedWithItsFindings`

#### Scenario: A second bound is the cut, and the record names the restart

- **WHEN** the restart is ended by a bound as well, or the round before the gate is ended at its bound and restarted
- **THEN** only the second ending records the item `cut` with `bound` and the key the bound is recorded in, and the
  item's record carries `metrics.reviewRestart` naming the round, the bound it reached and the file the cut round
  had written — so a round that answered after a restart does not read as a round that answered first time
- **Case**: `run#eachChildIsBoundedByItsOwnRolesTurns`, `run#aPreGateReviewEndedAtItsBoundNamesIt`

#### Scenario: A round the transport cut twice is asked again and not restarted

- **WHEN** a reviewer's report arrives cut on its way to the file twice and no bound ended that round
- **THEN** the round is asked once more under its `-retry` name and is never given a `-restart` prompt, and the item
  is recorded on that round's own shape rather than on a bound's: a round **after** the gate blocks it as the
  transport's fault, and the round **before** it is recorded `unanswered` with the gate child supplying the verdict,
  because nothing a bound enforces ended either attempt (issue #953)
- **Case**: `run#aReportCutTwiceBlocksAsTheTransportsFault`,
  `run#aPreGateReviewCutTwiceIsUnansweredAndTheGateDecides`

### Requirement: A review round is non-interactive, and a question is a cut-short answer

The reviewer prompt SHALL state, beside the output contract, that the round runs with nobody to ask: never ask a
question, never offer to proceed or to confirm, and never offer a summary instead of the round. It SHALL
name an answer that is a question a cut-short answer — no verdict is read, the retry spends the byte-identical
prompt, and the round is recorded `unanswered` — and leave a refusal's
`LOOP-DECISION` line to the contract it points at. The clause SHALL stand in both rounds' text.

#### Scenario: Both rounds are told the round is non-interactive

- **WHEN** the run renders the reviewer prompt, once for the round before the gate and once for the round after it
- **THEN** each carries the clause beside its output contract, the round before the gate carrying it because that
  round is the same prompt with its own section substituted rather than a prompt of its own
- **Case**: `prompt#thePreGateRoundRefusesOnlyWhatTheGateCannotSettle`

#### Scenario: A question is named a cut-short answer, and the cost is stated

- **WHEN** the reviewer prompt is rendered
- **THEN** it names an answer that is a question a cut-short answer and states what such an answer costs — the one
  retry spends a byte-identical prompt that asks the same question again, and the round is recorded as unanswered
- **Case**: `prompt#reviewerCarriesTheEightItems`
