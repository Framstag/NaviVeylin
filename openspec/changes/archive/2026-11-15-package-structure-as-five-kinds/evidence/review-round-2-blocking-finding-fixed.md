# Review round 2 — the blocking finding, and what this round changed

The review of round 1 refused the diff with one blocking finding (`LOOP-BLOCKING: 1`): the diff adds the
refusal of a declaration that holds no row, and the capability spec it ships names the case that should
assert it (`openspec/specs/architecture-test-suite/spec.md`, scenario "An empty declaration cannot pass" →
`StructureDeclarationTest#emptyDeclarationFails`), and no such case existed on the reviewed tree.

The reviewed tree sat on a base older than `062810f2` (#868), which landed a case of the same name against
the check-level refusal. The branch's half and that case were resolved once, in the merge into `main`:

- `architecture/src/test/kotlin/com/naviveylin/architecture/StructureDeclarationTest.kt` now carries one
  `emptyDeclarationFails` asserting both halves — `StructureDeclaration.parse` refuses a declaration with
  no row by name, and a check handed an empty rule set fails instead of reporting zero violations — beside
  the two cases task 1.11's other halves were missing on the branch base: `missingDeclarationFails` and
  `unknownKindFails`.
- `architecture/build.gradle.kts` keeps both sides: the check's declared inputs (`checkedTree`, this slice)
  and the shared `ForcedTestExecution` block (#868's `speed-up-build-test-gate`).
- `architecture/src/test/kotlin/com/naviveylin/architecture/StructureHookTest.kt` reads every source set
  (`ProjectSources.sourceFiles()`), because the merged `architecture/structure.tsv` carries the test-only
  packages #870 declared and `satisfyingTreeRunsTestsNormally` must see them.
- `openspec/specs/package-structure/spec.md` was identical on both sides; the merge took one copy.

Falsification on the merged tree (`guidelines/BuildEvidence.md` §4e), with the parse refusal removed:
`emptyDeclarationFails` fails and no other case does (`evidence/revert-check-1.11-empty-declaration-red.xml`,
`tests="7" failures="1"`, log `evidence/revert-check-1.11-empty-declaration-red.log`). With it restored the
class is green (`evidence/TEST-com.naviveylin.architecture.StructureDeclarationTest.xml`,
`tests="7" failures="0"`, log `evidence/revert-check-1.11-empty-declaration-green.log`).
`evidence/merge-resolution.md` records the merge and `evidence/gate-merged.log` the gate over it.