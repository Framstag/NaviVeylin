# Feature-List Guidelines — the generated catalogue and release notes

How the user-facing feature catalogue and the release notes are derived from the shipped specs, what may keep
them honest, and why nothing in them is hand-written.

This document owns the rules and the measurements that proved them. The facts — where the tool lives, how to run
it — are `AGENTS.md` "Documentation Map"; the behaviour contract is the capability specs
`openspec/specs/spec-feature-index`, `feature-list-generation` and `release-notes-generation`.

**Maintenance rule** — when a change supersedes a convention here, update this document in the same change. A
rule in this document names the case that enforces it; a rule with no such case does not belong here.

## 1. What the two documents are

`FEATURES.md` and `RELEASE-NOTES.md` live at the repository root and are generated, never written.

**An entry is a keyword and a short paragraph, and there is one per selling point.** The catalogue leads with a
short bold keyword a reader scans for (*Works without a connection*, *Know which lane to be in*) followed by one or
two sentences.

```
- **Works without a connection** — Download the maps you choose and keep them on the device, even when the network
  fails. Downloads show progress and report errors instead of silent failure.
  <!-- cap: map-download-ui#Download progress shown -->
  <!-- cap: basemap-ui#Basemap state shown in the map manager -->
```

**The catalogue is a pitch, not an inventory.** The document publishes only what the **shortlist**,
`tools/feature-list/highlights.json`, says is worth reading about — one entry per selling point, and nothing else,
however user-visible a capability may be. That file, not the spec set, decides the document's content: adding a
capability to it is how a feature reaches the page, and leaving one out is how the basics stay off it. A run
**fails** when a selling point reaches no entry, and it reports — without failing — a selling point whose specs
have all left the shipped set, the same way a stale classification entry is reported.

The target is fifteen to twenty-five selling points for a product of this size. If the shortlist grows towards one
entry per capability, the document is becoming a specification again.

Cases: `shortlist: a capability outside the shortlist fails the run`, `gates: an unreached selling point fails the
run`, `shortlist: an entry spanning two selling points fails the run`, `catalogue: a shortlisted-away capability is
not published`.

**One entry is one selling point, and it is short.** An entry's capabilities all belong to one highlight, its
keyword is six tokens or fewer, and its paragraph is **at most 320 characters** — one or two sentences, read in one
glance. The run fails above the bound and names the length, which is what stopped the first attempt at this
document from turning into 239 one-line entries, and the second from turning into 19 paragraphs of nine hundred
characters.

Cases: `length: an over-long paragraph fails the run`, `length: a short paragraph passes`,
`gates: a keyword longer than six words fails the run`.

Cases: `gates: an entry with no bold keyword fails the run`, `gates: a keyword longer than six words fails the
run`, `gates: a keyword word the cited specs lack fails the run`, `density: one entry per capability fails the run`,
`density: a merged area passes`, `catalogue: the report states the density it achieved`.

**The two documents themselves:**

- `FEATURES.md` is a **living document**: every run rewrites it whole, its areas are ordered by the area
  catalogue, and with identical inputs two runs produce byte-identical bytes. It must never be edited by hand —
  the next run would silently discard the edit.
- `RELEASE-NOTES.md` is **derived, not accumulated**: one entry per release version, replaced in place when the
  same version is generated again, entries ordered newest first. A version whose changes are all internal gets
  no entry and a verdict saying so.
- Neither document carries a date or time. A "generated on" line would make the byte-identity rule unsatisfiable
  and would turn every run into a diff; where a document must be anchored in time, the release **version** is
  the anchor, and it is an input.

Cases: `regeneration: two runs produce the same file`, `catalogue: no generation timestamp`,
`notes: re-running a version replaces its entry byte for byte`.

## 2. The shipped set is `openspec/specs/`

A capability is published when, and only when, a change that introduces it has been archived: archiving moves
its capability specs into `openspec/specs/`. That directory is therefore the product's shipped surface, and the
catalogue needs no "is it released yet" filter. An in-flight change contributes nothing.

Cases: `keys: an in-flight change contributes no key`.

Consequence for a change author: **archiving a capability is what publishes it.** A capability spec left in a
change directory is invisible to the catalogue, and `TODO.md`'s unimplemented feature rows are not published at
all.

## 3. One capability key identifies one requirement

The index derives one key per requirement of each shipped spec, written `<spec-id>#<Requirement name>`. That key
is the unit of everything downstream: the delta, the coverage check, the source marker on every bullet, and the
rename detection.

The delta compares **requirement text**, never spec bytes. A spec's `## Purpose` may be reworded, its formatting
changed, or a scenario rewritten without the catalogue being regenerated: only a change to a requirement's own
text counts.

Cases: `delta: a prose-only edit is not a change`, `delta: a requirement text edit is a change`,
`delta: an added requirement is an addition`.

## 4. The classification is the only human judgement, and an unclassified spec stops the run

`tools/feature-list/specs.json` names every shipped spec id exactly once, with the feature area it belongs to,
whether it is user-visible, and the surfaces it is available on (`phone`, `car`, or neither for something that
is not a surface at all, which is legal only for an internal spec). `tools/feature-list/areas.json` holds the
area catalogue: id, heading, order, and which single area is the car platform's own.

Three things a reader must know about the classification:

- **A shipped spec id that neither file names fails the whole run and writes nothing.** The failure names the
  id, and the fix is one line. Publishing an unclassified capability, or silently dropping one, are both worse
  than a red run.
- **A stale entry — an id the classification names that is no longer shipped — is reported without failing.**
  It is not demanded of the catalogue either: coverage is measured over the specs that are shipped.
- **`userVisible` is a property of the spec, not of each requirement.** A user-visible spec may contain a
  requirement that describes plumbing. What the catalogue owes is that no user-visible *spec* is dropped
  silently, not that every one of its requirements reaches a bullet; which of them a bullet mentions is the
  phrasing step's editorial call.

Cases: `gate: an unclassified shipped spec id fails the run`, `gate: an unclassified spec id writes no document`,
`gate: a stale classification entry does not fail the run`, `gate: a user-visible spec with no renderable surface
fails`, `gate: not exactly one car-only area fails the run`, `gates: a user-visible spec no bullet reaches fails
the run`.

**The rule for a change author:** a change that archives a capability spec adds its classification line in the
same change. `.pi/skills/feature-list` and the tool both tell you the id when it is missing.

## 5. Car support is a tag; the car platform has one area

An area states the surfaces its capabilities are available on, taken from the classification. A capability
available in the car appears once, under its own feature area, and is not repeated in a car-only section.
`In Your Car` exists only for capabilities whose *subject* is the car platform — how the app appears, starts and
behaves in the car — not for every capability that happens to run there.

Consequence: `auto-search` belongs under *Search & Destinations*, not under *In Your Car*; `android-automotive-os`
belongs under *In Your Car*, not under *Settings*.

Cases: `catalogue: a dual-surface capability appears once`, `catalogue: the car-only capability of a feature
area stays under that area`, `catalogue: it is not moved into the car-only area`.

## 6. The prose gates, not a reviewer's memory

Phrasing is the only part of the pipeline a language model touches, and it is constrained by gates that fail the
run and write nothing. `tools/feature-list/gate.awk` holds them; it can be read and run on its own.

| gate | what it refuses | what it only reports |
|---|---|---|
| source marker | a bullet carrying no key; a key whose spec does not exist or is not user-visible | — |
| coverage | a user-visible spec that no bullet reaches | — |
| figures | a bullet stating a figure that appears in none of the specs it cites | — |
| words | — | every content word of a bullet that appears in none of the specs it cites |

A source marker holds **one key per line**, repeated per key:

```
- **Works without a connection** — Download the maps you need and keep them on the device.
  <!-- cap: basemap-download#Basemap archive downloaded -->
  <!-- cap: basemap-ui#Basemap state shown in the map manager -->
```

One key per line, not a comma-separated list: requirement names in this project contain commas, so a grouped
list could not be told apart from a name. An entry may wrap its paragraph over several lines; the gate reads the
whole paragraph, its keyword and its markers as one entry.

An entry whose subject has **left the shipped specs** — a removal in a release note — is held to the marker and
the keyword-shape rules but not to the vocabulary of the current specs, because the specs no longer contain what
it names; its source is the baseline snapshot. Otherwise a removal could never be written down.

The word report is deliberately not a gate. It answers "is there a claim here the run cannot settle?" — a
reviewer reads it; it must not fail a run, because the phrasing of a true statement legitimately introduces words
the specs do not use. The figure gate is the opposite: a figure that no cited spec contains is a false claim, and
a bullet of that kind is a defect, not a judgement call.

Cases: `gates: the keyless bullet is named`, `gates: an invented figure fails the run`, `gates: the unsourced word
is named`, `gates: the report does not stand in for the figure gate`, `gates: a fully sourced bullet is not
reported`.

## 7. Only dirty areas are regenerated, and the prompt is the cache version

An area is regenerated when the set of specs belonging to it changed, when the text of one of its capabilities'
requirements changed, when a member's classification changed, or when the prompt changed. Everything else is
reproduced from `tools/feature-list/cache/`. A run with nothing to do invokes no model at all.

The **prompt digest** is a digest of the files under `tools/feature-list/prompts/`. Editing an instruction file
therefore invalidates every area's cached prose, which is the intended effect of a tone change and needs no
hand-bumped version number.

Cases: `regeneration: nothing changed means no dirty area`, `regeneration: an instruction change dirties every
area`, `delta: a prose-only edit does not dirty an area`.

## 8. The version is an input

`gen-feature-list.sh --release <versionName>` takes the version; nothing inside it discovers the version.

The build's version-state file (`app/release-version.properties`) is **gitignored and machine-local**, so a
fresh checkout, CI and another workstation have none. Reading it would make the notes ungeneratable exactly where
they are most wanted. The version's format (`<yyyy>-<MM>-<dd>-<N>`) is also **not lexicographically ordered** once
`N >= 10` — `-1 < -10 < -2` as strings — so the baseline snapshot is selected by comparing the four components
numerically.

Cases: `version: a run without a version fails with a usage error`, `version: the version-state file is never
read`, `snapshot: versions order as the release format orders them (-1 < -10 < -2)`.

## 9. What a change owes when it touches this

- Archiving a capability spec: add its classification line (`specs.json`) in that same change.
- Renaming a requirement: nothing — the run pairs the vanished key with its replacement and reports a change,
  not a removal. A rename that the pairing cannot match is reported for review rather than published.
- Changing a phrasing instruction: nothing — the prompt digest invalidates the affected prose. Check the
  regenerated areas before committing, since the run will invoke a model for every area.
- Adding a feature area: add it to `areas.json` with an order, then classify at least one spec into it; an area
  with no user-visible capability renders no heading, which is also how an internal area stays invisible.
- Publishing a capability in the catalogue: name its spec in a highlight in `highlights.json`. Nothing else puts it
  on the page, and a selling point that reaches no entry fails the run — so adding a highlight and adding the
  classification line for a new spec are the two steps a change owes when what it ships is worth telling a user
  about.
- A spec leaving the shipped set: nothing. Its highlight is reported as stale and no longer required, and its
  capabilities are not published as changes.
- Never edit `FEATURES.md` or `RELEASE-NOTES.md` by hand.
