## ADDED Requirements

### Requirement: A report's route resolves to a section its document carries

A change SHALL name the guideline section its work was routed by in one line, `LOOP-ROUTE: guidelines/<doc>.md §<id>`,
and `tools/loop/evidence-check.sh` SHALL resolve that id against the ids the named document's own `## ` headings
carry — the `## <id>. Thing` shape and the `## §<id> Thing` shape this project writes, and only those headings, never
the document's body: a heading that carries a dotted sub-number (`## 3.6 …`) or no shape separator at all
(`## 9 Nine`) carries no id. The check SHALL refuse a document outside the project's guideline directory, a document
that does not exist, and an id no heading of it carries, naming the ids the document does carry. A line SHALL count as
a route only when nothing but list or quote markers and space precedes it, so a paragraph that quotes the form is prose
about a route and not one, and the migration table's reference check SHALL NOT read a route line's id against its own
rows. A change that names no route at all SHALL be a warning until the project's configuration reads the route as
required, and a failure after that.

#### Scenario: An id a document does not carry is refused

- **WHEN** a change's markdown carries a route naming an id its document does not carry, the document's headings being
  `## 1. The queue` and `## 2a. Agent iteration protocol`
- **THEN** the check exits 1 and its message names the sections the document does carry, so the false route is read
  before the landing rather than archived as a route
- **Case**: `tools/loop/evidence-check.sh --selftest` (`a § the document does not have is a false pointer`)

#### Scenario: A refusal names the sections the document carries

- **WHEN** a route names an id the document does not carry
- **THEN** the refusal's own line lists the ids the document's headings carry, so a reader repairs the route without
  opening the document
- **Case**: `tools/loop/evidence-check.sh --selftest` (`the refusal names the document's section list`)

#### Scenario: Both heading shapes resolve

- **WHEN** a route names a section of a document written as `## <id>. Thing` or as `## §<id> Thing`
- **THEN** the check exits 0 and reports the route lines it resolved against the document's own headings
- **Case**: `tools/loop/evidence-check.sh --selftest` (`a route resolves against the document's own heading`)

#### Scenario: A dotted sub-number heading carries no id

- **WHEN** a route names an id whose only heading in the document is a dotted sub-number (`## 3.6 …`), which is
  neither of the two shapes the project writes
- **THEN** the check exits 1, so a false pointer to `§3` cannot pass as a resolved route
- **Case**: `tools/loop/evidence-check.sh --selftest` (`a dotted sub-number heading carries no id`)

#### Scenario: A heading with neither shape separator carries no id

- **WHEN** a route names an id whose only heading in the document carries neither separator (`## 9 Nine`), which is
  neither of the two shapes the project writes
- **THEN** the check exits 1, so a false pointer to `§9` cannot pass as a resolved route
- **Case**: `tools/loop/evidence-check.sh --selftest` (`a heading with neither shape separator carries no id`)

#### Scenario: A quoted form is not a route line

- **WHEN** a change's prose quotes the form — "the report block carries `LOOP-ROUTE: …`" — while the change carries no
  route line of its own
- **THEN** the check reads the paragraph as prose about the line and not as a route, so the id it happens to quote is
  not resolved and nothing is refused for it
- **Case**: `tools/loop/evidence-check.sh --selftest` (`a paragraph quoting the form is not a route line`)

#### Scenario: No route line is a warning while the project reads the route as a warning

- **WHEN** a change carries no route line and the project's configuration reads the route as `warn`
- **THEN** the check prints the warning, counts it as a warning and exits 0
- **Case**: `tools/loop/evidence-check.sh --selftest` (`a report with no route line warns rather than failing`)

#### Scenario: The same report is refused once the project requires the route

- **WHEN** the same change carrying no route line is read with the route as `fail`
- **THEN** the check exits 1 and names the missing route line, so the mode the configuration records is the one that
  decides and not a second rule in the tool
- **Case**: `tools/loop/evidence-check.sh --selftest` (`and the same report fails once the project reads the route as required`)
