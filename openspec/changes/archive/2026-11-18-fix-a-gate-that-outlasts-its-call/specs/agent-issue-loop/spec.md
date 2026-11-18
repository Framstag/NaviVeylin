# agent-issue-loop delta

## ADDED Requirements

### Requirement: The prompt that hands a child its gate names the wall and the way through it

The prompt that hands a child its gate SHALL name the wall between one tool call and the gate, and the way through
it: the gate started detached, its output written to the evidence directory, that log read by a poll the child's own
shell allows, and a resumed poll rather than a second gate. A call ended at its ceiling SHALL NOT be read as the
gate's end, and the gate line a child reports SHALL be the one that log carries.

#### Scenario: Both skeletons a child can be handed its gate by name the wall and the poll

- **WHEN** a child is handed the item's gate, in the deferred shape or in the one-child shape
- **THEN** the skeleton it is handed names the tool call and the ceiling that ends it, the detached start, the log in
  the evidence directory, the poll that reads it, and the refusal a second gate meets
- **Case**: `prompt#theGatePromptNamesTheWallAndTheWayThrough`

#### Scenario: A gate started while one runs measures nothing

- **WHEN** a child restarts the gate while the gate it started is still running
- **THEN** the second run refuses with `LOOP-GATE-REFUSED`, names the lock and its owner, exits `4` and writes no
  tally, so the child reads it as a wait rather than as a red gate
- **Case**: `gate#aSecondGateRefusesWhileOneRuns`

### Requirement: A value substituted into a prompt arrives as the text it was handed

A placeholder SHALL be replaced by its value exactly as that value stands, whatever characters it carries: a `&` or a
`\` in a value SHALL reach the rendered prompt as itself, and a `{{KEY}}` a value carries SHALL be read only as the
placeholder a later pair names. A shell option that gives a replacement a meaning of its own SHALL NOT apply where the
substitution is made, so a skeleton may hand a child a command carrying `&&`, a redirection `2>&1` or a trailing `&`.

#### Scenario: A shell command in a value reaches the prompt whole

- **WHEN** a value handed to the renderer carries `&&`, `2>&1` and a trailing `&`, and the template names it twice
- **THEN** the rendered text carries that value byte for byte at both places, and no copy of the placeholder appears
  in the output
- **Case**: `prompt#aValueArrivesAsTheTextItWasHanded`
