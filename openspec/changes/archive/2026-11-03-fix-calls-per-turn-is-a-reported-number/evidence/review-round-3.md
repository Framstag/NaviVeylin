# Review round 3 — fix-calls-per-turn-is-a-reported-number (issue #757)

Read-only closure review of the **committed** change, `HEAD` = `89f05d6d` ("fix: a run reports the shape it spent
its budget in (#757)") on `5e2de33e`, 2026-11-03; working tree clean; `git show HEAD` is the whole delta. One file
written: this one.

Ran: `bash -n` over `tools/loop/state.sh`, `tools/loop/report.sh`, `tools/loop-cases.sh`, `tools/loop-mutations.sh`;
the column census of the report's table; `openspec validate fix-calls-per-turn-is-a-reported-number --strict`;
`bash tools/loop/evidence-check.sh openspec/changes/fix-calls-per-turn-is-a-reported-number --root .`; and a
read-only recomputation of the archive's per-item call/turn ratios over the 227 event streams. The self-test and
`tools/loop/gate.sh` were **not** run, per this round's remit.

## Review
- Correct: the table is one shape. Header (`tools/loop/state.sh:726`), rule row (`:727`), the empty-state row
  (`:728`) and the row builder (`:769`) each carry **17 cells / 18 separators** — the leading `|` on `:769` is jq's
  own pipe, not a cell — and `table_cell 17` is the `calls` column (`tools/loop-cases.sh:8370` indexes cells as
  `n+1` of `awk -F'|'`; `:8628-8629`, `:8654` read the ratios there). `bash -n` passes on all four shell files.
- Correct: of the five P2s this round was asked to close, four are closed outright (the rule row, the floor
  sanitiser's comment, the predating state's stale digest, the "item above the floor is not named" clause) and
  the fifth, p75, is closed at two of its four sites; see section 1 for the per-finding evidence.
- Correct: `openspec validate … --strict` → `Change 'fix-calls-per-turn-is-a-reported-number' is valid`; the
  ADDED requirement's body is 385 characters, `ADDED` only, so no `MODIFIED` repeat and no `## Purpose` is owed.
- Correct: the evidence belongs to this revision — `evidence/gate.log:1544` names `HEAD 89f05d6d`, its stamps run
  `21:30:18Z`–`21:45:29Z` against a commit at `21:30:02Z`, and `gate.log:808` PASSes the new case (`:4` shows the
  file selection covering `tools/loop/**`); the loop tally is `tests="310" failures="0"` over `selected 310 of
  310`, `gate.log:888`, and the record check's is `231`/`0` at `:892`; the ten step tallies sum to the `LOOP-GATE`
  figure at `:1598`.
- Fixed: nothing — review-only pass.
- Finding: P1 ×1, P2 ×3 (section 3).
- Merge verdict: **OK with notes** — no engine line and no case assertion is wrong; two of the change's own
  record lines state a check result inaccurately, and one measured figure survives at its old value.

## 1. Round 2's findings, one by one

| round-2 finding | state | evidence |
|---|---|---|
| **P1** — `tasks.md` claimed the evidence check reports no dangling pointer; it exits 1 | **BROKEN (partly closed)** | The two classes are now stated (`tasks.md:80-91`, `proposal.md` "Reported, not repaired"), and the C2 half is exactly right — the summed tally line is carried by no XML, and the `310`/`231` pairs the change relies on are confirmed (`ok C2 test tallies: 5 pairs confirmed, 0 drift`). The C1 half is not: the check reports **11** C1 lines, two of which are `tasks.md:84` and `proposal.md:71` — the sentences that describe the pointers — so "neither in an artifact of this change" is false as written. See finding 1. |
| **P2** — the rule row carried 18 cells for a 17-column header | **closed** | `state.sh:727` now has 18 pipes = 17 cells, exactly the header's. The empty row at `:728` and the builder at `:769` agree. |
| **P2** — p75 over-corrected to 1.21 (raw 1.2158 → 1.22) | **BROKEN (partly closed)** | `tools/loop/loop.conf:129` and `guidelines/Backlog.recipes.md:318` now read 1.22, but `design.md:44` and `proposal.md:15` still read **1.21** over the same series (`p10 0.99, p25 1.05, p50 1.11, …, p90 1.32, max 1.61`). My recomputation reproduces round 2's figure exactly: n=227, p25 1.0500, p50 1.1053, p75 **1.2158** interpolated (1.2174 nearest-rank), p90 1.3236 — so 1.21 is the wrong one and it survives in two artifacts. See finding 2. |
| **P2** — `report.sh` said the floor sanitiser refuses `05` | **closed** | `tools/loop/report.sh:237` now reads "`1.` and `.` are refused; a leading zero (`05`) is read as 5, as jq reads it", which matches the pattern at `:239`. |
| **P2** — the predating-state sub-case left the state digest stale, so the mutation's red reason was the drift banner | **closed** | `tools/loop-cases.sh:8664` deletes `.stateDigest` before writing the old-shaped state, and `evidence/mutations.log:15-16` now quotes the assertion itself — "a state that predates the two counts says so, instead of naming a run of items that answered without a tool" — against the mutated `report.sh:269` selector (record `78.4`, `tools/loop-mutations.sh:749`). |
| **P2** — the "item above the floor is not named" clause lived in the scenario title only | **closed** | `specs/agent-issue-loop/spec.md:10` now carries "while the item above it is not named" in the `THEN`, and `tools/loop-cases.sh:8624` asserts it. |

Round 2's three findings that this round's list does not name are still open, all report-only: `red-on-head.log`
carries no revision or time (the base `5e2de33e` lives only in `proposal.md:23`); "worth roughly a third of the
archive's spend" (`proposal.md:19`, also as fact at `tools/loop/report.sh:229-230`) is still underived; and
`tasks.md` task 12's "`VERDICT: PASS`" and task 13's archiving checkbox are written ahead of the acts — the shipped
spec still carries no such requirement (`grep -c "shape a run spent its budget in"
openspec/specs/agent-issue-loop/spec.md` → `0`).

## 2. The scenario against the case

`SCENARIO report#anItemBelowTheCallsPerTurnFloorIsNamed` — every `THEN` clause is asserted:

| `THEN` clause (`specs/agent-issue-loop/spec.md:10`) | assertion |
|---|---|
| the run's rate with the two counts (`1.33 calls per turn (8 calls / 6 turns)`) | `loop-cases.sh:8619` |
| the item below the floor named with its own ratio (`#22 0.5`) | `:8622` |
| **while the item above it is not named** (the clause this round was asked about) | `:8624` |
| the last column reads `3` and `0.5` | `:8628`, `:8629` (`table_cell 17`) |
| a floor of `0` prints as off **and names no item** | `:8638` positive, `:8641` negative |
| an item that recorded no turn prints `-` with its reason | `:8650`, column `:8654` |
| a state written before the two counts existed says so | `:8668` |
| both items keep the outcome their work earned | `:8630` |

The floor and the two streams are the case's own (`:8584`, `:8588-8605`), and the counts are asserted on the state
before the report is read (`:8612-8615`), so no clause leans on the project's recorded floor.

## 3. Findings

1. **P1 — the change's own artifacts are among the C1 findings the task says they are not.** `tasks.md:80` reads
   "It reports two classes of finding, neither in an artifact of this change", and `proposal.md` "Reported, not
   repaired" repeats "neither carried by an artifact of the delta". A fresh run prints **11** `FAIL C1` lines;
   the change's markdown carries section-pointing lines as follows — `evidence/review-round-1.md` 2,
   `evidence/review-round-2.md` 7, `proposal.md:71` 1, `tasks.md:84` 1 — so two of the eleven are in the two
   primary artifacts, raised by the very sentences that describe the class. The retained
   `evidence/evidence-check.log` also no longer reproduces: it ends `evidence-check: FAIL (11)` with `4 pairs
   confirmed`, the tree now yields `FAIL (12)` with `5 pairs confirmed` — the log predates the last edit of
   `tasks.md` (its `tests="310"` quote in task 13 is the fifth pair, confirmed). Smallest fix: name the pointers
   without the section glyph in `proposal.md:71` and `tasks.md:84` ("sections 7b and 7e"), which both removes the
   two artifact-level hits and makes the "inside the two verbatim review reports" attribution true, then
   re-capture `evidence-check.log` so it reports the count the text claims. Nothing else in the run needs to
   change; the check still exits 1 on the nine review-report lines, so the statement that it fails stands.
2. **P2 — p75 is now quoted two ways in the same change.** `design.md:44` and `proposal.md:15` say 1.21;
   `tools/loop/loop.conf:129` and `guidelines/Backlog.recipes.md:318` say 1.22. My recomputation over the same
   227 streams (`tool_execution_end` ÷ `turn_end` per item, the set `tools/loop-spend.py` reads) gives p75
   **1.2158** interpolated and **1.2174** nearest-rank — 1.22 under either definition, so 1.21 is a rounding of
   each item's ratio *before* the percentile (1.2150), which is not the definition the neighbouring p90 1.32
   uses. Smallest fix: `p75 1.21` → `p75 1.22` in `design.md:44` and `proposal.md:15`.
3. **P2 — the shape case's own comment counts one fewer column than it asserts.** `tools/loop-cases.sh:8416` reads
   "Sixteen columns, so seventeen separators", while `:8420` requires **18** pipes on the row (17 columns), and
   the delta rewrote this case's count (17 pipes → 18) without moving the sentence that counts them. Smallest
   fix: "Seventeen columns, so eighteen separators".
4. **P2 — a log quoted as evidence carries no revision.** `evidence/gate.log:1598`'s `LOOP-GATE` line names no
   revision at all; the tree is named only incidentally by the issue-tooling step at `:1544` (`HEAD 89f05d6d`),
   and `evidence/mutations.log`'s records run `21:29:32Z`–`21:29:51Z`, some 30 s **before** the commit at
   `21:30:02Z` (the record check is re-applied by the gate, `gate.log:892`, so nothing is stale — but the log
   itself does not say which tree it measured). Smallest fix: stamp the record block with the base revision, as
   each record already carries its own time.

## 4. What I did not verify

- The self-test and the gate were not re-run, per this round's remit; the gate's own PASS of the new case
  (`gate.log:808`) and its 310/310 tally are the coverage relied on.
- `evidence-check.sh`'s C5 count (the last run before this file was added: `659 resolved and in range, 41
  unresolved or ambiguous`) is report-only by the check's own rule and was not traced line by line.
- The "3 909 of them a second read" half of `proposal.md:16-17` (needs per-session path history) still stands
  unverified, as in round 2.

VERDICT: NOTES
