# Proposal — a repeat read says what the rule withheld, not what it re-injected

## Why

**Root cause.** `tools/pi-trim-result.mjs`'s `repeatResult` (`:126`) replaces a read whose content a session already
holds with the one line `alreadyReadNotice` (`:114`) returns, and writes **no record** of having done it — only
`trimResult` (`:82`) appends to the record its caller passes (`:64`, `trimRecordLine`). So the item's own evidence
(`tool-result-trims.log`, named beside the child's stream by `tools/pi-child-events.sh:121`) holds the trims and not the
repeats, and the one reader that could relate the two, `tools/loop/report.sh`'s trend block (`:415`–`:447`), labelled the
**raw** repeat volume it reads from the event stream `chars re-injected` — the word is gone from the printed line, and the
block comment at `:423` says why the two halves are kept apart. Those characters are either material
the child never saw (the rule withheld them) or material it did (the rule was off, or the stream predates it), and the
report cannot tell the two apart.

**Evidence, in this tree today.** The figures the item quotes are the state's, not the report's: `metrics.dupReads` and
`metrics.dupChars` are summed by `tools/loop/state.sh`'s `child_usage_json` (`:1131`–`:1132`) from the event
stream's read ledger alone — the results as `tool_execution_end` records them, which is the tool's answer and not what
the context was handed. Nothing in `state.sh` opens the trim record, so `metrics` has no figure for the withheld half,
and the report's `reads:` line printed the raw total as `re-injected`. The record the rule would have to write is the one it already
writes for a trim: the same file, one more line shape (`repeat#aRepeatIsRecordedAsWhatItWithheld`).

**The defect's probe on this run's own record.** `bash .pi/issue-loop/items/831/probe-repeat-record.sh` →
`trims=82` and `repeats=0` over `.pi/issue-loop/items/808/tool-result-trims.log`; the output is archived as
`evidence/probe-repeat-record.log`. The
file the adapter names beside that item's stream holds the 82 trims the bound wrote and not one line for the repeats the
rule replaced, with the repeat rule on.

**The red test on the base** (`.pi/issue-loop/items/831/red-on-HEAD-aRepeatIsRecordedAsWhatItWithheld.xml`, with its
sibling for the second case): the rule's own record holds
no line for a repeat — `LOOP-TOOL-RESULT-REPEAT` is in no production file of the base `d5d2e668` — and the item's
`metrics` has no `withheld` figure, so a report that called the raw volume `re-injected` said it about characters the
rule had already replaced with one line each. The item's own observed report line is quoted, with its source, in
`evidence/item-observed-report-line.md`.

**The single fix.** The rule records what it withheld: `repeatResult` appends
`LOOP-TOOL-RESULT-REPEAT: <tool> chars=<n> kept=<k>` to the record it is already given, and `state.sh`'s
`child_usage_json` reads that record beside the stream it was already opening and records the sum as
`metrics.withheld` (`{reads, chars}`, `null` for an item whose directory holds no repeat record at all — a record that
was read and holds no repeat line says `0`, which is a measurement, and the two must not print the same), and the
report's `reads:` line then states both halves — the characters in the repeat results, and how
many of those the rule withheld — and stops calling the raw volume `re-injected`.

**Owning capability and guideline.** `openspec/specs/agent-issue-loop/spec.md`, "A run counts the reads its sessions
repeated" (`:3952`); the behaviour's prose is `guidelines/Backlog.recipes.md` section 7a (the result bound and the repeat rule,
`:172`–`:190`) and section 7e (the report's trend line, `:1285`–`:1287`).

surface: 1 case added, 1 case rewritten, 2 rule documents (the spec requirement and the two guideline sections), 2 code
sites plus 1 record site — `./tools/look.sh 're-injected'` → 4 matches in `tools/loop/tests/loop-cases.sh`, 2 in
`tools/loop/tests/loop-mutations.sh`, 1 in `guidelines/Backlog.recipes.md`, 1 in `openspec/specs/agent-issue-loop/spec.md`,
1 in `tools/loop/report.sh` and 1 in `tools/pi-trim-result.mjs` — the word is in the tree's prose and in the mutation
records, and in no line the report prints (the archive's own copies are not the rule).
