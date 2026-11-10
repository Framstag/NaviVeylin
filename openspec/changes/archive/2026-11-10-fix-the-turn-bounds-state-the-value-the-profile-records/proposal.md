# fix-the-turn-bounds-state-the-value-the-profile-records

## Why

`guidelines/Backlog.recipes.md` section 7b states the per-role turn bounds as the measurement `170 for the worker, 80 for
the gate child, 65 for the tail, 150 for the rework round a refusal buys, 75 for a review round and 75 for the round
before the gate` (:406-408 in the base commit) and again as the list `worker 170 (p50 77.5, …), gate 80, tail 65, review round 75, the
round before the gate 75` (:446-448 in the base commit), while `tools/loop/loop.conf` records six keys: `LOOP_CHILD_TURNS_MAX_WORKER=200`
(:176), `_WORKER_GATE=100` (:177), `_WORKER_TAIL=65` (:178), `_REVIEWER=110` (:179), `_REVIEWER_PRE_GATE=110` (:180)
and `_WORKER_REWORK=150` (:214). Two stated values are the value a run applies, four are not, and the second list has
no entry for the rework round at all; `guidelines/BacklogTooling.md` :137-139 in the base commit repeats the same four
stale values as "the turns one child may spend", so the drift has two homes and no check reads either.

**Root cause.** The guideline states the measurement that once set each bound in the same tense, and in the same
list, as the value the profile records, and nothing separates the two — so a value that moved (170 → 200 and 80 → 100
and 75 → 110, issue #804; the rework round 100 → 150, issue #842) leaves the prose behind, and no check reads
`guidelines/` for a value at all: `tools/check-doc-routes.sh` resolves routes and prices, and a value that drifts in
place is not a route.

**Red test.** `values#proseMatchesTheProfile` in the new `tools/tests/check-doc-values-selftest.sh` runs the new check
against this tree and fails on the pre-change text: pointed at a copy of `HEAD`'s `guidelines/Backlog.recipes.md` and
`tools/loop/loop.conf`, the check exits 1 with one line per role — `LOOP-CHILD-TURNS-MAX: worker prose - config 200`,
and the same for `worker-gate` 100, `worker-tail` 65, `reviewer` 110, `reviewer-pre-gate` 110 and `worker-rework` 150 —
because the section states the values in prose wording that names no key, which is the reader's defect as well as the
check's: XML `tests="1" failures="1"` (`evidence/red-on-HEAD.xml`, `evidence/red-on-HEAD.log`, 2026-11-10T09:22:48Z).

**Owning capability.** `documentation-ownership` — the requirement this change adds, "A documented bound states the
value the profile records". **Owning guideline section.** `guidelines/Backlog.recipes.md` section 7b "What an item costs,
and the bounds on it", the section `AGENTS.md`'s row "the loop's procedure — the run's phases, the bounds on a child,
…" routes.

surface: 2 guideline documents rewritten (3 passages: section 7b :406-408, section 7b :446-448, `BacklogTooling.md` :137-145), 1
check and 1 self-test added, 1 spec requirement added, 6 gate-suite pairs added, 0 cases rewritten, 0 engine lines
changed — `tools/look.sh 'LOOP_CHILD_TURNS_MAX'` (92 matches in 37 files: 2 guideline documents state the values, 1
profile records them in 8 lines, 1 engine reads them, the other 33 are cases, prompts and archived change history).
Round 2 added one passage to the same document: section 7b's four sentences that still stated the rework round's
bound now point at the table's row, so the value a reader reads is the one the check compares.
