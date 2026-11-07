# Tasks

Reference: `proposal.md` for the clusters, `design.md` for the split line. `skip_specs: true`.

## 1. Baseline

- [x] 1.1 Record `guidelines/MapRendering.md`'s section table and heading-number set as `evidence/pre-cut.md`
      (`tools/check-doc-routes.sh --sizes`, `wc -c`). Verify: the table names §1…§20 with their bytes and the document
      measures 46 751 B.
- [x] 1.2 **Measure the referring set**, do not assume it:
      `grep -rln 'MapRendering\.md.*§' guidelines/ AGENTS.md .pi/skills/` — record it as
      `evidence/pre-cut-citations.txt` with every citation line. Verify: the pattern tolerates the backtick form the
      tree writes (`` `MapRendering.md` §14 ``), and the file names at least `AGENTS.md` and
      `guidelines/Backlog.recipes.md`.
- [x] 1.3 Record the five hand-written rule names with their sections as `evidence/pre-cut-rule-names.txt`, and the
      companion anchors (`grep -n '^## §' guidelines/MapRendering.why.md guidelines/MapRendering.recipes.md`) as
      `evidence/pre-cut-companion-anchors.txt`. Verify: five names, all distinct, and every anchor names a section the
      baseline set carries.

## 2. The cut

- [x] 2.1 Create `guidelines/MapFrames.md` with §12, §13 and §14 verbatim, under their own numbers. Verify:
      `wc -c guidelines/MapFrames.md` under 32 000 and every section under 12 000 in `--sizes`.
- [x] 2.2 Create `guidelines/MapRoute.md` with §19, carrying its own number. Verify: under 32 000 and §19 under
      12 000.
- [x] 2.3 Move §20 into `guidelines/MapStyling.md`, under its own number, beside the renderer's other configured
      bounds. Verify: `wc -c guidelines/MapStyling.md` under 32 000 and §20 under 12 000 there.
- [x] 2.4 Reduce `guidelines/MapRendering.md` to §1, §1a–§1e, §2, §4, §5. Verify: `wc -c` under 32 000, every
      section under 12 000.
- [x] 2.5 Verify the relocated text is a move: the heading-number union equals the baseline set, and the relocation
      count closes in `evidence/relocation.txt` (each block found in its receiving document).

## 3. The companions

- [x] 3.1 Split the companions beside their halves: the anchors for §12–§14 to `guidelines/MapFrames.why.md` **only
      if that half carries the material**, §19's and §20's anchors to the receiving documents' companions if they
      carry them, and the rest stays. Verify: `bash tools/check-doc-routes.sh` exits 0, so every companion anchor
      names a section **its own base document** carries.
- [x] 3.2 Verify no companion was created empty: every companion file names at least one rule or carries at least one
      anchor. Verify: `grep -c '^## §'` on each new companion is greater than 0, or the file does not exist.

## 4. The routing and the citations

- [x] 4.1 Retarget `AGENTS.md`'s rows for the moved sections, add the rows for `MapFrames.md` and `MapRoute.md`, and
      price any lettered sub-section a row names. Verify: `bash tools/check-doc-routes.sh` exits 0 and
      `wc -c AGENTS.md` stays under 32 000.
- [x] 4.2 Add the new documents' rule globs to `tools/doc-routes.json`. Verify: `bash tools/check-rule-routes.sh` exits
      0 with **575** named rules — unchanged from before the cut.
- [x] 4.3 **Retarget every citation in the measured referring set**, name-only (`MapRendering.md §X` →
      `MapFrames.md §X` / `MapRoute.md §X` / `MapStyling.md §X`), with no statement or example changed. Verify: each
      line of `evidence/pre-cut-citations.txt` resolves to the document that now carries its number, the retargets are
      recorded in `evidence/retargeted-citations.txt`, and `bash tools/check-doc-routes.sh` exits 0.

## 5. Verification

- [x] 5.1 Verify the condition clears: the `document-size` row reports **2** conditions (was 3 — `UI` and `Backlog`
      remain until their own cuts). Record `evidence/conditions.md`.
- [x] 5.2 Verify the five rule names survive verbatim: diff `evidence/pre-cut-rule-names.txt` against the names the
      documents carry. Verify: the diff is empty.
- [x] 5.3 Run the suites this delta owes and name the exclusions: `bash tools/check-doc-routes.sh`,
      `bash tools/check-doc-routes-selftest.sh`, `bash tools/check-rule-routes.sh`,
      `bash tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list`,
      `bash tools/check-todo-references.sh`. Excluded: no `./gradlew` target, no native build, no device and no
      `pixel-check` — no compiled file is touched and no rendering value changes.
- [x] 5.4 Verify the change's own hygiene: `openspec validate guideline-maprendering-cut --strict` valid and
      `bash tools/check-openspec-hygiene.sh` prints its OK line.

## Verification — command and observed line

| task | command | observed |
|---|---|---|
| 1.1 | `wc -c guidelines/MapRendering.md` | `46751` |
| 1.1 | `bash tools/check-doc-routes.sh --sizes \| grep MapRendering.md` | 15 section lines §1…§20, clusters 29 024 / 10 505 / 3 055 / 3 044 |
| 1.2 | `grep -rln 'MapRendering\.md.*§' guidelines/ AGENTS.md .pi/skills/` | `guidelines/Backlog.recipes.md`, `AGENTS.md` |
| 1.3 | `grep -n '^## §' guidelines/MapRendering.why.md guidelines/MapRendering.recipes.md` | §1, §13, §20, §20 |
| 2.1 | `wc -c guidelines/MapFrames.md` | `11040`; `--sizes`: §12 1748, §13 6298, §14 2459 |
| 2.2 | `wc -c guidelines/MapRoute.md` | `3425`; `--sizes`: §19 3055 |
| 2.3 | `wc -c guidelines/MapStyling.md` | `20031`; `--sizes`: §20 3036 |
| 2.4 | `wc -c guidelines/MapRendering.md` | `30344`; largest section §1d 10282 |
| 2.5 | union of `^## <n>.` over the four documents | `{1,1a,1b,1c,1d,1e,2,4,5,12,13,14,19,20}` = baseline |
| 3.1 | `bash tools/check-doc-routes.sh` | `all 310 section references resolve, all 2 skill references resolve` (0) |
| 3.2 | `grep -c '^## §' guidelines/MapFrames.why.md` | `1`; no `MapRoute.why.md`, no `MapStyling.recipes.md` created |
| 4.1 | `bash tools/check-doc-routes.sh`; `wc -c AGENTS.md` | exit 0; `25129` |
| 4.2 | `bash tools/check-rule-routes.sh` | `every glob matches the tree and all 575 named rule(s) are reached` (0) |
| 4.3 | `bash tools/check-doc-routes.sh` | exit 0, 310 references; retargets in `evidence/retargeted-citations.txt` |
| 5.1 | `bash tools/sensors/run.sh --only document-size --json` | 2 violations: `35887`, `45327` |
| 5.2 | five-name diff against the post-cut document | 0 missing, 0 differing |
| 5.3 | the five checks, run one per call | all exit 0 |
| 5.4 | `openspec validate … --strict`; `check-openspec-hygiene.sh` | `Change 'guideline-maprendering-cut' is valid`; `OpenSpec artifacts are consistent …` |

## Workflow follow-up

- Independent read-only review of the four cuts together, once the last of them lands.
- Archive the cuts with the retune's change.
