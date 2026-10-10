# Spec Delta

## Purpose

The project publishes a living, user-facing catalogue of its shipped capabilities, clustered by feature area and
kept in step with the specs without regenerating the areas that did not change.

## ADDED Requirements

### Requirement: The catalogue presents only user-visible capabilities

The catalogue SHALL present a capability only when its spec is classified user-visible. A capability belonging to a
spec classified internal SHALL NOT appear in the catalogue, in any heading, entry, tag or count.

#### Scenario: An internal capability is absent

- **WHEN** the catalogue is generated while a spec classified internal has changed
- **THEN** no heading, entry or tag in the catalogue refers to that spec or to any of its requirements

#### Scenario: An area holding only internal capabilities gets no heading

- **WHEN** every capability of a feature area is classified internal
- **THEN** the catalogue carries no heading for that area

#### Scenario: The catalogue names every user-visible area

- **WHEN** the catalogue is generated
- **THEN** it carries a heading for each feature area that holds at least one user-visible capability

### Requirement: Car support is a tag, not a separate copy of the feature

Each feature area in the catalogue SHALL state the surfaces its capabilities are available on, taken from the
classification. A capability available on both surfaces SHALL appear once under its feature area, and SHALL NOT be
repeated in a car-only section. A capability available only in the car but belonging to a feature area SHALL appear
under that area, tagged through its area as available in the car.

#### Scenario: A capability on both surfaces is tagged once

- **WHEN** a capability applies to phone and to a car platform
- **THEN** it appears once under its feature area, tagged with both surfaces, and not again in any other area

#### Scenario: A car-only capability of a feature area stays under that area

- **WHEN** a capability of a feature area is available only in the car
- **THEN** it appears under that feature area, the area states car availability, and the capability appears in no
  car-only area

#### Scenario: Searching the catalogue for a surface finds every claim about it

- **WHEN** a reader looks for every capability the product offers in the car
- **THEN** the car tag on the feature areas and the car-only area together name them, and no car capability is
  reachable only by reading prose

### Requirement: The car platform owns one area of its own

Capabilities whose subject is the car platform itself — how the app appears, starts and behaves in the car rather
than what it does — SHALL be grouped under the single area that the classification marks car-only, and SHALL NOT be
spread across the feature areas. Exactly one area SHALL be marked car-only, and every capability placed in it SHALL
be exclusive to a car platform.

#### Scenario: Exactly one area is the car platform's own

- **WHEN** the catalogue is generated
- **THEN** exactly one area is marked car-only and it holds every capability whose subject is the car platform

#### Scenario: A capability about the car platform is not spread into a feature area

- **WHEN** a capability describes how the app appears or behaves in the car rather than what the app does
- **THEN** it appears in the car-only area and not under a feature area

#### Scenario: The car-only area is rendered last and heading-named

- **WHEN** the car-only area holds at least one user-visible capability
- **THEN** the catalogue carries that area's heading, and it renders under its own heading rather than merged into
  another area

### Requirement: Every entry leads with a keyword and a paragraph

Each catalogue entry SHALL begin with a short keyword in bold, followed by a paragraph, so a reader can scan the
area for the word that matters to them and then read what the app does. The keyword SHALL be at most six words and
SHALL be drawn from the vocabulary of the specs the entry cites; the paragraph SHALL state what the entry's
capabilities do.

#### Scenario: An entry leads with a keyword

- **WHEN** the catalogue is generated
- **THEN** every entry begins with a bold keyword followed by a paragraph, and no entry begins with bare prose

#### Scenario: The keyword is short enough to scan

- **WHEN** an entry's keyword is measured
- **THEN** it is at most six words

#### Scenario: The keyword comes from the cited specs

- **WHEN** an entry's keyword is checked against the specs the entry cites
- **THEN** every content word of the keyword is found there, so the scan word is the product's word and not an
  invention

### Requirement: Every entry carries the capabilities it came from

Every capability entry in the catalogue SHALL carry the capability keys it was derived from. A run that would emit
a entry carrying no key SHALL report that entry, fail, and SHALL NOT overwrite the catalogue.

#### Scenario: A entry with no key fails the run

- **WHEN** the generated prose contains a entry that carries no capability key
- **THEN** the run reports that entry and exits non-zero, and the catalogue on disk is unchanged

#### Scenario: A entry's keys resolve

- **WHEN** the catalogue is generated
- **THEN** every capability key a entry carries exists in the index and its spec is classified user-visible

### Requirement: The catalogue is a selection, not an inventory

The catalogue SHALL publish a capability only when a highlight in the project's shortlist names the spec that
capability belongs to. Every highlight SHALL be represented by at least one entry. A run that would leave a
highlight unreached SHALL report it, fail, and SHALL NOT overwrite the catalogue. The catalogue therefore states
what is worth reading about the app rather than everything the app can do: a capability that no highlight names is
deliberately absent, however user-visible it is.

#### Scenario: A capability outside the shortlist is absent

- **WHEN** a spec classified user-visible is named by no highlight
- **THEN** no entry in the catalogue cites any of its capabilities

#### Scenario: An unreached highlight fails the run

- **WHEN** no entry cites any capability of a highlight
- **THEN** the run reports that highlight, exits non-zero, and the catalogue on disk is unchanged

#### Scenario: A spec serves two selling points

- **WHEN** two highlights name the same spec
- **THEN** an entry of either highlight may cite that spec's capabilities, and the run accepts both

#### Scenario: Nothing is a selling point

- **WHEN** the shortlist names no highlight at all
- **THEN** the run reports that there is nothing to publish and writes no catalogue

#### Scenario: A selling point whose specs have all left the shipped set

- **WHEN** every spec a highlight names has left the shipped set
- **THEN** the run reports that highlight as a stale shortlist entry and does not require it, the same way a stale
  classification entry is reported without failing

### Requirement: An entry is one selling point

Every capability an entry cites SHALL belong to a single highlight, so that one entry sells one thing. A run that
would publish an entry whose cited capabilities span two highlights SHALL name that entry, fail, and SHALL NOT
overwrite the catalogue. One highlight MAY be spread over more than one entry.

#### Scenario: An entry spanning two selling points fails the run

- **WHEN** an entry cites capabilities of two different highlights
- **THEN** the run names the entry and exits non-zero, and the catalogue on disk is unchanged

#### Scenario: An entry of one selling point passes

- **WHEN** every capability an entry cites belongs to one highlight
- **THEN** the run accepts the entry

#### Scenario: A selling point may take two entries

- **WHEN** a highlight's capabilities are written as two entries
- **THEN** the run accepts both, and the highlight counts as reached

### Requirement: A paragraph stays short

An entry's paragraph, excluding its bold keyword, SHALL be at most 320 characters. A run that would publish a
longer paragraph SHALL name the entry and its length, fail, and SHALL NOT overwrite the catalogue, so that an entry
can be read in one glance and the catalogue reads as a set of selling points rather than as a specification.

#### Scenario: An over-long paragraph fails the run

- **WHEN** an entry's paragraph is longer than 320 characters
- **THEN** the run names the entry and its length and exits non-zero

#### Scenario: A short paragraph passes

- **WHEN** every entry's paragraph is within 320 characters
- **THEN** the run accepts the catalogue

#### Scenario: The keyword does not count towards the length

- **WHEN** an entry's length is measured
- **THEN** only the paragraph is measured, so a longer keyword cannot push an entry over the bound

### Requirement: A stated figure appears in a spec the entry cites

A number or figure stated in a entry SHALL appear in at least one spec that entry cites. A run that would publish
a entry stating a figure that appears in none of its cited specs SHALL report the entry and the figure, fail, and
SHALL NOT overwrite the catalogue.

#### Scenario: An invented number fails the run

- **WHEN** a generated entry states a figure that appears in none of the specs the entry cites
- **THEN** the run reports the entry and the figure and exits non-zero

#### Scenario: A figure taken from a cited spec passes

- **WHEN** a generated entry states a figure that appears in a spec the entry cites
- **THEN** the run accepts the entry

#### Scenario: Marketing phrasing does not change the check

- **WHEN** a entry is reworded so that its meaning and its cited specs are unchanged
- **THEN** the check accepts it, so that phrasing may change without changing the facts

### Requirement: Prose the run cannot verify is reported for review

The run SHALL report, without failing, every content word in a generated entry that appears in none of the specs that
entry cites, so that a claim the gate cannot settle is visible to a reviewer instead of silent. A content word is a
word of four or more characters that is not in the run's stopword list. The report SHALL name the entry and the word.

#### Scenario: A word the cited specs do not contain is reported

- **WHEN** a entry contains a content word that appears in none of the specs it cites
- **THEN** the run names that entry and that word in its report and still exits successfully

#### Scenario: A entry whose words are all sourced is not reported

- **WHEN** every content word of a entry appears in a spec that entry cites
- **THEN** the report names neither the entry nor any of its words

#### Scenario: The report does not stand in for the figure gate

- **WHEN** a entry states a figure that appears in none of its cited specs
- **THEN** the run fails rather than reporting, whatever the word report says

### Requirement: Only dirty areas are regenerated

A run SHALL regenerate an area only when the set of specs belonging to it changed, when the requirement text of one of
its capabilities changed, or when the phrasing instruction version changed. Every other area SHALL be reproduced
without invoking a language model.

#### Scenario: A run with nothing changed invokes no model

- **WHEN** the catalogue is generated twice with no spec change, no classification change and no instruction change
  in between
- **THEN** the second run invokes no language model at all and produces a byte-identical catalogue

#### Scenario: One area is dirty

- **WHEN** one requirement text of one spec changes and the catalogue is regenerated
- **THEN** exactly the area containing that spec is regenerated and every other area is reproduced unchanged

#### Scenario: A classification change dirties two areas

- **WHEN** a spec id is moved from one feature area to another
- **THEN** both the area it left and the area it joined are regenerated

#### Scenario: An instruction change dirties every area

- **WHEN** the phrasing instruction version changes and nothing else does
- **THEN** every area is regenerated

#### Scenario: A change to a spec outside an area's members does not dirty it

- **WHEN** one spec changes and an unrelated area is regenerated
- **THEN** that area's text is reproduced from its cache

### Requirement: The catalogue is a living document, regenerated rather than appended

The catalogue SHALL be written as a whole on each run, replacing any previous content, and SHALL NOT accumulate
previous runs. With identical inputs, two runs SHALL produce byte-identical output, including the order of headings
and entries.

#### Scenario: Two runs produce the same file

- **WHEN** the catalogue is generated twice from the same inputs
- **THEN** the two files are byte-identical

#### Scenario: Regeneration drops a capability that is gone

- **WHEN** a capability stops being user-visible, or its spec leaves the shipped set
- **THEN** the regenerated catalogue no longer presents it and does not retain it from the earlier run
