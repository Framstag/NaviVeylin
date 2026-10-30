# Evidence — run-report-trend-figures

Every number below is a command's output; the file that holds it is named. Taken 2026-10-29 on a tree whose HEAD
was `357a4670` when the last gate ran, with this change uncommitted.

## The gap on HEAD

```
$ grep -n "first round\|round 1\|reddened\|landings:\|trend" tools/loop/report.sh
(no match, exit 1)
```

`red-on-head.log` is the item's own probe (`.pi/issue-loop/items/247/absence-probe.log`, 2026-10-29T09:33:28Z),
copied unedited. Its first block is that command; its later blocks are the probe's follow-up greps, which is what
they are. The absence holds, and the file now prints the block (`live-state.log`).

## Two review rounds, both refused, both answered

`reviewer-report-1.md` and `reviewer-report-2.md` sit in `.pi/issue-loop/items/247/`.

**Round 1** (`LOOP-BLOCKING: 1`): the rate was counted from `reviewRounds`, and `run.sh:1680` sets that count
before the reviewer runs, so a refused round 1 that blocked counted as a pass. Measured on the tree's own state:
`#16` and `#197` are `blocked` with `reviewRounds: 1` against `VERDICT: FAIL` reports; the printed `9/12` was
`6/12`. The owner chose to record the verdict and keep the figure.

**Round 2** (`LOOP-BLOCKING: 4`): three were documentation — a `[x]` quoting the refused rule, a `[x]` quoting a
figure the code no longer prints, and quotes that no command in this record produced — and one was the same defect
one path narrower: `#51` carries `reviewRounds: 1` too, but its round 1 was **cut** at the 250-turn bound
(`LOOP-TIMEOUT`, no `VERDICT:`), and `run.sh` read that silence as a refusal. That path now records `cut-short`,
with its own case and mutation `73.1`.

What the two rounds changed in the code: `state.sh done --review-verdict` (closed vocabulary), `run.sh` passing
`review_verdict` at its five recording sites and recording a turn-bound cut as `cut-short`, and `report.sh`
counting the rate from the verdict with the undecided bucket named apart.

## The gate

`gate.log` is one full run from the change root after the round-2 corrections. Its verdict line is the gate's own
aggregate, which no single tally carries, so it is quoted here by step:

- **this change's step is green**: `tools/loop-selftest.sh --from-git 357a4670…` → `tests="284" failures="0"
  errors="0" time="442"` (`gate-tallies/tools_loop-selftest.sh_--from-git_357a4670….xml`) — 283 cases plus the one
  round 2 added.
- `bash tools/loop-mutations.sh --check` → `tests="217" failures="0"`; the doc, portability, issue-tooling and
  mutate suites are green in their own tallies under `gate-tallies/` (21 files, the directory emptied before the
  copy).
- **the Gradle step is red, and it is not this change**: `errors="1"`, `tests="0"`, 45 s, from
  `e: …/app/src/test/java/com/naviveylin/ui/map/LocationOptionsOverlayComposeTest.kt:171:17 No value passed for
  parameter 'avoidTolls'` (`gate.log:196`) — another session's in-flight work under `app/**`, which was mid-edit
  while the gate ran. No compiled file is part of this delta.

**An earlier full-suite run of this change was red in 13 `run#` cases** while the host had `free-mib=913` against
the loop's `min-free-mib=1024` — the run's own floor refusing to start, eight occurrences in `suite-rerun.log`,
which is that run's transcript. The later full runs of the same selection are green above.

## The new records, red then green

`mutations-72.log`:

```
$ bash tools/loop-mutations.sh --only '72.1 72.2 72.3 72.4 72.5 72.6 72.7 72.8 73.1'
loop-mutations: selected 9 of 217 records (labels: 72.1 72.2 72.3 72.4 72.5 72.6 72.7 72.8 73.1)
RED    72.1  report#trendFiguresFromState   …
…
red-when-mutated: 9 · not-noticed: 0 · restore-failures: 0
```

`72.1` is round 1's finding as a mutation and `73.1` is round 2's: the latter fails with
`the cut round was read as a refusal and bought a rework worker`.

## The figure

`live-state.log` — the report over the tree's own state (`.pi/issue-loop/run.json`, written before the field
existed):

```
trend — this run's own items; no rate is green or red in one run:
  review: - · this state records no review verdict — it was written before the report read one
  wall:   median 174 s (min 25, max 2679) · 30 items
  landings: not in the run's state — no record says which commit a landing merged, so a main it reddened is the tracker's answer: gh issue list --label broken-main
```

`count-numerator.log` — the eight-item fixture of `report#trendFiguresFromState`, as shipped and with `72.1`
applied:

```
  review: 2/5 first rounds passed · 1 undecided · 2 never reviewed
  review: 5/5 first rounds passed · 1 undecided · 2 never reviewed
```

which is what makes the case's `assert_not_contains "5/5"` a real assertion rather than a dead one.

The item's 2026-10-21 figures (`median 403 s over 9 items`, `29 of 40 round 1s refused`, `two landings reddened
main`) are quoted from the issue body, not re-measured here.

## The tree this change shares

- `another-writer.log` — `git diff HEAD --stat -- app/ build.gradle.kts` → `11 files changed, 263 insertions(+),
  20 deletions(-)`, and `git status --porcelain | grep -c '^ M app/'` → `10`. Another session's work, in flight,
  disjoint from every path this change touches.
- `submodule-status.log` — the submodule's status **as captured during this session** (13 modified files, two
  untracked, a `client-java-avoid-tolls/` change directory). It is clean as this file is written, which is why the
  capture carries its own timestamp rather than a claim about now.
- `head-moved.log` — `git diff --name-only 357a4670..HEAD` → two paths, neither of them this change's
  (`app/src/main/cpp/libosmscout`, `tools/feature-list/specs.json`).

## The spec and the documents

- `openspec-validate.log` — `Change 'run-report-trend-figures' is valid` (`--strict`); the requirement body is 458
  characters, six scenarios, each naming its case.
- `measure.txt` — `bash tools/loop/measure.sh --base 38b34946 --change run-report-trend-figures`: `scenarios=6
  cases=6 unnamed=0`.
- `evidence-check.log` — `evidence-check: PASS`.
- `issue-709.log` — `gh issue view 709 --json …`: the follow-up item this change files for the landing
  attribution, `OPEN`.
