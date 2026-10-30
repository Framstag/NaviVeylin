# Design — fix-landing-commit-is-scored

## Root cause

`tools/loop/state.sh:432`–`:434` accepts a landing's branch, its commit count and its evidence and nothing
else, and the record they build (`:492`) has no field for a commit. The run holds the commit anyway: the merge
function re-reads the base branch's tip into `base` (`tools/loop/run.sh:286`) and both `done` calls that record a
landing (`:252`, `:1928`) pass only the three facts the schema has. Without a commit, no landing can be attached
to the pipeline run it caused, so `tools/loop/report.sh:250` prints a pointer to the tracker — which
`tools/sensors/conditions/broken-main.sh:24`–`:32` cannot answer by landing, because its identity is `{job, case}`.

## The single fix

One field, one read, one line.

1. **The landing records its commit.** `state.sh done` gains `--merged-sha`, stored at `.merged.sha` and refused
   unless it is 7–40 hex characters — a value that is not a commit would make the score read a run that belongs
   to nobody. `run.sh` passes the `base` its merge function already re-read: that variable *is* the landing
   commit (the merge is a fast-forward or a pick onto the base branch, and `run.sh:286` reads the base branch's
   tip after it).
2. **A run scores it, once per landing.** A new `state.sh score-landings` selects every item whose
   `.merged.sha` is set and whose `.landedReddenedMain` is absent, takes one read per item —
   `gh run list --commit <sha> --limit 20 --json databaseId,headSha,event,status,conclusion` — and writes
   `true` when the push run it finds did not succeed, `false` when it did. `run.sh` calls it after each landing,
   best-effort: the call cannot fail an item, and a read that did not happen leaves the item unscored with its
   reason printed.
3. **The report prints the stored score.** `tools/loop/report.sh:250` becomes a `jq` projection over `.items`:
   reddened against scored, then the unscored and the commitless apart — the same shape the wall and review lines
   already use, and the same rule that a figure nobody measured prints as absent with its reason.

## Alternatives the evidence eliminated

- **The report reads the tracker** (`gh issue list --label broken-main`). Eliminated before this change and
  again here: `report#landingsAreTheStoredNumber` keeps the case's recorded assertion that the report makes no
  `gh` call (its `PATH` carries a `gh` that fails and logs), and `broken-main`'s identity is `{job, case}`
  (`tools/sensors/conditions/broken-main.sh:24`–`:32`), so a tracker count still could not say which landing
  reddened the base.
- **Score at the next run's start, not at the landing.** Eliminated by the state's own lifecycle: `state.sh init`
  *replaces* the state (`state.sh` header, "the state it replaces is kept"), so a new run's state holds no landed
  item to score; scoring the previous run's archived state would write into a file the report of that run reads
  only if a person passes `--state`, and would put the read back where the item rejected it — a pass over the
  run's own items, not one per landing.
- **Take the newest run for a sha.** Eliminated by the probe (`evidence/probe-gh-run-list.log`): one headSha
  carries a scheduled `Sensors` run beside the landing's `push` run, so "newest" can name a schedule tick.
  The score reads the `push` run.
- **`gh run list --branch <base> --commit <sha>`.** Eliminated by the same probe: the commit answers alone
  (`[]` for a commit no run exists for), and the branch name is a configured value the engine may not carry
  (invariant I13, `guidelines/Backlog.md` §4), so the second filter would cost a project key for nothing.
- **Parse the short sha out of `worktree.sh`'s landing line.** Eliminated by the line itself: it prints
  `--short HEAD` (`tools/loop/worktree.sh:494`) while `run.sh` already holds the full commit in `base`; taking
  the short one would need the field to accept a sha the tracker's `--commit` matches only by prefix.

## Blast radius

Three engine scripts, one case file, one mutation record, one new fixture, one spec requirement. No compiled
file. The state schema gains one nullable field inside `.merged` and one nullable top-level field per item, so a
state written before this change reads as *no landing records a commit* — the report says so on its own line and
nothing is backfilled. The three trend cases that slice the report up to the `  landings:` prefix keep working,
because the prefix does not move. `run.sh` grows one network call per landing: `gh` from the host's `PATH`, no
new project key, and its failure is recorded, not raised.

## Rollback

`git revert` of the one commit: the two `done` flags and the second `score-landings` call go, `.merged.sha` and
`.landedReddenedMain` are simply never written again, and the report line returns to the pointer it printed
before. Nothing reads the new fields except the report, so no state file needs rewriting either way.

## Verification

- The red case fails on HEAD: `state#doneRecordsTheCommitItLanded` cannot record a commit because the flag does
  not exist, and `state#theLandingScoreIsReadOnceAndStored` cannot score because the command does not exist.
- The suite's own cases hold the shape of the report line and the two-read prohibition; the mutation record that
  names the rewritten report case still catches a report that reads the tracker.
- The gate is `./tools/loop/gate.sh`, which for `tools/loop/**`, `tools/loop-cases.sh` and `tools/loop-mutations.sh`
  owes `tools/loop-selftest.sh --from-git <base>`, `bash tools/loop-mutations.sh --check` and the portability
  checks (`tools/loop/loop.conf`, `LOOP_GATE_SUITES`).
