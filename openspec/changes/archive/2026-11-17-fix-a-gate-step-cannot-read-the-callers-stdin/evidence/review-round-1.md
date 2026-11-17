# Independent review, round 1

Read-only reviewer, launched 2026-11-17T07:46Z against the uncommitted delta (its own summary in
`/tmp/pi-subagents-uid-1000/async-subagent-runs/23c81aec-b042-4cfd-a013-aba6d446ae3f/output-0.log:1756-1784`).

**Verdict: OK with notes — no P0/P1.** Per question:

1. The case reds on the right reason and cannot pass spuriously — `red-on-head.log` ends `errors=1` with the step
   ended at its 10 s bound, the suite's first act is `read -r line`, and the same tree passes in `0.29 s`
   (`green-case.log`). It asserts the step's own line (`notes: the step read nothing`) and `tests=1 failures=0`, so a
   step never started fails it too.
2. The per-step redirect is the right seam: `start_step` is the only place a step starts, and no other reader in
   `tools/loop` takes the caller's stdin (`run.sh:2125`, `loop-selftest.sh:660,683,860` already redirect).
3. Both patterns match exactly one line; `947.1` restores HEAD's start line byte for byte; no orphaned quote.
4. The requirement body is 400 characters; the scenario names exactly the one case; no shipped requirement about a
   step's stdin exists to contradict.
5. Claims checked against the logs; the notes below were the ones not carried.

Notes paid in this change:

- `proposal.md`: the "disjoint halves" claim replaced with what the two records actually share; the `look.sh` figure
  restated as it reads now (5 files, two of them this change's own prose); the incident figures marked as the issue's
  own Observed/Evidence lines, the run's log being their source.
- `design.md`: the `loop_end_group` citation `616` → `623`; `start_step`'s range `568-583` → `557-585`; the resume
  attempts stated once (twice, then a third after `stty -tostop`), as `gate.sh` and the case state them.
- `tasks.md`: `--only`'s exit named by the harness's own tally line instead of an "exit 0" no log carries; the
  structural checks recorded in `evidence/structural-checks.log`.

Accepted, not paid (P2, report-only):

- The case's first failing assertion on a broken tree is `assert_eq "0" "$rc"`; the blocked read is read from the
  appended output. It is the sibling cases' own convention.
- A step could still open `/dev/tty` itself. That is outside the invariant, and no step in the profile does it.
- `947.1`'s pattern is a suffix with no `^` anchor. It matches one line today and `--check` names the record if that
  stops being true; `63.1` remains the record that owns the line's head.
