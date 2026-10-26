# Proposal — fix-the-hotspot-bound-fits-churn

## Why

One defect, verified in this tree, issue #665: **the `hotspot` row's bound is smaller than a single changed file, so
the row measures that a path exists rather than that it churns.**

**Root cause (one).** The row's score is `size_in_bytes × times_changed` over the last 200 commits
(`tools/sensors/hotspot-report.sh:26-37`), and its bound is `5000` with `comparison: greater-than`
(`tools/sensors/sensors.json:50`) — below the size of one changed file, so **one** change of a 5 kB path crosses it.
The metric is a product, so a bound `b` is passed by a path changed once as soon as `size > b`; the row therefore
files for nearly every path a 200-commit window has touched. Nothing else is involved: the script holds no threshold
of its own (its own case proves that), the binding and the clearing read the same number, and `maxFilings: 10`
(issue #666) bounds how many of those items one tick writes — it does not make the row's verdict right.

**Verified, not quoted from the tracker.** Measured in this tree on the base commit `642c0278`, by the row's own
command (log `evidence/probe-hotspot-bounds.log`, 2026-10-26T22:38:19Z):

```
$ SENSORS_ID=hotspot SENSORS_ROOT=$PWD SENSORS_THRESHOLD=<n> SENSORS_CONDITIONS='[]' \
    SENSORS_PROFILE=tools/tooling.json SENSORS_PRINT_RANKING=0 bash tools/sensors/hotspot-report.sh | grep -c '"verdict":"violation"'
threshold=5000    violations=358
threshold=1000000 violations=7
$ git ls-files -z | xargs -0 -r wc -c | awk '$NF != "total"' | sort -rn | head -1
 535814 tools/loop-cases.sh
```

358 paths past 5 000 against 7 past 1 000 000, and the largest file the row watches is 535 814 bytes — so at
`threshold: 5000` a single change of any file over 5 kB is a hotspot, which is the item's filed evidence (337 items,
one every 6 s, tick `07:41:51Z` of 2026-10-26) and the mechanism a host test can settle.

**The red test** — `hotspot#theBoundIsAboveOneChangedFile`, failing on the base because the bound admits one change:
`a path changed once is not a hotspot, whatever its size: '"path":"once.txt"' unexpectedly present in: hotspot:
score=1400378 path=churned.txt size=200054 changes=7 / hotspot: score=6000 path=once.txt size=6000 changes=1`,
`tests="1" failures="1"` (`evidence/red-on-head-hotspot#theBoundIsAboveOneChangedFile.xml`, log
`evidence/red-on-head.log`, 2026-10-26T22:39:01Z). The case asserts the property, not the number: it reads the
row's bound and requires it to be above the largest tracked file, so a refit is one edit in the registry and a tree
that grows a larger file reddens the suite instead of silently restoring the defect.

**Owning capability and guideline section.** The behaviour is a new requirement of `backlog-sensors`, beside *A
check is a declared row, not a script* — the requirement whose "the bound the measurement is compared against" is what this row
declares (`openspec/specs/backlog-sensors/spec.md:11`). The prose that owns a row's bound is `guidelines/Backlog.md`
§9 "Checks", whose `hotspot` sentence (`guidelines/Backlog.md:840`) states the metric and, until this change, the
wrong half of what it measures.

surface: 1 case added, 0 rewritten, 0 fixtures realigned, 1 mutation record added (8.31), 1 guideline sentence
rewritten, 1 requirement added with 1 scenario, 1 registry value and 1 registry comment clause — the counts are
`bash tools/sensor-selftest.sh --list | wc -l` (65 declared cases, 64 on the base), `grep -c "^  '8\." tools/sensor-mutations.sh` (30 records on the base, 31 after),
`grep -rn "5000" tools/sensors/ tools/sensor-cases.sh tools/sensor-mutations.sh guidelines/ openspec/specs/ .github/`
(the one bound site is `tools/sensors/sensors.json:50`; the other hits are a fixture size and a JVM heap note) and
`grep -c -i hotspot openspec/specs/backlog-sensors/spec.md` (0 — no shipped requirement names the row) —
`evidence/surface-head.log`.

## What changes

- `tools/sensors/sensors.json` — the `hotspot` row's `threshold: 5000` becomes `1000000`, and the registry's own
  comment states the rule the number obeys: a churn row's bound sits above the largest file it watches. 1 000 000
  is above this tree's largest tracked file (535 814 bytes) and below its seventh real hotspot (1 504 404), so it
  separates churn from existence with headroom on both sides — the fit is measured, not chosen from the top seven's
  magnitude alone.
- `guidelines/Backlog.md` §9 — the `hotspot` sentence gains the fit and its measurement: one change is not heat,
  the bound is above the largest file the row watches, and the old bound's cost (358 paths past it, 337 items in one
  tick) is named as the reason the number exists.
- `tools/sensor-cases.sh` — `hotspot#theBoundIsAboveOneChangedFile` in the declared set and the dispatch table: a
  fixture repository where a 6 000-byte path changed once and a 200 000-byte path changed seven times, the bound read
  from the registry, and the fit asserted against the largest tracked file of the tree the row watches.
- `tools/sensor-mutations.sh` — `8.31` (the row's bound back at 5 000) reddens that case.
- `openspec/specs/backlog-sensors/spec.md` — one added requirement, one scenario.

## Capabilities

### Modified Capabilities

- `backlog-sensors`: adds *A churn bound is above one change* — the rule a row that multiplies size by change count
  obeys, so its bound separates churn from existence.

## Impact

`tools/sensors/sensors.json`, `tools/sensor-cases.sh`, `tools/sensor-mutations.sh`, `guidelines/Backlog.md` §9, the
spec delta. No compiled file, so no Gradle suite is owed by these paths; the shell suites the profile maps for
`tools/sensors/**`, `tools/sensor-*.sh`, `*.md` and `openspec/**` are owed, and the gate runs them once. Additive: no
engine code changes, no field is added or removed, and a tree whose registry keeps a smaller bound behaves as it does
today. Rollback: revert the branch — the number and one guideline sentence return, and the shipped row is the old
one.

## Spec

`ADDED` **A churn bound is above one change** in `backlog-sensors`, in the delta's own words. Scenario: *One change
of a file the row watches is not a hotspot* (`hotspot#theBoundIsAboveOneChangedFile`).

No existing requirement changes: *A check is a declared row, not a script* still says a row declares the bound it
compares against, and this change only fits one row's number.

## Open

The item's two other candidate repairs are eliminated by the measurement rather than ranked, so no fork is left:

- **Normalise by the window** (`size × changes / window`) — arithmetically the same inequality at
  `threshold × window` (a 1 000 000 bound over 200 commits *is* 5 000 bytes churned per commit), so it buys no
  discrimination and costs a rewrite of the score the finding text prints, plus the case that names the metric.
- **Scope the row to the source roots** — the seven paths past 1 000 000 are `tools/loop-cases.sh`,
  `openspec/specs/agent-issue-loop/spec.md`, `guidelines/Backlog.md`, `tools/loop-mutations.sh`, `tools/loop/run.sh`,
  `guidelines/Build.md` and `tools/sensor-cases.sh`; a source-root scope would drop most of them, so the row would
  stop reporting exactly the churn it exists to report. `source-size` already owns "this file is too big", which is
  why this row owns churn only — not why it may not watch the prose that churns.
