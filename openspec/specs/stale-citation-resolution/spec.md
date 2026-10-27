# stale-citation-resolution Specification

## Purpose

An issue's citation is checked and, where it can be, repaired automatically: one definition of what a citation
is, a mechanical ladder that rewrites a citation whose target moved, a quarantine for the ones that cannot be
repaired, and a bounded scheduled run — so that an authorised item is excluded only when its citation really is
gone, and the residue that needs a person is small, visible and ranked rather than silent.

## Requirements

### Requirement: One definition of what a citation is

The tooling SHALL decide what counts as a citation in exactly one place, used by every consumer, and that
decision SHALL distinguish a path from a mention: a token is a citation only when it respects token
boundaries, carries a source-file extension the project recognises, and denotes a path the project can hold.
Each citation SHALL resolve to exactly one class: `exact` (the path resolves from the repository root),
`suffix` (exactly one tracked file path ends with it), `submodule` (it resolves inside a submodule),
`generated` (it names an artefact the project produces rather than tracks), `not-a-path` (a token that is not a
citation at all), or `absent` (no file of that name exists anywhere in the repository or its submodules).
A consumer SHALL treat `absent` as a **report**, not a verdict: it SHALL name the token and SHALL NOT refuse
the item and SHALL NOT park it, because a path that is absent may be one the change itself creates.

#### Scenario: A fragment is not a citation

- **WHEN** a body contains a longer token such as `-Dorg.gradle.java.home` or a sentence fragment such as
  `it.sh`
- **THEN** no citation is extracted from it, and no staleness verdict follows

#### Scenario: A header name without a path separator is not a citation

- **WHEN** a body names a system header such as `jni.h` or `dagger.h`
- **THEN** no citation is extracted — a header is only a citation when it is written as a path — and the item is
  not stale

#### Scenario: A path written without its prefix resolves

- **WHEN** a body cites `SearchScreen.kt`, `guidelines/UI.md` by name only, or `app/src/test/.../XTest.kt`
  with the middle abbreviated
- **THEN** the citation classifies as `suffix` or `exact`, and the item is not stale

#### Scenario: A submodule path resolves inside the submodule

- **WHEN** a body cites `Tests/src/SearchScopeTest.cpp`
- **THEN** the citation classifies as `submodule`, and the item is not stale

#### Scenario: A generated artefact is not a missing file

- **WHEN** a body cites an artefact the project produces rather than tracks, such as `RELEASE-NOTES.md`
- **THEN** the citation classifies as `generated`, and the item is not stale

#### Scenario: Only an absent citation is stale

- **WHEN** no file of that name exists in the repository or in any submodule, and it is not generated
- **THEN** the citation classifies as `absent`, and only then is the item's staleness verdict true

#### Scenario: An absent citation is reported, not refused

- **WHEN** an item cites a path the repository does not hold
- **THEN** the token is named in the item's record and the item stays eligible, and no refusal label is written
- **Case**: `prescreen#missingPathIsReportedNotRefused`

### Requirement: A citation is repaired when the repair is mechanical

The resolver SHALL repair a citation whose target can be established as a fact, in this order: a unique suffix
of a tracked path (canonicalised to that path), a rename recorded in the repository's history, or a single
surviving file that declares the cited symbol. A repair SHALL rewrite the citation token and nothing else, and
SHALL preserve every body marker verbatim. A basename repair SHALL NOT rewrite an occurrence that stands inside a
longer path. A repair is a change to the record: the item SHALL count as repaired, and a comment SHALL claim a
repair, only when the produced body differs from the input **and** every rewritten citation re-classifies as no
longer `absent`; a rewrite that fails either test SHALL be reported as unresolved and the item SHALL be left
untouched.

#### Scenario: A suffixed path is canonicalised

- **WHEN** a body cites a class-file name that ends exactly one tracked path
- **THEN** the citation is rewritten to that full path
- **Case**: `resolver#suffixedCitationIsCanonicalised`

#### Scenario: A renamed file is followed

- **WHEN** the cited path no longer exists and the repository's history records a rename or a move of it
- **THEN** the citation is rewritten to its successor
- **Case**: `resolver#renamedFileIsFollowed`

#### Scenario: A removed symbol is re-anchored

- **WHEN** the cited file is gone and exactly one surviving file declares the cited symbol
- **THEN** the citation is rewritten to that file
- **Case**: `resolver#removedSymbolIsReAnchored`

#### Scenario: The item's record survives the repair

- **WHEN** a citation is rewritten
- **THEN** the body's `fingerprint` and `nvid` markers are byte-identical to what they were, and no prose around
  the citation is changed
- **Case**: `resolver#markersSurviveTheRepair`

#### Scenario: A repair is recorded

- **WHEN** a citation is rewritten and the repair is verified
- **THEN** one comment on the item names the citation before and after the repair, and the evidence for it
- **Case**: `resolver#repairPostsOneComment`

#### Scenario: A basename repair leaves a longer path alone

- **WHEN** a body cites a bare file name whose only other occurrence stands inside a longer citation — the
  doubled-directory form — and both are stale in the same body
- **THEN** the bare occurrence is canonicalised and the longer citation is collapsed, not re-doubled, so every
  citation in the written body resolves
- **Case**: `resolver#basenameRepairLeavesALongerPathAlone`

#### Scenario: A rewrite that changes nothing is not a repair

- **WHEN** the repair ladder attempts a rewrite whose result is the input again
- **THEN** nothing is counted as repaired, no comment claims a repair, and the citation is reported unresolved
  with the reason
- **Case**: `resolver#repairWithoutAChangeIsNotReported`

#### Scenario: A repair is verified by re-classification

- **WHEN** a rewrite replaces the cited token with a target the repository does not hold
- **THEN** the citation is not counted as repaired and it is reported unresolved with the reason, so the record
  is not claimed to be repaired again
- **Case**: `resolver#repairIsVerifiedByReclassification`

#### Scenario: A repair needs no second decision

- **WHEN** the resolver has repaired an item's citation
- **THEN** the item is eligible again without a person editing its body, and the next pre-screen queues it
- **Case**: `pipeline#scheduledRunRepairs`

### Requirement: What cannot be repaired is reported, never guessed

When a citation is `absent` and no mechanical successor exists, the resolver SHALL report it and write nothing
for the item — no status, no comment; it SHALL never invent a target, never close an item, never write the
authorisation label, and never change an item's kind, area or priority.

#### Scenario: An unrepairable citation is reported, not written

- **WHEN** the ladder finds no successor for an absent citation
- **THEN** the citation is named in the run's report and the item's state, labels and body are untouched, so the
  next run reaches it for the same citation as soon as the record states a successor
- **Case**: `resolver#unrepairableCitationIsReportedNotWritten`

#### Scenario: An ambiguous match is reported, not repaired

- **WHEN** more than one tracked path could be the citation's target
- **THEN** nothing is rewritten, and the ambiguity is reported with the candidates

#### Scenario: The resolver cannot authorise or close

- **WHEN** the resolver runs
- **THEN** it writes no `agent/*` label and closes no item, whatever it finds

### Requirement: The resolver runs on its own, bounded and idempotent

The resolver SHALL run without a person, in the checks' own pipeline under that pipeline's least privilege,
SHALL be bounded per run, SHALL default to a dry run, and SHALL be idempotent. The bound SHALL be spent only
on the items the run acts on — a repair, and in a dry run the items it would act on — so an item the ladder can
only report cannot consume the run's budget, and SHALL never hide the items it did not reach: the run SHALL
report how many wait. The count of unresolved citations SHALL be a declared check row, so a growing residue is a
measurement.

The citations SHALL be classified from the item's title and body together — the pair the pre-screen asks — while
only the body can be written. A token the body does not carry SHALL therefore never be rewritten, and the two
outcomes are the bound's own terms: a token that resolves SHALL leave the item settled and unwritten, costing no
slot, and a token the tree does not hold SHALL be reported with the reason recording that the citation stands in
the title — a report, so it costs no slot — and SHALL never be reported as a rewrite that changed nothing, so a
stale title is a state a person can query.

#### Scenario: A scheduled run repairs without a person

- **WHEN** the pipeline runs on its schedule
- **THEN** the citations it can repair are repaired, and the items are eligible again
- **Case**: `pipeline#scheduledRunRepairs`

#### Scenario: The bound holds

- **WHEN** more items are due than the run's bound allows
- **THEN** the run repairs the first ones, reports how many wait, and the next run continues
- **Case**: `resolver#boundHoldsAndReportsTheRest`

#### Scenario: The bound is spent where the tracker changes

- **WHEN** an item's only stale citation is ambiguous, so the ladder can report it but write nothing for it
- **THEN** the item costs no slot of the run's bound, the items behind it in tracker order are still reached,
  and the ambiguity is still counted and still writes nothing
- **Case**: `resolver#ambiguityDoesNotConsumeTheBound`

#### Scenario: A second run changes nothing

- **WHEN** the resolver runs again over items it already processed
- **THEN** no citation is rewritten, no comment is posted, and the run reports an empty working set
- **Case**: `resolver#secondRunChangesNothing`

#### Scenario: A citation the body does not carry is settled

- **WHEN** an item's title names a file by a bare name whose tracked path the body already cites in full, as
  every item repaired from the doubled-directory form ends up
- **THEN** the item is not due, its body is not rewritten, no comment is posted and its state is unchanged, so
  the run after a repair finds nothing to do
- **Case**: `resolver#titleSuffixBesideItsCanonicalPathIsSettled`

#### Scenario: A stale citation in the title is parked where it stands

- **WHEN** the title cites a file the repository does not hold and the body's own citations do not need it
- **THEN** it is reported with a reason naming the title as the citation's place, because the resolver writes
  bodies and cannot rewrite a title, and no rewrite that was never attempted is reported
- **Case**: `resolver#titleAbsentCitationIsReportedWhereItStands`

#### Scenario: The residue is a declared check

- **WHEN** citations remain unresolved
- **THEN** a registry row reports their count as its measurement, so the pile cannot grow unobserved
- **Case**: `pipeline#residueIsADeclaredRow`

### Requirement: A reversal restores the state the item's own record keeps

The reversal `--revisit-parked` owns SHALL restore the settled state an item's own record keeps, not the queue
state, when that record is present. A body carrying the profile's `settledRecordMarker` SHALL be reversed to
`roles.retiredStatus` and never to `openStatus`; a body carrying no such record SHALL keep the reversal to
`openStatus`. The run SHALL report the two outcomes apart (`restored` beside `reopened`) and SHALL still never
write the authorisation label.

#### Scenario: A parked tombstone is restored to its settled state

- **WHEN** a parked item the resolver reverses carries the settled-record marker in its body
- **THEN** its status is the profile's `retiredStatus` and not `openStatus`, the loop's stale refusal is
  cleared, the authorisation label is not written, and the run counts it as restored
- **Case**: `resolver#parkedTombstoneKeepsItsSettledState`

#### Scenario: An item without the record returns to the open state

- **WHEN** a parked item whose body carries no settled-record marker is reversed
- **THEN** its status is the profile's `openStatus`, as before, and the run counts it as reopened rather than
  restored
- **Case**: `resolver#cleanParkedItemIsReopened`

#### Scenario: A restore target that is not a settled state is refused

- **WHEN** the run asks for the reversal and the profile declares `retiredStatus` outside its
  `settledStatuses`
- **THEN** the run stops before it writes anything, naming both roles, so no item is reversed into a state the
  pre-screen would read as work
- **Case**: `resolver#tombstoneRestoreNeedsASettledState`
