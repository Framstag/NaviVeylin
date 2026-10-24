# Delta — feature-list-generation

## MODIFIED Requirements

### Requirement: Prose the run cannot verify is reported for review

The run SHALL report, without failing, the content words in a generated entry that appear in none of the specs that
entry cites, so that a claim the gate cannot settle is visible to a reviewer instead of silent. A content word is a
word of four or more characters that is not in the run's stopword list. The report SHALL name the word and the entry
it came from.

The report SHALL be bounded per area, so that its signal survives its volume: the run SHALL show at most its
word-report cap of the distinct such words of that area, SHALL rank them by how few specs of the project contain
them — a word no spec contains ranking ahead of a word one spec contains — and SHALL state for that area how many
words it found, how many it showed and how many it did not show. A word SHALL be reported once per area however many
entries of that area use it.

#### Scenario: A word the cited specs do not contain is reported

- **WHEN** a entry contains a content word that appears in none of the specs it cites
- **THEN** the run names that entry and that word in its report and still exits successfully
- **Case**: `gates: the unsourced word is named`

#### Scenario: A entry whose words are all sourced is not reported

- **WHEN** every content word of a entry appears in a spec that entry cites
- **THEN** the report names neither the entry nor any of its words
- **Case**: `gates: a fully sourced bullet is not reported`

#### Scenario: The report does not stand in for the figure gate

- **WHEN** a entry states a figure that appears in none of its cited specs
- **THEN** the run fails rather than reporting, whatever the word report says
- **Case**: `gates: an invented figure fails the run`

#### Scenario: The report shows no more words of an area than its cap

- **WHEN** an area's entries carry more unverifiable content words than the run's word-report cap
- **THEN** the report names no more of them than the cap allows, one line per word
- **Case**: `gates: the word report shows no more than its cap in one area`

#### Scenario: The report counts what it did not show

- **WHEN** an area's entries carry more unverifiable content words than the report shows
- **THEN** the report states, for that area, how many it found, how many it showed and how many it did not show
- **Case**: `gates: the word report counts the candidates it did not show`

#### Scenario: The rarest word is named first

- **WHEN** an area's report contains a word that appears in no spec of the project and a word that appears in one
- **THEN** the word no spec contains is named before the one a single spec contains
- **Case**: `gates: the word report names the rarest word first`
