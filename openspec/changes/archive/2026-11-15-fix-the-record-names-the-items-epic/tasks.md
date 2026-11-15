# Tasks — the item's record names its epic, and the report groups by it

The items are ticked because the work is in this tree and committed: `574de125` on the base `4227aab7`, merged onto
`main` `5458bacd` as `2a30065a` (and one mode-only commit, `bcec3429`, that restores a bit the merge lost). Every
anchor below is a line of this tree.

## 1. Measure before changing code

- [x] 1.1 Read what the item's record carried and what the screen already asked for: `state.sh:786` wrote
  `issue, class, result, verdict, change, note, leftover, stopReason, metrics`, `loop_issue_json` (`lib.sh:815`)
  already asked `gh issue view` for `parent` beside `issueType`, and nothing wrote it down.
- [x] 1.2 Read the run behind the item: `runs/2026-11-14T12:19:45Z`, six items of epic #906 (#899–#904) each
  `skipped`, 4 973 919 tokens and 529.364 s of wall between them (measured with
  `jq -r '[.items[]|select(.issue>=899 and .issue<=904)] | "wall=\(map(.metrics.wallSeconds)|add) tokens=\(map(.metrics.tokens|(.input+.output+.cacheRead+.cacheWrite))|add)"'`
  on that state — the issue quotes 25 m 31 s for the same six and the state says 8 m 49 s), and no field in the state
  saying they were one epic's work.
- [x] 1.3 Read why the epic is never a row: `prescreen.sh` refuses a container before any other judgement (the
  `containerType` arm), so a run works the children and the epic's own record never appears.
- [x] 1.4 Read how the class reaches an item's record, because the epic takes the same road: `.queue[]` first and
  `.excluded[]` second — an item the tracker moves mid-run is one of the screen's exclusions — and `cmd_queue`
  (`state.sh:453`) decides which fields survive into either.

## 2. The screen records the epic

- [x] 2.1 `parent` is read from the answer the screen holds (`prescreen.sh:297-303`), not from a second call.
- [x] 2.2 The candidate's record carries it as a number or `null` (`prescreen.sh:600-609`), and the unreadable
  candidate's record carries `parent: null` (`:272`), so one shape leaves the screen.

## 3. The queue and the item's record keep it

- [x] 3.1 `cmd_queue` carries `parent` beside `class` into both `.queue` and `.excluded` (`state.sh:454-456`,
  `:465`).
- [x] 3.2 `done` reads it where it reads the class (`state.sh:924-930`), refuses anything that is not a number, and
  writes `parent` beside `change` (`:954`, `--arg p` at `:997`). A second account of the same item joins the note
  and leaves the first record's parent alone, as it leaves its class and its mechanism.

## 4. The report groups by it

- [x] 4.1 One `by epic:` line (`report.sh:368-403`), one group per epic in the record's id order:
  `epic #N: m of n children closed · <tokens> · <wall>`, and the count of the run's items in no epic.
- [x] 4.2 `n` is the run's children of that epic and not the epic's own count, and the line says so where the code
  says why: the report reads the state and git and never the tracker (`report#landingsAreTheStoredNumber`).
- [x] 4.3 A figure no child reported prints `-` with its reason (`- tokens (no child reported them)`,
  `- wall (no child recorded one)`), never the zero a sum of nothing would read as; an epic of one child says
  `child`; and a state whose items all sit outside an epic prints
  `none — no item of this run recorded an epic`.

## 5. The cases and the mutation records

- [x] 5.1 `prescreen#theScreenRecordsTheItemsEpic` (`loop-cases.sh:14837`) — the fixture `scenarios/epic/issues.json`,
  the three hops (screen → queue → item record), the two epics kept apart and an item with no parent recorded
  `null`.
- [x] 5.2 `report#theRunGroupsItsItemsByEpic` (`loop-cases.sh:14883`) — two children of #906 (one closed, one
  blocked, 2.5 Mtok and 1500 s / 31 s each), a child of #907 whose figures nobody reported, one item in no epic,
  and a second run whose items sit outside every epic.
- [x] 5.3 Mutation records `920.1`–`920.4` (`loop-mutations.sh:1367-1370`), one per hop and one for the grouping;
  run with `--only "920.1 920.2 920.3 920.4"`: `red-when-mutated: 4 · not-noticed: 0 · restore-failures: 0`
  (`evidence/mutations-920.log`).
- [x] 5.4 The review round's two hardening halves, in the same case (`loop-cases.sh:14860-14879`): a second `done` for
  the same item leaves the first record's epic alone while its note is kept (the one-record rule, #284), and a queue
  record whose `parent` is not a number is recorded `null` rather than failing the writer.

## 6. The guideline and the delta

- [x] 6.1 `guidelines/Backlog.recipes.md`, its "Leftovers, the report and the queue" section, carries the field, the
  three hops and the line, with the 2026-11-14 reading.
- [x] 6.2 The delta adds one requirement to `agent-issue-loop` with two scenarios; `openspec validate
  fix-the-record-names-the-items-epic --strict` reports the change valid, and the requirement's body is 477
  characters, under the validator's 500.

## 7. Evidence

- [x] 7.1 `evidence/surface.txt` — the diffstat and the one new fixture.
- [x] 7.2 `evidence/gate.txt` — the gate's steps, their verdicts, and the one step this base already carried red.
- [x] 7.3 `evidence/anchors.txt` — the anchors this change cites, as the commands that read them.
- [x] 7.4 `evidence/mutations-920.log` — the four records, each red on the case it names and green again.

## 8. The landing's own work

- [x] 8.1 Merged `main` (`5458bacd`) as `2a30065a`: the one conflict, an append to the mutation array both sides made,
  was resolved by keeping both sides' records; the record set stayed green (`all 401 cases passed`).
- [x] 8.2 `bcec3429` — `tools/loop/tests/loop-mutations.sh` executable again after the landing merge lost the bit; the
  harness's own selftest calls it without `bash` in front of it.
- [x] 8.3 The review round's corrections as `db591935` (the numeric-only read, the two extra assertions, the run's own
  figure in place of the one the issue quotes). The reviewer's verdict was "OK with notes" and no blocking finding;
  its four notes are addressed by 8.3 and by §9.

## 9. What this change does not cover

- [x] 9.1 The epic's own issue is still never a run item, so `n` counts the run's children and not the epic's eight
  children of #906. Reading the tracker from a report is the thing this report refuses.
- [x] 9.2 The queue order does not change: `parent` is carried and never sorted on, so no item moves rank because it
  belongs to an epic.
- [x] 9.3 The base already carried one red when this delta was written: `loop-mutations.sh --check` and
  `loop-mutations-selftest.sh` failed on three records of the #898 change, each split into a valid record and a bare
  `item` element by an apostrophe in its description (`3 of 400 checked records no longer apply`; the same
  `3 of 396` at `4227aab7`). It was not this delta's and was left standing at the time, recorded in
  `evidence/gate.txt`; the changes that landed as the merge `2a30065a` repaired those records, so
  `loop-mutations.sh --check` is `all 401 cases passed` on the merged tree and the red is gone.
