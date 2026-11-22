## MODIFIED Requirements

### Requirement: A child is told what its own shell and tools refuse

The phase prompt a child is started with for work that writes the tree SHALL name what the process it runs in
refuses, before its first tool call:
that the commit is the run's and the child neither commits nor stages, which git calls come back refused for an
approval no unattended process can be asked for, that inline code is refused and how a number is taken instead, that
an edit call needs the text as it stands now, and which shell verbs over the tree are refused. The verb list SHALL
name every verb the refusal policy module exports, and the case for this requirement SHALL read that export and fail
when the prompt's list and the policy differ, so a verb the policy gains is a case that fails rather than a child that
learns it by being refused. The block SHALL hand the child the call that answers the question it refuses the shell
for — for a range of a file, `ctx_read`'s own `offset` and `limit` — and SHALL name no shell range read beside those
tools, because the alternative a block offers is the call a child takes. A reviewer's prompt is out of scope: that
child is bounded to reading.

#### Scenario: The phase prompt names what the child's shell refuses

- **WHEN** a phase prompt is rendered for work that writes the tree
- **THEN** it carries that block — the commit that is the run's, a git call that comes back refused, inline code, the
  edit that needs the text as it stands now, and the shell verbs a tree search is refused for — every verb the
  refusal policy exports is named in it, the read it hands the child for a range is `ctx_read`'s own with no shell
  range read named beside the tools, and each of the three skeletons a child can be handed for such work states
  that block's heading
- **Case**: `prompt#thePhasePromptNamesWhatItsShellRefuses`

### Requirement: A child's tool results are bounded and a trim is named

A result a child's tool returns SHALL enter that child's context whole only while it is no longer than the bound
the project records (`LOOP_TOOL_RESULT_MAX_CHARS`); above it, the result SHALL be replaced by its first
`<bound>` characters and one line naming the tool, the size the result had, the bound it was cut to and how to read
the rest. That call SHALL be `ctx_read`'s range call alone — no shell range read — so the notice a child reads and the
phase prompt that quotes it are one string. The trim SHALL be recorded once, with the tool and both sizes, beside the
child's own event stream in the
item's directory, so a reader of the item can re-read the range the notice names. The bound SHALL be applied where a
result passes from the tool to the child — not by asking the child to read less — and `0` SHALL mean no bound.

#### Scenario: A result above the bound is trimmed and named

- **WHEN** a child's tool returns more characters than the recorded bound
- **THEN** the first `<bound>` characters are handed over unchanged, no character of the result past the bound
  reaches the context, and one line names the tool, the size it had, the bound and how to read the rest — which is
  `ctx_read`'s range call alone, with no shell range read beside it
- **Case**: `trim#aResultAboveTheBoundIsTrimmedAndNamed`

#### Scenario: A result at or under the bound is handed over whole

- **WHEN** a child's tool returns at most the recorded bound
- **THEN** the result reaches the context byte for byte, with no notice and no record, so the bound costs nothing
  to the nine results in ten the archive measured under it
- **Case**: `trim#aResultUnderTheBoundIsHandedOverWhole`

#### Scenario: A result between the recorded bound and the one it replaced arrives cut

- **WHEN** a child's tool returns more characters than the project records but fewer than a bound it recorded before
- **THEN** the result is cut at the recorded bound and named, so the number the file holds and not an earlier one
  decides what the child is handed
- **Case**: `trim#aResultBetweenTheNewBoundAndTheOldOneArrivesCut`

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
