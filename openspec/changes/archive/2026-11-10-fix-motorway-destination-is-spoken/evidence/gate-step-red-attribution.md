# The gate's one red step is attributed to work this change does not touch

The gate ran once, from the change root against base `95efb9036e86b0eb72930085d00027e9949a3273`.
Its verdict line and the failing step's whole console are copied verbatim into
`evidence/gate-classification-red.txt` — the gate runner's own tally `tests=989 failures=0 errors=1` is not retained as a JUnit XML (it is the runner's aggregate verdict line), and the one error it counts is the step
`bash tools/gen-feature-list.sh --check-classification`. Every other step of that run is green, including
the build (`evidence/gate-classification-red.txt`, and the gradle step's tally
`evidence/gate-step-gradle.xml`: `BUILD SUCCESSFUL`).

So one step is red, and it names seven ids. None is a path this change writes.

## The seven ids, each measured

**Four unclassified ids — two other open changes' deltas.** The step's own message attributes them:
`architecture-test-suite` and `package-structure` are *introduced by the open change
package-structure-as-five-kinds*, `domain-vocabulary` and `vocabulary-projection` *by the open change
domain-vocabulary-is-declared*. Measured in this tree:

- the four deltas exist only under those two changes —
  `openspec/changes/package-structure-as-five-kinds/specs/architecture-test-suite/`,
  `openspec/changes/package-structure-as-five-kinds/specs/package-structure/`,
  `openspec/changes/domain-vocabulary-is-declared/specs/domain-vocabulary/`,
  `openspec/changes/domain-vocabulary-is-declared/specs/vocabulary-projection/` (all four directories present);
- `tools/feature-list/specs.json` carries **0** rows for the four ids
  (`tools/look.sh 'architecture-test-suite|domain-vocabulary|package-structure|vocabulary-projection' --path tools/feature-list` → 0 matches);
- the two directories were created `2026-11-10 05:06` and `2026-11-10 05:08` (`stat -c '%y %n'`), before
  this item's red run on the base at `2026-11-10T11:30:00.572Z`;
- each change owes its row *before it archives* — exactly what the step says. Neither is this change.

**Three stale entries — keys whose spec directory has never appeared here.** The keys live at
`tools/feature-list/specs.json:168` (`search-name-matching`), `tools/feature-list/specs.json:171`
(`search-result-row-icon`) and `tools/feature-list/specs.json:182` (`start-map-selection`), while
`openspec/specs/search-name-matching`, `openspec/specs/search-result-row-icon` and
`openspec/specs/start-map-selection` do not exist (`ls -d` on the three → not found). Nothing this change
wrote removes or adds either side.

**This change adds no capability id.** Its delta joins the capability that already ships:
`openspec/specs/voice-guidance/` exists, and the delta sits at
`specs/voice-guidance/spec.md` inside this change. The unclassified list does not contain `voice-guidance`.

## What this change writes, from its own plan

`proposal.md` ("What Changes") and `tasks.md` — the roster: two Kotlin production files
(`app/src/main/java/com/naviveylin/navigation/VoiceGuidance.kt`, `NavigationEngine.kt`), two string
resources (`app/src/main/res/values/strings.xml`, `values-de/strings.xml`), two test files, and this
change's own `openspec/` directory. None of them is read by the classification gate, which reads
`openspec/specs/`, the open changes' delta specs and `tools/feature-list/`.

## Red before this work, verbatim copies in `evidence/red-before-classification-gate.txt`

- item 741's gate, 2026-11-02: same change root, same step failing — `errors=1 … failing: bash
  tools/gen-feature-list.sh --check-classification`, with the file's own note that the error is
  "another open change's debt";
- item 721's copy of the step's output: the same three stale ids already reported then;
- item 837's gate, earlier on 2026-11-10 (red test 08:24:52Z vs this item's 11:30:00.572Z): same step
  failing — also copied there, with its source path inside the tree.

Red after this work follows from the same measurement: this change's roster touches none of the gate's
inputs, so every id the step reports is in the tree exactly as it was before the change existed.

## The steps that read this change's paths are green

- The build: `evidence/gate-step-gradle.xml` — `BUILD SUCCESSFUL`, `failures="0" errors="0"`.
- The change's own class after the fix: `evidence/green-NavigationEngineVoiceGuidanceTest.xml`
  (`tests="13" failures="0" errors="0"`, 2026-11-10T11:33:26.455Z) and
  `evidence/green-VoiceGuidanceTest.xml` (`tests="4" failures="0" errors="0"`, same second);
- The red case on the base: `evidence/red-on-HEAD.xml`
  (`tests="13" failures="1" errors="0"`, 2026-11-10T11:30:00.572Z) — `aMotorwayChangeNamesItsDestination`
  failed with `expected:<[… towards München]> but was:<[… the right]>`, the case this change fixes.

So the red step reads a classification file and two other changes' directories; this change reads
neither. Writing four rows owed by two other open changes and clearing three entries that predate the
base is not this change's claim. The step is waived and named in this item's report, not closed over in
silence.
