# A refused escalation files the returned condition as a new item

## Why

Issue #780. The `broken-main` row reported the red main as `escalated` on every tick since 2026-11-03T23:36
and wrote nothing; tick 41782438964 (2026-11-04T05:59) printed `action=escalated (past 3 recurrences)` and the
step exited 0, while #198 stayed `CLOSED` with `status/fixed-by` and neither a comment, a label nor a reopen
landed on it.

Root cause: the escalation path drops the writer's refusal. `sensors_escalate`
(`tools/sensors/run.sh:442-457`) calls the one guarded write path and then ends with `rm -f "$bodyfile"
"$commentfile"` (`tools/sensors/run.sh:456`), so the function returns `rm`'s status and never the writer's;
`tools/sensors/run.sh:513-517` reports `escalated (past $k recurrences)` without reading a status at all. The
writer does refuse, and it says so: the escalation's label pair sets `status/on-hold` on an item that carries
`status/fixed-by`, so `tools/issues/update.sh:216-218` runs `issues_status_settled_guard`, which prints
`status-settled: #<n> carries status/fixed-by, a settled state of the project profile — pass --displace-settled
to name the transition` and exits 3 (`tools/issues/lib.sh:408`). `sensors_write` (`tools/sensors/run.sh:215-222`)
was already returning that code; nothing above it read it. Two writes are lost at once — the label pair that
hands the item to a person, and any item at all — and the tick stays green.

The red case on the run's base `ec58cb91177f2d1c4621b87cdd26a9c7fced5117`, `2026-11-05T07:52:11Z–07:52:12Z`
(`evidence` copy: `.pi/issue-loop/items/780/red-case.log`):

```
$ bash tools/sensor-selftest.sh --case 'run#anEscalationOnASettledIssueFilesANewItem'
  the returned condition is filed as a new item: status-settled: #98 carries status/fixed-by, a settled state of the project profile — pass --displace-settled to name the transition
fixture-emit: fingerprint=48a09a835ee92d32ebcf0d2132fdfc6048aa30639ce1644360e52f3086ddbc51 verdict=violation measurement=5 issue=98 action=escalated (past 3 recurrences): expected '1', got '0'
FAIL run#anEscalationOnASettledIssueFilesANewItem
```

The runner's own tally: `tests=1 failures=1` (the one case it ran), `2026-11-05T07:52:12Z`.

The single fix: `sensors_escalate` returns the writer's exit code, and the escalation branch of
`sensors_outcome_violation` reads it — on `0` it reports the escalation as it does today, and on a refusal it
files the returned condition as a new item (under the same per-run `maxFilings` bound the no-issue branch
already spends) and reports the item it filed. Only when that bounded filing cannot happen does the row fail,
so the tick is red instead of silent. A routine write never displaces the settled state: the state means a
person's decision stands, and the escalation was never entitled to move it.

Owning capability and requirement: `backlog-sensors`, "A repeating condition escalates instead of reopening
forever" (`openspec/specs/backlog-sensors/spec.md:140-159`) — the defect contradicts the escalation it
prescribes by performing none of it. Owning guideline section: `guidelines/BacklogTooling.md` §9 "Checks", the
hysteresis rule (`guidelines/BacklogTooling.md:28-30`), which the change extends with the refusal clause.

Surface, counted rather than assumed (`grep -c 'escalat'` over the four places the behaviour is asserted):

- **1 case added, 0 rewritten, 0 fixtures realigned** — `grep -c 'escalat' tools/sensor-cases.sh` = 22 (6 cases
  name it, this change adds one), and `grep -l 'escalation' tools/sensors/fixtures/*.json | wc -l` = 6 fixture
  registries already declare `escalation`, read unchanged.
- The engine holds the rule in 9 lines: `grep -c 'escalat' tools/sensors/run.sh tools/sensors/lib.sh
  tools/sensors/sensors.json` = 6, 3, 3. The change edits two of the six.
- The spec states it in 5 lines (`openspec/specs/backlog-sensors/spec.md`), the guideline in 1
  (`guidelines/BacklogTooling.md` §9), and `guidelines/Backlog.md` in 7 (the write table, unchanged).

This change's own numbers (`git diff --stat -- tools guidelines openspec/changes/fix-settled-escalation-files-an-item`,
run from the change root): `3 files changed, 72 insertions(+), 8 deletions(-)` over the two production files and
the guideline. The loop's measurement of the change, quoted from its own block
(`.pi/issue-loop/items/780/measure-block.txt`): `LOOP-MEASURE-CHANGE
fix-settled-escalation-files-an-item scenarios=4 cases=4 unnamed=0` — every scenario of the delta names a case
that exists — and `LOOP-MEASURE-SUITE cases=314 cmd=tools/loop-selftest.sh --list` for the project's declared
count. The block carries no gate time (`LOOP-MEASURE-GATE seconds=- reason=no tally under the gate's directory
carries a time — the gate of this tree has not run`): the gate is the gate child's, not this worker's.

## What Changes

- `tools/sensors/run.sh` — `sensors_escalate` returns the writer's exit code; `sensors_outcome_violation`'s
  bound branch reports `escalated` only on `0` and otherwise files the condition as a new item within the
  row's filing bound, failing the row when that bound leaves no filing; `sensors_file` takes the writer's own
  flags after the body, so that filing can pass `--allow-duplicate` (the duplicate is deliberate: the same
  condition, returned after the settled item's fix).
- `tools/sensor-cases.sh` — `run#anEscalationOnASettledIssueFilesANewItem`, the red case above.
- `openspec/specs/backlog-sensors/spec.md` — the modified requirement, one added scenario.
- `guidelines/BacklogTooling.md` §9 — the one refusal clause in the hysteresis rule.

## Impact

- Affected files: `tools/sensors/run.sh`, `tools/sensor-cases.sh`, `guidelines/BacklogTooling.md`,
  `openspec/changes/fix-settled-escalation-files-an-item/**`. No compiled file, no Gradle module, no native
  source, no registry key, no profile key.
- Additive: no existing verdict, label or file changes except on the refusal path, which today writes nothing
  and reports a write that did not happen.
- Rollback: revert the two production hunks in `tools/sensors/run.sh`; the case returns red and no fixture
  moves.
- Affected guidelines: `guidelines/BacklogTooling.md` §9 only (`guidelines/Backlog.md` §5's write table already
  says a check never reverses a person's decision, which the fix keeps).
