# The falsification run (task 8)

`tools/issues/metadata-mutations.sh` — one mutation per invariant, applied to the tree, the named rule must
notice it, the mutation is restored, and the same probe must go quiet again. The harness refuses a mutation
that did not change its file (a mutation that changes nothing reads exactly like a mutation that was noticed
by nothing, which is how `loop-mutations.sh` first lied), and a `trap` restores the tree whatever happens.

Date: 2026-10-17 · exit code of the run: **0** — `metadata-mutations: 7 of 7 mutations noticed, and every
probe quiet again`.

| # | mutation (what was broken) | the probe that must notice | with the mutation | after the restore |
|---|---|---|---|---|
| 8.1 | `lib.sh`: the label set is dropped from the basis (`label_key=""`) | `basis#changesWhenAClassLabelChanges` | case failed (exit 1) | case passed (exit 0) |
| 8.2 | `lib.sh`: the basis carries the raw body instead of its normalised digest | `basis#stableAcrossWhitespaceOnlyBodyEdit` | case failed (exit 1) | case passed (exit 0) |
| 8.3 | `lib.sh`: the anchor-existence flag is dropped (`anchor_key=""`) | `basis#changesWhenTheCitedCodeGoesAway` | case failed (exit 1) | case passed (exit 0) |
| 8.4 | `prioritise.sh`: the basis carries the run's own timestamp (`$(date +%s%N)`) | `prioritise#secondRunScoresNothing` | case failed (exit 1) | case passed (exit 0) |
| 8.5 | `tooling.json`: the top band starts at 16, so score 15 falls in no band | `Bands#coversTheWholeRange` (Java core) | case failed (exit 1) | case passed (exit 0) |
| 8.6 | `classify.sh`: the pass writes the automation opt-in for an "obviously fixable" issue | `check-metadata.sh --write-log` over the run's own log, and the file-level reserved-label rule behind `check#neverWritesEligibility` | check failed (exit 1, `wrote-the-opt-in`) | check passed (exit 0) |
| 8.7 | `lib.sh`: `issues_body_with_marker` returns the body unchanged, so the writer stores the label without the marker | `check-metadata.sh --issues` over the stored fixture, the rule behind `check#markerLabelMismatchFails` and `check#labelWithoutMarkerFails` | check failed (exit 1, `label-without-marker`) | check passed (exit 0) |

## The two mutations that needed the end-to-end form

8.6 and 8.7 are the two invariants whose *named* cases (`check#neverWritesEligibility`,
`check#markerLabelMismatchFails`) hold a **fixture** log or issue set, so mutating a writer cannot make those
cases fail — the case's input is a file, not the pass's output. The harness therefore breaks the writer and
feeds what the broken writer produced to the *real* check:

- **8.6** — the mutation resolves the reserved label **at runtime**
  (`$(issues_core Profile "$profile" reservedLabels | head -1)`), which is exactly the form
  `check-metadata.sh`'s file-level grep (`names-the-reserved-label`) cannot see. The write-log rule catches
  it, and the probe asserts the reason code (`wrote-the-opt-in`), not merely a non-zero exit. *Recorded
  finding:* the file-level rule is a convenience, and the write-log rule is the one that holds against a pass
  that reads the label instead of naming it. `update.sh` would refuse the write itself (`label-reserved`,
  exit 3) — the mutation bypasses it deliberately, because D7 is about a pass that stops using the tooling.
- **8.7** — the mutation disables the marker in its **one home** (`lib.sh`), so both the composed body
  (`--body-of`, which the case applies back to its fixture) and the body the writer hands `update.sh` lose
  it. Had the mutation touched only the write path, the composed body a case reads would still have carried
  the marker and the probe would have proven nothing.

## The gates behind the run

| gate | result on 2026-10-17 |
|---|---|
| `tools/issue-tooling-selftest.sh` | 69 of 69 cases passed (JDK 27, offline, no token) |
| `tools/issues/check-metadata-selftest.sh` | 7 of 7 cases passed (both directions per case: the defect reds the check, removing it greens it) |
| `tools/issues/metadata-mutations.sh` | 7 of 7 mutations noticed, every probe quiet again |
| `tools/issues/check-metadata.sh` | 0 violations (token-free self-check, the CI step's command) |
| `tools/gen-feature-list.sh --check-classification` | exit 0 |

No Gradle build and no Gradle suite was run: this change touches shell, JSON and markdown only
(`guidelines/Build.md` §2, the scope block). No device and no screen is involved — nothing in this change
draws anything.
