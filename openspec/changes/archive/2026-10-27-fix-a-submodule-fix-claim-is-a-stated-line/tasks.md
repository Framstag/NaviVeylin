# Tasks — fix-a-submodule-fix-claim-is-a-stated-line

## 1. The red cases (do first)

- [x] 1.1 Realign `tools/loop/fixtures/scenarios/submodule/issues.json`: items 71 and 72 state
      `The fix lands in the submodule` on a line of their own instead of naming the path; items 74 (a body quoting
      `app/src/main/cpp/libosmscout` as the file the dirt was seen on) and 75 (a body quoting the marker inline and
      in backticks) are added. Verify: `prescreen#aQuotedSubmodulePathIsEvidenceNotAClaim` and
      `prescreen#aFixInTheSubmoduleIsAPersonsPatch` are red on the base with the fix reverted —
      `evidence/red-on-base-aQuotedSubmodulePathIsEvidenceNotAClaim.xml` and
      `evidence/red-on-base-aFixInTheSubmoduleIsAPersonsPatch.xml` (`tests=1 failures=1` each,
      2026-10-27T13:57:53Z).
- [x] 1.2 Flip `prescreen#aFixInTheSubmoduleIsAPersonsPatch` onto the marker (items 71/72 `needs-decision`, the
      reason still naming the path and "a person lands the patch", 73 eligible, queue 3). Verify: red on the base,
      green with the fix.
- [x] 1.3 Add `prescreen#aQuotedSubmodulePathIsEvidenceNotAClaim`: items 74 and 75 stay `eligible` and the queue
      holds the three items whose fix is this tree's. Verify: red on the base
      (`expected 'eligible', got 'needs-decision'`), green with the fix.

## 2. The verdict (`agent-issue-loop` — "The pre-screen refuses only a fix stated to land in the submodule")

- [x] 2.1 `tools/loop/prescreen.sh`: `names_the_submodule` becomes
      `states_the_fix_lands_in_the_submodule` — the profile's path and its basename are no longer searched; the answer
      is whether the title or body carries a line beginning `The fix lands in the submodule`, and the profile's path
      is still read so a project with no submodule owes no refusal and the reason names the path a patch lands in.
      Verify: both cases above pass; `bash tools/loop-selftest.sh --group prescreen` is green.
- [x] 2.2 `tools/loop/prescreen.sh`'s comment block above the function states the new signal and the three items it
      parked (#309, #310, #305). Verify: the file carries no `names_the_submodule` and the reason line is unchanged.

## 3. The mutation (`tools/loop-mutations.sh`)

- [x] 3.1 Record `69.1` relaxes the line anchor to `grep -qF` on the same marker. Verify:
      `bash tools/loop-mutations.sh --only 69.1` reads
      `RED 69.1 prescreen#aQuotedSubmodulePathIsEvidenceNotAClaim … expected 'eligible', got 'needs-decision'` and
      `restore 69.1 … green again` (`evidence/mutation-69.1.log`), and `--check` applies the record.

## 4. The authoring convention (`.pi/skills/create-issue/SKILL.md`)

- [x] 4.1 The marker is documented beside the body fields, with what the pre-screen reads and what it does not.
      Verify: `tools/check-doc-routes.sh` and `tools/check-todo-references.sh` are green, and
      `tools/check-doc-routes-selftest.sh` too.

## 5. The spec delta

- [x] 5.1 `specs/agent-issue-loop/spec.md`: one added requirement, two scenarios, each naming exactly one case.
      Verify: `tools/check-openspec-hygiene.sh` is green and `tools/loop-selftest.sh --list` names both cases.

## 6. The gate

- [x] 6.1 No compiled file changed, so no build is owed; the engine's own suite and its structural checks are.
      Verify: `./tools/loop/gate.sh` run once, its `LOOP-GATE:` line quoted in the report and its tallies copied into
      `evidence/gate-tallies/` and under the run's own tally directory.

## 7. Evidence and archive

- [x] 7.1 Copy the cited XMLs and logs into `evidence/` and into the item directory.
      Verify: `bash tools/loop/evidence-check.sh openspec/changes/fix-a-submodule-fix-claim-is-a-stated-line --root .`
      reports no dangling pointer.
- [x] 7.2 Archive the change and confirm the delta merged into `openspec/specs/agent-issue-loop/spec.md`.
      Verify: the archive directory exists and the capability spec carries the requirement.
