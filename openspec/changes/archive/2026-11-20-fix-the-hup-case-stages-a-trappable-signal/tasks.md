# Tasks

The line numbers below are the tree's as this change leaves it; the base is
`fcdc7ea875ccd6c203cc2ebbf2f60ec313430229`. The change edits `tools/loop/tests/loop-cases.sh` and
`guidelines/Backlog.recipes.md` and touches no engine script and no compiled file. Every run is kept in the run's
evidence directory, `.pi/issue-loop/items/978/`, and copied into this change's own `evidence/`.

## 1. The red case, before the fix

- [x] 1.1 `run#aHupEndedRunRecordsItsStopAndUnpicksItsItem`, as this change writes it, reds on HEAD under the
      disposition the gate hands its steps — a suite started detached, `SIGHUP` ignored — and reds at the disposition
      the run was started with rather than at the wait: `FAIL … a run that took HUP did not end inside 300 ticks (30 s
      for each of the 1 case(s) the pool starts beside this one; status 124) — its stop was '' and its item ''`, exit 1
      (`evidence/red-on-HEAD-nohup-case.log`; produced by `evidence/hup-ignored-suite.sh --case
      'run#aHupEndedRunRecordsItsStopAndUnpicksItsItem' --jobs 1`, which does `trap '' HUP` before its `exec`,
      2026-11-20T02:27:01Z, 36 s elapsed — `evidence/red-on-HEAD-nohup-case-timed.txt`; transcribed as
      `evidence/red-on-HEAD-nohup-case.xml`, `tests=1 failures=1 errors=0`, `time="36"`). The figure is the pool's
      width at one case, not a duration this host measured, and the empty stop and item are the gate's own reading at
      4800 ticks.
- [x] 1.2 The falsification that separates the disposition from the wait: with the reset removed and the assertion
      left in place, the same case reds **at the staging assertion** — the deadline's own message is absent from the
      log and the disposition's is in it — `FAIL … the run's shell (pid 1196870) ignores SIGHUP, so the trap this case
      sends its signal to cannot be installed in it (issue #978)` (`evidence/revert-check-without-the-reset.log`; the
      log carries no timestamp of its own, and its mtime is the 2026-11-20T01:32:20Z run, exit 1).
- [x] 1.3 The disposition is a fact about `SIGHUP` alone, and the neighbour cases say so: under the same `nohup`
      suite `run#interruptedRunEndsTheChildItStarted` (`TERM`) and
      `run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock` pass unchanged
      (`evidence/check-term-case-nohup.log`, `evidence/check-hup-cases-nohup.log` — the latter reads
      `loop-selftest: all 2 cases passed`). No second call site of `run_loop` is touched.

## 2. The reset is staged where the run is started — `tools/loop/tests/loop-cases.sh`

- [x] 2.1 `run_loop_resetting_hup` (`loop-cases.sh:1534-1539`) is `run_loop` with the one caller-side lever a shell
      has for a disposition: `env --default-signal=HUP bash "$LOOP_ENGINE/run.sh" "$@"`. It is a variant, not a
      change to `run_loop`: `tools/look.sh 'run_loop "\$p"' --path tools/loop/tests/loop-cases.sh --files` reads 169
      (`evidence/surface-run-loop-callsites.log`), and every one of them still starts its run as it did. The lever is
      a GNU coreutils one — `env --default-signal`, which this host's coreutils 9.12 has; a host whose `env` lacks
      the flag cannot start the run this way at all. Verify: the green pair of 2.2 and 2.3 — `PASS` in both
      (`evidence/green-on-fix-nohup-case.log`, `evidence/green-on-fix-focused-case.log`).
- [x] 2.2 The case starts its run through it (`loop-cases.sh:16508`), and the comment above it
      (`loop-cases.sh:16500-16507`) names the disposition as a stage of its own and why the reset lives there rather
      than in `run.sh`. Verify: the sentence the change replaced no longer stands —
      `tools/look.sh 'the write order is right and says nothing about the disposition' --path tools/loop/tests/loop-cases.sh`
      → no match (`evidence/check-premise-removed.log`: "LOOK: 0 matches in 0 files"), and the HUP case passes under
      `nohup` (2.1's first log).
- [x] 2.3 The case asserts the staging before it signals (`loop-cases.sh:16531-16543`): it reads `SigIgn` from the
      run's own `/proc/<pid>/status` and fails naming `SIGHUP` when bit 0 is set, and a host without `/proc` skips
      the assertion instead of guessing — the branch is `if [ -r "/proc/$owner/status" ]; then` (`loop-cases.sh:16535`),
      so on a host without `/proc` the assertion is not read at all. Verify: 1.2's red is that assertion firing, and
      2.1's green pair passes with it in place.
- [x] 2.4 The case's `# files:` declaration (`loop-cases.sh:16484`) names `tools/loop/prompts/worker-gate.md`, the
      prompt its new comment reaches and the one that states the detached start. Verify:
      `tools/loop/tests/loop-selftest.sh --case driver#everyCaseDeclaresTheFilesItCanReach` → `PASS`
      (`evidence/check-declaration-case.log`) — it is the first line this delta owes, and it is run before the
      neighbours.

## 3. The guideline that owns this case's staging — `guidelines/Backlog.recipes.md` section 7b

- [x] 3.1 The paragraph that owns the case's clock and staging, "The clock a case keeps is the pool's width, not a
      duration the host measured" (`guidelines/Backlog.recipes.md:869`), gains the third stage: the disposition, the
      two `SigIgn` readings, why the reset cannot live in `run.sh`, and the assertion that reds in its place
      (`guidelines/Backlog.recipes.md:903-921`). Verify: the paragraph carries all five facts, and no other section
      of the file states this case's staging — `tools/look.sh 'The signal.s disposition is the third thing' --path
      guidelines/Backlog.recipes.md` → one match, in section 7b (`evidence/check-guideline-paragraph.log`).

## 4. What the change leaves to the gate child

- [x] 4.1 Nothing outside the change directory is written after the gate starts, so this delta's own record — the
      logs of 1 and 2 copied into `evidence/`, `bash tools/loop/evidence-check.sh openspec/changes/fix-the-hup-case-stages-a-trappable-signal --root .`
      reporting `PASS`, and `openspec archive --yes` moving the change under `openspec/changes/archive/` — is the
      gate child's phase E, before its gate, not a step of this session (issue #837).

## 5. The corrections of round 2 — the findings the reviewer wrote

The round-1 review refused the change on one blocking finding and seven notes. Every one of them is a correction of
the change's own words or of a number it quotes; none asks for a new claim, a new case or a new assertion, and the
mechanism, the red→green differential and the gate's tally are untouched.

- [x] 5.1 **The measurement the guideline and the change's artifacts misstated** — the blocking finding: the
      change's own `evidence/dispositions.log`, section `== nohup`, prints `SigIgn: 0000000000000005` and then
      `DONE`, with no `TRAPPED` line — an ignored `SIGHUP` changes nothing and the shell lives on. The guideline
      (`guidelines/Backlog.recipes.md` section 7b), `proposal.md` and `design.md` said the opposite ("ends it having
      run no trap — neither the probe's `TRAPPED` nor its `DONE` line is printed"). All three now read, from that log,
      that no trap ran and the `DONE` line was printed.
- [x] 5.2 The figures `tasks.md` quoted for `evidence/revert-check-without-the-reset.log` — pid `1036087` and
      `2026-11-20T01:26:17Z` — are not the archived ones: the log carries pid `1196870` and no timestamp of its own
      (its mtime is the 2026-11-20T01:32:20Z run). `tasks.md` 1.2, `proposal.md` and `design.md` quote the archived
      figures now.
- [x] 5.3 The script that produced the red and the one time it was produced are named: `evidence/hup-ignored-suite.sh`
      (the suite script that does `trap '' HUP` before its `exec`), 2026-11-20T02:27:01Z — `tasks.md` 1.1 and
      `proposal.md`.
- [x] 5.4 `evidence/red-on-HEAD-nohup-case.xml` carried `time="0.00"` for a run that waited 300 ticks, and no command
      had produced that duration. The red was run again with the clock around the one command that produces the log —
      36 s elapsed, and the log that run produced is byte-identical to the one beside the XML
      (`evidence/red-on-HEAD-nohup-case-timed.txt`); the XML carries that measurement.
- [x] 5.5 `guidelines/BuildGate.md` section 2b already carries the same fact for the case's own start ("a case that
      sends `SIGHUP` is started with a plain `&`, never `nohup`"); section 7b now cross-references it and says which
      start each of the two rules covers.
- [x] 5.6 The new section 7b paragraph was glued to the one before it — no blank line between them, so it rendered as
      part of it. A blank line stands there now.
- [x] 5.7 `env --default-signal=HUP` is a GNU coreutils flag. The requirement is stated where the flag is used —
      `guidelines/Backlog.recipes.md` section 7b, `proposal.md`, `design.md` and `tasks.md` 2.1 — with the host's own
      version (coreutils 9.12) and what a host without the flag meets: it cannot start the run that way at all.
- [x] 5.8 The item asked for a measurement this change did not take — the pid the case sent the signal to beside the
      pid the state names at that instant, and the case's `WAIT_STOP_SEEN`/`WAIT_ITEM_SEEN` per tick. `proposal.md` and
      `design.md` say so, and name what the two halves rest on instead: the construction (a fresh project per case,
      `run.ownerPid` written once, after the traps) and the red→green differential.
- [x] 5.9 The surface totals the proposal quoted — "181 matches in 137 files" and the `grep -c` of 169 — rested on no
      archived output. They are replaced by the counts three `tools/look.sh` calls produced, each archived beside this
      change (`evidence/surface-case-name-tools.log`, `evidence/surface-case-name-guidelines.log`,
      `evidence/surface-case-name-specs.log`, `evidence/surface-run-loop-callsites.log`), and `tasks.md` 2.1 names the
      `look.sh` call instead of a `grep`.
- [x] 5.10 The checks `tasks.md` 2.2 and 3.1 name were not re-run and archived; they are now
      (`evidence/check-premise-removed.log`, `evidence/check-guideline-paragraph.log`), and `tasks.md` 2.3 quotes the
      line that skips the assertion on a host without `/proc` (`loop-cases.sh:16535`).
