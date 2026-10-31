# Tasks

Reference: `proposal.md` for the clusters, `design.md` for the split line and the five rejected options.
`skip_specs: true`.

## 1. Baseline

- [x] 1.1 Record `guidelines/Backlog.md`'s section table and heading-number set as `evidence/pre-cut.md`
      (`tools/check-doc-routes.sh --sizes`, `wc -c`). Verify: the table names every section and the document
      measures 35 887 B.
- [x] 1.2 **Measure the referring set**, do not assume it:
      `grep -rn 'Backlog\.md' --include='*.md' --include='*.sh' --include='*.json' . | grep '§'` — record it
      with every citation line as `evidence/pre-cut-citations.txt`. Verify: the pattern tolerates the backtick
      form the tree writes, `.pi/worktrees/` and `.pi/issue-loop/` are excluded as other working trees, and
      the file names `AGENTS.md`, `.pi/skills/loop-spend`, `.pi/skills/portability-check`, `tools/issues/lib.sh`,
      `tools/check-portability.sh`.
- [x] 1.3 Record the companion anchors with the byte span of the material under each
      (`grep -n '^## ' guidelines/Backlog.recipes.md guidelines/Backlog.why.md`) as
      `evidence/pre-cut-companion-anchors.txt`. Verify: `## §9` and `## §10` of the recipes companion and
      `## §9 Checks — why` of the rationale companion are the three that name a moving section, and nothing
      else does. **This design first said `Backlog.why.md` carried no §9 anchor and was wrong** — the check
      reported the orphaned anchor and the split was extended to it; see `design.md` D3 and `evidence/notes.md`.
- [x] 1.4 Record the `AGENTS.md` row that names `Backlog.md`, verbatim, as `evidence/pre-cut-rows.txt`.
      Verify: the row names §1, §4, §5, §9, §10 — two of them moving — and no other row names the document.

## 2. The cut

- [x] 2.1 Create `guidelines/BacklogTooling.md` with §9, §10 and §11, moving the text verbatim under their own
      numbers, with a preamble that names `Backlog.md` §14 as the maintenance rule. Verify: under 32 000 B and
      every section under 12 000 in `--sizes`.
- [x] 2.2 Reduce `guidelines/Backlog.md` to §1–§8 and §12–§14, with a preamble that points at the sibling.
      Verify: under 32 000 B and every section under 12 000 — measured with `--sizes` and `wc -c`, not
      subtracted on paper only.
- [x] 2.3 Repair the five internal references `design.md` D5 lists — three bare numbers, the §1 sentence, and
      the two `Backlog.recipes.md` §9 references. Verify: `grep -n '§9\|§10\|§11' guidelines/Backlog.md` shows
      no bare number naming a section the document no longer carries.
- [x] 2.4 Verify the relocated text is a move: the heading-number union equals the baseline set, and the
      relocation count closes in `evidence/relocation.txt`.
- [x] 2.5 Split the companions by anchor: `## §9` and `## §10` of `guidelines/Backlog.recipes.md` to
      `guidelines/BacklogTooling.recipes.md`, and `## §9 Checks — why` of `guidelines/Backlog.why.md` to
      `guidelines/BacklogTooling.why.md`; the rest stays. Verify: `bash tools/check-doc-routes.sh` exits
      0, so **every companion anchor names a section its own base document carries**, and no companion exists
      with zero anchors.

## 3. The routing and the citations

- [x] 3.1 Split `AGENTS.md`'s backlog row into the concept (`Backlog.md` §1, §4, §5) and the tooling
      (`BacklogTooling.md` §9, §10, §11), keeping every named-section clause. Verify:
      `bash tools/check-doc-routes.sh` exits 0, both documents are routed, and `wc -c AGENTS.md` stays under
      32 000.
- [x] 3.2 Move `tools/doc-routes.json`'s `Backlog.md §9` and `§10` section and rule entries to
      `BacklogTooling.md`, and point the relevant globs at the new document. Verify:
      `bash tools/check-rule-routes.sh` exits 0 with the named-rule count **unchanged at 575**.
- [x] 3.3 Retarget every citation in the measured referring set, **name-only**, including the two skill
      contracts and the shell comments `design.md` D6 lists. Verify: every line of
      `evidence/pre-cut-citations.txt` resolves, the retargets are recorded per file in
      `evidence/retargeted-citations.txt`, and `bash tools/check-doc-routes.sh` exits 0.

## 4. Verification

- [x] 4.1 Verify the condition clears: the `document-size` row reports **0** conditions (was 1). Record
      `evidence/conditions.md`.
- [x] 4.2 Verify no rule changed home silently: the named-rule count is unchanged at 575 and no rule name is
      duplicated across documents. Record `evidence/rules.txt`.
- [x] 4.3 Run the suites this delta owes and name the exclusions: `bash tools/check-doc-routes.sh`,
      `bash tools/check-doc-routes-selftest.sh`, `bash tools/check-rule-routes.sh`,
      `bash tools/check-rule-routes-selftest.sh`,
      `bash tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list`,
      `bash tools/check-todo-references.sh`. Excluded: no `./gradlew` target, no instrumented run, no device
      and **no `pixel-check`** — no composable, string, size or bound changes value.
- [x] 4.4 Verify the change's own hygiene: `openspec validate guideline-backlog-cut --strict` valid and
      `bash tools/check-openspec-hygiene.sh` prints its OK line.

## Verification record

Every command below was run at the end of the pass against the finished tree; suites one per call.

| task | command | observed |
|---|---|---|
| 1.1 | `python3 /tmp/backlog-sections.py` | `total lines 393 bytes 35887`, 19 numbered sections |
| 1.2 | `grep -rn 'Backlog\.md' … | grep '§'` | 13 routed + 8 prose references → `evidence/pre-cut-citations.txt` |
| 1.3 | `grep -n '^## ' guidelines/Backlog{,.recipes,.why}.md` | 9 + 5 anchors; three name a moving section → `evidence/pre-cut-companion-anchors.txt` |
| 1.4 | `grep -n 'Backlog\.md' AGENTS.md` | two rows (:58 the split one, :62 the PORTING citation) → `evidence/pre-cut-rows.txt` |
| 2.1 | `wc -c guidelines/BacklogTooling.md` + `--sizes` | `10829`; largest section §9 5 837 B |
| 2.2 | `wc -c guidelines/Backlog.md` + `--sizes` | `26032`; largest section §1 7 070 B |
| 2.3 | `grep -n '§9\|§10\|§11' guidelines/Backlog.md` | no bare number naming a section the document no longer carries |
| 2.4 | `python3 /tmp/backlog-evidence.py` | `Union equals the baseline set: True (disjoint: True)` → `evidence/relocation.txt` |
| 2.5 | `check-doc-routes.sh` after the companion splits | `all 325 section references resolve, all 2 skill references resolve` |
| 3.1 | `check-doc-routes.sh` + `wc -c AGENTS.md` | exit 0; `25287 AGENTS.md`; both documents routed |
| 3.2 | `check-rule-routes.sh` | `every glob matches the tree and all 575 named rule(s) are reached` |
| 3.3 | `grep -rn 'BacklogTooling\.md.*§' …` | 25 lines → `evidence/retargeted-citations.txt` |
| 4.1 | `tools/sensors/run.sh --only document-size` | `verdict=clear measurement=none … action=recorded on the row's record` → `evidence/conditions.md` |
| 4.2 | `check-rule-routes.sh` | 575 named rules reached, unchanged; 12 map entries retargeted → `evidence/rules.txt` |
| 4.3 | `check-doc-routes-selftest.sh` | `all 33 cases passed` |
| 4.3 | `check-rule-routes-selftest.sh` | `all 6 cases passed` |
| 4.3 | `mutate.sh --mutations tools/check-doc-routes-mutations.json --list` | 12 lines, every case listed |
| 4.3 | `check-todo-references.sh` | `66 citation(s) resolve through the recorded table` |
| 4.4 | `openspec validate guideline-backlog-cut --strict` | `Change 'guideline-backlog-cut' is valid` |
| 4.4 | `check-openspec-hygiene.sh` | `OpenSpec artifacts are consistent: 32 open change(s) carry task markers, …` |

Excluded, and why: no `./gradlew` target, no instrumented run, no device, no `pixel-check` — the delta is
documents, one routing table, one JSON map and eight name-only comment retargets.

## Workflow follow-up

- One independent read-only review of the four cuts together, then archive them with the retune's change.
