# Design: fix-non-http-url-scheme

## Context and evidence

The defect reproduces in one unit case. `HttpUrlFetcher.openConnection` is the only place a URL becomes a
connection, and both `text()` and `download()` already route a private `UnusableUrl` to
`unusableUrlFailure` → `RepositoryFailure.MalformedUrl`:

| step | code | behavior |
|---|---|---|
| cleartext policy | `HttpUrlFetcher.kt:139` `policyRefusal` | parses the URI and consults the policy for an `http` scheme only |
| parse | `HttpUrlFetcher.kt:120-124` | `URI.create(url).toURL()`; a failure throws `UnusableUrl` |
| cast | `HttpUrlFetcher.kt:125` | `parsed.openConnection() as HttpURLConnection` — no scheme check |
| classify | `HttpUrlFetcher.kt:151-154` `unusableUrlFailure` | `UnusableUrl` → `MalformedUrl` |
| generic catch | `HttpUrlFetcher.kt:60-61` / `:88-89` | any other `Exception` → `failureFor` → `TransportFailed` |

For `ftp://127.0.0.1/names.json`, `URI.create(...).toURL()` succeeds and returns a URL whose
`openConnection()` is a `sun.net.www.protocol.ftp.FtpURLConnection`; the cast at `:125` throws
`ClassCastException`, which falls through to the generic catch and reaches the user as
`TransportFailed(cause=… cannot be cast to HttpURLConnection)`. The red case
`HttpUrlFetcherTest#aNonHttpSchemeIsReportedAsUnusable` pins exactly this: `MalformedUrl` expected,
`TransportFailed` observed (XML `tests="8" failures="1"`, 2026-10-10T07:26:08.451Z).

## D1 — Where a non-HTTP scheme is rejected

The one fix is to validate the scheme at the parse step, where `UnusableUrl` is already thrown, so the
existing classification and UI need no change:

```kotlin
if (!isHttpScheme(parsed.protocol)) {
    throw UnusableUrl(IllegalArgumentException("unsupported scheme ${parsed.protocol}"))
}
```

Alternatives the evidence eliminates:

| option | why the evidence eliminates it |
|---|---|
| Catch `ClassCastException` in the generic catch and reclassify it as `MalformedUrl` | It treats the cast (the symptom) rather than the scheme (the cause). Any future `ClassCastException` from connection construction — not only a non-HTTP protocol — would be misreported as a URL problem, and the log line would name the cast rather than the offending scheme, so the user still cannot tell the fix is the scheme. |
| Add a new `RepositoryFailure` case (`UnsupportedScheme`) | The contract defines exactly one unusable-URL failure, and the existing user-facing text for it (`source_test_malformed_url` — "%1$s is not a usable address — enter it as https://host or http://host:port") already names the accepted form. A second case adds a `describe()` branch and new wording for a state with no distinct user action, and no requirement distinguishes it. The item's own candidate fix names the existing failure. |
| Reject at the URI parse (a stricter http-only parser) | `https` must stay supported, and `URI.create` already distinguishes the genuinely unparseable case the existing scenario covers. A scheme check belongs after parsing, beside the existing `policyRefusal` check, not inside the parse that owns the parse-failure contract. |
| Reject inside `policyRefusal` by widening its `http` test | `policyRefusal` answers the cleartext question, not the URL-validity question; widening it would conflate a policy refusal with an unusable URL and would miss `https` (it is deliberately exempt there). |

`parsed.protocol` is compared case-insensitively (`isHttpScheme`), because a scheme is case-insensitive and
`URL`/`URI` need not preserve the typed case. The accepted set is `http` and `https` — the two schemes an
`HttpURLConnection` can carry.

**Risk**: a scheme that is a known protocol but not HTTP (e.g. `ftp`, `file`, `jar`) now reports as unusable
instead of as a transport failure. That is the intended contract change, and no test or caller depends on the
old classification (the case that asserted a supported scheme is unaffected stays green).

## D2 — Blast radius and threading

- `text()` and `download()` are untouched: they already catch `UnusableUrl` before the generic `Exception`
  catch and already log through `unusableUrlFailure`, so both entry points change behaviour together.
- No new dispatcher, lock or lifecycle: the check reads a parsed string on the same `ioDispatcher` the request
  already runs on (`guidelines/Design.md` §4 is unaffected).
- `RepositoryFailure` gains no member, so the exhaustive `when` sites (`MapManagerScreen.describe()`,
  `MapManagerViewModel`) compile unchanged.
- Documentation honesty: the `HttpUrlFetcher` class KDoc and the `RepositoryFailure.MalformedUrl` KDoc both
  currently claim the failure is only about parsing; both are widened in this change to match the contract.

**Rollback**: revert the two-line check and the KDoc widenings. The tree then behaves exactly as before.

## Verification

| scenario | case |
|---|---|
| An unparseable URL is named as unusable | `HttpUrlFetcherTest#aMalformedUrlIsReportedInsteadOfThrown` (unchanged, must stay green) |
| A non-HTTP scheme is named as unusable (added requirement) | `HttpUrlFetcherTest#aNonHttpSchemeIsReportedAsUnusable` (red on HEAD, green after the fix) |
| A parseable URL is never reported as unusable | `HttpUrlFetcherTest#connectFailureIsReported` (an `http` URL's transport failure stays its own kind) |

Revert-check: one mutation — remove the scheme check — must fail `aNonHttpSchemeIsReportedAsUnusable` at its
`MalformedUrl` assertion, not earlier. Then restore and re-run forced. One forced both-flavor gate
(`./gradlew test -PforceTests --rerun-tasks`, production code changed) is the change's green evidence.
