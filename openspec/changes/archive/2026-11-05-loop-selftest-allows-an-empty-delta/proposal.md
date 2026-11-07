# A delta that no case declares is stated, not refused

## Why

`Check agent issue loop` runs the loop's whole case set on every push and every pull request: **17 min 15 s**,
measured 2026-11-04 (run 41810844107, job 126509396527). The same driver already narrows itself and the gate
already runs it that way — `--files` / `--from-git` selects the cases whose declaration intersects the delta, and
the profile's suite command carries `{base}`, which `gate.sh` replaces with the base it measured. The CI step is
the one caller that hands it nothing.

**Measured selection on this tree, 2026-11-04** (`bash tools/loop-selftest.sh --list --files <path>`):

| delta | selected |
|---|---|
| `guidelines/BuildGate.md` | 2 of 314 cases |
| `AGENTS.md` | 2 of 314 |
| `README.md` | 2 of 314 |
| `tools/loop/lib.sh` | 314 of 314 (a core file owes every case) |
| `app/src/main/java/com/naviveylin/MainActivity.kt` | **exit 2 — refusal** |
| `openspec/specs/agent-issue-loop/spec.md` | **exit 2 — refusal** |

**Root cause, in one sentence.** The driver has two answers — the selected cases, and a refusal for a file set
that names none — and a caller that is handed a real delta owes a third: "no case declares this delta", which is
not the same thing as "this file set is broken". Because the two are spelled identically (exit 2, one message), a
CI step cannot ask the first question at all: most deltas in this repository declare no case, so narrowing today
would turn the step red without a defect anywhere in the case set.

The refusal is right and stays. What changes is that the caller can now say which of the two it is asking.

surface: 1 flag added to `tools/loop-selftest.sh` (4 argument lines, 1 refusal branch, 1 summary line), 1 case
added to its `driver` group, 1 requirement modified with 1 scenario added, 1 workflow step given a base and a
skip — `grep -c 'allow-empty' tools/loop-selftest.sh` and
`bash tools/loop-selftest.sh --list --files app/src/main/java/com/naviveylin/MainActivity.kt`
(exit 2 before, exit 0 and the line after).

## What Changes

- **`tools/loop-selftest.sh`** — `--allow-empty`. With it, a file set that names no case prints
  `LOOP-SELFTEST: no case declares the delta` beside the file set it read and the declared set it selected
  against, runs nothing, and exits 0. Without it, nothing changes: the same file set still exits 2 with the
  refusal that says a run which executes nothing must not report success.
- **`.github/workflows/build.yml`** — the step hands the suite the delta it owes: `--from-git
  ${{ github.event.pull_request.base.sha }}` for a pull request, `--from-git ${{ github.event.before }}` for a
  push, the whole set when `before` is the all-zeros SHA (a first push, or a force-push whose base is unknown), and
  `--allow-empty` so that a delta the case set does not declare is a skip that carries the line rather than a red
  step. A delta to an engine file still selects every case, which is correct.
- **`guidelines/Backlog.md`** §7c — the paragraph that states the suite's cost and its file-set selection gains
  the third outcome and the bound's shape: the whole set stays owed before a change that owns `tools/loop/**` is
  archived. The body of that section is `guidelines/Backlog.recipes.md`, which is where the edit lands.
- **`tools/loop-cases.sh`, `tools/loop-mutations.sh`** — the case that reads the pair
  (`driver#anEmptyDeltaIsStatedWhenTheCallerAllowsIt`), two records beside 53.8, and one repair the step forced:
  `gate#theMapCoversEveryPipelineStep` now compares the pipeline's command with the map's after both drop the
  parts each substitutes at run time (`${{ … }}`, `--group <selection>`, `--from-git {base}`), because the leg
  split on the base made the step's own command something the map cannot carry verbatim. That repair is also
  task 4.1 of the open change `loop-suite-legs-across-runners`; whoever lands second takes the other's copy.

## What This Does Not Do

- It does not weaken the refusal: a path the tree does not hold, and a path no case declares *given without*
  `--allow-empty`, are answered exactly as they are today.
- It does not narrow the archive's evidence. A change that owns `tools/loop/**` still owes all 314 cases before it
  is archived — a case reaches behaviour through the scripts it calls, and only a hand-run of all of them covers
  that (`guidelines/Backlog.md` §7c, issue #276).
