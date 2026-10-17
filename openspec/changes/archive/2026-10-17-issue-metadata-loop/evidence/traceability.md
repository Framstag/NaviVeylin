# Scenario → case → task (task 7.6)

One row per scenario of `specs/issue-priority/spec.md`, naming the case that exercises it and the task that
owns that case. The row count is the spec's scenario count:

```
$ grep -c '^#### Scenario:' openspec/changes/issue-metadata-loop/specs/issue-priority/spec.md
22
```

Two scenarios are claims about a **reader's** behaviour rather than about a command: "Reading is read-only"
and "A dated deadline outranks a better score". The reader is the `triage-todo` skill, whose contract is
prose — a skill has no executable form to test — so those two name the skill section that carries the rule,
plus the case that keeps the underlying query honest. They are the only rows whose evidence is a document,
and they are listed as such rather than dressed up as a test.

| # | scenario (spec) | case | task | kind |
|---|---|---|---|---|
| 1 | The band is selectable | `list#prioSelector` | 5.1 | case |
| 2 | The components are recorded beside the value | `prioritise#writesLabelAndMarkerTogether` | 2.5 | case |
| 3 | The range is fixed and total | `Bands#coversTheWholeRange` (Java core) | 1.1 | case |
| 4 | The label and the record agree | `check#markerLabelMismatchFails`, `check#labelWithoutMarkerFails` | 1.2, 1.4 | case |
| 5 | An unchanged item is not re-interpreted | `prioritise#readsEveryIssueAndSkipsByBasis` | 2.2 | case |
| 6 | A repeated run does nothing | `prioritise#secondRunScoresNothing` | 2.4 | case |
| 7 | The basis covers the ways a context moves | `basis#changesWhenAClassLabelChanges`, `basis#changesWhenTheCitedCodeGoesAway`, `prioritise#occurrenceChangeInvalidatesTheBasis` | 2.1, 5.2 | case |
| 8 | A surface edit is not a context change | `basis#stableAcrossWhitespaceOnlyBodyEdit` | 2.1 | case |
| 9 | An old value is revisited | `basis#anAgedScoreIsRevisited` | 2.1 | case |
| 10 | A bounded run leaves the rest reported | `prioritise#capsTheDueSet` | 2.3 | case |
| 11 | Selection never samples | `prioritise#readsEveryIssueAndSkipsByBasis` | 2.2 | case |
| 12 | The run explains its cost | `prioritise#reportsEveryCounter` | 6.1 | case |
| 13 | The unclassified set is the queue | `classify#selectsOnlyUnclassified` | 3.1 | case |
| 14 | A proposal outside the vocabulary is refused | `classify#refusesAnOutOfVocabularyProposal` | 3.2 | case |
| 15 | An existing value outside the vocabulary is reported | `classify#reportsAnOutOfVocabularyCategoryWithoutWriting` | 3.2 | case |
| 16 | Classification writes nothing else | `classify#writesOnlyClassAndCategory` | 3.3 | case |
| 17 | A top-priority item is still not eligible | `list#eligibleOnlyIgnoresTheBand` | 5.3 | case |
| 18 | The pass cannot authorise | `prioritise#writesNoGateLabel`, `check#neverWritesEligibility` | 5.3, 1.3 | case |
| 19 | Reading is read-only | `.pi/skills/triage-todo/SKILL.md` (the reader writes nothing) + `list#prioSelector` (the query it reads from) | 4.3, 5.1 | document |
| 20 | An item with no stored priority is reported as unranked | `list#unrankedSelector` | 5.1 | case |
| 21 | The gates are not stored with the priority | `prioritise#writesNoGateLabel` | 5.3 | case |
| 22 | A dated deadline outranks a better score | `.pi/skills/triage-todo/SKILL.md` §5, "Deadline override" | 4.3 | document |

Every case name above resolves in one of the two suites:

```
$ bash tools/issue-tooling-selftest.sh --list | grep -c '#'      # the shell cases
$ bash tools/issues/check-metadata-selftest.sh --list            # the check's own seven cases
$ java -cp <classes> Cases --list | grep 'Bands#'                # the core's cases
```

Two further cases exist that no scenario names, because they guard the boundary rather than a behaviour
claim: `prioritise#bodiesAreDelimitedData` and `prioritise#refusesABodyThatClosesTheFence` (task 4.2's
untrusted-input rule, which the spec states in no scenario) and `prioritise#refusesATaxonomyOutsideValue`
(task 2.5's "refused before any write" half). They are recorded here so that a reader sees the mapping is
complete in both directions: no scenario is unmapped, and every case is claimed by a task.
