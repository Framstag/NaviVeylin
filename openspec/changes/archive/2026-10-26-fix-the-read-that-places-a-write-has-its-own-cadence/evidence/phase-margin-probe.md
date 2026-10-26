# The reading's phase against the writer's life, measured on HEAD

The probe below was planted in the HEAD engine for one measurement and then removed (the tree carries no
`PROBE` line): `run.sh`'s heartbeat logged every reading it took (`date +%s.%N`, the elapsed second and whether
the two readings of the project tree differed), and the case's stub action logged the moment it wrote
`demo-owned.txt` and the moment it took the write back (`sleep 3`, writer standing `sleep 2`).

```
PROBE write 1793042561.578573729      ← the stub action's write, at HEAD's own case shape
PROBE tick  1793042562.027425291 elapsed=1 diff=yes   ← the first reading, 0.45 s after the write
PROBE rm    1793042564.581848575

PROBE write 1793042572.379182150
PROBE tick  1793042573.035794913 elapsed=1 diff=yes   ← 0.66 s after the write
PROBE rm    1793042575.382281982

PROBE write 1793042583.282973341
PROBE tick  1793042584.039669318 elapsed=1 diff=yes   ← 0.76 s after the write
PROBE rm    1793042586.286173882
```

What it measures: HEAD takes its only in-time reading on the **reporting** cadence, so the case had to lower
`LOOP_PROGRESS_SECONDS` to 1 s to be answered at all — and the reading then lands 0.45–0.76 s after the write
against a writer that exists for 2 s. The margin is under a second on a quiet host, and one reading delayed past
the writer's life records the item `done` (`expected 'blocked', got 'done'`, the shape
`guidelines/Backlog.md` ("The loop") already quotes from 2026-10-23). The repair gives the reading a cadence of its own and
widens the case's window to 8 s of write against 10 s of writer, so the same class of delay has ~7 s of room.

The reading happens at the whole second that follows the heartbeat's start, so `elapsed=1` is the first reading in
all three runs; the spread of 0.45–0.76 s is the child's own start-up time, which is what moves the phase.
