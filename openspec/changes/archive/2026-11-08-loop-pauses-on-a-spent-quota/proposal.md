# The loop pauses on a spent quota instead of ending on it

## Why

The run works a tracker under a **provider quota it cannot see**. Nothing in the engine reads that quota, so a
window that has run out reaches the loop only as refused children — and a refusal is read by `state.sh next` as a
fact about the next item, because that is what the refusal streak is for. The run therefore pays for a pre-screen
and a warm worktree, meets the provider's own failures, and stops on the recorded streak with the budget it still
holds.

Measured 2026-11-08T16:50Z, `GET https://ollama.com/api/balance` with the owner's token answered

```json
{"included":{"session":{"remaining_percent":100,"resets_at":"2026-11-08T20:00:00Z"},
             "weekly":{"remaining_percent":0,"resets_at":"2026-11-09T00:00:00Z"}},
 "purchased":{"balance_usd":0}}
```

— the short window refills in three hours and the weekly window is spent until the next midnight. A run started in
that state can work nothing, and it cannot tell that from a tracker or a gate that refuses.

The same shape of loss is measured for the bound that would end it: **eight of twelve runs stopped on the pair of
refusals with 55–90 % of their budget unused** (2026-11-05/06, `state.sh`'s `LOOP_REFUSAL_STREAK_STOP`). A quota
spent mid-run is that loss again, with a cause the run can read and wait on.

**Root cause, in one sentence.** The run's stop conditions are the ones its own state records, and a quota is not
one of them — so the only way a spent window reaches the loop is as a refused child, which the run then reads as a
verdict about the next item.

## The one thing an operator must decide

The reader answers for **one provider's** quota, and the engine cannot tell which provider a given run spends:
`LOOP_AGENT_CMD*` is the project's, and naming a provider in an engine is what `tools/check-portability.sh`
forbids (D1). Left armed with the wrong provider's reader, a run would wait for a window it is not spending —
measured 2026-11-08, this reader answers `weekly 0 %` for six hours, while a run on other models spends that other
provider's quota entirely. The check is therefore switchable per run, in the two ways a caller has — the run's own
`--no-quota` and a **set-but-empty** `LOOP_QUOTA_CMD` in the environment (`loop.conf` writes the key as a
fill-if-unset expansion for exactly that reason, as `LOOP_BASE_REF` does) — and the run's header records what it
armed, so the log says whether quota was considered at all.

## What Changes

- **`tools/loop/state.sh`** — `next` asks the project's recorded quota command before an item and, when the command
  holds, exits **6** with the reader's own lines on stdout (the reason, and `resume-at <epoch>` the instant the wait
  clears); a command that fails is not a refusal and the run goes on. A new `hold` command moves the run's deadline
  with the wait and records it, because a pause spends no budget; `show` prints the pause total.
- **`tools/loop/run.sh`** — the item loop pauses on that status instead of stopping on it: it prints the reason,
  writes the pause before it sleeps, sleeps until the instant the reader named (or its own poll interval when the
  reader knows none), and asks `next` again. A wait longer than the recorded cap ends the run instead. `--no-quota`
  turns the reader off for one run, and the header records the reader it armed (`quota=loop-quota.sh`) or `quota=off`.
- **`tools/loop/lib.sh`** — the two statuses the two halves of the protocol use, named once each.
- **`tools/loop/loop.conf`** — the keys: which command answers the question (empty = no check, the engine's
  behaviour before this change), the two floors, the reserve the pacing line keeps back, the poll interval and the
  wait cap.
- **`tools/loop-quota.sh`** — the project's reader, outside `tools/loop/` like `tools/pi-child-events.sh`: it reads
  the balance, compares it against the floors and against a pacing line drawn from the fraction of the window
  elapsed, and answers with the protocol's exit status. No engine names a provider, a URL or a percentage.
- **`tools/loop/report.sh`** — the summary's `spent` figure is read as **work**: the recorded waits are subtracted
  from the clock and named beside it, and the figure floors at zero (a wait is recorded when it is taken, so a
  report read while the run is still asleep can see more waiting than elapsed clock). Without it a run of three
  items that waited an hour of its two reported `spent 120 min` and `budget left 3 items / -30 min`.
- **`tools/loop-cases.sh`** — four `state` cases, one `run` case and one `report` case, and
  `tools/loop-mutations.sh` — six records, one per invariant.

surface: 1 requirement added with 7 scenarios, 6 `LOOP_QUOTA_*` keys added to `tools/loop/loop.conf`, 1 project
script added, 3 engine scripts extended, 1 run argument added (`--no-quota`) and 1 header token, 7 cases added, 8
mutation records added —
`bash tools/loop-selftest.sh --list | grep -ciE 'quota|aHoldMovesTheDeadline|theSpendIsWorkNotWaiting'` for the cases
and `bash tools/loop-mutations.sh --list | grep -c 'quota-hold'` for the records.

## What This Does Not Do

- **It does not price an item in quota.** What one item costs in percent is not known when it starts — the run
  measures wall time, not quota — so an item that overshoots the pacing line makes the *next* wait longer and never
  leaves a window exhausted mid-item. A recorded cost per item is a later change if the line proves too coarse.
- **It does not hold inside an item.** The check is asked at the boundary between two items, where the run's own
  conditions are read. A window that runs out while a child is working is still that child's failure; what this
  change removes is a run that *starts* into a spent window and a run that ends on the refusals that follow.
- **It does not poll.** The endpoint is rate limited to 10 requests a minute per user, shared, so the reader is
  called once per item boundary and once per wait — never on a timer.
- **It cannot tell which provider a run spends.** The reader answers for one provider's quota and nothing knows
  which provider the configured agent commands use — that is a project value the engine may not name. So the
  pairing is the operator's, and the switch exists for it: a run on another provider is started with `--no-quota`
  (or the key empty, or pointed at that provider's own reader).
