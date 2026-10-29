# Evidence — fix-ui-honesty-rule-covers-every-restatement

## The red case and the green run

`evidence/red-probe.sh` reads `guidelines/UI.md` "Keeping this document honest" — no build, no device, no
network — and runs three cases. Its first run, on the tree before the guideline edit, reported
`tests="3" failures="1"` (`evidence/red-on-head.xml`, `evidence/red-on-head.log`,
`timestamp="2026-10-29T11:00:30Z"`):

```
FAIL ui-honesty#a-restatement-names-its-guard — the honesty section does not state: [restates]
  [name the case that holds] leaves these guarded restatements undeclared: CompassNeedleStrokeTest
  DaylightPaletteContrastTest FavoritesScreenTest FavoritesViewModelTest GermanTranslationCompletenessTest
  MapLayerBandsTest NavigationStateOverlayComposeTest ReroutePolicyGuidelineParityTest
  RoutePanelActionBandScaleTest StylesheetHexColorCaseTest
```

The same probe over the edited document reports `tests="3" failures="0"`
(`evidence/green-after-fix.xml`, `evidence/green-after-fix.log`, `timestamp="2026-10-29T11:00:31Z"`):

```
PASS ui-honesty#a-restatement-names-its-guard
PASS ui-honesty#a-listed-guard-is-a-case-in-the-tree
PASS ui-honesty#a-listed-section-resolves
```

The red run was re-taken with the final probe (the section-resolution case was tightened while writing it, and
a red taken with a discarded probe would prove nothing): the guideline section was restored to its two
pre-change bullets, the probe run, and the new text restored from a copy in the same step.

## Structural check

`bash tools/check-doc-routes.sh` (the check that stands in for the build on a change that touches no compiled
file): `check-doc-routes: all 155 section references resolve, all 4 skill references resolve`, exit 0. The
section citations the new list makes resolve by number **and** heading text under the same check.

## Measurement block

Quoted verbatim from `tools/loop/measure.sh`, whose whole output is `evidence/measure.txt`:

```
LOOP-MEASURE-SUITE cases=277 cmd=tools/loop-selftest.sh --list
LOOP-MEASURE-GATE seconds=1412 suites=23
LOOP-MEASURE-CHANGE fix-ui-honesty-rule-covers-every-restatement scenarios=0 cases=0 unnamed=0
LOOP-MEASURE-SURFACE surface: 1 guideline section edited, 1 evidence probe added with 3 cases, 0 Gradle cases added, 0 cases rewritten, 0 fixtures realigned — `tools/look.sh 'Keeping this document honest|parity table \(§1\)|glyph table \(§2\)' --files` (`17 matches in 15 files` on the base tree, `22 matches in 18 files` on this change's tree).
```

`scenarios=0 cases=0` is the `skip_specs: true` the change declares: no capability delta, so no scenario is
promised and no shipped case is owed. `LOOP-MEASURE-GATE` was `seconds=-` before the run; the archive reading,
taken with `--change 2026-10-29-fix-ui-honesty-rule-covers-every-restatement`, is
`LOOP-MEASURE-GATE seconds=1412 suites=23`, and the 23 tally files it reads are copied from the gate's directory
into `evidence/gate-tallies/`.

## The gate

`./tools/loop/gate.sh` ran once, from the change root, over the reviewed pathspec — `guidelines/UI.md` plus the
change's archived paths, which the accumulated base diff puts beside a compiled path, so the JVM suite ran as
well as the documentation suites. Its last line, kept verbatim in `evidence/gate.log`, reports **676 tests, 0
failures and 0 errors** over 23 suites, in 1412 s (`LOOP-MEASURE-GATE seconds=1412 suites=23`). The tallies of all
23 suites are in `evidence/gate-tallies/`.

## Review checklist

- **Red case on HEAD, green after** — above; both XMLs are in this directory.
- **Affected cases pass** — no shipped case, prompt, fixture or Gradle file changes, so the only cases that can
  be affected are the documentation suites the gate runs.
- **Evidence pointers** — `bash tools/loop/evidence-check.sh <archived change> --root .` run before and after the
  archive (`evidence/evidence-check.log`, `evidence/evidence-check-archived.log`).
- **No coordinate** — the change writes no log or diagnostics line.
- **Rollback** — restore the section's two previous bullets; nothing outside the document reads the rule.
