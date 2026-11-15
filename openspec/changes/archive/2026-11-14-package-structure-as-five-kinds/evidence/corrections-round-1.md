# Corrections after review round 1

The reviewer of round 1 refused the diff with one blocking finding: the diff shipped a capability
contract the tree does not implement, and shipped it in the versioned capability specs. The remedy it
named was subtractive — do not merge the unimplemented requirements into `openspec/specs/`, and narrow
the delta to the requirements this slice implements.

What changed in this round:

- `openspec/specs/package-structure/spec.md` now carries three requirements and four scenarios — one
  row per package, the declaration reader and the row the tree lacks, and the default deny. Every
  scenario names a case that exists in the tree
  (`architecture/src/test/kotlin/com/naviveylin/architecture/StructureDeclarationTest.kt`).
- `openspec/specs/architecture-test-suite/spec.md` is deleted: no requirement of that capability is
  implemented, and the 17 scenarios it carried are re-proposed by the open change
  `package-structure-rules-and-suite`.
- The archived delta `specs/package-structure/spec.md` is narrowed to the same three requirements, and
  the archived `specs/architecture-test-suite/spec.md` is removed with its scenarios.
- The 30 scenarios that did not land are re-proposed by the open change
  `package-structure-rules-and-suite`, which carries them and the tasks that follow them.
- `tasks.md` ticks the five tasks this slice landed (1.1–1.4 and the falsification 3.4) with the
  evidence that proves them, and its task 1.2 verifies the package count with the file-bearing count
  the record measures.
- The proposal's and the design's 2026-11-09 figures are named as the plan author's own baseline, and
  the proposal's surface line names what this archive holds.

The archive stays, although the change is not finished: the run sees a change by its archived
directory — `tools/loop/worktree.sh` reads `openspec/changes/archive/<name>/` in `carried_change`,
`carried_verification` and `record-review-round` — so the archive is where the run can find this
change and where the next review round's answer is recorded. The reviewer named this as its second
remedy, for the case where the run insists on archiving the round; the first remedy — keeping the
change open — would leave the run with no archived change to track, review or adopt.
