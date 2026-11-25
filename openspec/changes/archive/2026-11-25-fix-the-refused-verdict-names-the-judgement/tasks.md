# Tasks — the refused-class verdict names the judgement

- [x] 1. Write the red case before the fix: `case_prescreen_theRefusedVerdictNamesNoClassValue` refuses
      `improvement` (issue 24 of the `six` fixture) instead of the fixture's customary `feature`, and add its two
      registry rows — a body with no row is the `--list`/selection disagreement issue #929 refuses. Take the red
      proof on the base commit with `tools/loop/prescreen.sh` at its base text:
      `bash tools/loop/tests/loop-selftest.sh --case 'prescreen#*Refused*'` — exit 1,
      `the verdict names the judgement, not a class value: expected 'class-refused', got 'class-feature'`,
      `loop-selftest: 2 of 5 cases failed` (`evidence/red-on-head.log`; the proof's timestamp is the run's own copy's
      mtime — `stat -c '%y' /home/tim/projects/NaviVeylin/.pi/issue-loop/items/1030/red-on-head.log` →
      `2026-11-25 05:40:19 +0100`, that is `2026-11-25T04:40:19Z`); and the trace fixture
      on its own, `bash tools/loop/tests/loop-selftest.sh --case 'prescreen#refusalLeavesATraceInTheTracker'` —
      exit 1, `1 of 1 cases failed` (`evidence/red-on-head-trace.log`).
- [x] 2. Name the judgement in the branch: `tools/loop/prescreen.sh:408`'s verdict becomes `class-refused`, the
      reason still carries the class, and the refusal-label mapping at `:700` keys on the new word. Verified by the
      prescreen group: `bash tools/loop/tests/loop-selftest.sh --group prescreen` — exit 0,
      `all 45 cases passed` (`evidence/green-prescreen-group.log`), and the trace fixture alone green
      (`evidence/green-focused.log`).
- [x] 3. Falsify the verdict clause: set the branch back to `class-feature`, everything else in place, and expect the
      new case red for that one assertion (`evidence/mutation-red.log`); restore the file byte for byte and expect
      it green again (`evidence/mutation-restored.log`), with the restored file's checksum equal to the recorded
      one (`evidence/mutation-before.sha256`, checked in `evidence/structural-checks.log`).
- [x] 4. Falsify the reason clause on its own: remove `${class}` from the reason with the verdict left at
      `class-refused`, so the AND clause fails on its own assertion rather than behind the verdict's
      (`evidence/mutation-red-reason.log`); restore and confirm the recorded checksum still holds —
      `tools/loop/prescreen.sh: OK` (`evidence/structural-checks.log`).
- [x] 5. Structural check in place of a build — the delta compiles nothing, and the path the suite gates is shell:
      `bash -n tools/loop/prescreen.sh`, `bash -n tools/loop/tests/loop-cases.sh`, `bash tools/check-portability.sh`
      (OK — 64 engines and 8 templates name no project value) and the driver's own declaration case
      `loop-selftest.sh --case 'driver#everyCaseDeclaresTheFilesItCanReach'` — PASS, and
      `bash tools/loop/tests/loop-mutations.sh --check` — all 463 cases passed (the gate's own record,
      `evidence/gate-record.txt`); the shell syntax, the portability scan and the restored file's checksum are in
      `evidence/structural-checks.log`.
- [x] 6. Name the delta: `specs/agent-issue-loop/spec.md` modifies "The run acts only on authorised, eligible items"
      (its requirement now says a verdict's word names the judgement and never a value of the profile's vocabulary,
      its refusal-trace scenario carries `class-refused`, and its new scenario is the red case) and "A feature is
      planned before it is applied" (the two scenarios that spelled the old word).
- [x] 7. Take the measuring command's block for this change and keep it beside the evidence:
      `./tools/loop/measure.sh --change 2026-11-25-fix-the-refused-verdict-names-the-judgement` —
      `LOOP-MEASURE-SUITE cases=554`, `LOOP-MEASURE-CHANGE 2026-11-25-fix-the-refused-verdict-names-the-judgement
      scenarios=10 cases=4 unnamed=6` (`evidence/measure-block.log`); the unnamed six are the scenarios the two
      requirements already carried without a case on the base commit.
- [x] 8. Run the evidence check and the archive before the gate, so the tree the gate measures is the tree the round
      reads: `bash tools/loop/evidence-check.sh openspec/changes/fix-the-refused-verdict-names-the-judgement --root .`,
      then `openspec archive --yes fix-the-refused-verdict-names-the-judgement`
      (`evidence/evidence-check.log`, `evidence/archive.log`).
- [x] 9. Run the project's gate once, detached, and quote its own `LOOP-GATE:` line
      (`evidence/gate-output.txt:87`, `:219`; the one red's attribution in `evidence/gate-hygiene-red.log`).
