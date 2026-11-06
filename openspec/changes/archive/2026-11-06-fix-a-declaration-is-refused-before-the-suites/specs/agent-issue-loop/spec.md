# Spec Delta — agent-issue-loop

## ADDED Requirements

### Requirement: A step that refuses a delta in seconds is owed before the suites

The profile SHALL name the steps the gate owes before its build and before its suites, in the suite map's
`<glob>|<command>` shape, read in that order. A delta that writes a case's first-line `# files:` declaration SHALL
owe the case that reads it before the build and before the suite that would refuse it at its end. The child's step
SHALL send it to the plan's first line, which is what saves the attempt. The case that reads the declaration SHALL
stay the one owner of the reach table.

#### Scenario: A delta that writes a declaration owes its reader first

- **WHEN** a delta writes a case into `tools/loop-cases.sh` and touches a compiled file, and the gate is asked what
  that delta owes
- **THEN** the plan's first line is
  `tools/loop-selftest.sh --case driver#everyCaseDeclaresTheFilesItCanReach`, before the suite the declaration narrows
  and before the profile's compiled command
- **AND** the child's own step sends it to that same plan line and names no case of this project, so a project that has
  no such case still has a step to run
- **Case**: `gate#theDeclarationCaseIsOwedFirst`
