# Evidence — fix-gate-step-ends-with-its-command

Base measured against: `6e8b28892ea19d52a322b2c4c155a7d4d2ef4ed3` (the run's base).

## The measure block

`./tools/loop/measure.sh`, from the change root (`measure.log`), before the change's pathspec was complete:

```
LOOP-MEASURE-PROFILE /home/tim/projects/NaviVeylin/.pi/worktrees/loop/tools/loop/loop.conf
LOOP-MEASURE base=6e8b28892ea19d52a322b2c4c155a7d4d2ef4ed3 paths=0 insertions=0 deletions=0
LOOP-MEASURE-CHECK LOOP-GATE: nothing owed — no path this profile gates changed since 6e8b28892ea19d52a322b2c4c155a7d4d2ef4ed3
LOOP-MEASURE-SUITE cases=232 cmd=tools/loop-selftest.sh --list
LOOP-MEASURE-GATE seconds=- reason=no tally under the gate's directory carries a time — the gate of this tree has not run
LOOP-MEASURE-CHANGE 2026-10-26-fix-android-feature-set-follows-features-cmake scenarios=5 cases=0 unnamed=5
LOOP-MEASURE-SURFACE none — the proposal carries no surface: line
LOOP-MEASURE-END
```

…and again once the delta's paths were in place and the case was added (`measure.log`, the same file):

```
LOOP-MEASURE base=6e8b28892ea19d52a322b2c4c155a7d4d2ef4ed3 paths=3 insertions=129 deletions=19
LOOP-MEASURE-PATH tools/loop-cases.sh
LOOP-MEASURE-PATH tools/loop/gate.sh
LOOP-MEASURE-PATH tools/loop-mutations.sh
LOOP-MEASURE-SUITE cases=233 cmd=tools/loop-selftest.sh --list
LOOP-MEASURE-CHANGE 2026-10-26-fix-gate-step-ends-with-its-command scenarios=3 cases=3 unnamed=0
LOOP-MEASURE-SURFACE surface: 1 case added, 0 rewritten, 3 neighbouring cases re-run green, 1 mutation record rewritten + 1 added, 1
LOOP-MEASURE-END
```

The second block is the one quoted: the first was taken before the change existed (it is kept because it is what the
tree owed then — `paths=0`). The open change's directory is gitignored (`openspec/changes/*/`), so the artifacts
under it add no path to the pathspec; the three engine files are the whole delta.

## Phase A — the condition, the surface, the red case

- The condition holds on the base: `tools/loop/gate.sh:166` is `$cmd 2>&1 | tee "$stream"` and `:170` is
  `took=$((SECONDS - t0))` — the step's clock is taken after a pipeline that ends at the pipe's EOF, not at the
  command's exit (`git show 6e8b2889:tools/loop/gate.sh | grep -n 'tee "\$stream"\|PIPESTATUS'`, `surface-head.log`).
- Surface on the base (`surface-head.log`): one engine site (2 pipeline lines), 4 cases that read the stream or the
  clock, 2 mutation records, 1 requirement. The case set is the loop's own suite, whose declared count is
  `LOOP-MEASURE-SUITE cases=233` after the new case.
- Red test on the base (`red-on-head.log`, tally `red-on-head.xml`):

```
$ bash tools/loop-selftest.sh --case gate#aStepEndsWithItsCommandNotWithItsWatchers
  the step ran on after its command exited: still inside the gate 8s into a 20s writer that command left behind
FAIL gate#aStepEndsWithItsCommandNotWithItsWatchers
loop-selftest: 1 of 1 cases failed
```

The case is a throwaway project whose suite prints `notes: all 1 cases passed`, leaves `( sleep 20 ) &` behind and
exits: the gate must return inside 8 s, its `gate: … — Ns` line must carry a number below 8, and the console must
carry the suite's own line.

## Phase D — the gate

Two runs, and the difference between them is recorded because it is what the change is about.

**Run 1** (`gate-round1-red.log`) — `./tools/loop/gate.sh` from the change root, first attempt:

```
LOOP-GATE: tests=589 failures=1 errors=0 (tools/loop-selftest.sh --from-git 6e8b2889… 391s, …) — failing: tools/loop-selftest.sh --from-git 6e8b2889…
```

One case red: `gate#aFailingSuiteIsReadFromTheSameStream` — a case the change did not touch, and one of the three
that read the old observable. Its assertion is that the console carries a failing suite's own line; the follower
the change introduced ended on its first pass when the stop file was already there, so a suite that finished
inside one 0.1 s poll wrote nothing to the console. The fix is the drain in `follow_stream` (`tools/loop/gate.sh:65`):
a pass that printed bytes continues, and the follower ends only on a pass that found nothing *after* the stop
exists. Verified with five consecutive runs of that case and three of the two others (`green-cases.log`) — the
defect was a race, so one green run would not have said so.

**Run 2** (`gate.log`) — the same command over the corrected tree, exit 0:

```
LOOP-GATE: tests=589 failures=0 errors=0 (tools/loop-selftest.sh --from-git 6e8b2889… 374s, bash tools/loop-mutations.sh --check 2s, tools/loop-mutations-selftest.sh 235s, tools/check-portability.sh 1s, tools/check-portability-selftest.sh 23s, tools/mutate-selftest.sh 0s, tools/mutate.sh --mutations tools/portability-mutations.json --list 0s, tools/issue-tooling-selftest.sh 59s, tools/mutate.sh --mutations tools/{status,create,close,duplicates,decision-question}-mutations.json --list 0s)
```

Every tally is in this directory, copied from the profile's tally directory (`app/build/test-results/loop-gate/`); the
aggregate is the gate's own line because no single tally carries the sum, and it is carried as
`gate-summary.xml` (and, for round 1, `gate-round1-summary.xml`) — each derived from that run's own summary line the
way the gate derives a per-suite tally from a suite's summary, which is why they carry no failure text of their own:

- `tools_loop-selftest.sh_--from-git_6e8b28892ea19d52a322b2c4c155a7d4d2ef4ed3.xml` — `tests="233" failures="0"
  errors="0" time="374"`, the loop's own case set (`tools/loop/**` is a core file set, so the whole set is owed: no
  file-set narrowing) — the declared count is `LOOP-MEASURE-SUITE cases=233`.
- `bash_tools_loop-mutations.sh_--check.xml` — `tests="172" failures="0"`, every record applies to the file it names,
  `63.1` and `46.1` included.
- `tools_loop-mutations-selftest.sh.xml` — `tests="34" failures="0" time="235"`.
- `tools_check-portability-selftest.sh.xml` — `tests="29" failures="0"`; `tools_check-portability.sh.xml` —
  `tests="0" failures="0"` (its own summary line is `ok …`/a count of value sets, which the gate's tally writer does
  not read as cases — the same shape `2026-10-25-fix-retired-loop-skill-still-shipped` recorded).
- `tools_issue-tooling-selftest.sh.xml` — `tests="121" failures="0"`.
- `tools_mutate-selftest.sh.xml` and the five `tools_mutate.sh --mutations … --list.xml` — `tests="0" failures="0"`:
  a `--list` prints a set and reports no case.

**The attempt that did not complete, and why it is named here** (`gate.log` was truncated by it, and its suite was
left running): the command was started once through a shell alias whose call ends after about 100 s of silence. That
ended the *gate*, not the run's configured budget (`LOOP_CHILD_MAX_SECONDS=2700`), and it left the suite it had
started running with its per-case watchers; the tree was unchanged by it (`git status` still showed the three files),
the suite and its watchers were ended by PID, and the run above is the one quoted. No result was taken from the ended
attempt.

What the numbers say about the item: the selftest step's own line is `— 374s` and the suite's summary is in the same
step's stream, where the item measured `— 1237s` against a verdict at 565 s. The step's clock is now the suite's.

## Round 2 — the mutation record's escaping (its round-1 review)

The round-1 review refused the change on one blocking finding: `63.1`'s replacement carried three **unescaped** `&`
(`2>&1` twice, the trailing `&`), and the harness applies a record with `sed -i "$expr"` — where an unescaped `&`
in the replacement expands to *the whole match*. The record therefore rewrote `gate.sh`'s step line into a corrupted
one instead of restoring `$cmd 2>&1 | tee "$stream" &`, so the observed `RED 63.1` (`loop-mutations-315.log`) was a
failure for another reason, and that log quotes no failure text at all.

The correction is the record's replacement alone (`tools/loop-mutations.sh:536`), its three ampersands escaped the
way every record that needs a literal one writes them (`9.3`, `9.9`, `12.2`, `34.4`, `43.3`), plus a sentence in the
record's comment saying why. No case, assertion, scenario or engine line moved.

What the corrected record does to the step line, applied to a throwaway copy of `gate.sh` with the record's own
expression (`evidence/loop-mutations-check-round2.log` is the whole set's check; the diff is one line):

```
191c191
<   $cmd >"$stream" 2>&1 &
---
>   $cmd 2>&1 | tee "$stream" &
```

Verification, both from the change root:

- `bash tools/loop-mutations.sh --check` → `loop-mutations: all 172 cases passed — every selected record applies to
the file it names` (`evidence/loop-mutations-check-round2.log`).
- `bash tools/loop-mutations.sh --only '63.1'` → `RED 63.1 gate#aStepEndsWithItsCommandNotWithItsWatchers`, `restore
  63.1 … green again`, `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0`
  (`evidence/loop-mutations-63-1-round2.log`); the tree's step line is back to `  $cmd >"$stream" 2>&1 &`.

The harness captures the case's output to `/dev/null`, so the reason of that `RED` is taken separately, from the same
mutation applied to a private throwaway copy of the engine (`tools/loop` copied with `tools/issues` beside it, the
copy removed after the run) and the case run against it through `LOOP_ENGINE`:

```
$ LOOP_ENGINE=<engine copy with 63.1 applied> bash tools/loop-selftest.sh --case gate#aStepEndsWithItsCommandNotWithItsWatchers
  the step ran on after its command exited: still inside the gate 8s into a 20s writer that command left behind
FAIL gate#aStepEndsWithItsCommandNotWithItsWatchers
loop-selftest: 1 of 1 cases failed
```

(`evidence/red-mutated-63-1-round2.log`.) That is the invariant's own red — the pipe holding the step open on a writer
that outlived the command — and it is the line the round-1 record could not produce.

No gate was re-run for this round: the delta touches no compiled file, and the two commands above are the record
check's own step.

## Phase F — pointers, archive

- `bash tools/loop/evidence-check.sh openspec/changes/2026-10-26-fix-gate-step-ends-with-its-command --root .` —
  `evidence-check.log` (run before archive; the archived path is checked again below).
- `openspec archive` moved the change to `openspec/changes/archive/2026-10-26-fix-gate-step-ends-with-its-command/` and
  the delta merged into `openspec/specs/agent-issue-loop/spec.md` (the requirement now carries the step's end and
  three scenarios).
- The red tally `red-on-head.xml` is in this directory beside the gate's: it is the gate's own derivation from the
  suite's summary line (`loop-selftest: 1 of 1 cases failed`), copied in by hand because the red run was taken
  outside the gate.

