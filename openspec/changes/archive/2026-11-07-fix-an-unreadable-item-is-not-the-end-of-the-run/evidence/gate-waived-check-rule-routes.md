# The gate's one failing step, and why this change does not answer for it

The gate ran once, by the gate child, over this change's tree (base `fc9e0eb012dcfc781bf744d128acfac1cd9138f8`).
Its line, verbatim from the first process's hand-off:

```
LOOP-GATE: tests=951 failures=0 errors=1 (loop engine + issue tooling + doc/mutate/portability shell suites, 25 steps; no compiled step owed) — failing: tools/check-rule-routes.sh
```

The sum it states is the 25 suite tallies beside this file, in `evidence/gate-tallies/` —
`tests=951 failures=0 errors=1` when they are summed, written once into `evidence/gate-sum.xml`
(`tests="951" failures="0" errors="1"`).

The one `errors=` is `tools/check-rule-routes.sh`, exit 1. Its own tally, `evidence/gate-tallies/tools_check-rule-routes.sh.xml`
(the same lines the gate child's output carries, `evidence/gate-tail-output.txt`):

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

Item #822 ran the same step over this same change root at **2026-11-07 20:08 +0100** and got those byte-identical
six `::error` lines and the same `6 rule(s) are reached by no glob` summary
(`evidence/check-rule-routes-red-before.log`, copied verbatim from
`openspec/changes/archive/2026-11-07-fix-needs-diagnosis-is-no-shaping-verdict/evidence/check-rule-routes-red-before.log`,
whose own waiver is that archive's `evidence/gate-waived-check-rule-routes.md`). The step was already red in four
gates run the same evening, all before this change's first edit — `19:33`
(`openspec/changes/archive/2026-11-07-fix-a-childs-command-is-keyed-by-role-and-class/evidence/gate-step-check-rule-routes.log`),
`20:08`
(`openspec/changes/archive/2026-11-07-fix-the-spend-report-names-every-role/evidence/gate.log:1599`),
`21:22`
(`openspec/changes/archive/2026-11-07-fix-a-closure-names-a-commit-the-base-lacks/evidence/gate-full.log:1602`)
and `21:45`
(`openspec/changes/archive/2026-11-07-fix-needs-diagnosis-is-no-shaping-verdict/evidence/gate.log:745`) — each with
the same six lines, so this gate repeats a red this tree has carried all week, not a red this change made.

The step reads three kinds of input: `tools/doc-routes.json` (the routes), the `**Rule:` names in
`guidelines/*.md`, and the tree's tracked paths (the globs). Their state, measured with `stat`:

```
2026-11-06 22:58:38 guidelines/UIPhone.md
2026-11-07 01:35:10 tools/doc-routes.json
2026-11-07 13:12:04 guidelines/MapCar.md
2026-11-07 13:12:04 guidelines/UICar.md
2026-11-07 13:12:04 guidelines/UI.md
2026-11-07 21:31:12 guidelines/Backlog.md        ← written by another change, after the 20:08 red run
   … red run: 2026-11-07 20:08 …
2026-11-07 23:02:54 tools/loop-mutations.sh      ← this change, first edit
2026-11-07 23:18:44 tools/loop/run.sh            ← this change
2026-11-07 23:18:44 tools/loop-cases.sh          ← this change
2026-11-07 23:07:08 guidelines/Backlog.recipes.md ← this change, section 7d paragraph only
2026-11-07 23:25:14 tasks.md                     ← this change, last write
   … this gate: the step's tally written 2026-11-07 23:35 …
```

Five of the six orphaned rules live in files untouched since before the 20:08 red run, and `tools/doc-routes.json`
— the map whose routes decide reached-ness — is untouched since 01:35. The one input this change writes near them
is `guidelines/Backlog.recipes.md` (`section 7d`, a paragraph about unreadable items): it is not `guidelines/Backlog.md`,
it adds no `**Rule:` name and no route, and the Backlog.md rule the step reports predates this change entirely.

## Red after this work

The gate itself is the after-run: at **23:35**, with every edit of this change in place, the step printed the same
six `::error` lines and the same two summary lines quoted above
(`evidence/gate-tallies/tools_check-rule-routes.sh.xml`).

`6 rule(s) are reached by no glob` before (20:08, 21:45) and after (23:35), in the same six files, same rule names.
The red is therefore named here and waived, not repaired and not counted against this change:
`LOOP-GATE-WAIVED` carries the clause, `LOOP-GATE-WAIVED-SUITE` carries `tools/check-rule-routes.sh`.

## The change's own paths

The diff this gate was taken over edits exactly four tracked files — `guidelines/Backlog.recipes.md`,
`tools/loop-cases.sh`, `tools/loop-mutations.sh`, `tools/loop/run.sh` — plus this change directory. None is
`guidelines/UI.md`, `guidelines/MapCar.md`, `guidelines/UICar.md`, `guidelines/UIPhone.md`,
`guidelines/Backlog.md` or `tools/doc-routes.json`, and the failing rule's own line is `guidelines/UI.md:97`.

## What the gate did measure for this change

Inside the same gate: `tests=951 failures=0` — the loop self-tests, `tools/loop-selftest.sh --from-git
a3cefa934793b341e0d9290034a39b482e4e93dc` at `tests=370 failures=0`
(`evidence/gate-tallies/tools_loop-selftest.sh_--from-git_a3cefa934793b341e0d9290034a39b482e4e93dc.xml`),
the mutation self-tests, the issue tooling, `tools/check-doc-routes.sh`
(`all 329 section references resolve, all 7 skill references resolve`, `evidence/doc-routes.log`),
`tools/check-openspec-hygiene.sh` (`evidence/hygiene.log`) and `tools/check-portability.sh` all green. The red case
`run#anUnreadableItemIsNotTheEndOfTheRun` fails on HEAD (`evidence/red-on-HEAD.xml`, `tests="1" failures="1"`,
2026-11-07T22:07:39Z) and passes on the change (`evidence/green-on-change.log`).
