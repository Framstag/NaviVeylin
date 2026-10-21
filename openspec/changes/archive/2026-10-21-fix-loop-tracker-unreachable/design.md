# Design — fix-loop-tracker-unreachable

## Root cause

A `gh` failure carries no meaning in the tooling: nothing asks whether the failure was the transport's or the
tracker's, so each call site reads the non-zero exit as an answer. `issues_login` folds an unreachable API
and an unauthenticated one into the same empty string (`tools/issues/lib.sh:100-102`), and `policy.sh:42`
hands that string to `Policy.preflight`, whose only reading of it is `author-not-allowlisted`
(`tools/issues/core/Policy.java:52-54`). At the same time the loop's item read is a single attempt whose
failure is a stop (`tools/loop/lib.sh:243-245`, `tools/loop/run.sh:632-634`) and the pre-screen's is a die
(`tools/loop/prescreen.sh:162`).

## The fix (one)

A single seam, defined once as the shell function `gh` in `tools/issues/lib.sh`, which every script sources
— directly, or through `tools/loop/lib.sh`, which sources it. The seam:

1. runs `command gh` and keeps its stderr;
2. classifies the failure against `ISSUES_GH_TRANSIENT_RE` — timeouts, DNS, resets/refusals, TLS and response
   timeouts, 5xx, GitHub's secondary rate limit;
3. retries a transient failure with the delays `ISSUES_GH_RETRY_DELAYS` names (default `5 20 60`), and
4. returns `ISSUES_EXIT_UNREACHABLE=75` with `tracker-unreachable: …` on stderr once they are spent, passing
   any other failure straight through with its own status and message.

`issues_login` turns that status into the third outcome it needed (login / empty-at-0 / unreachable), and
`policy.sh` answers `tracker-unreachable` — decision `unknown`, exit 75 — for the third. `gh issue comment` is
exempt from the retry because an append may have landed before the socket died.

A function, not a set of edits: a new call site cannot forget the seam, `command gh` reaches the real CLI,
and the self-test's stub on `PATH` is still what runs.

## Alternatives the evidence eliminated

- **Retry at each call site.** Rejected: the call sites are 30+ across `tools/issues`, `tools/loop` and
  `tools/sensors`, and the item's own report shows three of them failing independently — a per-site retry is
  the drift the tooling's design D8 exists to prevent.
- **Treat a failed identity read as a refusal (status quo).** Rejected by the incident: the run wrote
  `agent/excluded-stale` and a policy reason onto `#129` for an outage the item had nothing to do with.
- **Treat an unreachable tracker as allow (fail-open).** Rejected: it would authorise work while nothing can
  verify the authorisation label, and the run's invariant is that authorisation is read from the tracker.
- **A `retryable` field on the run state plus an external relauncher.** Not eliminated as an idea — it is
  recorded as a non-goal: it changes the state schema and needs a launcher, and the measured incident (one
  `i/o timeout` ending a 182-minute run) is fully addressed by the retry. A sustained outage still stops the
  run, which is correct.

## Blast radius

- `tools/issues/lib.sh` — the seam, the classifier, the new exit code, `issues_login`.
- `tools/issues/policy.sh` — the unknown branch.
- `tools/issues/fixtures/gh` — transient-failure injection for the cases.
- `tools/issue-tooling-cases.sh` — four cases.
- `tools/loop/**` is unchanged: it inherits the seam through `tools/loop/lib.sh`'s `source` of the tooling's
  library. No Kotlin/Java/C++ file is touched, so no Gradle gate is owed.

## Rollback

Revert the four files; nothing else observes the seam. The one behaviour a revert restores is the base
commit's: a transport failure is read as an answer.

## Risks

- A retried write could repeat a non-idempotent call. Only `gh issue comment` is non-idempotent among the
  tooling's writes and it is exempt; label adds/removes and edits are idempotent.
- The default backoff adds up to 85 s to a call that is genuinely failing. That is bounded and smaller than
  the item it protects; the self-test sets the delays to `0 0 0`.
