# Spec Delta — agent-issue-loop

## ADDED Requirements

### Requirement: A step that refuses a delta in seconds is owed before the suites

The profile SHALL name the steps the gate owes before its build and before its suites, in the same
`<glob>|<command>` shape as the suite map, and the gate SHALL read them in the order the profile writes them.
A delta that writes a case's first-line `# files:` declaration SHALL owe the case that reads that declaration
before the suite that would otherwise refuse it at its end and before the build. The child's own verification step
SHALL name that case and the declaration that owes it, because a gate's order saves the gate's tail while only the
child's step saves the attempt: an attempt refused by the gate is recorded `blocked` and worked again from the base.
The case that reads the declaration SHALL stay the one owner of the reach table; a second, cheaper derivation of the
same reading is not this rule.

#### Scenario: A delta that writes a declaration owes its reader first

- **WHEN** a delta writes a case into `tools/loop-cases.sh` and touches a compiled file, and the gate is asked what
  that delta owes
- **THEN** the plan's first line is
  `tools/loop-selftest.sh --case driver#everyCaseDeclaresTheFilesItCanReach`, before the suite the declaration narrows
  and before the profile's compiled command
- **AND** the worker prompt names that case and the `# files:` line that makes it owed
- **Case**: `gate#theDeclarationCaseIsOwedFirst`
