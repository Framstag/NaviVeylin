# Design — fix-blocked-item-names-its-stop-mechanism

## Root cause

`state.sh done` writes one record per item and has no field for *why* a block was a block: `result` is one of
four words (`done|skipped|blocked|falsified`, `tools/loop/state.sh:436`) and `verdict` is the worker's or the
escalation's word, so both mechanism sites in `tools/loop/run.sh` — `:1467` (the last review round refused) and
`:1500` (the base would not take the branch) — reach the record as the same `blocked`. The distinguishing fact
lives only in `note`, prose written for a person, which is why the item's own measurement had to parse note text
by substring (`probe-tokens.py`) to split 198.8 M from 22.9 M.

## The single fix

One field, one vocabulary, one column:

- `.items[].stopReason` — `refused` | `unmergeable` | `null`, written only by `state.sh done --stop-reason`,
  refused outside that vocabulary and on any `--result` other than `blocked`. A block neither mechanism caused
  records `null`, not `""`: `jq`'s `//` yields the empty string for what is already empty, so an empty-string
  record would print an empty cell where the scenario promises `-`.
- `run.sh` names the mechanism at the two sites that already produce a mechanism block and passes it to the
  blocked `done` call; every other block passes nothing.
- `state.sh report` appends one column, `stop`, after `reason` — appended, never interleaved, so a reader that
  quoted the fourteen earlier columns keeps them at their index. A record that carries no mechanism prints `-`
  there, and the check is on the empty string rather than on `//`, because `//` substitutes only for `null` and
  yields `""` for an empty one. `report.sh` and `report.sh --json` are unchanged and carry the field through
  `.items`.

The engine's own rule that a second account of one item adds its note and replaces nothing
(`tools/loop/state.sh:452-473`, issue #284) is kept as it stands: the mechanism belongs to the attempt whose
metrics the row carries, so a later account does not overwrite it and does not fill an absent one.

## Blast radius

Two engine scripts (`state.sh`, `run.sh`), the loop's shell-case file, its mutation file, one guideline
paragraph and the spec delta. No fixture, no prompt, no profile, no app source, no build logic. The state file's
schema gains an optional field, so a state written before this change reads exactly as before (`-` in the new
column) — the readers are `jq` expressions over `.items`, and one more key is invisible to them.

## Rollback

Revert this change's commit: `--stop-reason` disappears from `done`, the `stop` column from the table, the two
`stop_reason=` assignments from `run.sh`, and the four cases return to `1 of 1 cases failed` each
(`evidence/red-cases-280.sh`). No state file in any archive is rewritten by either landing or reverting it.

## Alternatives the evidence eliminated

- **End the item at the refusing round unless the report is `LOOP-CORRECTIONS-ONLY: yes`** (the item's option 2) —
  eliminated by the archive: 36 of 49 closures (73 %) passed in a round after the first (28 at round 2, 8 at
  round 3, `measurement.txt`), so the arm saves the ~198.8 M by removing the round most deliveries arrive in.
- **Replay a branch whose base moved instead of blocking** (option 3) — eliminated twice: it reverses the shipped
  requirement *A branch the base does not descend from is not replayed*
  (`openspec/specs/agent-issue-loop/spec.md:1272-1278`, case `worktree#refusesToReplayABranchTheBaseDoesNotDescendFrom`),
  and it closes an item on a base nobody reviewed; the single attempt it would save is 22.9 M of 1154.9 M.
- **Bound what the round after a refusal may spend** (option 4) — eliminated as a second, unmeasured policy
  question: no archive figure says what a token or wall bound would cut, and the item's record cannot supply one
  until the mechanism is recorded, which is this change.
- **Rename the `verdict` instead of adding a field** (`blocked:refused`) — eliminated because `verdict` is the
  worker's or the escalation's word and feeds the label vocabulary (`--decision-label`, `report.sh`'s decision
  list) and the skip verdicts; a mechanism is the run's own fact about the record, not a verdict an escalate
  writes.
- **Leave the record alone and keep reading the note's prose** (the state today) — eliminated because it is what
  the item reports: the attribution exists only while someone maintains two string constants and a probe
  outside the tree.

## Correction round 1 (2026-10-25)

The review of round 1 refused the change on two defects, both of them the item's own subject — a scenario whose
THEN was not asserted:

1. **The stop column printed an empty cell, not `-`.** `cmd_done` wrote `stopReason:$sr` with `""` as the default,
   so a closure and a mechanism-less block stored `""`; the row interpolated `(.stopReason // "-")`, and `jq`'s
   `//` returns the empty string for an empty one. The case passed because its assertion, `| done | - |`, matched
   the *reason* column, which prints `-` for every closure (`state.sh:588`). Fixed by recording `null` (the
   round-1 report of a state written since) and rendering `-` for both `null` and `""`; asserted by reading
   column 15 of the row itself — on the closure, on a block with a mechanism, and on a block without one.
2. **`the state is not written` was asserted nowhere.** `state#aBlockedItemNamesTheMechanismThatStoppedIt` read
   the exit status of both usage errors and never the state. Fixed by comparing the state file byte for byte
   before and after each refusal, and by recording a second account of an item to show it fills no mechanism the
   first record left empty (the rule `state.sh`'s second-account arm states and no case covered).

The mutation records moved with the code: `58.3` now nulls the record's mechanism, `58.4` makes the column always
print `-`, and `58.5` makes it print an empty cell — the round-1 `58.4` mutated the whole interpolation and
produced an invalid jq escape (`\-`), so the report died and the case failed for a reason its own name did not
say. Every record's failing assertion is quoted in `evidence/revert-check-round2-messages.log`.

## Where the rule is stated

- `openspec/specs/agent-issue-loop/spec.md` — the new requirement and its four scenarios (delta in this change).
- `guidelines/Backlog.md` §7 "The loop" — the report paragraph, which says what the per-item table answers.
