# Proposal — fix-child-cap-follows-what-one-items-gate-owes

## Why

Issue #663: **the run gives every child the same wall-clock, and that number is smaller than the gate one item owes, so
a child that has already finished green is ended and its item is recorded `blocked`.**

**Root cause (one).** `LOOP_CHILD_MAX_SECONDS` (2700 s) was read as *the* bound of every child (`tools/loop/run.sh`,
`run_child`), and nothing tied it to what the child's own item costs. An item owed, on this host and measured
2026-10-26 on #310: gate round 1 (red) 957 s, one focused falsification suite 233 s, gate round 2 (green) 972 s, and
542 s of reading the item, the edit, the evidence, the archive and the report — 2 704 s of the 2 704 s the run allowed,
so the cap fired 8 s after the green `gate-summary.xml` was written, with `tests=601 failures=0 errors=0` and
`evidence-check: PASS` already on disk. Seven of the 132 item directories carry `LOOP-TIMEOUT` as their first line
(#255, #259, #262, #271, #279, #303, #310), and the same shape ended #664 and #666.

**Verified, not quoted from the tracker.** Measured in this tree 2026-10-26, by the case's own three dry runs — the
bound is printed in the run's header before anything is started: on a profile as the base recorded it (no gate key)
the header read `child-max-seconds=2700`, and on the profile this change ships it reads `child-max-seconds=3900`; a
profile recording a 60 s floor under an hour of gate reads `child-max-seconds=3600`, i.e. the gate's number and not
the floor. `evidence/mutation-64.1.log` is the same case run against the derivation removed.

The item's own record is `.pi/issue-loop/items/310/worker-report.md` (`LOOP-TIMEOUT: the child exceeded 2700 s`) and
the tallies it left are `openspec/changes/archive/2026-10-26-fix-loop-reset-worktree-submodule/evidence/gate-summary.xml`
(`tests="601" failures="0" errors="0"`, the sum of the 23 tallies beside it).

**The red test** — one case, because the repair makes one claim in two halves (what the profile records is not below
the measurement, and what the child is given is that number rather than the floor):

- `run#theChildCapIsNeverBelowWhatOneItemsGateOwes`. It is red when the derivation is removed and green with it: the
  harness's record `64.1` keeps the floor and drops the derivation, and the case then reads a child given less than
  its own item's gate costs — `evidence/mutation-64.1.log`.

**Owning capability and guideline sections.** The behaviour is the shipped requirement *One command starts a budgeted
run, with no dialogue* of `agent-issue-loop`, whose scenario *A child that outlasts the configured wall-clock is ended
and recorded* names the constant (`openspec/specs/agent-issue-loop/spec.md:51`). The prose that owns the bound is
`guidelines/Backlog.md` section 7, "What an item costs" (`:343`), and the key table a project copies is section 11
(`:858`).

surface: 1 case added, 0 rewritten, 8 case bodies moved onto the new `conf_with_child_cap` helper (one line each), 1
mutation record added (64.1), 1 requirement modified with 1 scenario added — the numbers are
`grep -c 'conf_with_child_cap' tools/loop-cases.sh` (9: the helper and its eight call sites),
`grep -c 'theChildCapIsNeverBelowWhatOneItemsGateOwes' tools/loop-cases.sh` (3: the declared set, the dispatch table
and the case) and `grep -n '64.1' tools/loop-mutations.sh` (one record).

## What changes

- `tools/loop/loop.conf` — `LOOP_CHILD_MAX_SECONDS` (2700) is now documented as the **floor** under a child, and
  `LOOP_ITEM_GATE_SECONDS=3900` records what one item's gate owes: one gate round measured at 971 s over 23 suites,
  times the 1.5 spread the item floor's derivation already uses (1 457 s, recorded as 1 500 s), for two rounds —
  red then green — plus 900 s for the phases the rounds are not (the measured 542 s, rounded up). The same file's
  suite map gains `tools/loop-spend*|bash tools/loop-spend-selftest.sh`: this change's first gate round named
  `tools/loop-spend-selftest.sh` as a pipeline Check step no pair of the map owed, a red the loop-spend tooling left
  on `main` and one this change cannot leave behind, because the gate cannot come back green past it.
- `tools/loop/run.sh` — one function, `child_cap`, returns the larger of the two numbers; the header names the bound
  it gave (not the floor) and `run_child` uses the same value, so the run's own line and the `LOOP-TIMEOUT` line that
  ends a child cannot disagree. Nothing else in the engine changes: the cap is still one wait, one kill, one line.
- `tools/loop-cases.sh` — the case above, and `conf_with_child_cap`, the seam the eight cap-shortening cases now use:
  a case that asks for a two-second bound must blank the gate's number too, or the derivation would keep it long.
- `tools/loop-mutations.sh` — `64.1` drops the derivation, so the case reads a child given less than its own item's
  gate costs.
- `guidelines/Backlog.md` — section 7 states the bound as the larger of two recorded numbers, with this measurement,
  and the key table in section 11 gains the key.
- `openspec/specs/agent-issue-loop/spec.md` — via the delta: the requirement's child scenario reads the given bound
  and names both keys, and a scenario states the claim the case holds.

## Spec

`MODIFIED` **One command starts a budgeted run, with no dialogue** — its body is unchanged; the scenario *A child that
outlasts the configured wall-clock is ended and recorded* now names the bound the run gives a child (the larger of the
floor and what one item's gate owes), and one scenario is added: *The bound a child is given is what one item's own
gate costs* (`run#theChildCapIsNeverBelowWhatOneItemsGateOwes`). Every shipped scenario of that requirement is carried
verbatim; `openspec validate fix-child-cap-follows-what-one-items-gate-owes --strict` passes.

## Impact

`tools/loop/loop.conf`, `tools/loop/run.sh`, `tools/loop-cases.sh`, `tools/loop-mutations.sh`, `guidelines/Backlog.md`,
the spec delta. No Kotlin, C++ or Gradle file: the suites the profile maps for `tools/loop/**` are the gate, and this
change's own gate round is `evidence/gate-summary.xml` (`tests="601" failures="0" errors="0"`, the sum of the tallies
beside it, 16 suites, 1 152 s) — its first round was red for two reasons, both fixed before the second: the case
declared too few of the files it reaches, and the pipeline step no suite pair owed. Rollback: revert the branch — the engine then reads the floor
alone, which is exactly the behaviour it had, because `child_cap` falls back to `LOOP_CHILD_MAX_SECONDS` when the
gate's key is absent.

## Open

The item's third candidate repair — *make the second round cheap, re-running only the suites the first round named
red* — is not taken here, and the measurement ranks it second rather than wrong: it would have saved ~972 s of the
2 704 s, but it changes the gate's contract and the falsification rule (`revert-check` re-runs what it mutated), and
it still leaves the child bound needing a number the run can read. The derivation lands first because it is the one
number the profile can state before the child starts; the second round stays a candidate for an item of its own.

The 1 500 s per round is a recorded fact about this host and not a reading of the gate's runtime: a run does not know
its pathspec before the worker edits, and the gate's tallies are cleared before each worker (`clear_stale_test_results`),
so a derivation from the tree's own measurement would read a stranger's round or nothing at all.
