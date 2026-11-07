# Gate attribution — the one red step is not this change's

`./tools/loop/gate.sh` ran once (2026-11-02). The run ended with `errors=1`:

```
LOOP-GATE: … errors=1 (./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache 340s, …,
bash tools/gen-feature-list.sh --check-classification 3s, …) — failing: bash tools/gen-feature-list.sh --check-classification
```

The per-suite tallies the line sums are in `evidence/gate/` (10 XMLs, copied from
`app/build/test-results/loop-gate/`): every suite's own tally records `failures="0" errors="0"` except the
failing step's, which is `tests="0" failures="0" errors="1" time="3"`
(`evidence/gate/bash_tools_gen-feature-list.sh_--check-classification.xml`).

The JVM half is green (BUILD SUCCESSFUL in 5m 40s; `gate-tallies.txt`: 782 files, 6009 tests, 0 failures,
0 errors). The failing step names only spec ids two **other** open changes in this shared worktree introduce:

```
gen-feature-list: unclassified spec id(s):
  auto/off-route-indicator (introduced by the open change car-reroute-without-losing-the-map — classify it in that change, before it archives)
  tooling-test-layout (introduced by the open change separate-tooling-test-code — classify it in that change, before it archives)
gen-feature-list: stale classification entry for a spec that no longer exists:
  search-name-matching
  search-result-row-icon
  start-map-selection
specs read: 182
classified: 185
unclassified: 2
delta spec ids: 15
stale: 3
```

This change's delta is `mini-map`, an existing, classified capability; it introduces no spec id and removes none.

## Two measurements that place the red outside this change

1. **The base tree, without any open change** — `git archive e190d8729eda946da6de579151552a5203055c61 FEATURES.md tools openspec | tar -x -C /tmp/729-base` then `cd /tmp/729-base && bash tools/gen-feature-list.sh --check-classification` (the `/tmp/729-base` scratch tree is not retained): `exit=0`, `unclassified: 0`, `stale: 3`. The three stale entries predate this run and the check tolerates them; they are not what fails the gate.
2. **A copy of this worktree's `FEATURES.md`, `tools/` and `openspec/` with the two other open change
   directories removed** (`/tmp/729-mine`, this change kept; not retained): `exit=0`, `unclassified: 0`,
   `delta spec ids: 2` (this change's `mini-map` delta plus the complete-but-unarchived
   `fix-guideline-routes-name-their-document` delta).

So the same command is green with this change present and red only when either of those two changes' deltas is
present. The fix that would turn it green — classifying `auto/off-route-indicator` and `tooling-test-layout` —
belongs to their changes, and writing it here would be a claim about work this item did not gate.

Per `guidelines/BuildEvidence.md` §4d the failing set and the isolated run are quoted together here rather than
retried; the gate itself was not re-run.
