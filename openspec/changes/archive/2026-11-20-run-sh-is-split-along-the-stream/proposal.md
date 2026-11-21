# Proposal — the engine is five scripts, and the cut follows the record

## Why

`tools/loop/run.sh` is one file: 4425 lines, 312 kB, 78 functions, and one of them — `run_child` — 393 lines
(`wc -lc tools/loop/run.sh`, and the function count from the `awk` that pairs each `name() {` with its `}`). It
grew 410 → 3793 lines in the four weeks to 2026-11-14 (+120 lines/day, no plateau), and reached 4425 lines by
2026-11-20, so the file that four weeks ago was readable is now the largest artifact in the repository.

Three costs follow, and each is measured rather than felt:

- **Nothing owns anything.** 78 functions in one address space, sharing dozens of globals; no name says which
  function may read which variable. A change to `run_child` can break `keep_leftovers` and only the suite knows.
- **A delta's gate cannot be narrowed.** The suite selects cases by declared files (`# files:`), and 204 of its
  502 cases declare `tools/loop/run.sh` — so a delta to the engine's header pays those 204 cases (723 s for 190
  cases, measured 2026-10-23; the suite is larger now). The mechanism exists and the layout defeats it.
- **Growth is unbounded and unpriced.** Nothing refuses a file past a size. A 9.3x growth in a month is therefore
  not an event the suite can report; the next month has no reason to look different.

The change `run-event-stream-is-typed` gives the cut a seam that is not arbitrary: the run already acts in
phases, and once each fact it prints is a typed event whose kind a phase owns, a file boundary can follow the
kinds. This change makes that boundary real and declares it.

## What Changes

- **`run.sh` becomes the entry plus three owner files:**

  ```
  tools/loop/run.sh         entry, flags, header, the item loop, the stop arms
  tools/loop/child.sh       run_child, the four bounds, the pid rules, the stale-result clear, the tail
  tools/loop/evidence.sh    the outside-write rule, red-gate attribution, the review's scope and its diff
  tools/loop/leftovers.sh   keep/adopt/merge/discard of a kept tree, the dirty and retry helpers
  ```

  Nothing is rewritten while it moves: every function body is the body it has today, and the whole suite (502
  cases) is the proof that a move changed nothing. The shared names are not a fourth new file: `shared.tsv`
  already declares them (archive `2026-11-16-fix-a-local-is-not-a-shared-name`) and the split re-harvests it.

- **The declared layout gains the split's rows**: `tools/loop/layout.tsv` (#890) already names each engine file and
  the line bound it is held to; this change adds a row per new file and gives the table the order column and the
  owner columns its rows are read for here. A case reads it, so the layout is a fact the suite checks rather
  than prose a reviewer remembers.
- **A size bound per file**, declared in the same table. Growth is then red in the suite instead of noticed six
  weeks later — and the bound is a recorded number, so raising it is a diff a person argues for.
- **One declaration of shared names**: `shared.tsv` lists every name one file assigns and another reads, with its
  assigning file. A name assigned in two files, or a name read by a file the row does not list, fails a case.
- **The source order is declared** and asserted: `lib.sh` → `evidence.sh` → `leftovers.sh` → `child.sh`, sourced by
  `run.sh` before its first use.
- **A case's `# files:` declaration follows the split**, so a delta to one owner's file selects that owner's
  cases: the 204 declarations that name `run.sh` name the file their behaviour now lives in, and a declaration
  naming a file the table does not carry fails. The narrow-selection mechanism finally does what it was built
  for.
- **Scope**: `tools/loop/**` only — no app code, no dependency, no build change. Additive in behaviour: the split
  is a move, and a case proves the run's observable behaviour is unchanged.
- **Not this change**: `state.sh`, `worktree.sh`, `gate.sh`, `prescreen.sh` and `report.sh` keep their shapes.
  They are already one-owner files; what this change adds is the declared bound, which they then also enter.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. `agent-issue-loop` gains two requirements, both new: the layout and its ownership, and the case declaration following the split. The shipped requirement
`The self-test runs its cases in a bounded pool` and `The self-test compiles the Java core when a case asks for
it` are untouched; the existing case `driver#everyCaseDeclaresTheFilesItCanReach` keeps its meaning and gains a
stricter subject (the split's files rather than one file name).

## Impact

Code:

- `tools/loop/run.sh` — reduced to the entry and the item loop; sources the three new files.
- `tools/loop/child.sh`, `tools/loop/evidence.sh`, `tools/loop/leftovers.sh` (new).
- `tools/loop/layout.tsv` — a row per new file, its bound, the owner columns and the source order row;
  `tools/loop/shared.tsv` — re-harvested so each row names its assigning file.
- `tools/loop/tests/loop-cases.sh` — the new cases and the `# files:` declarations of the cases whose behaviour
  now lives in a named file (204 of the 502).
- `tools/loop/tests/loop-mutations.sh` — one revert-check per rule this change adds (records `892.1`–`892.6`).
- `tools/loop/tests/loop-selftest.sh` — sources the split files where it sourced `run.sh`, and its `--files`
  selection reads the new declarations unchanged.

Guidelines and documents:

- `guidelines/Backlog.md` section 7 (loop procedure, via `tools/look.sh '^## ' --path guidelines/Backlog.md`) — the
  engine's layout, since a person fixing the engine reads that section to find the file.
- `guidelines/BuildGate.md` §2a (the iteration protocol) — the narrowed selection is what makes a focused
  `tools/loop/**` iteration cheap; the section names the new declaration.
- `PORTING.md` "What to copy" — `tools/loop/` gains three files; the copy list is a directory, so the recipe
  needs no row, but the "three files to edit" paragraph gains the sentence that `layout.tsv` is engine, not profile.

Rollback: revert the commit. The files are a move; a reverted tree has one file again and the suite is green on
it by construction, because no behaviour is in the split that was not in the move.

Costs to prove by measurement rather than assume:

- **the narrowed gate**, from the suite itself: a delta touching only `evidence.sh` must select materially fewer
  cases than 502, and the number and the wall time are recorded in the change's evidence;
- **the largest file**, from `wc -l`, before and after, and the bound it is held to.

surface: 2 requirements and 6 scenarios added to 1 delta file, 0 requirement bodies rewritten, 3 new engine
files, one layout table extended by 3 rows, 204 case declarations re-pointed — counted with
`grep -hc '^#### Scenario:' openspec/changes/run-sh-is-split-along-the-stream/specs/agent-issue-loop/spec.md`
and `grep -h '^### Requirement:' openspec/changes/run-sh-is-split-along-the-stream/specs/agent-issue-loop/spec.md`
