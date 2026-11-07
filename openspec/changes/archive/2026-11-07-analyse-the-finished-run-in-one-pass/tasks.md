# Tasks

Each task names the parent spec `agent-issue-loop`, "A finished run is analysed in one read-only pass that ends in
items".

## 1. The pass

- [x] 1.1 `.pi/skills/analyse-loop-run/SKILL.md` states the pass: choosing the finished run and excluding a live
  one (§0), the numbers the state already holds (§1), where the spend went (§2), what was good and bad (§3), the
  suggestions table and its rules (§4), the five output blocks (§5), and the constraints the pass must not
  re-learn (§6).
- [x] 1.2 The skill's §4 states that a `do` row becomes one issue, class `improvement`, `source/agent`, and never
  the opt-in label — the sentence the case holds.

## 2. The route

- [x] 2.1 `guidelines/Build.md` §1 carries one row for `analyse-loop-run`, and the paragraph above the table counts
  four tooling skills instead of three.
- [x] 2.2 `AGENTS.md`, "Agent iteration loop", names `.pi/skills/analyse-loop-run` by its path beside the two ends
  it already names.
- [x] 2.3 `tools/check-doc-routes-selftest.sh`'s `routed-read#theEntryDocumentNamesTheLoopSkillsByPath` reads three
  skill names and asserts the skill's `Read-only` and `agent/eligible` sentences.

## 3. Falsification

- [x] 3.1 The case fails on HEAD's `AGENTS.md` (no path) — `evidence/red-run-routing.log`.
- [x] 3.2 The case fails on the skill with its read-only boundary weakened — `evidence/red-run-contract.log`.
- [x] 3.3 The case passes on the tree the change leaves — `evidence/green-run.log`.
- [x] 3.4 The structural checks this documentation delta owes: `tools/check-doc-routes.sh`,
  `tools/check-todo-references.sh`, `tools/check-openspec-hygiene.sh` — `evidence/checks.log`.
