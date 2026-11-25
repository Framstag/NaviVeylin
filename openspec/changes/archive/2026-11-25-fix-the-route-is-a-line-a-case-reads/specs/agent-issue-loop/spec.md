## ADDED Requirements

### Requirement: The skeleton that owes a route states it as the line the check resolves

`tools/loop/prompts/worker.md` SHALL state the route in the anchored form `tools/loop/evidence-check.sh`'s C6
resolves — `LOOP-ROUTE: guidelines/<doc>.md §<id>` — in the phase step that picks the guideline section and in the
report block it ends with, and SHALL name the change's own markdown there as the copy the check reads. A route asked
for as a sentence SHALL NOT satisfy it, because no step carries a sentence.

#### Scenario: The skeleton prints the line the check resolves

- **WHEN** the report block of `tools/loop/prompts/worker.md` is read for the route a change names
- **THEN** the anchored form appears in the phase step that picks the section and in the report block, and the block
  names the change's own markdown as the copy the check resolves
- **Case**: `prompt#thePhasePromptsCarryTheirOwnRoute` (`the worker skeleton prints the route as the anchored line the check resolves`)

### Requirement: The loop's suite reads a route against a project's document

`tools/loop/tests/loop-cases.sh` SHALL hold a case that runs the route check over a project built by `new_project`,
not over the tool's own fixtures, and that case's `# files:` line SHALL name every file it reaches — including
`tools/pi-child-events.sh`. Each arm the case asserts SHALL be falsified by one record of
`tools/loop/tests/loop-mutations.sh`.

#### Scenario: A false route is refused through the loop's own case

- **WHEN** the suite runs the route case over a project whose change names an id its document does not carry, and over
  the same document's two heading shapes, and over a paragraph that only quotes the form
- **THEN** the case reads the check's exit 1 with the sections the document does carry for the false id, exit 0 for
  both shapes, exit 0 for the quoted form, and exit 0 with the warning when the change carries no route line under
  `warn`
- **Case**: `check#aRouteSectionResolvesAgainstItsDocument`

#### Scenario: Each arm of the case is falsified

- **WHEN** the heading comparison is made always true, and the missing-route arm is made to refuse under `warn`
- **THEN** the case goes red under each record in turn and green again once the file is restored, so the case is a
  reading of the tool and not a second copy of it
- **Case**: `check#aRouteSectionResolvesAgainstItsDocument` (records `974.1`, `974.2`)
