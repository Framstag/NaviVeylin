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

`issues_login` turns that status into the third outcome it needed (login / empty-at-0 / unreachable), and the
identity read happens in `issues_preflight`, which every write path and `policy.sh preflight` already call.
Neither caller reads the login itself any more: an assignment in a caller captured the empty string of an
outage and discarded the status that said so, which is how eight entry points kept reporting
`author-not-allowlisted` for a network failure after the seam had the answer (round 2). `policy.sh check`
reads no identity at all — it judges the issue's own author from the record it is handed — so the read moved
into the `preflight` branch instead of running before the mode branch.

Two calls are exempt from the retry because each is an append the tracker may already have accepted: `gh
issue comment` and `gh issue create`. A retry of either files the same finding or posts the same comment a
second time, and their transport failure is passed to the caller with its own status.

A function, not a set of edits: no existing call site had to change, `command gh` reaches the real CLI, and
the self-test's stub on `PATH` is still what runs. A *new* call site could still write `command gh` itself,
so `lib#onlyTheSeamCallsTheRealCli` fails the moment one does.

One reader stays as it was: `tools/sensors/guard.sh:130` prints the identity for a log line and makes no
policy decision, so it still reports `(unknown login)` when the read failed — an absent identity in a
display field, not a verdict about an item.

## Alternatives the evidence eliminated

- **Retry at each call site.** Rejected: the call sites are 30+ across `tools/issues`, `tools/loop` and
  `tools/sensors`, and the item's own report shows three of them failing independently — a per-site retry is
  the drift the tooling's design D8 exists to prevent.
- **Propagate the login status at each call site.** Rejected by the first round's own outcome: eight call
  sites repeated `login=$(issues_login)` and dropped the status, so the contract "no caller reads an outage
  as a refusal" was false at every one of them. A distinction every caller must re-implement is a distinction
  the shared path has to own; the read moved into `issues_preflight` and the assignment disappeared from all
  eight.
- **Report an unreachable tracker as `unknown` for `check` too.** Rejected: `check` judges the issue's own
  author from the JSON it was handed and never reads the running identity, so the read before the mode branch
  made a decision that needs no tracker fail on an outage. It moved into the `preflight` branch.
- **Treat a failed identity read as a refusal (status quo).** Rejected by the incident: the run wrote
  `agent/excluded-stale` and a policy reason onto `#129` for an outage the item had nothing to do with.
- **Treat an unreachable tracker as allow (fail-open).** Rejected: it would authorise work while nothing can
  verify the authorisation label, and the run's invariant is that authorisation is read from the tracker.
- **A `retryable` field on the run state plus an external relauncher.** Not eliminated as an idea — it is
  recorded as a non-goal: it changes the state schema and needs a launcher, and the measured incident (one
  `i/o timeout` ending a 182-minute run) is fully addressed by the retry. A sustained outage still stops the
  run, which is correct.

## Blast radius

- `tools/issues/lib.sh` — the seam, the classifier, the new exit code, `issues_login`, `issues_preflight`.
- `tools/issues/policy.sh` — the unknown branch, and the identity read narrowed to `preflight`.
- `tools/issues/update.sh`, `create.sh`, `close.sh`, `answer.sh`, `reopen.sh`, `import.sh`,
  `resolve-stale.sh` and `tools/sensors/run.sh` — the repeated login assignment is gone; each now calls
  `issues_preflight "$profile"`.
- `tools/issues/fixtures/gh` — transient-failure injection for the cases.
- `tools/issue-tooling-cases.sh` — eight new cases: four from the first round and four from the second.
- `tools/issue-tooling-selftest.sh` — the footer names the tree (`HEAD <sha>`, a digest of the working tree)
  and the instant, so the log dates itself and says which tree it measured instead of relying on its mtime.
- `openspec/specs/agent-issue-loop/spec.md` and this change's delta — two requirements, seven scenarios.
- `tools/loop/**` is unchanged: it inherits the seam through `tools/loop/lib.sh`'s `source` of the tooling's
  library. No Kotlin/Java/C++ file is touched, so no Gradle gate is owed.

## Rollback

Revert the four files; nothing else observes the seam. The one behaviour a revert restores is the base
commit's: a transport failure is read as an answer.

## Risks

- A retried write could repeat a non-idempotent call. The tooling's appends are `gh issue comment` and `gh
  issue create`; both are exempt, so no retry can post a comment twice or file one finding twice. Label
  adds/removes, edits and closes are idempotent and are retried.
- The default backoff costs up to 85 s (`5 + 20 + 60`) for one call that is genuinely failing. That is a bound
  per call, not per run: a run makes several tracker calls, so an outage that lasts minutes costs several
  such waits. It is still bounded by the calls the run makes, and the self-test sets the delays to `0 0 0`.
- An exempt append that fails is not `tracker-unreachable`; its status is the CLI's own. A caller must therefore
  not read the failure as "nothing was written" — the comment or the issue may have landed. The tooling's
  writers already stop on it rather than retrying or reporting success.
