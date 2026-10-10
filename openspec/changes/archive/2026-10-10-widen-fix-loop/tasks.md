# Tasks

No specs: this change is tooling/documentation and sets `skip_specs: true`. Each task therefore cites the
`proposal.md` / `design.md` section that owns it instead of a spec requirement. Where a config rule expects a
spec, the task names the design decision it verifies.

## 1. Land the renamed skill on `main` (design D7)

- [x] 1.1 Confirm the untracked working-tree copy equals the loop branch's tracked copy before moving
      anything: `diff -r .pi/skills/bugfix-loop <(git show bugfix-loop-2026-10-09 --stat)` is not the check —
      use `for f in SKILL.md scripts/loop-state.sh scripts/evidence-check.sh scripts/selftest.sh; do
      diff <(git show bugfix-loop-2026-10-09:.pi/skills/bugfix-loop/$f) .pi/skills/bugfix-loop/$f || echo
      "DIFFERS: $f"; done` and require no output.

- [x] 1.2 Rename the directory to its new name (`git mv` does not apply — the path is untracked on `main`):
      `cp -a .pi/skills/bugfix-loop .pi/skills/fix-loop && rm -rf .pi/skills/bugfix-loop`. Verify with
      `git check-ignore -v .pi/skills/fix-loop/SKILL.md` exiting **non-zero** (the `!.pi/skills/*/` negation
      admits it) and `ls .pi/skills/fix-loop/scripts/` listing all three scripts.

- [x] 1.3 Stage and commit the skill on `main` so the rename lands as history rather than as an untracked
      copy. Verify with `git diff --cached --name-only` showing only the four `fix-loop` paths, and
      `git ls-files .pi/skills/fix-loop` listing all four after the commit. State in the commit message that
      the loop branch's `bugfix-loop/` copy is superseded and is refreshed by the worktree recipe's step 4.

## 2. Rename the surfaces (proposal "What Changes", design D3/D6)

- [x] 2.1 `scripts/loop-state.sh`: `--bugs` → `--items`, write `itemLimit` in `init`, and read
      `(.itemLimit // .bugLimit)` in every reader (`show`, `remaining`, `next`, `done`, `report`), including
      the arithmetic at the `bugLimit`-reading lines. Verify by reading the diff: no `--bugs` string remains,
      `init` writes `itemLimit`, and every `.bugLimit` read is a `//` fallback.

- [x] 2.2 `scripts/selftest.sh`: switch the fixtures to `--items`, add the four assertions of design D5
      (`--items` accepted and `itemLimit` written; a fixture state carrying only `bugLimit` still resolves its
      remaining budget; no `^## ` heading inside the report template; exactly one cited-XML copy-out
      instruction and one seven-row gate table in `SKILL.md`). Verify with `bash
      .pi/skills/fix-loop/scripts/selftest.sh` exiting 0 and printing a pass line per assertion.

- [x] 2.3 `AGENTS.md`: rename the path in the backlog-maintenance skill table and in the agent-iteration-loop
      section, and state the widened scope there in one clause ("defect-shaped bugs and improvements").
      Verify with `grep -n 'bugfix-loop' AGENTS.md` returning nothing and `test -f
      .pi/skills/fix-loop/scripts/loop-state.sh` succeeding.

- [x] 2.4 `openspec/config.yaml`: rename the skill in the `context` paragraph and in the
      `operations.apply.guidance` entry that names it, and widen the latter's wording from "reproduce the
      defect" to cover a defect-shaped improvement's red case. Verify with
      `openspec instructions apply --change widen-fix-loop --json | jq -r '.operationGuidance | type'`
      printing `array` (the unquoted-`KEY: text` hazard quoted in `AGENTS.md`), the renamed path appearing in
      both places, and `openspec doctor` staying green.

- [x] 2.5 `scripts/evidence-check.sh`: update the comment that names the skill path. Verify with `grep -rn
      'bugfix-loop' .pi/skills/fix-loop/ AGENTS.md openspec/config.yaml` returning only intentional hits
      (branch names in history, the state directory's own name).

## 3. Widen the gate (proposal "What Changes", design D1/D2)

- [x] 3.1 Phase B: rewrite condition 4 to the class-parameterised wording and add improvement-only condition
      7, so the section holds one seven-row table; add the `not-spec-shaped` and `needs-target` verdicts and
      extend the "leave the entry honest" list with the `not-spec-shaped` outcome. Verify by counting the
      condition rows in the table (7) and by `grep -c` for each new verdict word (1 each).

- [x] 3.2 Pre-screen (step 0b): remove `improvement` from the rejected classes, keep `feature` rejected, and
      let the queue be built in `triage-todo`'s order (bugs first, then eligible improvements). Verify that
      the six conditions no longer appear inside the triage child's contract string — it must point at Phase
      B's table instead, so `grep -c 'exactly one fix' ` inside that block is 0.

- [x] 3.3 "When NOT to use" and the pitfalls: replace the bugs-only exclusion with the feature exclusion, and
      state why features stay out (condition 5 is the only thing carrying scope risk). Verify with `grep -in
      'bugs only\|bugs-only' .pi/skills/fix-loop/SKILL.md` returning nothing, and `feature` appearing in the
      exclusion with the reason.

- [x] 3.4 Phase A: name the third red shape (the contract/bound case for an improvement) next to the existing
      doc-vs-code divergence branch, with the same revert-check mutation. Verify that the phase names the case
      shape, the precedent tests, and the mutation target in one place, and that no new phase is introduced.

- [x] 3.5 Start: document `--items`/`itemLimit`, and make the state line name `.pi/bugfix-loop/run.json`
      explicitly with the reason it did not move. Verify with `grep -c '.pi/fix-loop/run.json'
      .pi/skills/fix-loop/SKILL.md` printing 0 and the state path appearing once.

## 4. Restructure the document (design D4)

- [x] 4.1 Reorder to execution order: pre-screen, roles, the recording commands, then the phases, then the
      invariants the phases reference. Verify by listing the section headings in file order and confirming
      each phase follows the section it needs to execute.

- [x] 4.2 Merge the duplicated rules into one normative statement each — the cited-XML copy-out rule, the
      `§id`-from-max-over-both-trees rule, the "never read a guideline in full" rule and the budget-authority
      rule — and reference the surviving statement by name from the phases. Verify with `grep -c` per phrase:
      each returns 1 outside the invariants section, and the phases cite it by name.

- [x] 4.3 Move provenance into a measured-costs appendix; the body keeps the rule and at most one pointer.
      Verify by reading the diff: no `§138 took 65 min`-style measurement survives outside the appendix and
      the cost table.

- [x] 4.4 Demote the report template's `## ` heading so a `^## ` scan no longer reads it as a section.
      Verify with `grep -c '^## Bug-fix loop' .pi/skills/fix-loop/SKILL.md` printing 0 (also assertion (c) of
      task 2.2).

- [x] 4.5 Deduplicate the pitfalls to those that add information, each naming the section that owns its rule.
      Verify by listing the remaining pitfalls and marking, per line, either "not stated in the body" or the
      owning section.

- [x] 4.6 Prove no rule was dropped, since this is the restructure's only real risk: enumerate one line per
      rule before and after (invariants, gate conditions, phase obligations, prohibitions, stop conditions)
      and require the count to be equal, with every removed line mapped to a surviving statement or a
      reference. Deliverable: the two inventories in the change's evidence directory, and the before/after
      line count recorded in the commit message.

## 5. Revert-checks (design D5, D6; `guidelines/Build.md` §6 discipline via the `revert-check` skill)

- [x] 5.1 Improvement eligibility is the change's new predicate. Name the triple (invariant: a defect-shaped
      improvement is eligible; mutation: restore `improvement` to the pre-screen's rejected classes; case that
      must fail: the sample pass). Run the mutation, confirm §164 and §120 become ineligible, restore, then
      re-run `selftest.sh` green. Record the mutation output and the restored run.

- [x] 5.2 The `itemLimit // bugLimit` tolerance. Name the triple (invariant: a state file carrying only
      `bugLimit` still resolves its budget; mutation: delete the `// .bugLimit` fallback; case that must fail:
      the fixture assertion of task 2.2). Confirm the failure is the expected assertion, restore, re-run
      `selftest.sh` green, and require the marker to be gone from the tree.

- [x] 5.3 The structure assertion. Name the triple (invariant: `SKILL.md` carries the copy-out rule once;
      mutation: append a second copy of that instruction; case that must fail: assertion (d) of task 2.2).
      Confirm failure, restore, re-run green, and confirm the mutation marker is absent.

## 6. Verification and residue

- [x] 6.1 Run the design's sample pass and record the verdict histogram next to the prediction: §164 and §120
      `eligible`; §163, §77 and §111 `needs-decision`; §76 `too-wide`; a `feature` entry not eligible. A
      verdict that disagrees with the prediction falsifies the gate wording — fix the wording before
      proceeding, not the prediction.

- [x] 6.2 Run `bash .pi/skills/fix-loop/scripts/selftest.sh` and `bash
      .pi/skills/fix-loop/scripts/evidence-check.sh --selftest`; both exit 0, with their output recorded.

- [x] 6.3 Record the build scope honestly instead of implying a gate: `git diff --name-only HEAD` must list
      only `.pi/`, `AGENTS.md`, `openspec/` and `TODO.md`. If that holds, no Gradle input changed and the
      both-flavor gate is **not** required — state that as the decision, with the file list as its evidence.
      If any source-set path appears, run the mobile/automotive gate instead
      (`./gradlew test -PforceTests --rerun-tasks`, per `guidelines/Build.md` §2).

- [x] 6.4 `openspec validate widen-fix-loop` and `openspec doctor` are green, and `openspec status --change
      widen-fix-loop --json` reports `proposal`/`design`/`tasks` done with `specs` skipped. Verify the exact
      output.

- [x] 6.5 File the one residue this change uncovered: the `documentation-ownership` extension (design Open
      Questions) as a new append-only `TODO.md` entry with a fresh id. The next id is the max over **both**
      trees — `main` reaches §164 while the loop branch owns §165–§171, so the entry is §172 — with
      `class: improvement`, `category: specs-and-process`, `status: open`, and the Clusters index row
      regenerated. Verify the structure checks: `##` count equals `**id:**` count, the new heading is
      followed by its metadata line, and no id collides.

## Workflow follow-up

- Reconcile the loop branch: copy the renamed skill into `.pi/worktrees/loop/.pi/skills/` (worktree recipe
  step 4 now copies `fix-loop`), commit it there, and remove the stale `bugfix-loop/` directory on that
  branch so a run started in the worktree reads the widened gate instead of the old text.
- Archive this change after the project's review requirements are satisfied, and verify the archived result.
