# Design — where the split is taken, and what the numbers beside it mean

## The defect, exactly

`GATE_RE = re.compile(r'gate\.sh|gradlew ')` (`tools/loop-spend.py:55`, at `1a5dede2`) searched each call's `command`
string. Three consequences, all of them silent:

1. `./gradlew :app:testDebugUnitTest --tests X` matched `gradlew ` — a **focused suite run**, the iteration
   `guidelines/BuildGate.md` §2a hands a child, and never the item's gate.
2. `sed -n 1,60p tools/loop/gate.sh`, `git show HEAD:tools/loop/gate.sh` and `rg gate.sh` matched `gate\.sh` — calls
   that **read** the file.
3. `bash tools/loop/gate.sh --plan …` matched too — a call that runs **no step**.

So `after=` and `afterFirstGate` were the tail after the earliest of those, in whichever order the session made them.
The fixture in the selftest proves the size of the error on a three-turn session: the old pattern splits at the `sed`
(turn 0, 95 000 of 95 000 tokens), the new reader at the gate (turn 3, 47 000).

## The rule, and where it already lives

`tools/loop/lib.sh:459` holds it, as jq definitions (`gate_command_jq`, issues #830, #857, #882): `words`,
`assignment`, `wrapper`, `run_word`, `whole`, `is_check`, `is_whole_check`, `is_gate`. Two readers use it — `state.sh`'s
`child_usage_json` and `run.sh`'s live count of by-hand checks — and its own comment says a second copy is a second
rule that would drift.

**A. Share the jq definitions with `loop-spend.py`.** The honest option, and eliminated on cost: the tool's inner loop
is per tool call, and the archive is 1.8 GB of event streams over thousands of calls. A `jq` subprocess per call turns a
3 s read into minutes for a figure that is printed once. Recorded rather than hidden, because the day the two readings
disagree, this is the paragraph that says why they could: the Python copy is `run_word`/`program`/`is_gate_run`/
`is_focused_suite`, it is line-for-line the jq's rule, and the wrapper list is the same list in the same order.

**B. Keep one position and call it the gate's.** Rejected: that is the defect — the two calls are five times apart on
this archive, and the loop's argument for reading a diff before the gate is *about the gate*.

**C. Keep `GATE_RE` and add the suite.** Rejected: the pattern's false positives (2) and (3) are not fixed by adding a
second pattern beside it.

Chosen: the rule, restated in Python, with both positions.

## The program, not the path

`is_gate_run` accepts a call when `program(cmd) == 'gate.sh'` **and** no word is `--plan`/`--plan=…`/`--list`/`--help`/
`-h`. `is_focused_suite` accepts when `program(cmd) == 'gradlew'`. `program` basenames what `run_word` returns, so
`bash tools/loop/gate.sh`, `/w/tools/loop/gate.sh` and `./gate.sh` are one program and
`sed … tools/loop/gate.sh` is `sed`. The suite's filter is not consulted: `--tests` narrows a suite run, it does not
change the class of the call (that distinction belongs to `is_check`/`is_whole_check`, which price *checks*, not the
item's split).

## Both halves are printed, and each names its own call

`report()` prints two sentences and `--json` two fields:

- `afterFirstGate` / `after=` — the tail after the first **gate run**, over the sessions that made one;
- `afterFirstSuite` / `suite=` — the tail after the first **focused suite run**, over the sessions that made one.

Each is its own denominator, because the two populations are different (37 sessions and 13 on this archive): folding
the union into one denominator would make the two shares add up to a number no session's stream supports. The old line
printed no denominator at all, which is how its figure could be read as a gate's.

## The numbers the sites quote

The six prose sites and the skill bullet quote figures taken over a **227-item, 2 591 Mtok** archive (2026-11-03). That
window is not in this tree: `.pi/issue-loop/items` holds 59 items and 1.1 B tokens, of which only 37 sessions ran a gate
and 13 a suite. Re-deriving the prose figures here would not correct them; it would replace them with a measurement
over a thinner window and lose what they recorded. So each site keeps its digits and gains the call it was taken at —
*the earlier of the item's first gate and its first focused suite run* — which is what the old pattern computed on a
session whose only gate-shaped call was one of those two; a session that read the gate's file, or asked for a plan,
before either had its split on that call instead, which is defects (2) and (3) above and the reason no figure of
those was the gate's. A site that needs the gate's own share now has a command that prints it; none of the six needs
one, because each argues about the tail after verification, and that tail is the union of the two.

The skill bullet (`.pi/skills/loop-spend/SKILL.md`) is the one site that *teaches* the reading, so it carries the
current pair with the window it was measured on and the command that printed it.

## What a reader can check

- `bash tools/loop/tests/loop-spend-selftest.sh --case spend#aFocusedSuiteRunIsNotTheItemsGate` — red on `1a5dede2`
  (95 000, split at the `sed`), green here (47 000, split at the gate).
- `python3 tools/loop-spend.py --quiet` on the loop's archive — `after=` and `suite=` on one line, `15 643 757` and
  `75 818 065` (2026-11-22).
- Two mutation probes (`evidence/spend-mutations.json`, run with `tools/mutate.sh`): the suite admitted to the gate
  predicate, and the gate's program dropped from it. Both red the case; both restore byte-identically
  (`evidence/mutation-probe.log`).

## Open decisions

None

The item's class and its own text settle the shape: the split is taken at the program a call starts, which is the rule
`tools/loop/lib.sh`'s `run_word` already holds, and the suite's position is printed beside the gate's rather than
replacing it. The two shapes not taken — sharing the jq reading, and keeping the union — are recorded above with what
rejects them.
