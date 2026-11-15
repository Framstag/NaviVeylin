# The merge into `main`

The branch was cut before `062810f2` (#868, forced test execution) and #870, so the merge into `main` at
`63f05d68` conflicted in three files. What each side kept:

| file | resolution |
|---|---|
| `architecture/build.gradle.kts` | both: the check's declared inputs (`checkedTree`, this change — the UP-TO-DATE defect task 1.9 closes) and #868's `ForcedTestExecution` block |
| `architecture/src/test/.../StructureDeclarationTest.kt` | one `emptyDeclarationFails`, asserting both the parse-level refusal this change adds and the check-level refusal #868 landed; `missingDeclarationFails` and `unknownKindFails` kept beside it |
| `architecture/src/test/.../StructureHookTest.kt` | not a conflict, but `satisfyingTreeRunsTestsNormally` must read every source set (`ProjectSources.sourceFiles()`): the merged `architecture/structure.tsv` carries the four test-only packages #870 declared, and a main-sources-only read reported them as `ROW_WITHOUT_PACKAGE` |
| `openspec/specs/package-structure/spec.md` | identical on both sides; one copy taken |

`evidence/gate-merged.log` is the gate over the merged tree.