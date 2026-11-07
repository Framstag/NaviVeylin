# Design — the record carries the bounds its numbers were measured against

## The root cause

A run's state names what it was given — items, minutes, review rounds, decision ceiling, base — and not the bounds
its children's numbers are read against. Three of those bounds are in `tools/loop/loop.conf` and each one is the
second half of a comparison the state already makes:

| the state already records | read against (`loop.conf`) | where the bound is applied |
|---|---|---|
| `items[].metrics.context` (the largest context a turn reported) | `LOOP_ITEM_CONTEXT_WARN` / `LOOP_ITEM_CONTEXT_MAX` | `run.sh:198-200` reads both, and the stop is what ends a child |
| an item's tool results, and the trims recorded in `items/<n>/tool-result-trims.log` | `LOOP_TOOL_RESULT_MAX_CHARS` | `pi-child-events.sh:86`, the adapter |

The file is where a person changes a bound — for this item's own experiment, the one line `loop.conf:372` — and the
state is what survives. Until now a reader who asked "which cap was this run cut at" had to trust the file at the
moment of reading, which is a different tree from the one the run measured: after the experiment the line goes back
to 8 000 and the archived state says nothing. The number the children actually get is the *file's*, twice over —
`pi-child-events.sh:86` re-reads `tools/loop/loop.conf` and exports it into the child it starts, and `loop_resolve`
sources the same file — so the record can carry it without a second home. It must be read from the file and not from
the shell variable: a worker child carries `LOOP_TOOL_RESULT_MAX_CHARS=8000` in its own environment (measured
2026-11-07 in this item's own session, `env | grep '^LOOP_'`), so a run started from inside a child would report the
parent's bound through `loop_conf_value` even where the configuration records none.

## The single fix

Read the three keys once, at `state.sh init`, from the configuration file the run resolved (`LOOP_CONF_PATH`) — the
same read `pi-child-events.sh:86` and `gate-attribute.sh:66` make of the keys they apply, "read, never sourced" —
and write them into `.run`:

- `toolResultMaxChars` — the character bound an item's results were cut at;
- `itemContextWarn` — the context at which the run prints `LOOP-CONTEXT:` and ends nothing;
- `itemContextMax` — the context that ends a child.

Each is the number the configuration records, and `0` when it records none — the value every reader of these keys
already treats as "no bound", not a second vocabulary. Both readers name them: `state.sh show` on a `bounds` line
beside `budget`/`flags`, and `report.sh` in the header its table is read with, `?` where a state predates the fields
(absent is not zero — the rule the spend columns already follow).

Nothing else is derived from the bound: the run does not flag the items above the warning (`report.sh:149-162`
already does that for tokens, and the context rate is #809's), and it does not end anything at a new number. The
change is one fact recorded and two readers naming it.

## Blast radius

- `tools/loop/state.sh` — `.run` gains three keys; `init` gains one printed line; `show` gains one.
  `stateDigest` (`state.sh`, D5) is a digest of whatever the state holds, so it moves with the shape and no stored
  value depends on it.
- `tools/loop/report.sh` — one `printf`, no column added to the table and no column renamed: the rule that the
  columns a reader already quoted keep their name and their order (`spec.md:111`) is untouched.
- `tools/loop-cases.sh` — one case and its two registrations (`LOOP_SHELL_CASES`, the dispatch).
- `tools/loop-mutations.sh` — one record.
- `guidelines/Backlog.recipes.md`:878 "Leftovers, the report and the queue" — one paragraph, in the passage that owns what the report names.
- `openspec/specs/agent-issue-loop/spec.md` — one requirement, one scenario, one case.

A state written before this change keeps reading: `.run.toolResultMaxChars` is absent, the `?` prints it as
unrecorded, and nothing reads the key as a bound.

## Rollback

Revert the commit. No state migration, no flag, no default moved: an older state read by the older `state.sh show`
loses one line and gains nothing it depended on. `tools/loop-mutations.sh --only 95.1` is the falsification of the
case, and `git revert` of the landing commit returns `tools/loop/state.sh`, `tools/loop/report.sh`,
`tools/loop-cases.sh`, `tools/loop-mutations.sh` and the guideline to their base text.

## Alternatives the evidence eliminated

1. **Move the cap (4 000, or `LOOP_SPEND_CAP4K`'s 27 % of the spend).** Rejected: `tools/loop/loop.conf:359-361`
   prices the three caps over the same archive and the item names the tension — a 4 000-character window invites the
   re-reads 45 % of the archive's reads already are (`.pi/issue-loop/loop.conf:366-367`, `LOOP-SPEND-READS
   duplicate=2174`), and the number that would settle it (`metrics.dupReads`, #814) does not exist yet. A change that
   moved the number would be a decision the item says is not taken.
2. **Honour an inherited `LOOP_TOOL_RESULT_MAX_CHARS` over the recorded key.** Rejected as a second mechanism: it
   would give the bound two sources that disagree exactly where the difference matters — the adapter re-reads the
   file whatever the environment says (`pi-child-events.sh:86`) — and it does not answer the question, because the
   experiment would still need its outcome recorded to be comparable. The fix makes the configuration's own number
   visible in the record, which is also what makes a run at 4 000 distinguishable from one at 8 000 when nobody wrote
   the experiment down.
3. **A per-item `metrics.trimBound`.** Rejected as duplication: one run starts every child of every item under one
   adapter and one file, so a copy per item is N places for the same number to drift and no reader that needs it.
4. **A per-item count of trims, or of results over the bound.** Rejected as a different item: it is a measurement of
   spend (#814/#809), not the bound a figure is read against, and nothing in this delta would make it decidable.
