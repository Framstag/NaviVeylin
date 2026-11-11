# Fix — a gate's tallies belong to the run that wrote them

## Why

`tools/loop/gate.sh:554` writes every step's tally under one path the profile names — `LOOP_GATE_TALLY_DIR`
(`tools/loop/loop.conf:702`: `app/build/test-results/loop-gate`) — and the gate neither claims that directory for the
length of a run nor clears what the run before it left. The directory is therefore one per *tree*, not one per
*run*, and a carrier an earlier run wrote survives the next one.

Measured 2026-11-10 (item #830): the green gate's directory held
`tools_loop_tests_loop-selftest.sh_--from-git_8c726e15…xml`, a carrier stating one case, one failure and an 1800 s
overrun — the `LOOP-GATE-STEP:` line of a run that had been red for the self-test suite's own lock — and
`tools_tests_check-portability-selftest.sh.xml`, stating four failures of a suite that gate did not run. Neither
step ran that way in the run being archived. Copying the directory by mtime window took three attempts to exclude
them; the record was finally made from the gate's console
(`openspec/changes/archive/2026-11-10-fix-a-gate-mention-is-not-a-gate-run/evidence/gate-run-line.md` says so, and
carries the numbers).

**Root cause, in one sentence:** the gate's tally directory is a profile path shared by every run of a tree, and
nothing makes it one run's — no claim for the length of a run, and no clearing of the run before — so a step file
one run wrote is read as the next run's, and a reader copying the directory as the gate's evidence copies a step the
gate never ran.

**Red test:** `gate#aCarrierAnotherRunWroteIsNotReadAsThisOnes` (`tools/loop/tests/loop-cases.sh`) plants a carrier an
earlier run wrote in the tally directory, then runs a gate that owes one stub suite, and asserts the directory holds
that suite's tally and no other and the gate's own line carries the suite's numbers. On HEAD the planted carrier
survives beside the new one — `evidence/red-on-head.xml`, `tests=1 failures=1` of the carrier, which is not a step
the gate ran.

**Owning capability:** `agent-issue-loop`, "The gate streams a suite's output while it runs" — the tally the run's
own check reads for a green claim. This delta adds "A gate's tallies belong to the run that wrote them".

**Owning guideline section:** "The gate and its suite" in `guidelines/Backlog.md`, whose body is the same section in
`guidelines/Backlog.recipes.md` — the row AGENTS.md's Documentation Map gives for the loop's procedure routes the
index in `Backlog.md` and the bodies beside it.

surface: 2 cases added, 0 rewritten, 2 mutation records added, 1 guideline paragraph, 0 fixtures — `tools/look.sh 'LOOP_GATE_TALLY_DIR' --path tools`: 4 files hold the key (the profile names the path, `gate.sh` writes it, `measure.sh` sums it, and the cases name a throwaway directory 20 times and empty the key twice). The directory's readers read it whole and need no edit once it is one run's; the suite's own count moved from 405 to 407 (`tools/loop/tests/loop-selftest.sh --list`).

## What changes

- `tools/loop/gate.sh` claims the tally directory for the whole run with a lock of its own (`.gate.lock` inside it,
  the owner's pid and start time written beside it), refusing by name while another gate holds it and taking over a
  dead owner's lock exactly as the self-test suite does; and it clears every `*.xml` the directory still holds
  before its first step writes a tally. Each tally carries the run that wrote it (`run="<pid>"`), and the gate's
  console names the directory and that run (`LOOP-GATE-TALLY:`).
- `tools/loop/tests/loop-cases.sh`: the red case above, and `gate#aSecondGateRefusesWhileOneRuns` for the refusal
  half.
- `tools/loop/tests/loop-mutations.sh`: one record per new guard (`848.1` the sweep, `848.2` the refusal).
- `guidelines/Backlog.recipes.md`, the section "The gate and its suite": the rule beside its measurement.
- `openspec/specs/agent-issue-loop/spec.md` (delta): the new requirement and its two scenarios.

## Non-goals

- A per-run *subdirectory*: it would move the path every reader already reads (`measure.sh`, the gate child's
  evidence copy, every archived record's pointer) and leave the stale-carrier question to each reader. The directory
  is made one run's in place instead.
- `measure.sh`'s reading of the directory: unchanged, because the directory now holds one run's carriers.
