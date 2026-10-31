# Acceptance — `guideline-ui-layers`, as landed

```
guidelines/UI.md        45 326 B   (< 48 000, every section < 12 000)
guidelines/UIPhone.md   47 926 B   (< 48 000, every section < 12 000, new normative document)
AGENTS.md               24 284 B   (< 32 000)
document-size conditions          3 -> 2   (Design, MapRendering remain)
normative-purity conditions      20 -> 12  (UI.md 8 -> 0, UIPhone.md 0)
named rules reached by globs     149 -> 338
```

Checks, all exit 0:

```
$ bash tools/check-doc-routes.sh
check-doc-routes: all 264 section references resolve, all 2 skill references resolve
$ bash tools/check-rule-routes.sh
check-rule-routes: every glob matches the tree and all 338 named rule(s) are reached
$ bash tools/check-doc-routes-selftest.sh
check-doc-routes-selftest: all 32 cases passed (throwaway fixtures and this tree, no Gradle, no device, no network)
$ bash tools/check-rule-routes-selftest.sh
check-rule-routes-selftest: all 6 cases passed (throwaway fixtures, no Gradle, no device, no network)
$ bash tools/check-openspec-hygiene.sh
OpenSpec artifacts are consistent: 28 open change(s) carry task markers, ...
$ openspec validate guideline-ui-layers --strict
Change 'guideline-ui-layers' is valid
$ bash tools/check-todo-references.sh
check-todo-references: 65 citation(s) resolve through the recorded table; ...
```

## No section number moved

`UI.md`'s documented set is now `1, 2, 3, 3a, 3b, 3c, 4, 6, 6b, 8d, 9, 10, 20` (the maintenance
section gained the number 20; 8d is the §8 sub-section that stayed cross-variant), and `UIPhone.md`
carries `5, 6a, 7, 7a, 7b, 8, 8b, 8c, 8a, 10a, 11, 12, 13, 14, 15, 16, 17, 18, 19`. Every number a
citation named before the cut is still carried by one of the two documents; no existing section
number is used for a different section.

## Rules named

Every normative bullet in both documents is named `**Rule: <name>**` (0 unnamed `- **` bullets
remain), each name is unique within and across `guidelines/*.md` (the duplicate check the doc-routes
run performs passed), and every name is reached by a glob in `tools/doc-routes.json` (338 reached).

## What was not done

Nothing in the change's scope. The design's D1/D4 boundary was amended during apply — recorded in
`design.md` under "Apply-phase amendment (2026-10-31)" — and the external citations that the move
dangled were retargeted (the two base documents the route check resolves: `Design.md`, `MapRendering.md`;
and, for honesty though no check resolves them, `Backlog.recipes.md` and
`.pi/skills/compose-geometry/SKILL.md`), listed in `retargeted-citations.txt`.
