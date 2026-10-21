# Proposal — a documented skill path resolves to a skill that exists

## Why

Measured on `44b673f` in `.pi/worktrees/loop` (evidence: `.pi/issue-loop/items/13/red-test-before-fix.txt`,
`2026-10-21T11:32:55Z`):

- `tools/check-doc-routes.sh` resolves `AGENTS.md`'s section references against `guidelines/*.md` and
  nothing else. A `.pi/skills/<name>` path is never looked at: running the check over a fixture whose table
  names `.pi/skills/pixel-check` while no such skill exists exits **0** and prints
  `check-doc-routes: all 5 section references resolve`.
- Root cause, one sentence: **the route check resolves section references only, so a document may keep
  naming a skill path that no longer resolves — a renamed or deleted skill is silent until the run that
  needs it fails.**
- The red case added to `tools/check-doc-routes-selftest.sh` before the check was changed:
  `routes#aSkillPathThatNoLongerResolvesIsReported` (then worded "a skill path that no longer resolves is
  reported") and three siblings. HEAD's self-test: **`tests=14 failures=4`** (10 passed, 4 failed, exit 1).
- The rule that would have bounded the documents under `.pi/skills/` has no owner:
  `openspec/specs/documentation-ownership/spec.md` states "A rule has one normative home" and "Every
  guideline document is covered by the route check", and **every requirement in it is written about
  `AGENTS.md` and `guidelines/`**. The hand-written skills are versioned with the project, are read by
  fresh-context children, and are named from `AGENTS.md`, `guidelines/Build.md` §1, `guidelines/Backlog.md`,
  `guidelines/FeatureList.md`, `openspec/config.yaml` and `tools/**` — five such paths exist in the shipped
  tree today, and a sixth would be unchecked too.
- What it costs today: a skill renamed without its references (`fix-loop` is named from `openspec/config.yaml`
  and `guidelines/Build.md`; `pixel-check` and `device-check` from `guidelines/Build.md`) is discovered by the
  child that is started to load it, mid-run, with the budget already spent.
- Why now: `widen-fix-loop` (archived 2026-10-10) removed one instance of the duplication this rule should
  prevent, and its own evidence — 479 lines, the cited-XML copy-out rule stated five times, the six
  eligibility conditions twice — is the measurement of what a document set without an owner looks like.

## What Changes

- **One new requirement in `documentation-ownership`**: a document that names a skill by its path SHALL
  resolve to a skill the tree carries; a skill is the `.pi/skills/` directory that carries its `SKILL.md`;
  the skill set is derived at run time. The requirement also states that the skills are a document set — "a
  rule has one normative home" applies inside a skill as it does across `guidelines/` — so the concern the
  tracker entry names has an owner instead of a precedent.
- **The existing check grows that resolution**: `tools/check-doc-routes.sh` resolves every `.pi/skills/<name>`
  path in `AGENTS.md` and in `guidelines/*.md` against the directories under `.pi/skills/`, reporting a path
  that resolves to no skill. It stays text-only: no build, no device, no network.
- **The self-test states the new cases and gains case ids**: the four skill cases plus `--case <id>`, so a
  task or a mutation set quotes one id (`routes#aSkillPathThatNoLongerResolvesIsReported`) instead of a
  sentence. The mutation set `tools/check-doc-routes-mutations.json` is the falsification of the new guard.
- **CI runs the check's own self-test and lists the mutation set** in the step that already runs the check,
  so the new cases cannot rot and the set cannot silently stop parsing.

No behaviour of the app, the tooling's other checks, or the tracker changes. Nothing is marked **BREAKING**.

## Capabilities

**Modified Capabilities:**

- `documentation-ownership` — one requirement added: a documented skill path resolves to a skill that exists,
  and the skills are a document set under the rule "a rule has one normative home".

**New Capabilities:** none.

## Impact

- `tools/check-doc-routes.sh` — the skill resolution, its summary line and its header.
- `tools/check-doc-routes-selftest.sh` — four new cases, case ids, `--case <id>`.
- `tools/check-doc-routes-mutations.json` — new, the revert-check set.
- `.github/workflows/build.yml` — the "Check documentation routes" step runs the self-test and lists the set.
- `openspec/specs/documentation-ownership/spec.md` — the added requirement, through this change's archive.
- No Kotlin, no C++, no Gradle input, no dependency: the change touches no compiled file.
