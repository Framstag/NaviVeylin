# Design — fix-loop-gate-tally-invents-no-test

## Root cause

`tools/loop/gate.sh` reads a suite's numbers from the suite's own summary line and, when that line is absent,
fills the gap so that a failing suite cannot contribute zero (both lines as the base commit `f5c32df2` carried
them — `git show f5c32df2:tools/loop/gate.sh`):

- `tools/loop/gate.sh:140` — `case "$f" in ''|*[!0-9]*) [ "$rc" -eq 0 ] || f=1 ;; esac`
- `tools/loop/gate.sh:148` — `[ "$rc" -eq 0 ] || [ "$n" -gt 0 ] || n=1`

Both write a number the suite never reported. `f` feeds the gate's own failure total and the tally's
`failures`; `n` feeds the tally's `tests` only, because the total is summed at `:141` before the substitution.
One mechanism: a failed suite's *absence of a summary* was expressed as *one failing case*.

## The fix

Keep the two facts apart, in one place:

- `n` and `f` are what the suite reported, and default to `0` without any substitution (`:140` and `:148` lose
  their fallbacks).
- `err` is `1` exactly when the suite exited non-zero and reported no failing case
  (`[ "$rc" -eq 0 ] || [ "$f" -gt 0 ] || err=1`). It is summed beside `cases` and `failures`.
- The tally writes `tests="<n>" failures="<f>" errors="<err>"` — the `errors` attribute already exists and was
  always `"0"` (`:149`), so no consumer's shape changes.
- The gate's own line gains `errors=<err>` between the counts and the coverage list, so the operator's line and
  the tally carry the same three facts.

Nothing else in the engine moves: the exit status, the `— failing:` list, the streamed output, the last-three-
lines `<system-out>` tail and the "nothing owed" path are untouched.

## Why not the alternatives

- **Keep `f=1` and add `errors="1"` beside it.** That is the shape the item itself offers as a bound, but it
  still states a failure the suite did not report, and the item's own sentence — "keep `failures` for a count
  the suite itself reported" — forbids it. Eliminated by the item's bound, not by taste.
- **Write `tests="0"` and leave the tally's `failures` at 1.** The same contradiction one attribute over: the
  tally would claim a failing case count while its case count is zero, and `tools/loop/worktree.sh:248` reads
  the pair together.
- **A `<system-err>` element instead of the `errors` attribute.** The item offers it as an alternative, but a
  reader that decides greenness by XML attributes (`worktree.sh:248`) would then have to parse element text;
  the attribute is already in the vocabulary of every tally this gate writes.
- **Drop the suite from the tally entirely.** Then the run's own check — a green claim has a test-results XML in
  the tree (`tools/loop/run.sh`) — loses the only record that this suite ran and failed, and `measure.sh` would
  stop naming it. The failure has to stay visible; only the invented number has to go.

No option survives beside the fix, so this is one change and not a decision.

## Blast radius

- `tools/loop/gate.sh` — four lines touched, one attribute and one format field.
- `tools/loop-cases.sh` — one case added, one assertion added to `gate#aFailingSuiteIsReadFromTheSameStream`,
  one entry in `LOOP_SHELL_CASES` and one in the name-to-function map.
- `tools/loop-mutations.sh` — one record (`47.1`).
- `tools/loop/prompts/worker.md` — one format line.
- `guidelines/Backlog.md`, section "7. The loop" — one paragraph gains the rule and its measurement.
- `openspec/specs/agent-issue-loop/spec.md` (on archive) — one requirement added.

The tallies already written elsewhere keep their shape: `measure.sh:82` and `evidence-check.sh:91` match
`tests=` then `failures=` and ignore a trailing attribute, and `worktree.sh:248` reads `errors` already.

## Rollback

Revert the five files; the change is additive to a record and subtractive to two substitutions, so nothing
downstream keeps state. An archived tally written by the fixed gate is still readable by every consumer named
above.

## Verification

- Red: `gate#aFailingSuiteWithNoSummaryInventsNoTest` fails on HEAD (`evidence/red-HEAD.log`,
  `evidence/red-HEAD.xml`).
- Green: the same case passes, with the fixed tally kept as `evidence/fixed-tally.xml`.
- Falsification: `tools/loop-mutations.sh --only 47.1` puts the fabrication back and the case goes red; restore,
  the case goes green (`evidence/revert-check-47.1.log`).
- Gate: `./tools/loop/gate.sh` once, its per-suite tallies copied into `evidence/gate-tallies/`.
