## 1. Red test (spec: backlog-sensors — "A check reports one of three outcomes, and only clearing closes")

- [x] 1.1 Add the case `sensors#aRecordedConditionParsesToItsObject` to `tools/sensor-cases.sh` and register
  it in `SENSOR_CASES` and `sensor_run_case`, before touching the parser. Its three assertions are: a body
  carrying `<!-- condition: {"job":"build","case":"apk"} -->` parses to that object; a body carrying no marker
  yields `{}`; and a green build's run closes the item the recorded condition identifies. Verify: on the
  pre-fix pattern `bash tools/sensor-selftest.sh --case 'sensors#aRecordedConditionParsesToItsObject'` fails —
  `a marker with its --> tail parses to its object: expected '{"job":"build","case":"apk"}', got '{}'`,
  `EXIT=1` at `date=2026-10-21T10:59:08Z` (`evidence/red-sensor-case.log`). — **done 2026-10-21**.
- [x] 1.2 Record the symptom itself, not just the parse: with the two parse assertions neutralised (probe
  only, not committed) the case fails on the identity —
  `'"fingerprint":"4c0fc623…"' not found in: {"row":"broken-main","fingerprint":"83bb457d…","verdict":"clear",
  "measurement":"success","issue":null,"action":"nothing filed, nothing to clear"}` — which is the recorded
  Sensors run's own line and its own two hashes (`evidence/red-symptom-probe.log`). — **done 2026-10-21**.

## 2. The fix (spec: the same requirement, "the finding's identity is the one the issue's own condition record declares")

- [x] 2.1 `tools/sensors/lib.sh:409`: `scan("condition:[ ]*[^>]*")` → `scan("condition:[ ]*\\{[^}]*\\}")`, with
  the reason (the marker's `-->` tail, and the writer's guarantee that a value carries no `>` and no `}`) in
  the comment above the line. Verify: `grep -n 'scan("condition' tools/sensors/lib.sh` shows the brace
  pattern, and `grep -rn 'condition:' tools/` shows no other parser of the marker. — **done 2026-10-21**.

## 3. The case is green (spec: the one scenario above)

- [x] 3.1 Re-run the case: `bash tools/sensor-selftest.sh --case
  'sensors#aRecordedConditionParsesToItsObject'`. Verify: `PASS sensors#aRecordedConditionParsesToItsObject`,
  `EXIT=0`, `date=2026-10-21T10:59:11Z` (`evidence/green-sensor-case.log`). — **done 2026-10-21**.
- [x] 3.2 Run the whole harness: `bash tools/sensor-selftest.sh`. Verify: 43 of 44 cases pass and the single
  failure, `workflow#onlyTwoTriggers` (`expected '4', got '5'` on the workflow's gated-step count), is red on
  the base commit `44b673f` without this change and untouched by it (`evidence/pre-existing-onlyTwoTriggers.log`,
  `evidence/green-sensor-selftest.log`). — **done 2026-10-21**: named as the change's one exclusion in
  `proposal.md`, "Exclusions, named".

## 4. Falsification (revert-check)

- [x] 4.1 Add mutation `8.15` to `tools/sensor-mutations.sh` — `tools/sensors/lib.sh`, the pattern reverted to
  `[^>]*`, case `sensors#aRecordedConditionParsesToItsObject` — and run `bash tools/sensor-mutations.sh
  --only 8.15`. Verify: `sensors#aRecordedConditionParsesToItsObject red under the mutation, green after the
  restore`, `1 behaved, 0 did not`, `EXIT=0`, `date=2026-10-21T11:00:04Z` (`evidence/mutation-8.15.log`), and
  `bash tools/sensor-mutations.sh --list` prints it. — **done 2026-10-21**.

## 5. Gate: none owed, and why

- [x] 5.1 Run no Gradle gate. The delta touches no compiled file — `tools/sensors/lib.sh`,
  `tools/sensor-cases.sh`, `tools/sensor-mutations.sh` — so `./gradlew test -PforceTests --no-build-cache`
  would execute nothing this change can move; the structural check is the harness above
  (`--case` red→green, the whole suite with its one pre-existing failure named, mutation `8.15`). Verify: this
  task states the exclusion, so the change claims no run it did not make. — **done 2026-10-21**.
- [x] 5.2 Copy every cited log into the change's own `evidence/` (`proposal.md` and `design.md` cite four:
  `red-sensor-case.log`, `green-sensor-case.log`, `red-symptom-probe.log`,
  `pre-existing-onlyTwoTriggers.log`, plus `green-sensor-selftest.log` and `mutation-8.15.log`). Verify:
  `ls openspec/changes/archive/*fix-sensor-condition-marker-parse/evidence/` lists them. — **done 2026-10-21**.

## 6. Archive

- [x] 6.1 Archive the change and confirm the delta merged into `openspec/specs/backlog-sensors/spec.md`.
  Verify: the archived directory exists and the capability spec's "The condition has cleared" scenario names
  `sensors#aRecordedConditionParsesToItsObject` and the identity clause. — **done 2026-10-21**.
