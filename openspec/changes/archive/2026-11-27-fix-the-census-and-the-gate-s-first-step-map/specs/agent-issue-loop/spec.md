## ADDED Requirements

### Requirement: The case set, not one cases file, is what the census and the gate read

`tools/loop/census.sh` SHALL read the suite's case text from a **set**: its `--cases` argument SHALL accept either a
file or a directory, a directory SHALL be read as every `*.sh` directly below it, and the phrase test SHALL be
answered against the whole set — so the counts a census prints are a function of the cases the suite carries and not
of the one path it was pointed at. An argument that names neither a file nor a directory holding at least one `*.sh`
SHALL be a usage refusal naming it, and a directory that holds none SHALL be refused rather than read as "no case
carries this phrase".

The profile's `LOOP_GATE_FIRST_STEPS` SHALL owe the suite's declaration case for a delta to **any** cases file of the
set — the one the suite reads today and a cases file a split puts beside it in `tools/loop/tests/cases/` — and
`loop_case_core_files` SHALL name the directory the suite's cases files live in rather than one file, with a core
token ending in `/` read as owning the paths below it, so a cases-file delta still owes the whole set before the
hunk→case map narrows it.

#### Scenario: A directory of case files is counted as one set

- **WHEN** the census is asked for its numbers over a directory of two case files whose text together is the text of
  the fixture cases file it is asked about today
- **THEN** it prints `guards: 3`, `no-case: 2`, `no-patron: 1` and `unheld: 1` — the same counts the file form
  prints — and its `--json` list of the cases a guard's phrase is missing from carries the same two lines
- **Case**: `tools/loop/tests/loop-selftest.sh --case census#theCensusIsANumber` (the directory leg)

#### Scenario: A sibling cases file owes the declaration's case in seconds

- **WHEN** a delta's only changed path is `tools/loop/tests/cases/run.sh`
- **THEN** the gate's plan carries `tools/loop/tests/loop-selftest.sh --case driver#everyCaseDeclaresTheFilesItCanReach`,
  the step that refuses a bad `# files:` declaration in seconds, before the suites the same delta owes
- **Case**: `tools/loop/tests/loop-selftest.sh --case gate#aPathACheckReadsOwesThatStepsCommandSet` (the sibling-cases-file step)

#### Scenario: A core token that names a directory owns the paths below it

- **WHEN** the suite's core-file list carries `tools/loop/tests/cases/` and a file set names
  `tools/loop/tests/cases/run.sh`, which no case declares
- **THEN** every case of the set is selected (`selected 4 of 4 cases`) instead of the file set being refused, while a
  core token without a trailing `/` still matches by equality only
- **Case**: `tools/loop/tests/loop-selftest.sh --case driver#aFileFilterRunsOnlyTheCasesTheDeltaOwns` (the
  core-directory leg)
