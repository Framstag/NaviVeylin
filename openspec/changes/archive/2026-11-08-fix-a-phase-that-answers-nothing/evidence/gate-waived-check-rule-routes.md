# The gate's one failing step, and why this change does not answer for it

The gate ran twice over this change's tree — once by the gate child of round 1 and once by the correction round's
own session (`evidence/gate-round2.log`) — and both tallied the same. The gate child's own line — carried verbatim by
the tail report and copied here with the step's output as `evidence/check-rule-routes-gate-step.log` — reads
`tests=953 failures=0 errors=1`, failing: `tools/check-rule-routes.sh`, and this tally is not retained as one JUnit XML (the gate is a shell suite whose aggregate is the sum of the per-step prints `gate.log` lists one by one, of which the failing step carries no case at all). The 953 counted cases carry no failure; the one error is the step named below.

## The failing output

From the gate child's own print (`evidence/check-rule-routes-gate-step.log`, a verbatim copy of the step's lines in
`.pi/issue-loop/items/810/worker-tail-report-gate-output.txt`):

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

The failure is one fact stated six times: a rule name extracted from a guideline document that no glob in
`tools/doc-routes.json` reaches. It is a property of the glob table and of the documents' `**Rule:` spans, printed at
`tools/check-rule-routes.sh:226`.

## Red before this work

The diff the round before the gate read (every `diff --git` header of `review-pre-gate.diff`, read whole, 254 lines) is
five paths:

```
guidelines/Backlog.md
guidelines/Backlog.recipes.md
tools/loop-cases.sh
tools/loop-mutations.sh
tools/loop/run.sh
```

The check's inputs are: the map at the path `tools/tooling.json` names, the `**Rule:` spans extracted from the base
guideline documents (`base_documents`, which skips `*.why|*.recipes`, at `tools/check-rule-routes.sh:110`, feeding
the extraction loop at `tools/check-rule-routes.sh:209`), the map's own reached set (`tools/check-rule-routes.sh:119`)
and the tree's tracked paths, read only for the glob-matches-nothing invariant.

The diff the change lands also carries `openspec/specs/agent-issue-loop/spec.md` and the change's own archive directory
under `openspec/changes/archive/`: neither is an input to this check, whose documents are the base guideline files and
whose paths are the map's own globs.

Of the five changed paths, only `guidelines/Backlog.md` is an input, and the diff's one hunk there
(`guidelines/Backlog.md:301`) rewrites a pointer paragraph — a grep of the whole diff for `**Rule` returns zero
matches, so no rule name is added, removed or reworded and the extraction the check reads is byte-identical to the
base's. `guidelines/Backlog.recipes.md` is not a base document. The other four paths are not inputs at all, and the
change adds no path, so no glob that matched before matches nothing now.

The same script over the base tree `8280d8aa` therefore prints the same six errors: red before this work by
construction, no re-run needed. The tree's own history carries the same six, byte-identical with the same summary —
the archived change `2026-11-07-fix-an-unreadable-item-is-not-the-end-of-the-run` recorded them before this item
started, and its record is copied here as `evidence/check-rule-routes-red-before.log`.

## Still red after this work

The gate's own print after the change is the six lines above with the same `6 rule(s) are reached by no glob`
summary (`evidence/check-rule-routes-gate-step.log`), and the first process's pre-gate measurement of the same four
check commands ends with that same summary (`evidence/doc-checks.log`). The correction round's gate prints the same six
lines and the same summary (`evidence/gate-round2.log`). Red before, red after, same six rules, same inputs.

## The waiver

The step is named rather than closed over in silence: `LOOP-GATE-WAIVED` carries the clause, and
`LOOP-GATE-WAIVED-SUITE` names the suite it names — `tools/check-rule-routes.sh`. What the gate did measure for this
change stands: its counted cases carry no failure, and the change's own red case is green on the tree as it stands
(`evidence/green-red-case-round2.log`, 2026-11-07T23:40:24Z) with its red-on-base run retained
(`evidence/red-on-HEAD.log`, `evidence/red-on-HEAD.xml`).
