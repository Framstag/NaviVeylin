# Spec Delta — agent-issue-loop

## ADDED Requirements

### Requirement: A prompt whose child reads a long text names the cap that cuts it

The phase prompt a child is started with SHALL name the bound a tool result is cut at, the marker a cut result
carries, the call and the arguments that page the rest until the file's last line, and the rule that a quoted anchor
comes only from a window that arrived whole. The bound is enforced where a result passes
(`tools/pi-trim-result.mjs`), never by asking the child to read less, so the prompt is the only place a child learns
that what it holds may be a window rather than the file — and a review's whole product is a claim about the text it
read, which the gate cannot tell from one written over a file head.

#### Scenario: Every phase prompt names a cut read and how to page it

- **WHEN** the worker's, the gate child's, the tail's or the reviewer's prompt is rendered
- **THEN** it names the recorded bound a tool result is cut at, the marker the cut result carries, the call and the
  range arguments that page the rest until the file's last line, and that only a window that arrived whole may be
  quoted or graded
- **Case**: `prompt#thePhasePromptsCarryTheirOwnRoute`
