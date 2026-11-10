# Design — the repeat rule records what it withheld

## Root cause

`repeatResult` (`tools/pi-trim-result.mjs:126`) returns `alreadyReadNotice(...)` and appends nothing, while its sibling
`trimResult` (`:82`) appends `trimRecordLine` (`:64`) to the record. One rule of the pair is therefore an item's fact and
the other is the child's memory. `state.sh`'s `child_usage_json` sums the repeats from the event stream, and the stream
holds the tool's answer — the size the repeat result *had* — so `metrics.dupChars` is the raw volume and the report's
`re-injected` (the word the trend line used to print, kept in the block comment at `tools/loop/report.sh:423`) is a claim
about a context nobody measured.

## The single fix

1. `tools/pi-trim-result.mjs` — `repeatResult(text, read, bound, tool, record)`, and where it replaces the text it
   appends one line, best effort and never throwing, exactly as `trimResult` does:

   ```
   LOOP-TOOL-RESULT-REPEAT: <tool> chars=<the result's size> kept=<the notice's size>
   ```

   `chars - kept` is the withheld volume. The line carries no path: the path is in the notice the child is handed, and a
   record line that named one would be a coordinate in a log (the rule every `LOOP-TOOL-RESULT-*` line keeps). The line
   goes to the same record the adapter already names beside the item's stream, so it is a fact of the item, not of the
   child's memory.
2. `tools/loop/state.sh` — `child_usage_json` reads `$(dirname <events>)/tool-result-trims.log` (the sibling path
   `tools/pi-child-events.sh:121` names for the bound's record) and records `metrics.withheld` as
   `{reads, chars}`: the repeat lines' count and the sum of `chars - kept` floored at zero, and `null` when that file is
   absent or unreadable — a record that was read and holds no repeat line says `0`, which is a measurement, and the two
   must not print the same. The report prints `-` with its reason for the absent figure, as it does for
   every other absent figure.
3. `tools/loop/report.sh` — the trend's `reads:` line prints the two halves: the characters in the repeat results
   (`metrics.dupChars`, unchanged in meaning) and how many of those the repeat rule withheld (`metrics.withheld.chars`).
   `re-injected` leaves the report's **output**; the word stays in the tree's prose — `tools/loop/report.sh:423` (the
   comment that says why), `tools/loop/tests/loop-cases.sh`, `guidelines/Backlog.recipes.md:183` and
   `openspec/specs/agent-issue-loop/spec.md:3958` — and the withheld half prints `-` and its reason for a state that holds
   no such figure. The printed figure is the sum over the items whose state holds the field, so a state that mixes eras
   prints a lower bound.

## Blast radius

- `tools/pi-trim-result.mjs`: `repeatResult`'s signature and one append; `trimContent` (`:187`) and the CLI (`:231`) pass
  the tool and record they already hold. A repeat whose content changed still records nothing, because it is not one.
- `tools/loop/state.sh`: one awk read of a file beside the stream, and three fields (`withheld` in the item's `metrics`,
  `null` in the empty-usage fallback string).
- `tools/loop/report.sh`: the `reads:` line's text and one selection over the items that hold the figure.
- Cases: `report#anItemRecordsItsRepeatReads` rewritten (its items' streams and records move into per-item directories,
  because the read is the sibling of the stream), `repeat#aRepeatIsRecordedAsWhatItWithheld` added — and that new case is
  the one that carries the assertion that a read after an edit records no repeat line;
  `repeat#aRepeatAfterAnEditIsHandedOverWhole` is unchanged in what it asserts.
- Mutation records: `96.2` retargeted at the withheld clause (its old search text is the phrase this change deletes),
  `96.3` added for the rule's record line.
- Prose: `openspec/specs/agent-issue-loop/spec.md`'s "A run counts the reads its sessions repeated" gains the figure and
  its clauses; `guidelines/Backlog.recipes.md` sections 7a and 7e state the same two halves.

## Rollback

Revert the three files and the cases; a state that carries `metrics.withheld` is read by a report that does not know the
field, which is the shape every older report already handles. No key is added to `loop.conf`, no bound changes, and no
child's behaviour changes — the record is written where a record was already allowed to be written.

## Alternatives the evidence eliminated

- **Read the record in `report.sh`.** The report reads the state and git, never an item's files (`tools/loop/report.sh:4`–`:6`,
  D11); a figure read from a per-item file would be one the state does not hold and a second reader of the same run
  would disagree with it.
- **Count the repeats from the result the stream records.** The stream holds the tool's answer, not the text the
  context was handed (the item's own observation, and the reason `dupChars` is the raw volume); a harness that echoes a
  notice back would also be miscounted as a repeat.
- **Rename `dupChars` to the withheld volume.** A state written before the field would then print an old-era number under
  a new name — the one thing `report.sh` refuses to do with an absent figure (`:423`–`:426`).
