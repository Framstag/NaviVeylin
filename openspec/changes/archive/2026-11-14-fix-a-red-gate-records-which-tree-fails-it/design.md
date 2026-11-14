# Design — a red gate records which tree fails the case it names

## The root cause

`tools/loop/run.sh:3059` decides a red gate with `gate_tally_state` (`tools/loop/lib.sh:113`) — `red | green |
unknown`, read out of `failures=` / `errors=` — and the arms that write the record pass the tally's prose alone
(`loop_state done … --gate "$gate_field"`). Nothing asks whether the case the red names also fails at the base commit,
so `metrics.gate` is the whole of what a reader gets and every red is the same verdict.

## The single fix

One field, one measurement, one place:

1. **`tools/loop/gate.sh` writes the log it prints.** The gate already owns every fact `tools/gate-attribute.sh` reads
   (its step blocks, its `FAIL <group>#<name>` lines, its `LOOP-GATE:` verdict), and it holds the tally directory the
   profile names. It now writes that same text to `<LOOP_GATE_TALLY_DIR>/gate.log` beside the per-suite tallies and
   names it with `LOOP-GATE-LOG: <path>`. The log is written whether or not `--quiet` suppressed the console, because
   the file is what a reader of a red gate uses; `tools/gate-attribute.sh` keeps its one derivation of the failing
   case and the rerun command, and no second derivation is written.

2. **`tools/loop/run.sh` attributes a red tally once.** `attribute_red_gate <tally> <tally dir> <change root> <base>`
   returns `delta | base | unattributable`, and empty for a tally that is not red:
   - no readable `gate.log`, a case line the block does not carry, or a rerun command it cannot derive →
     `unattributable`;
   - the case the log names is one the record's own `metrics.redTest` text names (`<group>#<name>` token) → `delta`,
     and **no base tree is paid** for it: the owner's recorded decision on #853 (`2026-11-12T16:00:04Z`, option 1) is
     that a red case the change itself named is not a pre-existing red;
   - otherwise one detached tree at the run's base (`git -C <change root> worktree add --detach <tmp> <base>`), the
     rerun command `gate-attribute.sh` derived, one case's bound (`LOOP_CASE_MAX_SECONDS`) over it, and the tree
     removed again: exit `0` → `delta`, exit `2` (the base cannot select that case), `124`/`137` (the bound) or a tree
     that would not be made → `unattributable`, any other status → `base`.

   The answer is computed where the waived fields are settled (`tools/loop/run.sh:3077`), when the tally is red, and
   travels to the record as `--gate-attribution` on the arms that carry the gate — `closed`, `blocked` and `cut`. The
   prose tally `metrics.gate` is not touched: the owner's decision puts the answer in a field of its own, beside
   `gateWaived`/`gateWaivedSuite`.

3. **`tools/loop/state.sh`** takes `--gate-attribution` in the closed vocabulary `delta|base|unattributable`, refuses
   it beside a tally that passed (there is nothing to attribute), and writes `metrics.gateAttribution` after
   `gateWaivedSuite` — `null` when the arm passed none, so an item whose gate was green or that ran no gate tells
   itself apart from one this state was written before.

4. **`tools/loop/report.sh`** prints the count beside `waived:` — `<n> of <m> items recorded which tree fails the case
   their gate named (<word> ×<k>)` — with the same absence-versus-null shape every other line there has, so the field
   is not write-only.

5. **`guidelines/BuildEvidence.md` §4d** gains the rule the engine now implements: the loop records the attribution of
   a red gate in `metrics.gateAttribution`, and a `base` answer is a pre-existing red and not a finding about the
   delta.

## What a `base` answer does *not* change

The item's "a direction, and not a decision" bullet says a `base` answer "stops the block". The recorded decision is
narrower than that bullet, in its own closing sentence: *"a single base run would have written `delta` and blocked
exactly as it did, so this item's gain is readability, not fewer blocks"*. So this change records the answer and leaves
the blocked path exactly as it stands — a pre-existing red is now readable as one, and no verdict is invented for a
gate that did not pass. Changing what a red gate *does* to an item (closing it, waiving it automatically) is a second
change with its own trade-off and is left to the item that owns it, as the decision's own follow-up clause does for the
flake probe (`delta` → one warm rerun → `base|delta|flaked`).

## The repair after the first gate (2026-11-13)

The gate of the first attempt ran red on `tools/loop/tests/loop-mutations-selftest.sh` — `FAIL … a file set runs the
records its file owns: expected [0] got [1]`, the same for `--from-git`, and `RESTORE-FAILED 853.2
run#aRedGateIsAttributedToTheTreeThatFailsIt stayed red after the restore` — and on `./gradlew test`
(`MapManagerSourceSwitchPlanTest > aSwitchThatKeepsTheTargetsBasemapKeepsItRegistered FAILED`), which is a red this
tree already carries (`.pi/issue-loop/items/853/pre-existing-red-mapmanager-2026-11-06.xml`: the same class, `NoClassDefFoundError:
Could not initialize class sun.security.ssl.NamedGroup$SupportedGroups`, 2026-11-06) and which nothing in this delta
compiles.

The self-test's cause is the copy it mutates, not the engine. `attribute_red_gate` reads the gate-log reader as a
sibling of the engine (`$LOOP_DIR/../gate-attribute.sh`) — the same reach `tools/loop/lib.sh:45` already makes for
`$LOOP_DIR/../issues` — and the copy shipped `tools/loop` with `tools/issues` beside it and nothing else. The tool was
missing, the block came back empty, and the pristine copy could only answer `unattributable`, so a record's restore
could never go green. The fix is the harness's own contract: the copy carries the second sibling
(`ln -s tools/gate-attribute.sh` beside `tools/issues`), `--engine` refuses a copy without it rather than running cases
that can only answer `unattributable`, and the self-test asserts that refusal — its 36th guard. No production file is
touched by the repair, and the gate's own step now reads `all 36 cases passed` where it read `tests="34" failures="2"`.

## Alternatives the evidence eliminated

- **Unconditional base run on every red tally** (the decision's option 2, rejected there): it pays a cold base tree on
  every red, and for a case the branch added it can only answer `unattributable` — an answer the red test already
  gives.
- **Re-derive the failing case and the rerun command in `run.sh`** (`tools/gate-attribute.sh`'s parse repeated): two
  derivations of one fact drift, and the run has no console to read — the gate's own log is the one source, and the
  gate now writes it.
- **Parse the tally's `— failing:` list for the case name**: the gate's verdict names the failing *steps*, not the case
  (`tools/loop/gate.sh:605`), so a suite with three reds would attribute the wrong one.
- **Attribute inside the child's report** (a new `LOOP-…` line the child writes): the child has no base commit and no
  detached tree, and a judgement the run can take is not one a child should be asked for.

## Blast radius

- `tools/loop/gate.sh`: one log file beside the tallies, and the console's own lines routed through one function. No
  step, no order, no bound and no tally changes; a gate that owes nothing, prints a plan or is refused leaves no log.
- `tools/loop/run.sh`: one function, one call site, three record calls gaining `--gate-attribution` when it is set.
- `tools/loop/state.sh`, `tools/loop/report.sh`: the field, its refusal and its one line.
- `guidelines/BuildEvidence.md` §4d: one rule bullet.
- `tools/loop/tests/loop-cases.sh`: two cases and one rewritten count — `run#aRedGateIsAttributedToTheTreeThatFailsIt`
  (the three answers), `gate#aRedGateWritesTheLogItIsAttributedFrom` (the gate writes the log), and
  `gate#aCompiledStepsTallyIsNotHidden`, whose visible-file count now counts the tallies a `*.xml` glob finds because
  the tally directory carries the gate's log too.
- `tools/loop/tests/loop-mutations.sh`: three records, `853.1`–`853.3`, one per invariant — the run's attribution, the
  state's field, and the gate's log — so a half removed on its own turns its case red; and the `--engine` prerequisite
  now names both siblings the engine reads, not `issues` alone.
- `tools/loop/tests/loop-mutations-selftest.sh`: the copy it mutates carries `tools/gate-attribute.sh` beside
  `tools/issues`, and one guard asserts that a copy without it is refused.

## Rollback

`git revert` of the change's commit: the field is absent, `state.sh` refuses no new flag, the report prints no line,
and `gate.sh` writes one file into a gitignored build directory the next gate already clears. Nothing reads
`metrics.gateAttribution` as an input, so a state written with the field reads on a tree that does not know it.

## The measurement, and its limits

The base tree is `git worktree add --detach` (measured 2026-11-12 on the item's own decision record: 266 ms) plus one
shell case (7.49 s, 7.53 s) ≈ 7.8 s; a Gradle case's cold base cost is unmeasured, and a base tree that cannot build
records `unattributable` rather than failing the item twice.
