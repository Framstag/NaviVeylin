# Proposal — a number a change quotes is a measurement, and the block carries the ones a worker used to guess

## Why

The loop's review round refuses most items, and most of what it refuses is a number nobody measured. Measured over
`.pi/issue-loop/items/*/reviewer-report-1.md` (2026-10-21, issue #244): **11 `VERDICT: PASS` against 29 `FAIL`**,
with **147 `UNPROVEN` claims** in the round-1 reports. The recorded examples are counts and timings the change
asserts and no command in its record produced: "the existing 148 shell cases" where the tree held 130 and the base
128, "80 files, 261 mentions" where the tree holds 569 lines and 577 occurrences, "499 characters" where the
measured string is 454, "82 cases in five XMLs" where the run executed 107 cases in ten. Two whole scenarios fail
the same way. A second round is another whole agent pass — 90 to 99 % of an item's wall time is model latency, not
tool execution (item #69: 9 s of tools in 671 s; #133: 74 s in 772 s) — and it is paid by 61 to 73 % of items.

Two mechanisms, and this change is both halves of issue #244's fix shape:

- **The package hands the worker no facts about the two numbers it invents most.** `tools/loop/measure.sh`
  (issue #246) carries the paths, the owed checks, the tallies and the delta's own case counts, but not the
  suite's declared case count and not the gate's own timing. A worker that needs "how many cases does the suite
  have" or "what did the gate cost" counts or estimates by hand, and the count it wrote was the base's, the
  tree's, or neither.
- **Neither task states the rule a quoted number follows.** `worker.md` says a *file* read once is cited, not
  re-read; `reviewer.md`'s item 6 says "every quoted number carried by the artifact it names". Neither says that
  a number is a command's output, nor what a number with no command behind it is. #205 split wording from
  blocking; this is the same split applied to numbers, and the reviewer's round-1 refusal of a wrong count is then
  a correction rather than a block.

- **Root cause, one mechanism**: the measuring block is missing the two facts a worker used to take by hand, and
  the rule that makes a number a claim — "a number is the output of a command in the record" — is stated in
  neither task, so a guess and a measurement are indistinguishable to the reviewer.
- **Owning requirement**: `agent-issue-loop` — "The worker is handed one command that answers what the change
  owes" (`openspec/specs/agent-issue-loop/spec.md:1435`), whose block contents this change extends, and "A review's
  task states what the round may run, and quotes the gate instead of running it"
  (`openspec/specs/agent-issue-loop/spec.md:1380`), whose task text gains the number rule.
- **Owning guideline section**: `guidelines/Backlog.md`, section "7. The loop" — where the measuring block and the
  review round are described.

## What Changes

- `tools/loop/loop.conf` gains one project key: `LOOP_SUITE_CASES_CMD`, the command that prints the project's
  declared cases, one per line, **without running one** — this project `tools/loop-selftest.sh --list`. Empty for a
  project whose suite cannot list its cases, and then the block says so instead of a count of zero.
- `tools/loop/measure.sh` runs that reading command and prints `LOOP-MEASURE-SUITE cases=<n> cmd=<the command>`,
  so the count is reproducible; it sums the `time` attribute of the tallies the gate wrote into
  `LOOP-MEASURE-GATE seconds=<n> suites=<n>`, and each `LOOP-MEASURE-TALLY` line now carries its own
  `time=<n>s`. A gate that has not run yet is named rather than read as zero.
- `tools/loop/prompts/worker.md` states the rule the worker applies: a number the change quotes is the output of a
  command in its record, the block's numbers are cited from it, a number taken outside the block is recorded with
  the command that took it, and a number with neither is not a claim and does not belong in the change.
- `tools/loop/prompts/reviewer.md` states the rule the reviewer applies: a quoted number with no command in the
  record is graded `NOTE:` (a correction — deleting or fixing it needs no new claim, case or assertion), and is
  `BLOCKING:` only when the change's own contract, case or assertion depends on the number.
- One case added (`measure#printsTheSuiteCountAndTheGateTiming`) and two extended
  (`prompt#workerIsHandedTheMeasureBlock`, `prompt#reviewerIsBoundedToReading`); four mutation records (`61.1`–`61.4`)
  so the falsification is checked by every `--check` rather than remembered.

Additive in shape: no requirement is removed, two are modified to carry one sentence each, and the rollback is the
previous `measure.sh`, `worker.md` and `reviewer.md` — no state, no on-disk format and no caller's contract
changes. `LOOP_SUITE_CASES_CMD` is a new optional key; a configuration that does not record it gets the "names no
case-listing command" line, which is what the foreign-project fixture exercises.

Surface, counted before the plan was fixed:
`grep -rn 'LOOP_SUITE_CASES_CMD\|LOOP-MEASURE-SUITE\|LOOP-MEASURE-GATE\|A number the change quotes\|no command in
the record produced\|printsTheSuiteCountAndTheGateTiming\|is not a claim' tools/loop-cases.sh
tools/loop-mutations.sh tools/loop/measure.sh tools/loop/loop.conf tools/loop/prompts/ guidelines/Backlog.md
PORTING.md openspec/specs/agent-issue-loop/spec.md` → 28 hits in 6 files on the pre-change tree (`loop-cases.sh` 16, `loop-mutations.sh`
4, `worker.md` 4, `measure.sh` 2, `loop.conf` 1, `reviewer.md` 1). The two prompt cases are re-run and extended;
no other case, fixture or profile asserts the block's contents or the number rule.

surface: 1 case added, 2 rewritten, 0 fixtures realigned, 4 mutation records added — the command above.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `agent-issue-loop`: "The worker is handed one command that answers what the change owes" — the block gains the
  declared case count and the gate's timing, and the task gains the number rule (one scenario added, one added for
  the task text); "A review's task states what the round may run, and quotes the gate instead of running it" — the
  task text gains the number rule and its grading (one scenario added).

## Impact

- `tools/loop/loop.conf` — the new `LOOP_SUITE_CASES_CMD` key, with the measurement behind it.
- `tools/loop/measure.sh` — the case-count line, the `time=` attribute read from each tally, the summed
  `LOOP-MEASURE-GATE` line.
- `tools/loop/prompts/worker.md`, `tools/loop/prompts/reviewer.md` — the number rule each task states.
- `tools/loop-cases.sh` — one case added and registered, two extended.
- `tools/loop-mutations.sh` — records `61.1`–`61.4`.
- `PORTING.md` and `guidelines/Backlog.md`, section "7. The loop" — the new key and the number rule named.
- `openspec/specs/agent-issue-loop/spec.md` — the two modified requirements, merged at archive.
- No Android module, no Gradle build, no native code and no on-disk format is touched: the delta is shell, prompts
  and their own cases, so the gate owes no compiled suite for it (the gate still runs the loop's own suites for a
  `tools/loop/**` path).
