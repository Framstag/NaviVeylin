# A pre-screen refusal leaves a trace the owner can query

## Why

`prescreen.sh` decides one verdict per candidate and writes it into `.pi/issue-loop/run.json` only. That
state is machine-local and per-run: `state.sh init` replaces it wholesale on the next run. So an item the
loop will *never* work — `stale`, `class-feature`, `settled` — keeps `agent/eligible` and `status/open`
forever and is indistinguishable from one the loop has simply not reached yet.

Measured, root cause, in one line: **the pre-screen's refusal has no writer outside the run's own state, and
that state is the one thing a run is guaranteed to overwrite.**

Evidence:

- `evidence/red-on-head.log` — on `HEAD` the pre-screen reaches the `stale` verdict for issue #31 and writes
  no label at all (`agent/excluded-stale` not found in the recorded `gh` write log); the verdict exists only
  in the run state.
- Issue `#163`, measured 2026-10-18 while authorising #162 and #159: both cited paths that no longer existed,
  so both were excluded as `stale` on every run while carrying the authorisation label, and the owner could
  not tell them from items not yet reached.

The contradicting requirement is the one this change modifies: **"The run acts only on authorised, eligible
items"** states that a rejected candidate's verdict is "part of the run's output", and the run's output is
the machine-local state — so a tracker-visible trace is not required by any requirement today.

## What changes

The pre-screen's refusal becomes a label the tracker carries, written by the loop's script:

- the verdicts an item **cannot escape by itself** (`stale`, `class-feature`, `settled`) write
  `agent/excluded-stale`, `agent/excluded-feature`, `agent/excluded-settled`;
- the authorisation label and the status are left exactly as the owner left them — the write is a refusal,
  not a transfer of the owner's opt-in;
- the first run whose verdict is no longer a refusal **removes** the label, so a repaired citation is worked
  instead of staying frozen;
- a `--dry-run` pre-screen (which is what `run.sh --dry-run` passes) writes nothing.

Out of scope, and stated as such in the design: `in-flight` (self-clearing when the other change closes),
`needs-device` and `needs-decision` (already have a label write of their own in `labels.sh`/`run.sh`), and
`missing-authorisation`/`author-not-allowlisted` (the item is already visibly unauthorised — the label would
say nothing new).
