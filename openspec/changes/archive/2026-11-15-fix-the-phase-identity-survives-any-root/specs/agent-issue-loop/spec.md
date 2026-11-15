# agent-issue-loop delta

## MODIFIED Requirements

### Requirement: A bounded phase answer keeps the state and the package's head

`tools/loop/phase.sh` SHALL print the phase state first and cut the work package's tail, so an answer cut at
`LOOP_TOOL_RESULT_MAX_CHARS` still carries the phase state and as much of the work package's head as the bound leaves
after that state, with one line naming where the whole package is. A case that asserts the package's head survives a
cut SHALL ask it of a phase whose state leaves room for that head at the recorded bound, because a token the window
cannot reach is a fixture its own bound contradicts rather than a finding about the tool.

The state SHALL be printed in the order a cut leaves it whole: the phase line, then the one `ROOT:` line, then the
item, then the package's identity — its path, its fingerprint, its provenance and the title it is accepted against —
and only then the tree, the last gate verdict the item's stream records, the paths the phase writes, the checklist and,
last, the item's own text. Every path the answer prints under the run root SHALL be relative to that one `ROOT:` line,
and a path outside the root SHALL keep its absolute form, so that what the bound takes is the checklist's tail and
then the item's text, which the identity above it names the path to.

A case that asks what an answer cut at a bound keeps SHALL pin the bound it asks at rather than inherit the recorded
figure, and SHALL ask it of a root deep enough that the root's own length cannot decide the verdict.

#### Scenario: A cut at the recorded bound keeps the state and the package's head

- **WHEN** the phase state is printed at the recorded bound for a phase whose state leaves room for the work package's
  head
- **THEN** the answer is at or under the bound, says it was cut, carries the phase state and the tree it stands in, and
  holds the package's opening delimiter and its first line but not its last
- **Case**: `phase#aResultOverTheBoundIsCutAndNamed`

#### Scenario: The identity survives the bound on a root of any depth

- **WHEN** the phase state is printed at a pinned bound on the suite's own fixture root and on one over 120 characters
  long, and at the recorded bound on that long root
- **THEN** each answer is at or under its bound, says it was cut, and still holds the package's fingerprint, its
  provenance, the title it is accepted against and the path that names the package relative to the root, together with
  the last gate verdict and the evidence directory and their paths relative to the root — while the root itself is
  printed once, on the `ROOT:` line, so no other line spends the bound on it
- **Case**: `phase#theIdentitySurvivesTheBoundOnAnyRoot`

#### Scenario: One call prints the phase state

- **WHEN** a phase's state is asked in one call of a fixture whose state leaves room for the package's head, with the
  bound off because the case asserts the shape of the whole answer
- **THEN** the answer names the phase with the heading its own checklist carries, the item and the change the run asked
  for and the phase the run recorded, the tree with its branch, its head, what it is ahead and behind of and the paths
  it has dirty, the last gate verdict the item's stream records, the package's fingerprint and provenance with the
  item's text inside its delimiters, the paths the phase is expected to write, and the checklist line naming the prompt
  that carries the phase's section — no other phase's section, and a phase whose prompt carries no section named as
  such instead of invented
- **Case**: `phase#oneCallPrintsThePhaseState`
