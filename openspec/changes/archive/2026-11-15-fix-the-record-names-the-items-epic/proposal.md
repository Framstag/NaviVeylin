# A run's item names the epic it belongs to, and the report groups by it

## Why

An item's record carried its class, its change, its verdict and its spend, and nothing that says which epic the item
is a piece of (issue #920). The epic itself is never worked — the pre-screen refuses a container, because its children
carry the work — so a run that worked six children of one epic printed six rows and no line said they were one epic's
work. The reading that filed the item:

- **The record had no field for it.** `tools/loop/state.sh:943` (at the base) wrote `issue, class, result, verdict, change, note,
  leftover, stopReason, metrics` and no parent; `run.sh` never asked the tracker for one and `report.sh` printed no
  grouping by one.
- **The spend of an epic's children was reachable only by hand.** In `runs/2026-11-14T12:19:45Z` six items of epic
  #906 (#899, #900, #901, #902, #903, #904) each recorded `skipped`, and the 4 973 919 tokens and 529.364 s of wall
  they cost could be totalled only by summing `.items[]` by hand or by reading six `events.jsonl` files. #906's own
  record — type `Epic`, eight children — never appears in a run at all. The issue quotes 25 m 31 s for the same six;
  the state this tree holds for that run gives 8 m 49 s, and the figure this change writes down is the one the
  command gives.
- **The field was already on the wire.** `loop_issue_json` (`tools/loop/lib.sh:818` at the base) has asked `gh issue view` for
  `parent` beside `issueType` since the issue-type work; the pre-screen held it and wrote it into no record. So this
  is a field of an answer the screen already pays for, not a second read.

## What Changes

1. **The pre-screen records the epic** (`tools/loop/prescreen.sh:298-308`, `:605-614`): `parent` is read from the
   answer the screen already holds, and written into the candidate's record as a number or `null` — a tracker without
   sub-issues and a fixture written before the field read exactly as before. The read keeps only a value whose `type`
   is `number`, so a malformed field costs the item its epic and never a `tonumber` that fails the record build. The
   unreadable candidate's record carries `parent: null` too, so one shape leaves the screen.
2. **The queue keeps it** (`tools/loop/state.sh:454-456`, `:465`): the projection that decides which fields survive
   into `.queue` and `.excluded` carries `parent` beside `class`, so the writer still has it when the item ends.
3. **The item's record carries it** (`tools/loop/state.sh:924-930`, `:954`, `:997`): read where the class is read
   (`.queue` first, `.excluded` second, because an item the tracker moves mid-run is one of the screen's exclusions),
   refused as a number or nothing, and written beside `change`. A state written before the field, an item with no
   parent and a value that is not a number all record `null`.
4. **The report groups by it** (`tools/loop/report.sh:368-403`): one `by epic:` line, one group per epic in the
   record's id order — `epic #N: m of n children closed · <tokens> · <wall>` — plus the count of the run's items in no
   epic. `n` is the run's children of that epic, not the epic's own count: the report reads the state and git and never
   the tracker. A figure no child reported prints `-` with its reason, the rule every unreported figure in this report
   keeps, and a run whose items all sit outside an epic prints `none`.
5. **Two cases and four mutation records** (`tools/loop/tests/loop-cases.sh:14837`, `:14883`,
   `loop-mutations.sh:1367-1370`):
   `prescreen#theScreenRecordsTheItemsEpic` covers the three hops the fact takes (screen → queue → item record), the
   one-record rule (a second account of the same item leaves the first record's epic alone) and a malformed parent;
   `report#theRunGroupsItsItemsByEpic` covers the line, including the absent figure, the singular `child`, the items in
   no epic and the all-`null` state. `920.1`–`920.4` drop the field at each hop and the grouping, and each case must go
   red.
6. **One fixture scenario** (`tools/loop/tests/fixtures/scenarios/epic/issues.json`): two children of #906, one item
   with no parent and one child of #907, so the screen's read and two groups are exercised from the tracker's own shape
   instead of a hand-written queue.
7. **The rule, where the backlog routes it** (`guidelines/Backlog.recipes.md`, its "Leftovers, the report and the
   queue" section): one paragraph naming the field, the
   three hops and the line, with the 2026-11-14 reading.
8. **The requirement** (`openspec/specs/agent-issue-loop/spec.md`): one `ADDED` requirement with two scenarios, so the
   record and the line are stated rather than implied by the code.
9. **One mode-only repair** (`bcec3429`): `tools/loop/tests/loop-mutations.sh` lost its executable bit when the merge
   that landed the base-red subtraction resolved the same conflict this delta's merge did, and
   `loop-mutations-selftest.sh` calls the harness without `bash` in front of it. Restored, content untouched.

## Impact

- `tools/loop/prescreen.sh`, `tools/loop/state.sh`, `tools/loop/report.sh` — one field each way and one line.
  `tools/loop/tests/loop-cases.sh`, `tools/loop/tests/loop-mutations.sh`, one new fixture scenario under
  `tools/loop/tests/fixtures/scenarios/epic/`. `guidelines/Backlog.recipes.md`, the "Leftovers, the report and the
  queue" section — one paragraph.
- The state gains one more field per item record (`parent`), so a reader of the state sees what the report groups by;
  a record written before the field, or an item with no epic, carries `null`, which is the field's absence for every
  reader. No engine value, bound or exit code moves, no queue order changes (the field is carried and never sorted on),
  and no Android module, native boundary or UI surface is touched. Rollback is the revert of these files.
- Deviation left standing: the epic's own issue is still never recorded as a run item, so `n` counts what the run
  worked and not the epic's eight children. Reading the tracker from a report is the one thing
  `report#landingsAreTheStoredNumber` refuses, and the line says which children it counted instead.

## Evidence

- The implementation: `574de125` on the base `4227aab7`, the review round's corrections `db591935`, the merge onto
  `main` `5458bacd` (`2a30065a`) and the mode-only repair `bcec3429`.
- Cases: `prescreen#theScreenRecordsTheItemsEpic`, `report#theRunGroupsItsItemsByEpic`.
- Mutations: `920.1`, `920.2`, `920.3`, `920.4`, each red on the case it names and green again after its restore.
- The gate, the anchors this change cites and the mutation log: `evidence/`.

surface: 7 paths, +299 −5 against `main` `5458bacd` (`git diff --stat`, in `evidence/surface.txt`) — of which +166 −5
is the change's own base `4227aab7`: `tools/loop/report.sh` +33, `tools/loop/state.sh` +18/−2,
`tools/loop/prescreen.sh` +13/−3, `tools/loop/tests/loop-cases.sh` +106, `tools/loop/tests/loop-mutations.sh` +8, one
new fixture scenario (`tools/loop/tests/fixtures/scenarios/epic/issues.json`, 4 items), `guidelines/Backlog.recipes.md`
+15; one mode-only repair of `loop-mutations.sh`; 1 requirement and 2 scenarios added to 1 delta file, 0 shipped
requirement bodies rewritten, 0 source files of the app touched.
