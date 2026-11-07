# Proposal — a run ended by a signal records the signal as its own stop, and HUP is trapped beside INT and TERM

## Why

**The entry, verified in the tree (issue #755).** Three facts, one mechanism:

- `tools/loop/run.sh:588`–`591` — `on_signal() { interrupted=1; exit "$1"; }`, and the only traps are
  `EXIT`, `INT` (130) and `TERM` (143). `HUP` is not among them, so the commonest way a person ends a run — the
  terminal or the harness goes away — kills the shell with no cleanup at all.
- `tools/loop/run.sh:579`–`584` — the `EXIT` trap's interrupted branch unpicks the item's in-progress label and
  records the *item* `blocked` with the note "interrupted by a signal"; it never calls `loop_state stop`. The only
  caller of that command is `stop_run` (`tools/loop/run.sh:606`–`609`), which every path that ends a run at one of
  its refusals calls — so a signalled run is the one end nothing writes a reason for.
- `tools/loop/state.sh:445`–`452` — `cmd_stop` is what writes `.run.stop`; `tools/loop/state.sh:976`–`995` —
  `unlock` removes the lock directory and nothing else. So a run whose `EXIT` trap never runs leaves the item's
  `agent/in-progress` label standing, which is the one label no script clears, and the next pre-screen excludes the
  item as "another change already holds it".
- `tools/loop/report.sh:137`–`139` prints what the empty record forces it to print — "not stopped — the run is
  still going, or it ended without recording a reason" — and `tools/loop/state.sh:263`–`268` prints the same fact as
  "the run that wrote this state is gone — its process (pid N) is not running and it recorded no reason".

Not already fixed: `bash tools/look.sh 'on_signal'` still lists the three traps and no `HUP`;
`bash tools/look.sh 'interrupted by a signal'` returns `tools/loop/run.sh` alone, and the branch it names calls no
stop.

**The measurement the item carries, re-taken in the tree today (2026-11-07T07:35Z).** Over the runs this repository
archived, the record is still mostly absent: of 105 archived runs, 50 record no stop, and 18 of those had worked
items — a third of the loop's own history has no cause, and every statistic above it is computed over the rest:

```
$ jq -s 'length, (map(select((.run.stop.reason // "") == "")) | length),
         (map(select((.run.stop.reason // "") == "" and ((.items // []) | length) > 0)) | length)' \
     .pi/issue-loop/runs/*.json
105
50
18
```

**Root cause, one sentence.** The signal path ends the run without writing the run's own end — the interrupted
branch of the `EXIT` trap records the item and not the run — and it is reached by `INT` and `TERM` only, so a run
whose terminal goes away takes no cleanup path at all.

**The red test.** `run#aHupEndedRunRecordsItsStopAndUnpicksItsItem` (new) and
`run#interruptedRunEndsTheChildItStarted` (one assertion more): on the base with those two cases alone in the tree,
`sent HUP`/`TERM` leaves `run.stop` empty — `'SIGHUP' not found in:` / `'SIGTERM' not found in:`,
`loop-selftest: 1 of 1 cases failed` for each, `tests=2 failures=2` over the pair
(`evidence/red-on-HEAD.log`, 2026-11-07T07:32:43Z–07:32:53Z). The declaration case is green on the same tree
(`driver#everyCaseDeclaresTheFilesItCanReach`, PASS at 07:32:43Z), so the new case's `# files:` line is not what is
being measured.

**The owning requirement and guideline section.** Capability `agent-issue-loop`, requirement "An interrupted run
ends the child it started" (`openspec/specs/agent-issue-loop/spec.md:1130`) — the requirement whose case the item
names and whose subject is what a signalled run must finish before it exits; the record itself is owned by
`guidelines/Backlog.recipes.md` §7d "Refusals, records and the landing" (:674), the section that owns what the run
records when it ends.

**Surface, counted before the plan was fixed.**
`grep -c "^  'run#" tools/loop-cases.sh` → `129` (`128` on the base, so **1 case added**), and the case the item
names takes **1 assertion more** (**1 case rewritten**); no fixture is realigned
(`bash tools/look.sh 'on_signal|interrupted by a signal'` names `tools/loop/run.sh` and `tools/loop-cases.sh` only):

surface: 1 case added, 1 rewritten, 0 fixtures realigned, 1 engine file + 1 case file + 1 mutation set + 1 spec delta + 1 guideline section — `grep -c "^  'run#" tools/loop-cases.sh`; `bash tools/look.sh 'on_signal|interrupted by a signal'`

## What changes

1. `tools/loop/run.sh` traps `HUP` beside `INT` and `TERM`, and the trap names the signal it took, so the record
   below can say which one ended the run rather than an exit code a reader has to decode.
2. `tools/loop/run.sh`'s interrupted branch records the run's own end — `loop_state stop "interrupted by SIG<NAME>"`
   — before the label comes off and before the lock is released, so `run.stop` is set on a signal-ended run and
   the report prints the signal instead of "it recorded no reason".
3. `tools/loop-cases.sh` — one new case (`run#aHupEndedRunRecordsItsStopAndUnpicksItsItem`) and one assertion added
   to `run#interruptedRunEndsTheChildItStarted` beside a wait for the run's own end, so both the `HUP` path and the
   `TERM` path are held to the record and read it after the trap finished writing it.
4. `guidelines/Backlog.recipes.md` §7d states the rule the engine now enforces: a run a signal it traps ended
   records that signal as its stop, and `HUP` is one of the signals it traps.
5. `tools/loop-mutations.sh` — record 9.4 is realigned to the `loop_labels unpick` line the restructure re-indents, and
   9.13 names the stop write the trap now makes, so deleting that line is what the case notices.

## What this does not change

- The item's own record on a signal — `blocked` with the note "interrupted by a signal" — is exactly what it was;
  the case that asserts it (`labels#trapRemovesInProgressOnSignal`) is untouched.
- The traps' other work: ending the child's process group (`loop_end_group`) and the heartbeat (`loop_end_pid`) is
  unchanged, and so is the order that runs them before the item's outcome is written.
- The pre-screen's repair of an in-progress mark an already-dead run left behind is a *second* mechanism, owned by
  the queue and not by the signal path: it repairs state the runs before this change left, and no signal-ended run
  after it leaves such a mark. It is not this change's, and the case added here reads the field
  (`.run.ownerPid`) that repair would read, so its absence is visible.
- Every refusal that already records its own stop (`stop_run`) is untouched, and `state.sh`'s vocabulary, `report.sh`
  and the `unlock` verb are unchanged.
