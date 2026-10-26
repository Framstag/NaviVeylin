## ADDED Requirements

### Requirement: A churn bound is above one change

A row whose score multiplies a path's size by how often it changed SHALL declare a bound above the largest file in
the tree it watches, so a path the row merely contains — changed once in the window — is not a condition, and
crossing the bound takes at least a second change. The number is the row's to refit when the tree outgrows it; the
rule is that one change is not heat.

#### Scenario: One change of a file the row watches is not a hotspot

- **WHEN** a churn row measures a path the tree holds that changed once in its window, beside a path that changed
  several times
- **THEN** only the churned path is a condition, and the row's bound is above the largest tracked file of the tree
  it watches, so no single change of a file that tree holds can cross it
- **Case**: `hotspot#theBoundIsAboveOneChangedFile`
