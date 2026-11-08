# Design — a condition outside the run is a wait, not a verdict

## Root cause

`state.sh next` is the run's only authority on whether an item may start (invariant 3), and every condition it
reads comes from the run's own state: the item cap, the deadline, the queue, the refusal streak, the floor derived
from the measured wall times. A **quota** is none of those — it belongs to the provider, it changes while the run
sleeps, and it is exactly the condition the run's own numbers cannot express.

What reaches the loop instead is a refused child. `run.sh` records that item `blocked` and, after
`LOOP_REFUSAL_STREAK_STOP` of them, ends the run — measured 2026-11-05/06, eight of twelve runs stopped that way
with 55–90 % of their budget unused. Two of those refusals is a prediction about the *next item's* provider call,
and for a spent quota the prediction is right and the conclusion is wrong: the next item is fine, the window is
not.

## The fix

Three decisions, each with the alternative that was rejected.

### 1. The condition is asked where every other condition is asked

The check lives in `state.sh next`, not in `run.sh`. Invariant 3 is that the run never does its own budget
arithmetic, and "may another item start" is that arithmetic. The engine also then has one place where the question
is answered, and `run.sh` needs no new condition of its own.

**Rejected:** a pre-flight in `run.sh` before the lock and the worktree. It would be a second condition that can
only refuse — it cannot pause, and it cannot see a window that runs out during the run.

### 2. A hold is its own status (6), and the wait moves the deadline

`next` already exits 3 for every stop and 5 for a refusal that must not be retried. A wait is neither: the run does
not end, and the condition is not a fact about an item. So `next` exits **6**, and `run.sh` treats it as: print,
record, sleep, ask again.

The **deadline moves with the wait** (`state.sh hold --seconds N`). Without that the whole mechanism is a
decoration: a window that returns in six hours would be waited for past the run's own deadline, and the run would
wake, read `deadline passed`, and end — the outcome the hold exists to avoid. A pause is not work, so it is not
spent budget, and `state.sh` is the only writer of that budget.

The pause is also **recorded** (`.run.holds[]`): a run that took nine hours to work three items can then say how
much of that was waiting, which the items' own wall times cannot — they start and end inside a child and never
span a hold.

**Rejected:** letting the pause count against `--minutes`. It makes the deadline a wall clock the operator must
size by guessing a provider's reset, and every long wait then ends the run at 0 minutes left.

### 3. The reader is the project's; the engine names only a status

`tools/loop-quota.sh` lives outside `tools/loop/`, beside `tools/pi-child-events.sh`, and `loop.conf` names it
through `LOOP_QUOTA_CMD`. That is the portability rule `tools/check-portability.sh` enforces (D1, D16): an engine
names no project value, and a URL, a provider and a percentage are all project values. The engine owns *when* the
question is asked; the reader owns *how* it is answered.

The protocol between them is three statuses — `0` go, `3` hold (the reason on stdout and `resume-at <epoch>` as the
last line), anything else "the reading could not be made" — and both halves name their value once
(`LOOP_HOLD_READ` in the engine, the reader's own `exit`). The engine turns the hold into its own `LOOP_HOLD` (6),
so a project that answers with a different reader still cannot make the run end on a wait.

**Rejected:** teaching the engine about the provider's JSON. It would make the engine the port's problem and break
the one check that proves the engine is portable.

### 4. The switch belongs to the operator, not to the engine

The reader answers for **one provider**, and a run may spend a **different** one: `LOOP_AGENT_CMD*` is the project's
own configuration, and a run started on other models (the measured case: children configured for another provider's
models, while this reader answers `weekly 0 %` for six hours) would be held for a window it is not spending. That is
the one way this change can waste a whole run, so the check is switchable in the two ways a caller has, and the
header records the choice — a run that waited on nothing because nobody asked must not look like a run with quota to
spare.

- `run.sh --no-quota` empties the key **in the run's own process**, before any child starts.
- `LOOP_QUOTA_CMD= …` in the environment does the same, which is what a scheduler or another script has.
- `LOOP_QUOTA_CMD="${LOOP_QUOTA_CMD-bash tools/loop-quota.sh}"` in the configuration is what makes both work: the
  file is sourced **after** the environment, so a plain assignment would overwrite whatever the caller set, and `-`
  rather than `:-` is what makes *set-but-empty* mean "off" (`LOOP_BASE_REF` below it uses the same pattern for the
  same reason).

**Rejected:** having the engine decide. It could read `LOOP_AGENT_CMD*` and look for its reader's provider name in
the command, but that is the engine naming a provider — the portability check's business (D1) — and it would guess
from a string that a role, a class and a model flag can each change independently of the reader. The one fact the
engine cannot have is the one the operator holds, so the operator states it and the header records it.

**Rejected:** a reader that decides for itself (this project's reader could source `loop.conf` and return "go" when
the agent command names another provider). It would put a second reader of the configuration in the adapter, and it
decides by pattern-matching a command line — the failure it prevents (a run held for the wrong window) is one a
single flag prevents exactly.

## The arithmetic, and why the line is drawn where it is

A window is a **rate over a period**, not a pot: the weekly window refills at the top of its week, so a run that
spends it flat out on day one buys nothing for the six days after it. Two bounds follow, and the second is what
makes the first useful:

```
floor   : remaining_percent >= LOOP_QUOTA_<WINDOW>_MIN_PERCENT        (15 session, 5 weekly)
pacing  : burned_percent  <  elapsed_fraction_of_week * (100 - reserve)   (reserve 10)
```

- The **floor** is the reserve below which the run stops borrowing from the future. The short window's is the
  larger because the short window returns within hours; the weekly one does not.
- The **pacing line** is the linear share of the week that may have been burned by now. At the top of the week it
  is near zero, so the run takes what an item costs; as the week fills, the line arrives and the run waits instead
  of overdrawing. This is "burn in relation to the week, never overburn" as arithmetic, and the reserve is the
  share kept back for **every other consumer of the same quota** — the owner's own sessions, another machine, a
  desktop tool.

The reserve is **multiplicative** (`elapsed * (100 - reserve)`) rather than subtracted from the line
(`elapsed * 100 - reserve`): the subtractive form keeps back a tenth of the *week* at its start, which is 17 hours
of a window nobody has touched yet, and the run would sit idle on the day the quota is fullest. The chosen numbers
give roughly a seventh of the week per day less a tenth, which is the shape the owner asked for.

The **resume instant** is solved, not polled: for the floor it is the window's own reset; for the pacing line it is
the instant `elapsed * (100 - reserve) = burned`, unless that is past the reset, in which case the reset is
sooner and is what is named. The reader adds a 15 s margin so the run wakes *after* the boundary rather than on it.

**Rejected:** calculating an item's cost in percent from the run's own history (the shape `LOOP_ITEM_FLOOR_MINUTES`
uses for wall time). One item's quota cost is only observable as the difference between two readings, the endpoint
is rate limited, and the first item of a run would have nothing to compare against — so the pacing line is the
whole guard and an item that overshoots it simply lengthens the next wait.

## What `--items` and `--minutes` now mean

Both stay the run's own bounds and neither is deprecated, but a pause changes what the clock counts. `--minutes` is
**work** minutes: each wait moves `run.deadlineEpoch` by its own seconds, so after a six-hour hold the deadline is six
hours later and the minutes left are still the budget minus the time actually worked (`state.sh show` prints them, so
the line and the budget agree). `--items` is untouched — a wait spends no item slot, and a run that paused three
times still works exactly the items it was given. `LOOP_QUOTA_HOLD_MAX_SECONDS` is what bounds the waiting itself
(`0` = no bound, the recorded default, because a weekly window legitimately resets days away).

## The consequence the deadline shift has in the report

`report.sh` read the run's spend as the clock from `startedEpoch` to the stop, so a run whose deadline moved with a
wait reported the **wait** as work: with a one-hour wait inside a two-hour window it printed `spent 120 min` and
`budget left 3 items / -30 min` — a negative budget nobody could act on, and a figure that disagrees with the one
number the change exists to keep honest (a pause is not spent budget). The report therefore subtracts the recorded
waits from the clock, names them (`· paused 60 min in 1 wait(s)`), and floors the figure at zero: a wait is recorded
when it is **taken** — before the run sleeps in it — so a report read while the run is still asleep can see more
waiting than elapsed clock, and work spent is never a negative number.

**Rejected:** printing the pause without subtracting it. A reader would then have to know that `spent` includes
waiting to make the budget line add up, which is the state that produced the negative figure.

## Where it can be wrong

- **A spent window inside an item.** The check is a boundary condition; a child working when the window runs out
  still fails. The change removes the run that *starts* into a spent window and the run that ends on the refusals
  that follow, and it says so in the proposal's "What This Does Not Do".
- **The endpoint's own shape.** The reader was written against one measured answer (2026-11-08). A missing or
  renamed field makes the reading "could not be made", which is a go — the run burns quota blind rather than
  stopping, and the reason is relayed to stderr when the reader says so.
- **A provider whose reset is not a week.** `LOOP_QUOTA_WEEKLY_SECONDS` (default 604800) is what the pacing line
  divides by, and it is recorded rather than derived from the window's name.

## Open decisions

none
