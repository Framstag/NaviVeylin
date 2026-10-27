# Gate evidence — add-configurable-long-press-timeout

`./tools/loop/gate.sh` was run once from the change root on this tree (2026-10-27T18:29Z, a wall-clock
recollection — `gate.sh` prints no timestamp) and exited 0. Its own
summary line is quoted in the item report; each suite it ran wrote the tally below, copied verbatim into
`gate-tallies/`. The numbers are the XMLs' own attributes, not counted again.

- `bash_tools_gen-feature-list.sh_--check-classification.xml` — tests="0" failures="0" errors="0"
- `bash_tools_loop-mutations.sh_--check.xml` — tests="197" failures="0" errors="0"
- `gradlew_test_-PforceTests_-PjvmTestsOnly_--no-build-cache.xml` — tests="0" failures="0" errors="0"
- `tools_check-compose-sandbox-rule-selftest.sh.xml` — tests="0" failures="0" errors="0"
- `tools_check-compose-sandbox-rule.sh.xml` — tests="0" failures="0" errors="0"
- `tools_check-doc-routes-selftest.sh.xml` — tests="0" failures="0" errors="0"
- `tools_check-doc-routes.sh.xml` — tests="0" failures="0" errors="0"
- `tools_check-openspec-hygiene-selftest.sh.xml` — tests="6" failures="0" errors="0"
- `tools_check-openspec-hygiene.sh.xml` — tests="0" failures="0" errors="0"
- `tools_check-portability-selftest.sh.xml` — tests="29" failures="0" errors="0"
- `tools_check-portability.sh.xml` — tests="0" failures="0" errors="0"
- `tools_check-todo-references.sh.xml` — tests="0" failures="0" errors="0"
- `tools_gen-feature-list-selftest.sh.xml` — tests="119" failures="0" errors="0"
- `tools_issue-tooling-selftest.sh.xml` — tests="125" failures="0" errors="0"
- `tools_loop-mutations-selftest.sh.xml` — tests="34" failures="0" errors="0"
- `tools_loop-selftest.sh_--from-git_6f61cffe4f82e91c38349f5a6787c28011466387.xml` — tests="254" failures="0" errors="0"
- `tools_mutate-selftest.sh.xml` — tests="0" failures="0" errors="0"
- `tools_mutate.sh_--mutations_tools_check-doc-routes-mutations.json_--list.xml` — tests="0" failures="0" errors="0"
- `tools_mutate.sh_--mutations_tools_close-mutations.json_--list.xml` — tests="0" failures="0" errors="0"
- `tools_mutate.sh_--mutations_tools_create-mutations.json_--list.xml` — tests="0" failures="0" errors="0"
- `tools_mutate.sh_--mutations_tools_decision-question-mutations.json_--list.xml` — tests="0" failures="0" errors="0"
- `tools_mutate.sh_--mutations_tools_duplicates-mutations.json_--list.xml` — tests="0" failures="0" errors="0"
- `tools_mutate.sh_--mutations_tools_portability-mutations.json_--list.xml` — tests="0" failures="0" errors="0"
- `tools_mutate.sh_--mutations_tools_status-mutations.json_--list.xml` — tests="0" failures="0" errors="0"

## The compiled step's executed count (review round 1, finding 2)

`gate.sh` reads a `<suite>: all N cases passed` line and Gradle prints none, so the gate's own tally for the
compiled step is `gate-tallies/gradlew_test_-PforceTests_-PjvmTestsOnly_--no-build-cache.xml` — tests="0"
failures="0" errors="0", `time="394"`, `BUILD SUCCESSFUL in 6m 34s` — and the 764 in the report's
`LOOP-GATE` line is the seven shell suites (197 + 6 + 29 + 119 + 125 + 34 + 254) with no Kotlin case in it.

The step's executed count is the JVM suite's own. It was taken with the profile's own command
(`tools/loop/loop.conf`: `LOOP_GATE_COMPILED_CMD`), on the corrected tree and after the review round's three
cases were added: `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache` → `BUILD SUCCESSFUL in 6m 16s`
(376 s), console log `jvm-tallies.log`, per-module sums from each task's result XMLs in `module-tallies/`:

- `:app` mobile: tests="2124" failures="0" errors="0" (292 classes, `module-tallies/app-mobile.xml`)
- `:app` automotive: tests="2124" failures="0" errors="0" (292 classes, `module-tallies/app-automotive.xml`)
- `:auto`: tests="885" failures="0" errors="0" (92 classes, `module-tallies/auto.xml`)
- `:core`: tests="646" failures="0" errors="0" (61 classes, `module-tallies/core.xml`)
- `:osmscout-client-java`: tests="116" failures="0" errors="0" (23 classes, `module-tallies/osmscout-client-java.xml`)

5 895 tests, 0 failures, 0 errors. The sums are `module-tallies/module-tallies.py`'s output, retained beside
them so the numbers can be re-taken rather than trusted.

This is the compiled step alone, not a second `gate.sh`: the review round changed test sources, a spec delta and
this evidence, so no shell suite's inputs moved and the round-1 gate's seven suites stand as recorded above. The
JVM half is re-run because the correction added three cases to it, and because this finding is that its count was
never written down.
