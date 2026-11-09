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
      — missing '#833' '200 427' '200 733' '201 981'` (`evidence/red-on-HEAD.log`, 2026-11-09T22:01:23Z; the tally
      carries that run's clock in its `run` property).

## 2. The record

- [x] 2.1 `guidelines/Backlog.recipes.md:254` (section 7b) — one paragraph after the bound's measurement and before the hit-rate
      paragraph: the lever above the stop is refused, with the run of 2026-11-07T19:53Z, the three items it ended at
      the bound and the work each already carried, the patches each kept, and the value unchanged at 200 000; and
      #754's and #809's readings named as what the bound owed instead.
      Verify: `bash evidence/refusal-record-probe.sh` → `tests=1 failures=0` (`evidence/after-change.log`).

## 3. The structural checks this delta owes

- [x] 3.1 `bash -n evidence/refusal-record-probe.sh`, `bash tools/check-doc-routes.sh`,
      `bash tools/check-openspec-hygiene.sh` and `bash tools/check-change-readiness.sh
      openspec/changes/archive/2026-11-09-fix-a-refused-lever-is-recorded` — the shell file parses (`bash-n exit=0`), every section
      reference resolves, the opened change is well formed, and the change's readiness line is `LOOP-CHANGE-READY: ok`
      (`evidence/structural-checks.log`).

## 4. Verification

- [x] 4.1 The probe green on the change (`evidence/after-change.xml`, `evidence/after-change.log`).
- [x] 4.2 `bash tools/loop/measure.sh --change fix-a-refused-lever-is-recorded` — the block this change records, its
      pathspec and the checks it owes (`evidence/structural-checks.log`). The gate is this change's: round 1 ran it
      (`evidence/gate-round-1.log`, kept for the record) and round 2 runs it again on the tree this round's
      corrections produced, so the record names the tree the reviewer reads (`evidence/gate.log`).

## 5. The round-1 review's findings, corrected

- [x] 5.1 BLOCKING — the gate had measured a tree this change was afterwards corrected on (`LOOP-GATE-TREE:
      head=8d8a1bfb… diff=0420cbe7…`, against the tree the round read at `head=1b4ae8fa… diff=38d53474…`): the gate
      runs again on the corrected tree, and its record is `evidence/gate.log`; the earlier run is kept beside it as
      `evidence/gate-round-1.log`.
- [x] 5.2 `proposal.md` — "within a thousand" → "within two thousand" (201 981 is 1 981 above 200 000); `design.md` —
      "sat within a thousand tokens of it" → "sat within two thousand tokens of it".
- [x] 5.3 `proposal.md` — "four `red-on-HEAD-*.log`" → the three names the tree carries.
- [x] 5.4 `design.md` — "Section 7b grows by 761 B in 8 lines" → "grows by 8 lines (`LOOP-MEASURE … insertions=8`)";
      the byte figure is dropped, because no command in the record produced it.
- [x] 5.5 `design.md` — the rule that a rule is stated once is cited at
      `openspec/specs/documentation-ownership/spec.md:259`; the skill-scoped `:417` is dropped.
- [x] 5.6 `design.md` — `guidelines/Backlog.why.md` §12 "The decisions, and why" is named among the alternatives §13
      was eliminated against.
- [x] 5.7 `guidelines/Backlog.recipes.md` — the paragraph narrows "recorded three of its items past the bound" to
      what the records say: only 719's own record names `bound` as its stop reason, and 753 and 819 carry the reading
      and the kept patch and no stop reason.
- [x] 5.8 `tasks.md` 3.1 and 4.2 — the change's own paths are the archived ones, and the gate statement is the one
      this change actually ran.
- [x] 5.9 the probe's tally carries the run's clock (a `run` property), and the red and the green tally were re-taken
      in one run each, so each log and its XML carry the same time (`evidence/red-on-HEAD.log`/`.xml`,
      `evidence/after-change.log`/`.xml`, 2026-11-09T22:01:23Z).
