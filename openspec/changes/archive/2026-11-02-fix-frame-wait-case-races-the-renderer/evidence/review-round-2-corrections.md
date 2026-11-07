# Round 2 — what the two blocking findings changed

The review's verdict was `LOOP-CORRECTIONS-ONLY: yes` with two `BLOCKING` findings. Both are applied;
nothing else about the change moved, and the production tree is untouched (the gate of round 1 still
describes it — this round changed one test file's assertions and the change's own record).

## Correction 1 — the scenario's "one line naming the retry index" is now asserted

- Finding: `tasks.md` 2.3 and `proposal.md` claimed "one recorded diagnostic", but the case asserted only
  `assertTrue(reports.isNotEmpty())` (`AutoMapRendererFrameWaitTest.kt:240`), so a second report, or one that
  never names the retry index, passed.
- Fix: the case now asserts the sibling seam case's own form —
  `assertEquals("the armed wait recorded exactly one expired-wait line", 1, reports.size)` and
  `assertTrue(..., reports.single().contains("retry 1/${AutoMapRenderer.FRAME_WAIT_MAX_RETRIES}"))`
  (`AutoMapRendererFrameWaitTest.kt:241-246`). Nothing else in the case changed, and the scenario's THEN
  ("records one coordinate-free line naming the retry index") is now the assertion's own text.
- Why the count is 1 by construction: `frameWaitIteration` emits exactly one line per retry
  (`AutoMapRendererLoops.kt:156-160`) and the retry's re-armed wait drops at `:146` (its `armedAt` is the
  retry instant), so the chain ends with one line.
- `design.md` D3 and `tasks.md` 2.3 now say **exactly one** and name the retry index; the sibling form is
  cited as the source of the assertion.
- Record: `evidence/repeat-runs.txt` — eleven runs with the strict assertion, each read from that run's own
  JUnit XML: five single-case runs, `tests="1" failures="0"`, and six class runs,
  `tests="7" failures="0" errors="0"`. Representative copies: `evidence/case-repeat.xml`
  (`timestamp="2026-11-02T18:57:11.419Z"`) and `evidence/class.xml`
  (`timestamp="2026-11-02T18:55:18.037Z"`).

## Correction 2 — `design.md` carries the declaration

- Finding: `design.md` had no `## Open decisions` section, so `tools/check-change-readiness.sh` would have
  refused it (`declaration-missing`).
- Fix: `## Open decisions` added with `none` as its first non-empty line. `none` is the item's own record
  (`ITEM_DECISION: recorded: none`) and D1-D3 settle or eliminate their forks.
- Record: `evidence/readiness-round2.txt` — `bash tools/check-change-readiness.sh
  openspec/changes/archive/2026-11-02-fix-frame-wait-case-races-the-renderer` → `LOOP-CHANGE-READY: ok`.

## Three record repairs the corrections forced

1. `evidence/case-repeat.xml` and `evidence/class.xml` were re-taken (a run's XML cannot be kept while its
   case changes), so `design.md` Verification and `tasks.md` 4.1/4.2 quote the new timestamps and point at
   `evidence/repeat-runs.txt`, which carries every run rather than one representative — the review's NOTE
   that nine of ten timestamps were unfalsifiable.
2. `tasks.md` 4.4's pointer check now names the archived path and is carried: `evidence/evidence-check.txt`
   (`evidence-check: PASS`). To make C2 pass, the gate's own `tests=0 failures=0` pair is marked **not retained**
   in `evidence/gate.txt` and 4.3 — it is a build-dir artefact that a later `./gradlew clean` removed. The
   module tallies the gate did write and the `LOOP-MEASURE-GATE seconds=322` line are unchanged and still
   cited; no second gate was run, because only test sources changed.
3. `design.md` Non-Goals said the other six cases "never arm"; the review showed every case that has a
   surface arms through `onSurfaceCreated`'s request (`AutoMapRenderer.kt:849`). The clause now says what is
   true — they all arm, and none of them decides its expiry from the armed job — without widening the
   non-goal.

`evidence/measure-round2.txt` is the `./tools/loop/measure.sh` block taken after the archive moved (so its
`LOOP-MEASURE-TALLY` lines name the archived path); `evidence/measure.txt` keeps the round-1 block because it
is the only record of the gate's own timing.
