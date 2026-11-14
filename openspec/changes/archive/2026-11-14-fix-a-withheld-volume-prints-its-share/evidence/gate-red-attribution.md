# Gate red attribution — item #866, phase F (tail worker, 2026-11-14)

Gate line handed to the tail, never re-run:
`tests=1068 failures=1 errors=0 … — failing: tools/loop/tests/loop-selftest.sh --from-git fcbe328dd1ed75c0863bc43ad7de0f8a734f638e`

## The failing step, named

- **Suite:** `tools/loop/tests/loop-selftest.sh --from-git fcbe328dd1ed75c0863bc43ad7de0f8a734f638e`
- **Tally (this gate, run=2360277):** `tests="427" failures="1" errors="0" time="841"` — system-out
  `loop-selftest: 1 of 427 cases failed — 838.93 s`. Carried here as
  `gate-xml/tools_loop_tests_loop-selftest.sh_--from-git_fcbe328dd1ed75c0863bc43ad7de0f8a734.xml`
  (the same copy is in `.pi/issue-loop/items/866/gate-xml/`).
- **Case:** `FAIL phase#aResultOverTheBoundIsCutAndNamed` — reason in this item's `gate-run.log:1330`
  `the package's head is part of the answer: … 'PKG-LINE-001' not found in: … [PHASE-BOUND: cut at 4000
  characters — the phase state is printed first, so a cut takes the work package's tail; …]` (`:1369`).
  The phase state fills the 4 000-character bound, so the cut takes the work package's head. A `phase#…`
  case lives in `tools/loop/phase.sh`'s self-test — loop tooling.

## Red before this work

`gate-red-before-item-862-gate-run.log` — item #862's gate, **mtime 2026-11-13T23:53:00Z**
(`2026-11-14 00:53:00 +0100`), i.e. before this change existed (run base `d25972c1`, change commit
`d87249c4` after it): same suite, same base `fcbe328dd1ed75c0863bc43ad7de0f8a734f638e`,
`:1272` `FAIL phase#aResultOverTheBoundIsCutAndNamed`,
`:1328` `loop-selftest: 1 of 427 cases failed`,
`:1329` `gate: tools/loop/tests/loop-selftest.sh --from-git fcbe328dd1ed75c0863bc43ad7de0f8a734f638e — 869s`.
The same leg was already waived once at item #880 with the same case named
(`openspec/changes/archive/2026-11-14-report-a-stale-map-install/evidence/gate-red-attribution.md`).

## Red after this work / outside this change's paths

- Stays red at **this** item's gate: the `--from-git` XML above, `failures="1"`, run=2360277.
- The change (`git diff --name-only d25972c1..HEAD`) touches exactly four files:
  `guidelines/Backlog.recipes.md`, `tools/loop/report.sh`, `tools/loop/tests/loop-cases.sh`,
  `tools/loop/tests/loop-mutations.sh`. Of the case's declared inputs
  (`tools/loop/phase.sh tools/loop/prompts/worker.md tools/loop/loop.conf tools/loop/lib.sh
  tools/loop/state.sh tools/loop/tests/loop-cases.sh tools/loop/tests/loop-selftest.sh
  tools/pi-child-events.sh tools/tooling.json`) only `loop-cases.sh` intersects — and its whole diff is
  one hunk `@@ -12840,12 +12840,23 @@` inside `report#anItemRecordsItsRepeatReads`;
  `case_phase_aResultOverTheBoundIsCutAndNamed` (line 16560) and `phase_fixture` are untouched.
- The bound the failing assertion measures is `LOOP_TOOL_RESULT_MAX_CHARS=4000` in
  `tools/loop/loop.conf`, introduced by commit `419d482c fix: a tool result is bound at 4 000 …` —
  an **ancestor of the run base** (`git show d25972c1:tools/loop/loop.conf` prints
  `LOOP_TOOL_RESULT_MAX_CHARS=4000`), i.e. in the tree before this work.
- The change's own region is green in the same gate: `gate-run.log:1237`
  `PASS report#anItemRecordsItsRepeatReads`, `bash tools/loop/tests/loop-mutations.sh --check` green
  (358 records), gradle green.

Waived clause: `LOOP-GATE-WAIVED`, suite: `LOOP-GATE-WAIVED-SUITE` — both in
`.pi/issue-loop/items/866/worker-tail-report.md`.

## The round's own gate — run=919045, and the tallies of the two runs

The gate this section above attributes is the run's **first**, which resolved its base from the stale
`origin/main` (`fcbe328d`) and so measured a tree that was not the round's. Round 2's rework ran a second gate with the
base named, and that one is in this archive as `evidence/gate-run-round2.log`:

- its `LOOP-GATE-TREE` line records `head=3fbec6ee… diff=cb188f9396ef51ef`, and that `diff=` is the figure
  `review-2.diff.identity` records for the tree the round reads — equal field for field, which is the comparison
  `guidelines/Backlog.recipes.md` §7c makes and round 1 refused for;
- its `LOOP-GATE-TALLY` line records `run=919045`;
- its failing step is `tools/loop/tests/loop-selftest.sh --from-git d25972c1d85a990eb8405108c70bbe419c930eb7` — the
  run's own base, not the stale `fcbe328d` — and its **single** failure is the same case, `FAIL
  phase#aResultOverTheBoundIsCutAndNamed`. The red attribution above therefore stands unchanged: the same case, the same
  `PKG-LINE-001` assertion, on the tree this round reads.

The per-step tallies of the two runs are archived apart, because they are two runs and an XML names its own run in its
`run=` attribute, which is not edited here:

| directory | run | leg | note |
|---|---|---|---|
| `evidence/gate-xml/` | `2360277` | `--from-git fcbe328d` | the first gate's, the one quoted above |
| `evidence/gate-xml-round2/` | `919045` | `--from-git d25972c1` | round 2's, 23 per-step tallies, copied after this round |

The second copy needed its own name because `app/build/test-results/loop-gate` is **one** directory that every gate run
reuses: round 2's run had already written over run 2360277's files there, so copies under the names the first ones
carry would have replaced one run's tallies with the other's. Neither the archiving nor this note moves the round's
identity — `LOOP_REVIEW_EXCLUDE_PATHSPEC` leaves `**/evidence/**` out of it.

One figure the directories carry only as a sum: the **total** quoted at the top of this file is the gate's own sum over
its steps, and no single XML under either directory carries it — each directory's XMLs add up to it, and both runs' do,
because the round's gate met the same suite. So `tools/loop/evidence-check.sh` reports that one pair as `C2 DRIFT`
(the check looks for one XML carrying the pair) and confirms every per-step pair; that is what
`evidence/evidence-check-final.txt` records, after the copies, and it is a limitation of the check's carrier rule and
not a tally the archive lacks.
