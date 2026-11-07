# The gate's one failing step, and why this change does not answer for it

The gate ran once, by the gate child, over this change's tree
(`./tools/loop/gate.sh --base 580c2106c9a9294d63d2bd621fb8e265dbb7dca8`). Its tally:

```
LOOP-GATE: tests=944 failures=0 errors=1 (shell gate, 25 steps; failing: tools/check-rule-routes.sh — 6 rules reached by no glob, in Backlog.md/MapCar.md/UICar.md/UIPhone.md/UI.md, untouched by this diff; loop-spend-selftest all 27 cases passed) — not retained as one JUnit XML: the gate is a shell suite and its aggregate is the sum of the per-step tallies `gate.log` lists one by one, of which the failing step carries no case at all.

The one `errors=` is `tools/check-rule-routes.sh`, exit `1`, from the gate's own log
(`evidence/gate.log`, `::error` lines at the step named `gate: tools/check-rule-routes.sh`):

```
::error file=tools/doc-routes.json::the rule "the queue reads the whole labelled set, and refuses one it could not cover." in Backlog.md is reached by no glob — a rule no change is told to obey
::error file=tools/doc-routes.json::the rule "the navigation surface's app-drawn elements are the compass rose, the speed badge and speed-limit sign, the attribution, the map-unavailable notice and the calculation strip." in MapCar.md is reached by no glob — a rule no change is told to obey
::error file=tools/doc-routes.json::the rule "a failed reroute is reported on the map surface." in UICar.md is reached by no glob — a rule no change is told to obey
::error file=tools/doc-routes.json::the rule "while navigation is active the wait is drawn, never pushed." in UICar.md is reached by no glob — a rule no change is told to obey
::error file=tools/doc-routes.json::the rule "the rows' words come from the surface's resources." in UIPhone.md is reached by no glob — a rule no change is told to obey
::error file=tools/doc-routes.json::the rule "the car's reroute wait is a surface strip, the phone's is a card tint." in UI.md is reached by no glob — a rule no change is told to obey
check-rule-routes: 0 glob(s) match no tracked path, 6 rule(s) are reached by no glob, 0 rule(s) state no name
check-rule-routes: 0 document(s) are matched by no glob, 0 guideline route(s) carry a document their glob does not match
```

## Red before this work

The six rules live in six `.md` files and the glob table that routes them, and none of those seven paths is changed by
this work. Over the whole diff this change adds (`git diff --stat 580c2106c9a9294d63d2bd621fb8e265dbb7dca8..HEAD`),
the changed paths are:

```
 .pi/skills/loop-spend/SKILL.md |  23 +++++-
 guidelines/Build.md            |   2 +-
 tools/loop-spend-selftest.sh   | 173 +++++++++++++++++++++++++++++++++--------
 tools/loop-spend.py            |  45 +++++++++--
```

and the same range over the failing step's inputs is empty:

```
$ git diff --stat 580c2106c9a9294d63d2bd621fb8e265dbb7dca8..HEAD -- Backlog.md MapCar.md UICar.md UIPhone.md UI.md tools/doc-routes.json
$
```

The check reads only `tools/doc-routes.json` and the documents it routes, so the same script over the same unchanged
inputs is red at the base commit by construction: no line this change adds or removes is an input to it.

## Still red after this work

After the gate, the step alone was run once more (not the gate) and answered the same six rules with the same exit
code (`evidence/check-rule-routes-after.log`, also at
`.pi/issue-loop/items/822/scratch/check-rule-routes-after.log`):

```
$ bash tools/check-rule-routes.sh; echo "CHECK-RULE-ROUTES-EXIT=$?"
[the six ::error lines above, byte-identical]
CHECK-RULE-ROUTES-EXIT=1
```

`6 rule(s) are reached by no glob` before and after, in the same six files, and the step is red for a reason this
change's four files cannot reach. It is therefore named here and waived, not repaired and not counted against this
change: `LOOP-GATE-WAIVED` carries the clause and `LOOP-GATE-WAIVED-SUITE` the suite (`tools/check-rule-routes.sh`).

## What the gate did measure for this change

Inside the same gate, `bash tools/loop-spend-selftest.sh` printed `loop-spend-selftest: all 27 cases passed`
(`evidence/spend-suite-green.log`), including the four cases this change adds. The gate's 944 counted cases with no
failure carry them; the one error is the waived step above.
