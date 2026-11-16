# Proposal — a `local` is not a name two engine files share

## Why

`layout_shared_awk` (`tools/loop/tests/loop-cases.sh:16965`) derives the names the engine's files share and
`driver#theSharedNamesAreDeclared` (`:17514`) compares that set with `tools/loop/shared.tsv` in both directions. Its
own header states the model — "A `local`, and an assignment inside a body, is not state a second file can read, so it
needs no row" (`:16944`-`:16945`) — and on the base the derivation did not implement it: the mention pass
(`:17131`-`:17151`) counted every `$name` on a line as a read of another file's assignment, with no notion of the
function that read it and no notion of a single-quoted run that outlives its line.

Three places in today's engine show the gap (all three cited from issue #930, verified here on the base
`d794277b`):

- **a `local` read inside the function that declared it.** `lib.sh:557` declares `archived` in
  `loop_change_restore` (`:556`-`:578`), and `:564`-`:578` read it in that same function. A file that mentions the
  name elsewhere makes the derivation report it as shared state.
- **a value handed across a process boundary by argv.** `run.sh:486`-`:487` assign `merge_refused_branch` and
  `merge_refused_paths` at column 0; `state.sh` reads its own `local`s of both names (`:689`, `:693`) inside
  `cmd_done` (`:687`-`:1010`, reads at `:827`, `:830`, `:934`-`:937`). Two rows of `shared.tsv` declared the handoff
  as if it were a shared variable, because the derivation could not tell the receiver's `local` from a read of the
  sender's name.
- **a jq program in single quotes, read as shell.** `prescreen.sh:248`-`:256` holds a multi-line jq program;
  `scanned()` (`:17068`) stripped single-quoted text per line, so a line inside the program was scanned as shell and
  a `$name` there was a read. Latent today only because #934 renamed the counter the program last spelled.

The check reds the next author who reads the header and believes it — which is what the `archived` rename and the two
argv rows of change `the-engine-has-a-declared-bound`'s follow-up (`471de691`) worked around rather than fixed.

## What Changes

- **`tools/loop/tests/loop-cases.sh`** — `layout_shared_awk` gains the two models its header already states: a
  function scope (a name the reading function declares `local`, or binds as its `for` loop variable, is that file's
  own state and not a read of another file's assignment) and a single-quoted run that carries across lines. The
  header's `read` bullet states both, so the prose and the code are one rule.
- **`tools/loop/shared.tsv`** — 16 rows go, because the model now reads their readers as their own `local`s or `for`
  variables (`merge_refused_branch` and `merge_refused_paths` among them: the argv handoff), and 11 rows lose a reader
  for the same reason. The header says what a handoff is not.
- **Two cases** — `driver#aLocalIsNotAShareOfAnotherFilesName` (a `local` read, and a `for` counter read) and
  `driver#aQuotedProgramIsNotAShellRead` (a `$name` on the second line of a single-quoted program). Both are red on
  the base and green once the derivation models the rule.
- **`tools/loop/report.sh:259`-`:261`** — the comment that kept the report's `archived` under another spelling stated
  that a static reader counting two unrelated `local`s reds the suite. After this change it does not, so those two
  lines are deleted rather than left as a claim the tree no longer makes true.
- **Scope**: `tools/loop/**` and one delta file. No app code, no dependency, no build change, no run behaviour.
- **Not this change**: renaming `report.sh`'s array back to `archived`, and any rewrite of the derivation into a
  real parser. Both are named in `design.md` under the alternatives the evidence eliminated.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `agent-issue-loop` — "Every name two engine files share is declared in one place": the requirement's body gains the
  function scope and the quoted run, and two scenarios name the two new cases.

## Impact

Code:

- `tools/loop/tests/loop-cases.sh` — the derivation's mention pass and its helpers, the two new cases, and their
  entries in the case list and the dispatch table.
- `tools/loop/shared.tsv` — 16 rows removed, 11 rows' reader lists narrowed by the same model, and four header lines
  stating what a `local`, a handoff and a quoted run are not.
- `tools/loop/report.sh` — one stale comment removed.

Guidelines and documents: none — `guidelines/Backlog.md:345`-`:347` and `guidelines/BuildGate.md:70`-`:72` describe
the table's shape (a row per name one file assigns and another reads), not this rule, and stay true.

Rollback: revert the commit. 27 table rows and one static reader; nothing at run time reads them.

surface: 2 cases added, 0 rewritten, 16 rows removed from 1 table and 11 rows' reader lists narrowed, 1 comment
(`report.sh:261`) deleted, 1 helper's mention pass rewritten — counted on the base `d794277b`, before this change's
files existed, with `tools/look.sh 'local.{0,40}(no row|not state)|a `local`'` (8 matches in 7 files, of which
`tools/loop/tests/loop-cases.sh` and `tools/loop/shared.tsv` are the rule's own statement) and
`tools/look.sh 'shared\.tsv'` (48 matches in 24 files, 11 of them inside `tools/loop/tests/loop-cases.sh`).

## Evidence on the base

- Red on `d794277b`, the change's two cases: `evidence/red-on-HEAD.log`, `evidence/red-on-HEAD.xml` — taken in a
  clone of the base commit carrying only this change's two case bodies, so the derivation that failed is the base's.
- The derivation's own refusals for the base's engine, from `driver#theSharedNamesAreDeclared` before and after:
  `evidence/shared-rows.log`.
