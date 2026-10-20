# fix-loop-child-usage-in-report

## Why

A run's report names each item's verdict, change, root cause, red test, gate, review rounds and wall time, and
nothing about what the item **spent** — no tool call, no token, no context size — although the agent's own
event stream carries all three (issue #211).

- **Observed ✗.** `tools/loop/loop.conf` started the child as `LOOP_AGENT_CMD="pi --print"`, which writes the
  final assistant text and nothing else, and `tools/loop/run.sh` `run_child` redirects exactly that stream
  (`( cd "$LOOP_ROOT" && $LOOP_AGENT_CMD "$1" ) > "$2" 2>&1`). The two questions the run's own budget implies
  therefore had no answer: *what did this item spend*, and *did a phase come near the context limit*. The two
  long items measured on 2026-10-20 show the gap: #126's worker produced a 445 KB review diff and three
  13–16 KB reviewer reports, and no number in the report described either.
- The agent documents what was thrown away (`docs/json.md`, this install):
  `message_update` carries `usage {input, output, cacheRead, cacheWrite, totalTokens, cost{…}}`;
  `toolcall_start` / `toolcall_end` / `tool_execution_end` carry `toolName`, and `tool_execution_end` also
  `durationMs`; a successful compaction carries `result.tokensBefore`.
- **Expected ✓.** The report states, per item, the tools the child called with their count, the tokens it used
  and what they cost, and the largest context it reached — read from the child's own stream, and `-` where a
  provider reports nothing rather than a zero that reads as a measurement.

**Root cause and its evidence.** One redirect discards the only measurement of spend that exists, and nothing
replaced it:

- `tools/loop/run.sh` `run_child` (line 481 on HEAD `2424cf4`): `( cd "$LOOP_ROOT" && $LOOP_AGENT_CMD "$1" ) >
  "$2" 2>&1` — the child's whole stdout becomes the transcript; in print mode that stdout carries the final
  text and no event.
- `tools/loop/loop.conf` (line 62 on HEAD): `LOOP_AGENT_CMD="pi --print"` — the print mode is where the stream
  is lost.
- The red cases (below) against HEAD's engine, only the cases themselves being this change's:
  `run#childUsageIsRecordedPerItem` fails with `the tool counts are recorded by name: expected '2', got
  'null'`; `report#zeroUsagePrintsDashNotZero` fails with `no table row for #21` (`loop: done: unknown argument
  --events`); `run#plainTextChildStillReports` fails with `tools, tokens and context are -, not zero: '| - | -
  | - | done |' not found in: | #21 | … | 4s | done | - |`. Logs: `evidence/red/`.

**The single fix.** An adapter outside the loop engine — `tools/pi-child-events.sh`, named by
`LOOP_AGENT_CMD` — starts the agent in its JSON event mode, appends every event line to the path `run.sh`
exports per item (`LOOP_CHILD_EVENTS`), and prints the child's answer to stdout so `report_field` and every
`LOOP-VERDICT` parse keep working unchanged. `state.sh done --events <file>` sums the item's stream into
`metrics.tools` (per name), `metrics.tokens` (in/out/cache), `metrics.cost` and `metrics.context` (the largest
context), and `state.sh report` prints the three columns `tools`, `tokens`, `context` — `-` where nothing was
reported.

**Spec requirement the defect contradicts:** `agent-issue-loop`, "One command starts a budgeted run, with no
dialogue" — its scenario "A run is recorded" promises that the run "justifies itself with numbers" through
every item's row; spend was the one number it had and never printed, because the run discarded the stream
before reading it.

## Cases and evidence

Three cases in `tools/loop-cases.sh`, registered in **both** the `--list` array and the dispatch map, each
driven by a hand-written event stream instead of a model (the stub `pi` prints it on stdout, which is where a
real child's stream comes from):

- `run#childUsageIsRecordedPerItem` — a fixture with two `bash` calls, one `read` call, two `message_update`
  usages inside one turn and a compaction of 150 000 tokens: the item records `tools.bash=2`, `tools.read=1`,
  `tokens.input=2000` (the turn's **last** usage, not the sum of its two updates), `cost=0.025`,
  `context=150000`, and the row prints `bash=2 read=1`, `in=2000 out=200 cache=1000`, `cost=$0.025`, `150000`.
  The worker prints no plain text at all, so the item can only close if the adapter printed the answer out of
  the stream's last assistant message. A second run with the review on proves an item's figures are the sum
  over its children (`bash=4`).
- `report#zeroUsagePrintsDashNotZero` — a complete turn whose usage is all zeros, and an item recorded with no
  event file at all: both print `| - | - | - |` and neither prints `=0`.
- `run#plainTextChildStillReports` — a project whose `LOOP_AGENT_CMD` is a plain print-mode command: the item is
  worked, recorded and reported, no event file appears, and the three columns are `-`.

Red on HEAD `2424cf4`: `evidence/red/`. Green on the fix: `evidence/green/`, which also re-runs the cases this
surface touches (`run#metricsRecordedPerItem`, `report#headerAndTableShape`,
`report#wallTimeAndReasonInTheTable`, `run#childReportsWhileItWaits`, `run#engineNamesNoProjectFact`,
`state#agentCommandIsTheOnlyIndirection`) plus `tools/check-portability.sh`, `openspec validate --strict` and
`openspec doctor`. The whole offline suite is **not** run by this change: the orchestrator runs it once on the
merged tree.

## What Changes

- `tools/pi-child-events.sh` (new, project tooling, outside `tools/loop/`): starts the agent in JSON event
  mode, appends the stream to `$LOOP_CHILD_EVENTS`, passes a plain-text command's own lines through, prints the
  last assistant message's text, and exits with the child's status.
- `tools/loop/loop.conf`: `LOOP_AGENT_CMD="bash tools/pi-child-events.sh pi"` — the adapter with the agent as
  its argument, relative to the project root as `LOOP_GATE_CMD` is. The engine still names no agent command and
  no mode flag; a project with no adapter points the key at its own command and reports `-`.
- `tools/loop/run.sh`: exports `LOOP_CHILD_EVENTS` per item — `items/<n>/events.jsonl`, removed before the
  item's first child for the same reason the per-child progress marker is — and passes it to the three
  `state.sh done` call sites. Nothing else about the child, the cap or the progress lines changes.
- `tools/loop/state.sh`: `done` accepts `--events FILE` and records `metrics.tools`, `metrics.tokens`,
  `metrics.cost` and `metrics.context` beside `metrics.wallSeconds`; `report` prints the `tools`, `tokens` and
  `context` columns after `wall`, `-` for a value nothing reported.
- `tools/loop/report.sh`: the file's own account of what the report answers names the three spend columns
  (a comment; no behaviour changes there).
- `tools/loop-cases.sh`: the three cases, in both registries, and the two case helpers they need (the stub's
  event fixture, and the adapter the throwaway project copies).
- `tools/loop/fixtures/pi`: `PI_STUB_EVENTS`, the fixture the stub prints as the child's stream.
- `.pi/skills/fix-loop/SKILL.md`: the report template carries the three columns, the wrapper table names the
  adapter, and a new subsection states what a run measures and why `-` is not `0`.
- `guidelines/Backlog.md` §7 "The loop": "what each one cost" now names the spend read from the child's own
  event stream.

## Impact

Affected files, all of them the loop's own harness — no app, native or Gradle file is touched:

| file | module / area |
|---|---|
| `tools/pi-child-events.sh` | new: this project's child adapter, started through `LOOP_AGENT_CMD` |
| `tools/loop/loop.conf` | the loop's recorded configuration: the one indirection every child is started through |
| `tools/loop/run.sh` | the run's only writer: `run_child`, the item loop, the `done` call sites |
| `tools/loop/state.sh` | the run's state, budget and lock; `done` and `report` |
| `tools/loop/report.sh` | the report a run prints at its end — its own account of what it answers (comment only) |
| `tools/loop-cases.sh` | the loop's offline self-test cases (`tools/loop-selftest.sh`) |
| `tools/loop/fixtures/pi` | the stub child the self-test starts |
| `.pi/skills/fix-loop/SKILL.md` | the `fix-loop` skill's report template and its account of what a run measures |
| `guidelines/Backlog.md` | §7 "The loop", where the run's report is described |
| `openspec/specs/agent-issue-loop/spec.md` | the loop's contract: the report's content, and the reason the wait is reported |

No Android module, manifest, resource, native source or JNI bridge is touched, so the phone/car parity rules,
the native-build rules and the `submodule patch` / `local override` decision do not arise. The scope is the
offline harness only: nothing here runs on a device or under Gradle.

No new project value enters an engine: the adapter lives outside the engine set, `loop.conf` gains no value
that `tools/check-portability.sh` reads, and the check still reports 42 engines naming no project value. The
event dialect the engine now reads is not a project value but a *format*, and it is read tolerantly: a stream
holding none of its records yields four nulls, never a number.

## Previous specifications changed

- `openspec/specs/agent-issue-loop/spec.md` — **MODIFIED** "One command starts a budgeted run, with no
  dialogue": the scenario "A run is recorded" now names the spend each item's row carries, and two scenarios
  are added — "An item's spend is read from the child's own stream" and "Nothing reported is printed as zero".
  The requirement's other scenarios are restated verbatim; no unrelated text is rewritten.
- `openspec/specs/agent-issue-loop/spec.md` — **MODIFIED** "A run reports the wait it cannot get from the
  child": its reason clause said the run must report the wait *because the configured agent command is print
  mode and writes nothing before the child exits*. The configuration no longer starts a print-mode command, so
  the reason is restated as the fact that holds either way — a child's answer arrives when it exits — and every
  scenario is carried over verbatim, because none of them changes.

No other capability owns the loop's report, its child process or the wait it prints.

## Additive or breaking, and rollback

**Additive.** Three columns are appended to the table, three fields to an item's `metrics`, and one flag to
`state.sh done`; every earlier column, field, argument, exit code and recorded configuration value keeps its
name, its order and its meaning. A caller that parsed the old table still finds its columns. A project whose
`LOOP_AGENT_CMD` writes plain text keeps working unchanged and reports `-`, which is the property
`run#plainTextChildStillReports` asserts.

Rollback: revert this commit, or — without touching the code — set `LOOP_AGENT_CMD="pi --print"` in
`loop.conf`: the run then reports `-` in the three columns exactly as it did before the change. No state file,
branch, label or archived change is affected either way, because the change writes none of them.
