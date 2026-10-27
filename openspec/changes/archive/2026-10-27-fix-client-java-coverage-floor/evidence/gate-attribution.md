# The gate's one failure, attributed

`./tools/loop/gate.sh` ran once, at 2026-10-27, and printed:

```
LOOP-GATE: tests=703 failures=1 errors=0 (./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache 251s,
tools/loop-selftest.sh --from-git 4a4b3a9554dc25fee6fa9cb441b6d2f0ec5c5a46 413s, … ) — failing:
tools/loop-selftest.sh --from-git 4a4b3a9554dc25fee6fa9cb441b6d2f0ec5c5a46
```

The compiled step it also ran is green (`./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache`,
251 s, this change's module suite included). The one failing case is the previous landing's
`adapter#theChildIsStartedWithTheResultBound`, and it is not this change's: the gate's 30 per-suite tallies
are beside this file (`evidence/gate/*.xml`), their sum is `evidence/gate-summary.xml`
(`tests="703" failures="1" errors="0" time="1097"`) and the gate's own line is
`evidence/gate-summary.log`. The failing suite's tally is `evidence/gate/
tools_loop-selftest.sh_--from-git_4a4b3a9554dc25fee6fa9cb441b6d2f0ec5c5a46.xml`
(`tests="247" failures="1"`).

- **Reproduced on its own.** `bash tools/loop-selftest.sh --case
  'adapter#theChildIsStartedWithTheResultBound'` → `1 of 1 cases failed`, first line: "a child started by
  hand, with no event stream, names no record file and writes nothing into the project: expected '', got
  '<project>/.pi/issue-loop/items/320/tool-result-trims.log'".
- **The mechanism.** The case unsets one of the two variables the adapter reads before it starts a child by
  hand — `env -u LOOP_CHILD_EVENTS …` (`tools/loop-cases.sh:6668`) — and asserts that the record path the
  adapter hands the child is empty (`tools/loop-cases.sh:6683`). A worker child of this loop carries **both**
  `LOOP_CHILD_EVENTS` and `LOOP_TOOL_RESULT_TRIM_RECORD` in its environment (both point into the item's own
  directory), so the second one leaks into the case's child and the assertion measures the run's record file
  instead of the adapter's silence.
- **Nothing in this change reaches it.** The delta is `osmscout-client-java/src/test/**` and `openspec/**`;
  the failing case declares `tools/loop-cases.sh tools/loop/loop.conf tools/pi-child-events.sh
  tools/pi-trim-result.mjs` as the files it owns, and the case ran only because the gate's diff carries the
  previous landing, whose files those are. The full suite was re-run after the gate to attribute, not to
  improve, its number: `1 of 247` failed, the same case (`evidence/selftest-rerun.log`).
- **What it would take, and why this change does not do it. One word beside the existing `-u` on
  `tools/loop-cases.sh:6668` (`-u LOOP_TOOL_RESULT_TRIM_RECORD`) completes the rule the case's own comment
  already states — "the suite may itself run inside a child whose run exported one". Round 1 recorded the
  red and left the word to the engine's own item.

The first round's gate is therefore recorded as it printed it, red, in `evidence/gate-summary-round1.log`;
the item's own green evidence is the module's suite (`106` cases, `0` failures,
`evidence/module-test-results/` and `evidence/module-suite-tally.txt`) and the probe
(`evidence/green-on-change.xml`).

## Round-2 correction

The review of round 1 refused the change with this case as one of its three blocking findings and named the
word, so the correction was made here rather than left to a later item: the hand-started invocation now runs
`env -u LOOP_CHILD_EVENTS -u LOOP_TOOL_RESULT_TRIM_RECORD`, and the case measures the adapter's silence rather
than the record path the run exports into every child. The gate was re-run once on the corrected tree and is
green: `LOOP-GATE: tests=703 failures=0 errors=0` (`evidence/gate-summary.log`), with the loop suite at
`tests="247" failures="0"` (`evidence/gate/tools_loop-selftest.sh_--from-git_4a4b3a95….xml`) where this
attribution had recorded `1 of 247`.
