# Tasks — one read of the compile marker, and the record that reaches it

Spec: `agent-issue-loop` (delta `specs/agent-issue-loop/spec.md`). Gate: `bash tools/loop/tests/loop-selftest.sh
--from-git 42ddc5469a3d2cc69bf039f542795db250a1450e`, `bash tools/loop/tests/loop-mutations-selftest.sh` and
`bash tools/loop/tests/loop-mutations.sh --check` — the three suites `tools/loop/loop.conf` maps to the files this
change touches. No Gradle build is owed: no compiled file changes (`guidelines/Build.md` §2, the scope block).

Route:

LOOP-ROUTE: guidelines/BuildEvidence.md §4e

The section already states the rule (*a regression test must fail against the unfixed revision first*), so no
guideline text is owed; this line is the one `tools/loop/evidence-check.sh` C6 resolves against the document's own
heading.

## 1. The driver's one read

- [x] 1.1 `tools/loop/tests/loop-selftest.sh`: `core_ensure` claims first and reads `$selftest_core_marker` once — the
  fast path above the claiming block is removed, the compile, the marker write and the claim release are kept, and the
  comment states that a second read is what made record `48.2` unnoticeable (issue #1047).
  Verify: `tools/look.sh 'selftest_core_marker'` names four occurrences in the driver — the declaration, the wait's
  `break`, the guard, the write.
- [x] 1.2 `tools/loop/tests/loop-mutations.sh`: record `48.2` quotes the surviving read at the function's top level
  (`^  if \[ ! -f "$selftest_core_marker" \]; then$`) and its comment says why one read is the whole guard (issue
  #1047). Verify: `bash tools/loop/tests/loop-mutations.sh --check --only 48.2` → `all 1 cases passed`
  (`evidence/mutations-48.2-check.log`).

## 2. The record, red

- [x] 2.1 `bash tools/loop/tests/loop-mutations.sh --only 48.2` → `RED 48.2`, the case's own log line quoted
  (`javac ran 2 times … expected '1', got '2'`), `restore 48.2 … green again`, tallies `red-when-mutated: 1 ·
  not-noticed: 0 · restore-failures: 0` (`evidence/mutations-48.2.log`).
- [x] 2.2 The other half of the invariant is untouched: `--only 48.1` → `red-when-mutated: 1 · not-noticed: 0 ·
  restore-failures: 0` (`evidence/mutations-48.1.log`).
- [x] 2.3 The base behaviour is recorded, not assumed: with the driver restored from `42ddc546`, `--only 48.2` prints
  `GREEN 48.2 … did not notice the mutation` and `not-noticed: 1` (`evidence/red-on-head.log`), and the driver was
  restored byte for byte afterwards (sha1 `668ad820bc2d6e27a91f658b98041d3a193f1de3`).

## 3. The guard that keeps the read single

- [x] 3.1 `tools/loop/tests/loop-mutations-selftest.sh`: case 11 counts the driver's `if [ ! -f "$selftest_core_marker"
  ]; then` reads and requires one, naming the file and the record it protects.
  Verify: the suite's tally `all 40 cases passed` (`evidence/mutations-selftest.log`), and on the base driver
  `1 of 40 cases failed … expected [1] got [2]` (`evidence/red-on-head-case.log`).

## 4. The change's own evidence

- [x] 4.1 Every log this change cites is copied into `evidence/` at the moment it is produced.
  Verify: `bash tools/loop/evidence-check.sh openspec/changes/fix-a-shadowed-guard-hides-the-compile-count --root .`.
