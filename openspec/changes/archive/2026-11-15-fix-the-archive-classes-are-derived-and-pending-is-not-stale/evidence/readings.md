# The archive's classes are derived on read, and the two stale states are reported apart

Readings from the tree at `929b9e8f` plus this change directory, 2026-11-15. Nothing was run, built or mutated for
this change. The archive census below is a reading of the machine-local `.pi/issue-loop/runs/` that the shipped commit
recorded; that directory is gitignored and is **not** present in this worktree, so the figures are quoted from the two
places the shipped change wrote them and cannot be re-derived here.

## The archive census (issue #922)

The figures, as `guidelines/Backlog.recipes.md:1628`–`1643` records them (measured 2026-11-15), and as the shipped
commit `76588853` states them in its own message:

- **158** archived states, **511** item records, **179** of them `blocked` or `cut` — the only two outcomes a class
  can belong to, because a closed, skipped or falsified item is recorded with no class by design.
- **66 decided**: `bound 51 · child-blocked 1 · refused 9 · unmergeable 5`.
- **107 undecidable**, and **6 ended by a review round, not by the item**.
- The line the report prints, verbatim in the recipe: `archive classes: bound 51 · child-blocked 1 · refused 9 ·
  unmergeable 5 — of 179 blocked or cut record(s): 66 decided, 107 undecidable, 6 ended by a review round, not by the
  item`.
- What the analysis pass printed before the repair: `blocked by stop class: bound 27 · child-blocked 1 · refused 9 ·
  unmergeable 5 · undecidable 98`, and beside it `unclassified 119` from `(.stopReason // "unclassified")`.
- Why the two undecidable figures differ (107 and 98): the pass totalled the blocked half only and counted `119` of
the 140 blocked plus 15 of the 39 cut records that name no mechanism as one class of absence; `1eddc98c` corrected the
comment that had said "119 of 179" to the half it came from. A project with no archive prints `archive classes: -`
(`guidelines/Backlog.recipes.md:1643`).

## The classification figures (issue #926)

Measured in this worktree, `bash tools/gen-feature-list.sh --check-classification`, 2026-11-15 — the verbatim output is
`evidence/classification-check.txt`:

    specs read: 191
    classified: 197
    unclassified: 0
    delta spec ids: 2
    stale: 6
    pending (classified ahead of its archive): 0

The two delta ids are this change's own two capabilities, both already classified, so the split is exercised by the
self-test and not by the project's own tree: `pending` is 0 because no open change's capability is unclassified. The
state the two figures exist to separate is the one the shipped change measured (`tools/gen-feature-list.sh:203`:
`stale: 7`, five of them open changes' deltas), and the self-test builds it:
`tools/tests/gen-feature-list-selftest.sh:309`–`331` opens a change introducing `newcap`, classifies it, and asserts
`pending (classified ahead of its archive): 1` with `stale: 0`.

## The behaviour the deltas state

| what the delta requires | where the tree does it |
|---|---|
| the archive is totalled by the class the record decides | `tools/loop/report.sh:295`–`321`; `stop_class` in `tools/loop/lib.sh:326` (`:346` the definition) |
| the population is `blocked` and `cut` only | `tools/loop/report.sh:307` (`select(.result == "blocked" or .result == "cut")`) |
| undecidable and round-ended are figures of their own | `tools/loop/report.sh:310`–`317` |
| an archive with no such record prints no class | `tools/loop/report.sh:304`, `:313` |
| the analysis pass reads the same derivation | `.pi/skills/analyse-loop-run/bin/analysis.sh:38` (`bound_class_jq`), `:276`, `:286` |
| `pending` and `stale` are two states | `tools/gen-feature-list.sh:198`–`210` (`:205` pending, `:206` stale) |
| neither is an error | `tools/gen-feature-list.sh:259`–`266` (stderr only; `gate_errors` untouched) |
| the two figures are printed apart | `tools/gen-feature-list.sh:288`–`289` (check), `:438`–`439` (report) |
| the state is stated in the guidelines | `guidelines/FeatureList.md:154`–`159`, `guidelines/Backlog.recipes.md:1628`–`1643` |

## The cases

| case | where |
|---|---|
| `report#theArchivesClassesAreDerivedNotInvented` — registration `tools/loop/tests/loop-cases.sh:378`, dispatch `:1226`, body `:13541`–`13569` | asserts `bound 1`, `refused 1`, `of 4 blocked or cut record(s): 2 decided, 1 undecidable, 1 ended by a review round, not by the item`, no `unclassified`, no `#105`, and `archive classes: -` for a project with no archive |
| `gate: a delta-only classification is reported as pending, not as stale` | `tools/tests/gen-feature-list-selftest.sh:328` |
| `gate: a delta-only classification is not a stale entry` | `tools/tests/gen-feature-list-selftest.sh:329` |
| `gate: the pending entry is named as one` | `tools/tests/gen-feature-list-selftest.sh:330` |
| `gate: a pending entry is not reported as stale` | `tools/tests/gen-feature-list-selftest.sh:331` |
| `gate: a classified delta passes before it ships` (shipped, answer moved `stale` → `pending`) | `tools/tests/gen-feature-list-selftest.sh:324` |
| `gate: a stale classification entry does not fail the run`, `gate: a stale entry is reported`, `gate: the stale entry is named` (shipped, unchanged) | `tools/tests/gen-feature-list-selftest.sh:298`–`301` |

## The mutations

One, for #922: **`922.1`** at `tools/loop/tests/loop-mutations.sh:1170` — `s|{cls: (stop_class),|{cls:
(.stopReason // "unclassified"),|` on `tools/loop/report.sh`, naming
`report#theArchivesClassesAreDerivedNotInvented` as the case that must fail on it.

None for #926: `tools/tests/` holds mutation sets for `check-change-readiness`, `check-doc-routes`, `check-glossary`,
`check-rule-routes` (JSON beside their self-tests) and the loop engine's own
(`tools/loop/tests/loop-mutations.sh`), and nothing for `gen-feature-list.sh` — so the split is held by the four
self-test lines and by no falsification record.

## The gate verdict that shipped it

`76588853` records the figures and the repair; `1eddc98c` corrected the comment's denominator; `cd45d567` records what
the gate of the pair found — that `1eddc98c` had put its new case row *over* the row of
`state#aCutRecordsTheFilesItsPatchCarries`, so the #922 case kept its dispatch arm and its body and lost the only entry
that could select it, and the row was restored, measured after the insert (bodies 459, selection rows 455, dispatch
arms 455). No gate verdict for the change as a whole is stated in either message, so this change claims none.