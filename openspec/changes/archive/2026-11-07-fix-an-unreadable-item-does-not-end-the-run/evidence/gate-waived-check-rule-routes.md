# The gate's one red step, attributed — `tools/check-rule-routes.sh`

The gate ran once, by the gate child, over this change root with base
`fc9e0eb012dcfc781bf744d128acfac1cd9138f8`. It is quoted, never re-run. Its `LOOP-GATE` line is carried verbatim
by the gate child's own closing answer (`.pi/issue-loop/items/753/worker-tail-report-gate-output.txt`) and by the
first process's report; it is not retyped here, because the tally it states is the sum of the 25 suite tallies the
gate wrote under `app/build/test-results/loop-gate/` (all 25 copied beside this file under `gate-results/`) and no
single JUnit file holds that aggregate. What the line states: `errors=` 1, the one error being the step below, and
`tests=` 950 over the 25 tallies;
`failures=` 0, every other suite green.

The one `errors=` is that step's own JUnit tally, `gate-tally-check-rule-routes.sh.xml`, beside this file, copied
verbatim from the gate's tally directory (`app/build/test-results/loop-gate/`):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="tools/check-rule-routes.sh" tests="0" failures="0" errors="1" time="2">
  <system-out>::error file=tools/doc-routes.json::the rule "the car's reroute wait is a surface strip, the phone's is a card tint." in UI.md is reached by no glob — a rule no change is told to obey check-rule-routes: 0 glob(s) match no tracked path, 6 rule(s) are reached by no glob, 0 rule(s) state no name check-rule-routes: 0 document(s) are matched by no glob, 0 guideline route(s) carry a document their glob </system-out>
</testsuite>
```

The `system-out` is the step's own last lines cut to 400 bytes by the gate's tally writer (`gate.sh:471`), so the
tally carries only the first `::error` line whole. The step's full output — all six `::error` lines and both
summary lines, recovered verbatim from the gate child's console for its `./tools/loop/gate.sh` call
(`.pi/issue-loop/items/753/events.jsonl`, tool call `call_7khz0h59`) — is copied beside this file as
`gate-step-check-rule-routes.log`:

    ::error file=tools/doc-routes.json::the rule "the queue reads the whole labelled set, and refuses one it could not cover." in Backlog.md is reached by no glob — a rule no change is told to obey
    ::error file=tools/doc-routes.json::the rule "the navigation surface's app-drawn elements are the compass rose, the speed badge and speed-limit sign, the attribution, the map-unavailable notice and the calculation strip." in MapCar.md is reached by no glob — a rule no change is told to obey
    ::error file=tools/doc-routes.json::the rule "a failed reroute is reported on the map surface." in UICar.md is reached by no glob — a rule no change is told to obey
    ::error file=tools/doc-routes.json::the rule "while navigation is active the wait is drawn, never pushed." in UICar.md is reached by no glob — a rule no change is told to obey
    ::error file=tools/doc-routes.json::the rule "the rows' words come from the surface's resources." in UIPhone.md is reached by no glob — a rule no change is told to obey
    ::error file=tools/doc-routes.json::the rule "the car's reroute wait is a surface strip, the phone's is a card tint." in UI.md is reached by no glob — a rule no change is told to obey
    check-rule-routes: 0 glob(s) match no tracked path, 6 rule(s) are reached by no glob, 0 rule(s) state no name
    check-rule-routes: 0 document(s) are matched by no glob, 0 guideline route(s) carry a document their glob does not match

## This change does not touch it

The whole delta against the base is four files, none of them a document a reported rule lives in and none of them
the map the rules are matched against:

```
$ git diff --stat fc9e0eb012dcfc781bf744d128acfac1cd9138f8
 guidelines/Backlog.recipes.md | 14 +++++++++
 tools/loop-cases.sh           | 42 +++++++++++++++++++++++++++
 tools/loop/run.sh             | 28 ++++++++++++++----
 tools/loop/state.sh           | 67 +++++++++++++++++++++++++++++++++++++------
 4 files changed, 138 insertions(+), 13 deletions(-)
```

The one guideline file this change edits adds no `**Rule:` name, so it changes no rule the step reads:

```
$ git diff fc9e0eb012dcfc781bf744d128acfac1cd9138f8 -- guidelines/Backlog.recipes.md | sed -n '/^\+/p' | sed -n '/Rule:/p'
(no output)
```

## It was red before this work — the inputs prove it, not a second measurement

The invariant that failed (`rule(s) are reached by no glob`) reads exactly three kinds of input: the `**Rule:`
names of `guidelines/*.md`, extracted by `extract_rules` (`tools/check-rule-routes.sh:89`) over the document list
of `base_documents` (`tools/check-rule-routes.sh:110`); the route map `tools/doc-routes.json`, turned into the
reached set by `map_reached` (`tools/check-rule-routes.sh:119`); and the tree's tracked paths for the glob side.

Every one of those inputs is byte-identical between the run's base and this tree:

```
$ git diff --stat fc9e0eb012dcfc781bf744d128acfac1cd9138f8 -- tools/doc-routes.json tools/tooling.json \
    guidelines/UI.md guidelines/Backlog.md guidelines/MapCar.md guidelines/UICar.md guidelines/UIPhone.md
(no output)
```

The one reported rule is present, unchanged, in the base's copy of its document — the file has not moved between
the base and this tree, so its line number is the base's too:

```
guidelines/UI.md:97:- **Rule: the car's reroute wait is a surface strip, the phone's is a card tint.** The deliberate
```

The base's tree is therefore this tree minus the four files in the diff above, and on the step's inputs the two
trees are the same bytes: the step's verdict on the base is the verdict the gate measured here. The gate is the
after-run — with every edit of this change in place it printed the same `6 rule(s) are reached by no glob`.

Corroboration, same day, before this change's first edit (21:13): the identical six `::error` lines were read from
a run of this step at 20:08 and archived as
`openspec/changes/archive/2026-11-07-fix-needs-diagnosis-is-no-shaping-verdict/evidence/check-rule-routes-red-before.log`,
and the same six-rule shortfall was attributed to the base, not to a change, in
`openspec/changes/archive/2026-11-07-fix-a-childs-command-is-keyed-by-role-and-class/evidence/gate-waiver-check-rule-routes.txt:68`
(base `ca7c6b6d`, an ancestor of this run's base — `git merge-base --is-ancestor ca7c6b6d fc9e0eb012dcfc781bf744d128acfac1cd9138f8`
exits 0).

So the red is named here, not repaired and not counted against this change: red on inputs this change never opens,
before this work, and unchanged after it.

## What the gate did measure for this change

Every suite but the one above is green: the loop self-tests (`tests="370" failures="0"`, the tally
`gate-results/tools_loop-selftest.sh_--from-git_a3cefa934793b341e0d9290034a39b482e4e93dc.xml` beside this file),
the mutation and rule-route mutations and selftests, the issue tooling, openspec hygiene,
`check-todo-references`, feature-list classification and `loop-spend` — 24 tallies, copied beside this file under
`gate-results/`. The red case the change
answers for is `run#oneUnreadableItemDoesNotEndTheRun`: `red-on-HEAD.xml` (`tests="1" failures="1"`,
2026-11-07T21:13:59Z) beside this file, and `green-on-change.xml` (`tests="1" failures="0"`) beside this file.

## The waiver

LOOP-GATE-WAIVED: tools/check-rule-routes.sh reports 6 guideline rules reached by no glob, in documents this change does not edit (Backlog.md, MapCar.md, UICar.md ×2, UIPhone.md, UI.md) and against a map (tools/doc-routes.json) it does not edit — the step's inputs are byte-identical between the base and this tree, so the red is the base's and stays the base's
LOOP-GATE-WAIVED-SUITE: tools/check-rule-routes.sh
