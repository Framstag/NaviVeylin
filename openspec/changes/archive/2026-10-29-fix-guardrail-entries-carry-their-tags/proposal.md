# Proposal — fix-guardrail-entries-carry-their-tags

## Why

**The condition, verified in the tree at base `7e1f8ee9` (2026-10-29).** Issue #68 is the record of §40, the
guardrails extracted from `ki_processing_failures.log`, and its discharge paragraph states what completes it:
"every item names the artifact that carries the rule (`guidelines/Build.md`, `guidelines/Design.md`,
`guidelines/UI.md`, `guidelines/MapRendering.md`, `AGENTS.md`, a CI step or a skill) … and the docs tag the rule
with its item number (`§40.N`) so the mapping stays auditable." Measured on that tree,
`grep -rhoE '§40\.[0-9]+' guidelines/*.md AGENTS.md | sort -u | wc -l` prints `54`, and six of the record's 60
entries carry no tag anywhere in the guidelines or `AGENTS.md`: 5, 8, 37, 38, 46, 55. Three of the six (5, 8,
37) the record itself tracks by section in the retired backlog (`tracked: §17`, `§6`, `§14, §15`), so its own
text says their home is not a `§40.N` tag. The other three name an artifact and carry no tag:

| entry | the rule | where the tree states it | tag hits |
|---|---|---|---|
| §40.38 | a harness flake of unknown origin gets an entry with the rerun evidence, not a silent retry | nowhere as a rule — only its worked example, `guidelines/Build.md:910-914` ("the awaited state came from `Dispatchers.Default`, the fix pinned the dispatcher") | `grep -rnF '§40.38' guidelines AGENTS.md` → 0 |
| §40.46 | before an apply/verify pass, check for a concurrent writer; if a peer is mid-edit, quote what is collected and stop | `guidelines/Build.md:158-160`, §2a "Agent iteration protocol" ("run `find <module> -newermt '-10 minutes'` beside the wrapper probe, and if a peer is mid-edit, quote the evidence already collected and stop") | `grep -rnF '§40.46' guidelines AGENTS.md` → 0 |
| §40.55 | stylesheet hex literals are lowercase-only, and a stylesheet defect is not caught by building | `guidelines/MapRendering.md:899-903` ("a stylesheet defect is NOT caught by building — the app must be run once with `adb logcat -s NaviVeylin | grep -i "style error"`") | `grep -rnF '§40.55' guidelines AGENTS.md` → 0 |

**Root cause, one sentence.** Three of §40's entries were carried into the guideline section that owns them
without the record tag the record's own discharge paragraph promises, and in one case (38) the worked example
was carried while the rule it exemplifies was not, so the mapping the record offers a reader cannot be audited
for those entries.

**Red test** (`evidence/red-probe.sh`, its base-tree run captured in `evidence/red-HEAD.txt` and its JUnit XML
`evidence/red-on-head.xml` — `tests="3" failures="3"` — at
2026-10-29T11:57:13Z, reading the base commit's `guidelines/` through `git archive`): the probe requires the
record tag inside the section that owns each rule, and on HEAD reports `tests=3 failures=3`:

```
MISSING §40.38  guidelines/Build.md:586-642 ("## 4d. Attributing a red run") carries no §40.38 tag
MISSING §40.46  guidelines/Build.md:104-174 ("## 2a. Agent iteration protocol") carries no §40.46 tag
MISSING §40.55  guidelines/MapRendering.md:876-914 ("## 15a. Stylesheet load failure") carries no §40.55 tag
tests=3 failures=3
FAIL guardrail-tag#eachCarriedEntryCarriesItsRecordTag — 3 of 3 entries carry no §40.N tag (exit 1)
```

**Surface.** Of §40's 60 entries, 54 are tagged in the guidelines or `AGENTS.md`; the six untagged split into
the three the record tracks by section in the retired backlog (5, 8, 37 — left alone, their own text names
another home) and the three this change carries (38, 46, 55). Two guideline files are edited: `Build.md` gains
one rule and two tags, `MapRendering.md` gains one tag. No Gradle case, spec case or fixture asserts the
citation, so nothing is rewritten.

surface: 2 guideline files edited, 1 rule added, 3 record tags added, 0 Gradle cases added, 0 cases rewritten, 0 fixtures realigned — `grep -rhoE '§40\.[0-9]+' guidelines/*.md AGENTS.md | sort -u | wc -l` (`54` distinct tags before the change), `grep -rnF '§40.38' guidelines AGENTS.md` (`0`), `grep -rnF '§40.46' guidelines AGENTS.md` (`0`), `grep -rnF '§40.55' guidelines AGENTS.md` (`0`).

**Owning capability and guideline sections.** The rule is
`openspec/specs/documentation-ownership/spec.md:390-406` — *A recorded guardrail is stated in the section that
owns it*: the section that owns the concern states the rule "and tagged with the record entry that produced
it". The owning sections are `guidelines/Build.md` §2a "Agent iteration protocol" (the one-builder-per-tree
rule and its edit probe), `guidelines/Build.md` §4d "Attributing a red run" (what a red run is attributed and
recorded as —
its flake line is the tree's own statement that "this tree keeps no flake list"), and
`guidelines/MapRendering.md` §15a "Stylesheet load failure (app side) — never render a rejected style, never
crash", the section `AGENTS.md`'s stylesheet row routes a stylesheet change to. This is a documentation change
that states no new behaviour, so `.openspec.yaml` sets `skip_specs: true` and no capability delta is invented.

## What Changes

- `guidelines/Build.md:158-160` (§2a): append `(§40.46)` to the concurrent-writer clause of the one-builder
  bullet.
- `guidelines/Build.md` §4d: one bullet stating that a flake of unknown origin gets an entry with its rerun
  evidence rather than a silent retry, with the `FavoritesSheetReorderComposeTest` example pointing at §6d and
  the record tag `(§40.38)`.
- `guidelines/MapRendering.md:899`: append `(§40.55)` to the stylesheet-verification bullet.

## Impact

- Two guideline files, five changed lines, plus this change's artifacts and its evidence probe. No source
  file, no test, no build script, no route or section number changes, so `AGENTS.md`,
  `tools/check-doc-routes.sh` and the spec text need no edit.
- The pathspec is markdown only, so the gate owes the documentation suites
  (`tools/check-doc-routes.sh`, `tools/check-todo-references.sh`, `tools/check-openspec-hygiene.sh`,
  `tools/gen-feature-list.sh --check-classification`) and no Gradle task.
