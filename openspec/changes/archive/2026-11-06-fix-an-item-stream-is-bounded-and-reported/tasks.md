# Tasks — the item's kept stream is bounded, and its leftovers are reported

## 1. Diagnosis and the red cases

- [x] 1.1 Measure the archive this item is about: `python3
      openspec/changes/fix-an-item-stream-is-bounded-and-reported/evidence/archive-census.py .pi/issue-loop/items`
      → `items=264 events-files=254 total=3483MiB p50=11.84MiB p90=29.88MiB max=63.47MiB`,
      `bound=16MiB kept-sum=2573MiB`, `days=14 dirs=105 MiB=1411`, `all item-dirs MiB=3563`
      (`evidence/archive-census.log`).
- [x] 1.2 Verify the entry against the base `007ac561`: the append is `tools/pi-child-events.sh:117`, the only bound
      in the path is `LOOP_TOOL_RESULT_MAX_CHARS` (a result the child sees), and `loop-clean.sh` has no arm over
      `items/` (its five arms are build paths, scratch trees, branches, worktrees and change sources).
- [x] 1.3 Three cases were written first and fail on the base with the engine change absent:
      `clean#reportsAnAgedItemDirectory`, `adapter#theKeptStreamIsBoundedAndItsRotationIsRecorded`,
      `run#aRotatedStreamStillCountsTheTurnsItDropped` — `1 of 1 cases failed` each, `tests=3 failures=3`
      (2026-11-06T18:48:07Z–18:49:24Z; `evidence/red-on-HEAD.log`, `evidence/red-on-HEAD-clean.log`,
      `evidence/red-on-HEAD.xml`).
- [x] 1.4 Route: spec `agent-issue-loop`; `guidelines/Backlog.recipes.md`:212 "What an item costs, and the bounds
      on it" (the turn bound reads the stream) and `:764` (what the report answers for).
- [x] 1.5 Count the surface before the plan: `tools/look.sh
      'events\.jsonl|LOOP_CHILD_EVENTS|child_turns|LOOP_ITEM_ARCHIVE_DAYS|item directories'` → 153 matches in 49
      files, 11 live (run.sh 13, loop-cases.sh 46, pi-child-events.sh 8, loop-clean.sh 7, loop.conf 7, and five
      more); the delta is 3 cases added, 0 rewritten, 4 mutation records, 2 guideline sections.

## 2. The change

- [x] 2.1 `tools/pi-child-events.sh`: read `LOOP_CHILD_EVENTS_MAX_BYTES` (environment, else `loop.conf`), keep a
      running byte count seeded from the size the file already has — so a stream already past the bound is rotated by
      the first event a child appends — and `rotate_events` past it: newest half of the lines kept, `stream_rotation`
      record written first with cumulative `dropped_turn_ends`.
- [x] 2.2 `tools/loop/run.sh`: `child_turns` adds the records' `dropped_turn_ends` to the `turn_end` lines present.
- [x] 2.3 `tools/loop/loop-clean.sh`: the item-directory arm, with the age gate, the state reading, `--apply` as the
      only removal path, and the `0` switch.
- [x] 2.4 `tools/loop/loop.conf`: `LOOP_CHILD_EVENTS_MAX_BYTES=16777216` and `LOOP_ITEM_ARCHIVE_DAYS=14`, each with
      the census that produced it.
- [x] 2.5 `tools/loop-cases.sh`: the three cases, their list and dispatch entries, and the `clean#` declaration
      (`tools/pi-child-events.sh` through `new_project`) — `driver#everyCaseDeclaresTheFilesItCanReach` PASS. The
      adapter case also stages a stream written past its bound *before* the child starts, and bounds the kept file
      at the bound plus one line rather than at twice the bound (the round before the gate, #802).
- [x] 2.6 `tools/loop-mutations.sh`: records 89.1–89.4, one per invariant; `--check` over the three changed engine
      files → `all 104 cases passed`.

## 3. Verification

- [x] 3.1 The three new cases are green on the change: `PASS` each, 18:49:31Z–18:49:42Z
      (`evidence/green-on-change.log`).
- [x] 3.2 The affected suite: `tools/loop-selftest.sh --files 'tools/pi-child-events.sh tools/loop/run.sh
      tools/loop/loop-clean.sh tools/loop/loop.conf' --jobs 4` → **all 339 cases passed** in 463 s
      (19:00:34Z–19:08:18Z, `evidence/scoped-suite.log`).
- [x] 3.3 The four mutation records, run for real: `red-when-mutated: 4 · not-noticed: 0 · restore-failures: 0`
      (19:10:56Z, `evidence/mutation-89.log`).
- [x] 3.4 The structural checks of a `tools/loop/**` delta: `tools/check-portability.sh` → `OK — 56 engines and 7
      templates name no project value`; `tools/mutate-selftest.sh` → `36 passed, 0 failed`;
      `tools/check-doc-routes.sh` → `all 329 section references resolve, all 5 skill references resolve`;
      `tools/check-todo-references.sh` → 66 citations resolve; `tools/check-openspec-hygiene.sh` → consistent
      (`evidence/structural-checks.log`).
- [x] 3.5 The change is ready: `tools/check-change-readiness.sh
      openspec/changes/fix-an-item-stream-is-bounded-and-reported` → `LOOP-CHANGE-READY: ok`; `openspec validate
      fix-an-item-stream-is-bounded-and-reported --strict` → `is valid`.
- [x] 3.6 The gate is not this session's: it is deferred to the fresh child that follows the reviewer, so no
      `./tools/loop/gate.sh` was run here.
