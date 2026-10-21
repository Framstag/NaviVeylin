# Tasks — fix-ambiguous-section-reference

Each task's verification is stated in the task. The change touches no compiled file, so no Gradle build task
exists here: the structural check that stands in is `bash tools/check-doc-routes-selftest.sh` plus
`bash tools/check-doc-routes.sh` on the repository (`guidelines/Build.md` §2, the scope block).

## 1. Red case

- [x] 1.1 Add the four body cases to `tools/check-doc-routes-selftest.sh` (`body-ambiguous`, `body-text`,
      `body-fenced`, `body-dangling`) and record their run against `git show 44b673f:tools/check-doc-routes.sh`
      — three red (`a bare duplicated number in a guideline body is reported as ambiguous`,
      `a heading text in a guideline body resolves its duplicated number`, `a guideline body naming a section
      its document lacks is reported`) and the fenced counter-case green for the wrong reason. Verify:
      `check-doc-routes-selftest: 12 passed, 3 failed`, exit 1, `evidence/red/selftest.txt`
      (2026-10-21T11:53:49Z).
      *(spec: A section reference a guideline body writes is resolved — all five scenarios)*
- [x] 1.2 Add the fifth case (`a reference in a change artifact is not checked`): an archived change and an
      open change artifact each carrying a bare ambiguous reference are accepted, so the widened scan cannot
      drift into the history it must leave alone. Verify: the case passes on the fixed tree and on HEAD
      (counter-case), and the scan reads neither path.
      *(spec: A change artifact is history and is not read)*
- [x] 1.3 Make `expect` take every substring a case requires, and have the two body error cases assert the
      reported `file=guidelines/Build.md` beside their message, so the report's `file=%s` argument is exercised.
      Verify: reverting the report to a literal `file=AGENTS.md` fails both cases and no other (13 passed,
      2 failed), `evidence/green/mutation-report-file.txt`.
      *(spec: A bare number a guideline body writes is ambiguous; A guideline body names a section its document
      lacks)*

## 2. The check reads a guideline body

- [x] 2.1 Factor the reference parser into `extract_refs` in `tools/check-doc-routes.sh`, run it over the map
      region and every `guidelines/*.md` body, skip fenced blocks in a body, and report each error against the
      file the reference was written in. Verify: case `a bare duplicated number in a guideline body is reported
      as ambiguous` turns green, and the ten cases that were already in the file — nine `expect` cases plus
      the `AGENTS.md`-untouched case — keep their verdicts.
      *(spec: A section reference a guideline body writes is resolved)*
- [x] 2.2 State the widened scope in the script's header (the text `-h` prints) — including that an archived
      change and an open change artifact are history, neither checked nor rewritten. Verify:
      `bash tools/check-doc-routes.sh -h` prints the new paragraph, and `bash -n` accepts the script.
      *(spec: A section reference a guideline body writes is resolved)*

## 3. Repair the reference the check now flags

- [x] 3.1 `guidelines/Design.md:503` — write `§14 "Android Auto renderer"` instead of the bare `§14`; the
      section it means is unchanged. Verify: `bash tools/check-doc-routes.sh` exits 0 with
      `all 120 section references resolve`, and `git diff` shows one line changed.
      *(spec: A section reference a guideline body writes is resolved — the heading-text scenario)*
- [x] 3.2 `guidelines/Backlog.md` §14 — the bullet that documents `tools/check-doc-routes.sh` also names the
      body-reference rule, so the guideline does not describe a narrower check than the tree runs. Verify: the
      bullet names both rules, and `bash tools/check-doc-routes.sh` still exits 0.
      *(spec: The always-read document states facts and references rules — its summary names the owning check)*

## 4. Green, and the guard proven able to fail

- [x] 4.1 Run the check and its self-test on the fixed tree. Verify: `15 passed, 0 failed` and
      `all 120 section references resolve`, exit 0 both, `evidence/green/selftest.txt`
      (2026-10-21T12:09:48Z).
      *(spec: all five scenarios)*
- [x] 4.2 Revert-check A — disable the ambiguity refusal (`count[key] > 1` → `count[key] > 99`), run the
      self-test, restore, re-run green. Verify: `13 passed, 2 failed` with both ambiguity cases named, then
      `15 passed, 0 failed`; `evidence/green/mutation-ambiguity.txt`.
      *(spec: A bare number a guideline body writes is ambiguous)*
- [x] 4.3 Revert-check B — disable the missing-section branch (`if (!(key in headings))` → `if (0)`), run the
      self-test, restore, re-run green. Verify: `14 passed, 1 failed`, the failing case being
      `a guideline body naming a section its document lacks is reported`, then `15 passed, 0 failed`;
      `evidence/green/mutation-dangling.txt`.
      *(spec: A guideline body names a section its document lacks)*

## 5. Archive

- [x] 5.1 Verify the change against `openspec/specs/documentation-ownership/spec.md`, archive it, and confirm
      the delta merged into the capability spec — including the round-2 narrowing, which is applied to the
      delta and the merged spec alike. Verify: the requirement is in
      `openspec/specs/documentation-ownership/spec.md` and `openspec validate --specs` reports
      `176 passed, 0 failed`; the archived delta is textually identical to the merged requirement.
