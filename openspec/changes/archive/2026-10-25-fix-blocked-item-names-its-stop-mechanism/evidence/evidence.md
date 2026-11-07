# Item #280 — evidence

**Verdict: closed.** Change `fix-blocked-item-names-its-stop-mechanism`, archived as
`openspec/changes/archive/2026-10-25-fix-blocked-item-names-its-stop-mechanism` on base `85d02943`.
The decision recorded on the item (2026-10-24T17:06:19Z) is **option 1** — keep both stop rules and record a
distinct stop reason per mechanism — and that is exactly what landed: no stop rule moved, no requirement was
reversed, the record gained the mechanism.

## Phase A — the condition, verified in the tree

Both mechanisms reach the item record as the one word `blocked`: `tools/loop/run.sh:1467` (the last review round
refused the change) and `tools/loop/run.sh:1500` (the branch could not be merged onto the base) both write
`--result blocked --verdict blocked` at `tools/loop/run.sh:1646`, and `state.sh done` has no field for the
mechanism (`tools/loop/state.sh:436` lists the four results). The distinguishing fact lived only in `note` prose.

Re-measured over the archive (`python3 .pi/issue-loop/items/280/probe-tokens.py`, output in `measurement.txt`):
142 attempts, 128 with usage, 1154.9 M tokens — 10 refusing rounds **198.8 M / 17.2 % / mean 19.88 M / 2724 s**,
1 unmergeable attempt **22.9 M / 2.0 % / 2840 s**, against a closure's 738.7 M / 15.08 M mean. The item's
20-blocked / 289 M reading was taken 2026-10-23; the symptom is stronger now, and a refusal block costs 32 % more
than a closure.

**Root cause (one).** The record has no field for the mechanism, so the note's prose is the only place the two
blocks differ and every figure derived from the state can only total `blocked`.

**Red test, on HEAD `85d02943`, 2026-10-24T23:34Z** — `red-cases-head.log`, re-runnable as `red-cases-280.sh`;
each case is one named selection of `tools/loop-selftest.sh`:

```
state#aBlockedItemNamesTheMechanismThatStoppedIt  loop: done: unknown argument --stop-reason       1 of 1 cases failed
run#reviewRoundsCapAtTwo                          expected 'refused', got 'null'                  1 of 1 cases failed
run#doesNotCloseOnAMergeTheBaseCannotTake         expected 'unmergeable', got 'null'              1 of 1 cases failed
report#wallTimeAndReasonInTheTable                '| stop |' not found in the header              1 of 1 cases failed
```

**Owning requirement / guideline.** `openspec/specs/agent-issue-loop/spec.md` gained one requirement, *A block
records the mechanism that stopped it* (now `openspec/specs/agent-issue-loop/spec.md:2105`); the guideline is
`guidelines/Backlog.md` §7 "The loop", the paragraph on the report (`guidelines/Backlog.md:496`).

**Surface, counted.** `grep -rln 'stop-reason\|stopReason\|failed its last review round\|could not be merged onto
the base' tools .pi/skills openspec/specs guidelines` → 3 files before the change (all the loop's own:
`tools/loop/{run.sh,loop-cases.sh,loop-mutations.sh}`), `tools/loop/state.sh` the fourth the field lands in.
Landed: 1 case added, 3 cases extended, 4 mutation records, 1 state field + flag, 1 report column, 1 guideline
paragraph — 130 insertions over 6 files (`git diff --stat`).

## The gates and runs

| step | result | artifact |
|---|---|---|
| four red cases on HEAD | 4 × `1 of 1 cases failed` | `red-cases-head.log` |
| four cases on the tree | 4 × `PASS` | `green-cases.log` |
| mutation `58.1`–`58.4` (refusal site, merge site, state write, report column) | `red-when-mutated: 4 · not-noticed: 0 · restore-failures: 0`, 23:36:37Z–23:37:33Z | `revert-check.log` |
| `./tools/loop/gate.sh` (once) | `LOOP-GATE: 529 cases · 0 failures · 0 errors` over sixteen suites, 23:38:23Z–00:01:56Z (build 229 s — owed because the gate's base `db888dd8` predates earlier changes' app sources; loop suite 219 cases by `--from-git`) | `gate.log`, `gate/` |
| `evidence-check.sh` (archived change) | `evidence-check: PASS` (552 `file:line` resolved) | `evidence-check.log` |

## What the change is

- `tools/loop/state.sh` — `done --stop-reason refused|unmergeable` (closed vocabulary; a token outside it, or a
  mechanism on a result that is not `blocked`, is a usage error before anything is written) recorded as
  `.items[].stopReason`; the item table gains a `stop` column appended after `reason`, so no earlier column moved.
- `tools/loop/run.sh` — the two mechanism sites name themselves (`refused` at the last review round,
  `unmergeable` at the merge refusal) and the blocked `done` call hands the value through; every other block sets
  nothing, so an unrelated block records no mechanism.
- `tools/loop-cases.sh` — `state#aBlockedItemNamesTheMechanismThatStoppedIt` plus the three cases extended to the
  mechanism their own block stopped on.
- `tools/loop-mutations.sh` — records `58.1`–`58.5`, one per clause, each noticed by its named case.
- `guidelines/Backlog.md` §7 — the report paragraph names the mechanism column beside the spend.

Additive: a state written before this change carries no `stopReason` and reports `-` in the new column; nothing
in `report.sh`, `report.sh --json`, the pre-screen or any archived run's reading changes.

## The loop's own measure block (from this run)

    LOOP-MEASURE-CHECK ./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache
    LOOP-MEASURE-CHECK tools/loop-selftest.sh --from-git db888dd8b7c509cfba82854a21427ae5c9c8e192
    LOOP-MEASURE-CHECK bash tools/loop-mutations.sh --check
    LOOP-MEASURE-CHECK tools/loop-mutations-selftest.sh
    LOOP-MEASURE-CHECK tools/issue-tooling-selftest.sh
    LOOP-MEASURE-CHECK tools/issues/check-metadata.sh
    LOOP-MEASURE-CHECK tools/issues/metadata-mutations.sh --list
    LOOP-MEASURE-CHECK tools/check-compose-sandbox-rule.sh
    LOOP-MEASURE-CHECK tools/check-compose-sandbox-rule-selftest.sh
    LOOP-MEASURE-CHECK bash tools/gen-feature-list.sh --check-classification
    LOOP-MEASURE-CHECK tools/check-doc-routes.sh
    LOOP-MEASURE-CHECK tools/check-doc-routes-selftest.sh
    LOOP-MEASURE-CHECK tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list
    LOOP-MEASURE-CHECK tools/check-todo-references.sh
    LOOP-MEASURE-CHECK tools/check-openspec-hygiene.sh
    LOOP-MEASURE-CHECK tools/check-openspec-hygiene-selftest.sh
    LOOP-MEASURE-CHANGE 2026-10-24-fix-sensor-tick-gap-selection scenarios=13 cases=8 unnamed=5
    LOOP-MEASURE-SURFACE none — the proposal carries no surface: line
    LOOP-MEASURE-END

(The block measured the newest archived change because no change was open when it ran; the change this item
planned is `fix-blocked-item-names-its-stop-mechanism`, whose proposal carries its own counted `surface:` line.)

## One pre-existing warning, not this change's

`openspec validate agent-issue-loop --type spec --strict` exits 1 on **seven** requirements longer than 500
characters (indices 3, 6, 19, 27, 28, 36, 37, 38). The change's own delta validated strictly (`Change
'fix-blocked-item-names-its-stop-mechanism' is valid`), and the requirement this change added is index 62 at 454
characters — none of the warnings is it, and all seven predate the base commit. The change-level artifacts, the
hygiene check and `check-doc-routes.sh` are green.

## Correction round 1 (the review of round 1 refused the change)

Two BLOCKING findings, both the item's own subject — a scenario whose second THEN line no case read:

1. **The `stop` column printed an empty cell, not `-`.** `cmd_done` wrote `stopReason:$sr` with `""` as the
   default, so a closure and a mechanism-less block stored `""`; the row interpolated `(.stopReason // "-")`, and
   `jq`'s `//` yields `""` for an empty string. The case passed because its assertion (`| done | - |`) matched the
   **reason** column, which prints `-` for every closure. Fixed in `tools/loop/state.sh:476` (record `null` when no
   mechanism is given) and `tools/loop/state.sh:599-603` (print `-` for `null` and for `""`), and asserted by
   reading column 15 of the row itself.
2. **`the state is not written` was asserted nowhere.** Fixed in
   `tools/loop-cases.sh:1183-1206`: the state file is compared byte for byte before and after each of the two
   usage errors, and a second account of item 23 shows it fills no mechanism the first record left empty.

The mutation records moved with the code: `58.3` nulls the record's mechanism, `58.4` makes the column print `-`
always, `58.5` makes it print an empty cell — the round-1 `58.4` mutated the whole interpolation, produced `\-`
(invalid jq), and the report died instead of the column going missing.

The round-1 shape (`"stopReason": ""`) has no producer in the tree — no state under `.pi/issue-loop/runs/`
carries the key at all, because the change has not yet run in a real run — so it is measured by a probe rather
than a case: the report prints `-` for it in column 15 (`stop-reason-probe.log`), and a case could not write that
shape without editing a state `state.sh` had just written.

| step | result | artifact |
|---|---|---|
| the corrected cases on the pre-correction tree | `report#wallTimeAndReasonInTheTable` `while a closure's stop column prints -: expected '-', got ''`, exit 1, 2026-10-25T00:06:09Z; `state#…` passes there too (its clause was true and only unasserted) | `red-cases-head-round2.log`, `red-cases-280-round2.sh` |
| the corrected cases on the tree | 2 × `PASS`, exit 0, 2026-10-25T00:06:20Z–00:06:23Z | `green-cases-round2.log` |
| the affected groups | `report` 15 cases, `state` 18 cases, all pass | quoted in `tasks.md` 6.2 |
| five mutation records | `red-when-mutated: 5 · not-noticed: 0 · restore-failures: 0`, 2026-10-25T00:07:46Z–00:08:46Z | `revert-check-round2.log` |
| each record's failing assertion | `expected 'refused', got 'null'` (58.1–58.3), `expected 'refused', got '-'` (58.4), `expected '-', got ''` (58.5), every restore clean | `revert-check-round2-messages.log` |
| a block with no mechanism, as stored and as printed | `{"result":"blocked","stopReason":null}` and `| blocked | a block with no mechanism | - |`; a record carrying the round-1 shape (`"stopReason": ""`) prints `-` in the same column, 2026-10-25T00:35:22Z | `stop-reason-probe.log` |
| `./tools/loop/gate.sh` (once, started after the mutation run ended at 00:08:46Z, its log last written 00:34Z; the log's own stamps run 00:15:07Z–00:33:52Z) | `LOOP-GATE: 530 cases · 0 failures · 0 errors` over sixteen suites (`./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache` 227 s; `tools/loop-selftest.sh --from-git db888dd8…` 971 s, 219 cases; `tools/loop-mutations.sh --check` 150 records; `tools/loop-mutations-selftest.sh` 34 cases; the rest as the profile names them) | `gate-round2.log`, `gate-round2/` |

Stale counts corrected in the archived artifacts: `proposal.md` says 5 mutation records over 6 files (it said 3
and 5), `design.md` says the field records `null` (it said "absent") and carries the correction section,
`tasks.md` 2.1/2.2/3.3 record what actually asserts the clauses and 4.2 says 454 characters (not 499). This file
now lives in the change's own `evidence/` as well as in the item directory.
