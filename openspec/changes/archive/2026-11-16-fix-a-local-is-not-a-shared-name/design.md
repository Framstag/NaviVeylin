# Design — the derivation models the function, the loop and the quoted run

## Root cause

`layout_shared_awk`'s mention pass (`tools/loop/tests/loop-cases.sh:17131`-`:17151`) decided that a line which
spells `$name` is a read of *another file's* assignment, and it answers that from the line alone: it has no
function scope, so a read inside the function that declared the name `local` is attributed as a read of the file
that assigned the same spelling; and `scanned()` (`:17068`) stripped single-quoted text per line, so a line inside a
program opened with `'` on an earlier line was scanned as shell text. The header (`:16944`-`:16950`) states the rule
the pass is supposed to implement, so the defect is the model the pass lacks, not a mis-stated rule.

## The single fix

Two models inside the same awk program, and one table row set that follows from them:

1. **Function scope.** The assignment pass records, per function name, the names its `local` lines declare (flags
   `-a`/`-A`/`-r` skipped, `=value` cut) and the name its `for NAME in` / `for ((NAME=…))` lines bind. The mention
   pass skips a name when the function it is currently inside owns it. Function boundaries are the engine's own
   style: a `name() {` line at column 0 opens, a `}` line at column 0 closes.
2. **A single-quoted run carries across lines.** `drop_single_quoted()` walks the line and keeps the run's state in a
   variable reset per file, replacing the dropped text with `''` so two words never merge. Both passes scan the
   dropped line, so the assignment side cannot be fooled either.
3. **`shared.tsv` loses the rows the first model disproves** — `merge_refused_branch` and `merge_refused_paths`:
   `state.sh`'s `merge_refused_branch` and `merge_refused_paths` are `local`s of `cmd_done`, so no second file reads
   `run.sh`'s assignment of either.

Nothing about the doubles check (`driver#aNameAssignedTwiceIsRefused`) changes: it reads the same `assigned`/`seen`
tables, which now simply do not hold a mention a function owns.

## Blast radius

- `tools/loop/tests/loop-cases.sh` — one awk program, two case bodies, two registrations (the case list `:435`
  region and the dispatch table `:1185` region).
- `tools/loop/shared.tsv` — 16 rows the model no longer holds, 11 rows whose reader list narrows, and one header
  sentence.
- `tools/loop/report.sh:259`-`:261` — a comment whose claim this change makes false.
- Nothing else reads either table at run time: `layout_shared_refusals` and `layout_shared_awk` are reached only from
  the four `driver#` cases.

The derivation's output for the engine today changes only where the two models apply, and
`driver#theSharedNamesAreDeclared` is the instrument that says where: it is run before the table is edited, so the
rows that move are named by the case rather than guessed.

## Rollback

Revert the commit. The two tables are static data read by static cases; a reverted tree runs exactly as the base did.

## Alternatives the evidence eliminated

- **Reword the header to admit a row is an argv handoff, and keep the rows.** Rejected: the header's rule is the one
  the requirement at `openspec/specs/agent-issue-loop/spec.md:5045` states ("A name local to a function SHALL need no
  declaration"), so rewording the header would leave the check contradicting the spec it is gated by, and the next
  author is red on the same shape (`local`, argv) that the rows now bless.
- **Rename the engine's variables until the check stops complaining** — what `471de691` did for `archived` and
  `prescreen.sh`'s counter. Rejected: it fixes the tree for one spelling and leaves the reader wrong, which is the
  reported defect; the case would still be green by coincidence of naming.
- **Parse bash properly.** Rejected: no parser is a dependency this tree may take for a static check (the engine is
  offline and shell-only), and the header's own words — "the approximation is deliberate and it is the same reader on
  both sides of every comparison" (`:16957`) — price the miss (a red commit) against the false share (one row).
- **Rename `report.sh`'s `archived` back.** Rejected for scope: the name is correct under the fix, but the rename is
  cosmetic and its own diff; the stale comment is what claims something about the tree, so the comment goes.
