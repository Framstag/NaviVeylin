# Spec Delta — agent-issue-loop

## ADDED Requirements

### Requirement: A wait ends when the writer of the record it waits for is gone

The wait for a child's completion record SHALL read, beside the clocks and the spend bounds it already enforces,
whether the subshell that writes that record is still running, and SHALL end the wait when it is not: the record is
written by the run's own subshell, so its pid is the only reading that tells a child still working from a child whose
writer was killed before it wrote one. That reading SHALL be taken only while the record is absent, SHALL be used only
to end the wait, and SHALL NOT make the wait depend on the pid in any other way — a pid the shell has already reaped
answers nothing, and a `wait` on a pid the shell no longer tracks returns no status (issue #227). A wait it ends SHALL
be recorded as a bound the run enforces: one machine-readable line naming the vanished writer (`LOOP-TIMEOUT: the
child's writer (pid <n>) vanished before it wrote its completion record …`), everything the child left ended as the
other bounds end it, and the item `blocked` with `bound` as its stop reason, no decision label and no decision slot. A
child that is merely slow SHALL be waited for exactly as before, and a pid that answers `kill -0` SHALL NOT end a wait.

#### Scenario: A child whose writer vanished ends the wait

Measured 2026-11-06: the run started at 06:27:14Z was reparented to `systemd --user` when the session that owned it
died and sat in this poll with a single `sleep 0.05` 17 minutes into a 600-minute budget, its lock still held and item
#793 still `agent/in-progress`; the child cap would have ended the wait at 45 minutes (issue #795).

- **WHEN** the subshell writing a child's completion record is killed before it writes the record, with the child's
  wall clock an hour away and the run's deadline not reached
- **THEN** the run ends the wait within seconds, appends `LOOP-TIMEOUT: the child's writer (pid <n>) vanished before it
  wrote its completion record` to the child's transcript, and records the item `blocked` with `bound` as its stop
  reason, its note naming the vanished writer, no `agent/needs-decision` label and no decision slot
- **Case**: `run#aVanishedWriterEndsTheWait`

#### Scenario: A child that is merely slow is still waited for

- **WHEN** a child is running and has written nothing — a worker reading its package looks like this — and the pid its
  record is written by answers `kill -0`
- **THEN** the run keeps waiting, writes no `LOOP-TIMEOUT` line for it, and ends the wait on the record the child writes
  when it finishes
- **Case**: `run#aSlowChildIsNotMistakenForAVanishedWriter`
