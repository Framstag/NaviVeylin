# Tasks

The change's own line numbers are the tree's as this change leaves it; the base's are `5db58389`. The red run's tally
is `evidence/red-on-HEAD.xml`, its log `evidence/red-on-HEAD.log`.

## 1. The red cases

- [x] 1.1 Added `report#aTurnNoSessionRecordReachesIsItsOwnBucket` and
      `report#thePhaseLineNamesTheClockItIsAShareOf` to `tools/loop/tests/loop-cases.sh` (appended after
      `case_report_aRoleWhoseTurnNamedNoModelKeepsItsRow`, registered in the case list and dispatched), each with its
      first-line `# files:` declaration. Verify: against the base's readers — `state.sh` and `report.sh` as `5db58389`
      has them, byte-compared in the log — `bash tools/loop/tests/loop-selftest.sh --case
      'report#aTurnNoSessionRecordReachesIsItsOwnBucket'` → `the turns no session record reaches are a bucket of their
      own: expected '2000000', got 'null'` and `… --case 'report#thePhaseLineNamesTheClockItIsAShareOf'` → the line
      prints `1.3 s over 1 item` with no denominator (2026-11-11T08:44:41Z; `evidence/red-on-HEAD.log`,
      `evidence/red-on-HEAD.xml`, `tests=2 failures=2 errors=0`).
- [x] 1.2 The declarations' own check is accepted before those runs:
      `bash tools/loop/tests/loop-selftest.sh --case 'driver#everyCaseDeclaresTheFilesItCanReach'` → `PASS`
      (`evidence/gate-run.log` records the same case green in this change's one gate).

## 2. The engine — `tools/loop/state.sh`

- [x] 2.1 The `sessions` reducer opens with a region of its own (`orphan:true`) instead of `cur:null`, so a `turn_end`
      before the stream's first `session` record is collected as a turn inside a session is, and a region that
      collected no turn is kept by neither. Verify: the red case's `roles.unattributed` reads `2000000` and
      `metrics.tokens.cacheRead` reads `3000000` — the bucket and the one session's role are the total.
- [x] 2.2 The `roles` reduction maps an orphan region to the bucket `unattributed` and adds it to the object's seed,
      `null` where no turn stood there. Verify: `report#aStreamWithoutDurationsPrintsDash` still reads `.metrics.roles`
      as `null`, and `report#theSpendIsSplitByPhaseRoleAndGate` still reads its three roles as the same numbers.
- [x] 2.3 The same mapping on the `roleModels` pass, and `unattributed` in its seed: an orphan turn's model stays
      keyed where its tokens are counted, so the model rows and the bucket cannot print one tally twice. Verify: the red
      case reads `.metrics.roleModels.unattributed["mimo-v2.6-flash"]` as `2000000`, and on item 738's own stream the
      trend prints `reviewer (minimax-m3.1-flash) 3.7 Mtok · unattributed (mimo-v2.6-flash) 2.3 Mtok · 6 Mtok over 1
      item` against a recorded 5 988 239 tokens (`evidence/real-stream-738.log`).
- [x] 2.4 `afterGate` stays over the sessions that ran a gate (`select(.orphan | not)`): an orphan region is no
      session and the split point it would give is unknown. Verify: `report#aCallThatNamesTheGateIsNotAGateRun` reads
      `afterGate.tokens` `3000000` and `null` exactly as on the base.

## 3. The report — `tools/loop/report.sh`

- [x] 3.1 The role order is the trio the line always printed followed by every key the record holds that the trio does
      not, and both arms of the line read it. Verify: the red case prints
      `worker (minimax-m3.1-flash) 1 Mtok · unattributed (mimo-v2.6-flash) 2 Mtok · 3 Mtok over 1 item`, and
      `report#aRoleWhoseTurnNamedNoModelKeepsItsRow` still prints
      `worker (mimo-v2.6-flash) 1 Mtok · reviewer 3 Mtok · 4 Mtok over 1 item`.
- [x] 3.2 The phase line prints the share of the item wall the three buckets are, and the time they leave outside a
      turn and a tool, summed over the items that recorded a phase figure. Verify: the red case reads
      `1.3 s over 1 item, 3 % of the 42 s of item wall — 40.7 s of it in neither a turn nor a tool`, and
      `report#theSpendIsSplitByPhaseRoleAndGate` still contains `model 7.5 s · gate 5 s · other 0.3 s · 12.8 s over
      1 item`.
- [x] 3.3 Where no item of that set recorded a wall the line says so instead of printing the total as the clock:
      `— no item wall recorded to read it against`. Verify: the red case's second half, and
      `report#aStreamWithoutDurationsPrintsDash` still prints its dash with no `0 s` beside it.

## 4. The mutation records — `tools/loop/tests/loop-mutations.sh`

- [x] 4.1 `832.1` mutates `state.sh` so the orphan region is sent back to the prompt the stream no longer carries (the
      bucket stays `null`), and `832.2` mutates `report.sh` so the phase line prints no denominator. Verify:
      `bash tools/loop/tests/loop-mutations.sh --check --files 'tools/loop/state.sh tools/loop/report.sh'` → `all 59
      cases passed — every selected record applies to the file it names`, and the two-record run reports `RED` with the
      case's own assertion then `restore … green again` (2026-11-11T08:58:19Z and 08:58:23Z,
      `red-when-mutated: 2 · not-noticed: 0 · restore-failures: 0`; `evidence/mutation-run.log`). The rest of the file
      set is the gate child's: the harness owes the whole set before a change that owns `tools/loop/**` is archived.

## 5. The guideline — `guidelines/Backlog.recipes.md` section 7b

- [x] 5.1 The paragraph at `:641`–`:658` that gained the bucket beside the
      three roles, the reason a turn can stand outside a session, and the phase line's denominator. Verify:
      `tools/look.sh 'unattributed' --path guidelines/Backlog.recipes.md --files` finds the paragraph.

## 6. Verification

- [x] 6.1 Both cases green on the change, with the sibling cases the rule is read beside:
      `report#theSpendIsSplitByPhaseRoleAndGate`, `report#aRoleWhoseTurnNamedNoModelKeepsItsRow`,
      `report#aRolesTokensRecordTheModelThatAnswered`, `report#aStateWithoutAModelKeepsItsRoleTotals`,
      `report#aStreamWithoutDurationsPrintsDash` and `report#aCallThatNamesTheGateIsNotAGateRun` — a whole file-set run,
      `bash tools/loop/tests/loop-selftest.sh --files 'tools/loop/state.sh tools/loop/report.sh'` → `all 190 cases
      passed … 212.76 s` (`evidence/green-on-change.log`).
- [x] 6.2 The structural check of the files this change edits — it compiles nothing: `bash -n tools/loop/state.sh`,
      `bash -n tools/loop/report.sh`, `bash -n tools/loop/tests/loop-cases.sh` and
      `bash -n tools/loop/tests/loop-mutations.sh`, all clean. No build is run for a
      delta of shell, one spec delta and a guideline paragraph (2026-11-11T08:58:35Z, `evidence/structural-checks.log`).

The gate is not this session's: the run has deferred it to the child that follows the diff review, and this change's
one gate is that child's whole self-test — 405 declared cases with this change's two (`evidence/measure-surface.txt`).

## 7. The correction round

- [x] 7.1 The reviewer's blocking finding is answered: the roles total the run file yields is **55 676 163** and the
      gap it derives is **17 544 309** (24 % unchanged), so the figures published in
      `guidelines/Backlog.recipes.md`, in this change's `proposal.md` and in the comment of
      `tools/loop/tests/loop-cases.sh` are corrected. Verify: `jq -r '[.items[].metrics.roles[]] | add'
      .pi/issue-loop/runs/2026-11-07T19:53:44Z.json` → `55676163`
      (`evidence/measure-run-totals.log`).
- [x] 7.2 The reviewer's notes are answered: the phase line's denominator is no longer claimed to be over the
      numerator's set (in `guidelines/Backlog.recipes.md`, in `tools/loop/report.sh`'s comment and in `design.md`), the
      guideline paragraph this task names is the one the change added, the design's byte-identical claim names the
      columns that move, `tools/loop/state.sh`'s comment no longer claims the orphan region's role is a fact the
      stream no longer holds, the structural check names the fourth file `evidence/structural-checks.log` checks, and
      the measurements that were named but carried by no artifact are carried now
      (`evidence/measure-run-totals.log`, `evidence/measure-stream-timestamps.log`) or dropped. The reviewer's own
      report is `evidence/review-round-1-requested.md`; what it asked for and what this round changed is
      `evidence/corrections-round-2.md`.
