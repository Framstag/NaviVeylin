# Tasks — a filing skill names the type axis on the call it shows

## 1. The red case

- [x] 1.1 `docs#theFilingSkillsNameTheTypeAxis` in `tools/issues/tests/issue-tooling-cases.sh:3400` reads all four
      filing skills (the two-element list becomes `create-issue`, `update-issue`, `file-finding`, `reshape-issue`)
      and, for the two that document a `create.sh` call, asserts `--type` and `--parent` on that call line.
      **Verified** red on the base `6a7107042e2f` with this tree's case and the base's two skills
      (`evidence/red-on-base.log`, JUnit carrier `evidence/red-on-base.xml`, `tests=1 failures=1`,
      2026-11-25T03:13:48Z: `.pi/skills/file-finding/SKILL.md no longer names --type`), and red with the paragraph
      kept and only the call line reverted (`evidence/red-on-call-line.log`, 2026-11-25T03:13:54Z: `…'s documented
      create.sh call no longer carries --type and --parent beside its other flags`).
      Case: `docs#theFilingSkillsNameTheTypeAxis`.

## 2. The fix

- [x] 2.1 `.pi/skills/file-finding/SKILL.md:41` — the documented call reads
      `tools/issues/create.sh --type Task --parent <container> <<'FINDING'`, and the paragraph under it names
      `guidelines/Backlog.md` §3 "What the tracker holds", the reason the two are not body fields, and
      `roles.containerType` in `tools/tooling.json` as the container's name.
      **Verified** `PASS docs#theFilingSkillsNameTheTypeAxis`, 2026-11-25T03:13:49Z (`evidence/green-on-fix.log`).
      Case: `docs#theFilingSkillsNameTheTypeAxis`.
- [x] 2.2 `.pi/skills/reshape-issue/SKILL.md:94` — the same on the call a split files each child through, with issue
      #911 named as the shape a child filed without them produces.
      **Verified** the same case, plus the call-line probe of 1.1, which reverts this tree's `file-finding` call and
      the suite names that file.
      Case: `docs#theFilingSkillsNameTheTypeAxis`.
- [x] 2.3 The delta `specs/issue-backlog/spec.md` restates "The skill a filer is routed to names the type axis" with
      the four files and the call line, and its scenario names this case — one rule, one case.
      **Verified** `openspec validate fix-a-filing-skill-names-the-type-axis --strict` prints
      `Change 'fix-a-filing-skill-names-the-type-axis' is valid` (2026-11-25T03:14Z), and
      `tools/loop/measure.sh --change fix-a-filing-skill-names-the-type-axis` reads
      `LOOP-MEASURE-CHANGE fix-a-filing-skill-names-the-type-axis scenarios=1 cases=1 unnamed=0`.
      Case: `docs#theFilingSkillsNameTheTypeAxis`.

## 3. The structural check that stands in for the build

- [x] 3.1 This delta compiles nothing, so the structural check of the files it edits is the case itself:
      `bash tools/issues/tests/issue-tooling-selftest.sh --case docs#theFilingSkillsNameTheTypeAxis`, token-free and
      offline. **Verified** as 1.1 records it: red on the base, red on the reverted call line, green here.
      The remaining checks the pathspec owes (`tools/check-doc-values.sh`, `tools/check-doc-routes.sh`,
      `tools/check-todo-references.sh`, `tools/check-portability.sh` and their self-tests, as
      `tools/loop/measure.sh`'s `LOOP-MEASURE-CHECK` lines name them) are the gate's selection, run once by the gate.
      Case: `docs#theFilingSkillsNameTheTypeAxis`.
