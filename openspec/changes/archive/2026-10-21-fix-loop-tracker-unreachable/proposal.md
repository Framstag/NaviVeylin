## Why

A network failure between the host and GitHub was consumed as an *answer*: the run stopped on its first
attempt, and the identity read turned the outage into a policy refusal written onto an item.

- **Verified in the tree today** ✗ at the base commit `2e732f0e00fa771d04569483ffe80bc2ca3fa974`:
  - `tools/issues/lib.sh:100-102` — `issues_login()` is
    `gh api user --jq .login 2>/dev/null || true`. An unreachable API and an unauthenticated one both yield
    the empty string, so the caller cannot tell them apart.
  - `tools/issues/policy.sh:42` — `[ -n "$login" ] || login=$(issues_login)` then hands `--login ""` to
    `tools/issues/core/Policy.java:52-54`, whose only reading of an empty login is
    `Decision.refuse("author-not-allowlisted", "(no authenticated login)")`.
  - `tools/loop/lib.sh:243-245` — `loop_issue_json()` is one `gh issue view … 2>/dev/null`, whose non-zero
    status `tools/loop/run.sh:632-634` turns into `stop_run "gh could not read #$issue — the tracker did not
    answer"`; `tools/loop/prescreen.sh:162` turns the same failure into `loop_die`.
  - No `gh` call site anywhere retries: `grep -rn 'retry\|backoff' tools/issues tools/loop` finds the
    reviewer-answer retry and the loop's child restarts only.
- **Red test** (base commit `2e732f0e`, 2026-10-20, `tools/issue-tooling-selftest.sh`; the shell suite reports
  its own case count, there is no JUnit XML in this project's shell gates):

  | probe on the base commit's engine | result |
  |---|---|
  | `gh api user` fails transiently (`i/o timeout`), `policy.sh preflight --json` | `{"login":"","decision":"refuse","reason":"author-not-allowlisted","detail":"(no authenticated login)"}`, exit 3 |
  | `gh issue list` fails transiently twice, `list.sh --json --limit 1` | exit 0, **empty answer**, 1 attempt made — the outage was read as "no items" |

  The new cases `lib#retriesATransientFailure`, `lib#reportsAnUnreachableTrackerAfterTheLastRetry`,
  `lib#exitCodeConstants` and `policy#preflightReportsAnUnreachableTracker` are red against the base engine
  (they assert the retry, the `75` exit and the `tracker-unreachable` reason, none of which exist there);
  `lib#doesNotRetryAnAuthoritativeFailure` is green there by construction and is the guard against
  over-retrying. The transcripts are `evidence/red-on-HEAD.log`.
- **Root cause**: a `gh` failure is never classified by *what kind* of failure it is, so every call site reads
  a transport failure as the tracker's answer — an empty login, an empty item list, a stop.
- **One fix**: one retry seam through which every `gh` call passes, which retries only what the transport
  caused and reports an exhausted transient failure as *unknown* (`75`, `tracker-unreachable`) instead of an
  empty answer — plus the one caller that has a policy meaning, `issues_login`/`policy.sh`, branching on it.
- **Non-goals** (recorded, not silently dropped): the item also asks for a `retryable` field on the run state
  so a launcher can restart the run after a sustained outage, and for the mid-flight item to return to the
  queue. Both are a state-schema change consumed by the run's starter; this change only makes a *single*
  timeout stop being an answer, which is the effect the incident measured (one 182-minute run ended on one
  `i/o timeout`).
- **Owning capability**: `agent-issue-loop`. **Owning guideline section**: `Backlog.md` §4 "The invariants"
  (what the tooling may write and what it may not infer) with the run's failure handling in §9 "Checks".

## What Changes

- `tools/issues/lib.sh` gains `ISSUES_EXIT_UNREACHABLE=75`, one transient-failure classifier
  (`ISSUES_GH_TRANSIENT_RE`, `issues_gh_transient`) and one seam defined as the shell function `gh`, which
  wraps `command gh` and retries a classified transient failure with `ISSUES_GH_RETRY_DELAYS` (default
  `5 20 60`, i.e. three retries). `gh issue comment` is exempt because a retried append can post twice.
- `issues_login` returns `75` when the read was unreachable and keeps returning the empty string, at status
  0, when the tracker answered and named no identity.
- `policy.sh` answers `tracker-unreachable` (decision `unknown`, exit 75) rather than passing an empty login
  to the core, in both the JSON and the text form.
- `tools/issues/fixtures/gh` gains `GH_STUB_TRANSIENT_FAIL` / `_TIMES` / `_STATE`, so a case can fail the
  first N matching invocations the way a socket does.

## Impact

- Affected specs: `agent-issue-loop` (one added requirement).
- Affected code: `tools/issues/lib.sh`, `tools/issues/policy.sh`, `tools/issues/fixtures/gh`,
  `tools/issue-tooling-cases.sh` (three new cases plus one extended).
- Not affected: no Kotlin, Java or C++ file; no Gradle gate is owed by this change.
