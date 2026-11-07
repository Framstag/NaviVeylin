# The round-2 gate: one failing step, attributed to work this change does not touch

The gate was run once, in correction round 2, from the change root against the run's base
`4367cb4debdba8d99a1cbedf3daf5cf0d06bf4fd`. It planned eight steps and ran all eight: seven green, one
red. Over the whole run it counted 41 cases with no failure and one error — the error is the
classification step's tally below, and the 41 is the two self-test steps' case counts summed
(`tools/check-doc-routes-selftest.sh` 34 cases, `tools/check-openspec-hygiene-selftest.sh` 7, both
all-passing, read from the gate's own `LOOP-MEASURE-TALLY app/build/test-results/loop-gate/…` lines).
The run's own record of the gate line, verbatim, is the round-2 worker report in the loop's item directory
for issue #736; the per-step tallies are here.

| step | time | verdict | tally |
|---|---|---|---|
| `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache` | 275s | green | `gate-round2-gradlew-test.xml` (`tests="0" failures="0"`, `BUILD SUCCESSFUL in 4m 35s`) — its own tally counts no cases, so the gate's case count comes from the self-test steps in this table |
| `bash tools/gen-feature-list.sh --check-classification` | 3s | **red** | `gate-round2-gen-feature-list.xml` |
| `tools/check-doc-routes.sh` | 1s | green | |
| `tools/check-doc-routes-selftest.sh` | 7s | green | |
| `tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list` | 1s | green | |
| `tools/check-todo-references.sh` | 1s | green | |
| `tools/check-openspec-hygiene.sh` | 0s | green | |
| `tools/check-openspec-hygiene-selftest.sh` | 0s | green | |

So every step that reads a path this change edits is green — the compiled file, the `openspec/` artifacts
and their route checks — and the one red step reads a file this change does not write.

## The failing step, and what it printed

`bash tools/gen-feature-list.sh --check-classification` — its tally
`tests="0" failures="0" errors="1"` (`gate-round2-gen-feature-list.xml`), system-out
`car-only areas: 1 highlights: 19 gen-feature-list: classification gate failed`, console:

```
gen-feature-list: unclassified spec id(s):
  auto/off-route-indicator (introduced by the open change car-reroute-without-losing-the-map — classify it in that change, before it archives)
  tooling-test-layout (introduced by the open change separate-tooling-test-code — classify it in that change, before it archives)
gen-feature-list: stale classification entry for a spec that no longer exists:
  search-name-matching
  search-result-row-icon
  start-map-selection
gen-feature-list: classification gate failed
```

## Why no id in that list is this change's

- None of the five ids is this change's capability. `car-host-fault-isolation` carries its own
  classification row at `tools/feature-list/specs.json:49`
  (`{ "area": "car-only", "userVisible": false, "surfaces": ["car"] }`), and this change writes no file
  under `tools/feature-list/`: its changed paths are
  `auto/src/main/java/com/naviveylin/auto/NavigationManagerController.kt`,
  `openspec/specs/car-host-fault-isolation/spec.md`, and this change directory.
- The two unclassified ids are other open changes' deltas, standing in the same worktree and each owing
  its own classification before it archives:
  `openspec/changes/car-reroute-without-losing-the-map/specs/auto/off-route-indicator/` and
  `openspec/changes/separate-tooling-test-code/specs/tooling-test-layout/`.
- The three stale entries were red before this work and stay red after it, unchanged: at the run's base
  `4367cb4debdba8d99a1cbedf3daf5cf0d06bf4fd`, `git show <base>:tools/feature-list/specs.json` carries
  three lines naming them (`grep -cE '"(search-name-matching|search-result-row-icon|start-map-selection)"'`
  → `3`), while `git ls-tree --name-only <base> openspec/specs/` matches none of them (→ `0`). The
  current tree reads the same `3` keys and `0` spec directories, so nothing this change wrote moved that
  count in either direction.

Fixing the step means writing two other items' classification rows and three dead entries that predate
the base — neither is this change's claim, and neither is a path this change edits.
