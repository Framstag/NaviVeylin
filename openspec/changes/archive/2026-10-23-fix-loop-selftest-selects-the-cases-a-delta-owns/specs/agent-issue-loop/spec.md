## ADDED Requirements

### Requirement: A case's files select it, and the gate hands a suite the base it measured

A case SHALL declare the files it can be affected by in the first line of its body (`# files: <token> …`), and
the self-test SHALL select on that declaration with `--files <path> …` and `--from-git <base>`. A file set that
names no case SHALL be refused with exit 2 before any case runs, the selection SHALL be stated against the
declared set, a core file SHALL owe every case, and a profile's `{base}` SHALL be replaced by the base the gate
resolved.

#### Scenario: A file set runs the cases that declare it and no others

- **WHEN** the driver is run with `--files` naming a script one case declares and a path below the directory
  another declares, against a declared set of four cases
- **THEN** exactly those two cases run, the suite's summary counts two, and the selection is stated as two of four
- **Case**: `driver#aFileFilterRunsOnlyTheCasesTheDeltaOwns`

#### Scenario: A file set that names no case is refused

- **WHEN** `--files` names a path no case declares, and then a path the tree has but no case declares
- **THEN** the driver exits 2 naming the declared set it could not resolve, and no case has been executed
- **Case**: `driver#aFileSetThatNamesNoCaseIsRefused`

#### Scenario: A core file owes every case

- **WHEN** `--files` names the library every engine script sources
- **THEN** every declared case runs, however narrow the cases' own declarations are
- **Case**: `driver#aCoreFileOwesEveryCase`

#### Scenario: A declaration is checked against the case it describes

- **WHEN** every declared case's body is read against its `# files:` declaration
- **THEN** each path the body names, and each engine script a helper it calls starts, is covered by the
  declaration, and every declared token names a path this tree has
- **Case**: `driver#everyCaseDeclaresTheFilesItCanReach`

#### Scenario: The gate hands a suite the base it measured

- **WHEN** a profile's suite command carries `{base}` and the gate is asked what a delta of an engine path owes
- **THEN** the plan carries that command with the resolved base substituted, and no `{base}` token reaches a shell
- **Case**: `gate#theDeltaHandsASuiteTheBaseItMeasured`
