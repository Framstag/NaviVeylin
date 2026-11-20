# Tasks — the run's typed record

Spec: `agent-issue-loop` (delta `specs/agent-issue-loop/spec.md`). The iteration check is
`bash tools/loop/tests/loop-selftest.sh --files tools/loop/…`; the full suite is this change's gate and runs once,
from `./tools/loop/gate.sh`, over the completed tree — the applying child does not run it (offline: no token, no
network, no device). No Gradle build is owed — no compiled file changes (`guidelines/Build.md` §2, the scope block).

The conversion of the print-only sites is `the-print-sites-are-converted`'s, the report's cross-check is its
requirement `The record and the report's figures agree, or the difference is named`, and the agreement between the
vocabulary and every call site is its case `driver#everyEmittedKindHasARow`: this change lands the writer those three
stand on and converts nothing further.

## 1. The vocabulary and the writer

- [x] 1.1 Write `tools/loop/events.tsv`: one row per kind (`kind<TAB>fields<TAB>format`) for `run-start`,
  `run-end`, `heartbeat`, `item-result` — the four kinds the path is proven with. Fields are `name:type`
  (`text`, `num`, `ident`, `path`) joined by `+`; the format column carries `%s`-style placeholders in field
  order. Verify: `tools/loop/events.tsv` has no row whose field count differs from its format's placeholder count
  (the loader's own refusal, exercised by case 1.4). Satisfies `The event vocabulary is declared`.
- [x] 1.2 Add `loop_emit` to `tools/loop/lib.sh`: resolve `$LOOP_RUN_EVENTS` beside the state file, load the
  vocabulary once into three associative arrays, escape a `text` field (`"`, `\`, newline, tab), refuse an
  undeclared kind and a missing required field with exit 3 naming kind and field, append one JSON object, then
  render the format to the stream the caller names. Refusal text on stderr, one line. Verify: cases 1.4 and 1.5.
  Satisfies `A fact the run records is printed from its record, once` and `...an undeclared kind is refused`.
- [x] 1.3 Add the bound: `LOOP_RUN_EVENTS_MAX_BYTES` read through `loop_conf_value` with a default, rotation to
  `<file>.1` with a `record-rotated` row added to the vocabulary, and the rotated name in the event. Verify:
  `run.sh`'s header names the bound it used. Satisfies `The run's record is bounded`.
- [x] 1.4 Cases `run#anUndeclaredKindStopsTheRun` and `run#aDeclaredKindMissingAFieldStopsTheRun`: drive
  `loop_emit` through a driver stub, assert exit 3, the kind and field on stderr, and no line in the record.
  Each case declares `# files: tools/loop/lib.sh tools/loop/events.tsv`.
- [x] 1.5 Cases `run#aFullRecordRotatesAndSaysSo`, `run#theRotationKeepsTheOlderFile`,
  `run#theBoundIsARecordedValue`: emit past a small bound, assert `.1` exists, the first event of the new file
  is the rotation, and an unset key falls back to the default named in the header.
- [x] 1.7 Cases `driver#noEventCarriesACoordinate` and `run#anEventNamesAnIssueAndAPhaseNeverAPosition`: the
  first refuses a vocabulary row declaring a latitude/longitude-shaped field; the second asserts an item and a
  phase event carry the issue number and the phase name and that an emit handed a coordinate-shaped value is
  refused. Satisfies `No event carries a coordinate` (both surfaces: the vocabulary and the call site).

## 2. The one call site the writer carries

- [x] 2.1 Header and budget: the run's start, the resolved flags, the armed quota reader — the header line is
  rendered from the `run-start` event, and the bound the record keeps is named beside its path. Verify
  `run#aFactPrintedOnceIsRecordedOnce`. *(Kept although the rest of this section is
  `the-print-sites-are-converted`'s: this site is the writer's own proof that a printed line is rendered from the
  record, and this delta's `A header fact is one event and the line shows it` names its case. Declares
  `# files: tools/loop/run.sh tools/loop/events.tsv`.)*

## 4. Documents the code now owns

- [x] 4.1 `guidelines/Logging.md` §2 gains the rule that an engine's own record is JSON-lines, carries no
  coordinate and is read back rather than grepped. Verify: `tools/check-doc-routes.sh` and the section's own
  route row still resolve (`bash tools/check-doc-routes.sh`).
- [x] 4.2 `guidelines/Backlog.md` section 7 (its section list, via `tools/look.sh '^## ' --path guidelines/Backlog.md`)
  names the run's record beside the state file; `guidelines/BuildEvidence.md` §4f's artifact list gains it.
- [x] 4.3 `tools/loop/loop.conf` records `LOOP_RUN_EVENTS_MAX_BYTES` with its default and the reason the bound
  exists, in the profile's own prose style.

## 5. Verification

- [x] 5.1 Full suite green: `bash tools/loop/tests/loop-selftest.sh` — record the case count and the wall time,
  and name any case excluded. The change's gate runs this once over the completed tree; the applying child runs
  the focused cases only, so this box is the gate's to close. *(Ran 2026-11-20 on f55c8d6b: 505 cases passed,
  507.77 s, none excluded — `evidence/gate-2026-11-20.md` and its log beside it.)*
- [x] 5.2 Revert-check for the undeclared-kind refusal: mutate `loop_emit` to warn instead of exiting
  (`printf … >&2; return 0`), run `--case run#anUndeclaredKindStopsTheRun`, it must fail; restore; re-run green
  forced with `-PforceTests` not applicable here — the shell suite's forced form is
  `bash tools/loop/tests/loop-selftest.sh --case run#anUndeclaredKindStopsTheRun` plus one full run.
- [x] 5.6 Revert-check for the coordinate rule: add `lat:num` to a vocabulary row and
  `driver#noEventCarriesACoordinate` must fail; restore, forced green. *(The first run of this check passed with
  the row in place: the case read the field's *type* half and so could not see a field named `lat`. The case now
  reads the name half — the surface the rule states — and the mutation fails as this task asks.)*
