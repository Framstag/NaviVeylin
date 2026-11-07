# fix-a-blocked-item-records-the-gate-it-ran

## Why

Every `file:line` in this section is the base `233cd286`'s: the change inserts seven lines above them (`gate_field`'s
`2354` is `2361` in this change's tree), and the current tree's numbers are the ones `## What Changes` gives.

`tools/loop/run.sh:2353`–`2357` computes the tally an item's gate produced — the child's own `LOOP-GATE` line when it
made one, the test-results XMLs the tree holds otherwise, `skipped (no gate configured)` for a project with no gate —
into `gate_field`, and exactly one of the five outcome arms passes it to the record: the `closed` arm, at
`run.sh:2767`–`2768` (`--gate "$gate_field"`). The `falsified` (`run.sh:2793`), `skipped` (`:2837`), `cut` (`:2867`) and
`blocked` (`:2932`) arms call `state.sh done` without the flag, so `metrics.gate` is written as `""`
(`tools/loop/state.sh:616`) for every item that ran the gate and then refused.

Two readers then read the absence as "the gate never ran". `tools/loop/report.sh:482`–`488` emits one action per item
whose `result` is not `done`, `falsified` or `cut` and whose `metrics.gate` is empty:

```
re-run the gate for #<issue> — the run recorded no gate result — transcript …/items/<issue>/worker-report.md
```

so a blocked item whose gate tally is already on disk and in its own report is ordered a full re-run — #798, as the
issue measures it: `worker-gate` 856 s, and `LOOP-GATE: tests=880 failures=2 errors=0` (not retained as a tally here —
another item's tree, read before this work) in both
`.pi/issue-loop/items/798/worker-gate-report.md` and `.pi/issue-loop/items/798/worker-tail-report.md`, ended `blocked`,
recorded `metrics.gate: ""` (the issue's own count over this loop's archive — 68 runs with at least one item, 371
items, 7 items quoting `tests=<n> failures=<n>` in their note beside an empty `metrics.gate` — is not re-taken here).

The same omission at a second field: `run.sh:2441` seeds `review_verdict` from the *refusal* the pre-gate round ended
in and from nothing else, so an item whose round before the gate answered `VERDICT: PASS` (or refused with a count)
and which then blocked records `reviewVerdict: "off"` and `reviewRounds: 0` — the spend of that round is in
`metrics.roles.reviewer` beside a record that says no round was read, and `report.sh:284`–`295` prints it as
`no round of this run ended in a verdict · 2 never reviewed`.

**Root cause, in one sentence:** the item's outcome record is written by five arms of one block and only the `closed`
arm passes the two facts the block computed before it — the gate tally `gate_field` and the verdict of the round the
diff was read in — so every other ending records neither, and the report reads the empty field as a measurement nobody
took.

**The red case, on the base `233cd286`** — `tools/loop-selftest.sh --case 'run#aRedGateIsBlockedByTheTail'`, with the
case as it now stands and `tools/loop/run.sh` at the base (`git diff --stat tools/loop/run.sh` empty at the time of
the run):

`run#aRedGateIsBlockedByTheTail|   the gate tally the tail read, not the emptiness: expected 'tests=1 failures=2 (the gate)', got ''` — the two numbers inside the message are the case's expected string, not a tally of a run: not retained as one.
The runner's closing lines are `FAIL run#aRedGateIsBlockedByTheTail` and `loop-selftest: 1 of 1 cases failed`.

and `tools/loop-selftest.sh --case 'run#aBlockedItemRecordsTheRoundThatReadIt'`:

```
run#aBlockedItemRecordsTheRoundThatReadIt|   the verdict of the round that read the diff: expected 'PASS', got 'off'
FAIL run#aBlockedItemRecordsTheRoundThatReadIt
loop-selftest: 1 of 1 cases failed
```

`evidence/red-on-HEAD.xml` and `evidence/red-on-HEAD-round.xml` are those two runs transcribed into a `testsuite`
(`tests=1 failures=1 errors=0` each), because the loop's shell runner writes no JUnit XML.

**Owning requirement:** `openspec/specs/agent-issue-loop/spec.md` — added, "An item's record keeps what the run
measured to decide it". It sits beside "The run verifies rather than trusts" (`spec.md:544`), whose scenario "The
gate's evidence is read by the run" (`:566`) owns the reading of the gate's evidence and whose record is where the
tally lands.

**Owning guideline section:** `guidelines/Backlog.recipes.md`:710 "Refusals, records and the landing" (`:710`–`:786`)
— the section that owns what a run records of an item that did not close. It names the reasons an item ends with and
never the metrics beside them (`:712`–`:775`), so this change owes it no edit; `design.md` says which paragraph was
read and why the rule has no home there.

surface: 1 rule file carries the tally's arm (`./tools/look.sh 'gate_field'` — 4 matches in 2 files: `tools/loop/run.sh`
3 and one archived change's evidence transcript 1), 1 further rule file carries the round's seed
(`./tools/look.sh 'review_verdict'` — 41 matches in 13 files, `tools/loop/run.sh` 21 of them, the rest archived
changes and one study), 1 case rewritten (`run#aRedGateIsBlockedByTheTail`, which asserts the new field and the action
list's absence), 1 case added (`run#aBlockedItemRecordsTheRoundThatReadIt`; `tools/loop-selftest.sh --list | wc -l`
→ 358, 357 before), 2 mutation records added (`bash tools/loop-mutations.sh --list | wc -l` → 277, 275 before),
0 fixtures realigned, 1 requirement added with 2 scenarios, 0 guideline sentences touched.

## What Changes

1. **`tools/loop/run.sh` passes `--gate "$gate_field"` in the four arms that do not close** — `falsified`
   (`:2807`–`:2809`), `skipped` (`:2852`–`:2854`), `cut` (`:2883`–`:2885`) and `blocked` (`:2949`–`:2951`), where the
   `closed` arm's own `--gate` is `:2782` — so the tally the block already computed reaches the record whatever the
   item ended as. The field's own three shapes keep their meaning: a child's `LOOP-GATE` line, the XML count the tree
   holds, and `skipped (no gate configured)`.
2. **`tools/loop/run.sh` seeds the review round from the round before the gate** when that round answered and refused
   nothing: `review_verdict` takes the round's own `PASS`/`FAIL`/`NOTES` (`run.sh:2208`–`:2210` reads it and keeps its
   blocking findings) and `review_rounds` becomes `1` (`run.sh:2448`–`:2455`), so a blocked or cut item's record no
   longer says a round nobody read.
3. **`tools/loop-cases.sh` asserts both**: `run#aRedGateIsBlockedByTheTail` gains the recorded tally and the absence of
   the gate-rerun action in `report.sh`'s list (and `tools/loop/report.sh` joins its `# files:` declaration, which is
   what the case now reaches); `run#aBlockedItemRecordsTheRoundThatReadIt` drives the same red gate with `--review on`
   and asserts the round's verdict, its count and the report's `1/1 first rounds passed` line.
4. **`tools/loop-mutations.sh` records `97.1` and `97.2`** — the blocked arm's `--gate` argument dropped, and the
   round's seed dropped — so each case is red on the arm it names.

## What this does not do

- It does not touch `tools/loop/report.sh`. The filter the issue asks for is already there: `report.sh:488` excludes
  `cut` items (issue #788) and reads `.metrics.gate` as the predicate, so recording the tally is the whole of the fix on
  that side, and a second edit would be a second place for the rule to live.
- It does not change what a run does. No arm's `result`, its escalation, its label or its branch reads
  `metrics.gate` or `metrics.reviewVerdict`: `tools/loop/report.sh` is the only reader of either
  (`report.sh:190`, `:284`–`:295`, `:488`), so the change is a record and the report it prints.
- It does not renumber or re-rank the four arms, and it does not give a `falsified` item a tally it did not take: its
  record carries `not run — the tree is unchanged`, the line its own report wrote.
