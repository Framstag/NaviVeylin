# Tasks — fix-skill-path-routes

## 1. The red case (before the fix)

- [x] 1.1 Add the four skill cases to `tools/check-doc-routes-selftest.sh` and run
      `bash tools/check-doc-routes-selftest.sh` against HEAD's check. Recorded in
      `.pi/issue-loop/items/13/red-test-before-fix.txt`: `tests=14 failures=4`, exit 1, `2026-10-21T11:32:55Z`,
      with `routes#aSkillPathThatNoLongerResolvesIsReported` among the failures.
      **Verification:** the FAIL lines quoted in that file name the skill cases and no section case.

## 2. The check resolves skill paths

- [x] 2.1 Derive the skill set from `.pi/skills/*/SKILL.md` and resolve every `.pi/skills/<name>` token in
      `AGENTS.md` and `guidelines/*.md` against it, as a whole name, reporting a path that resolves to
      nothing through the existing `::error file=…::` form.
      **Verification:** `bash tools/check-doc-routes-selftest.sh --case routes#aSkillPathThatNoLongerResolvesIsReported`
      exits 0 (the case passes on the fixed check).
- [x] 2.2 Count the skill references into the same exit-code decision and summary line, and state the new
      behaviour in the script's header.
      **Verification:** `bash tools/check-doc-routes.sh` on the shipped tree prints
      `all 89 section references resolve, all 5 skill references resolve` and exits 0.

## 3. Case ids and the falsification

- [x] 3.1 Give every self-test case an id (`routes#<name>`) and add `--case <id>`, so one case can be quoted
      by a task or a mutation set.
      **Verification:** `bash tools/check-doc-routes-selftest.sh --case routes#everyReferenceResolves` runs
      exactly one case and exits 0; an unknown id exits 2.
- [x] 3.2 Add `tools/check-doc-routes-mutations.json` — one mutation, the skill resolution removed — and run
      it.
      **Verification:** `bash tools/mutate.sh --mutations tools/check-doc-routes-mutations.json` prints
      `RED(exit 1)  1 occurrence(s) replaced`, `GREEN(exit 0)  restored` and exits 0; the transcript is
      `evidence/revert-check.txt`.

## 4. CI holds both

- [x] 4.1 Run the self-test and list the mutation set in the "Check documentation routes" step.
      **Verification:** the step's script text in `.github/workflows/build.yml` names
      `check-doc-routes-selftest.sh` and `check-doc-routes-mutations.json`, and
      `bash tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list` prints one record.

## 5. The requirement has its owner

- [x] 5.1 The change's `specs/documentation-ownership/spec.md` adds the requirement with its four scenarios,
      each naming one case, and `proposal.md`/`design.md` carry the Phase A evidence.
      **Verification:** `openspec validate fix-skill-path-routes --type change` exits 0.
- [x] 5.2 Archive the change and confirm the requirement is in the shipped spec.
      **Verification:** `openspec/specs/documentation-ownership/spec.md` contains
      "A document that names a skill resolves to a skill that exists" after the archive, and
      `openspec/changes/archive/<date>-fix-skill-path-routes/` exists.

## 6. Scope note — no Gradle gate

- [x] 6.1 No compiled file is touched (`tools/**`, `.github/**`, `openspec/**` only), so the project's gate
      `./gradlew test -PforceTests --no-build-cache` is **not** run: the changed surface is a shell check and
      its self-test, and the structural checks in tasks 2–3 are its gate (`guidelines/Build.md` §2, scope
      block).
      **Verification:** the changed-file list of this change contains no `*.kt`, `*.java`, `*.cpp` or Gradle
      input.

## 7. Evidence

- [x] 7.1 Copy the cited transcripts into `evidence/` and into `.pi/issue-loop/items/13`.
      **Verification:** `evidence/red-test.txt`, `evidence/selftest-after.txt`, `evidence/revert-check.txt`
      exist and quote the counts stated in tasks 1–3.
