## ADDED Requirements

### Requirement: A child's tool results are bounded and a trim is named

A result a child's tool returns SHALL enter that child's context whole only while it is no longer than the bound
the project records (`LOOP_TOOL_RESULT_MAX_CHARS`); above it, the result SHALL be replaced by its first
`<bound>` characters and one line naming the tool, the size the result had, the bound it was cut to and how to read
the rest, so no claim is ever judged on a cut line. The trim SHALL be recorded once, with the tool and both sizes,
beside the child's own event stream in the item's directory. The bound SHALL be applied where a result passes from
the tool to the child — not by asking the child to read less — and `0` SHALL mean no bound.

#### Scenario: A result above the bound is trimmed and named

- **WHEN** a child's tool returns more characters than the recorded bound
- **THEN** the first `<bound>` characters are handed over unchanged, nothing past the bound reaches the context,
  and one line names the tool, the size it had, the bound and how to read the rest
- **Case**: `trim#aResultAboveTheBoundIsTrimmedAndNamed`

#### Scenario: A result at or under the bound is handed over whole

- **WHEN** a child's tool returns at most the recorded bound
- **THEN** the result reaches the context byte for byte, with no notice and no record, so the bound costs nothing
  to the nine results in ten the archive measured under it
- **Case**: `trim#aResultUnderTheBoundIsHandedOverWhole`

#### Scenario: The trim is recorded for the item

- **WHEN** a result is trimmed
- **THEN** one line names the tool, the size it had and the bound it was cut to, beside the child's event stream in
  the item's directory, so a reader of the item can re-read the range instead of trusting a cut line
- **Case**: `trim#theTrimIsRecorded`

#### Scenario: Every child is started with the bound

- **WHEN** an agent process is started for an item
- **THEN** its argv loads the file that applies the bound, immediately before the mode words, and that file is a
  hook on the tool result rather than a flag the agent happens to accept
- **Case**: `adapter#theChildIsStartedWithTheResultBound`
