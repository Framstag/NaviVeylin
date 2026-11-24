# Design: the-action-list-leads-with-its-counts

## Context

`report.sh`'s `decisions` builds the caller's list from the state: each escalated item, each branch the base does not
carry, each item a bound cut, and the worktree note. Every line spells its reason in full, because the reason is the
run's evidence and a person deciding on an item must not have to open a second file for it. That is right per line and
wrong as a list: the reader's first question is how much of what kind awaits them, and the list answers it at the end,
if at all.

## Decision 1 — the counts are a header, not a summary

The counts come from the same rows the list prints — `decide` from the escalated items, `land` from the branch rows,
`resume` from the cut items, `worktree` from the note — so the header and the list cannot disagree. The list itself is
unchanged, line for line: the change adds a reading, it does not replace one. A caller who needs the batch (nine
branches is one merge decision) reads the header; the lines stay for the one item they must look at.

## Decision 2 — `--actions` is the list on its own

The report is read at a desk, and a caller who has already read it once wants the list again after acting on part of
it. `--actions` prints the list only, which is the same shape `--json` gives a machine reader; both are flags on the
call, neither changes what the default output says.

## Decision 3 — what was rejected

- **Shortening the reasons.** The reason is the evidence for the decision; a reader deciding #950 needs the clause
  that says the leftover patch does not apply.
- **Grouping the list by class.** A reorder of nineteen lines that the header already counts, and it would break the
  order in which the run recorded them, which is what a caller correlating two runs reads.
- **Folding the resumes into the land rows.** Four resumes share a branch with a land row, but they are not the same
  action: one lands a tree, the other re-opens an item. Merging them would lose a distinction the loop's own bounds
  make.

## The falsification

Both cases are red on the current tree: no counts line exists, and `--actions` is an unknown argument (`report: unknown
argument`), which `run_arguments` answers with a die. The second case is what keeps the flag from becoming a second
report.

## The document this change owes

The shape of the caller's action list is stated by the spec `agent-issue-loop` and by no guideline: `tools/look.sh
'actions the caller owes'` names `openspec/specs/agent-issue-loop/spec.md` and `tools/loop/report.sh`, and its only
matches under `guidelines/` are `report.sh`'s other flags (`Logging.md`, `BuildGate.md`), which this change does not
move. So the delta added below is the edit the rule owes, and `tools/check-doc-values.sh` and
`tools/check-doc-routes.sh` owe nothing — there is no guideline value or routing row for a header line that did not
exist before.

## Open decisions

none
