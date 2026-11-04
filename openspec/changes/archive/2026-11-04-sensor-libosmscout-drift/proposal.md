# Proposal

## Why

Nothing tells the project that upstream libosmscout has moved past the commit the submodule is pinned to, so the
only detector is a person noticing. Measured on 2026-11-04: the pin is `7c449bf66` and upstream `master` is
`9fd3a1e62` — 40 commits the branch has not merged. The cost of that drift is paid at the merge, which is where
every JNI conflict is resolved by hand, so a late merge is a more expensive merge: the recorded baselines show
one cycle carrying 56 commits and seven conflicts.

surface: no app surface — no screen, sheet or native surface changes, so the surface this change moves is the tracker and the sensor pipeline, counted with `jq -r '.rows[].id' tools/sensors/sensors.json | wc -l` (15 declared rows before, 16 after) and `bash tools/sensor-selftest.sh --list | wc -l` (78 cases before, 81 after).

## What Changes

- Declares a check row **`libosmscout-lag`** in `tools/sensors/sensors.json`: the measurement is the number of
  commits upstream `master` carries that the pinned submodule commit does not (`ahead_by`), the bound is `1` under
  `comparison: boolean` — any drift is the condition — with `consecutive: 1`, a daily cadence at 13:00 UTC,
  `class: improvement`, `category: native-jni`, `lifecycle: file+close`.
- Adds `tools/sensors/metrics/libosmscout-lag.sh`: it reads the pinned gitlink with `git ls-tree HEAD` (no
  submodule clone) and upstream's head plus `ahead_by` from one unauthenticated `compare` API call, then emits one
  finding. An unreadable API answer, or a pin the upstream repository does not carry, is an `error` finding and
  never a `clear`.
- Adds the row's id to the "condition rows and repository rows" step of `.github/workflows/sensors.yml`, because
  the row reads nothing a build publishes: `workflow#aRedRecordRunsNoRecordRow` asserts the three steps' `--rows`
  lists together equal the registry exactly.
- Gives the row **its own recurrence bound** (`k`), since this condition returns as a matter of course after every
  update: at the registry's 3 the fourth routine cycle would escalate ordinary maintenance to a person as a design
  problem. The mechanism exists in the engine and is undeclared in the spec, so the delta to `backlog-sensors`
  states it and bounds it — a declared bound is a count of at least one, refused by name otherwise, the same shape
  `maxFilings` got in issue #666.
- Updates `guidelines/BacklogTooling.md` §9 (the list of declared rows) and
  `guidelines/BacklogTooling.recipes.md` §9 (one paragraph per notable row, with this row's measured numbers).

**Not changing:** the agentic loop. A sensor-filed item carries no opt-in (`guidelines/Backlog.md` §5, I1) and
this item's fix lands inside the submodule, which the loop may not commit or push to (`tools/loop/prescreen.sh`,
issue #254). Detection is automated; the update stays a person running
`.pi/skills/update-to-current-libosmscout-master`.

## Capabilities

### New Capabilities

None. The mechanism is `backlog-sensors`' already: "a check is a declared row, not a script".

### Modified Capabilities

- `backlog-sensors`: adds one requirement — a row whose condition recurs by design declares its own recurrence
  bound, and a row's declared bound is validated as a count of at least one. Every shipped requirement keeps its
  behaviour unchanged.

## Impact

**Files**

- `tools/sensors/sensors.json` — one row appended; no existing row, bound or registry key changes.
- `tools/sensors/metrics/libosmscout-lag.sh` — new project command (the row's `cmd`).
- `tools/sensors/lib.sh` — `sensors_row_problems` validates a row's own `k` as `maxFilings` is validated.
- `.github/workflows/sensors.yml` — one id appended to the condition rows' `--rows` list.
- `tools/sensor-cases.sh` — three new cases (the row's own bound, the bound's validation, the row's emission).
- `guidelines/BacklogTooling.md` §9, `guidelines/BacklogTooling.recipes.md` §9.
- `openspec/specs/backlog-sensors/spec.md` — via the delta.

**No compiled module, no Android surface.** No Kotlin, Gradle, manifest, resource or Compose change; the APK is
unchanged and there is no phone/car parity question, because this change has no user-visible surface. **No native
change either:** no submodule patch and no `:osmscout-client-java` override — the change reads the submodule's
pointer and never its content.

**Credentials.** No new secret. The GitHub call is unauthenticated — `Framstag/libosmscout` is public and the row
makes one or two requests a day against a 60/hour limit — because `SENSORS_TOKEN` is documented as repository
access limited to this repository (`guidelines/BacklogTooling.md` §9). A rate-limited or failed call is a failed
check, so the row cannot read a refused request as a cleared condition.

**Cost.** One `git ls-tree` and one to two HTTP requests per daily tick, and no submodule checkout — a submodule
`init` would clone hundreds of megabytes into every sensor run.

**Additive, and the rollback is the row.** Removing the row from `sensors.json` and its id from the workflow's
`--rows` list retires the check; the engine names no row, so no engine file changes back. The item the row filed
is then closed with a reason, which the lifecycle never reopens.

**Breaking changes:** none.
