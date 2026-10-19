# fix-update-status-note

## Why

A comment-only `update.sh` call printed `update: #165 already carries  — no label write`: the note exists
to explain a *state* write that found its state in place, and a call that named no state has nothing to
explain. The status was empty and the note described a write the caller had never requested. Seen six
times in the closure pass of 2026-10-19 (#182).

## What changed

The note is printed only when the call named a state (`--status`, or `--label` of the state namespace).
Everything else — a comment, a body, a label — is silent on a successful write, as it was before.

## Spec

No delta: this is a report line, not a contract. The defect is that the line claimed something untrue.

## Cases and evidence

`update#commentOnlyReportsNoStatusWrite` — red on the previous `update.sh` (the note appears with an empty
status), green with the guard; mutation A1.5 removes the guard and turns it red.

- `evidence/green-on-fix.txt`, `evidence/revert-check.txt`, `evidence/gate.txt`

## Impact

`tools/issues/update.sh`, `tools/issue-tooling-cases.sh`, `tools/close-mutations.json`.
