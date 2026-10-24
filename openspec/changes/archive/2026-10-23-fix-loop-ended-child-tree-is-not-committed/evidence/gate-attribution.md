# The gate's first run: red, attributed, and what it cost

`./tools/loop/gate.sh` was run once on this change before the record below was written, and it came back red:

```
LOOP-GATE: <the aggregate pair is quoted and exempted below; the raw line is gate-first-red.log>
  (./gradlew test -PforceTests --no-build-cache, tools/loop-selftest.sh,
   bash tools/loop-mutations.sh --check, tools/loop-mutations-selftest.sh,
   bash tools/gen-feature-list.sh --check-classification, tools/check-doc-routes.sh)
  — failing: tools/loop-selftest.sh tools/loop-mutations-selftest.sh
```

It is kept as `gate-first-red.log`, and the aggregate pair it ends on, `tests=230 failures=6`, is not retained as a tally of its own — the six suites' tallies lived in the tree's build directory, and only the two that failed were copied here, as `tools_loop-selftest.sh.xml` (`tests="98" failures="1"`,
`loop-selftest: 1 of 176 cases failed`) and `tools_loop-mutations-selftest.sh.xml`
(`tests="34" failures="5"`).

**The cause was this change, not the tree.** Two checks read the declared mutation set out of
`tools/loop-mutations.sh` by a textual range — `sed -n '/^MUTATIONS=(/,/^)/p' … | grep -c "^  '"`, in
`tools/loop-mutations-selftest.sh:130` and in the case `mutation#everyDeclaredRecordApplies`
(`tools/loop-cases.sh`) — so the set is the records declared **inside** `MUTATIONS=( … )`. The new record `45.1`
had been appended in a second `MUTATIONS+=( … )` block after the array closed: the harness ran 98 records while
both checks read 97, and each failed on that one number (`expected [97] got [98]`,
`expected [selected 3 of 97 records]`, `expected [names no record of the 97 declared]`, and
`all 97 cases passed` vs `all 98 cases passed`).

**The fix is the shape of the record, not a test weakened:** `45.1` now sits inside `MUTATIONS=( … )`, after
`44.1`. The record's own falsification was re-taken afterwards (`revert-check-45.1.log`: `red-when-mutated: 1 ·
not-noticed: 0 · restore-failures: 0`), and the suites that were red pass in full on their own
(`loop-mutations-selftest: all 34 cases passed`, `loop-selftest: all 176 cases passed`). The gate was then run
once more, on the corrected change — that run is `gate.log`, and it is the one this change is verified by.

**The lesson for the next estimate.** The surface of a change to the mutation set is not the record: it is the
record *plus* every check that derives the set by reading the array's text. `grep -rn "MUTATIONS=(" tools/`
names both of them.
