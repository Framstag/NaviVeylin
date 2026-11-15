# agent-issue-loop delta

## ADDED Requirements

### Requirement: The self-test's file set is scoped by the cheapest file the set declares

The mutation harness's self-test SHALL scope its file-set leg by one file of the declared set, chosen for how much of
the declaration that file owns: the file the declared set gives the fewest records to, among the files that own at
least two, so the leg runs a handful of records rather than the whole of a file that owns most of the set. The choice
SHALL be held to the declaration by a case of the case driver rather than trusted as a constant, and a pick the
declaration gives no set of at least two to SHALL be reported as such rather than read as a file set.

#### Scenario: The file-set leg scopes by the file the declaration gives fewest records to

- **WHEN** the case driver reads the file the self-test's file-set leg scopes by, and counts the declared set's records
  per file the way the leg itself derives them
- **THEN** that file is the one owning the fewest records of any file that owns at least two, so the leg's run is
  bounded by a handful of records instead of by the whole of the largest owner's set, and a pick the declaration gives
  no two records to is named rather than run
- **Case**: `mutation#theFileSetLegScopesByTheCheapestFile`
