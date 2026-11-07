# Evidence — fix-a-run-without-pgrep-is-refused (issue #703)

## The condition this change answers

`tools/loop/run.sh:888` and `:895` read the child's own tree with `pgrep -g` / `pgrep -P` and swallow their stderr,
so on a host without procps the walk prints only `$child`, `outside_write_seen`'s filters drop that pid, and every
difference in the project's own tree is recorded and none placed, with no line saying so. The only refusal was
`tools/loop-selftest.sh:71`'s required-tool list — the *suite*'s, which a run started by hand never consults.

## The red case, on HEAD

`run#aHostWithoutThePlacementReadingIsRefused` is the change's own case; the change's only production file is
`tools/loop/run.sh`, so its HEAD half is the case against the base's `run.sh`. `evidence/red-on-head.sh` (log
`evidence/red-on-head.log`, tally `evidence/red-on-head.xml`) printed `tests=1 failures=1` at
2026-10-30T09:10:53Z:

```
<failure message="stop: the identity this run acts as is not authorised — nothing was created: expected '2', got '3'">run#aHostWithoutThePlacementReadingIsRefused: …</failure>
```

With `pgrep` off the case's `PATH` the run reached the pre-screen and stopped on an unrelated refusal (exit 3)
instead of the usage error the scenario requires (exit 2).

## What the change does

- `tools/loop/run.sh`: one `command -v pgrep` check beside the `LOOP_AGENT_CMD` check in the header, whose
  `loop_die` names the command and the outcome — "a host without procps would record every difference and place
  none". It runs before the lock, the worktree, the pre-screen, a branch or a child exists.
- `tools/loop-cases.sh`: `run#aHostWithoutThePlacementReadingIsRefused` simulates the host with a `PATH` of symlinks
  holding every executable except `pgrep`, and asserts the refusal's status and sentence, no worktree and no child.
- `tools/loop-mutations.sh`: record `12.13`, whose mutation deletes the check.
- `guidelines/Backlog.md`, section 7: the placement-reading paragraph now says the run refuses such a host at its
  header and that the suite's refusal stays.
- `tools/loop-selftest.sh` unchanged: it still refuses the host before a suite runs 288 cases.

## The revert direction

`evidence/mutation-12.13.log` — the record applied to `run.sh`, the case run, restored and run again:

```
RED    12.13  run#aHostWithoutThePlacementReadingIsRefused   (the run refusing a host whose placement reading cannot answer)
       2026-10-30T09:12:36Z — a host without the placement reading is a usage refusal: …
restore 12.13 run#aHostWithoutThePlacementReadingIsRefused green again

red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0
```

## The gate

`evidence/gate.log` (tallies in `evidence/gate-tallies/`, summary in `evidence/gate-summary.xml`) printed, one run:

```
LOOP-GATE: tests=726 failures=0 errors=0 (tools/loop-selftest.sh --from-git 2f954a04… 395s, bash tools/loop-mutations.sh --check 2s, tools/loop-mutations-selftest.sh 277s, …, tools/issue-tooling-selftest.sh 73s)
```

`openspec validate fix-a-run-without-pgrep-is-refused` — `Change 'fix-a-run-without-pgrep-is-refused' is valid`
(`evidence/openspec-validate.log`).

## The change's numbers, as the measuring command printed them

`evidence/measure.log`:

```
LOOP-MEASURE-SUITE cases=288 cmd=tools/loop-selftest.sh --list
LOOP-MEASURE-GATE seconds=780 suites=20
LOOP-MEASURE-CHANGE fix-a-run-without-pgrep-is-refused scenarios=13 cases=9 unnamed=4
LOOP-MEASURE-SURFACE surface: 1 case added, 0 rewritten, 2 code sites, 1 guideline paragraph, 0 fixtures realigned — …
LOOP-MEASURE-TALLY openspec/changes/fix-a-run-without-pgrep-is-refused/evidence/gate-summary.xml tests=726 failures=0
LOOP-MEASURE-TALLY openspec/changes/fix-a-run-without-pgrep-is-refused/evidence/red-on-head.xml tests=1 failures=1
```

`scenarios=13 cases=9 unnamed=4`: the added scenario is the thirteenth and names its case. The four unnamed ones are
inherited verbatim from the owning requirement, which carries them today — the delta restates the requirement as it
stands and adds one scenario, and does not rewrite unrelated text.

## Scenario → case

| scenario (delta) | case |
|---|---|
| A host that cannot read the child's tree refuses the run | `run#aHostWithoutThePlacementReadingIsRefused` |

The scenario's red half is `evidence/red-on-head.xml` (`tests=1 failures=1`, 2026-10-30T09:10:53Z) and its green half
is the gate's `tools/loop-selftest.sh` tally (`tests=288 failures=0`, `evidence/gate-tallies/`).
