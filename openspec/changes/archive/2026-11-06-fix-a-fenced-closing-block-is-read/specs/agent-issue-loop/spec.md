# Spec Delta — agent-issue-loop

## ADDED Requirements

### Requirement: A closing line is read where it stands, fence or no fence

The run SHALL NOT read the fence a child puts around its closing block as the absence of the line: one backtick at
each end of a `LOOP-BLOCKING:` or `VERDICT:` line SHALL be taken off before that line is read as a field, a count or
the closing line. Taking it off SHALL NOT weaken either fact the completeness rule is made of: a report with no
closing line, or whose last byte is not the adapter's newline, is still cut, and one that states no count is still a
refusal.

#### Scenario: A closing block written as inline code is a complete answer

Measured 2026-11-06 on item #788: both of its pre-gate reports ended `` `LOOP-BLOCKING: 3` `` then
`` `VERDICT: FAIL` ``, each file's last byte the newline the adapter prints, and the run read both as cut answers —
it blocked the item and escalated *no reviewer refused this change* while the report in its hand carried three
blocking findings (issue #793).

- **WHEN** a review round's report ends with its `LOOP-BLOCKING:` and `VERDICT:` lines written as inline code, with
  the adapter's newline after the last of them
- **THEN** the answer is complete: the round is not asked again, the count inside the fence is the count the round is
  decided by, and a fenced `VERDICT: FAIL` whose count is zero closes the item with its `NOTE:` findings recorded
- **Case**: `run#aFencedClosingBlockIsACompleteAnswer`
