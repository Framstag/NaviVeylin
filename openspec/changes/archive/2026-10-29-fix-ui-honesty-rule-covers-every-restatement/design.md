# Design — fix-ui-honesty-rule-covers-every-restatement

## Root cause

`guidelines/UI.md` "Keeping this document honest" (`:1383`) states its obligation as a list of change kinds
("labels, styling, or hierarchy") plus an enumeration of the two tables that carry them (§1's parity table,
§2's glyph table). A section that restates a value from a spec requirement or a code constant — the §8
reroute-policy bullet (`:969`), §8's overlay minimums (`:843`), §8a's palette values (`:1084`) — is not one of
those kinds and is not one of those tables, so the document owes nothing when the value it copies moves, and
no case is named that would fail when the copy drifts. The ten `*Test` identifiers the document already names
in its body are the guards that were added one change at a time, each by an author's memory rather than by
this rule.

## The single fix

One section, three bullets:

1. "Specs are the contract; this document is the condensed knowledge base." — unchanged.
2. The update obligation, widened: a change to what a spec requirement or a code constant owns — a label, a
   style, a hierarchy **or a number** — updates *every restatement* here in the same change, the two tables
   and each section the next bullet lists; and "restatement" is defined (a section writing down a value
   another artefact already owns, that artefact remaining the normative home).
3. The guard obligation, new: a section that restates such a value SHALL name the case that holds the two
   together, or the requirement that owns it; a restatement with no guard is a defect and is fixed or
   removed in the change that finds it. Then the declared set — the six sections this document restates in
   and the ten cases that guard them — each section cited by number **and** heading text so the citation
   resolves in a document that numbers sub-sections (`7a`, `8a`).

The declared set is the ten case identifiers the document already names, collected by
`grep -no '[A-Za-z][A-Za-z0-9]*Test[A-Za-z0-9]*' guidelines/UI.md` in six sections (sections 1, 7a, 8, 8a,
10 and 11). The fix changes the rule's text and adds no new guard: every case in the list exists and passes today,
which is why the red case is the *absence* of the declaration rather than a drifting number — the item's
own instance (the §187 bullet) was repaired by `fix-ui-reroute-numbers`, and this change is the rule that
would have obliged it without a purpose-built case.

## Blast radius

- **`guidelines/UI.md` only.** The section is the last in the document, and nothing else reads it: no
  `tools/*-cases.sh` case, no test source and no script asserts its text (`tools/look.sh` above). No section
  number, route row, parity-table row or glyph-table row changes, so `AGENTS.md` and
  `tools/check-doc-routes.sh` need no edit — the new `§N "heading"` citations resolve under the check's
  number-plus-heading rule.
- **The change's markdown pulls the documentation suites** (`tools/loop/loop.conf`, `LOOP_GATE_SUITES`:
  `*.md` → `check-doc-routes.sh`, its self-test and its mutation list, `check-todo-references.sh`;
  `openspec/*` → `check-doc-routes.sh`, `check-openspec-hygiene.sh`, `check-openspec-hygiene-selftest.sh`,
  `gen-feature-list.sh --check-classification`). No compiled path is touched, so no Gradle task is owed and
  the structural check stands in for a build (`guidelines/Build.md` §2, the scope block).
- **The probe reads files only** — the document, the test-source directories and nothing else. It needs no
  build, no device and no network, which is what lets the three cases run beside the documentation suites.

## Alternatives eliminated

- **Narrow the rule instead** — keep the two-table enumeration and state that a restated number is "the
  reader's convenience" with no obligation. Eliminated: the item's instance is a number that went stale for
  a whole change cycle while the document was the artefact a reader took as current, and the document
  already names ten guards; a rule that declares the guards unnecessary deletes the only mechanism that
  caught the drift (`ReroutePolicyGuidelineParityTest`) and leaves the §8 overlay minimums with no owner.
- **Add a requirement to `documentation-ownership`** — state the guard obligation in the spec instead of the
  guideline. Eliminated: the delta ships no shipped test case, so the requirement's scenarios would name no
  case, which `openspec/config.yaml`'s specs rule forbids; the obligation is about this document's own text,
  which the guideline owns, and the spec's *A rule has one normative home* already makes the spec the
  contract this document restates.
- **Make the probe a permanent `tools/check-*.sh` gate step** — turn the three cases into a shell check with
  its self-test, mutation list and pipeline wiring. Eliminated for this change: it is a new gate step (the
  loop's own `gate#theMapCoversEveryPipelineStep` and `aPathACheckReadsOwesThatStepsCommandSet` cases pin
  that surface), its own change with its own red case, and the rule this change states would first have to
  exist for the check to enforce. The probe is retained in the change's `evidence/` and cited as the rule's
  guard until that change is filed.

## Risk

Low, and bounded to prose: a reader who follows the rule now sees six sections and ten cases instead of two
tables, and the list is wrong in exactly one way — a guard renamed or removed after this change. That is what
the probe's second and third cases read, and `tools/check-doc-routes.sh` still resolves every section
citation the list makes.

## Verification

- **Host, no device**: `evidence/red-probe.sh` — three cases, `tests="3" failures="1"` on HEAD
  (`evidence/red-on-head.xml`) and `tests="3" failures="0"` after the edit (`evidence/green-after-fix.xml`).
- **Structural**: `bash tools/check-doc-routes.sh` — every section citation the new list makes resolves
  (`all 155 section references resolve`).
- **The project's gate** once, over the documentation suites the pathspec owes (`./tools/loop/gate.sh`).
- No on-device check is owed: no surface, renderer, log line or coordinate-bearing output changes.
