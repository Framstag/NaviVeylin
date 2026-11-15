# Proposal — a second read is a measured bound with an arm

**This change describes work that has already landed.** The whole of it is commit `36ed407b` (its ref decor being
`pi-subagents/925-2a3ba89-3ecc-s0-t0`), merged as `07694555` and carried by the base `929b9e8f` this delta is written
on. The commit carries the change's three cases, its eleven mutation records and its evidence, and no suite line —
`evidence/gate.txt` states that rather than quoting a verdict the tree does not hold. Its subject line is as the tree
has it (`fix: the-repeat-read-rule-is-a-measured-bound-wit (#925)`), a truncated title kept rather than rewritten.

## Why

**Root cause.** The rule "a file you have read once is cited, not read again" was prose in the worker's prompt
(`tools/loop/prompts/worker.md`, the paragraph now at `:116-120`), with the measurement that earned it — issue #246,
8 to 49 of every item's 22 to 79 file reads a second read — and nothing counted it and nothing enforced it. A prose
rule is not a bound: a second read costs a turn, the turn costs a cache read of a transcript that already holds the
text, and the item that repeats most is the item that stops at its bound. The advisory is also why a smaller
tool-result cap was rejected: the rule that fills the gap with a re-read is what the cap made necessary.

**Evidence, from the item (issue #925, read 2026-11-15).** Over the run behind the issue the children issued 2 987
reads of which **2 167 (72.5 %)** were a second read of a path the item had already read; `#872` alone read four
paths 283 to 295 times each across 226 turns and 2 914 calls — 12.89 calls per turn against the run's 2.24 — and
ended at `LOOP_ITEM_CONTEXT_MAX=200000`. The run's read census: 3 401 125 characters of tool result, 251 017 withheld
by the 4 000-character trim.

**The single fix, as it landed.** (All line anchors are in `evidence/anchors.txt`, read from the base tree.)
`tools/pi-trim-result.mjs` counts every path it handed a child whole (`repeatReadResult`, `:222`) and appends
`LOOP-TOOL-RESULT-REPEAT-READ: <tool> turn=<n> arm=<arm>` to the record beside the child's stream
(`repeatReadRecordLine`, `:122`); the arm is `LOOP_REPEAT_READ=measure|refuse` in the configuration's project block
(`tools/loop/loop.conf:482`), read by `tools/pi-child-events.sh:100` and exported into every child (`:103`), never
sourced — the engine reads no such key, because it is this project's adapter that applies it (D16). `measure` serves
the read and counts it; `refuse` answers it with one line naming the path and the earlier result's range
(`repeatReadNotice`, `pi-trim-result.mjs:201`), the size and the call that reads a range of it, and no text; an absent
key or any word that is not an arm is `measure` (`recordedRepeatArm`, `:93`). A read of a path **this child** was
never handed is served under either arm (`:241`), and a rule that cannot be applied returns the text rather than
failing the read (`:243-245`).

**The line and the arm answer different questions.** The arm stands ahead of the ledger's content test: a path this
child was handed and then edited is not handed over whole under `refuse`, while a path the item's ledger holds and
this child was never handed is answered by that ledger as it was. That is the half the shipped requirement for the
item-wide ledger (`openspec/specs/agent-issue-loop/spec.md:4816`) did not say, and the delta modifies it to say so.

**The count is a measured number, not a sentence's hope.** `tools/loop/state.sh` reads the lines into
`metrics.repeatReads` (`state.sh:1413`, `:968`) — `null`, never `0`, for a directory that holds no record (`:1429`),
and `0` for a record read and holding no second read. `tools/loop/report.sh` prints it in the `reads:` line beside
`dupReads`, never as that figure: `N second reads counted by the repeat-read rule`, `the rule counted no second read`,
or `the repeat-read count is unrecorded — this state predates the figure` (`report.sh:539-548`, joined into the line
at `:575`). `dupReads` counts a repeat a *session* had; `repeatReads` counts one a *child* was handed, and the two
differ where a child other than the first reads a path.

**The red tests.** Three cases — `repeat#aRepeatReadIsCountedAndTheRefusalArmWithholdsIt`
(`tools/loop/tests/loop-cases.sh`, registered `:457`, dispatched `:1198`),
`adapter#theChildIsStartedWithTheRepeatReadArm` (`:343`, `:1088`) and `report#theRuleRecordsTheSecondReadsItCounted`
(`:381`, `:1126`) — and eleven mutation records `925.1`–`925.11` (`tools/loop/tests/loop-mutations.sh:1325-1335`), one
per half: the arm that withholds, the line that counts, the never-handed guard, the earlier result the notice cites,
the arm's own name, the adapter's export, the fallback to `measure`, the record the count is read from, the anchor,
the null-not-zero rule and the trend print.

**Owning requirements.** Two. The ledger's requirement, `spec.md:4816` "The repeat rule's ledger is the item's, not
the child's", is modified — the arm's answer stands ahead of its content test, and a path this child was never handed
is unchanged. The counting rule is added: no shipped requirement owned a count, and the shipped sentence that says the
figure "SHALL be a warning: no key bounds it and no item's outcome reads it" (`:4116`) is about a figure nothing
counted, which this change does not contradict — the arm decides an answer, never whether the read counts.

**Owning guideline section.** `guidelines/Backlog.recipes.md:221`, corrected from "no key gates it" to the arm the
adapter reads.

## Surface

surface: 9 files, +450 −21 (`36ed407b`'s own diffstat, in `evidence/surface.txt`): `tools/pi-trim-result.mjs` +113,
`tools/loop/tests/loop-cases.sh` +204, `tools/loop/state.sh` +46, `tools/loop/report.sh` +27,
`guidelines/Backlog.recipes.md` +24, `tools/loop/tests/loop-mutations.sh` +20, `tools/loop/loop.conf` +19,
`tools/pi-child-events.sh` +13, `tools/loop/prompts/worker.md` +5 −1. One key, one counting path in the adapter, one
record field, one report clause, three cases, eleven mutation records, one modified and one added requirement.

## What Changes

1. **`LOOP_REPEAT_READ`** (`loop.conf:482`): the arm, in the project block, read and never sourced, `measure` the
   recorded value and every unrecognised value.
2. **The adapter** (`pi-child-events.sh:100-103`): the value the file records, exported into every child.
3. **The counting rule** (`pi-trim-result.mjs:93-250`): a second read of a path this child was handed counted every
   time, served under `measure` and answered with a notice under `refuse`, with the earlier result's range, and a path
   this child never held served under either arm.
4. **The record and the report**: `metrics.repeatReads` (`null` for no record, `0` for no second read) and the
   `reads:` line's clause beside `dupReads`.
5. **The three cases, the eleven mutation records, the guide-line correction and the two deltas** as above.

## What this does not do

- It does not count a path a child was handed *in part*. The ledger records what the child was handed whole; a read
  that arrived cut (the `LOOP_TOOL_RESULT_MAX_CHARS` trim) is not the file, and the rule does not claim it.
- It does not read an item's outcome. No item's result, class or streak depends on `repeatReads`; the figure is a
  measurement, and the shipped warning clause about a figure nothing gates stays true of it.
- It does not change the trim. `LOOP_TOOL_RESULT_MAX_CHARS=0` turns this rule off with the bound, and the cap itself
  is untouched.
- It does not re-measure the run the issue cites: the 2 167 of 2 987 reads and `#872`'s 2 914 calls over 226 turns
  are the item's evidence, quoted, and the run's own states are not in this worktree's tree.