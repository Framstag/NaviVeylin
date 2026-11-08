# The gate's one failing step, and why this change does not answer for it

The gate ran once on this change's tree (`./tools/loop/gate.sh`, run by the gate child). Its line, carried
verbatim by the tail report:

```
LOOP-GATE: tests=953 failures=0 errors=1 (gradlew jvm build gate + 27 shell tool suites, 28 steps) — failing: tools/check-rule-routes.sh
```

The aggregate behind that line is the awk sum over the 28 step tallies copied here as
`evidence/gate-tally/` — `tests="953" failures="0" errors="1"`, retained as
`evidence/gate-tally/LOOP-GATE-aggregate.xml`. Every counted case carries no failure; the one error is the
step named below.

## The failing output

From the gate child's own print, copied whole as `evidence/gate-output.log` (the step's block, lines
1846–1855, also standalone as `evidence/check-rule-routes-gate-step.log`):

```
gate: tools/check-rule-routes.sh
::error file=tools/doc-routes.json::the rule "the queue reads the whole labelled set, and refuses one it could not cover." in Backlog.md is reached by no glob — a rule no change is told to obey
::error file=tools/doc-routes.json::the rule "the navigation surface's app-drawn elements are the compass rose, the speed badge and speed-limit sign, the attribution, the map-unavailable notice and the calculation strip." in MapCar.md is reached by no glob — a rule no change is told to obey
::error file=tools/doc-routes.json::the rule "a failed reroute is reported on the map surface." in UICar.md is reached by no glob — a rule no change is told to obey
::error file=tools/doc-routes.json::the rule "while navigation is active the wait is drawn, never pushed." in UICar.md is reached by no glob — a rule no change is told to obey
::error file=tools/doc-routes.json::the rule "the rows' words come from the surface's resources." in UIPhone.md is reached by no glob — a rule no change is told to obey
::error file=tools/doc-routes.json::the rule "the car's reroute wait is a surface strip, the phone's is a card tint." in UI.md is reached by no glob — a rule no change is told to obey
check-rule-routes: 0 glob(s) match no tracked path, 6 rule(s) are reached by no glob, 0 rule(s) state no name
check-rule-routes: 0 document(s) are matched by no glob, 0 guideline route(s) carry a document their glob does not match
gate: tools/check-rule-routes.sh — 2s
```

The failing suite's own tally is `evidence/gate-tally/tools_check-rule-routes.sh.xml`:
`tests="0" failures="0" errors="1"`.

## What the failing step measures

`tools/check-rule-routes.sh` reads the route map (`tools/doc-routes.json`), the `**Rule:` spans of the
guideline documents it maps, and the tree's tracked paths (for the glob-reaches-nothing invariant). The red
is one fact stated six times: a `**Rule:` name no glob in the map reaches, first named as the reroute rule
"the car's reroute wait is a surface strip, the phone's is a card tint." at `guidelines/UI.md:97`.

## Red before this work

The change's own diff against the run's base `ba96953f6d43df8ac5926c23697a75294cc10af2` names three paths:

```
M app/src/main/java/com/naviveylin/navigation/NavigationEngine.kt
M app/src/test/java/com/naviveylin/navigation/NavigationEngineStepIndexTest.kt
M guidelines/Design.md
```

Of the check's inputs, only `guidelines/Design.md` is touched — and its one hunk rewrites a bullet that
carries no `**Rule:` marker (the diff's `^[+-]` lines contain zero `**Rule:` occurrences, so no rule name is
added, removed or reworded). The five documents the six errors name — `guidelines/Backlog.md`,
`guidelines/MapCar.md`, `guidelines/UICar.md`, `guidelines/UIPhone.md`, `guidelines/UI.md` — plus
`tools/doc-routes.json` are byte-identical to base (`git diff ba96953f… -- <those paths>` is empty), so all six
unreached rules existed unreached on the base tree. The failing rule itself is present at base:
`guidelines/UI.md:97`. The change adds no tracked path, so no glob that matched before matches nothing now
(`0 glob(s) match no tracked path` in the gate's own tally).

The tree's own history carries the same six errors recorded before this change existed: the archived change
`2026-11-08-fix-car-poi-results-cap` (its evidence written 02:36, this change directory created 02:50) quotes
the identical six `::error` lines and the identical `6 rule(s) are reached by no glob` tally as its own
`errors=1` in `openspec/changes/archive/2026-11-08-fix-car-poi-results-cap/evidence/gate-attribution-rule-routes.md`.
Red before this work.

## Still red after this work

The gate's own print after the change is the six lines above with the same
`6 rule(s) are reached by no glob` summary (`evidence/gate-output.log`), and the check is a pure function of
the inputs just enumerated — which this change leaves byte-identical except for a `Design.md` bullet that adds
no `**Rule:` line and no tracked path. Same inputs → same verdict before and after. The failing cases live in
five guideline documents this change never edits, and the change's own cases are answered by the gate's green
steps: `failures=0` across all 953 counted cases, `BUILD SUCCESSFUL` in the gradle step
(`evidence/gate-tally/gradlew_test_-PforceTests_-PjvmTestsOnly_--no-build-cache.xml`), and its red-on-base
case `startNavigation_publishesTheRoutesStepsBeforeAnyPositionUpdate` retained as
`evidence/TEST-NavigationEngineStepIndexTest-red-on-HEAD.xml` (`tests="3" failures="2"`).

## The waiver

The step is named rather than closed over in silence:

```
LOOP-GATE-WAIVED: the single red tally is errors=1 in tools/check-rule-routes.sh — six **Rule:** lines in Backlog.md, MapCar.md, UICar.md, UIPhone.md and UI.md that no glob in tools/doc-routes.json reaches, in five guideline documents and a route map this change leaves byte-identical to base ba96953f while it edits only NavigationEngine.kt, its test and one Design.md bullet that carries no **Rule: line — red before this work and red after it
LOOP-GATE-WAIVED-SUITE: tools/check-rule-routes.sh
```
