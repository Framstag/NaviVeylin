# Spec Delta

## ADDED Requirements

### Requirement: Free-text candidate budget counts kept candidates

The per-source free-text budget SHALL count the candidates the search keeps, not every hit it inspects. A hit
the active scope refuses SHALL NOT spend the budget, so a scoped search SHALL deliver the requested candidate
count when the text index reports that many in-scope hits. The budget SHALL NOT be read as bounding the
index's traversal: the index hands a query's complete hit set back in one call.

#### Scenario: A scoped walk delivers the requested candidates past refused hits

- **WHEN** a search runs with an active scope and a candidate limit of 30
- **AND** the text index reports 60 hits, the first 30 of which lie outside the scope's extent
- **THEN** the search inspects all 60 hits and keeps 30 in-scope candidates
- **Case**: `Tests/src/SearchScopeTest.cpp` "A scoped free-text walk collects the requested candidates, not
  fewer" — red with the HEAD rule (kept 0 for a limit of 30), green with this change

#### Scenario: A refused hit does not spend the budget

- **WHEN** the budget allows one candidate and the first hit offered lies outside the scope's extent
- **THEN** that hit is refused and the budget is still unspent
- **AND** the next in-scope hit is kept and spends it
- **Case**: `Tests/src/SearchScopeTest.cpp` "A refused hit does not spend the budget"

#### Scenario: Without a scope every hit spends the budget

- **WHEN** no scope is active (an unset extent) and the limit is 5
- **THEN** the fifth hit offered spends the budget, because an unset extent admits every position
- **Case**: `Tests/src/SearchScopeTest.cpp` "Without a scope every hit spends the budget"
