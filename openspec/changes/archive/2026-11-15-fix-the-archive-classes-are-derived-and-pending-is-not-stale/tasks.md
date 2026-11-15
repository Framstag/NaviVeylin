# Tasks — the archive's classes derived on read, and the two stale states reported apart

The behaviour shipped before this change directory was written (commit `76588853`, its figure corrected by
`1eddc98c`, its case row restored by `cd45d567`); the tasks below are the steps that landed with the evidence that
reads them back from the tree, and the artifact tasks that record the change. Nothing in this round was run, built or
mutated; task 5.1 names what that leaves unestablished.

- [x] 1.1 `tools/loop/tests/loop-cases.sh`: `report#theArchivesClassesAreDerivedNotInvented` written first, registered
      (`:378`), dispatched (`:1226`), declaring its files. It archives a run of five items — one `refused`, one whose
      note quotes the turn bound, one that recorded nothing, one a review round's bound ended, one closed — and
      asserts the class line: `bound 1`, `refused 1`, `of 4 blocked or cut record(s): 2 decided, 1 undecidable, 1 ended
      by a review round, not by the item`, no `unclassified`, no closed record, and `archive classes: -` for a project
      with no archive. Verified: the case is the one all four scenarios of the `agent-issue-loop` delta name
      (`tools/loop/tests/loop-cases.sh:13541`–`13569`).
- [x] 1.2 `tools/loop/report.sh`: the archive is totalled by the derivation (`:306` `stop_class` inside the jq), with
      the decided classes, the undecidable records and the round-ended ones counted apart (`:307`–`318`), the
      `archive classes: ` line printed (`:321`) and `-` where no record can carry a class (`:304`). Verified: the
      case's four assertions; and the run-side rate the line stands beside is unchanged
      (`report#theBoundRateIsNamed`, the archive read still over every record — `report.sh:277`–`289`).
- [x] 1.3 `tools/loop/lib.sh`: `stop_class` as a declaration inside `bound_class_jq` (`:326`, the definition at
      `:346`), so the report and the analysis pass share one reader instead of two readings of `stopReason`.
      Verified: the case's `bound 1` — a record whose note quotes the bound is decided by the declaration, not by the
      report's own copy of it.
- [x] 1.4 `.pi/skills/analyse-loop-run/bin/analysis.sh`: the pass sources the declaration (`:38`) and totalled both
      of its figures through it (`:276`, `:286`), replacing `(.stopReason // "unclassified")` — the expression that
      printed `unclassified 119`. Verified: the mutation below, and the archive census in the evidence file.
- [x] 1.5 `tools/loop/tests/loop-mutations.sh`: mutation `922.1` (`:1170`) puts the old reader back
      (`{cls: (.stopReason // "unclassified"),`) and names `report#theArchivesClassesAreDerivedNotInvented` as the
      case that must fail on it. Verified: the record applies to the file it names and the case's
      `assert_not_contains … unclassified` is what it breaks (`tools/loop/tests/loop-mutations.sh --check`).
- [x] 2.1 `tools/gen-feature-list.sh`: the two states split — `pending` as the intersection of the open changes'
      delta ids and the classified-but-not-shipped ids (`:205`), `stale` as the rest of that set (`:206`) — with the
      stderr sentences apart (`:260` "classified ahead of its archive (an open change's delta — unfinished work, not
      a stale entry)", `:264` "stale classification entry for a spec that no longer exists"), the check-mode figures
      (`:288`–`289`) and the report's (`:438`–`439`). Verified: the four self-test lines at
      `tools/tests/gen-feature-list-selftest.sh:326`–`331`.
- [x] 2.2 `tools/tests/gen-feature-list-selftest.sh`: `gate: a delta-only classification is reported as pending, not as
      stale`, `gate: a delta-only classification is not a stale entry`, `gate: the pending entry is named as one`,
      `gate: a pending entry is not reported as stale`, beside the shipped stale cases (`gate: a stale classification
      entry does not fail the run`, `gate: a stale entry is reported`, `gate: the stale entry is named`) which are
      unchanged. Verified: the four lines assert `pending (classified ahead of its archive): 1`, `stale: 0`, the
      stderr sentence, and the absence of the stale sentence for a pending entry.
- [x] 3.1 `guidelines/FeatureList.md` §4 states the two states and the measurement that split them (`:154`–`159`,
      `stale: 7` holding five pending lines), and `guidelines/Backlog.recipes.md` §7e gains the report's three-sided
      archive tally with the 119 corrected to the blocked half it came from (`1eddc98c`). Verified:
      `bash tools/check-doc-routes.sh` reports every section reference resolving (`evidence/structural-checks.txt`).
- [x] 3.2 The deltas in this change: `specs/agent-issue-loop/spec.md` as `## ADDED Requirements` (one requirement, four
      scenarios) and `specs/spec-feature-index/spec.md` as `## MODIFIED Requirements` on "An unclassified spec id
      stops the run" — the shipped scenarios repeated in their shipped order, the one whose answer moved (`A delta
      classified before it ships passes the run`, `stale` → `pending`) rewritten in place under its own name, and two
      scenarios added. Verified: `openspec validate
      fix-the-archive-classes-are-derived-and-pending-is-not-stale --strict` (`evidence/validator-strict.txt`).
- [x] 4.1 `design.md` records the decisions the artifact needed: the two faults are one shape (an absence read as a
      measurement) and one commit, so they ride one change with two capability deltas rather than two change
      directories; #926's owning capability is `spec-feature-index` and not the `feature-list-generation` label on the
      item, because the shipped text it edits lives there and the one that mentions `stale` in passing stays true; and
      the census is `ADDED` because no shipped requirement totals the archive. Verified: the readings in
      `evidence/readings.md`, which name each shipped site both deltas rest on.
- [x] 5.1 What this change does **not** cover: #926 carries **no mutation record** — `tools/tests/` holds mutation
      sets for `check-change-readiness`, `check-doc-routes`, `check-glossary`, `check-rule-routes` and the loop
      engine, and none for `gen-feature-list.sh` — so the split is held by the four self-test lines and by no
      falsification a `--check` can replay. The archive census figures are a reading of the machine-local
      `.pi/issue-loop/runs/` taken 2026-11-15, and `gitignore`d: `evidence/readings.md` records the command and
      the numbers, and a reader cannot re-derive them from the tree. The shipped commit records no gate verdict for
      either half; the only gate-shaped evidence in the pair is `cd45d567`, which found `1eddc98c` had displaced the
      row of `state#aCutRecordsTheFilesItsPatchCarries` and left the #922 case selectable nowhere.