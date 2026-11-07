# Proposal — fix-metadata-drift-check-never-runs

## Why

One defect, verified on `main` (base `0f342656`), issue #292: **the priority invariant's per-item half runs in no
gate, so an item whose band label and stored marker drifted apart is invisible while every check stays green.**

**Root cause (one).** The per-item half of `tools/issues/check-metadata.sh` exists only in its two tracker-reading
modes — `--live` (`check-metadata.sh:113-124`, the `gh issue list` read) and `--issues` (the offline form the
self-test drives) — and nothing unattended calls either: CI runs the token-free self-check
(`.github/workflows/build.yml:195`, `bash tools/issues/check-metadata.sh`, whose header states that mode "never
reads the tracker"), while the only `--live` caller in the tree is a person's helper (`helper/backlog-pass.sh:144`)
and a skill line that tells a person to run it and see it green (`.pi/skills/issue-metadata/SKILL.md:173`). No
sensor row reads the priority pair — the registry's eight rows are `build-duration`, `package-size`, `hotspot`,
`coverage`, `source-size`, `broken-main`, `pr-conflicts` and `stale-citations` (`tools/sensors/sensors.json`).
So the one invariant the pass writes in a single operation precisely so the two halves cannot drift is enforced
only when somebody remembers to look.

**Verified, not quoted from the tracker.** Measured 2026-10-25T19:22:52Z on this base
(`evidence/live-check-head.log`):

```
$ bash tools/issues/check-metadata.sh --live; echo "exit=$?"
#305 label-without-marker: prio/p3
#304 label-without-marker: prio/p3
check-metadata: 306 issue(s) checked · 2 violation(s)
exit=1
```

Two items drift today — and they are *not* the three the item observed on 2026-10-24 (`#278`, `#277`, `#217`,
which were repaired since), which is the point: drift recurs from any later writer, so a repair is not the fix.

**Red test — the smallest case that exercises the defect, failing on HEAD.** The change's own two cases, on this
base with only the case text applied (`evidence/red-case-head.log`, taken 2026-10-25T19:24:04Z):

```
$ bash tools/sensor-selftest.sh --case sensors#metadataDriftRow
  the registry declares no metadata-invariant row
FAIL sensors#metadataDriftRow
exit=1

$ bash tools/sensor-selftest.sh --case sensors#metadataDriftClearsUnderItsOwnIdentity
  the drift keeps the identity the issue holds: … tools/sensors/metrics/metadata-invariant.sh not found in:
FAIL sensors#metadataDriftClearsUnderItsOwnIdentity
exit=1
```

Both are green after the change (`evidence/focused-green.log`), and the falsification
`bash tools/sensor-mutations.sh --only 8.19` prints `red under the mutation, green after the restore`.

surface: 1 registry row added, 1 pipeline `--rows` list extended, 2 cases added, 0 rewritten, 0 fixtures realigned, 1 guideline paragraph, 2 header lines of the check — `jq -r '.rows | length' tools/sensors/sensors.json` → 8 before, 9 after; `grep -rn 'check-metadata' --include=*.sh --include=*.yml tools/ .github/` (the one tracker-reading caller before this change is `helper/backlog-pass.sh:144`) → `evidence/surface-head.log`

**The single resulting fix.** The tracker's priority pair becomes a **declared check**: a ninth registry row,
`metadata-invariant`, whose measurement is a one-file script that runs `tools/issues/check-metadata.sh --live
--json` — the command that owns the rule — and reports that command's own violations. Its identity is the item
and the disagreement (`fingerprint.fields: ["issue","code"]`), so an item closes when that pair agrees again and
a second, different disagreement about the same item is a second condition. It is selected by the pipeline's
row step that runs for any record, because it reads the tracker and not the build.

**The requirement the defect contradicts.** `backlog-sensors` — *A check is a declared row, not a script*
(`openspec/specs/backlog-sensors/spec.md:11`) states that "adding a check is a row", and `guidelines/Backlog.md`
§9 "Checks" (the doctrine that owns the rows) states the same; `issue-priority`'s scenario *The label and the
record agree* requires that "a disagreement fails a check naming the item, because a hand edit is otherwise
invisible" — the check existed, and nothing ran it. The delta therefore **adds** one requirement to
`backlog-sensors`: the priority pair is re-checked on a schedule. The owner recorded the decision on the item
(2026-10-25, option 1 of three: a `sensors.json` row shaped like `stale-citations`, over refusing the band label
in `update.sh` or a pre-screen guard), and asked for the `update.sh` refusal as a separate, later item.

## What Changes

- **`tools/sensors/metrics/metadata-invariant.sh`** — the row's measurement: reads the tracker through
  `check-metadata.sh --live --json`, emits one `violation` per reported `(issue, code)`, one `clear` per recorded
  open condition that no longer drifts, the row-level `clear` (`{"issue":"none","code":"none"}`) when nothing is
  found, and `error` for an unreadable read or a read that reports no item at all — never a clean tracker.
- **`tools/sensors/sensors.json`** — the ninth row: `cron` `0 9 * * *`, `threshold` 1, `comparison` `boolean`,
  `consecutive` 1, `class` `bug`, `category` `specs-and-process`, `lifecycle` `file+close`, fingerprint
  `["issue","code"]`.
- **`.github/workflows/sensors.yml`** — the row joins the step that runs for any record
  (`--rows broken-main,pr-conflicts,hotspot,source-size,stale-citations,metadata-invariant`), and the step's
  comment names it. Without this the row would never run: the two `--rows` lists are the registry's whole
  execution plan, which `workflow#aRedRecordRunsNoRecordRow` already asserts (`declared == selected`).
- **`tools/sensor-cases.sh`** — two cases, `sensors#metadataDriftRow` (the row's own shape, one condition per
  drifted item, an agreeing item is no condition, the pipeline selects it, no grammar restated, the row-level
  clear, and an unreadable read as `error`) and `sensors#metadataDriftClearsUnderItsOwnIdentity` (a still-drifted
  pair is no clear; a repaired one clears under the fingerprint the issue carries).
- **`tools/sensor-mutations.sh`** — mutation `8.19`: the drift's verdict is written as a `clear`, so the
  reproducer case is red (the change's falsification, `revert-check`).
- **`guidelines/Backlog.md`** §9 — one paragraph beside the `stale-citations` one, naming the row, why it exists,
  what it reports rather than repairs, and the decision it implements.
- **`tools/issues/check-metadata.sh`** — the header's `--live` line names its unattended caller; no behaviour
  change (and its `--help` range follows the two added lines).

**Additive, no breaking change.** No Kotlin, no C++, no Gradle logic, no manifest, no resource, no compiled
file at all. Nothing about `check-metadata.sh`'s own verdicts changes, the other eight rows are untouched, and
the new row only reads: it files, comments and closes the issues it owns, and repairs no drifted pair. Rollback
path: revert this change's commit — the row disappears from the registry and the pipeline, the metric script
goes with it, and the red test's failure on HEAD reproduces.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `backlog-sensors`: one requirement **added** — *The priority pair is re-checked on a schedule* — with four
  scenarios, each naming the one case that exercises it. No shipped requirement changes. The delta lives at
  `specs/backlog-sensors/spec.md`.

## Impact

Tooling (the checks' engine, its registry and its pipeline)
- `tools/sensors/metrics/metadata-invariant.sh` — new measurement
- `tools/sensors/sensors.json` — the ninth row
- `.github/workflows/sensors.yml` — the row step's selection and its comment
- `tools/issues/check-metadata.sh` — the header line only

Cases and their harness
- `tools/sensor-cases.sh` — two cases
- `tools/sensor-mutations.sh` — mutation `8.19`

Doctrine
- `guidelines/Backlog.md` §9 — the row's paragraph

Structural checks (no compiled path, so no Gradle build and no test suite — `guidelines/Build.md` §2, its scope
block)
- `bash tools/sensor-selftest.sh` — the sensor tooling's own gate, every case offline
- `bash tools/sensor-mutations.sh --only 8.19` — the change-time falsification
- `bash tools/check-portability.sh`, `bash tools/build-inputs.sh --check`, `bash tools/issues/check-metadata.sh`,
  `bash tools/issues/metadata-mutations.sh --list`, `bash tools/check-doc-routes.sh`,
  `bash tools/check-openspec-hygiene.sh` and `bash tools/gen-feature-list.sh --check-classification` — the
  structural half the delta's pathspec owes, run once as the change's gate (`./tools/loop/gate.sh`)
- Not run, deliberately: no Gradle suite (no compiled file), and no live `workflow_dispatch` of `sensors.yml` —
  its guard refuses every ref but `main`, and the pipeline's own run is the evidence the row is reachable
