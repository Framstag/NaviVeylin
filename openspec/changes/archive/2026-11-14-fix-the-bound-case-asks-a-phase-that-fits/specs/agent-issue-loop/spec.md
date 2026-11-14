# agent-issue-loop delta

## ADDED Requirements

### Requirement: A bounded phase answer keeps the state and the package's head

`tools/loop/phase.sh` SHALL print the phase state first and cut the work package's tail, so an answer cut at
`LOOP_TOOL_RESULT_MAX_CHARS` still carries the phase state and as much of the work package's head as the bound leaves
after that state, with one line naming where the whole package is. A case that asserts the package's head survives a
cut SHALL ask it of a phase whose state leaves room for that head at the recorded bound, because a token the window
cannot reach is a fixture its own bound contradicts rather than a finding about the tool.

#### Scenario: A cut at the recorded bound keeps the state and the package's head

- **WHEN** the phase state is printed at the recorded bound for a phase whose state leaves room for the work package's
  head
- **THEN** the answer is at or under the bound, says it was cut, carries the phase state and the tree it stands in, and
  holds the package's opening delimiter and its first line but not its last
- **Case**: `phase#aResultOverTheBoundIsCutAndNamed`
