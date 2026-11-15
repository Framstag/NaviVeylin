# Review round 2 — the blocking finding, and what this round changed

The review of round 1 refused this diff with one blocking finding (`LOOP-BLOCKING: 1`): the diff adds the
empty-declaration refusal (`architecture/src/main/kotlin/com/naviveylin/architecture/StructureDeclaration.kt`)
and the capability spec it ships names the case that should assert it
(`openspec/specs/architecture-test-suite/spec.md`, scenario "An empty declaration cannot pass" →
`StructureDeclarationTest#emptyDeclarationFails`), and no such case existed in the tree.

What this round changed, and nothing else:

- `architecture/src/test/kotlin/com/naviveylin/architecture/StructureDeclarationTest.kt`: added
  `emptyDeclarationFails` — a declaration of no rows is refused by name (`DeclarationParseError` naming
  `architecture/structure.tsv`), a declaration of comment lines carries no row either, and a check over an
  empty rule set reports the tree's files rather than zero violations.
- `tasks.md` 1.11: the tick names the case and cites the red and green artifacts.

Red first (`guidelines/BuildEvidence.md` §4e): the refusal was removed from `StructureDeclaration.kt` and the
class run — `evidence/revert-check-1.11-empty-declaration-red.xml` (`tests="7" failures="1"`), log
`evidence/revert-check-1.11-empty-declaration-red.log` — then the refusal was restored and the class run again
— `evidence/TEST-com.naviveylin.architecture.StructureDeclarationTest.xml` (`tests="7" failures="0"`), log
`evidence/revert-check-1.11-empty-declaration-green.log`.

The refusal in `StructureDeclaration.kt` is byte for byte the tree's own: the round removed it for the red run
and restored it, and `git diff` over that file is empty.
