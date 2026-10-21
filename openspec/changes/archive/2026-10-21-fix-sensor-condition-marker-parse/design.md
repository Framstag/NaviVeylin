# Design — fix-sensor-condition-marker-parse

## Root cause

`tools/sensors/lib.sh:409` parses the condition an item records with
`scan("condition:[ ]*[^>]*")`. The marker the engine writes itself ends with ` -->`
(`tools/sensors/run.sh:205`), so the match runs into the tail, `fromjson` receives
`{"job":"build","case":"apk"} --`, fails, and the `// {}` fallback yields an empty object for every item that
ever recorded a condition. `tools/sensors/conditions/broken-main.sh:47-58` then iterates that empty list,
`cleared` stays 0, and the fallback arm emits the green record's own identity `{"job":"gate","case":"the-build"}`
— a fingerprint that owns no item — instead of the recorded `{"job":"build","case":"apk"}`. Measured in
`proposal.md`'s `## Why`: the probe reproduces both of the issue's fingerprints (`4c0fc623…` expected,
`83bb457d…` reported) and the recorded report line.

## The fix

One pattern, in one place:

```jq
condition: ((((($issue.body // "") | [scan("condition:[ ]*\\{[^}]*\\}")] | .[0]) // "") | sub("^condition:[ ]*"; "") | fromjson?) // {}),
```

The object between the braces is the whole match; the marker's tail is outside it. The bound that makes it
correct rather than lucky is written above the line and is enforced upstream:
`sensors_condition_record` (`tools/sensors/run.sh:193`) `gsub`s `>` and newlines out of every value it
serialises, so a value can never end the match early at a `>`; no value can contain `}` either, so the
brace-delimited match cannot stop inside the object. A body with no marker still degrades to `{}` — the
second half of the case — so an item that records no condition is not a crash, it is the absent condition the
fallback arm exists for.

Read `scan` and never a named capture, unchanged: a marker missing one field must degrade rather than error,
because an error here would empty the whole conditions list.

## Blast radius

- `tools/sensors/lib.sh` — one line and the comment that carries the reason.
- `tools/sensor-cases.sh` — one case (three assertions: the marker parses, a body without one does not, and a
  green run closes the item the recorded condition identifies) plus its two registration lines.
- `tools/sensor-mutations.sh` — mutation `8.15`, which is the pre-fix pattern and therefore the rollback.
- Nothing else reads the marker: `tools/sensors/lib.sh` is the only parser of `condition:` in the tree
  (`grep -rn 'condition:' tools/` → the writer `tools/sensors/run.sh:205` and this reader). The other
  `[^>]*` patterns in `tools/issues/lib.sh` and `tools/issues/duplicates.sh` deliberately match a whole
  `<!-- … -->` comment between its delimiters, where `[^>]*` is correct, and are untouched.

## Risk

The pattern is narrower than the old one: a body whose condition object contains a `}` in a *value* would
now stop at that brace. That cannot happen by construction (values are written by
`sensors_condition_record`, which strips `>` and newlines and never introduces `}`); if a future writer
serialised a JSON value containing `}`, the case's first assertion would go red only when the value is the
last one — so the comment above the line names the constraint instead of leaving it implicit. The alternative
that removes the constraint entirely (parse the marker's content as JSON with a proper scanner) is listed as
eliminated below, because it costs a second jq program for a property this project's writer already holds.

## Alternatives the evidence eliminated

- **Match the whole comment and strip the delimiters** (`scan("<!--[[:space:]]*condition:[[:space:]]*[^>]*-->")`,
  the shape `tools/issues/lib.sh:265` uses). Eliminated by the marker's payload: `[^>]*` still cannot cross
  the `>` of `-->`, and stripping a trailing `-->` afterwards would fix the tail but leave the parse
  depending on the tail's exact spelling — the same class of bug one step away. The object between braces is
  the thing being parsed.
- **Make the sensor derive the cleared identity from its input instead of from the record.** This is the
  issue's "second face", and it is eliminated by the data: a red build's record says `build`/`apk` while a
  green build's says `gate`/`the-build` (the recorded Sensors run of 2026-10-21T04:26Z), so the green run's
  input cannot name the condition it clears. The record is the only route, which is exactly why the parser
  has to work.
- **Delete the `// {}` fallback so a malformed marker is loud.** Eliminated by the contract: an item filed by
  a person carries no condition record at all, and the run must then report "the condition is absent" rather
  than fail. The fallback is the third outcome's input, not a mask — and the case's second assertion is what
  keeps it.
- **Leave the fallback arm's identity as is and let the tracker deduplicate.** Eliminated by the measurement:
  `83bb457d…` owns no item, so nothing is deduplicated; the clear is reported as `nothing filed` and the
  item stays open forever, which is the item's whole complaint (`#198` open after a green main).
- **Fix it in the workflow instead** (skip the condition rows on a green build). Eliminated as a reversal of
  a recorded decision: `backlog-sensors` requires a green build to report the clear, and the row's own arms
  are the specified place for it.

## Verification

- `bash tools/sensor-selftest.sh --case 'sensors#aRecordedConditionParsesToItsObject'` — red on the pre-fix
  pattern, green on the fix (`evidence/red-sensor-case.log`, `evidence/green-sensor-case.log`).
- `bash tools/sensor-mutations.sh --only 8.15` — mutation applied, case red, restored, case green
  (`evidence/mutation-8.15.log`).
- `bash tools/sensor-selftest.sh` — the whole harness, with its one pre-existing failure named in
  `evidence/pre-existing-onlyTwoTriggers.log`.
- No Gradle gate: the delta touches no compiled file, so the structural check above is the whole verification
  (this is stated as an exclusion in `tasks.md`, task 5).

## Rollback

Restore the one pattern. `sensors#aRecordedConditionParsesToItsObject` fails with
`expected '{"job":"build","case":"apk"}', got '{}'` — the rollback is the mutation, so it is visible in both
directions.
