# Tasks — the sixth invariant

Spec: `issue-backlog` (delta `specs/issue-backlog/spec.md`). Gate: `bash tools/issues/tests/issue-tooling-selftest.sh
--case metadata#<name>` while iterating (offline: a stub `gh`, no token, no network), and one full run of that suite
(`bash tools/issues/tests/issue-tooling-selftest.sh`) before the change is complete. No Gradle build is owed — no compiled file changes
(`guidelines/Build.md` §2, the scope block). No app code, no label vocabulary, no dependency.

## 1. The invariant

- [x] 1.1 `tools/issues/check-metadata.sh` gains the sixth per-item check: for an item whose `.parent` names an issue
  of the profile's `roles.containerType`, a non-empty `.issueType` is required. One violation line naming the child,
  the container and the absent type, plus a `--json` field beside the other invariants. Satisfies `A container's child
  carries an issue type`.
- [x] 1.2 The check runs in `--issues <file>` and in `--live` from one implementation, so the two modes cannot
  disagree; `--live` reads the same `gh issue list --json` document and adds no call. Satisfies `The type axis has a
  reader in every mode that sees an issue`.
- [x] 1.3 A document carrying no `issueType` field at all is printed as one line saying the axis could not be read,
  and is not counted as consistency.

## 2. The cases (offline, against the stub `gh`)

- [x] 2.1 `metadata#aTypedChildIsConsistent`, `metadata#anUntypedChildIsAViolation`,
  `metadata#aChildOfAnOrdinaryIssueIsOutOfScope`: a fixture JSON array with a container, a typed child, an untyped
  child and a parent-child pair of ordinary issues. Each case is named in the suite's `ISSUES_SHELL_CASES` and
dispatched by name, which is how this suite is narrowed to one case — `# files:` declarations are the loop suite's
convention (`tools/loop/tests/loop-cases.sh`) and the issue tooling's cases carry none.
- [x] 2.2 `metadata#bothPerItemModesCheckIt`: the same fixture through `--issues` and through the stub tracker's
  `--live`, asserting the same violation and that no additional `gh` call is made.
- [x] 2.3 `metadata#theJsonCarriesTheViolation`, `metadata#anUnreadableFieldIsNamed`: the `--json` document's shape,
  and a fixture whose items carry no type field at all.

## 3. The comment that names a capability nobody has

- [x] 3.1 `tools/issues/create.sh:59` cites `issue-types` as the owning capability; no spec under `openspec/specs/` and
  no guideline carries that id, and the axis is `issue-backlog`'s ("The tracker's own classification is a second
  axis"). Correct the comment. Verify: `tools/look.sh 'issue-types'` names no comment citing it as a capability — the
  hits that remain are the stub's own fixture name `issue-types.json` and the archived change that bears the id.

## 4. Documents and evidence

- [x] 4.1 `guidelines/Backlog.md` §3 states that a container's children carry a type and that the metadata pass is
  where it is checked. Verify `bash tools/check-doc-routes.sh`.
- [x] 4.2 `guidelines/BacklogTooling.recipes.md` §9's `metadata-invariant` row gains the sixth invariant — that file
  is where the declared rows live; `BacklogTooling.md` §9 points at the table and states no row itself.
- [x] 4.3 Record in the change's evidence: the number of untyped children the check reports on this tracker before the
  fix (the nine children of #895 at filing), the command, the date, and one `--live` run's wall time before and after.

## 5. Verification

- [x] 5.1 The issue tooling's suite green over the whole tree, with its case count and wall time recorded.
- [x] 5.2 One `--live` run recorded, showing the violation for a tracker that has one (a fixture tracker or a
  temporary parent-child pair), so the mode is proven and not only the offline path.
- [x] 5.3 Revert-check for the invariant: make the check skip an untyped child and
  `metadata#anUntypedChildIsAViolation` must fail; restore, forced green.
- [x] 5.4 Revert-check for the scope rule: make the check fire on a child of an ordinary issue and
  `metadata#aChildOfAnOrdinaryIssueIsOutOfScope` must fail; restore, forced green.
- [x] 5.5 Revert-check for the unread case: read an absent type field as consistency and
  `metadata#anUnreadableFieldIsNamed` must fail; restore, forced green.
- [x] 5.6 File every unrelated defect this work finds with `.pi/skills/create-issue`.
