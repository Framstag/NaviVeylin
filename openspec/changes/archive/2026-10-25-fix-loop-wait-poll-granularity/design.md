# Design — the wait's granularity, and the cadence that has to survive it

## Root cause, one sentence

Both sleeps in `run_child` — the poll for the child's own completion record and the heartbeat subshell the wait
is joined through — are a whole second, and the heartbeat counts wake-ups (`ticks`) rather than elapsed seconds,
so the granularity cannot be lowered without changing what `LOOP_PROGRESS_SECONDS` means.

Cited in the tree as it stands after this change: `tools/loop/run.sh:924-957` (the poll, `sleep "$poll"` at
:956) and `tools/loop/run.sh:890-916` (the heartbeat, `elapsed=$(( SECONDS - hb_base ))` at :899).

## The single fix

1. A poll granularity of its own, `LOOP_POLL_SECONDS` (default `0.05`), validated the way the two bounds beside
   it are: empty, non-numeric or zero falls back to the default, because a wait that stops looking is a run that
   hangs and a bare `0` is a busy loop. `tools/loop/run.sh:864-868`.
2. The heartbeat reports on elapsed **seconds**: `hb_base=$SECONDS` at its start, `elapsed=$((SECONDS - hb_base))`
   per look, a line (and the outside-write reading beside it) when `elapsed >= next`, then
   `next=$((elapsed + interval))`. `tools/loop/run.sh:890-916`.
3. The two whole-second clocks stay whole seconds and are read **once per second** rather than once per look:
   the deadline (`run.deadlineEpoch` via `jq`) and the cap (`date +%s`) are compared only when the shell's own
   `SECONDS` has moved — the guard is one line, so the two checks keep the indentation their records quote.
   `tools/loop/run.sh:926-956`.
4. The `TERM`→`KILL` grace after a cap or a deadline firing stays one second (`run.sh:944`, `run.sh:952`): it is
   a grace for a child to end itself, and shortening it would turn "ended" into "killed".

Why the deadline read is gated instead of moved: the finer granularity is bought with wake-ups, and a `date` plus
a `jq` per wake-up would spend 20 forks a second to answer a question whose unit is a second — the fix would cost
CPU to save wall clock on the same machine.

## Blast radius

- The transcript's shape is unchanged: the `started`, `running` and `finished` lines keep their text, and
  `fmt_elapsed` now receives elapsed seconds (which is what `ticks` meant while a tick was a second).
- The outside-write reading (issue #286/#288) still happens once per configured interval, on the heartbeat's tick.
- The cap and the deadline still fire within a second of being due, as before.
- No caller, no profile key, no state field, no on-disk format changes; `loop.conf` is untouched because the
  granularity is a validation-defaulted constant, not a project fact.

## Alternatives the evidence eliminated

- *Lower `sleep 1` alone*: the heartbeat would report every `interval` **wake-ups** — with a 0.05 s poll a
  configured 30 would print every 1.5 s — which the item names as the reason the granularity was not lowered in
  the first place.
- *Lower the sleep and renumber the heartbeat to `interval / poll`*: it makes the cadence depend on the
  granularity (a second knob to keep in sync) and it divides by a fractional value in shell arithmetic, which
  is integer-only here.
- *Keep the second in the poll and shorten only the heartbeat*: the poll is what ends the wait, and the item's
  own measurement (40 % of `run#recordsAFailedReviewOnTheBranch` was sleep) is inside the wait.
- *Read the deadline per look* (measured instead of guessed): rejected above — it spends CPU per wake-up for a
  whole-second clock.
- *Use `read -t` on a fifo instead of sleeping*: the case for `run#childReportsWhileItWaits` records why a fifo
  rendezvous was removed (an orphaned writer held the suite open twice, 2026-10-20), and a fifo is a second
  process to keep alive where a `sleep` is none.

## Rollback

Revert `tools/loop/run.sh` to the tick-counting heartbeat and `sleep 1` in both places; delete the two cases and
the two mutation records with it. Nothing else in the tree depends on the new name, and no evidence artifact is a
prerequisite of the engine.
