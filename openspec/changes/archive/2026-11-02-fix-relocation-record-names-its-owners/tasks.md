# Tasks

Reference: `proposal.md` for scope, `design.md` for why, `specs/documentation-ownership/spec.md` for the contract.
The requirement lands as `documentation-ownership` "A relocation record names the owners it cites", and its one
scenario's case is `artifact-hygiene#anOwnerCitedAsADirectoryListingIsReported`.

## 1. Before anything is edited

- [x] 1.1 Record the entry the item names, verified against the tree, as `evidence/surface.txt`: the record's owner
      glob and the 35 paths under it, the eight table rows it was written for, and the archived review's grade of
      it. Verify: the file carries the three commands and their numbers —
      `sed -n '/^B2/,/^B3/p' …/relocation-entry-document.txt | grep -c '^\s*\.pi/skills/'` = 35,
      `git show 4330ce3e^:AGENTS.md | sed -n '/^| Skill | Job |/,/^$/p' | grep -c '^| \`'` = 8, and
      `grep -n 'G2' …/known-gaps.md` = 22.
- [x] 1.2 Record which home the rule does *not* belong in, with the measurement that eliminated it, as
      `evidence/why-not-evidence-check.txt`. Verify: it quotes `evidence-check: PASS` over the archived change, and
      `0` for the number of `path:NN` citations among the record's owner lines.
- [x] 1.3 Count the surface before the plan is fixed, as `evidence/surface.txt`. Verify:
      `grep -rn '^[[:space:]]*owner[s]*:' openspec/changes/ | wc -l` = 10 lines in 1 record, 1 of them carrying a
      glob, and that record is under `archive/`; `grep -c '^case_' tools/check-openspec-hygiene-selftest.sh` = 7
      cases (6 at base).

## 2. The red case

Requirement: "A relocation record names the owners it cites", scenario "An owner cited as a directory listing is
reported".

- [x] 2.1 Add `artifact-hygiene#anOwnerCitedAsADirectoryListingIsReported` to
      `tools/check-openspec-hygiene-selftest.sh`: an open change's `evidence/relocation.txt` carrying
      `owner: .pi/skills/*/SKILL.md`, the same lines copied under `openspec/changes/archive/`, and the assertions
      that the open record's `file:line` is reported, the finding names the refused form, and nothing under
      `archive/` is. Verify: run against base `650028bf`'s checker it fails —
      `evidence/red-on-HEAD.log`, `evidence/red-on-HEAD.xml` (tests=1 failures=1, 2026-11-02T04:25:12Z), taken by
      `evidence/red-on-HEAD.sh`.
- [x] 2.2 Confirm the failure is the class and not the fixture: the case extends case `a`'s clean tree, so the only
      difference is the class's own record. Verify: the same script run over that clean tree prints
      `all 7 cases passed` once the class lands, and case `a`'s fixture is the one the failing case extends.

## 3. The class that owns it

Requirement: the same requirement.

- [x] 3.1 `tools/check-openspec-hygiene.sh`: a fourth class — every open change's `*.md` and `*.txt` artifacts are
      read for an `owner:`/`owners:` line carrying a glob, and each is printed as a `::error` naming the record and
      the line. `openspec/changes/archive/` is skipped. The header states the class and why the CLI is silent about
      it, and the `--help` range covers the new paragraph. Verify: `bash tools/check-openspec-hygiene.sh --help`
      prints class 4 and exits 0, and `bash tools/check-openspec-hygiene.sh` over this tree exits 0 — no open change
      carries an owner citation, so the class reports nothing today.
- [x] 3.2 `clean_tree` in the selftest gains a well-formed `evidence/relocation.txt` whose `owner:` line names a
      path, so the passing shape is asserted by the case that already proves a clean tree passes. Verify:
      `bash tools/check-openspec-hygiene-selftest.sh --case artifact-hygiene#aWrittenPurposePasses` exits 0.

## 4. Green, and the harness falsifies the guard

- [x] 4.1 Run the case and then the whole suite. Verify:
      `bash tools/check-openspec-hygiene-selftest.sh --case artifact-hygiene#anOwnerCitedAsADirectoryListingIsReported`
      exits 0, and `bash tools/check-openspec-hygiene-selftest.sh` reports `all 7 cases passed` (6 before this
      change).
- [x] 4.2 One mutation, one named case, restore, forced green — `.pi/skills/revert-check`'s form, recorded as
      `evidence/mutation.log`: the class's glob test (`&& /\*/`) deleted from the awk program, so the same record
      passes; the case must go red with `an owner cited as a directory listing passed`, and restoring the line must
      turn it green again. Verify: the log quotes both runs with their timestamps, and
      `git diff --stat tools/check-openspec-hygiene.sh` shows no surviving mutation.

## 5. The artifacts the run reads

- [x] 5.1 Quote the run's own measurement block, `./tools/loop/measure.sh --change
      fix-relocation-record-names-its-owners`, into `evidence/measure-block.txt`; its `LOOP-MEASURE-PATH` and
      `LOOP-MEASURE-CHECK` lines are the pathspec this change owes. Verify: the block names no compiled step.
- [x] 5.2 Run the project's gate once, `./tools/loop/gate.sh`, and copy the tallies it wrote beside
      `evidence/gate.log`. Result `LOOP-GATE: tests=7 failures=0 errors=0 (tools/check-openspec-hygiene.sh 0s,
      tools/check-openspec-hygiene-selftest.sh 0s)`, with the two tallies copied beside it. Verify: the measurement
      block's two `LOOP-MEASURE-CHECK` lines are exactly the two suites the gate ran.
- [x] 5.3 Copy every cited result into `evidence/` and check the pointers mechanically:
      `bash tools/loop/evidence-check.sh openspec/changes/archive/2026-11-02-fix-relocation-record-names-its-owners
      --root .`. Verify: it reports no dangling `file:line`, `tests=` pair, `/tmp` pointer or section reference.
      The archive then merged the delta into `openspec/specs/documentation-ownership/spec.md`, a path the profile maps
      to seven further checks; they were run focused, after the archive, as
      `evidence/archive-path-checks.log` — six green, and `gen-feature-list.sh --check-classification` red for the
      two other open changes' unclassified spec ids (`auto/off-route-indicator`, `tooling-test-layout`) and three
      stale entries, none of which this change introduces (it adds a requirement to an already-classified
      capability).
- [x] 5.4 Archive the change and confirm the requirement merged into
      `openspec/specs/documentation-ownership/spec.md`. Result: archived as
      `openspec/changes/archive/2026-11-02-fix-relocation-record-names-its-owners`, and the requirement is at
      `openspec/specs/documentation-ownership/spec.md:698`. Verify:
      `openspec show documentation-ownership --type spec --json | jq '[.requirements[] | select(.text | test("cite each owner"))] | length'`
      prints `1`.

## 6. Correction round 2 — the review's one blocking finding

- [x] 6.1 Narrow the requirement to the form the class can see, in both copies: the shipped
      `openspec/specs/documentation-ownership/spec.md:698` and this change's archived delta now read "SHALL NOT cite a
      glob — the form a directory's complete listing takes — in their place", so no reader is told that a bare
      directory citation is refused. `design.md`'s "The rule" bullet and `proposal.md`'s capability line carry the same
      wording. Verify: `grep -n -e 'directory or a directory' -e 'glob or a directory' -e 'glob or directory'
      openspec/specs/documentation-ownership/spec.md specs/documentation-ownership/spec.md design.md proposal.md
      tools/check-openspec-hygiene.sh` prints nothing, so the requirement and every copy of it name one refused form.
- [x] 6.2 Delete the four claims the review recorded as `UNPROVEN` rather than support them: the CLI-validates-and-
      exits-0 sentence from `proposal.md`, `design.md`'s root cause and class 4 of the checker's header, the
      "no tool reads the record" half of the same root cause, `tasks.md` 2.2's "passes on base", and
      `proposal.md`'s "the claim … is true". Verify: `grep -n -e 'no tool reads the record' -e 'validates the change and'
      -e 'passes on base' design.md proposal.md tools/check-openspec-hygiene.sh` prints nothing, and `tasks.md` 2.2 no
      longer states what the base suite did.
- [x] 6.3 Re-check the pointers and run the gate once for this round: `bash tools/loop/evidence-check.sh
      openspec/changes/archive/2026-11-02-fix-relocation-record-names-its-owners --root .` prints
      `evidence-check: PASS` (295 `file:line` pointers resolved, 0 dangling), and `./tools/loop/gate.sh` printed its
      summary line over seven steps with `errors=1`, the failing step being
      `bash tools/gen-feature-list.sh --check-classification` (`evidence/gate-round-2.log`, whose tallies are copied
      beside it: `tools/check-doc-routes-selftest.sh.xml` tests=33 failures=0,
      `tools/check-openspec-hygiene-selftest.sh.xml` tests=7 failures=0, and the failing step's own XML errors=1).
      The failing step is attributed in `evidence/gen-feature-list-round-2.log`: it names the two other open changes'
      unclassified spec ids and three stale entries, and this change introduces no spec id.
