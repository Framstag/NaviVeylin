## MODIFIED Requirements

### Requirement: The declared set agrees with the bodies and the dispatch arms of the cases file

The suite's driver SHALL derive the case names the files it sourced for its cases define — a body is a
`case_<group>_<Name>() {` line and an arm is a `'<group>#<Name>') fn=case_<group>_<Name> ;;` line — and SHALL compare
them with the declared set `loop_shell_cases` answers before it lists, selects or runs anything. The scan SHALL be
handed every file the suite reads its case names from: the cases file it sources, and the file a dispatch arm table of
its own is read from (`tools/loop/tests/loop-case-index.sh`) when the tree carries one, so the three sets are compared
once over the union of those files and not over one path. A body with no row, a row with no body or no dispatch arm, an
arm with no row, and an arm whose function is not the body its name derives SHALL each be a refusal that names the case
and the set it is missing from, with exit status 1 and no case executed. The names SHALL be read from the files that
were just sourced, never from a second copy of them. A scanned file that defines no body SHALL NOT be held to the bodies
(it dispatches by convention) and the bodies-only comparison SHALL be taken when no scanned file carries an arm: a body
or an arm inside a heredoc SHALL NOT be read as this suite's own, and the four `stub#owns*` bodies a nested fixture's own
quoted `LOOP_SHELL_CASES` string carries SHALL be exempt by name.

#### Scenario: A row a delta replaced instead of inserting is refused before any case runs

- **WHEN** the driver is run over a cases file that declares one row and defines the bodies `case_stub_kept` and
  `case_stub_lost`, each with a dispatch arm
- **THEN** the driver exits 1 naming `stub#lost` as a body with no row in `LOOP_SHELL_CASES`, no case has been
  executed, and the one row it declares is not read as a whole suite
- **Case**: `driver#aCaseWithNoSelectionRowIsRefused`

#### Scenario: An arm table in a second file is compared, not skipped

- **WHEN** the driver is run over a cases file that carries two rows and two bodies while the dispatch arms live in the
  second file beside it, which that cases file sources — complete in one run, and with the arm for `stub#lost` removed
  in the next
- **THEN** the complete arm table selects and runs `stub#kept` with exit 0, and the missing arm exits 1 naming
  `stub#lost` as a row of `LOOP_SHELL_CASES` with no dispatch arm, no case executed
- **Case**: `driver#anArmTableInASecondFileIsCompared`
