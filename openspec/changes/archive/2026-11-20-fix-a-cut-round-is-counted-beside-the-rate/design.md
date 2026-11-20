# Design — the count of cut rounds, printed beside the rate

## The root cause, one sentence

The trend's rate is derived from four fields of the item's record — the verdict, the round count, the wall time and
the gate bucket — and from none of the scale the round's own input bound wrote, so a round that read the head of its
diff and one that read all of it are the same figure (`tools/loop/report.sh:511`–`:523`).

## The fix, one line of jq and one `def`

`$it` gains a `cut` flag per item — `((.metrics.reviewInput // "") != "")` — the count over it is bound once
(`| ([$it[] | select(.cut)] | length) as $cut`), and `def cut_note` renders it: the empty string at `0`, and
` (N truncated)` otherwise. The rate's own string interpolates `\($cut | cut_note)` between `first rounds passed` and
the undecided bucket, which leaves every other figure of the block where it was.

Nothing else in the tree moves: `run.sh` writes the scale (#924), `state.sh` records it (`:1033`), and the report's
per-item line prints it (`:365`). This change reads the field the two already agree on.

## Blast radius

- **The rate's text.** The one block at `report.sh:506`–`:523` (a comment, a `def`, one field on `$it`, one
  binding, one interpolation) and no other output. A run with no cut round prints the byte-identical line it printed
  before, which is what the case's second half asserts and what keeps
  `report#trendFiguresFromState` (`loop-cases.sh:15562`) and `run#aBlockedItemRecordsTheRoundThatReadIt`
  (`loop-cases.sh:12506`) green without an edit: neither of their states records a scale.
- **The bucket the count is not.** `cut` is not a fifth bucket: an item is undecided, never reviewed, a refusal or a
  pass exactly as it was, and the count is printed beside the rate rather than inside its numerator or denominator.
  A round that passed on a cut input is still a first-round pass — the reader is told what it was a pass over.
- **A state written before `metrics.reviewInput` existed.** No item carries the key, `$cut` is 0, and the line is the
  one that state always printed. That is deliberate: `0 truncated` for such a state would be a claim that no round
  was cut, which the state cannot make. The per-item line (`:365`) already reads the same way (`none`).
- **No compiled file** is touched, so no Gradle build is owed and no gate is this session's (`tools/loop/**`'s gate
  is `tools/loop/tests/loop-selftest.sh`, and the run has deferred it to the gate child).

## Rollback

Revert the hunks in `tools/loop/report.sh`, the case, the mutation record and the
`guidelines/Backlog.recipes.md:1808` paragraph. Nothing in
the record changes, so a reverted tree reads every state this one wrote exactly as it did before, and no state has to
be migrated in either direction — the delta is a rendering of a field the state already carries.

## Alternatives the evidence eliminated

- **Make the cut a fifth bucket in the rate's buckets** (`5/5 · 6 truncated · 1 undecided · 2 never reviewed`). It
  reads as if the six were a fourth outcome of a round, when a cut is a property of the input to a round that still
  ended in a verdict. Issue #936 writes the shape it asks for as `5/5 (6 truncated)`: the count qualifies the rate
  rather than adding to it.
- **Count only the cut rounds that passed** (the parenthetical over the numerator alone). The issue's own figure is
  `6` against a `5/5` — more cut rounds than first-round passes — because the figure a reader needs is how much of the
  run was read through a keyhole, not only how much of the numerator. A count restricted to the numerator answers a
  different question, and the one the run asked is the run's.
- **Re-derive the scale from the round's `<n>.cut.md` on disk** instead of the record. The report is built from the
  state file and git and reads no item directory (`report#landingsAreTheStoredNumber` holds that rule); the record
  already carries the scale, and a second reading of the same fact is the defect `#246` measured.
- **Name the bytes and the share in the rate line** (`5/5 (6 truncated, and the bytes they cut)`). The per-item line
  (`:365`) already carries both, and the trend block's lines are read at a glance; a second, summed share in the rate
  would be a new arithmetic over fields nobody re-measures, with no case to keep it honest.
