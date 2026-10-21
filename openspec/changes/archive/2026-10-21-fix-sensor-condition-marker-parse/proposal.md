# fix-sensor-condition-marker-parse

## Why

Issue #230. Verified on the run's base commit `44b673f` on 2026-10-21, in the loop's worktree.

**The claim holds, at one line.** `tools/sensors/lib.sh:409` reads the condition the tracker's item records:

```jq
condition: ((((($issue.body // "") | [scan("condition:[ ]*[^>]*")] | .[0]) // "") | sub("^condition:[ ]*"; "") | fromjson?) // {}),
```

The marker the engine itself writes (`tools/sensors/run.sh:205`) ends with the marker's own tail —
`<!-- condition: {"job":"build","case":"apk"} -->` — so `[^>]*` stops at the `>` of `-->` and the substring it
hands to `fromjson` is `{"job":"build","case":"apk"} --`, which does not parse. `fromjson?` swallows the
failure and the `// {}` fallback turns every recorded condition into an empty object.

Reproduced directly, without the engine:

```
$ jq -c '[.[]? | . as $issue | {condition: ((((($issue.body // "") | [scan("condition:[ ]*[^>]*")] | .[0]) // "") | sub("^condition:[ ]*"; "") | fromjson?) // {})}]' <<< '<a body carrying the marker>'
[{"condition":{}}]
```

**Root cause, one sentence:** the condition parser matches the marker with a pattern that tolerates the
marker's `-->` tail instead of taking the object between the braces, so no recorded condition ever parses and
the sensor that would clear an item can only fall back to its own identity.

**The consequence, measured through the engine, not inferred.** `tools/sensors/conditions/broken-main.sh:47-58`
iterates `SENSORS_CONDITIONS` and emits one `clear` per open recorded condition; when nothing parses,
`cleared` stays 0 and the fallback arm emits the *green record's* identity `{"job":"gate","case":"the-build"}`
instead of the item's `{"job":"build","case":"apk"}`. The new case asserts the two identities, and the probe
with the parse assertions neutralised reproduces the recorded Sensors run of 2026-10-21T04:26Z verbatim
(`evidence/red-symptom-probe.log`):

```
  the clear names the recorded condition's identity: '"fingerprint":"4c0fc623bb0a7f23c5e2bceb8261bae03673acacd1ccc3485b191fb59c7bbe6d"' not found in:
  {"row":"broken-main","fingerprint":"83bb457d6f2996fec5413363404d41f47fad1ca7ce10db5c43de4664d2fa2207","verdict":"clear","measurement":"success","issue":null,"action":"nothing filed, nothing to clear"}
```

Both hashes are the item's own: `4c0fc623…` is the fingerprint of the item's condition
`{"job":"build","case":"apk"}` and `83bb457d…` is the fingerprint of the fallback's `{"job":"gate",
"case":"the-build"}` — exactly the values issue #230 quotes from the run, so the reproduction is the
defect and not a lookalike.

**Red test on HEAD.** `sensors#aRecordedConditionParsesToItsObject` in `tools/sensor-cases.sh`, run with
`bash tools/sensor-selftest.sh --case 'sensors#aRecordedConditionParsesToItsObject'` against the pre-fix
pattern (`evidence/red-sensor-case.log`, `date=2026-10-21T10:59:08Z`):

```
  a marker with its --> tail parses to its object: expected '{"job":"build","case":"apk"}', got '{}'
FAIL sensors#aRecordedConditionParsesToItsObject
EXIT=1
```

Green on the fix, same command (`evidence/green-sensor-case.log`, `date=2026-10-21T10:59:11Z`):
`PASS sensors#aRecordedConditionParsesToItsObject`, `EXIT=0`.

**The project's gates produce no JUnit XML for this delta.** `tools/sensor-selftest.sh` is a shell harness
that prints `PASS`/`FAIL` per case and exits 0/1; there is no JUnit report and no Gradle task behind it. So
the red and green halves above are quoted as the harness prints them, with their exit status and timestamp,
and `LOOP-RED-TEST`/`LOOP-GATE` carry those lines rather than a `tests=/failures=` tally.

**Owning capability:** `backlog-sensors` — requirements "A check reports one of three outcomes, and only
clearing closes" and "Identity is finer than the check that produced it". **Owning guideline section:**
`guidelines/Backlog.md` §9 "Checks", whose outcome block reads `cleared -> comment the measurement, close
once the consecutive count is met` and whose first rule is "Identity is finer than the check" (routed by
`AGENTS.md`'s Documentation Map: "the backlog itself, or the automation over it … → `Backlog.md` §4, §5, §9,
§10").

## What changes

- `tools/sensors/lib.sh:409` — the condition is read as the object between the braces
  (`scan("condition:[ ]*\\{[^}]*\\}")`) instead of everything up to the next `>`, with the reason in the
  comment above it. The body's field values cannot contain `>` (they are `gsub`'d to `-` by
  `sensors_condition_record`, `tools/sensors/run.sh:193`) and never contain `}`.
- `tools/sensor-cases.sh` — one case, `sensors#aRecordedConditionParsesToItsObject`: the marker with its
  `-->` tail parses to its object, a body with no marker yields `{}`, and a green build's run closes the
  item the recorded condition identifies.
- `tools/sensor-mutations.sh` — mutation `8.15`: revert the pattern to `[^>]*`, and the case must fail.

No production code, no workflow, no registry, no guideline edit: nothing about the contract changes, the
parser was simply not implementing it. Rollback is restoring the one pattern, and the mutation is that
rollback — the case goes red again, so the regression is visible rather than silent.

## Impact

- `tools/sensors/lib.sh` (1 line + comment), `tools/sensor-cases.sh` (1 case + 2 registration lines),
  `tools/sensor-mutations.sh` (1 entry).
- Verification: the case, the full sensor self-test, mutation `8.15`; no Gradle gate is owed because no
  compiled file changes (see `tasks.md`, task 5).

## Exclusions, named

`bash tools/sensor-selftest.sh` reports **1 of 44 cases failed** on this worktree, and that case is red on the
base commit without this change: `workflow#onlyTwoTriggers` counts the steps gated on
`steps.record.outputs.ready == 'true'` and expects 4, while `.github/workflows/sensors.yml:127` is a fifth
(the `Close what the tracker says about itself` step, added by `c891ba3` without updating the count). This
change touches neither the workflow nor that case function (`git diff --numstat -- tools/sensor-cases.sh`:
0 deletions); the log is `evidence/pre-existing-onlyTwoTriggers.log`. It is a separate defect with a separate
root cause, so fixing it here would make two mechanisms one change.
