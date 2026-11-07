# The gate reads the profile of the tree it measures, and says which one that was

## Why

**Verified in the tree at the base commit `850dea2c`.** `tools/loop/run.sh:66` exported the run's configuration to
every child:

```
export LOOP_CONF="${LOOP_CONF:-$LOOP_DIR/loop.conf}"
```

and `tools/loop/lib.sh:116` takes an inherited `LOOP_CONF` as given (`conf="${LOOP_CONF:-$LOOP_DIR/loop.conf}"`). The
value run.sh exported is resolved from **its own** engine directory — the project the run was started from — while
the child it starts works in the worktree (`tools/loop/run.sh:680`, `cd "$LOOP_ROOT"`) and runs **that** tree's
engine (`LOOP_GATE_CMD="./tools/loop/gate.sh"`, `tools/loop/loop.conf:139`). So the gate of a change whose delta
edits `tools/loop/loop.conf` measured the profile the change was replacing, and the gate's log named no profile at
all, so nothing in the log could say so.

**Root cause, one sentence.** The run promotes its own resolved profile into an environment variable that every
child inherits, and the child's gate prefers that inherited value over the profile beside the engine it is running —
so the gate measures a rule the tree it is gating may be replacing.

**What it cost, as the item measures it.** Finishing #273 — whose delta rewrites `LOOP_GATE_COMPILED_PATTERNS` — the
round-3 gate log carried no `LOOP_CONF=` pin and named `./gradlew test -PforceTests --no-build-cache` first, while
the same tree's own profile owed five shell checks and no build; the round-3 reviewer refused the round because the
log contradicted the change's own claim, and the repair was a hand-pinned re-run
(`LOOP_CONF=<worktree>/tools/loop/loop.conf`). The archived change
`2026-10-23-fix-gate-reads-a-compiled-path-by-its-tree` carries both logs (`evidence/gate-round3.log` against
`evidence/gate-round3-pinned.log`). Any change that edits `loop.conf`, adds a pattern or adds a suite meets it, and
the failure is silent: the gate is green.

**The red test.** Two cases added by this change, run against the engine as `850dea2c` carries it, 2026-10-23T14:18:02Z
(`bash tools/loop-selftest.sh --case 'gate#the*Profile*'`, transcript `evidence/red-on-head.log`, tally
`tests="2" failures="2"` in `evidence/red-on-head.xml`):

- `gate#theGateNamesTheProfileItRead` — `the log names the profile the gate read: 'LOOP-GATE-PROFILE: …/pinned.conf'
  not found in: LOOP-GATE: nothing owed …`;
- `gate#theChildReadsTheProfileOfTheTreeItWorksIn` — the run handed its child
  `LOOP_CONF=/home/tim/projects/NaviVeylin/tools/loop/loop.conf`, and the gate of the worktree the child works in
  read that file instead of the one beside its own engine.

**Owning requirement.** `agent-issue-loop`. The capability owns every other property of what a gate measures and of
what it records — the pathspec, the compiled rule, the streamed output, the numbers, the tally — and none of them
says *which profile* the numbers belong to, nor which profile a child's gate reads.

**Owning guideline section.** `guidelines/Backlog.md`, its section "The loop" (`guidelines/Backlog.md:235`–`414`), in the run
of paragraphs about what fills the gate's log, beside the sentence that a tally's name is never a dotfile. No
existing sentence asserts which profile the gate reads; this change adds one.

## What Changes

- **`tools/loop/lib.sh`** — `loop_resolve` records the file it sourced as `LOOP_CONF_PATH`, one global that answers
  "which profile did this process read" for every script that uses the library. The seam is unchanged:
  `LOOP_CONF` still overrides the profile beside the engine.
- **`tools/loop/gate.sh`** — the log's first line is the profile the gate read: `LOOP-GATE-PROFILE: <path>`, and a
  profile read from outside the tree the engine sits in is named as pinned beside the one the engine ships. `--plan`
  prints commands and nothing else, because `measure.sh` reads that output as the check list; the pin is written
  only when the gate is going to run something.
- **`tools/loop/run.sh`** — the run no longer exports its own profile to its children (`export -n LOOP_CONF`): the
  value it read stays this process's own, and a child resolves the profile beside the engine it runs. The empty-agent
  refusal names `LOOP_CONF_PATH`, the file that was actually sourced.
- **`tools/loop/measure.sh`** — the block names the profile the checks came from (`LOOP-MEASURE-PROFILE`), so the
  worker's own numbers are attested the way the gate's are.
- **`tools/loop-cases.sh`** — two cases, `gate#theGateNamesTheProfileItRead` and
  `gate#theChildReadsTheProfileOfTheTreeItWorksIn`, declared in the case list and named in the dispatcher.
- **`tools/loop-mutations.sh`** — records `52.1`, `52.2` and `52.3`, one per half of the invariant: the run's export,
  the pin line, and the "pinned" annotation.
- **the spec `agent-issue-loop`** — one ADDED requirement, "The gate reads the profile of the tree it measures",
  with its two scenarios naming those cases.
- **`guidelines/Backlog.md`**, in its section "The loop" — one paragraph: the profile a gate reads is the one beside
  its own engine, and the log names it.

surface: 2 cases added, 0 rewritten, 0 fixtures realigned — `grep -rn "LOOP_CONF" tools/loop-cases.sh
tools/loop-selftest.sh tools/loop/prompts tools/loop/fixtures` (80 lines, 2 files, both of them case code) and
`grep -rn "LOOP_CONF" tools/loop/*.sh` (12 sites in four engine files: `lib.sh` 4, `gate.sh` 4, `run.sh` 3,
`measure.sh` 1). No case asserts which profile a child's gate reads — the nine `gate#` cases the change inherits
pin `LOOP_CONF` per invocation, and the two it adds assert the pin line — and no prompt or fixture names the profile
of a child's tree.
