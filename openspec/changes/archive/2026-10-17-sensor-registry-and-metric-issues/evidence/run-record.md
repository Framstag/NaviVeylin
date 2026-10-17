# The pipeline's dispatch record (archived task 9.2)

Task 9.2 asked for one dispatch of `.github/workflows/sensors.yml` against `main`, with its run URL and the
report it printed. The owner dispatched it three times on 2026-10-17 after the change was merged; all three
succeeded, and this file is the record.

| run | commit | started (UTC) | mode | result |
|---|---|---|---|---|
| [39071848370](https://github.com/Framstag/NaviVeylin/actions/runs/39071848370) | `86dab1ea` | 21:39:15 | dispatch, `apply=false` | success |
| [39072358248](https://github.com/Framstag/NaviVeylin/actions/runs/39072358248) | `2d048678` | 21:46:38 | dispatch, `apply=false` | success |
| [39072426950](https://github.com/Framstag/NaviVeylin/actions/runs/39072426950) | `2d048678` | 21:47:40 | dispatch, `apply=false` | success |

Everything the workflow does, in order, from the third run's log — the log's own words:

```
Fetch the main branch the guard compares against   (git fetch origin +refs/heads/main:refs/remotes/origin/main)

Guard the run:   bash tools/sensors/guard.sh --ref "$GITHUB_REF"
  env:  GH_TOKEN: ***
  guard: on-main (main)
  guard: head-is-remote-main origin/main 2d048678fed7
  guard: clean-tree
  guard: identity Framstag
  guard: registry 6 rows

Create the declared labels:   bash tools/issues/labels.sh --apply
  0 to create, 51 already present        (the first dispatch created them)

Run the sensors:   bash tools/sensors/run.sh --due --dry-run
  env:  GH_TOKEN: ***
  no rows selected at 2026-10-17T21:47:56Z

Report the rows:   bash tools/sensors/report.sh
  row=build-duration last=never verdict=- measurement=- issue=- clear=0 violation=0 occurrences=0 trend=-
  stale row=build-duration no measurement on record — the row has never run or never filed
  … one pair per row (package-size, hotspot, coverage, broken-main, pr-conflicts)
```

What the record establishes:

- **the guards ran before anything else, and passed**: on `main`, `HEAD` equal to `origin/main` at `2d048678`,
  a clean tree, the identity resolved to `Framstag` — which the pipeline could only do with the repository
  secret, so the token is real, scoped at least to `GET /user`, and the guard's identity term is satisfied by
  a concrete allowlisted person rather than a bot;
- **the label taxonomy was created from the registry**: `sensor/broken-main`, `sensor/build-duration`,
  `sensor/coverage`, `sensor/hotspot`, `sensor/package-size`, `sensor/pr-conflicts` are in the tracker (51
  labels present in total after the step) — the `sensor` namespace is declared in `tools/tooling.json` and
  populated from `sensors.json`, exactly once;
- **nothing was written**: `apply` defaults to false, the run reports `no rows selected` because no row's cron
  matches 21:47 (the daily rows run at 03:00–06:00, `hotspot` on Monday, and the two conditions on even hours
  or at :30), and the tracker's issue count is 161 both before and after — the 159 the migration left plus
  `#160` and `#161`, which were filed by hand today;
- **the token is not echoed**: every step that holds it logs `GH_TOKEN: ***`, and the job's own
  `GITHUB_TOKEN` permissions are the declared split (`Contents: read`, `Issues: write`, `Metadata: read`).

Still owed, and why this record is not 9.3: a run with `apply=true` (task 9.3) has nothing to do at an
arbitrary minute either — a row is selected at its own cron minute — and four of the rows would report
`error` even then, because their inputs are not materialised in this pipeline (filed as **#161**). The
first real filing follows #161, or a dispatch pinned to a row's minute.
