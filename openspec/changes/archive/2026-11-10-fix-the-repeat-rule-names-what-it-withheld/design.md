# Design — record the replacement, read the record, print the pair

## Root cause

One mechanism: the repeat rule replaces the result and records nothing. `repeatResult`
(`tools/pi-trim-result.mjs:124`) computes the one line and returns it; the record the handler was given is only used
by `trimResult` (`:79`). The event stream records what the tool returned, not what the child was handed, so no reader
can price the rule: `tools/loop/state.sh`'s dup fold reads the stream and sums raw result sizes into `dupChars`, and
`tools/loop/report.sh` prints that sum as `chars re-injected`.

## The single fix

Three readers of one fact, in the order the fact is produced:

1. **Record.** `repeatResult` appends `LOOP-TOOL-RESULT-REPEAT: <tool> path=<path> chars=<withheld> kept=<entered>`
   through the new `repeatRecordLine` (`tools/pi-trim-result.mjs:70`) into the same record `LOOP_TOOL_RESULT_TRIM_RECORD`
   names, in the same try/catch discipline the bound already keeps: a record that cannot be written costs a figure and
   never the result. `read.record` and `read.tool` are carried on the ledger invocation the two entry points already
   build (`:215`, `:268`).
2. **Read.** `child_usage_json` (`tools/loop/state.sh:914`) reads the record beside the stream —
   `$(dirname "$events")/tool-result-trims.log`, the path `tools/pi-child-events.sh` names to the adapter — and sums
   the two fields (`:931`–`:940`), recording them as `metrics.repWithheld` and `metrics.repKept` (`:1087`, written by
   `cmd_done` at `:774`). No record is a `null`; a record with no repeat line is a measured `0`.
3. **Print.** The trend's `reads:` line prints the withheld volume and what entered as a one-line pointer, and the raw
   volume as the denominator it is (`tools/loop/report.sh:417`–`:448`). A run whose items kept no record prints the raw
   volume with the dash that says what the rule withheld is unrecorded — the dash-versus-zero rule every spend figure
   of this report keeps. The report opens no trim record: it reads the state it already reads, which is why the figure
   is recorded once.

## Why the raw figure stays

`metrics.dupChars` is not replaced. A run that spans this change has items recorded before it, and the raw volume is
the only denominator those items have; deleting it would make the per-item column and the trend incomparable across
the change. What changes is the label and the two figures beside it.

## Blast radius

- `tools/pi-trim-result.mjs` — one new exported line builder, one append beside the notice, two invocation objects.
- `tools/loop/state.sh` — the trim-record sum, the two fields, the fallback JSON, the `metrics` object.
- `tools/loop/report.sh` — the `reads:` line's arms and its comment.
- `tools/loop/tests/loop-cases.sh` — one case (`report#theRepeatRuleNamesWhatItWithheld`), its registry line and
  dispatcher arm, and the trend assertion of `report#anItemRecordsItsRepeatReads` which now spells the unrecorded arm.
- `tools/loop/tests/loop-mutations.sh` — record `96.2` re-anchored on the wording it now mutates, record `96.3` added
  for the record the state reads.
- `guidelines/Backlog.recipes.md` sections 7a and 7e, `openspec/specs/agent-issue-loop/spec.md` — the rule as
  stated.
- No fixture, no compiled file, no profile key.

## What the evidence eliminated

- **Reading the trim record from `report.sh`** — rejected: the report reads the state and nothing else, and a second
  reader of the record would be a second source for one number. `report#anItemRecordsItsRepeatReads` states the same
  rule for the figures it already carries.
- **Storing the withheld volume in the event stream** — impossible: the stream is the harness's record of the tool's
  result, not of the extension's replacement, and the handler may not write to it.
- **Renaming `dupChars` to mean the withheld volume** — rejected: it silently changes the meaning of a figure in every
  run record written before this change, which is the one thing a per-item comparison across a change cannot survive.
- **A new profile key to switch the record on** — rejected: `LOOP_TOOL_RESULT_MAX_CHARS = 0` already keeps both rules
  off, and a second switch would leave the record and the rule able to disagree.

## Rollback

Revert the five files: no state schema is migrated, and a state written with `repWithheld` / `repKept` reads in an
older report as two unused keys (the report selects the keys it knows and ignores the rest), while a state written
before this change prints the unrecorded arm the case asserts.
