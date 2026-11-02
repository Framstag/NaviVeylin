# Spec Delta — documentation-ownership

## MODIFIED Requirements

### Requirement: The review's reading set is derived from the change's paths

A review's reading set SHALL be derived from the paths a change touched, through a map from a path glob to the
guideline sections and rule names that own that path's conventions. The map SHALL be a file in the tree, a glob
that matches no tracked path SHALL be reported, and a rule that no glob reaches SHALL be reported. A glob over
`guidelines/` SHALL name the document — with its companion layers — or the concern family whose sections and rules
its route carries, and SHALL NOT be wide enough to match the whole corpus, because under the union a review
derives, one corpus-wide glob returns every guideline route for any guideline edit. A change MAY add a section to
its derived set by naming it.

#### Scenario: The reading set follows the diff

- **WHEN** a change touches paths whose globs the map names
- **THEN** the derived set lists those sections and rule names, and a review is graded against that set rather than
  against the sections the change's own artifacts name
- **Case**: `routes#theReadingSetFollowsTheDiff` — `tools/check-rule-routes.sh --diff <range>` over a fixture range
  prints the sections and rules, and a change naming fewer sections than the map derives is reported

#### Scenario: A glob that matches nothing is reported

- **WHEN** the map names a glob under which the tree holds no file
- **THEN** the check exits non-zero and names the glob, because a moved package or module otherwise orphans the
  rules a reader is told to read
- **Case**: `routes#aGlobMatchingNoTrackedPathIsReported`

#### Scenario: A rule no glob reaches is reported

- **WHEN** a named rule under `guidelines/` is reachable from no glob in the map
- **THEN** the check reports the rule and its section, so every rule has a reader before it has a violation
- **Case**: `routes#aRuleNoGlobReachesIsReported`

#### Scenario: One guideline edit derives its own document

- **WHEN** a change touches one file under `guidelines/` — a normative document, or one of its companion layers
- **THEN** the derived set is that document's concern's entries and no other document's, so a one-document edit is
  never told to read the whole corpus
- **Case**: `routes#aGuidelineEditDerivesItsOwnDocument` — this tree's own map and guideline corpus are copied into
  a throwaway git tree, one edit is committed per document, and `--diff` must derive the edited document's entries
  alone (measured on base `650028bf`: 270 entries naming 10 documents for one `guidelines/Logging.md` edit)
