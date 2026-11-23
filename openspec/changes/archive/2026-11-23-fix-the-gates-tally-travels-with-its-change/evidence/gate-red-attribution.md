# Gate red, attributed (issue #979, tail phase F) — 2026-11-23

Gate line, run once by the gate child, quoted from `gate.log:2265` of item 979:

```
LOOP-GATE: tests=1268 failures=1 errors=0 (22 steps, 22 ran; failing: tools/loop/tests/loop-selftest.sh --from-git 7da0851f 3 cases of which 2 also red on the base, base-red tools/loop/tests/loop-selftest.sh --from-git 7da0851f:2, tools/check-openspec-hygiene.sh:1 on six stale change dirs)
```

Nothing here re-ran the gate. Every pointer below is a line of a log the run already holds.

## The paths this change edits

`scratch/first-process.diff` of item 979 names five: `guidelines/Backlog.recipes.md`,
`tools/loop/layout.tsv`, `tools/loop/shared.tsv`, `tools/loop/tests/loop-cases.sh` (three hunks — case list `:127`,
dispatch `:961`, the new case body `:4375`), `tools/loop/worktree.sh` — plus this change's own
`specs/agent-issue-loop/spec.md` delta inside the change directory. No `tools/loop/run.sh`, no `tools/loop/loop.conf`,
no prompt, no `report.sh`.

## The one failing step: `tools/loop/tests/loop-selftest.sh --from-git 7da0851f…` — 3 red cases

Tree run tally: `tests="544" failures="3"` — the XML copied beside this note
(`tools_loop_tests_loop-selftest.sh_--from-git_7da0851f4eaa6e60d6d9647e49de66f0031.xml`);
the step's own line `gate.log:1717` (`3 of 544 cases failed`).

1. **`run#aQuotaHoldPausesTheRunAndResumesIt`** — red on the tree (`gate.log:1477`), red on the base: the gate's own
   base measure at `7da0851f4eaa` recorded `base-red.log:41505-41507`
   (`LOOP-BASERED-STEP …|2`, `LOOP-BASERED-CASE …|run#aQuotaHoldPausesTheRunAndResumesIt`),
   tally `base-red.log:41508` (`tests=543 failures=2`), recorded by the gate as
   `gate.log:1720-1721` (`2 case(s) red at 7da0851f4eaa`).
   Reaches `tools/loop/run.sh` / `tools/loop/loop.conf` (records `quota-hold.5/.7/.8` in
   `tools/loop/tests/loop-mutations.sh:1141-1144`) — files this change does not edit.
2. **`run#theQuotaSwitchIsExplicit`** — red on the tree (`gate.log:1694`), red on the base by the same measure
   (`base-red.log:41507`); same two reached files, not edited here.
3. **`run#childUsageIsRecordedPerItem`** — red on the tree with
   `the worker phase is two children, not one: expected '2', got '3'` (`gate.log:947-948`), and **not** in this
   measure's two base-red cases. What proves it red before this work:
   - `base-red.log:17635-17636` — the same case, the same message `expected '2', got '3'`, inside the run headed
     `# 2026-11-21T21:55:44Z … gate exit 1` (`base-red.log:16685`) — two days before this change existed.
   - `base-red.log:26541-26542` — the same case, `every child of the item is counted … expected '6', got '8'`,
     inside the run of branch `loop-20261122-112313` (`base-red.log:26526`) — 2026-11-22, also before this change.
   - The case declares its own reach at `tools/loop/tests/loop-cases.sh:12806`:
     `fixtures/scenarios/ prompts/worker.md prompts/worker-tail.md report.sh child.sh evidence.sh leftovers.sh run.sh
     pi-child-events.sh tooling.json` — none of them edited by this diff, and the diff's `loop-cases.sh` hunks touch
     only the `worktree#` case it adds. The counting logic lives in `tools/loop/run.sh`, untouched.

Red stays red after the work (`gate.log:948`) on a case whose paths the change does not carry, and the two other
cases were measured red on `7da0851f` by the gate itself.

## `tools/check-openspec-hygiene.sh:1` — base-red, other changes' open deltas

The gate recorded it under `base-red:` only, not under `failing:` (`gate.log:2265`), with its own measure at
`gate.log:1836-1838` (`LOOP-GATE-BASERED:tools/check-openspec-hygiene.sh:1`); that base measure ran at
`2026-11-23T08:31:07Z, gate exit 1` and printed six stale `::error file=openspec/changes/…/.openspec.yaml` lines
(`base-red.log:41510-41523`): `al-prefixed-loop-skills`, `dedupe-stylesheet-loads`,
`fix-a-case-wait-decided-by-its-record`, `fix-car-follow-reengage-render`, `fix-compound-name-matching`,
`fix-details-screen-observation-scope`. The tree run's own `::error` lines (`gate.log:1830-1832`) name
`fix-a-rejected-publish-is-recorded`, `fix-a-sensor-filing-carries-the-rows-type`,
`fix-the-checkpoint-splits-the-items-chain`, and its note (`gate.log:1833`) lists six dirs written before
2026-11-19. Every one is another change's open delta; this change is archived
(`openspec/changes/archive/2026-11-23-fix-the-gates-tally-travels-with-its-change/`) and is named by no error line.

## The red test the item cites

`worktree#theGatesTallyTravelsWithTheChangeItMeasured` — red on the base at `2026-11-23T08:04:55Z`
(`evidence/red-on-base.log`, `tests=1 failures=1`) and carried by the gate's own run as
`PASS worktree#theGatesTallyTravelsWithTheChangeItMeasured` (`gate.log:175`).

## Test results cited, copied

- `tools_loop_tests_loop-selftest.sh_--from-git_7da0851f4eaa6e60d6d9647e49de66f0031.xml` — the failing step's tally
  (also in item 979's evidence directory).
- `tools_check-openspec-hygiene.sh.xml` — the hygiene step's tally (also in item 979's evidence directory).
