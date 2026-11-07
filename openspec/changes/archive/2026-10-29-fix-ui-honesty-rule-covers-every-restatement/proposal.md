# Proposal — fix-ui-honesty-rule-covers-every-restatement

## Why

**The condition, verified in the tree at base `ffdd080a` (2026-10-29).** `guidelines/UI.md:1383` opens
"Keeping this document honest", and before this change its two bullets read (`sed -n '1383,1387p'
guidelines/UI.md`):

> - Specs are the contract; this document is the condensed knowledge base.
> - When a spec requirement changes labels, styling, or hierarchy, update the parity table (§1) and glyph
>   table (§2) here at the same time.

The obligation is keyed on *labels, styling, hierarchy* and enumerates *two* tables. The document's other
restatements of spec'd or code-derived values carry no obligation: the phone/AA reroute-policy bullet
(`:969`–`:974`, now naming `NavigationEngine`'s constants and `ReroutePolicyGuidelineParityTest`) and §8's
overlay minimums (`:843`–`:847`, 24sp / 64dp / 28sp / 32sp …) are the two the item names, and the document
names ten case identifiers in six sections that the rule never mentions
(`grep -no '[A-Za-z][A-Za-z0-9]*Test[A-Za-z0-9]*' guidelines/UI.md`). Measured 2026-10-13 and still true
here: `tools/look.sh 'Keeping this document honest|parity table \(§1\)|glyph table \(§2\)' --files` reports
`17 matches in 15 files`, and no case in `tools/*-cases.sh` or in the test sources asserts the rule.

**Root cause, one sentence.** The honesty rule states its obligation as a two-item enumeration of tables
and a list of change kinds, so a number restated in any other section of the document has no stated update
obligation and no case that holds it to its source — the §8 reroute-policy bullet kept the retired
50 m / 10 s through the change that moved the policy because nothing was owed outside §1 and §2.

**Red test** (`evidence/red-probe.sh`, its first run on HEAD in `evidence/red-on-head.xml`,
`evidence/red-on-head.log`, `timestamp="2026-10-29T11:00:30Z"`): the probe reads the honesty section and runs
three cases; on HEAD it reports `tests="3" failures="1"`:

```
FAIL ui-honesty#a-restatement-names-its-guard — the honesty section does not state: [restates]
  [name the case that holds] leaves these guarded restatements undeclared: CompassNeedleStrokeTest
  DaylightPaletteContrastTest FavoritesScreenTest FavoritesViewModelTest GermanTranslationCompletenessTest
  MapLayerBandsTest NavigationStateOverlayComposeTest ReroutePolicyGuidelineParityTest
  RoutePanelActionBandScaleTest StylesheetHexColorCaseTest
```

The case fails for two independent reasons that are the defect itself: the rule carries no term for a
restated value and no obligation to name the case that guards one, and the ten cases the document already
names in its body are undeclared by the section that is supposed to keep the document honest. The probes's
other two cases (`a-listed-guard-is-a-case-in-the-tree`, `a-listed-section-resolves`) pass vacuously on
HEAD because the section names no case and no section; they constrain the declared set after the edit.

**Surface.** `tools/look.sh 'Keeping this document honest|parity table \(§1\)|glyph table \(§2\)' --files`
reports `17 matches in 15 files` on the base tree and `22 matches in 18 files` on this change's tree, the
five extra matches being this change's own probe and its two XMLs. No shipped Gradle case and no
`tools/*-cases.sh` case asserts the honesty rule; the two files that carry the phrase are
`guidelines/UI.md` (the rule) and `guidelines/Backlog.md` §14 (its own, differently-scoped honesty list),
and `AGENTS.md:44` routes "whether `UI.md` is still accurate" to the section this change edits.

surface: 1 guideline section edited, 1 evidence probe added with 3 cases, 0 Gradle cases added, 0 cases rewritten, 0 fixtures realigned — `tools/look.sh 'Keeping this document honest|parity table \(§1\)|glyph table \(§2\)' --files` (`17 matches in 15 files` on the base tree, `22 matches in 18 files` on this change's tree).

**Owning capability and guideline section.** The document-organization rules are owned by
`openspec/specs/documentation-ownership/spec.md` — *A rule has one normative home*, which makes a spec the
contract and this document its condensed restatement. The rule itself is
`guidelines/UI.md` "Keeping this document honest", the section `AGENTS.md:44` routes an accuracy question to.

**Why no capability delta.** The change states no system behaviour and ships no new shipped test case: its
three cases are an evidence probe over the document. A requirement added to `documentation-ownership` would
therefore carry scenarios with no shipped case to name, which `openspec/config.yaml`'s specs rule forbids
("every scenario names the one test case that exercises it"). `.openspec.yaml` records `skip_specs: true`,
and the change is a guideline correction under the capability that already owns the document set.

## What Changes

- **`guidelines/UI.md` "Keeping this document honest"** — the second bullet is widened from "labels, styling,
  or hierarchy" to every value a spec requirement or a code constant owns, numbers included, and from the two
  tables to every restatement the section lists. A third bullet states the guard obligation: a section that
  restates such a value SHALL name the case that holds the two together, or the requirement that owns it, and
  a restatement with no guard is a defect fixed or removed in the change that finds it. The bullet then
  declares the six restatement sections this document owns and the ten cases that guard them — sections 1,
  7a, 8, 8a, 10 and 11 — each section cited by number **and** heading text.
- **`evidence/red-probe.sh`** — the change's three cases over that section, read without a build, a device or
  a network.

No production code, no test source, no fixture and no capability spec changes; no Gradle task or device run
is owed.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- None — `.openspec.yaml` carries `skip_specs: true` for the reason stated above.

## Impact

- `guidelines/UI.md` — one section ("Keeping this document honest"), two bullets edited and one bullet plus a
  six-entry list added.
- `openspec/changes/fix-ui-honesty-rule-covers-every-restatement/**` — the planning artifacts and the evidence
  probe.
- No module, resource, API, dependency, Gradle file or test source is touched.
- **Rollback** is the section's two previous bullets: nothing outside the document reads the rule, so reverting
  it restores the prior text with no other artefact to undo.
