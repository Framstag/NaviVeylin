# Design — the withheld volume prints its denominator, and the comment says which repeats are not waste

## Root cause

Pointers to `report.sh` here are the base's `d25972c1` numbering; the clause this change writes sits 14 lines lower in
the tree it leaves (`tools/loop/report.sh:469`–`:479`).

`report.sh`'s `reads:` line is one `jq` program that reduces the state's items into `$dups`
(`tools/loop/report.sh:447`–`:450`), sums their four figures (`:456`–`:458`) and prints them. The rule's withheld volume
enters that program as `$wline` (`:461`–`:464`), a string built as `"\($w | add) chars withheld by the repeat rule"`
with no reference to `$tc`, the repeat results' own characters — so the printed line joins two independent volumes with
a `·` (`:465`). The comment above it (`:432`–`:446`) records why the share was dropped on purpose (`:443`): the withheld
figure is the item's ledger, which counts a repeat across two children (#858), so it is no share of `dupChars`, which
counts one session.

That is right about the arithmetic and wrong about the reader: the figures are the two halves of one question — how
much of the volume the rule saw did it withhold — and printed apart, the first reads as waste and the second as a
number nothing is a share of. The volume the first figure counts is not waste either, and nothing in the file says so:
a repeat is keyed `(($x.s | tostring) + "|" + $x.p)` (`tools/loop/state.sh:1221`), the session and the path and no range,
so a 786-line diff read as `lines:1-200`, `96-300`, `200-400`, `310-520`, `410-620`, `515-786` is five repeats of one path
whose windows carry text the session did not have. The state records no range (`:1210`–`:1219` keeps the path and the
result's length), so the record cannot tell the two apart and the report cannot either — which is exactly why the line
needs to say what it is counting, and why the lever it invites (a lower `LOOP_TOOL_RESULT_MAX_CHARS`, worth 27.5 % of the
last measured window in `tools/loop-spend.py`'s `capSaving`) is the wrong one.

## The single fix

One expression and one comment in `tools/loop/report.sh`. Nothing in the state, the rule or the run changes.

1. **`$wline` carries the share.** The branch that prints the volume (`:469`–`:473`) adds, in the same clause,
   `(<pct> % of the repeat results)`, where `<pct>` is `$w|add` over `$tc`, one decimal, the shape the line's own reads
   percentage uses (`:479`, `(43 %)`) at the precision a 0.2 % share needs. The record's `null` branch — an item with no
   repeat record, and a whole state that predates the field — is untouched, and so is the dash it prints.
2. **The same branch guards an empty denominator.** `$tc` is a sum over items that recorded repeats, and a repeat whose
   result held no characters (`tools/loop/state.sh:1213`–`:1217` sums text lengths) leaves it 0 while the withheld
   volume is not 0. Divided, that is a `jq` error and the report fails; so the clause prints the volume and the reason
   there is no share (`the repeat results hold no characters, so the withheld volume is no share of them`, `:476`) rather
   than a share of zero — the dash-and-its-reason rule every spend figure in this file keeps.
3. **The comment names the window and the share's bound.** `:432`–`:454` is rewritten: the withheld volume is printed as
   a share of the repeat results; the share may read above 100 % because the ledger is the item's; and a path read in
   windows of itself is counted as repeats, so the repeat volume is a count of reads the session already held the path
   of and not waste avoided. The base's sentences at `:438`–`:443` that refused the share, and called the two figures
   unrelated, go.

The share is over the same two figures the line already prints, so no new state key, no new command and no new number
taken by hand: `metrics.withheld.chars` and `metrics.dupChars` are both in the state the report already reads
(`tools/loop/state.sh:1257`–`:1258`).

## Alternative the evidence eliminates

**Count a repeat per range, so the windows of one path stop being repeats.** It is the tempting fix — it removes the
over-count instead of explaining it — and the record cannot pay for it: `state.sh` keeps `path` and the result's
character count and drops the tool call's `arguments` (`:1206`–`:1219`), and `pi-trim-result.mjs`'s ledger keys a read by
`sha <tab> turn <tab> size <tab> session <tab> path` and holds no range either, so both readers would have to be taught
a shape and every state and ledger written before them would be a record nobody can re-read. It is also a different
claim: a second read of the same whole text is what the rule replaces, and a window that repeats an earlier window's
text is a repeat whatever its range says. The item is report-only for that reason.

## Blast radius

- One expression and one comment block in `tools/loop/report.sh`; no state field, no run step, no configuration key.
- Every other figure of the line is byte-identical: `$td`, `$tr`, the reads percentage, `$tc` and the worst item are
  computed by the code that computed them before, and `$wline` is unchanged where the state records no withheld volume.
- The one case that asserts the line is rewritten where it moved, and one phase is added beside it for the empty
  denominator; the mutation record `96.2` still applies to the withheld clause it quotes, and a `96.4` is added for
  the share, because the case now fails if the percentage goes.
- The guideline's two statements of the clause gain the share and the window; the requirement gains its `AND` clause.
  An archive that already quotes the old wording is frozen history and is not edited.

## Rollback

Revert `tools/loop/report.sh`, `tools/loop/tests/loop-cases.sh`, `tools/loop/tests/loop-mutations.sh`,
`guidelines/Backlog.recipes.md` and this change's delta: the code is one expression inside a `jq` program and two
sentences of comment, so the line comes back byte for byte, and no state written in the meantime needs re-reading —
the fields were there before this change and the report only prints them differently.
