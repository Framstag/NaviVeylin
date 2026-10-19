# fix-loop-stop-reason — tasks

- [x] 1.1 A case over a finished run that asserts the run names the condition that ended it, records it in
      the state, and that the report repeats the same sentence. Red on HEAD: no `stop:` line at all.
- [x] 1.2 The case reads the run's own line, not the report's — the first version passed with the run silent
      because the report repeats the text.
- [x] 2.1 `state.sh stop <reason>` records `run.stop.reason` (and its timestamp).
- [x] 2.2 `run.sh` records on every path that leaves the loop: the `next` conditions, `gh could not read`,
      a refused start, a failed in-progress write.
- [x] 2.3 `report.sh` and `state.sh show` print the reason; the report says so when nothing is recorded.
- [x] 3.1 Mutations 16.1, 16.2, 16.3, 20.3: four red, four green after the restore.
- [x] 3.2 `loop-selftest` 76/76, and the pre-change state file stays readable (`// ""` defaults, digest
      re-signed by `patch_state`).
