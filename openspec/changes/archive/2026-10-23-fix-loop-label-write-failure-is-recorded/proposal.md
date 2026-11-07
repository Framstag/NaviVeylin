# A label write the tracker refuses is recorded, not swallowed

## Why

**Observed ✗, verified in the tree today.** A block ends an item with two tracker writes, and both discarded
their exit status:

- `tools/loop/run.sh:1461` — `loop_labels unpick "$issue" >&2 || label_failures="unpick"` replaced
  `… || true`, so a refused removal of `agent/in-progress` left the mark on an item nothing was working,
  and the run cleared its own `in_progress` variable on the same path, so no later step retried it.
- `tools/loop/run.sh:1489`–`:1494` — the escalation's `if loop_labels escalate …; then` had no `else`, and
  `escalated_label` stayed `""`, which is the empty `--decision-label` (the `escalatedLabel: ""` the item's
  evidence quotes from `.pi/issue-loop/runs/2026-10-23T06:52:53Z.json`, item #260).

The consequence is what the report then cannot see. `tools/loop/report.sh:139` names the decisions the owner
owes by `(.escalatedLabel // "") != ""`, so an item whose escalation was never written is named nowhere — not
in the decision list, not in the cap that counts it, not in the next attempt's package — while the six other
items of the same run carry `agent/needs-decision`.

**Root cause, one sentence.** The two writes are the only thing that makes a blocked item found again, and the
run discarded their failure instead of recording it with the item, so nothing in the run distinguishes "the
tracker carries no decision" from "the run could not write it".

**The red test.** Two cases added by this change, on the base commit's engine, 2026-10-23T19:29Z:
`tools/loop-selftest.sh --case 'run#aFailed*'` → `2 of 2 cases failed`, each on the assertion that the
refused transition is recorded with the item (`expected 'escalate', got ''`, `expected 'unpick', got ''`).
Evidence: `evidence/red-HEAD.log` and `evidence/red-HEAD.xml` (`tests="2" failures="2"`).

**Owning capability and guideline.** The capability is `agent-issue-loop`: its requirement "The run survives
interruption" already owns "A block's reason is not left to the run's state", and its requirement "A pile of
open decisions stops the run" owns "The report names the decisions the run itself wrote". The guideline is
`guidelines/Backlog.md` §7 "The loop", the two paragraphs the report's action list and the mid-item escalation
are described in; `AGENTS.md` routes the loop there.

## What Changes

surface: 2 cases added, 0 rewritten, 0 fixtures realigned, 3 mutation records added, 2 guideline paragraphs rewritten — cases: `tools/loop-selftest.sh --list --files "tools/loop/run.sh tools/loop/state.sh tools/loop/report.sh tools/loop-cases.sh tools/loop-mutations.sh"` (204 case names owed; the case file is a core file, so the gate owes the set whole); records: `bash tools/loop-mutations.sh --list --files "tools/loop/run.sh tools/loop/state.sh tools/loop/report.sh"`.

- `tools/loop/run.sh`: the blocked path records each refused transition — `unpick`, `escalate` — in
  `label_failures`, carries the same text into the item's own `--note`, and passes the list to
  `state.sh done --label-failures`.
- `tools/loop/state.sh`: `done` accepts `--label-failures <transition>…` and records it as `.labelFailures`
  beside `.escalatedLabel`, merging (never replacing) the field when an item is recorded a second time.
- `tools/loop/report.sh`: the action list gains a sixth source — an item whose label write the tracker refused,
  named with the transitions and the command that shows it.
- Two cases (`run#aFailedEscalationWriteIsNamed`, `run#aFailedUnpickIsNamed`), three mutation records
  (`14.6`, `14.7`, `14.8`), the delta in `openspec/specs/agent-issue-loop/spec.md`, and the two paragraphs of
  `guidelines/Backlog.md` §7 that count the action list's sources.

## Impact

`tools/loop/run.sh`, `tools/loop/state.sh`, `tools/loop/report.sh`, `tools/loop-cases.sh`,
`tools/loop-mutations.sh`, `guidelines/Backlog.md`, and the capability delta. No compiled file: the gate owes
the two shell suites, the mutation record check and the doc-route/classification checks, not the build.
