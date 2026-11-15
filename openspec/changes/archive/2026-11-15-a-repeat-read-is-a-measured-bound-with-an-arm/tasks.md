# Tasks — a second read is a measured bound with an arm

The items are ticked because the work is in the tree: this change was written after commit `36ed407b` landed, and
every task below names the path and line that carry it.

## 1. Measure before changing code

- [x] 1.1 Count the second reads the run's children made: 2 987 reads of which 2 167 (72.5 %) were a second read of a
  path the item already held; `#872` read four paths 283 to 295 times each over 226 turns and 2 914 calls — 12.89
  calls per turn against the run's 2.24 — and ended at `LOOP_ITEM_CONTEXT_MAX=200000` (issue #925).
- [x] 1.2 Read what the rule had been until then: a paragraph in `tools/loop/prompts/worker.md` (now `:116-120`) with
  issue #246's measurement behind it and nothing counting or enforcing it — a prose rule, not a bound.
- [x] 1.3 Read where the rule can be applied without the engine: the child's own adapter (`tools/pi-child-events.sh`
  starting `tools/pi-trim-result.mjs`), which is where a tool result is already trimmed (D16 — the engine reads no
  project key).

## 2. The arm

- [x] 2.1 `LOOP_REPEAT_READ=measure|refuse` in the configuration's project block (`tools/loop/loop.conf:482`), with
  the census this item cites and the reason `measure` is the recorded value stated beside it.
- [x] 2.2 Read, never sourced: `tools/pi-child-events.sh:100` reads the raw file with `sed` and exports the value into
  every child it starts (`:103`).
- [x] 2.3 `recordedRepeatArm` (`tools/pi-trim-result.mjs:93`) answers `refuse` on that word alone and `measure` for
  anything else, an absent key and an empty value included.

## 3. The count and the refusal

- [x] 3.1 A child's ledger of what it was handed whole, per child, keyed by path — the per-process Map the adapter
  keeps, so the rule is about what this child holds and not what its siblings or the item do.
- [x] 3.2 `repeatReadResult` (`pi-trim-result.mjs:222`) counts every second read and appends
  `LOOP-TOOL-RESULT-REPEAT-READ: <tool> turn=<n> arm=<arm>` to the record beside the child's stream
  (`repeatReadRecordLine`, `:122`).
- [x] 3.3 `measure` serves the read; `refuse` answers it with `repeatReadNotice` (`:201`) — the path, the earlier
  result's line range, its size and the call that reads a range of it — and no text.
- [x] 3.4 A path this child was never handed is served under either arm (`:241`), and a rule that cannot be applied
  returns the text rather than failing the read (`:243-245`).

## 4. The ledger's content test, and the arm ahead of it

- [x] 4.1 The arm's answer stands before the item ledger's content test: what `refuse` withholds is not handed over
  whole because the child edited the path, while a read of a path the item holds and this child does not is answered
  by that ledger as it was — the delta modifies `openspec/specs/agent-issue-loop/spec.md:4816` to say so.

## 5. The record and the report

- [x] 5.1 `tools/loop/state.sh` reads the lines as `metrics.repeatReads` (`state.sh:1413`, stored `:968`) — `0` for a
  record read and holding no second read, `null` and never zero for a directory that holds no record (`:1429`).
- [x] 5.2 `tools/loop/report.sh` prints the count in the `reads:` line beside `dupReads`, never as that figure
  (`:539-548`, joined at `:575`): `N second reads counted by the repeat-read rule`, `the rule counted no second read`,
  `the repeat-read count is unrecorded — this state predates the figure`.
- [x] 5.3 The two figures are kept apart in the code and in the prose: `dupReads` counts a repeat a *session* had,
  `repeatReads` one a *child* was handed.

## 6. The cases and the mutation records

- [x] 6.1 `repeat#aRepeatReadIsCountedAndTheRefusalArmWithholdsIt` (`tools/loop/tests/loop-cases.sh:1198`, registered
  `:457`): a read twice under `measure`, the same path edited and read under `refuse`, and a pair under a value the
  rule does not declare.
- [x] 6.2 `adapter#theChildIsStartedWithTheRepeatReadArm` (`:1088`, registered `:343`): the child is handed `refuse`,
  then `measure` for a word the rule does not declare, then `measure` for no key at all.
- [x] 6.3 `report#theRuleRecordsTheSecondReadsItCounted` (`:1126`, registered `:381`): `metrics.repeatReads` from the
  lines, `0` for a record that counted none, `null` for a directory holding no record, and the three report clauses.
- [x] 6.4 Mutation records `925.1`–`925.11` (`tools/loop/tests/loop-mutations.sh:1325-1335`), one per half: the arm
  that withholds, the line that counts, the never-handed guard, the earlier result the notice cites, the arm's name,
  the adapter's export, the fallback to `measure`, the record the count is read from, the anchor, the null-not-zero
  rule and the trend print.

## 7. The guideline and the deltas

- [x] 7.1 `guidelines/Backlog.recipes.md:221` corrected from "no key gates it" to the arm the adapter reads.
- [x] 7.2 Two deltas under `agent-issue-loop`: the ledger's requirement modified (the arm stands ahead of its content
  test), and the counting rule added — no shipped requirement owned a count.

## 8. Evidence

- [x] 8.1 `evidence/surface.txt` — the commit's own diffstat; `evidence/gate.txt` — why no suite verdict exists to
  quote for this commit, and what the commit does hold.
- [x] 8.2 `evidence/anchors.txt` — every anchor this change cites, as the command and its output read from the base
  tree.

## 9. What this change does not cover

- [x] 9.1 A path a child was handed *in part* is not counted: the ledger holds what a child held whole, and a read that
  arrived cut by `LOOP_TOOL_RESULT_MAX_CHARS` is not the file — the rule does not claim it.
- [x] 9.2 No item's outcome reads the figure: no result, class or streak depends on `metrics.repeatReads`, and the
  shipped warning clause about a figure nothing gates (`openspec/specs/agent-issue-loop/spec.md:4116`) stays true of it.
- [x] 9.3 The trim is untouched, and `LOOP_TOOL_RESULT_MAX_CHARS=0` turns this rule off with it.
- [x] 9.4 The run the item cites was not re-measured: the 2 167 of 2 987 reads and `#872`'s 2 914 calls are the item's
  evidence, quoted; the run's own states are not in this worktree's tree.