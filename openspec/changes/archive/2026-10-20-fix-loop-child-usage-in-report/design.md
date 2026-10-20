# Design — fix-loop-child-usage-in-report

## D1 — Where the event stream is read, and why it is not read anywhere the evidence eliminated

The root cause is one redirect: `run.sh` `run_child` writes the child's stdout to the item's report, and the
configuration started the child in a mode whose stdout is the final text only. Three ways to get the stream
back were available; the evidence eliminates all three.

**Alternative A — read the child's session file afterwards.** The agent persists a session per invocation, so a
run could read the usage out of it after the fact, without a stream at all. **Impossible:** the loop cannot
attribute a session to a child. The run starts children through one indirection (`LOOP_AGENT_CMD`) and holds no
handle on the session the agent chooses — the agent's session id and path are its own, are not passed in, and
two children of one item can be running against the same project. A run that guessed "the newest session file
after this child exited" would attribute a reviewer's tokens to a worker whenever two runs or two sessions
overlapped, and would report a number as authoritative that the run cannot tie to the item it prints. The
report may not rest on an attribution the run cannot make (design D11: every number is one the wrapper
observed).

**Alternative B — parse print mode.** Keep `pi --print` and read the spend out of what it writes. **Impossible:**
there are no events in it. Print mode writes the final assistant text and nothing else (`docs/json.md`,
"JSON Event Stream", against the print-mode invocation in `loop.conf`); measured on this host, a print-mode
child on a pipe emits nothing at all until it exits. There is no field to parse, which is the defect itself.

**Alternative C — always run in JSON mode and parse the text out of the stream in `run.sh`.** **Refused:**
it puts an agent-command fact inside the engine. `tools/loop/**` may name no agent command and no mode flag —
the case `run#engineNamesNoProjectFact` greps the engine for `pi --print`, and `tools/check-portability.sh`
scans `tools/loop/*.sh` for every value the project's profile declares — because the engine is meant to be
copied into a project with another agent (design D16). A `--mode json` in `run.sh` is exactly the leak the two
checks exist to refuse, and it would also break the seam the self-test uses to substitute an agent command
(`state#agentCommandIsTheOnlyIndirection`).

**Chosen: an adapter outside the engine, named by the configuration.** `tools/pi-child-events.sh` is this
project's tooling, not the engine's. `loop.conf` sets `LOOP_AGENT_CMD="bash tools/pi-child-events.sh pi"` — the
adapter with the agent as its argument, a path relative to the project root exactly as `LOOP_GATE_CMD` is
(`./gradlew test …`). The engine keeps starting one configured command, the mode flag lives where the agent is
a project fact, and a second project points the key at its own adapter or at its own plain-text command.

The adapter's interface, chosen so nothing in the engine or the prompts changes:

```
pi-child-events.sh <command> [command args…] <prompt-file>
```

The prompt is the **last** argument because that is where `run.sh` puts it (`$LOOP_AGENT_CMD "$prompt"`), so a
wrapper still composes (`bash tools/pi-child-events.sh sbx exec pi`); the mode flag is appended to the command
immediately before the prompt. The adapter appends every JSON event line to `$LOOP_CHILD_EVENTS`, prints a
non-JSON line through (a command that ignores the mode flag answers in plain text, so a plain-text project
needs no second code path), prints the last assistant message's text from `message_end` once the stream ends,
and exits with the child's own status so `run_child`'s "the worker process exited N" path keeps its meaning.

Risk: the adapter is a second process between the run and the child, so the run's wall-clock cap and its
progress lines act on the adapter's process group. It keeps the child in its own group (`run.sh` starts the
adapter with `set -m`), so `kill -TERM -$child` still reaches the agent and everything it started; measured by
`run#childOutlastingTheWallClockIsBlocked`, which stays green.

## D2 — What the sums mean, and where they are computed

**Alternative — the adapter computes the summary.** It would keep the event dialect out of the engine
entirely. Rejected: the run needs the *item's* figures across several children (worker, reviewer rounds, a
correction worker), and only the run knows which children belong to the item. An adapter-level summary would
have to be a second file in a shape the engine still reads, so the dialect would not leave the engine — it would
merely be renamed. The engine also already reads a child's output *format* wherever it parses `LOOP-VERDICT`,
so the format-boundary is a boundary the engine already has; the *command* boundary is the one the two checks
enforce, and it stays intact.

`state.sh done --events FILE` therefore reads the stream `run.sh` exports, and records four fields beside
`wallSeconds`:

| field | meaning | rule |
|---|---|---|
| `metrics.tools` | `{name: count}` over `tool_execution_end` | counts, not durations: a count is what a reader compares between items |
| `metrics.tokens` | `{input, output, cacheRead, cacheWrite}` | summed over responses, each response counted at its **last** `message_update.usage` — `usage` is cumulative within a response, so summing updates would count one response once per streamed chunk |
| `metrics.cost` | `usage.cost.total` summed over the same responses | `null` unless positive; a free local model reports zero, which is not a measurement of spend |
| `metrics.context` | the largest context any child reached | the maximum of `input + cacheRead` over responses and of `compaction_end.result.tokensBefore`, because a compaction's `tokensBefore` is a window size the responses' own figures no longer show |

Three states are distinguished rather than flattened: a value the stream reported, `null` for nothing
reported, and — at the report — `-` for the null. A stream holding none of these records yields four nulls, so
the engine reads a dialect without depending on it, and `-` is never `0`.

**One file per item, not per child.** `run.sh` sets `LOOP_CHILD_EVENTS="$item_dir/events.jsonl"`, removes it
before the item's first child (the same discipline as the per-child `.progress-stop` marker, and for the same
reason: an item that is started again must not inherit a previous attempt's spend) and exports it to every
child of that item. One file means the sum over an item's children is one `jq -s` pass, and the item's
directory holds the raw stream beside the reports it describes — the evidence a later reader needs to
re-derive any figure without a second run.

## D3 — Threading, lifecycle, state (no new Android component)

No Kotlin, Java, C++, manifest or resource file is touched: everything here is offline harness tooling, so the
threading model of `guidelines/Design.md` §4 does not arise. What takes its place is the process model — one
bounded child per wait, started by the run with job control on so the cap can end its group, and a reader in
the adapter that consumes the stream line by line so a full pipe buffer never stalls the child.

The state change is additive and one-way: `done` writes four more fields under `.metrics`, and every reader of
the state (the report, `show`, `report.sh --json`, `--state`) keeps working, printing `-` for a state recorded
before this change.

## D4 — Verification

- **New cases** (red first, then green) — `run#childUsageIsRecordedPerItem`, `report#zeroUsagePrintsDashNotZero`,
  `run#plainTextChildStillReports`, all offline, driven by a hand-written event stream rather than a model:
  `bash tools/loop-selftest.sh --case '<each>'`.
- **Cases this surface touches, re-run** — `run#metricsRecordedPerItem`, `report#headerAndTableShape`,
  `report#wallTimeAndReasonInTheTable` (its row now has fourteen columns and fifteen separators),
  `run#childReportsWhileItWaits`, `run#engineNamesNoProjectFact` (the engine still names no agent command) and
  `state#agentCommandIsTheOnlyIndirection` (the seam still substitutes a command).
- **Structural checks, no Gradle, no device** — `bash tools/check-portability.sh --quiet`,
  `openspec validate fix-loop-child-usage-in-report --strict`, `openspec doctor`. `guidelines/Build.md` §2's
  scope block applies: the delta touches no compiled file, so no build and no suite is run by this change; the
  orchestrator runs the whole offline suite once on the merged tree.
- **No device measurement**: nothing here renders, so no pixel, logcat or GPX replay applies.
- **Revert-check**: the invariant this change introduces — "nothing reported is printed as zero" — is
  falsifiable in one mutation: replace the `if $t == null then "-"` branch of `cmd_report`'s `tokens` cell with
  `"in=\($t.input)"`, and `report#zeroUsagePrintsDashNotZero` must fail (it then prints `in=0`). Restore and the
  case is green again. The second invariant — "an item's spend is the sum over its children" — is falsified by
  making `run.sh` remove `$LOOP_CHILD_EVENTS` per child instead of per item; `run#childUsageIsRecordedPerItem`'s
  second run then reports `bash=2`, not `4`.
