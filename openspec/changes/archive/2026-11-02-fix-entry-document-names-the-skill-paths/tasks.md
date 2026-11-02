# Tasks

Reference: `proposal.md` for scope, `design.md` for why, `specs/documentation-ownership/spec.md` for the contract.
Every delta scenario's case is named in the task that lands it.

## 1. The red case, before the document is touched

Requirement: "The entry document names the skill a reader needs by its path", scenario "The loop's two ends are
reachable by path".

- [x] 1.1 Add `routed-read#theEntryDocumentNamesTheLoopSkillsByPath` to `tools/check-doc-routes-selftest.sh`: read
      `AGENTS.md`, require `.pi/skills/create-issue` and `.pi/skills/device-check` in it, then run
      `tools/check-doc-routes.sh --root <tree>` and require exit 0. Update the file header, which counts the
      `routed-read#` cases. Verify: `bash tools/check-doc-routes-selftest.sh --case
      routed-read#theEntryDocumentNamesTheLoopSkillsByPath` exits 1 on base `9318dad9` with only this case added —
      recorded as `evidence/red-on-HEAD.log` and `evidence/red-on-HEAD.xml` (`tests=1 failures=1`,
      2026-11-02T04:37:21Z, taken by `evidence/red-on-HEAD.sh`).

## 2. The pointer

Requirement: the same requirement's path sentence.

- [x] 2.1 Add one bullet to `AGENTS.md` under "Agent iteration loop (measure first)" naming both paths and the case.
      No existing sentence is edited. Verify: `grep -rho '\.pi/skills/[A-Za-z0-9._-]*' AGENTS.md guidelines/*.md |
      sort -u` prints four paths instead of two, both new ones among them, and `bash tools/check-doc-routes.sh`
      exits 0 with `all 4 skill references resolve`.
- [x] 2.2 Falsify the invariant once (`revert-check`): delete the bullet, run the case (must fail), restore it, run
      the case again (must pass). Verify: `evidence/mutation.log` reads `MUTATION exit=1` then `RESTORED exit=0`,
      taken by `evidence/mutation.sh`.

## 3. The structural checks the delta owes

- [x] 3.1 Run the gate once and quote it: `./tools/loop/gate.sh`. Verify: its `LOOP-GATE` line in
      `evidence/gate.log`, and the JUnit tallies it wrote copied beside it.
- [x] 3.2 Resolve every pointer the change's markdown carries: `bash tools/loop/evidence-check.sh
      openspec/changes/fix-entry-document-names-the-skill-paths --root .`. Verify: `PASS`, no dangling `file:line`,
      no unbacked `tests=` pair, no `/tmp` pointer.

## 4. Archive

- [x] 4.1 Archive the change and confirm the requirement merged into `openspec/specs/documentation-ownership/spec.md`
      at the end of the file, with its scenario and case name. Verify: `grep -c 'The entry document names the skill a
      reader needs by its path' openspec/specs/documentation-ownership/spec.md` = 1, and the change directory is under
      `openspec/changes/archive/`.
- [x] 4.2 Re-run `bash tools/check-doc-routes-selftest.sh` and `bash tools/check-openspec-hygiene.sh` after the
      archive, so the archiving itself is seen by the checks that read `openspec/changes/`. Verify: both exit 0.
