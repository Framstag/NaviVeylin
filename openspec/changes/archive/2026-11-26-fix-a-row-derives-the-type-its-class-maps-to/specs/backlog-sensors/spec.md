## MODIFIED Requirements

### Requirement: A check's issues arrive classified and unauthorised

An issue filed by a check SHALL carry the kind and area its row declares, the tracker type that kind maps to in
the registry, and SHALL NOT carry the automation opt-in. A class is this project's vocabulary and a type is the
tracker account's, so the mapping is the registry's own data and no engine names either side.

#### Scenario: The row's classification is used

- **WHEN** a check files an issue
- **THEN** the issue carries the kind and area declared in its row, so no further interpretation is needed
  before it can be ranked or selected

#### Scenario: The class derives the type, and the row may override it

- **WHEN** a row is read for the type its issues carry
- **THEN** a `type` the row declares is answered as it stands, and otherwise the class the row declares is answered
  through the registry's `classTypes` mapping — so `bug` is `Bug` and `improvement` is `Task` in this project's
  registry, and the engine names neither a class nor a type
- **Case**: `Registry#everyRowIsComplete`

#### Scenario: A class with no type behind it is refused before any filing

- **WHEN** a registry's `classTypes` does not cover a class one of its rows declares
- **THEN** that row is reported as `row-type-unmapped`, naming the class, so a registry that would file an untyped
  item is refused before any filing rather than after one
- **Case**: `Registry#everyRowIsComplete`

#### Scenario: Filing is not authorising

- **WHEN** a check files an issue
- **THEN** the issue is not eligible for automated work until a person applies the opt-in, however clearly
  defect-shaped it is
