# Tasks

Reference: `proposal.md` for the clusters, `design.md` for the boundary. `skip_specs: true` — no requirement changes.

## 1. Baseline

- [x] 1.1 Record `guidelines/UIPhone.md`'s section table and heading-number set as `evidence/pre-cut.md`
      (`tools/check-doc-routes.sh --sizes`, `wc -c`). Verify: the table names every section §5…§19 with its bytes and
      the document measures 47 926 B.
- [x] 1.2 Record every citation of this document as `evidence/pre-cut-citations.txt` —
      `grep -rn 'UIPhone\.md §' AGENTS.md guidelines/*.md .pi/skills/*/SKILL.md` — and every rule name it carries with
      its section, as `evidence/pre-cut-rule-names.txt`. Verify: each citation names a section the baseline set
      carries.
- [x] 1.3 Record the companion anchors as `evidence/pre-cut-companion-anchors.txt`: every `## §N` heading in
      `guidelines/UIPhone.why.md` and `UIPhone.recipes.md`, with the byte span of the material under it. Verify: every
      anchor names a section the baseline set carries — it is the split's own input.

## 2. The cut

- [x] 2.1 Create `guidelines/UIPhoneMap.md` with §7, §7a, §7b, §8, §8a, §8b, §8c, §10a and §11, moving the text
      verbatim under their own numbers. Verify: `wc -c guidelines/UIPhoneMap.md` is under 32 000 and every one of its
      sections is under 12 000 in `--sizes`.
- [x] 2.2 Reduce `guidelines/UIPhone.md` to §5, §6a and §12–§19, leaving the cluster's section numbers with the
      document that took them and no index entry pointing at itself. Verify: `wc -c guidelines/UIPhone.md` is under
      32 000, every section under 12 000.
- [x] 2.3 Split the companions beside their halves: the map half's anchors to `guidelines/UIPhoneMap.why.md` and
      `UIPhoneMap.recipes.md` — **created only if that half carries the material**, never empty — and the rest stays
      in `UIPhone.why.md` / `UIPhone.recipes.md`. Verify: `bash tools/check-doc-routes.sh` exits 0, so every companion
      anchor names a section its own base document carries.
- [x] 2.4 Verify the relocated text is a move: the numbers are unchanged and no statement was reworded. Verify: the
      heading-number union equals the baseline set plus nothing, and the relocation count closes in
      `evidence/relocation.txt` (each block found in its receiving document).

## 3. The routing

- [x] 3.1 Add one row to `AGENTS.md`'s `## Documentation Map` for `UIPhoneMap.md` and narrow the existing phone row to
      `UIPhone.md`'s sections, pricing any lettered sub-section the rows name. Verify: `bash tools/check-doc-routes.sh`
      exits 0 and `wc -c AGENTS.md` stays under 32 000.
- [x] 3.2 Add the new document's rule globs to `tools/doc-routes.json`. Verify: `bash tools/check-rule-routes.sh` exits
      0 — every glob matches the tree and every named rule is reached.
- [x] 3.3 Verify the citations: every line of `evidence/pre-cut-citations.txt` resolves to the document that now
      carries its number, and the retargeting is recorded in `evidence/retargeted-citations.txt`.

## 4. Verification

- [x] 4.1 Verify the condition clears: the `document-size` row reports **3** conditions (was 4 — `UIPhone` gone,
      `MapRendering`, `UI` and `Backlog` remain until their own cuts). Record `evidence/conditions.md`.
- [x] 4.2 Run the suites this delta owes and name the exclusions: `bash tools/check-doc-routes.sh`,
      `bash tools/check-doc-routes-selftest.sh`, `bash tools/check-rule-routes.sh`,
      `bash tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list`,
      `bash tools/check-todo-references.sh`. Excluded: no `./gradlew` target, no instrumented run, no device and no
      `pixel-check` — no compiled file is touched and no size changes value.
- [x] 4.3 Verify the change's own hygiene: `openspec validate guideline-uiphone-cut --strict` valid and
      `bash tools/check-openspec-hygiene.sh` prints its OK line.

## Workflow follow-up

- Independent read-only review of the four cuts together, after all four land.
- Archive the four cuts with the retune's change.

## Observed verification (commands and their lines)

- 1.1 `tools/check-doc-routes.sh --sizes | grep UIPhone.md` -> 19 `section-cost` lines, `wc -c` 47926
- 1.2 `grep -rn 'UIPhone\.md §' AGENTS.md guidelines/*.md .pi/skills/*/SKILL.md` -> citations recorded, each names a baseline section
- 1.3 `grep -n '^## §' UIPhone.why.md UIPhone.recipes.md` -> §7a §7b §8b §8c §8a §12 §18 / §16, all in the baseline set
- 2.1 `bash tools/check-doc-routes.sh --sizes | grep UIPhoneMap` -> §7 1704 … §11 3403, all < 12000; `wc -c guidelines/UIPhoneMap.md` 29340
- 2.2 `wc -c guidelines/UIPhone.md` -> 19375; every section < 12000
- 2.3 `bash tools/check-doc-routes.sh` -> `all 308 section references resolve, all 2 skill references resolve` (0)
- 2.4 `sed -n` slices + `--sizes` per-section bytes equal the baseline -> `evidence/relocation.txt`
- 3.1 `bash tools/check-doc-routes.sh` exit 0; `wc -c AGENTS.md` 25028 (< 32000)
- 3.2 `bash tools/check-rule-routes.sh` -> `every glob matches the tree and all 575 named rule(s) are reached` (0)
- 3.3 `evidence/retargeted-citations.txt`; `bash tools/check-doc-routes.sh` -> 308 refs resolve (0)
- 4.1 `bash tools/sensors/run.sh --only document-size --json` -> 3 violations (Backlog 35890, MapRendering 46751, UI 45324); was 4
- 4.2 `check-doc-routes.sh` 0, `check-doc-routes-selftest.sh` 33/33 (0), `check-rule-routes.sh` 0, `mutate.sh --list` 11 mutations (0), `check-todo-references.sh` 0
- 4.3 `openspec validate guideline-uiphone-cut --strict` valid; `check-openspec-hygiene.sh` OK
