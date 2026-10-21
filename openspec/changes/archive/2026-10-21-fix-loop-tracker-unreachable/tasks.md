# Tasks — fix-loop-tracker-unreachable

## 1. The red case

- [x] 1.1 Add `lib#retriesATransientFailure`, `lib#reportsAnUnreachableTrackerAfterTheLastRetry`,
  `lib#doesNotRetryAnAuthoritativeFailure` and `policy#preflightReportsAnUnreachableTracker` to
  `tools/issue-tooling-cases.sh` (registry, dispatch and bodies), and extend `lib#exitCodeConstants` to the
  fifth exit code. Verification: against the base commit's engine the probes in `evidence/red-on-HEAD.log`
  answer `author-not-allowlisted` (exit 3) for a transient identity failure and an empty item list for a
  transient read (`tests: 4 case(s) introduced, 4 red`; the shell suite has no JUnit XML and reports
  `N of M cases failed`).
- [x] 1.2 Add `GH_STUB_TRANSIENT_FAIL` / `_TIMES` / `_STATE` to `tools/issues/fixtures/gh`. Verification: the
  red probe above uses it; the stub never writes into the fixture tree.

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
  in `evidence/`.
- [x] 3.2 `bash tools/loop-selftest.sh` — the gate for `tools/loop/**` — not run as this change's gate: no file
  under `tools/loop/` changed. The shared library's behaviour change reaches the loop through `source`, and
  the loop suite was run once on the base commit for the record (`127 of 128`; `worktree#keepsByDefaultAndRemovesOnRequest`
  fails at the base commit, before this change).
- [x] 3.3 `bash tools/check-portability.sh` — the structural check this change owes instead of a Gradle
  selection: the new seam is engine code and names no project value (`check-portability: OK — 44 engines`,
  `evidence/check-portability.log`). `tools/declared-cases.sh` selects Gradle `*Test` classes and has nothing
  to select here.
- [x] 3.4 `openspec validate fix-loop-tracker-unreachable --strict` — the delta parses; the one warning is
  the requirement's length.
