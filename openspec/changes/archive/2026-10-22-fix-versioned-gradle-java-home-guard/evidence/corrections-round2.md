# Corrections — round 2 (review refusal, corrections-only)

Reviewer verdict on round 1: `LOOP-BLOCKING: 1`, `VERDICT: FAIL`, `LOOP-CORRECTIONS-ONLY: yes`,
`LOOP-DECISION` recommended option (2) — replace the proposal's wrong Surface count with the measured
`grep -c` result, and state the `477 + 5768` split where `6245` is quoted. Both are applied. No case, no
assertion, no workflow line and no guideline sentence changed; this round touches prose and its evidence only.

## 1. The blocking finding — the Surface grep count

Quoted from `proposal.md` before this round:

```
grep -c "org\.gradle\.java\.home" .github/workflows/build.yml: 9 after the change (5 pre-existing, 4 added)
```

The sentence counted *invocations* (`-Dorg.gradle.java.home="$JAVA_HOME"`) under a command that counts
*lines*, and the added block spells the key three different ways. Measured on the reviewed tree, from the
change root:

```
$ grep -c "org\.gradle\.java\.home" .github/workflows/build.yml
10
$ grep -n "org\.gradle\.java\.home" .github/workflows/build.yml
66:      # AGP reads sdk.dir; setup-vcpkg.sh reads sdk.dir + org.gradle.java.home.
71:          org.gradle.java.home=$JAVA_HOME
345:            echo "::error file=gradle.properties::the versioned property source must carry no org.gradle.java.home — …"
462:      # org.gradle.java.home and JAVA_HOME (Gradle 9.6.1, "Daemon JVM
468:      # org.gradle.java.home line (issue #91).
475:        run: ./gradlew -Dorg.gradle.java.home="$JAVA_HOME" :app:assembleMobileDebug
482:        run: ./gradlew -Dorg.gradle.java.home="$JAVA_HOME" :app:generateSbomMobileDebug
491:        run: ./gradlew -Dorg.gradle.java.home="$JAVA_HOME" :app:checkLicensePolicy
504:        run: ./gradlew -Dorg.gradle.java.home="$JAVA_HOME" -I tools/gate-timings.init.gradle.kts test -PforceTests --no-build-cache
525:        run: ./gradlew -Dorg.gradle.java.home="$JAVA_HOME" :koverHtmlReport …
```

- 10 matching lines: **9 pre-existing** (66, 71, 462, 468, 475, 482, 491, 504, 525) + **1 added** (`:345`).
- The added predicate at `:344` spells the key escaped (`org\.gradle\.java\.home`), so this quoted grep —
  which matches a literal `.` — does not match it; the four added lines `:344-347` are the predicate
  (escaped), the `::error` (unescaped), `exit 1` and `fi` (no key).

`proposal.md`'s Surface block now carries those numbers and its `surface:` line is a single `^surface:`
line (the form `tools/loop/measure.sh:157` reads), so the block no longer reports "none".

## 2. The second number — `TOTAL 6245`

`gate-tallies.txt` prints five module tallies summing to 5768 and then `TOTAL 6245 0`; the total is
`477` (the eight shell suites the gate's own `tests=` line counts: 10 + 0 + 115 + 117 + 34 + 156 + 45 + 0)
`+ 5768` (the module XMLs: app mobile 2101, app automotive 2101, auto 876, core 642,
osmscout-client-java 48). `tasks.md` 3.5 now states the split there rather than leaving `6245 tests` to be
read as one population, and records that the gate's `tests=477` counts the shell suites only.

## 3. What this round did not do, and why

- **No gate re-run.** The corrections touch no compiled file (two `.md` files and this note), so the
  structural check stands in per the loop's scope rule. The round-1 gate result (`exit 0`,
  `tests=477 failures=0`, both flavours 3/0 for the new class, timestamps `2026-10-22T08:50:12.730Z` and
  `2026-10-22T08:51:54.115Z`) is the evidence and is not re-created.
- **The property-source half is still not falsified against the real file.** The reviewer's §5 NOTE asks
  for the stricter shape — append the line to `gradle.properties`, run the class, remove, forced green.
  This process cannot do it: the path policy denies `gradle.properties` to the worker
  (`[pi-permission-system] Denied by policy: 'path' for tool 'ctx_shell' for path 'gradle.properties'
  (rule '*gradle.properties')`), the same limitation the reviewer recorded for its own read. Only
  `git show HEAD:gradle.properties | grep -n 'java.home'` is reachable, and it exits 1 (no line), which is
  the state #91 left rather than a falsification. The scan's liveness therefore still rests on the
  fixture-level detectability case. A person or a policy-free process should append the line once.
- **Scenario wording not re-cut.** The reviewer's §1 NOTES (scenario 1's "reported by line number and text"
  is exercised by the detectability case; scenario 2's "fails the job before the SDK work" is proved by the
  step's text and `ci-step-green.log` rather than by an executed `exit 1`) and its §7 NOTE (the requirement
  binds the step's name) are recorded here as known, unaddressed weaknesses of the plan rather than changed
  in a corrections-only round.
