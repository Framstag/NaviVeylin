# Evidence — motorway-destination-reaches-the-manoeuvre (issue #877)

Every number below is carried by a file in this directory, with the exceptions named where they appear: a tally
summed from `*/build/test-results/*/*.xml` (sections 1 and 5) and the ABI object timestamps, which are build
trees rather than carried files. Nothing here was re-derived in the tail.

Commits: submodule `901127b84` ("routing: a motorway manoeuvre carries the destination of the node it
was decided at"), superproject `2d2af2af` (the gitlink alone); then, after the review's absence-case finding,
submodule `c7ac5846c` ("test: pin the absence of a carried destination on a motorway change"), superproject
`7d2b6677` (the gitlink alone).

## 1. What ran

| step | command | verdict | log |
|---|---|---|---|
| native case on the pin | `git checkout HEAD~1 -- <2 sources>`; `ninja -C build RouteStepModelTest`; `ctest -R RouteStepModelTest` | **red**: `test cases: 16 \| 15 passed \| 1 failed`, `assertions: 145 \| 144 passed \| 1 failed`, `REQUIRE( change->destination.has_value() )` | `red-on-pin.log` |
| the same case on the commit | restore, rebuild, `ctest -R RouteStepModelTest` | **green**: `100% tests passed out of 1`, 0.03 s | `red-on-pin.log` |
| revert-check (design D2) | line 756's own-record guard → `if (false)`; rebuild; `ctest -R RouteStepModelTest` | **red**: 16 cases / 15 passed / 1 failed, `*change->destination=="Wuppertal"`; restored → `100% tests passed out of 1` | `revert-check.log` |
| native through the app | `./gradlew :app:assembleMobileDebug` | `BUILD SUCCESSFUL in 1m 20s`, 31 executed; `:app:buildCMakeDebug[arm64-v8a]`, `[armeabi-v7a]`, `[x86_64]` all ran, and `RoutePostprocessor.cpp.o` is newer in all three trees (the object timestamps were read from the build tree, not from the log) | `assemble-mobile-debug.log` |
| focused suites | `:core:testDebugUnitTest --tests "*ManeuverFromStepTest" --tests "*ManeuverLabelTest"`; `:auto:testDebugUnitTest --tests "*NavigationTemplateMapperTest"`; `:app:testMobileDebugUnitTest --tests "*NextTurnOverlayTest" --tests "*CarHintContentTest" --tests "*RoutePanelDiagnosticsTest"` | green, `BUILD SUCCESSFUL in 4s / 20s / 24s`; JUnit XML: 12 + 17 + 76 + 8 + 13 + 5 cases, 0 failures, 0 errors (the tallies are summed from the modules' `build/test-results`, which the log does not carry) | `focused-cases.log` |
| the gate, both flavors (first run, `901127b84` / `2d2af2af`) | `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache --continue` | `BUILD SUCCESSFUL in 8m 8s`, 40 executed, 127 up-to-date | `gate.log` |
| the absence case, on the pin and on the patch | `git checkout 901127b84^ -- <2 sources>`, rebuild, `ctest -R RouteStepModelTest -V`; restore, rebuild, same | pin: `17 \| 16 passed \| 1 failed`, `148 \| 147 \| 1` — only the change case, so the absence case is green there; patch: `All tests passed (149 assertions in 17 test cases)` | `red-on-pin-with-absence-case.log` |
| the gate again, after the absence case (`c7ac5846c` / `7d2b6677`) | the same command, `LOOP-GATE-TREE: head=7d2b6677… submodule=c7ac5846c…`, started `17:25:54Z`, 0 `FROM-CACHE` | `BUILD SUCCESSFUL in 7m 10s`, 19 executed, 148 up-to-date; the tallies below, whose XMLs were all rewritten `17:26:01Z`–`17:33:05Z` | `gate-after-absence-case.log` |

The gate's own tallies, summed from `*/build/test-results/*/*.xml` (all files rewritten inside the run, 17:26:01Z–17:33:05Z):

| suite | cases | failures | errors |
|---|---|---|---|
| `:app:testMobileDebugUnitTest` | 2294 (321 XMLs) | 0 | 0 |
| `:app:testAutomotiveDebugUnitTest` | 2294 (321 XMLs) | 0 | 0 |
| `:core:testDebugUnitTest` | 677 (70 XMLs) | 0 | 0 |
| `:auto:testDebugUnitTest` | 974 (99 XMLs) | 0 | 0 |
| `:osmscout-client-java:test` | 112 (23 XMLs) | 0 | 0 |

Both flavors are covered because the change's inputs are native (`BuildEvidence.md` §4b). The tallies are
identical to the first gate's, which is what a test-only second commit should produce. The first gate's log
carries no timestamp of its own, which is why the second run stamps `LOOP-GATE-TREE`, its start and its
verdict; the native compile proof stays the `:app:assembleMobileDebug` row — one `externalNativeBuild`, shared
by both flavors (`app/build.gradle.kts`), so the automotive APK was not assembled a second time. No native
target other than the patched description code changed: the diff is `RoutePostprocessor.{h,cpp}` and
one test file, and the three ABI trees recompiled exactly that translation unit.

Named exclusion: `:core:test` and `:auto:test` are aggregate lifecycle tasks that reject `--tests`
(`Unknown command-line option '--tests'`), so the focused run used `testDebugUnitTest` in both
modules; the gate runs the aggregate `test` over every module, the automotive flavor included.

## 2. The device census — the premise check, and what it refused

`evidence/device-census.txt` carries both routes' lines verbatim, the sha256 that identifies which
build produced each, and the step rows read off the panel.

- **Route A, the issue's own endpoints** (Dortmund `51.57246, 7.46489` → Essen `51.45343, 6.99432`):
  `motorway=4 motorway_dest=3 dest_empty=0` on the pin **and** on the patch. The A 45 → A 40 change
  the issue names words its record on both builds: `10 / 21  Keep slightly right onto A 40 towards
  Essen`.
- **Route B, found by probing** (Dortmund → Duisburg `51.43440, 6.76230`): `motorway=5 motorway_dest=3
  dest_empty=1` on the pin **and** on the patch. The empty step is `10 / 21  Keep straight onto A 3`,
  and it stays without a destination clause on the patched build too — the way carries no tag in this
  extract, so `dest_empty` counts a legitimate absence here, not the loss.

**Verdict: the census did not reproduce the loss.** Task 1.2 asked for `dest_empty > 0` to start the
change and for `dest_empty = 0` to stop it; the measured pair is 0 on the issue's route and 1 on a
route whose empty step is a missing tag, so the key cannot tell the model loss from a missing tag and
the gate settles neither way. The change proceeds on the code-path case instead, and this divergence
is stated here rather than folded into the numbers: a reviewer who wants the census to gate this
change needs a route whose `dest_empty` step gains a destination clause after the patch, and that
route is not among the two measured.

**The deviation is the owner's to lift, and it was lifted.** The review below read D3's stop condition
as met by route A's `dest_empty=0` and withheld the pass on it; the owner answered on 2026-11-14 that
the gate is released for this change — the displacement D1 names is in the description whatever the
census exercised, the census cannot tell a lost record from a missing tag, and the surface observation
keeps its own finding. The answer is quoted in task 1.2, and the alternative it rules out (revert the
gitlink and plan the surface cause first) is not taken.

## 3. What the surfaces show

Phone, route A, first motorway change, on the patched build — `Keep slightly right onto A 40 towards
Essen` (row 10 of 21). The same row reads identically on the pin, which is the point of section 2:
the fix does not change this junction's wording because the record already reached the manoeuvre.

**Owed:** the car panel's target street for the same change (`UICar` wording, task 5.3's second half).
No head unit and no driving session were available; the projection binary needs
`lean-ctx allow desktop-head-unit` and a human at the DHU window (`BuildNative.md` §10a, §2b). The
phone half is measured; the car half is not, and the shared step model means the phone's row is the
only evidence that the record reaches both surfaces.

The observation the issue itself rests on — no destination in Android Auto on route A — is not
reproduced by the model on that route (row 10), so it is worth its own finding: the record reaches
the step, and what suppressed the clause in that session lies in the car surface or its session, not
here. It is **not** filed as an item: `tools/issues/create.sh` refuses a finding that carries neither a
proven cause with the case that exercises it nor a prepared change, and this one is a hypothesis about
the car surface with no measurement behind it. It is recorded here because this change stops at the
model, and a surface change is the next change's job.

## 5. The review round, and what it changed

One independent read-only reviewer — a fresh process that edited nothing, which is why `git status`
was clean before and after it — graded the change against
`tools/check-rule-routes.sh --diff 2d2af2af~1..2d2af2af --change …`. Its verdict was
`LOOP-BLOCKING: 2`, `VERDICT: FAIL`. Every finding, and what it cost:

| finding | grade | outcome |
|---|---|---|
| the change crossed design D3's gate: route A's `dest_empty=0` is the stop condition as written, and the patch landed anyway | BLOCKING | the owner released the gate (§2 above); the answer is in task 1.2 |
| the declared reading set was smaller than the map's: `BuildNative.md` §12 and `DesignNative.md` §5a are derived for the touched paths and named nowhere (`Build.md` §12 does not count) | BLOCKING | named in the proposal, in task 5.4 and in §4 above; `check-rule-routes` exits 0 |
| scenario 1's surface clause quoted "Ruhrschnellweg (A 40) Richtung Essen", which no surface renders — `ManoeuvreLabels.leadsOnto` composes the reference and "towards" | NOTE | the delta now quotes what the app renders, "A 40 towards Essen" |
| scenario 3's case was the pre-existing street-route case, which never runs `InstructionPostprocessor` | NOTE | the case the scenario names is now in the tree: `A motorway change carries no destination when no node carries a record`, green on the pin (`red-on-pin-with-absence-case.log`) and on the patch |
| "every number below is carried by a file here" was untrue for the focused run's JUnit tallies, the gate's per-suite table and the ABI object timestamps | NOTE | the header now names the build-tree source of each, and the gate's own log carries its start and finish stamps |
| `gate.log` carried no timestamp, and only `:app:assembleMobileDebug` was assembled, not the automotive flavor | NOTE | the second gate's log is stamped at both ends (`gate-after-absence-case.log`); the flavors' JVM suites run under one `externalNativeBuild`, which the `[arm64-v8a]`, `[armeabi-v7a]` and `[x86_64]` trees of that assemble cover |
| the fixture's comment claimed `DestinationPostprocessor` produces the record's placement, while its nodes carry no objects and that postprocessor would attach nothing | NOTE | the comment now says the placement is put there by hand and why that shape is the one the postprocessor leaves behind |
| the carried record's provenance is unpinned: `AddDescription` overwrites the node's entry, so a link's record can be the way before the ramp | NOTE | unacted, and stated: design Risk 1 names it, the census cannot show it (both lines are identical), and a route whose record is wrong is the measurement that would |

No finding was accepted unread: the two BLOCKING ones cost the owner's answer and a reading-set
correction, the six NOTEs a delta wording, a case and a comment. The change's contract is unchanged
by the round; the tree it certifies is now `c7ac5846c` / `7d2b6677`.

## 4. Guideline sentences

`DesignNative.md` §5a, §5b and `BuildNative.md` §12 are unchanged: the fix lives inside the submodule's own
`libosmscout` — upstream, behind the contract the app already consumes — patched in one place only, and the
superproject carries the gitlink. §5a is the section this diff is graded by (a defect inside the library behind
an unchanged contract is fixed upstream, in a minimal patch that mirrors its surroundings), and the first pass of
this note named only §5b and `Build.md` §12 — a pointer the rule map does not count as naming
`BuildNative.md` §12. The review's finding is why all three are named now. No wording rule was touched
(`ManoeuvreLabels.kt` and the resources are unmodified), and no `MOTORWAY_LEAVE` gained a
record.