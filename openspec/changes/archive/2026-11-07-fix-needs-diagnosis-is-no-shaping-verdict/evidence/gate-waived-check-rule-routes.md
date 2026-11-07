# The gate's one failing step, and why this change does not answer for it

The gate ran once, by the gate child, over this change's tree
(`./tools/loop/gate.sh`, base `b0daba96d7648fecd8dd4f5cb21e7693546f6c9d`). Its tally, verbatim from
`evidence/gate.log:754`:

```
LOOP-GATE: tests=949 failures=0 errors=1 (tools/loop-selftest.sh --case driver#everyCaseDeclaresTheFilesItCanReach 11s, tools/loop-selftest.sh --from-git a3cefa934793b341e0d9290034a39b482e4e93dc 404s, bash tools/loop-mutations.sh --check 2s, tools/loop-mutations-selftest.sh 414s, tools/issue-tooling-selftest.sh 133s, tools/issues/check-metadata.sh 0s, tools/issues/metadata-mutations.sh --list 1s, tools/mutate.sh --mutations tools/close-mutations.json --list 0s, tools/check-portability-selftest.sh 23s, bash tools/gen-feature-list.sh --check-classification 3s, tools/check-doc-routes.sh 1s, tools/check-doc-routes-selftest.sh 7s, tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list 0s, tools/check-todo-references.sh 1s, tools/check-openspec-hygiene.sh 0s, tools/check-openspec-hygiene-selftest.sh 0s, tools/check-portability.sh 1s, tools/mutate-selftest.sh 1s, tools/mutate.sh --mutations tools/portability-mutations.json --list 1s, tools/mutate.sh --mutations tools/status-mutations.json --list 1s, tools/mutate.sh --mutations tools/create-mutations.json --list 1s, tools/mutate.sh --mutations tools/duplicates-mutations.json --list 0s, tools/mutate.sh --mutations tools/decision-question-mutations.json --list 0s, bash tools/loop-spend-selftest.sh 1s, tools/check-rule-routes.sh 2s) — failing: tools/check-rule-routes.sh
```

The one `errors=` is `tools/check-rule-routes.sh`, exit 1, from the gate's own log
(`evidence/gate.log:745-752`; the same six lines are in `evidence/gate-results/tools_check-rule-routes.sh.xml`):

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

Item #822 ran the same step over this same change root at **2026-11-07 20:08 +0100**, before this change's first
edit (21:31), and it answered the byte-identical six `::error` lines and the same
`6 rule(s) are reached by no glob` summary (`evidence/check-rule-routes-red-before.log`, copied verbatim from
`.pi/issue-loop/items/822/check-rule-routes-after.log`; item #822's own waiver of the same step is
`.pi/issue-loop/items/822/gate-waived-check-rule-routes.md`).

The step reads exactly three kinds of input: `tools/doc-routes.json` (through `tools/tooling.json` `docRoutes`),
the `**Rule:` names in `guidelines/*.md`, and the tree's tracked paths (the globs). Their state around that red run:

```
2026-11-06 22:58:38 guidelines/UIPhone.md
2026-11-07 01:35:10 tools/doc-routes.json
2026-11-07 13:12:04 guidelines/MapCar.md
2026-11-07 13:12:04 guidelines/UICar.md
2026-11-07 13:12:04 guidelines/UI.md
   … red run: 2026-11-07 20:08:55 …
2026-11-07 21:31:12 guidelines/Backlog.md      ← this change, §5 text only
2026-11-07 21:32:36 tools/tooling.json         ← this change, roles.shapingVerdicts only
```

Five of the six orphaned rules live in files untouched since before that red run, and `tools/doc-routes.json`
— the map whose routes decide reached-ness — is untouched since 01:35. The two inputs this change does write:

- `guidelines/Backlog.md` — §5 "Who may write what" paragraph (tasks.md §3.1): prose about the three shaping
  verdicts. It adds, removes or renames no `**Rule:` name; the Backlog.md rule the step reports
  (`Backlog.md:176`) predates the edit and is reported identically before and after.
- `tools/tooling.json` — `roles.shapingVerdicts` (line 47) only; `docRoutes` (line 6) still reads
  `tools/doc-routes.json`.

## Still red after this work

The gate itself is the after-run: at **21:45**, with every edit of this change in place, the step printed the same
six `::error` lines and the same two summary lines quoted above (`evidence/gate.log:745-752`).

`6 rule(s) are reached by no glob` before (20:08) and after (21:45), in the same six files, same rule names.
The red is therefore named here and waived, not repaired and not counted against this change:
`LOOP-GATE-WAIVED` carries the clause, `LOOP-GATE-WAIVED-SUITE` carries `tools/check-rule-routes.sh`.

## What the gate did measure for this change

Inside the same gate: `tests=949 failures=0` — the loop self-tests, the mutation self-tests, the issue tooling,
`tools/check-doc-routes.sh` (`all 329 section references resolve, all 7 skill references resolve`,
`evidence/doc-routes.log`), `tools/check-openspec-hygiene.sh`, `tools/check-portability.sh` and
`loop-spend-selftest: all 27 cases passed` (`evidence/gate.log:742`) all green. The red case
`run#aNeedsDiagnosisSkipCarriesTheLabelsTheProfileNames` fails on HEAD (`evidence/red-on-HEAD.xml`,
`tests=1 failures=1`) and passes on the change (`evidence/green-on-change.log`).
