# Rule inventory — `fix-loop` skill, AFTER the change

Every ID listed in `skill-rule-inventory-before.md`, mapped to the section that states it now. Measured
2026-10-10 against `.pi/skills/fix-loop/SKILL.md` (377 lines) and `.pi/skills/fix-loop/REFERENCE.md` (86).

| ID | where it is stated now |
|---|---|
| G1 | "What the loop guarantees", row 1; the mechanism is Phase A + Phase E |
| G2 | "What the loop guarantees", row 2; Phase B condition 3 |
| G3 | "What the loop guarantees", row 3; Phase B condition 5 |
| G4 | "What the loop guarantees", row 4; Phase C (revert-check task) + Phase D |
| C1–C4 | `REFERENCE.md` "Measured costs" |
| S1 | "When to use" |
| S2 | "When NOT to use" — **changed**: improvements are in scope now; `class: feature` excluded with its reason |
| B1 | "Start — the budget (the only prompt of the run)" |
| B2 | "Start", table — **changed**: the cap counts items (bugs and improvements together) |
| B3 | "Start", the state paragraph; Invariant 3 |
| P1–P5 | "Pre-screen the backlog (one pass, not eight)" |
| R1–R4, R6 | "Roles — who does what" |
| R5 | Invariant 2 "One writer per tree" |
| T1 | Invariant 3; "Recording progress" |
| T2–T5 | "Recording progress" |
| T6 | "Recording progress" (`<phase>` is one of `prescreen A B C D E F`) |
| W1–W10 | `REFERENCE.md` "Running the loop in a worktree (recipe)" |
| A1–A9 | "Phase A — root cause and the red test" |
| E1–E10 | Invariant 1 "Evidence pointers" |
| B4 | "Phase B — eligibility gate" — **changed**: seven conditions |
| B5–B8 | "Phase B" |
| C5–C11 | "Phase C — planning the `fix-*` change" |
| D1–D6 | "Phase D — apply" |
| E11–E14 | "Phase E — independent review" |
| F1–F6 | "Phase F — verify, archive, bookkeeping" |
| X1 | "Stop conditions", first line |
| X2–X4 | "Stop conditions" |
| H1–H5 | "Failure handling (never silently absorb)" |
| RP1 | "Report (end of run)" |
| PT1–PT6, PT9–PT11, PT13 | `REFERENCE.md` "Field notes" 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 (PT13 → note 10) |
| PT7 | rule lives in "Phase C" ("planning only — it must not touch project code"); no field note kept |
| PT8 | rule lives in "Phase F" step 4 and the pre-authorization paragraph; no field note kept |
| PT12 | rule lives in "Failure handling", last row (mis-scoped candidate); no field note kept |
| PT14 | rule lives in "Recording progress"; no field note kept |
| PT15 | rule lives in "Phase C" (`§ids` from max over both trees) + `REFERENCE.md` worktree bullet 2 |

**Counted:** 4 (G) + 4 (C) + 2 (S) + 3 (B1–B3) + 5 (P) + 6 (R) + 6 (T) + 10 (W) + 9 (A) + 10 (E1–E10) + 5
(B4–B8) + 7 (C5–C11) + 6 (D) + 4 (E11–E14) + 6 (F) + 4 (X) + 5 (H) + 1 (RP1) + 15 (PT) = **112 IDs**, every
one of them present in the two files. The field notes carry 10 of the 15 pitfalls; the other 5 are stated once
in the body, which is what "a rule has one normative home" asks for.

## Changed by this change (not present before)

| ID | new rule | where |
|---|---|---|
| N1 | condition 7 — an improvement's delta must be spec-owned and its red case countable | Phase B, row 7 |
| N2 | the verdicts `not-spec-shaped` and `needs-target` | Phase B row 7; Phase B's "leave the entry honest" |
| N3 | the class ranks the queue instead of gating it: bugs first, then improvements | "Pre-screen", step 2; the JS contract; "When NOT to use"; "Failure handling" |
| N4 | the third red-case shape (a missing bound/count/absence) and its revert-check mutation target | Phase A, the shapes table |
| N5 | `itemLimit` written, `bugLimit` still read — an open run survives the rename | `scripts/loop-state.sh`; `scripts/selftest.sh` assertion (b) |
| N6 | four mechanical assertions guard this document's structure | `scripts/selftest.sh`; design D5 |
| N7 | the rules file and its reference material are separate files | "Reference"; `REFERENCE.md`; design D4 |
| N8 | the report table names each item's class | "Report" |
| N9 | a `not-spec-shaped` skip gets its own honest status line | Phase B, "leave the entry honest" |

## Line counts (task 4.6 deliverable)

| file | before | after |
|---|---|---|
| `SKILL.md` | 479 | 377 |
| `REFERENCE.md` | — | 86 |
| total | 479 | 463 |

The proposal's "~320 lines" target is not met and is corrected in `design.md` (D4): the rules file alone is
377, because the widened gate added ~40 lines of new rule text (N1–N3, N8). The binding property — **no rule
lost** — is what this inventory establishes; the line count is recorded, not asserted, and `scripts/selftest.sh`
guards the structure that the dedupe produced.
