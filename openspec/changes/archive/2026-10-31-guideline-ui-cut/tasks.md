# Tasks

Reference: `proposal.md` for the clusters, `design.md` for the split line. `skip_specs: true`.

## 1. Baseline

- [x] 1.1 Record `guidelines/UI.md`'s section table and heading-number set as `evidence/pre-cut.md`
      (`tools/check-doc-routes.sh --sizes`, `wc -c`). Verify: the table names every section and the document measures
      45 327 B. The table is already measured — write it from here and confirm it against the command:

      | section | bytes | lines |
      |---|---|---|
      | preamble | 1 038 | 1–22 |
      | §1 Cross-variant UI parity | 1 716 | 23–49 |
      | §2 Action glyphs on the car display | 532 | 50–62 |
      | §3 Car-host template constraints | 3 690 | 63–107 |
      | §3a Map renderer startup | 5 379 | 108–181 |
      | §3b Settings-screen loading | 2 036 | 182–212 |
      | §3c Car wait notices | 2 912 | 213–248 |
      | §4 Details attribute list (Android Auto) | 933 | 249–262 |
      | §6 Shared data resolution | 1 313 | 263–280 |
      | §6b Android Auto search surface | 5 169 | 281–347 |
      | §8d Cross-variant map presentation | 5 539 | 348–412 |
      | §9 Dark mode | 4 877 | 413–476 |
      | §10 Internationalisation | 8 816 | 477–600 |
      | §20 Keeping this document honest | 1 377 | 601–617 |

      Ruler: a section's bytes are its lines from the `## ` heading through the line before the next one,
      trailing blank included — the ruler `--sizes` prints. The 13 sections sum to 44 289 B.

      **§8d is the section neither cluster claims** — it is 5 539 B of nine already-named rules about *both*
      surfaces. It **stays in `UI.md`**; see `design.md`, "Amendment during apply — §8d was missing".
- [x] 1.2 **Measure the referring set**, do not assume it:
      `grep -rln 'UI\.md.*§' guidelines/ AGENTS.md .pi/skills/` — record it with every citation line as
      `evidence/pre-cut-citations.txt`. Verify: the pattern tolerates the backtick form the tree writes, and the file
      names at least `AGENTS.md`, `guidelines/Backlog.recipes.md`, `guidelines/Design.md`, `guidelines/UIPhone.md`.
- [x] 1.3 Record the rule names with their sections, and the companion anchors with the byte span of the material
      under each (`grep -n '^## §' guidelines/UI.why.md guidelines/UI.recipes.md`), as
      `evidence/pre-cut-companion-anchors.txt`. Verify: every anchor names a section the baseline set carries — this
      is the split's input and `UI.why.md` is the largest companion in the tree.
- [x] 1.4 Record the `AGENTS.md` rows that name `UI.md`, verbatim, as `evidence/pre-cut-rows.txt`, marking which of
      them name a **car** section (§2, §3, §3b, §3c, §4, §6b) and which name a **parity or shared** one (§1, §6, §9,
      §10, §20). Verify: every row is in one of the two sets.

## 2. The cut

- [x] 2.1 Create `guidelines/UICar.md` with §2, §3, §3a, §3b, §3c, §4 and §6b, moving the text verbatim under their
      own numbers. Verify: under 32 000 B and every section under 12 000 in `--sizes` — measured 21 562 B, largest
      section §3a 5 382 B.
- [x] 2.2 Reduce `guidelines/UI.md` to §1, §6, §8d, §9, §10, §20 and its preamble. Verify: **24 881 B** against
      32 000 B, and the largest remaining section §10 at **8 816 B** against 12 000 — both measured with `--sizes`
      and `wc -c`, not subtracted on paper only. The 23 638 B of kept sections plus a preamble that points at the
      sibling document is what the 24 881 B is made of.
      §8d **stays**: seven of its nine rules are dual-surface (per-surface anchors with shared labels, one
      reroute policy for the session, the row rule for street-name labels, one route and one marker palette per
      presentation, the opaque casing, lowercase stylesheet hexes, the per-surface voice switch with the shared
      GPS switch); its AA surface-indicator rule and its phone-only location-options rule are single-surface
      bullets inside a dual section. Do **not** split the section — that would renumber.
- [x] 2.3 Verify the relocated text is a move: the heading-number union equals the baseline set, and the relocation
      count closes in `evidence/relocation.txt`.
- [x] 2.4 Split the companions **by anchor**, measured (`grep -n '^## ' guidelines/UI.why.md guidelines/UI.recipes.md`):
      `UI.why.md` 15 551 B carries `## §1 The parity decisions` (**stays**), `## §3a The wait that is not the
      loading row's deadline` (**car**), `## §3c The route-calculation notice's dwell` (**car**), `## §8d The car
      search distance reference` (**stays** — §8d stays, even though this anchor reads car-flavoured), `## §9 Why
      the dark chrome is not app-themed` (**stays**); `UI.recipes.md` 1 032 B carries `## §3 Reading the car-list
      capacity` (**car**). Every anchor naming a car section to `guidelines/UICar.why.md` /
      `UICar.recipes.md` — created **only if that half holds the material** — and the §1, §8d and §9 anchors stay. Verify: `bash tools/check-doc-routes.sh` exits 0, so **every companion anchor names a section its
      own base document carries**; and no companion file exists with zero anchors.

## 3. The routing and the citations

- [x] 3.1 Retarget `AGENTS.md`'s **car** rows to `UICar.md`, add one row for `UICar.md`, keep the parity and shared
      rows naming `UI.md`, and keep every lettered sub-section priced. Verify: `bash tools/check-doc-routes.sh` exits
      0, the parity rows still name `UI.md` (compare against `evidence/pre-cut-rows.txt`), and `wc -c AGENTS.md` stays
      under 32 000.
- [x] 3.2 Add `UICar.md`'s rule globs to `tools/doc-routes.json`. Verify: `bash tools/check-rule-routes.sh` exits 0
      with the named-rule count **unchanged**.
- [x] 3.3 Retarget every citation in the measured referring set, **name-only** (`UI.md §X` → `UICar.md §X` for a
      car section; unchanged for §1, §6, §8d, §9, §10, §20), with no statement, sentence or example changed.
      The referring set is measured: `AGENTS.md`, `guidelines/Backlog.recipes.md`, `guidelines/Design.md`,
      `guidelines/UIPhone.md`, plus `guidelines/UIPhoneMap.md` (created by `guideline-uiphone-cut`) — re-measure it
      with `grep -rln 'UI\.md.*§' guidelines/ AGENTS.md .pi/skills/` rather than trusting this list. Verify: every
      line of `evidence/pre-cut-citations.txt` resolves, the retargets are recorded per file in
      `evidence/retargeted-citations.txt`, and `bash tools/check-doc-routes.sh` exits 0.

## 4. Verification

- [x] 4.1 Verify the condition clears: the `document-size` row reports **1** condition (was 2 — `Backlog` remains
      until its own cut). Record `evidence/conditions.md`.
- [x] 4.2 Verify the parity rule survived: `UI.md` §1's text is byte-identical to the baseline but for its
      references, and the row that routes a parity question still names `UI.md`. Verify: a diff against the baseline
      shows no wording change, recorded in `evidence/parity-preserved.txt`.
- [x] 4.3 Run the suites this delta owes and name the exclusions: `bash tools/check-doc-routes.sh`,
      `bash tools/check-doc-routes-selftest.sh`, `bash tools/check-rule-routes.sh`,
      `bash tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list`,
      `bash tools/check-todo-references.sh`. Excluded: no `./gradlew` target, no instrumented run, no device and **no
      `pixel-check`** — no composable, string, size or bound changes value.
- [x] 4.4 Verify the change's own hygiene: `openspec validate guideline-ui-cut --strict` valid and
      `bash tools/check-openspec-hygiene.sh` prints its OK line.

## Verification record

Every command below was run at the end of the pass, against the finished tree; the observed line is
quoted rather than summarised. Suites were run one per call.

| task | command | observed |
|---|---|---|
| 1.1 | `wc -c guidelines/UI.md` (baseline, before the cut) | `45327 guidelines/UI.md` |
| 1.1 | `python3 /tmp/ui-split.py` (per-section ruler) | `total lines 617 bytes 45327`, `sum blocks 44289` |
| 1.2 | `grep -rln 'UI\.md.*§' guidelines/ AGENTS.md .pi/skills/` | 5 files: AGENTS.md, Backlog.recipes.md, Design.md, UIPhone.md, UIPhoneMap.md → `evidence/pre-cut-citations.txt` |
| 1.3 | `grep -n '^## §' guidelines/UI.why.md guidelines/UI.recipes.md` | 5 anchors + 1 anchor → `evidence/pre-cut-companion-anchors.txt` |
| 1.4 | `grep -n 'UI\.md' AGENTS.md` | 6 table rows + 1 prose line, each assigned → `evidence/pre-cut-rows.txt` |
| 2.1 | `wc -c guidelines/UICar.md` + `--sizes` | `21562 guidelines/UICar.md`; largest section `§3a 5382 bytes` |
| 2.2 | `wc -c guidelines/UI.md` + `--sizes` | `24881 guidelines/UI.md`; largest section `§10 8816 bytes` |
| 2.3 | `/tmp/ui-evidence.py` (union + verbatim comparison) | `Union equals the baseline set: True (disjoint: True)` → `evidence/relocation.txt` |
| 2.4 | `check-doc-routes.sh` after the companion split | `all 320 section references resolve, all 2 skill references resolve` |
| 3.1 | `check-doc-routes.sh` + `wc -c AGENTS.md` | exit 0; `25138 AGENTS.md` |
| 3.2 | `check-rule-routes.sh` | `every glob matches the tree and all 575 named rule(s) are reached` |
| 3.3 | `grep -rn 'UICar\.md.*§' AGENTS.md guidelines/ .pi/skills/` | 9 lines → `evidence/retargeted-citations.txt` |
| 4.1 | `tools/sensors/run.sh --only document-size` | `verdict=violation measurement=35887` — the one remaining over-bound row is `Backlog.md` → `evidence/conditions.md` |
| 4.2 | `/tmp/ui-evidence.py` (§1 comparison) | `baseline bytes: 1716`, `after-cut bytes: 1716`, `byte-identical: True` → `evidence/parity-preserved.txt` |
| 4.3 | `tools/check-doc-routes-selftest.sh` | `all 33 cases passed` |
| 4.3 | `tools/check-rule-routes-selftest.sh` | `all 6 cases passed` |
| 4.3 | `tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list` | every case listed, including `rule-with-no-name` and `companion-anchor` |
| 4.3 | `tools/check-todo-references.sh` | `66 citation(s) resolve through the recorded table` |
| 4.4 | `openspec validate guideline-ui-cut --strict` | `Change 'guideline-ui-cut' is valid` |
| 4.4 | `tools/check-openspec-hygiene.sh` | `OpenSpec artifacts are consistent: 31 open change(s) carry task markers, …` |

Excluded, and why: no `./gradlew` target, no instrumented run, no device, no `pixel-check` — no composable,
string, size or bound changes value. The delta is documents, a routing table and one JSON map.

## Workflow follow-up

- Independent read-only review of the four cuts together, once `guideline-backlog-cut` lands.
- Archive the cuts with the retune's change.
