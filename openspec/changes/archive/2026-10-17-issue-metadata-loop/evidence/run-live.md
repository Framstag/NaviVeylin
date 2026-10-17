# The real run, and the run after it (tasks 7.2 and 7.3, task 6.2's numbers)

Date: 2026-10-17 · tracker: `Framstag/NaviVeylin`, 159 issues · bound: `--max-rescore 5`

The bound is deliberately small: this is the first real run, and five items are enough to prove the
mechanism end to end (a label, a marker, a band order, a consistent check) against a tracker nobody wants
swept by a brand-new pass. The remaining 154 are reported as `waiting`, exactly as the design intends.

## The first run (7.2)

```
$ tools/issues/prioritise.sh --json --scores /tmp/scores-first.json --max-rescore 5 --write --write-log /tmp/live-writes.log
```

| counter | value |
|---|---|
| `read` | 159 |
| `skipped` | 0 (nothing carried a marker yet) |
| `due` | 159 |
| `selected` | 5 |
| `scored` | 5 |
| `written` | 5 |
| `transitions` | 0 (a first score is not a transition, so no comment was posted) |
| `batches` | 1 |
| `waiting` | 154 |
| `unclassified` | 0 |
| `refused` | 0 |
| wall | 20.9 s (the plan's 7.9 s, the five writes, and the scoring) |

The five items, scored against the tables in the `issue-metadata` skill:

| issue | band | score | sev | reach | conf | why (the recorded rationale, abbreviated) |
|---|---|---|---|---|---|---|
| #1 `§129` router vs description distance | `prio/p0` | 15 | 4 | 3 | 3 | the header and the summed step legs disagree by 34 % on a device-measured route, and both are shown |
| #5 `§153` rotation during follow crashes | `prio/p1` | 13 | 5 | 2 | 3 | rotating the phone mid-drive kills the process; reproduced on the AVD, phone-only reach |
| #3 `§139` router under-counts the distance | `prio/p2` | 9 | 3 | 2 | 3 | the router's figure is wrong, but the app reads the description's total — the residue is the fallback |
| #4 `§144` a failed reroute leaves its flags set | `prio/p2` | 10 | 4 | 2 | 2 | every reroute indicator stays on for the attempt's window; read from code, not reproduced |
| #2 `§131` the device case kept the old orientation | `prio/p3` | 6 | 3 | 1 | 3 | a verification defect with developer reach, quoted from the case's own output |

Each was written as **one** operation — the label and the body in a single `gh issue edit` — and the write
log carries exactly those five invocations, each naming only a `prio/*` label:

```
prioritise.sh 1 --label prio/p0 --body /tmp/…/new-body-1
prioritise.sh 2 --label prio/p3 --body /tmp/…/new-body-2
prioritise.sh 3 --label prio/p2 --body /tmp/…/new-body-3
prioritise.sh 4 --label prio/p2 --body /tmp/…/new-body-4
prioritise.sh 5 --label prio/p1 --body /tmp/…/new-body-5
```

And the two verifications the task names:

```
$ tools/issues/check-metadata.sh --live
check-metadata: 159 issue(s) checked · 0 violation(s)          # exit 0

$ tools/issues/list.sh --prio p0,p1,p2,p3 --json | jq -r '[.[] | "#\(.number) \(…)"] | join(", ")'
#1 prio/p0, #5 prio/p1, #3 prio/p2, #4 prio/p2, #2 prio/p3      # non-empty, in band order

$ tools/issues/list.sh --prio p0,p1,p2,p3 --json | jq length ; tools/issues/list.sh --unranked --json | jq length
5
154
```

## The run immediately after it (7.3, and task 6.2's numbers)

```
$ tools/issues/prioritise.sh --json --max-rescore 5
{"read":159,"skipped":5,"due":154,"selected":5,"scored":0,"written":0,"transitions":0,"batches":1,
 "wallMs":6633,"waiting":149,"unclassified":0,"refused":0}

$ tools/issues/prioritise.sh --json --scores /tmp/scores-first.json --max-rescore 5 --write --write-log /tmp/live-writes2.log
{"read":159,"skipped":5,"due":154,"selected":5,"scored":0,"written":0,"transitions":0,"batches":1,
 "wallMs":6774,"waiting":149,"unclassified":0,
 "refused":[{"issue":1,"code":"out-of-scope"}, … five entries, one per already-scored issue]}
```

- **scored = 0**, **written = 0**, exit 0, and the write-log file was never created (no write happened).
- The five items the first run scored are now **skipped by basis**: their labels, bodies, citations,
  recurrence counts and freshness buckets are unchanged, so the marker matches and nothing about them is
  interpreted again.
- The five scores handed to the second run name issues that are no longer in the selection, so the pass
  reports them as `out-of-scope` instead of re-applying them.

**Where the time went — the honest version.** The stated claim is "the second run is materially below the
first". It is (7.2 s against 20.9 s), but the numbers say *where*: the script half is deliberately
**constant** — the pass examines every issue on every run, because skipping an item that had changed is the
one failure the design refuses (spec "Selection never samples") — so both runs pay ~7 s for the 159-issue
page set and the basis arithmetic. What disappears on the second run is the *interpretation*: 5 judgements
and 5 write round-trips become 0, and that is the cost this change exists to remove. A tracker whose due set
is empty therefore still costs one script pass; it costs no model work at all.

**Recorded ratio (task 6.2):** first run 5 scored / 0 skipped (159 due); second run 0 scored / 5 skipped
(154 due, 149 of them waiting behind the bound), wall 20.9 s then 7.2 s, measured 2026-10-17 after the first
real run. The steady state this change is built for is the second row.
