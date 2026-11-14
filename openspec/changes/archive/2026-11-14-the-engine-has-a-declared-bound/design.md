# Design — describe the engine as it is, so growth has a number

## Context

See `proposal.md` — Why. Constraints:

- **The suite selects cases by declared files** (`# files:`), and 189 declarations in `loop-cases.sh` name `run.sh`
  (`tools/look.sh '# files:.*tools/loop/run.sh' --path tools/loop/tests/loop-cases.sh`, 2026-11-14). A change to one
  engine file therefore pays most of the suite today; this change does not fix that —
  it makes the file list and its numbers explicit so the next change can.
- **Nothing may move.** A declaration change that also moves a function cannot be told apart from a behaviour change
  by the suite, and the move is its own child.
- **`local` needs no declaration**: a name local to a function is not shared state, and declaring 200 of them would
  be a second list to drift.
- **`tools/loop/**` may name no project value** (`tools/check-portability.sh`), so both tables are engine data.

## Goals / Non-Goals

Goals: a number per file the suite compares against, one declaration of the names files share, and a source order a
reader can see. Non-goals: moving anything, owning functions per file (that needs the move), narrowing the selection
(that needs the move too).

## Decisions

### D1 — Two tables, not one

Alternatives:

- **One table with a column per fact** (rejected): a file row and a name row have nothing in common but the file
  they mention; one table with a ragged second half is harder to read and to check than two flat ones.
- **The bound in the script's own header comment** (rejected): a comment is not a number a case can compare, and
  the engine already has 1496 comment lines in one file — the drift this project keeps paying for.

Chosen: `layout.tsv` (file, bound, order) and `shared.tsv` (name, assigned by, read by) — both flat, both read by a
static case, both cheap to extend by one row.

### D2 — The bound is per file and starts at today plus a headroom

Alternatives:

- **One total bound** (rejected): the total is already violated and a total lets growth hide in the file nobody is
  reading — which is exactly what happened to `run.sh`.
- **A bound per function** (rejected): `run_child` legitimately needs 393 lines; a function bound pushes logic into
  more, smaller functions with more shared state — the coupling the next child reduces.

Chosen: per file, `wc -l` today + 25 % rounded up, so the table records today's size and refuses only growth. The
figures are committed with the change, so raising one is a diff with a reason beside it.

### D3 — The shared names are harvested once and then checked, never generated

Alternatives:

- **Generated from the code on every run** (rejected): a generator that parses bash is a parser with its own bugs,
  and the check would then agree with whatever the code does — including a name two files assign, which is the
  defect it must catch.
- **Declared by hand with no check** (rejected): a declaration nothing compares is prose.

Chosen: the rows are written once (from a read of the function bodies) and a case compares the declaration with the
names actually assigned in one file and read in another, so neither a stale row nor an undeclared name survives.

## Risks / Trade-offs

- **A bound that is already too tight** (a file grows by one line and the suite reddens) → the headroom is 25 %, and
  raising a bound is a one-line diff a person argues for rather than a refactor.
- **The harvest misses a name** (a variable assigned in one file, read in another, behind a subshell) → the
  comparison case is over-approximated and a missed name is red on the commit that adds the read, not silent.
- **The table rots as scripts are added** → a script with no row is a refusal, so a new engine file cannot arrive
  unmeasured.

## Migration Plan

1. `layout.tsv` with the 12 rows and the source order the entry script already uses.
2. `shared.tsv` harvested from the function bodies; the suite must be green with nothing moved.
3. The eight static cases, then the recorded baseline in the evidence directory.

Rollback: revert the commit. Two data files and eight static cases.

## Open decisions

none
