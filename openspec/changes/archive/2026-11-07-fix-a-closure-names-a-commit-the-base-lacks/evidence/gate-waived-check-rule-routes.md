# The gate's one failing step, and why this change does not answer for it

The gate ran once, by the gate child, over this change's tree (`./tools/loop/gate.sh`, base
`76fbe7eaca3e77e398fecb37b957fa798f1e161f`). Its line is quoted, never re-run:

```
LOOP-GATE: tests=947 failures=0 errors=1 (… 25 steps …) — failing: tools/check-rule-routes.sh — not retained as one JUnit XML: the gate is a shell suite and its aggregate is the sum of the 25 per-step tallies beside this file, of which tools_check-rule-routes.sh.xml carries errors="1"
```

The full output is `evidence/gate-full.log`; the failing step's own JUnit tally is
`evidence/tools_check-rule-routes.sh.xml` (`tests="0" failures="0" errors="1"`), and the step's output is
transcribed into `evidence/gate-attrib-rule-routes.log` (gate-full.log:1596):

```
::error file=tools/doc-routes.json::the rule "the queue reads the whole labelled set, and refuses one it could not cover." in Backlog.md is reached by no glob — a rule no change is told to obey
::error file=tools/doc-routes.json::the rule "the navigation surface's app-drawn elements are the compass rose, the speed badge and speed-limit sign, the attribution, the map-unavailable notice and the calculation strip." in MapCar.md is reached by no glob — a rule no change is told to obey
::error file=tools/doc-routes.json::the rule "a failed reroute is reported on the map surface." in UICar.md is reached by no glob — a rule no change is told to obey
::error file=tools/doc-routes.json::the rule "while navigation is active the wait is drawn, never pushed." in UICar.md is reached by no glob — a rule no change is told to obey
::error file=tools/doc-routes.json::the rule "the rows' words come from the surface's resources." in UIPhone.md is reached by no glob — a rule no change is told to obey
::error file=tools/doc-routes.json::the rule "the car's reroute wait is a surface strip, the phone's is a card tint." in UI.md is reached by no glob — a rule no change is told to obey
check-rule-routes: 0 glob(s) match no tracked path, 6 rule(s) are reached by no glob, 0 rule(s) state no name
```

## What the check reads, and this change's relation to each input

The failing invariant (`rule(s) are reached by no glob`) reads exactly two things: the map
`routes[].glob` / `routes[].{sections,rules}` entries (built into the reach set at
`tools/check-rule-routes.sh:205`) and the `**Rule: <name>**` extraction over `guidelines/*.md`
(`tools/check-rule-routes.sh:209`, walked at `tools/check-rule-routes.sh:223`). The whole transcript is
`evidence/gate-attrib-rule-routes.log`:

- `tools/doc-routes.json` and the four documents that carry five of the six rules (`MapCar.md`, `UICar.md`,
  `UIPhone.md`, `UI.md`) are byte-identical between the base and this change's commit — `git diff --stat` over
  them is empty.
- `tools/tooling.json`, which names the map path, adds one key (`baseBranch`); `docRoutes` is untouched.
- `guidelines/Backlog.md` — the one guideline document this change *does* edit — gains exactly one
  invariant-table row (I19): no line removed, and `git diff … -- guidelines/ | grep -c -F '+**Rule:'` →
  `0`. The extraction input is therefore the same rule set at base and at HEAD, and the failing Backlog.md
  rule (`guidelines/Backlog.md:176`) is present unchanged at the base.
- All six failing rules are present, once each, in the base copy of their document (counts in
  `evidence/gate-attrib-rule-routes.log`).

## Red before this work, red after it

No line this change adds or removes is an input to the failing invariant, so the base's own verdict on this
step is the same six-rule shortfall — red before, proven from the inputs rather than from a second
measurement (a re-run of the gate is evidence destroyed). The gate itself ran *on this change's tree* and
printed the same six errors — red after. The 947 counted cases with `failures=0` carry this change's own
work: the two red cases green on HEAD (`evidence/red-on-HEAD.xml` transcribes them red on the base),
`issue-tooling-selftest: all 164 cases passed` (`evidence/tools_issue-tooling-selftest.sh.xml`),
`loop-selftest: all 368 cases passed` (`evidence/tools_loop-selftest.sh_--from-git_a3cefa934793b341e0d9290034a39b482e4e93dc.xml`),
`all 284 cases passed` of `loop-mutations --check`, and both new mutations RED→GREEN
(`evidence/mutation-close-A1.6.log`, `evidence/mutation-labels-99.1.log`).

## The waiver

LOOP-GATE-WAIVED: tools/check-rule-routes.sh reports 6 guideline rules reached by no glob — pre-existing at base 76fbe7ea, with the map (tools/doc-routes.json), the docRoutes path and every `**Rule:` line byte-identical to it, since this change's only guideline edit adds one invariant-table row and no rule — so the red is the base's and stays the base's
LOOP-GATE-WAIVED-SUITE: tools/check-rule-routes.sh
