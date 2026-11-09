# Design — the model is read where the stream is read, and printed where the state is read

## Root cause

`child_usage_json` (`tools/loop/state.sh:863`–`:1037`) already slurps the item's whole event stream once and folds it
into one object. Its `sessions` pass (`:892`–`:916`) reduces each `turn_end` into `{usage, ms, calls}` — the usage, the
`durationMs` and the `toolCall` blocks — and the `roles` reduction (`:927`–`:933`) sums each session's turns under the
role its prompt path names. The `model` that sits beside those same three fields on the same `turn_end` message
(`:903`–`:915`) is never read, so the state carries a role's tokens and no subject for them, and `report.sh` has
nothing to print.

## The single fix

Extend that one reader and the one report line that prints it. Nothing else in the run changes.

1. **`child_usage_json` reads the model a turn answered with.** The `turn_end` handler keeps
   `model: (responseModel // model // null)` — the concrete model when the provider reported one, else the requested
   one, else null — beside the usage, duration and calls it already copies from `$m`. A new `roleModels` pass, beside
   the existing `roles` pass (`:939`–`:951`), folds each session's modelled turns into a map of model to the tokens
   that model's turns spent, keyed the same way `roles` is (`worker-tail` → `tail`, `reviewer` → `reviewer`, else
   `worker`), summing two sessions of one role rather than overwriting them. The whole field is `null` when no turn
   named a model — never zero, the rule the function's header states for every figure.
2. **The `roles:` line reads `role (model) tokens`.** `report.sh` (`:457`–`:489`) prints one row per `(role, model)`
   tally when at least one item recorded a model, in the role order the line already uses (`worker`, `reviewer`,
   `tail`, each model by name), and keeps the role totals as the total the line is over. The tokens of a role no model
   explained are printed as a row of the role alone, because a row set that dropped them would no longer add up to the
   total it is printed over — and the item's own proposal says exactly that such a role needs no label. A tally of
   zero prints no row: a turn that named a model and spent nothing is not a measurement. A state that records no model
   at all — an older harness, a fixture, a state written before the field existed — falls through to the line it
   always printed, so nothing an archive already reports is rewritten.

The field lives **beside** `metrics.roles`, not inside it. `metrics.roles.<role>` stays the number it has always
been, because the item's own evidence says the existing case `run#aBlockedItemRecordsTheRoundThatReadIt` "pins
`metrics.roles.reviewer` and would pin a model beside it", and because the report's own convention for a figure added
later is a field that is absent where it predates the change — the shape `afterGate`, `phases` and `dupReads` took.

## Open decisions

none

## Blast radius

- The recording is one added pass in `state.sh` and one added field in the object `child_usage_json` returns; the
  report is one arm of the `roles:` line and no format-string change to the arms beside it.
- Every existing figure is byte-identical: `tokens`, `context`, `calls`, `turns`, `tools`, `cost`, `phases`, `roles`,
  `afterGate`, `reads` and `dupReads` are computed by the passes they were computed by before, and the new pass reads
  the same events without writing to them.
- `report.sh --json` dumps `.items` from the state (`tools/loop/report.sh:73`), so the field reaches the JSON
  read with no second path to keep in step.
- Rollback: delete the `model` key from the `turn_end` handler, the `$roleModels` pass, its output field and its
  fallback string, and restore the `roles:` line's three arms. The state schema loses one field it only added; a
  report read against a state written by the newer tree prints the role totals, because the model arm is gated on
  `roleModels` being present.

## The alternatives the evidence eliminated

- **Reshaping `metrics.roles` into `role → {tokens, models}`.** Eliminated by the requirement the item states — "a
  state written before this change still prints its role totals and names no model" — and by the cases that pin
  `metrics.roles.reviewer` as a number (`report#theSpendIsSplitByPhaseRoleAndGate`,
  `run#aBlockedItemRecordsTheRoundThatReadIt`); a reshape moves every one of them and every archived state for a
  figure that is *beside* the total, not inside it.
- **Reading the model from `LOOP_AGENT_CMD_*` (the configuration).** Eliminated by the item's own third proposal: the
  command a later run reads says nothing about what an archived item ran on, and a role left on the default carries
  no key at all, so the record would name the model a project *meant* rather than the one the provider answered with.
- **`responseModel` alone, or `model` alone.** Eliminated by the stream: `responseModel` is present only when the
  provider reports a concrete model, and `model` is present on every assistant message, so the turn's own reading is
  the concrete one when it exists and the requested one otherwise — a fallback, not a choice.
- **A per-item table column.** Eliminated by the number of items: the question the item asks is about an archived
  item's own record, which the state holds, and the trend line is where a run's roles are already read.
