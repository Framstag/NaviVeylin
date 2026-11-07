# fix-a-close-over-a-red-gate-records-its-waiver

## Why

Every `file:line` in this section is the base `eb513ca5`'s. Two readers of an item's close read the gate's tally as
free text and neither can tell a waived red from a green run.

`tools/loop/state.sh:519` accepts one field for it — `--gate TALLY` — and `tools/loop/state.sh:616` writes it into
`metrics.gate`. There is no field for "the gate was not green and the item closed anyway", so a close over a red tally
is written exactly as a close over a green one: `metrics.gate` carries the tally of a gate that printed
`failures=2 errors=0 (mobile)` beside
`result: done`, and nothing in the record says the red was waived.

`tools/loop/report.sh:73` counts the closed items from `.result == "done"` and `:396` prints the trend's lines — the
review rate, the wall median, the calls, the reads, the phases, the roles, the share after the first gate — and none of
them is a count of waivers. The waiving judgement therefore exists only inside the closing child's transcript: measured
on the run of `2026-11-06T21:19:06Z` (the issue's own count over `.pi/issue-loop/runs/`), 3 of its 7 closed items carry
a red gate — #806 and #759 with `errors=1` on `MapManagerSourceSwitchPlanTest >
aSwitchThatKeepsTheTargetsBasemapKeepsItRegistered` (`NoClassDefFoundError: Could not initialize class
sun.security.ssl.NamedGroup$SupportedGroups`), #799 with `errors=1` on `:auto:testDebugUnitTest
SearchHistoryScreenTest#thePageRowOpensTheNextSliceOfTheHistory` — and each was waived in prose ("file untouched by this
diff", "green in the automotive run", "not repaired, not retried").

`tools/loop/run.sh:2358`–`:2364` computes the tally into `gate_field` and `run.sh:2782` passes it to the one arm that
closes. So the field exists on the way in and the only thing missing on the way out is the clause.

**Root cause, in one sentence:** the record has one field for the gate's tally and no field for the judgement a close
over a red tally makes, so a waiver lives in a child's prose where nothing counts it and the same failing suite is
re-diagnosed — and re-waived — by every item that meets it.

**The red case, on the base `eb513ca5`** — the three cases this change adds, run against the base's engine with
`tools/loop/state.sh`, `tools/loop/lib.sh`, `tools/loop/report.sh` and `tools/loop/run.sh` restored from `eb513ca5`
(`git show eb513ca5:tools/loop/state.sh > tools/loop/state.sh`, the four files, the working tree's other paths
untouched):

```
state#aCloseOverARedGateRecordsWhyItWasWaived| loop: done: unknown argument --gate-waived
state#aCloseOverARedGateRecordsWhyItWasWaived|   the waived close was refused
FAIL state#aCloseOverARedGateRecordsWhyItWasWaived
loop-selftest: 1 of 1 cases failed
```

```
state#aCloseOverARedGateWithoutAWaiverIsRefused|   a close over a red tally that records no waiver is a usage error: expected '2', got '0'
FAIL state#aCloseOverARedGateWithoutAWaiverIsRefused
loop-selftest: 1 of 1 cases failed
```

```
run#aCloseOverARedGateIsWaivedOrBlocked|   and the clause travels from the child's report into the record: expected 'the failing class never initialises its SSL context on the mobile flavour, and this diff touches no network path', got 'null'
FAIL run#aCloseOverARedGateIsWaivedOrBlocked
loop-selftest: 1 of 1 cases failed
```

The third is the defect in one line: on the base the item closes (`result: done`) over a red tally and the field that
would say why is `null`. `evidence/red-on-HEAD.log` holds the three runs; the loop's shell runner writes no JUnit XML,
so `evidence/red-on-HEAD.xml` transcribes them into a `testsuite` (`tests=3 failures=3 errors=0`), which is that
file's own count and not a run of the project's suite.

**Owning requirement:** `openspec/specs/agent-issue-loop/spec.md` — added, "A close over a gate that did not pass
records its waiver". It sits beside "An item's record keeps what the run measured to decide it" (`spec.md:3869`),
which owns the tally and the review verdict an item's record carries whichever arm it ended on.

**Owning guideline section:** `guidelines/Backlog.recipes.md`:710 "Refusals, records and the landing", which owns
what a run records of an item and what it does with a refusal. It names the reasons an item ends with and the two
refusals the landing makes, and never what a close over a red gate records — `./tools/look.sh 'waiv' --path
guidelines/` returned `0 matches in 0 files`. This change adds one paragraph to it, beside the commit identity.

surface: 1 rule file carries the tally's field (`./tools/look.sh 'metrics\.gate'` — 24 matches in 11 files,
`tools/loop/run.sh` and `tools/loop/state.sh` the two that write it), 5 files under `tools/loop/` carry `lib.sh`'s
predicate once it exists (`./tools/look.sh 'gateWaived|gate-waived|LOOP-GATE-WAIVED|gate_tally_state' --path
tools/loop/` — 30 matches in 5 of them), 3 cases added (`state#aCloseOverARedGateRecordsWhyItWasWaived`,
`state#aCloseOverARedGateWithoutAWaiverIsRefused`, `run#aCloseOverARedGateIsWaivedOrBlocked`; `tools/loop-selftest.sh
--list | wc -l` → 361, 358 before), 3 mutation records added (`bash tools/loop-mutations.sh --list | wc -l` → 280, 277
before), 0 cases rewritten, 0 fixtures realigned, 1 requirement added with 3 scenarios, 1 guideline paragraph added.

## What Changes

1. **`tools/loop/lib.sh` reads the tally's verdict once.** `gate_tally_state <tally>` prints `red` when the tally
   carries a nonzero `failures=` or `errors=`, `green` when it carries the counts and they are zero, and `unknown` when
   it carries neither — the three shapes `run.sh` writes (`gate_field`, `run.sh:2358`–`:2364`). One predicate, so the
   refusal in `state.sh` and the refusal in `run.sh` cannot disagree about what a red gate is.
2. **`tools/loop/state.sh done` records the waiver and refuses its silence.** `--gate-waived CLAUSE` and
   `--gate-waived-suite TEST` are written into `metrics.gateWaived` / `metrics.gateWaivedSuite` beside the tally; a
   `--result done` whose tally is `red` and which records neither is refused (exit 2, the item still queued), as is a
   waiver on a result that is not `done`, a waiver with no `--gate`, and a waiver beside a tally that passed.
3. **`tools/loop/run.sh` carries the clause, and refuses the close that recorded none.** It reads
   `LOOP-GATE-WAIVED` and `LOOP-GATE-WAIVED-SUITE` from the report the item is decided from and passes them to the
   `closed` arm; an outcome that would close over a `red` tally with no clause becomes `blocked` with its reason
   (`stop_reason: refused`) **before** the branch is merged and the item is closed on the tracker, because a close that
   fails the write that records it leaves the tracker saying `closed` about a record that does not exist.
4. **`tools/loop/report.sh` counts them.** The trend gains `waived: N of M closed items (<suite> ×<n>, …)`, grouped by
   the suite each waiver names, `-` for a waiver that named none, and the two dashes the report already distinguishes:
   no closed item this run, and a state written before the field existed.
5. **`tools/loop/prompts/worker-tail.md` states the line.** The tail is the process that writes `LOOP-VERDICT:
   closed`, so it is where a waived red is named: `LOOP-GATE-WAIVED` in its report block, `LOOP-GATE-WAIVED-SUITE`
   beside it when the clause names a test.

## What this does not do

- It does not judge the three waivers. Whether a red on an untouched network path, green in the sibling flavour, may be
  waived is a person's call and stays one: the change records the call and counts it.
- It does not print a per-suite total **across** the run archive. `report.sh` reads one state file and git, and nothing
  else (D11); a total over `.pi/issue-loop/runs/*.json` is a second reader of the archive, which is a delta of its own.
  The line this change prints is that figure for the run it reports, which is the one `report.sh` is allowed to make.
- It does not change what a waiver does. No arm's `result` reads `gateWaived` and no label, closure, merge or branch
  reads it: the field is written, printed and counted.
- It does not add the line to `tools/loop/prompts/worker.md`. That block carries the gate a worker ran itself and no
  `closed` verdict — the verdict is the tail's — so a waiver line there would be a claim no arm reads.
