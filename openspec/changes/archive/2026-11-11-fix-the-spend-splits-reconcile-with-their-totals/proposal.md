# The report's two spend splits reconcile with the totals they are printed beside

## Why

Issue **#832**. The trend block prints two pairs of figures a reader cannot reconcile, and both pairs come from two
measures over two different sets:

- **The tokens.** `roles:` prints `worker 23 Mtok · reviewer 30.4 Mtok · tail 2.3 Mtok · 55.7 Mtok over 13 items`
  while the same 13 items' `metrics.tokens` sum to 73.2 Mtok — 17 544 309 tokens, 24 %, that the line counts in no
  bucket and never names. Measured on the run of 2026-11-07T19:53:44Z
  (`.pi/issue-loop/runs/2026-11-07T19:53:44Z.json`; the command and its output are kept in
  `evidence/measure-run-totals.log`): `.items[].metrics.tokens | .cacheRead + .input + .output | add`
  = **73 220 472** against `[.items[].metrics.roles[]] | add` = **55 676 163**.
- **The clock.** `phases: model 1.6 h · gate 935.6 s · other 963.1 s · 2.1 h over 13 items` covers 7 586 s of the
  same run's 24 104 s window (`.items[].metrics.wallSeconds` sum to 23 890 s), and nothing in the block says the
  three counters are the children's own turns and tool executions rather than the run.

**The root cause of the token half, in one sentence:** `child_usage_json` builds the token total from **every**
`turn_end` in the item's stream (`tools/loop/state.sh:974`–`:982`, `:1135`–`:1144`) and the role split from the
sessions alone (`:1014`–`:1038`, `:1049`–`:1055`), whose reducer drops every turn that stands before the stream's first
`session` record — so a turn the record cannot place under a role is in the total and in no bucket, and the two
figures are over different sets by construction. Measured on the filed run's items, per item: #738 records 5 988 239
tokens and 3 677 074 under a role (its `session` record stands at line 14 254 of 21 310 of the item's own stream,
with the 138 turns a `stream_rotation` took off the head — `evidence/measure-stream-timestamps.log`), #753 4 797 233
against 3 160 625, #715 6 939 763 against 6 427 758.

**The clock half is one measure printed without its denominator**, and the record already holds both halves: the
three phase buckets are the children's turn durations and tool durations (`state.sh:1042`–`:1048`), the item's own
clock is `metrics.wallSeconds`, and the trend prints the buckets' total with nothing saying what it is a share of
(`tools/loop/report.sh:476`–`:488`). The stream carries no timestamp on its events (measured: 3 of 22 125 events of
item 808's own stream carry one, all of them `session` records — `evidence/measure-stream-timestamps.log`), so the
difference cannot be recorded from the stream at all — it is child startup, the run's own steps between children and
the work a phase does outside a turn and outside a tool call.

**Verified in the tree at the run's base `5db58389`:** both readers are as stated above and no line of the trend
names either denominator; the only `roles` keys any writer emits are `worker`, `reviewer`, `tail`
(`state.sh:1054`, the reducer's initial object) and the report iterates exactly that trio
(`report.sh:514`–`:519`).

**The red cases** — `report#aTurnNoSessionRecordReachesIsItsOwnBucket` and
`report#thePhaseLineNamesTheClockItIsAShareOf` (`tools/loop/tests/loop-cases.sh`), read against the base's two readers
(`tools/loop/state.sh` and `report.sh` as `5db58389` has them, byte-compared in the log) with the change's own cases
file, one `bash tools/loop/tests/loop-selftest.sh --case '<name>'` each at 2026-11-11T08:44:41Z:

```
report#aTurnNoSessionRecordReachesIsItsOwnBucket|   the turns no session record reaches are a bucket of their own: expected '2000000', got 'null'
FAIL report#aTurnNoSessionRecordReachesIsItsOwnBucket
report#thePhaseLineNamesTheClockItIsAShareOf|   the phase line names the item clock the three buckets are a share of, and what they leave out: 'model 1 s · other 0.3 s · 1.3 s over 1 item, 3 % of the 42 s of item wall — 40.7 s of it in neither a turn nor a tool' not found in:   phases: model 1 s · other 0.3 s · 1.3 s over 1 item
FAIL report#thePhaseLineNamesTheClockItIsAShareOf
```

`evidence/red-on-HEAD.log` is that run and `evidence/red-on-HEAD.xml` its tally (`tests=2 failures=2 errors=0`) —
transcribed, because the shell runner writes no JUnit XML of its own.

**The recorded decision.** The item's decision of 2026-11-11T08:31:19Z settles the token half: *option A — the record
partitions every turn, and the report prints every bucket it holds*, because "the missing 17 544 309 Mtok is a hole in
the record, not in the printing". This change is that arm: the bucket is recorded and every bucket is printed. The
clock half is the printing arm only because no record can partition it — the stream it would be read from carries no
timestamps (measured above), so the line names the item clock it sits inside instead of inventing a fourth bucket.

**Owning requirement:** `openspec/specs/agent-issue-loop/spec.md`, "The report says which phase and which role spent
the run's clock" (`:3213`), whose body splits the tokens "by the role that spent them (worker, reviewer, tail)"; the
delta adds the bucket those three leave out and the denominator the phase line is a share of.

**Owning guideline section:** `guidelines/Backlog.recipes.md` section 7b "What an item costs, and the bounds on it", the
paragraph at `:621`–`:639` that names `metrics.phases` and `metrics.roles`.

surface: 2 cases added, 0 rewritten, 0 fixtures realigned, 1 engine reducer and 1 report line in two files
(`state.sh`: the `sessions` reducer's orphan region and the bucket `roles` and `roleModels` key it as; `report.sh`: the
`roles:` line's role order and the `phases:` line's denominator), 1 requirement modified (two scenarios added), 2
mutation records, 1 guideline paragraph. The record is `evidence/measure-surface.txt`, which carries the case count
(**405** with this change's two cases) and the surface line itself; the passes the count came from are
`bash tools/loop/tests/loop-selftest.sh --list` and `tools/look.sh '<rule>' --files` over the cases, the engine, the
prompts and the fixtures.
