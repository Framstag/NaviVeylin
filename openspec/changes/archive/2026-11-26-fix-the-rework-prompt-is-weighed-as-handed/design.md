# Design

## Root cause

The bound is measured on the file the run has rendered; the prompt the child is handed is that file plus an append
whose text is the reviewer's whole report. Two files, one number: the check can only ever report the smaller one, so
the largest prompt the run composes (42 409 B on #1080 against a 29 000 B bound) was the one no line could name. The
append also re-sends the report's prose with every turn of the rework session, which is the cost the bound exists to
make visible.

## The single fix

The append carries what a rework pass can act on — the `BLOCKING:` lines, the vocabulary the round after a refusal is
owed by (#1085) — and names the report's own path, and the check runs after the append.

- `blocking_finding_lines` in `tools/loop/evidence.sh` is the one reading of that line shape; `blocking_findings`
  counts through it, so the grep pattern is not written a second time in `run.sh`.
- `run.sh` appends `findings` (that function's output) and `$reviewer_report` in one `printf`, then calls
  `worker_prompt_bound_check` — the order the case asserts.

## Blast radius

`worker_prompt_bound_check` itself is unchanged: it still reports on the first worker prompt (`run.sh:1234`) and still
changes no verdict. What moves is which file the rework call weighs and what the append carries. `blocking_findings`'
count is unchanged — it counts the same lines through one more call. Two registries (`layout.tsv`'s `owns` cell,
`shared.tsv`'s row) gain the new name; both are checked by the driver's structural cases, which the gate runs.

## Rollback

Revert the change directory's diff: `run.sh`'s two hunks, `evidence.sh`'s one, the two registry rows and the guideline
sentence. Nothing is recorded in state, no key is added, no event kind is emitted, so a revert lands on a tree whose
behaviour is HEAD's.

## Alternatives the evidence eliminated

- **Bound the appended report, keep sending it whole.** Two numbers where one rule is wanted, and the prose stays in
  every turn of the session; the item's own measurement (15 289 B of a 42 409 B prompt) is the prose, not the finding.
- **Move the check only, keep `findings=$(cat …)`.** Then the check fires on the report's whole text and the only
  repair it offers is "the report is large" — a line naming a number the run cannot change, at every refusal.
- **Put the extraction inline in `run.sh`.** A second copy of `^[[:space:]>*_]*BLOCKING:`, which is exactly what a
  later vocabulary edit (#1042's emphasis-wrapped line, #999's round 2) would leave behind.
