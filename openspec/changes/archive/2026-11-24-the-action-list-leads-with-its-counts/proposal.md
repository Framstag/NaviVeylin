# Proposal: the-action-list-leads-with-its-counts

## Why

`report.sh`'s "actions the caller owes" is a numbered list of every action the run could not finish, each carrying the
whole reason string the state records. The reasons are the run's own prose — two or three clauses each in a bad run —
so the list is where the report's length is, and the reader meets it in the order the run happened to record it rather
than in the order of what it costs.

- **Observed** ✗: the run started 2026-11-18T22:18:25Z printed **19 numbered actions for 11 items** — 7 decisions,
  9 branches to land, 4 resumes, 1 worktree note — about **4.4 kB of the report's 79 lines**, and the four resumes
  are the same four items whose branch the list already names two lines above them.
- **Measured** ⊨: the list's own items are countable by class (decide / land / resume / worktree), which is what a
  caller acts on first: "9 branches unlanded" is a decision about a batch, and the 9 lines are the batch's detail.
- **Why it matters** ⊕: this report is the loop's own hand-off, and a caller who stops reading it at line 40 of 79 has
  not seen the landings at all.

## What Changes

- **`tools/loop/report.sh` prints the counts first**, one line per class with the count and the first path or issue —
  `actions the caller owes: decide 7 · land 9 · resume 4 · worktree 1` — and then the numbered list, unchanged.
  Nothing is dropped and no reason is shortened.
- **`report.sh --actions`** prints the numbered list alone, for a caller who wants it without the rest of the report
  (the shape `--json` already has for a machine reader).
- **`tools/loop/tests/loop-cases.sh`: two cases** — a state with actions of three classes prints the counts line with
  the three figures, and `--actions` prints the list and no header.

## Evidence

- The analysed run's own list: 19 actions, 7 `decide #N`, 9 `land fix/issue-…`, 4 `resume #N`, 1 `keep or remove the
  worktree`; the resumes repeat the branch of the land line above them, in the state's own wording.
- The list is one function in `report.sh` (`decisions`), so the change is a header and one flag.
- The red proof, taken before the change: with the base's own `report.sh` restored, both cases fail —
  `evidence/red-on-HEAD-counts.log` (`the header's decide figure is the number of decide rows the list prints:
  expected '1', got ''`) and `evidence/red-on-HEAD-actions.log` (`loop: report: unknown argument --actions`); with
  the change in the tree, `evidence/green-counts.log` and `evidence/green-actions.log` are the two cases passing.

surface: 1 header line, 1 flag, 2 cases — `tools/look.sh 'actions the caller owes' --path tools/loop/report.sh` and
`tools/look.sh 'decisions' --path tools/loop/report.sh`
