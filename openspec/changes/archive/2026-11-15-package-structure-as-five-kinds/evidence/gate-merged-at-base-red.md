# The merge's gate, and the one red it carries

`evidence/gate.log` is `tools/loop/gate.sh --base 07694555` on the merged `main`, and it ends with

```
LOOP-GATE: tests=106 failures=0 errors=1 (./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache 764s, …) — failing: tools/check-rule-routes.sh
```

The one error is `tools/check-rule-routes.sh`, and it is the base's, not this delta's
(`guidelines/BuildEvidence.md` §4d, issue #921): the same check run on the base commit `07694555`
(`.pi/worktrees/base-check`) reports **8** rules reached by no glob, the merged tree **7**, and the rule that
stopped being unreached is not one this change adds — this change's pathspec holds no `tools/doc-routes.json`
and no `UIPhone.md` line. `evidence/tools_check-rule-routes.sh.xml` is the step's own tally in this run, and
`evidence/check-rule-routes-red-before-base.log` the base-red measurement the change already recorded.

Everything else the merged tree owes is green, and its numbers are in the run's tally:
`gradlew_test_-PforceTests_-PjvmTestsOnly_--no-build-cache.xml` (`errors="0"`, 764 s),
`bash_tools_gen-feature-list.sh_--check-classification.xml`, the per-class architecture tallies
(`TEST-com.naviveylin.architecture.*.xml`: 7 declaration cases, 2 hook cases, 1 suite-list case, the rule
classes), and `gate.log` itself for the 18 further suites.