# Tasks — fix-loop-tracker-unreachable

## 1. The red case

- [x] 1.1 Add `lib#retriesATransientFailure`, `lib#reportsAnUnreachableTrackerAfterTheLastRetry`,
  `lib#doesNotRetryAnAuthoritativeFailure` and `policy#preflightReportsAnUnreachableTracker` to
  `tools/issue-tooling-cases.sh` (registry, dispatch and bodies), and extend `lib#exitCodeConstants` to the
  fifth exit code. Verification: against the base commit's engine the probes in `evidence/red-on-HEAD.log`
  answer `author-not-allowlisted` (exit 3) for a transient identity failure and an empty item list for a
  transient read (the shell suite has no JUnit XML; it reports `N of M cases failed`).
- [x] 1.2 Add `GH_STUB_TRANSIENT_FAIL` / `_TIMES` / `_STATE` to `tools/issues/fixtures/gh`. Verification: the
  red probe above uses it; the stub never writes into the fixture tree.
- [x] 1.3 The two dates a reader sees on `evidence/red-on-HEAD.log` are its probe date (`2026-10-20`, in its
  header) and the copy's mtime (`2026-10-21`, when it was archived); the log states one of the two, so the
  round-2 log `evidence/red-on-HEAD-round2.log` states a single UTC instant instead of relying on a copy's
  metadata.

## 2. The fix

- [x] 2.1 `tools/issues/lib.sh`: the classifier, the `gh` seam and `ISSUES_EXIT_UNREACHABLE=75`.
  Verification: `tools/issue-tooling-selftest.sh --case lib#retriesATransientFailure` and
  `--case lib#reportsAnUnreachableTrackerAfterTheLastRetry`.
- [x] 2.2 `tools/issues/lib.sh`: `issues_login` keeps its three outcomes apart. Verification:
  `--case policy#preflightRefusesUnknownLogin` is still green (an answered-but-foreign identity still
  refuses) and `--case lib#reportsAnUnreachableTrackerAfterTheLastRetry` is green.
- [x] 2.3 `tools/issues/policy.sh`: the `tracker-unreachable` branch, JSON and text. Verification:
  `--case policy#preflightReportsAnUnreachableTracker`.

## 3. The gate

- [x] 3.1 `bash tools/issue-tooling-selftest.sh` — the gate for `tools/issues/**` — green, with its case count
  in `evidence/gate-issue-tooling.log` (`all 110 cases passed`).
- [x] 3.2 `bash tools/loop-selftest.sh` — the gate for `tools/loop/**` — not run as this change's gate: no file
  under `tools/loop/` changed. It was run against the fixed tree for the record
  (`evidence/loop-selftest.log`): `1 of 128 cases failed`, on `worktree#keepsByDefaultAndRemovesOnRequest`
  ("git still lists the worktree that was removed"). `tools/loop/**` is byte-identical to the base commit
  (`git diff 2e732f0..HEAD -- tools/loop` is empty), so that case's state belongs to the checkout's git
  worktree bookkeeping, not to this change.
- [x] 3.3 `bash tools/check-portability.sh` — the structural check this change owes instead of a Gradle
  selection: the new seam is engine code and names no project value
  (`check-portability: OK — 44 engines and 2 templates`, `evidence/check-portability.log`).
  `tools/declared-cases.sh` selects Gradle `*Test` classes and has nothing to select here.
- [x] 3.4 `openspec validate agent-issue-loop --type spec --strict` and `openspec doctor` — the delta parses
  and the capability spec's scenarios name the cases (`evidence/openspec-validate.log`). The strict run
  reports three length warnings on this spec (this change adds two of them) in the same class as the 80 the
  repository already carries; they are not errors the change introduces a new class of.

## 4. Round 2 — the reviewer's two blocking findings

- [x] 4.1 `tools/issues/lib.sh`: the identity read moves into `issues_preflight`, which exits 75 when the
  tracker did not answer, and the eight call sites (`update.sh`, `create.sh`, `close.sh`, `answer.sh`,
  `reopen.sh`, `import.sh`, `resolve-stale.sh`, `tools/sensors/run.sh`) stop repeating
  `login=$(issues_login)`. Verification: `--case update#reportsAnUnreachableTrackerInsteadOfARefusal` is red
  on the first round's tree (`author-not-allowlisted`, exit 3) and green after the fix.
- [x] 4.2 `tools/issues/policy.sh`: the identity read happens in the `preflight` branch only, so `check`
  answers from the issue record during an outage. Verification:
  `--case policy#checkAnswersWithoutAnIdentityRead` is red on the first round's tree (exit 75) and green
  after.
- [x] 4.3 `tools/issues/lib.sh`: `gh issue create` is exempt from the retry beside `gh issue comment`.
  Verification: `--case lib#doesNotRetryAnAppend` is red on the first round's tree (`status:75`, i.e. four
  attempts) and green after (one attempt, the failure passed through).
- [x] 4.4 `tools/issue-tooling-cases.sh`: `lib#onlyTheSeamCallsTheRealCli` fails if a call site reaches
  `command gh` instead of the seam. Verification: it passes on both trees and is the guard the design's
  "a new call site cannot forget the seam" claim needed.
- [x] 4.5 `tools/issue-tooling-selftest.sh`: the footer carries `HEAD <sha>`, a digest of the working-tree state
  and a UTC instant, so a captured gate log proves the tree it came from. Verification:
  `evidence/gate-issue-tooling.log` ends with all three.
- [x] 4.6 `evidence/red-on-HEAD-round2.log`: the three new claims driven against the first round's tree, with
  the production files restored from `17f6b8d` in a read-only copy (`/tmp/red231r2`). Verification: the three
  cases fail there for the three reasons above; the change root was not modified by the probe.
