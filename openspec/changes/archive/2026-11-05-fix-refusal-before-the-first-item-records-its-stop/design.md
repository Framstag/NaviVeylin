# Design — fix-refusal-before-the-first-item-records-its-stop

## Root cause

`run.sh`'s `stop_run` (`:562`) is one line of state and one line of console — `loop_state stop "<reason>"` plus
`printf 'stop: %s\n'`. Every refusal that ends a run before an item was picked calls it except three, and each of
those three ends through a bare `exit "$LOOP_REFUSE"` (the `--reset-worktree` repair at `:692` ends through
`loop_refuse`, which is `printf` to stderr plus that same exit). `report.sh` decides which end it is looking at by
reading `.run.stop.reason` (`:93`); empty, and the process gone, is the killed-process sentence (`:120`). So an
unrecorded refusal is not a smaller record — it is a different one, and the wrong one.

## The single fix

Route the three refusals through the function that already exists:

- `:758`, the decision cap — `stop_run` with a reason built from the two numbers the refusal already read
  (`open_decisions`, `labelled_decisions`) and the candidate list it already printed, before `report.sh` and the
  exit. The candidate list is built once and used twice: printed under its heading, and joined into the reason.
  No new tracker read: both counts and the list come from the reads the refusal already paid for.
- `:721`, the kept worktree a previous run left dirty — `stop_run` naming the refusal and the item the tree's
  branch marker names, before the report it already prints.
- `:692`, `--reset-worktree` that cannot make the tree clean — `stop_run` in place of `loop_refuse`, then the
  report and the exit its neighbour at `:721` uses. The reason carries the paths the repair printed — the status
  left behind after both verbs, not the one the attempt started from, since a file the reset did discard is not
  work a reader still owes. The repair's own output — what it named, what it could not discard — is unchanged;
  the case that reads it keeps every assertion.

Nothing else moves: the same exit codes (`5`), the same lines on the console, the same order.

## Blast radius

Three exits in `tools/loop/run.sh`, one case added and two cases given assertions each —
`run#dirtyKeptWorktreeIsARecordedRefusal` two, `run#resetWorktreeNamesTheDirtItCannotDiscard` two, the second of
those added by round 1's correction so the reset arm's reason names the paths its repair printed. A run that is refused
at any of the three now writes `run.stop`, so `report.sh` prints its end instead of "not stopped — the run is
still going, or it ended without recording a reason" (`:132`) or, once the pid is gone, "it was killed and
recorded no stop" (`:120`). Nothing reads `run.stop` for a decision: `state.sh next` reads a run's *own* stop only
for its own window, and the next run's `init` announces a state it replaces that never recorded one (`state.sh:153`),
which after this change no longer fires for these three paths.

## Rollback

Revert the three `stop_run` calls and the three case edits; the tree returns to a state with the defect, which is
the base commit's own behaviour. No data or tracker write is involved: the change writes one key into a state file
the next run replaces anyway.

## Alternatives the evidence eliminated

- **Record the reason in the item's own block only, as the kept-worktree arm does today.** Eliminated by the
  measurement the item carries: a refusal's item entry is on the tracker, the run's end is in `run.json`, and the
  report is what a reader runs first — `report.sh:103`-`:120` reads `run.stop`, so an item block alone still
  prints the killed-process sentence. The blocked-item record is kept; it is not the run's end.
- **Make `report.sh` infer a refusal from the exit code or the printed text.** Eliminated by the rule the report
  already follows (`report.sh:91`-`:93`): it reads the state, never the console, because the console scrolls away
  and the exit code is not written anywhere. A second inference would be a second rule about what an end is (#184).
- **Record the reason from a wrapper around every `exit`** (an `EXIT` trap that calls `stop_run` when nothing was
  recorded). Eliminated because `on_exit` already runs on every exit and owns the lock, the interrupted item and
  the sweep; a second writer of `run.stop` in the same trap would record a reason for exits that are not refusals
  — the two `end_run` paths, a usage error before `init` — and "which end is this" would become a guess rather
  than the caller's statement.

## What is not changed

The two `end_run` exits in the window (`:764`, `:798`) are ends of a run that did what it could — `--items 0` and a
pre-screen that found nothing eligible — not refusals, and they carry `LOOP_OK`. Whether a *successful* end should
also record a reason is a different claim about a different sentence (`report.sh:131`), touches `end_run` and the
whole item loop's exit, and is not this item's. This change leaves them exactly as they are.

## Open decisions

none
