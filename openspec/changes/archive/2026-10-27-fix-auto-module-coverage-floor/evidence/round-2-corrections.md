# Round-2 corrections — item #319

Answers the round-1 review (`review-round-1-requested.md`) one finding at a time. No production file moved;
the only new artefact is this record.

## The blocking finding (§3, gate reality)

The round-1 gate ran before task 9 (the archive) and its log named the compiled step alone, so the seven
`openspec/**` and `*.md` checks the change's pathspec owes never executed on the shipped delta.

- Fixed by running the gate once on the **final** tree, after the spec merge and the archive:
  `evidence/gate.log` carries all eight steps (`LOOP-GATE: tests=6 failures=0 errors=0 (… 282s, … 0s)`), and
  each step's own tally is under `evidence/gate-tallies/` (eight files, one per step).
- The block this run is quoted from is `evidence/measure.txt`:
  `LOOP-MEASURE-GATE seconds=290 suites=8`.
- `tests=6` is the engine counting the hygiene selftest's `all 6 cases passed`; the doc-routes selftest prints
  `19 passed, 0 failed`, which the engine's summary parser does not read, so its tally reads `tests=0`. The
  gate's compiled step prints no case count at all (`tests=0`); the module suite it ran is counted from the
  result XMLs it wrote — `evidence/module-suite-tally.txt`, `classes=92 tests=885 failures=0 errors=0` — and
  that file now names the gate's own invocation, not a separate Kover run.

## §1 scenario coverage

- Scenario 1's case now dates itself: `coverage-floor-probe.sh` writes
  `timestamp="2026-10-27T13:44:49Z"` on the `<testsuite>`, and both `red-on-base.xml` and
  `green-on-change.xml` were re-emitted from the stored reports (`tests="1" failures="1"` and
  `failures="0"`, same numbers as before: `covered=23923 missed=10643 percentage=69.2` and
  `covered=25064 missed=9502 percentage=72.5`).
- Scenarios 2, 3, 4 and 6 are reworded to what their cases assert: the map strip's **count** and the action
  strip's zoom titles in order (not "the pan/exit and the zoom-in/zoom-out actions"); `attach` exactly once and
  `detach` exactly once (the case verifies counts, not the object); the screen **processes** both publications
  without faulting; and the status is published by the provider while the screen is started, then cleared on
  destroy. No case was weakened and no new case was added.
- Scenario 1's change-time clause ("having been below it on the base tree with the same arithmetic") is gone:
  the capability contract no longer states a fact about this commit.

## §2 cause or gap scope

- The requirement and the proposal now say what was measured. The rule is stated for a module the row reads;
  the change carries `auto`'s measurement only, and the proposal says so explicitly (`## What Changes`).

## §4 task honesty, §6 evidence pointers, §8 overreach

- `proposal.md` "Surface": the scenario count is corrected — `grep -c '^#### Scenario:'` reads **30** on the
  merged spec, six of them this delta's, so **24** existed before.
- `design.md` "Blast radius": the unsupported "91 → 92 classes" is gone; the change states the count it left
  (92 classes / 885 cases / 0 failures) and says the base class count is not recorded.
- `tasks.md` task 6: the command attribution is the one the tally file records (`awk` over the result XMLs),
  not `python3 tally.py`. Task 7 names the round-2 run and says round 1's record was the pre-archive one.
  Task 8 cites `evidence/evidence-check.txt` — the check run on the **archived** path, output carried.
- Stale copies dropped: `coverage-counters.md` (values contradicting the cited reports, never cited) and
  `red-on-base-319.xml` (a probe from the earlier attempt) are deleted, and the item's copy of
  `coverage-floor-probe.sh` is replaced with this change's script, so a reader following the item copy can
  reproduce both XMLs.

## §7 spec and guideline honesty

- The owning guideline section now carries the number it owns: `guidelines/Build.md` section 7's baseline
  records the 2026-10-27 re-measurement (`auto` INSTRUCTION 25 064 of 34 566 = 72.5 %, from 23 923 of 34 566 =
  69.2 %, with the suite at 92 classes / 885 cases / 0 failures). `design.md` "Blast radius" and "Rollback"
  name that sentence, and `tasks.md` task 5 records it as the step's second half.

`bash tools/loop/evidence-check.sh openspec/changes/archive/2026-10-27-fix-auto-module-coverage-floor --root .`
→ `PASS` (`evidence/evidence-check.txt`).
