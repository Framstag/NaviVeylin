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
