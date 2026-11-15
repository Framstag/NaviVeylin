# The report states what the run recorded, not what it can re-derive

## Why

Two of the trend block's figures and the refused landing a block reports were not read from the record: they were
re-derived, each time the report ran, from text the run had already written or from the clock the report happened to
run at (issue #883). `report.sh` read figures back out of prose — the note's branch, the paths after a refusal's `onto
the base:`, a window dated from the report's own start — so a report could print a landing, or a span, that the run's
own record did not hold, and a second reading of the same run could differ from the first without anything having
changed.

The reading that made it visible:

- **The gate share of an item's wall was never stated.** The trend block printed the phase line's five buckets and each
  item's wall, so a reader had to divide two printed columns to get the only figure that says how much of an item's
  time the gate took — and the gate is the phase the loop is meant to shrink. Run `2026-11-13T05:47:11Z`:
  `#853 gate 4568 s (80 %) of 5709 s`, `#865 60.4 %`, `#864 58.8 %`. Two of the three differ only in the tenth, so a
  figure printed as a whole percent tells them apart not at all.
- **The window the items do not account for was not stated either.** The same run's window was 45 173 s against item
  walls of 22 925 s: 22 247 s, 49.3 %, outside every item. The report named the items' wall and the window and left
  their difference to the reader.
- **The refused landing was printed from a second reading of prose.** A block whose item the merge refused printed the
  branch and the paths the run had already composed into a note, parsed out of that text at report time. Nothing in the
  record held the refusal, so nothing but the note could be handed to a caller — and a note is a sentence about a
  refusal, not the refusal.

All the fields were already in the state: `metrics.wallSeconds`, the gate bucket, the recorded start and stop, and the
merge's own outcome. Neither line needed a new field in the engine contract; the refusal needed the run to hand over
what it had, which is one record.

## What Changes

1. **The window is the recorded stop minus the recorded start** (`tools/loop/report.sh:164-171`): `window_secs` reads
   `.run.start.startedAt` and `.run.stop.when`, so the span a report prints is one the state holds. A run whose stop the
   state does not carry prints a dash, not a span dated from the report's own clock.
2. **`gate share:`** (`:626-644`) — a `jq` sum over the items that recorded a gate: each item's gate bucket divided by
   its own `metrics.wallSeconds`, in tenths, naming the worst two items. In tenths because 60.4 % and 58.8 % are told
   apart only there. An item that recorded no gate is in no rate, and a run of none prints a dash.
3. **`outside items:`** (`:645-659`) — the window minus the items' own walls, with the share of the window it is, so
   the figure the reader was left to subtract is printed beside the two figures it is the difference of; a wall that
   runs past the window is said to disagree rather than printed as a negative share.
4. **Both lines are printed in the trend block** (`:762`) beside the phase line, in sums the report can and does name as
   its own: this is the one place the report still does arithmetic, and it is stated as a derivation over recorded
   fields.
5. **The run records the refusal it met** (`tools/loop/run.sh:486-487`, `:513-521`, `:542-545`): `merge_committed_branch`
   keeps the branch it could not land and the lines after `onto the base:` that name the paths, and the arm where the
   base could not be recorded keeps the branch alone. `:4145-4165` hands both to `state.sh done` as
   `--merge-refused-branch` / `--merge-refused-path`.
6. **The state writes what it was handed** (`tools/loop/state.sh:731-732`, `:824-840`, `:924-928`, `:952`, `:996`):
   `mergeRefused: {branch, paths}` beside `merged:`, a path with no branch refused, a branch with a stop other than
   `unmergeable` refused, a path carrying a line break refused — so a refusal cannot enter the record half-formed.
7. **The report prints the record, and says when it cannot** (`tools/loop/report.sh:837-893`): `refusal_kind` is
   `record` when the state holds one and the branch and paths printed are read from it verbatim; `absent` or `empty`
   when it holds none, where the block falls back to the note and says that what it printed is re-derived — for the
   state an older run wrote, which has no field.
8. **Two new cases and one extended** (`tools/loop/tests/loop-cases.sh`):
   `report#theTrendBlockNamesTheGateShareAndTheOutsideWindow` (`:14542`) and
   `report#theRefusedLandingIsReadFromTheRecord` (`:13357`), plus `report#anUnmergeableItemNamesItsLanding` (`:13283`)
   extended to read the recorded refusal. Registry entries `:372`, `:394-395` and `:1120`, `:1139-1140`.
9. **Three mutation records** (`tools/loop/tests/loop-mutations.sh:1360`, `:1361`, `:1362`): `883.1` dates the window
   from the clock since the run started; `883.2` reads the landing back out of the note; `883.3` stops `state.sh`
   writing the refusal at all.
10. **The rule, where the backlog routes it** (`guidelines/Backlog.recipes.md:749-761`): one paragraph beside the phase
    line's — the gate share and the outside-items window are read from the record, not from a log, with the 2026-11-13
    reading, the two figures' arithmetic and the refusal record.
11. **The requirements** (`openspec/specs/agent-issue-loop/spec.md`): two `MODIFIED` blocks — "The report states
    whether the loop is getting better" (`:3083`) and "An item a merge refused names its landing" (`:4838`) — each
    shipped requirement carried verbatim, then the paragraph and scenario that own the record.

## Impact

- `tools/loop/report.sh` — the window's source and the two figures and their printed lines; the refusal branch of the
  block reads the record and names its fallback. `tools/loop/run.sh` — the refusal captured where the merge met it and handed
  to the item's record. `tools/loop/state.sh` — one field, with the three refusals that keep it whole.
  `tools/loop/tests/loop-cases.sh` — two cases and one extended case. `tools/loop/tests/loop-mutations.sh` — three
  records. `guidelines/Backlog.recipes.md` — one paragraph.
- The state gains a field (`items[].mergeRefused`), so a reader of the state sees one more thing an item may carry; a
  run that wrote no refusal writes `mergeRefused: null`, which is the field's absence for the report's purposes. No
  engine value, bound or exit code moves. Rollback is the revert of these five files; no Android module, native
  boundary or UI surface is touched.
- Deviation left standing: `report.sh` still does two subtractions (the gate share and the outside-items figure). They
  are stated as derivations over recorded fields in the trend block itself, and the requirement says so — a report that
  could not total what it prints would have nothing to print.

## Evidence

- The implementation: commit `86fec1a5`, merged into the base as `cda0fc5b`. The change was written against the
  shipped tree, so the implementation landed first and this change names it.
- Cases: `report#theTrendBlockNamesTheGateShareAndTheOutsideWindow`,
  `report#theRefusedLandingIsReadFromTheRecord`, and `report#anUnmergeableItemNamesItsLanding` for the refused landing
  it now reads.
- Mutations: `883.1`, `883.2`, `883.3`.
- Readings, anchors, the 2026-11-13 figures and the gate verdict this change can and cannot produce:
  `evidence/readings.md`.
