# Glossary — the declared vocabulary

The domain's words are declared once, in `tools/glossary/`, and every row is checked against the tree that
uses it. This document owns the rules of that declaration: what a row is, what it must carry, which spellings
are refused, and how a refusal is measured. It defines no word — the rows do, and `tools/glossary/GLOSSARY.md`
is the generated reading copy. The rationale layer, with the measurements these rules were set from, is
`guidelines/Glossary.why.md`.

## 1. The declaration, and the four numbers a green run prints

`tools/gen-glossary.sh` projects; `tools/check-glossary.sh` reads. A green run is the only census: it prints the
rows declared, the rows anchored, the kinds used and the files scanned, and those four numbers are what a
change quotes when it claims the declaration covers something. Measured on 2026-11-11 over `app/src/main`,
`auto/src/main` and `core/src/main`: **rows 53, anchored 53, kinds attribute/entity/operation/value, files 469**
(the 7 skeleton types the projection emits are part of the 469; the 462 the change's proposal measured on
2026-11-09 was the tree before them).
A row count that moves without a row being written is a defect in the census, not a word that appeared.

The scopes are not declared here. `architecture/structure.tsv` owns where a package sits and which edges are
legal, and its `feature` column is the glossary's scope list, read at run time; a declaration that cannot be
read fails the check closed rather than passing with no scopes.

- **Rule: the vocabulary classifies nothing and names no rule.** It says what a word means and how it is
  spelt. Placement is `architecture/structure.tsv`, rules are `guidelines/`, and a row that restates either is
  a second owner of a fact that already has one.

## 2. A row is one term in one grammatical kind

A row is keyed `(<scope>/)<term>#<kind>`. `kind` is one of `entity`, `value`, `operation`, `attribute`,
`boolean`, `event`, `constant`, and it decides the grammatical role the row must declare a surface in — a
`value` is a type, an `attribute` is a property, an `operation` is a function. `scope` is written only when one
word carries two meanings in two features; two rows of one word then name each other in their `not` fields.

- **Rule: one key is one term in one role, and no two rows share a key.** A noun and a verb are two rows, so a
  refusal can say which role failed.
- **Rule: a row declares one surface per role it occupies, with the forms forbidden in that role.** A forbidden
  form observed in the role it is forbidden in is `drift`, and the finding names the file and the line.
- **Rule: a physical quantity names its unit, and a boolean carries `is`/`has`/`can`.** An attribute carrying a
  quantity and no unit is `unit-missing`; a declared boolean surface with no prefix is `bool-prefix`.

## 3. Every row carries an anchor

An anchor is a `**Case**:` test name a shipped spec declares, or a named rule a guideline carries. A row
carrying neither is refused where it is written, not by a scheduled measurement, because a definition with no
proof behind it is a plausible invention.

- **Rule: an anchor resolves, or the row does not exist.** An anchor text no spec and no document carries is
  `unanchored`, and the finding names the anchor and where it was looked for.
- **Rule: a definition excludes its neighbours, and the exclusion is mutual.** Row A naming row B while B does
  not name A is `lopsided-exclusion`; two rows of one kind that exclude nothing are `twin-term`, because two
  words that discriminate nothing are one word.

## 4. The concept kind is the only global scope

A word common to the whole app has the home kind `concept` — the kind the structure declaration already
defines as depending on nothing of ours. A word whose home is a feature is never declared global, however many
features use it: the count is a measurement, not a meaning.

- **Rule: a global term is homed in `concept`, and a scope names a feature the structure declaration carries.**
  A global row homed in a feature is `unowned-term`, naming the row and its home kind; a scope the `feature`
  column does not carry is `undeclared-context`.
- **Rule: only a coining kind declares a term.** `concept` and `feature/<name>` coin words; `port`,
  `platform/<name>` and `shell` do not. A declared term whose type is declared in a non-coining kind is
  `misplaced-term`, and a vacuous suffix (`Manager`, `Provider`, `Impl`, `Util`, …) is `bad-name` in a coining
  kind only — a port role such as `LocationProvider` is a name its kind demands.

## 5. The refusals

Fourteen words, fixed in `tools/check-glossary.sh` the way `tools/check-change-readiness.sh` fixes its own, so
a second spelling of a finding cannot appear: `phantom`, `drift`, `undecided`, `overruled`, `kind-mismatch`,
`unit-missing`, `bool-prefix`, `unanchored`, `twin-term`, `lopsided-exclusion`, `unowned-term`,
`misplaced-term`, `bad-name`, `undeclared-context`.

- **Rule: a refusal is one line naming the row's key, the file and line a surface was found on, the artefact
  the finding was decided against, and exactly one refusal word.** Two findings on one line is a line a reader
  cannot file.
- **Rule: the declaration settles a split spelling, and the code settles a majority.** A term observed with two
  spellings and no declared canonical form is `undecided`, with both counts on the line; a declared form that a
  ten-to-one majority of the code contradicts is `overruled`. Where a majority exists the code wins, and a row
  that agrees with it passes.

## 6. The projections

The declaration reaches the code or it lives only in prose. `tools/gen-glossary.sh` writes a row's definition
into the KDoc of the type that declares it, and emits a skeleton type for a `concept` row whose type no file
declares yet.

- **Rule: the projection is additive.** A type that already carries a KDoc block is reported as not projected
  and never rewritten; a skeleton is written only when its file is absent. Reverting a hand-written comment is
  not a generator's decision.
- **Rule: `--check` fails instead of writing.** A projection whose text differs from the declaration is
  reported naming the type and the row, exit is non-zero, and no file is touched. No Gradle task runs the
  generator: a build that writes production sources makes every incremental build a possible working-tree
  change.

## 7. The drift row and its bound

`vocabulary-drift` in `tools/sensors/sensors.json` measures the refusals the check prints. Its fingerprint is
the refusal word, never the term: a rename that leaves fifty spellings behind is one class with fifty terms in
it, not fifty issues. It runs `check-glossary.sh --json` and groups what that printed, so the row and the check
can never disagree about what a finding is.

- **Rule: the bound and the identity are the row's, and the class escalates to a person.** `threshold: 0`,
  `consecutive: 2`, `maxFilings: 2`, `fingerprint.fields: ["refusal"]`; the first scheduled run reports the
  count and writes nothing; past the registry's three recurrences the class is handed to a person and holds no
  eligibility for automated work.
