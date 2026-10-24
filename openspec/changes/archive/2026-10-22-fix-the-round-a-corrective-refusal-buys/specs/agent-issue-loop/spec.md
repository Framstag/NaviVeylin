# agent-issue-loop — deltas

## ADDED Requirements

### Requirement: The round a corrective refusal buys is handed the corrections

A run SHALL hand the round it grants after a corrective refusal — a report that states every finding is
subtractive (`LOOP-CORRECTIONS-ONLY: yes`) at the round budget — the correction round alone: the diff between
the refusing round's own HEAD and the current HEAD, under the same recorded exclusion of generated output every
other round uses. The same round's prompt SHALL carry the refusing round's report by path and quoted, and
SHALL state that the diff is that range rather than the change. Every other round SHALL keep reading the
change from the run's base.

#### Scenario: The extra round reads the corrections, not the change again

- **WHEN** a corrective refusal at the round budget buys one more round and the correction worker commits
- **THEN** that round's diff is taken from the refusing round's HEAD, so the line the corrections replaced
  appears in it only as context and not as a change, while the round at the cap carries the change from the
  run's base
- **Case**: `run#aCorrectiveRoundIsHandedTheCorrections`

#### Scenario: The refusing findings travel into the extra round

- **WHEN** the run renders the prompt of the round a corrective refusal bought
- **THEN** the prompt names the refusing round's report, quotes its findings, and states that the diff under
  review is the correction round alone — and a first round's prompt carries no such section
- **Case**: `run#aCorrectiveRoundIsHandedTheCorrections`
