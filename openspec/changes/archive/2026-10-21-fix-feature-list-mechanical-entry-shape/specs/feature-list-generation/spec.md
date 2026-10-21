# feature-list-generation — deltas

## MODIFIED Requirements

### Requirement: Every entry leads with a keyword and a paragraph

Each catalogue entry SHALL begin with a short keyword in bold, followed by a paragraph, so a reader can scan the
area for the word that matters to them and then read what the app does. The keyword SHALL be at most six words and
SHALL be drawn from the vocabulary of the specs the entry cites; the paragraph SHALL state what the entry's
capabilities do. A run that composes an entry without a phrasing command SHALL shorten a requirement name to
that shape.

#### Scenario: An entry leads with a keyword

- **WHEN** the catalogue is generated
- **THEN** every entry begins with a bold keyword followed by a paragraph, and no entry begins with bare prose
- **Case**: `gates: an entry with no bold keyword fails the run`

#### Scenario: The keyword is short enough to scan

- **WHEN** an entry's keyword is measured
- **THEN** it is at most six words
- **Case**: `gates: a keyword longer than six words fails the run`

#### Scenario: The keyword comes from the cited specs

- **WHEN** an entry's keyword is checked against the specs the entry cites
- **THEN** every content word of the keyword is found there, so the scan word is the product's word and not an
  invention
- **Case**: `gates: a keyword word the cited specs lack fails the run`

#### Scenario: A mechanically composed entry leads with a six-word keyword

- **WHEN** no phrasing command is given and a capability's requirement name is longer than six words, so that it
  would lead an entry of the release note
- **THEN** the entry leads with a keyword of at most six words taken from that name, and the run writes the
  release note instead of failing its own gate
- **Case**: `length: a mechanical release-note entry is composed inside the bound`

### Requirement: A paragraph stays short

An entry's paragraph, excluding its bold keyword, SHALL be at most 320 characters. A run that would publish a
longer paragraph SHALL name the entry and its length, fail, and SHALL NOT overwrite the catalogue, so that an entry
can be read in one glance and the catalogue reads as a set of selling points rather than as a specification. A run
that composes an entry without a phrasing command SHALL stay inside that bound as well: the text it wraps around
the capability names is measured too.

#### Scenario: An over-long paragraph fails the run

- **WHEN** an entry's paragraph is longer than 320 characters
- **THEN** the run names the entry and its length and exits non-zero
- **Case**: `length: an over-long paragraph fails the run`

#### Scenario: A short paragraph passes

- **WHEN** every entry's paragraph is within 320 characters
- **THEN** the run accepts the catalogue
- **Case**: `length: a short paragraph passes`

#### Scenario: The keyword does not count towards the length

- **WHEN** an entry's length is measured
- **THEN** only the paragraph is measured, so a longer keyword cannot push an entry over the bound
- **Case**: `length: the paragraph and its length are named`

#### Scenario: A mechanically composed paragraph is composed inside the bound

- **WHEN** no phrasing command is given and a capability's requirement names are long enough that the entry's
  paragraph would pass 320 characters once its lead-in and its full stop are added
- **THEN** the run composes the paragraph inside the bound and writes the catalogue, rather than failing and
  leaving both documents unwritten
- **Case**: `length: a mechanical catalogue entry is composed inside the bound`
