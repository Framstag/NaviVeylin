# Evidence — fix-landing-commit-is-scored (issue #709)

## The condition this change answers

`tools/loop/state.sh:432`–`:434` (at base `0bc31dc`) accepted a landing's branch, its commit *count* and its
evidence and no commit; `tools/loop/report.sh:250` therefore printed a pointer to the tracker
(`gh issue list --label broken-main`) instead of a number, which is the one figure
`run-report-trend-figures` (§D4,
`openspec/changes/archive/2026-10-29-run-report-trend-figures/design.md:80`) deferred to this item.

## The item's open question, measured before the key was specified

`evidence/probe-gh-run-list.log` (2026-10-30T07:32:35Z, `gh run list --commit <sha>` against this repository):

- a landing commit answers with its one `push` run — `2f954a04` → `Build Debug APK`, `completed`/`success`;
- a commit with no run answers `[]` — `0bc31dc7` → `[]`, so *"no run yet"* is a state, not a green run;
- one headSha carries a scheduled `Sensors` run beside the landing's `push` run (`357a4670`, `42d3e000`,
  `53021e88`), so the score reads the **push** run and never "the newest run of the commit".

## The red case, on HEAD

`evidence/red-on-head.sh` runs the four cases the delta names, one suite invocation each. On the base commit's
content it printed `tests="4" failures="4"` at 2026-10-30T07:35:39Z (`evidence/red-on-head.xml`,
`evidence/red-on-head.log`), each case failing on the fact that does not exist:

```
FAIL state#doneRecordsTheCommitItLanded — loop: done: unknown argument --merged-sha
FAIL state#theLandingScoreIsReadOnceAndStored — loop: done: unknown argument --merged-sha
FAIL state#aLandingWithNoRunIsUnscored — loop: done: unknown argument --merged-sha
FAIL report#landingsAreTheStoredNumber — loop: done: unknown argument --merged-sha
```

## What the change does

- `tools/loop/state.sh`: `done --merged-sha` records `.merged.sha` and refuses a value that is not 7–40 hex
  characters; the new `score-landings` command reads `gh run list --commit <sha>` once per unscored landing, picks
  the `push` run, and writes `.landedReddenedMain` — `true` for a run that did not succeed, `false` for one that
  did, and nothing (with the reason printed) when there is no run yet, no finished run, or no readable answer.
- `tools/loop/run.sh`: both `done` calls that record a landing pass the commit the merge made (the `base` the
  merge function already re-reads), and each calls `score-landings` once — best-effort, so a network fault can
  never decide an item the run has already closed on its work.
- `tools/loop/report.sh`: `landings:` prints the stored score (`1 of 2 reddened main · 1 unscored · 1 records no
  commit`) and reads nothing but the state file and git.

## The rewritten case can still fail

`evidence/revert-check-72.6.log` — mutation 72.6 (the report calling the tracker) applied to the *rewritten* case
`report#landingsAreTheStoredNumber`:

```
RED    72.6  report#landingsAreTheStoredNumber   …  the report called the tracker: issue list --label broken-main
restore 72.6 report#landingsAreTheStoredNumber green again
red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0
```

## The gate

Two rounds, which is what a red round plus its repair looks like (`tools/loop/loop.conf` records the same shape
for #310: 957 s red, 972 s green):

- **round 1** (`evidence/gate-round-1-red.log`, tally in `evidence/gate-round-1-red.xml`, tallies in
  `evidence/gate-round-1-tallies/`) printed `tests="724" failures="1" errors="0`" — the one failure was the change's own doing:
  `driver#everyCaseDeclaresTheFilesItCanReach` named `state#theLandingScoreIsReadOnceAndStored` as reaching
  `tools/issues/fixtures/gh` without declaring it. The declaration gained `tools/issues/fixtures/`.
- **round 2** (`evidence/gate.log`, tallies in `evidence/gate-tallies/`, summary in
  `evidence/gate-summary.xml`) printed `tests="724" failures="0" errors="0`" in
  `LOOP-GATE: tests=724 failures=0 errors=0`, over `tools/loop-selftest.sh --from-git <base>` (287 of 287 cases,
  465 s), `tools/loop-mutations.sh --check` (217 records), `tools/loop-mutations-selftest.sh` (296 s), the
  portability checks, the issue tooling's suite and the four `--list` mutation registries.

Nothing in the second round is a "refresh": its only difference from the first is the two `# files:` declarations
the first round named.

## Surface, as planned and as landed

Planned in `proposal.md`: 3 cases added, 1 rewritten, 1 extended, 1 mutation record renamed, 1 state flag, 1 state
command, 1 fixture, 1 spec requirement added and 2 scenarios restated, 0 compiled files. Landed as planned; the
four new/rewritten cases are green in round 2 and red on HEAD by `evidence/red-on-head.xml`.
