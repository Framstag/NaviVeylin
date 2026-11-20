# Tasks

The line numbers below are the tree's as this change leaves it; the base is
`fcdc7ea875ccd6c203cc2ebbf2f60ec313430229`. The change edits `tools/loop/tests/loop-cases.sh` and
`guidelines/Backlog.recipes.md` and touches no engine script and no compiled file. Every run is kept in the run's
evidence directory, `.pi/issue-loop/items/978/`, and copied into this change's own `evidence/`.

## 1. The red case, before the fix

- [x] 1.1 `run#aHupEndedRunRecordsItsStopAndUnpicksItsItem`, as this change writes it, reds on HEAD in the tree the
      gate hands it — a suite started detached with `nohup` — and reds at the disposition the run was started with
      rather than at the wait: `FAIL … a run that took HUP did not end inside 300 ticks (30 s for each of the 1
      case(s) the pool starts beside this one; status 124) — its stop was '' and its item ''`, exit 1
      (`evidence/red-on-HEAD-nohup-case.log`, 2026-11-20T01:28:08Z, transcribed as
      `evidence/red-on-HEAD-nohup-case.xml`, `tests=1 failures=1 errors=0`). The figure is the pool's width at one
      case, not a duration this host measured, and the empty stop and item are the gate's own reading at 4800 ticks.
- [x] 1.2 The falsification that separates the disposition from the wait: with the reset removed and the assertion
      left in place, the same case reds **at the staging assertion**, in seconds instead of at its deadline —
      `FAIL … the run's shell (pid 1036087) ignores SIGHUP, so the trap this case sends its signal to cannot be
      installed in it (issue #978)` (`evidence/revert-check-without-the-reset.log`, 2026-11-20T01:26:17Z, exit 1).
- [x] 1.3 The disposition is a fact about `SIGHUP` alone, and the neighbour cases say so: under the same `nohup`
      suite `run#interruptedRunEndsTheChildItStarted` (`TERM`) and
      `run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock` pass unchanged
      (`evidence/check-term-case-nohup.log`, `evidence/check-hup-cases-nohup.log` — the latter reads
      `loop-selftest: all 2 cases passed`). No second call site of `run_loop` is touched.

## 2. The reset is staged where the run is started — `tools/loop/tests/loop-cases.sh`

- [x] 2.1 `run_loop_resetting_hup` (`loop-cases.sh:1534-1539`) is `run_loop` with the one caller-side lever a shell
      has for a disposition: `env --default-signal=HUP bash "$LOOP_ENGINE/run.sh" "$@"`. It is a variant, not a
      change to `run_loop`: `grep -c 'run_loop "\$p"' tools/loop/tests/loop-cases.sh` reads 169, and every one of
      them still starts its run as it did. Verify: the green pair of 2.2 and 2.3 — `PASS` in both
      (`evidence/green-on-fix-nohup-case.log`, `evidence/green-on-fix-focused-case.log`).
- [x] 2.2 The case starts its run through it (`loop-cases.sh:16508`), and the comment above it
      (`loop-cases.sh:16500-16507`) names the disposition as a stage of its own and why the reset lives there rather
      than in `run.sh`. Verify: the sentence the change replaced no longer stands —
      `tools/look.sh 'the write order is right and says nothing about the disposition' --path tools/loop/tests/loop-cases.sh`
      → no match, and the HUP case passes under `nohup` (2.1's first log).
- [x] 2.3 The case asserts the staging before it signals (`loop-cases.sh:16531-16543`): it reads `SigIgn` from the
      run's own `/proc/<pid>/status` and fails naming `SIGHUP` when bit 0 is set, and a host without `/proc` skips
      the assertion instead of guessing. Verify: 1.2's red is that assertion firing, and 2.1's green pair passes with
      it in place.
- [x] 2.4 The case's `# files:` declaration (`loop-cases.sh:16484`) names `tools/loop/prompts/worker-gate.md`, the
      prompt its new comment reaches and the one that states the detached start. Verify:
      `tools/loop/tests/loop-selftest.sh --case driver#everyCaseDeclaresTheFilesItCanReach` → `PASS`
      (`evidence/check-declaration-case.log`) — it is the first line this delta owes, and it is run before the
      neighbours.

## 3. The guideline that owns this case's staging — `guidelines/Backlog.recipes.md` section 7b

- [x] 3.1 The paragraph that owns the case's clock and staging, "The clock a case keeps is the pool's width, not a
      duration the host measured" (`guidelines/Backlog.recipes.md:869`), gains the third stage: the disposition, the
      two `SigIgn` readings, why the reset cannot live in `run.sh`, and the assertion that reds in its place
      (`guidelines/Backlog.recipes.md:902-914`). Verify: the paragraph carries all five facts, and no other section
      of the file states this case's staging — `tools/look.sh 'The signal.s disposition is the third thing' ` →
      one match, in section 7b.

## 4. What the change leaves to the gate child

- [x] 4.1 Nothing outside the change directory is written after the gate starts, so this delta's own record — the
      logs of 1 and 2 copied into `evidence/`, `bash tools/loop/evidence-check.sh openspec/changes/fix-the-hup-case-stages-a-trappable-signal --root .`
      reporting `PASS`, and `openspec archive --yes` moving the change under `openspec/changes/archive/` — is the
      gate child's phase E, before its gate, not a step of this session (issue #837).
