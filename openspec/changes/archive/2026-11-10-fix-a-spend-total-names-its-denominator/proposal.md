# A spend total names the figure it is a share of

## Why

Issue **#832**. The report's trend block prints two totals that a reader cannot reconcile with the state they are
printed from, because each is a sum of one field printed as `over N items` and never beside the figure the same items
record that covers the same spend.

**Verified in the tree at this change's base `c0ddb97c`:**

- The trend sums one field per line and prints no denominator: `tools/loop/report.sh:437`–`:438` bind `$phs` from
  `metrics.phases` and `$rol` from `metrics.roles`, the phases branch (`:444`–`:456`) prints `… over N items` and no
  wall, and the role branch (`:457`–`:489`) prints the role total and never the item's `metrics.tokens`.
- Both denominators are already in the item's record. `metrics.wallSeconds` is written by `state.sh done --wall`
  (`tools/loop/state.sh:766`), and `metrics.tokens` (`tools/loop/state.sh:1055`–`:1064`) sums the usage of **every**
  turn the stream holds, while `metrics.roles` (`:977`–`:983`) folds only the turns inside the `session` records the
  `sessions` pass opens (`:942`–`:966`). A child whose stream carries no `session` record therefore spends tokens the
  role split never sees, and the two totals of one item differ by exactly those turns.
- Measured 2026-11-10 on `.pi/issue-loop/run.json` (`evidence/measure-surface.txt` carries the commands and their
  output): the 10 items that recorded phases sum to 8 187 572 ms against 16 253 s of wall they recorded, and item #833
  carries 5 440 384 tokens against 3 535 653 by role — the 1 904 731 of the difference being exactly the tokens of the
  19 turns its stream records before its first `session` record.

**Root cause, in one sentence:** the trend sums one field per line and prints it as a total `over N items` without
the figure the same items already record that covers the same spend, so a total that is part of the clock and a role
total that is part of the tokens each read as the whole and cannot be reconciled with the state.

**The red case** — `report#theSpendTotalsNameWhatTheyAreAShareOf` (`tools/loop/tests/loop-cases.sh`), run against the
base's report, one `bash tools/loop/tests/loop-selftest.sh --case
'report#theSpendTotalsNameWhatTheyAreAShareOf'` run at 2026-11-10T02:18:29Z:

```
report#theSpendTotalsNameWhatTheyAreAShareOf|   the phases' total is printed beside the wall the same items recorded, so the two reconcile: 'model 9.5 s · gate 5 s · other 0.3 s · 14.8 s over 1 item · 35 % of the 42 s the same items recorded' not found in:   phases: model 9.5 s · gate 5 s · other 0.3 s · 14.8 s over 1 item
--- report#theSpendTotalsNameWhatTheyAreAShareOf[0] (exit 1)
FAIL report#theSpendTotalsNameWhatTheyAreAShareOf
loop-selftest: 1 of 1 cases failed
```

`evidence/red-on-HEAD.log` is that run, with the case's first-line `# files:` declaration accepted before it
(`driver#everyCaseDeclaresTheFilesItCanReach` → `PASS`, in the same file). The shell runner writes no JUnit XML of its
own, so the log is the tally.

**Owning requirement:** `openspec/specs/agent-issue-loop/spec.md`, "The report says which phase and which role spent
the run's clock" (`:3275`), whose body splits the spend where it was spent; the delta adds the rule that each total is
printed beside the figure it is over.

**Owning guideline section:** `guidelines/Backlog.recipes.md:254` "What an item costs, and the bounds on it", the
paragraph at `:538` that names `metrics.phases`, `metrics.roles`, `metrics.roleModels` and `metrics.afterGate` and
states that the trend "prints the run's three sums beside each other".

surface: 1 case added, 0 rewritten, 0 fixtures realigned, 1 report file at 2 sites (the phases clause and the role
clause), 2 mutation records, 1 requirement modified (one scenario added), 1 guideline paragraph — counted before the
delta with `bash tools/loop/tests/loop-selftest.sh --list | wc -l` (**392** on the base, 393 with this change's case)
and `tools/look.sh 'phases line|role split'` (11 matches in 6 files). The record is `evidence/measure-surface.txt`.

## What Changes

1. **The `phases:` line prints the wall it is a share of.** The three buckets and their total stay byte-identical;
   the line gains `· <pct> % of the <wall> the same items recorded`, the wall being `metrics.wallSeconds` summed over
   the items that recorded phases. An item that recorded no wall prints the line it always printed.
2. **The `roles:` line prints the tokens it is a share of.** The role rows and their total stay byte-identical; when
   the items' `metrics.tokens` exceed the role total, the line gains `· of the <tokens> the same items recorded —
   <difference> (<pct> %) in turns no session record covers`. A state whose two totals agree prints the line it
   always printed.
3. **`tools/loop/tests/loop-cases.sh` gains the case** — one item whose stream opens with a child that carries no
   `session` record, so its turns are in `metrics.tokens` and in no role, and whose wall is longer than its phases:
   the two lines must print both halves.
4. **`guidelines/Backlog.recipes.md:254` gains the rule** the two figures follow, with the measurement.

## What this does not do

- It adds no field to the state and changes `tools/loop/state.sh` in no line: both denominators are fields an item
  already records, read at print time, so an archived state prints the reconciliation too.
- It does not attribute a stream's sessionless turns to a role. A role is read from the prompt path a `session`
  record carries, and a child whose stream names none has no role to charge — inventing one would be a claim about
  which role spent the tokens. The difference is printed instead.
- It does not touch the `after the first gate:` line, which already prints the share with the two numbers it is a
  share of.
