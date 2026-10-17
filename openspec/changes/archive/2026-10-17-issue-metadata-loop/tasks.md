# Tasks

This change is spec-owned by `issue-priority`: `specs/issue-priority/spec.md` defines it, so each task names the
**design decision** it lands, and its verification names the case that exercises it; the traceability task in this change maps every scenario to its case. Every task carries its own verification.

Scope guard: this change edits shell, JSON and markdown — no Gradle source set, no compiled file, no
manifest, no native source. **No Gradle build and no Gradle suite is run or required**
(`guidelines/Build.md` §2, the scope block); the gate is `tools/issues/check-metadata-selftest.sh` plus
the offline cases. No device or pixel measurement applies: no UI changes and nothing is read on a
screen — say so in the change report.

Ordering: `github-issue-tooling` and `migrate-todo-to-github-issues` must be applied first (the tracker
and `prio/` in the vocabulary). `sensor-registry-and-metric-issues` is independent, but applied first it
gives the basis an `occurrence` count to read (task 5.2 handles its absence).

## 1. Bands and the invariant (D2, D7)

- [x] 1.1 Write the profile's `bands` section in `tools/tooling.json`: the scoring table (severity 1–5, reach 1–3, confidence 1–3), the four band edges `P0 15..18 · P1 11..14 · P2 7..10 · P3 3..6`, the `ttl_days` default (180) and the label spelling (`prio/p0`–`prio/p3`). This change owns that section and **adds no namespace**, because `github-issue-tooling` reserves every one. Verify: `jq -e . tools/tooling.json` succeeds, the four band edges are the only namespace content this change writes, and a case `Bands#coversTheWholeRange` asserts every score in `3..18` maps to exactly one band and no band is unreachable (D2).
- [x] 1.2 Write `tools/issues/check-metadata.sh`: fail when a `prio/*` label is outside `p0..p3`, when an issue's marker band and label disagree, when a marker exists without a band or vice versa, or when any issue carries more than one band. Verify: case `check#markerLabelMismatchFails` on a fixture with a hand-edited band exits 1 naming the issue, and case `check#outOfRangeBandFails` does the same for a `prio/p9` fixture (D1, D7).
- [x] 1.3 Add the authorisation separation to the check: assert that the pass's write log (a fixture file in the cases) contains no `agent/eligible` write, and that `prioritise.sh` and `classify.sh` never pass it. Verify: case `check#neverWritesEligibility` fails when a fixture write log is mutated to include it, and `grep -rn 'agent/eligible' tools/issues/prioritise.sh tools/issues/classify.sh` prints nothing (D7).
- [x] 1.4 Write the self-test `tools/issues/check-metadata-selftest.sh` proving every check can go red, following the `tools/*-selftest.sh` convention. Verify: it exits 0 on the fixtures and non-zero when one fixture is made benign (D7).
- [x] 1.5 Add one `Check issue metadata` step to `.github/workflows/build.yml`, with no token. Verify: the command runs green locally with `env -i PATH="$PATH" bash tools/issues/check-metadata.sh` (D7).

## 2. The basis and the selector (D1, D3, D9)

- [x] 2.1 Implement the basis in `tools/issues/prioritise.sh`: `sha256` over the sorted `class/*`, `category/*`, `status/*` label set, the body hash, the anchor-existence flag (path, and symbol when named, via `git grep`), the `occurrence` count read from the body record or defaulting to 1, and the TTL bucket. Verify: case `basis#stableAcrossWhitespaceOnlyBodyEdit` asserts an unchanged digest for a reflowed body, and case `basis#changesWhenAClassLabelChanges` asserts a different digest (D3).
- [x] 2.2 Implement the partition and the skip: read titles, labels and bodies for **every** issue in one paged call, recompute each basis, and skip every issue whose basis matches its marker — no sampling, no early exit. Verify: case `prioritise#readsEveryIssueAndSkipsByBasis` with a 5-issue fixture where 4 bases match asserts all 5 were read, exactly 1 was due, and the skipped 4 received no write (D3, D9).
- [x] 2.3 Implement `--max-rescore N` (default 20) and the report counter for due-but-unscored issues. Verify: case `prioritise#capsTheDueSet` gives 25 due issues and asserts 20 scored and 5 reported as due-but-unscored, with no write on the 5 (D4).
- [x] 2.4 Implement idempotence: a second run with nothing changed must be a no-op. Verify: case `prioritise#secondRunScoresNothing` asserts the second run's scored count is 0, its write count is 0 and its exit code is 0 (D9).
- [x] 2.5 Implement the writer, in one operation per issue, through `tools/issues/update.sh`: the `prio/<band>` label and the body marker `band, score, sev, reach, conf, occ, basis, at`. Verify: case `prioritise#writesLabelAndMarkerTogether` asserts one `update.sh` invocation carrying both, and case `prioritise#refusesATaxonomyOutsideValue` asserts a band outside `p0..p3` is refused before any write (D1).
- [x] 2.6 Implement the transition comment: post one comment only when the band changes, carrying the previous band, the new band and the model's one-line rationale. Verify: case `prioritise#commentsOnlyOnABandTransition` asserts a same-band rescore posts nothing and a band change posts exactly one comment with both bands in it (D4).

## 3. Classification (D5)

- [x] 3.1 Write `tools/issues/classify.sh`: select issues with **no** `class/*` label, hand them to the model for a `class` + `category` proposal, and write both through `update.sh`. Verify: case `classify#selectsOnlyUnclassified` with a fixture where 2 of 6 lack a class asserts exactly those 2 are proposed and written (D5).
- [x] 3.2 Refuse and report rather than guess: a proposal outside the closed vocabulary is refused with a reason and nothing is written; an issue whose existing `category` is outside the vocabulary is **reported**, never silently relabelled. Verify: case `classify#refusesAnOutOfVocabularyProposal` and case `classify#reportsAnOutOfVocabularyCategoryWithoutWriting` (D5).
- [x] 3.3 Assert the boundary: classification never writes `agent/eligible`, `prio/*` or any `status/*` label. Verify: case `classify#writesOnlyClassAndCategory` asserts the write set of a fixture run is exactly those two namespaces (D5, D7).

## 4. The judgement half (D4, D5, D8)

- [x] 4.1 Write `.pi/skills/issue-metadata/SKILL.md`: the scoring tables (severity, reach, confidence, the status caps, the tie-breakers), the band edges, the batching contract, the rule that the model **returns numbers and never writes**, and the classification contract. Verify: the document names all three component tables, the four band edges, `--max-rescore`, and the words "the model never writes a label" (D8).
- [x] 4.2 State the untrusted-input rule in the skill: an issue's title and body are **data**, passed as a delimited field, never an instruction — the same boundary `github-issue-tooling` D7 fixed. Verify: the skill contains the rule, and a case asserts the prompt the pass builds places the body inside the delimited block exactly once (D4).
- [x] 4.3 Rewrite `.pi/skills/triage-todo/SKILL.md`: rank by the persisted band, then by the tie-breakers; report a band-less issue as **unranked** (read-only, it cannot write one); keep the scoring tables as the definition the pass implements; name `issue-metadata-loop` as the owner of a rescore. Verify: the skill names the band as its primary key, names unranked issues explicitly, keeps all three component tables, and contains no instruction to recompute the backlog (D8).

## 5. Selectors and the sensor input (D3, D6)

- [x] 5.1 Add `--prio <p0,p1,..>` and `--unranked` to `tools/issues/list.sh`. Verify: case `list#prioSelector` narrows a 5-issue fixture to the expected numbers in band order, and case `list#unrankedSelector` returns exactly the issues with no band (D1, D6).
- [x] 5.2 Read the `occurrence` count from the issue's body record, and treat its absence as `1`, so the pass works whether or not `sensor-registry-and-metric-issues` has been applied. Verify: case `prioritise#occurrenceAbsenceIsOne` asserts an issue with no occurrence field scores normally, and case `prioritise#occurrenceChangeInvalidatesTheBasis` asserts a reopened issue (occ 1 → 2) becomes due (D3, D6).
- [x] 5.3 Confirm the gates stay derived: assert the pass writes no `status/*` or `agent/needs-device` label, and that the gate lists are computed by `list.sh` filters at read time. Verify: case `prioritise#writesNoGateLabel` asserts the write set of a fixture run contains only `prio/*` and the body marker (D6).

## 6. Metrics (D9)

- [x] 6.1 Implement the pass's report line: issues read, skipped by unchanged basis, due, scored, classified, band transitions, model batches, wall time. Verify: case `prioritise#reportsEveryCounter` asserts all eight fields appear in a fixture run's output, with `read` equal to the fixture's issue count (D9).
- [x] 6.2 Record the numbers in the change's evidence after the real run (task 7.2): the scored/skipped ratio and the wall time, as the change's own value justification. Verify: the recorded file carries those numbers with the run's timestamp (D9).

## 7. Integration

- [x] 7.1 Run `tools/issues/prioritise.sh --dry-run` and `classify.sh --dry-run` over the live tracker and record the report: how many of the 136 issues are unranked, how many would be scored on the first run, and how many after it. Verify: the transcript is recorded and no `gh` write occurred (D9).
- [x] 7.2 Run the pass for real on the live tracker and record: issues scored, issues classified, band transitions, wall time and the model batch count. Verify: `tools/issues/check-metadata.sh` exits 0 afterwards, and `tools/issues/list.sh --prio p0 --json` returns a non-empty, band-ordered set (D1, D9).
- [x] 7.3 Run the pass a second time immediately and record that it scored nothing. Verify: the scored count is 0, the write count is 0, and the elapsed time is materially below the first run's — the change's central claim, measured rather than asserted (D3, D9).
- [x] 7.4 Confirm the exclusions explicitly in the change report: no Gradle build and no suite was run; `build.yml` gained one token-free step only; no model provider key was added to CI; the app, the native boundary and the release path are untouched. Verify: `git diff --name-only` lists no file under `app/`, `auto/`, `core/`, `osmscout-client-java/`, `buildSrc/`, and the report names the exclusion (`guidelines/Build.md` §2, scope block).
- [x] 7.5 Classify `issue-priority` in `tools/feature-list/specs.json` as the capability-classification gate requires, since CI fails on a shipped spec with no line there (the gate is `tools/gen-feature-list.sh --check-classification`). Verify: adding the line makes `bash tools/gen-feature-list.sh --check-classification` exit 0, and removing it makes the same command fail naming `issue-priority`, so the line is load-bearing rather than decorative.
- [x] 7.6 Add the scenario-to-task traceability table for `issue-priority` and verify it is complete: one row per scenario naming the case that exercises it and the task that owns that case, with no scenario unmapped and no task claiming a scenario it does not cover. Verify: the row count equals `grep -c "^#### Scenario:" specs/issue-priority/spec.md` and the change report says so.
- [x] 7.7 Keep `guidelines/Backlog.md` true for this change, and verify the routing table resolves: correct every section this change makes stale — its row in §1, and the §12 rows for the decisions it lands — then run `bash tools/check-doc-routes.sh` and `bash tools/check-doc-routes-selftest.sh` and assert both exit 0. Verify: both exit 0, the §1 row for issue-metadata-loop is marked landed rather than planned, and no §12 row it owns still describes an intention the implementation did not take.

## 8. Falsification (revert-checks)

One mutation each, restored after; the named case must fail, then the suite must be green again. The
gate is the check's self-test plus the offline cases, not Gradle — name the exit codes in the report.

- [x] 8.1 Basis, labels: drop the label set from the basis. `basis#changesWhenAClassLabelChanges` must fail. Restore; self-test exits 0 (D3).
- [x] 8.2 Basis, body: drop the body hash. `basis#stableAcrossWhitespaceOnlyBodyEdit` still passes, so the mutation to make is the reverse — **include the raw body instead of its hash**: the whitespace case must fail. Restore; self-test exits 0 (D3).
- [x] 8.3 Basis, anchor: drop the anchor-existence flag. A case asserting a deleted cited path invalidates the basis must fail. Restore; self-test exits 0 (D3).
- [x] 8.4 Idempotence: include the run timestamp in the basis. `prioritise#secondRunScoresNothing` must fail. Restore; self-test exits 0 (D3, D9).
- [x] 8.5 Band mapping: move an edge so one score maps to no band. `Bands#coversTheWholeRange` must fail. Restore; self-test exits 0 (D2).
- [x] 8.6 Authorisation: let `classify.sh` write `agent/eligible` for an obviously fixable issue. `check#neverWritesEligibility` must fail. Restore; self-test exits 0 (D7).
- [x] 8.7 Marker agreement: write the label without the marker. `check#markerLabelMismatchFails` must fail. Restore; self-test exits 0 (D1).

## Workflow follow-up

- Archive after task 7.2 and 7.3 have recorded the real run and its no-op rerun, or after they record
  explicitly why no issue was due.
- `agent-issue-loop` consumes the bands this change persists; applied first, its prescreen can select by
  `--prio` instead of ranking with a model.
