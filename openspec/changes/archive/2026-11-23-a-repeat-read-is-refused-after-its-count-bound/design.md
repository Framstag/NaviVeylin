# Design: a-repeat-read-is-refused-after-its-count-bound

## The root cause the evidence leaves

`repeatReadResult` (`tools/pi-trim-result.mjs`) counts a second read and then answers it by the arm
`LOOP_REPEAT_READ`: `measure` serves the text, `refuse` serves one `[LOOP-REPEAT-READ: …]` line. Neither arm refuses
*something that keeps happening* — the same path is asked for again, and again, and the guard is a comparison of
paths, never a count of them. #943's 2 372 reads of one gate log under 245 turns are that shape: each refusal was a
turn, so the characters the rule withheld cost the run its context instead of saving tokens
(`items/943/events.jsonl`, `report.sh`'s `dup 2418/2457`).

## D1 — Where the count lives: the child's tally, not the item's ledger

The alternatives the evidence eliminated:

- **The item's ledger** (`LOOP_TOOL_RESULT_LEDGER`, `readLedger`/`writeLedger`) — impossible as the count's home. It
  answers "does the *item* hold this content" and is keyed by sha, not by delivery; #858 split the two maps on
  purpose. A per-item count refuses a gate child's **first** read of a path its worker asked for 2 372 times, which is
  the false refusal the shipped rule already guards against with `entry.handed`: the child holds nothing to quote.
- **A record the report reads back** (`LOOP-TOOL-RESULT-REPEAT-READ` lines) — impossible: those lines carry no path
  (a log line may carry no coordinate, `guidelines/Logging.md` §2), so no reader could reconstruct a count per path.

So the count is a field on the entry of this process's own tally (`child`, the map #925 shipped), which is the one
place a second read of one path by one child is visible. A new child of the item starts at zero, and that is the
unit the measurement is about: a session's turns.

## D2 — Which shape the bound takes: the arm's own `refused`, its own notice

The `refused` shape already exists (`repeatReadResult` returns `{ text, refused: true }`, `trimContent` then skips
every rule below), so the count bound reuses it and adds no outcome, no verdict and no `state.sh`/`report.sh` figure.
Alternatives eliminated:

- **A new notice family** (`[LOOP-READ-BOUND: …]`) — costs a shape a child has to learn besides the two it knows, and
  `isRefusalNotice` (`:362`) would have to grow a third prefix for the same fact.
- **A first-class tool error** — a rule that can fail a tool mid-item is what the file's own header forbids: an error
  path is worse than the spend it saves.
- **A new record line carrying the count** — nothing reads it (`metrics.repeatReads` counts the existing lines by
  anchor), and a line that carried a path would be a coordinate in a log.

`repeatReadNotice` therefore keeps its shipped wording when no count answered and appends the count, the bound and the
turn the earlier result came in at when the bound did: one line, one shape, two lengths.

## D3 — Where it fires, and the guards it keeps

The bound is checked **before** the arm branch, because the arm's `measure` default is exactly where the re-asks were
served (#989's run: 86 % of file reads were a second read). It keeps the two guards the shipped rule has, for the
shipped reasons:

- `!entry.handed` — a read the item's ledger already withheld is served, since a refusal would name lines the child
  does not hold (#925's third arm);
- `read.shell` — a shell range read is never withheld, since the child holds those lines and not the file (#1009).

The count is incremented for every repeat, including a range read, because the record counts every repeat; the guard
above is what keeps the refusal off the range read itself.

## D4 — The recorded value: 3

The set a child legitimately re-reads is small and named by the rules beside this one: the file it just edited (one
verification read, #886) and the range a notice sends it to (#1009) — one full delivery and two repeats. The tail past
that is the loop this rule exists to stop: `#872` read four paths 283 to 295 times each across 226 turns, and no run of
the archive shows a fourth whole delivery of one path that is work rather than a re-ask. `0` — or a project that
records no key — leaves the count off, which is the rollback and the ported-project behaviour.

## Risk

Withholding a read a child needs costs more than the turn it saves — the reason the arm's default is `measure`. The
count is the same trade taken at the third repeat, and it is bounded three ways: it is the same path in the same
process, the notice names the turn whose result may be quoted instead, and the value is one key. A child that re-reads
a file it just edited is served: the two repeats the bound leaves are the verification read a child's own edit earns
(#886) and the range a notice sends it to (#1009).

## Open decisions

none

The recorded value, the answer shape (`refused`, the arm's own notice) and the guards the bound keeps are settled
by the measurements above; nothing is left for a person to choose.

## Verification

- `adapter#aPathIsRefusedAfterItsRepeatBound` — the adapter's export, the reads served up to the bound, the refused
  read past it (count and earlier turn in the line, none of the text, one line only), the read after it still refused,
  and a second path served.
- The neighbours that must not move: `repeat#aRepeatReadIsCountedAndTheRefusalArmWithholdsIt`,
  `repeat#theRecordedZeroKeepsTheRepeatRuleOff`, `repeat#anItemsLedgerSpansItsChildren`,
  `repeat#aShellRangeReadFeedsTheTally`, `adapter#theChildIsStartedWithTheRepeatReadArm` and
  `gate#aStepIsNotHandedTheItemsSession`.
- Falsification: mutation record `989.1` rewrites `LOOP_REPEAT_READ_REFUSALS` to `0` in `loop.conf`; the case must go
  red and green again on restore.
- No compiled file is touched (`tools/**`, `guidelines/**`, `openspec/**`), so the build is not run; the structural
  checks of the delta are `bash tools/loop/tests/loop-selftest.sh --files …` and
  `bash tools/loop/tests/loop-mutations.sh --check`.