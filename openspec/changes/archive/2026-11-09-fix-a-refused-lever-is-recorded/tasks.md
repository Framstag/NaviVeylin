# Tasks

Reference: `proposal.md` for the evidence and the probe, `design.md` for the single fix and the alternatives the
records eliminated, `guidelines/Backlog.recipes.md:254` (section 7b) for the section that gains the paragraph. No requirement is
added, so this change carries no `specs/` delta and no case.

## 1. The red probe

- [x] 1.1 `evidence/refusal-record-probe.sh` — reads the body of section 7b (`guidelines/Backlog.recipes.md:254`), from its
      heading to the next `## ` heading, and requires `#833`, `refused` and the three recorded contexts
      (`200 427`, `200 733`, `201 981`) in it; writes one JUnit-shaped tally.
      Verify: run against the section as the base `8a2f1adbdaced0f79c4c2fccf5dfeab321d07a47` carries it (`git show
      <base>:guidelines/Backlog.recipes.md` into a scratch root, passed as `--root`), it fails `tests=1 failures=1
      — missing '#833' '200 427' '200 733' '201 981'` (`evidence/red-on-HEAD.log`, 2026-11-09T21:46:54Z).

## 2. The record

- [x] 2.1 `guidelines/Backlog.recipes.md:254` (section 7b) — one paragraph after the bound's measurement and before the hit-rate
      paragraph: the lever above the stop is refused, with the run of 2026-11-07T19:53Z, the three items it ended at
      the bound and the work each already carried, the patches each kept, and the value unchanged at 200 000; and
      #754's and #809's readings named as what the bound owed instead.
      Verify: `bash evidence/refusal-record-probe.sh` → `tests=1 failures=0` (`evidence/after-change.log`).

## 3. The structural checks this delta owes

- [x] 3.1 `bash -n evidence/refusal-record-probe.sh`, `bash tools/check-doc-routes.sh`,
      `bash tools/check-openspec-hygiene.sh` and `bash tools/check-change-readiness.sh
      openspec/changes/fix-a-refused-lever-is-recorded` — the shell file parses (`bash-n exit=0`), every section
      reference resolves, the opened change is well formed, and the change's readiness line is `LOOP-CHANGE-READY: ok`
      (`evidence/structural-checks.log`).

## 4. Verification

- [x] 4.1 The probe green on the change (`evidence/after-change.xml`, `evidence/after-change.log`).
- [x] 4.2 `bash tools/loop/measure.sh --change fix-a-refused-lever-is-recorded` — the block this change records, its
      pathspec and the checks it owes (`evidence/structural-checks.log`). The gate is not this change's: the run has
      deferred it to a fresh child, which reads the diff first.
