# Proposal — fix-non-http-url-scheme

## Why

Root cause: `HttpUrlFetcher.openConnection` (`app/src/main/java/com/naviveylin/data/HttpUrlFetcher.kt:125`) casts the parsed URL's connection to `HttpURLConnection` without checking the scheme, so a URL with a known non-HTTP protocol (`ftp://host/x`) throws `ClassCastException`, which the caller's generic catch classifies as `TransportFailed`.
Evidence:    com.naviveylin.data.HttpUrlFetcherTest#aNonHttpSchemeIsReportedAsUnusable fails on HEAD — XML tests="8" failures="1" (`expected:<Failed(failure=MalformedUrl)> but was:<Failed(failure=TransportFailed(cause=class sun.net.www.protocol.ftp.FtpURLConnection cannot be cast to class java.net.HttpURLConnection …))>`), 2026-10-10T07:26:08.451Z
Repro:       ./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.data.HttpUrlFetcherTest"
Spec:        map-download-infrastructure / A base URL that cannot be parsed is reported as an unusable URL   Guideline: guidelines/Design.md §9

The parse-failure path the change `allow-lan-http-map-repository` added (`MalformedUrl`, spec
`map-download-infrastructure` — "A base URL that cannot be parsed is reported as an unusable URL") checks only
that the string becomes a URI. `URI.create("ftp://host/x").toURL()` succeeds, so the URL reaches the cast at
`HttpUrlFetcher.kt:125` and the platform's `FtpURLConnection` is cast to `HttpURLConnection`. The user sees the
transport wording (`source_test_unreachable`, "… could not be reached (… cannot be cast to …)") for a URL whose
fix is the scheme, exactly the parser-vs-user confusion the earlier change removed for the unparseable case. The
existing unusable-URL text already names the expected form ("enter it as https://host or http://host:port"), so
the failure is simply not routed to it.

## What Changes

- `HttpUrlFetcher.openConnection` validates the parsed URL's scheme before it opens the connection: a scheme
  that is neither `http` nor `https` throws the same private `UnusableUrl` the parse failure throws, so both
  `text()` and `download()` classify it as `RepositoryFailure.MalformedUrl` through the existing
  `unusableUrlFailure` path. No request is sent.
- The class KDoc and the `RepositoryFailure.MalformedUrl` KDoc are widened to say the failure covers a URL that
  is not an HTTP URL — unparseable, or a non-HTTP scheme — so neither doc keeps claiming the failure is only
  about parsing.

**Explicit non-goals** (recorded so they are not re-litigated during apply):

- No new `RepositoryFailure` case. The requirement defines one unusable-URL failure and its user-facing text
  already names the accepted form; a second case adds a UI branch with no distinct user action.
- No change to the cleartext policy (`policyRefusal` keeps handling `http` only) and no change to the accepted
  schemes (`http` and `https` both stay supported).
- The `source_test_malformed_url` wording is untouched.

Additive: no public type or signature changes; the only observable behaviour change is that a non-HTTP scheme
now reports as unusable instead of as a transport failure. **Rollback**: revert the scheme check in
`openConnection`; the tree then behaves as before.

## Capabilities

### New Capabilities

None. The behaviour belongs to an existing capability.

### Modified Capabilities

- `map-download-infrastructure`: adds "A base URL with a non-HTTP scheme is reported as an unusable URL" (one
  requirement, one scenario) and modifies "A base URL that cannot be parsed is reported as an unusable URL"
  only in its "A parseable URL is never reported as unusable" scenario, which is narrowed to an `http`/`https`
  scheme because the added requirement now owns the non-HTTP case. The requirement name and scenario names are
  preserved (`openspec validate` refuses a MODIFIED block that drops or renames a scenario).

## Impact

- `app/src/main/java/com/naviveylin/data/HttpUrlFetcher.kt` — scheme check in `openConnection` plus a private
  `isHttpScheme` predicate; class KDoc widened.
- `app/src/test/java/com/naviveylin/data/HttpUrlFetcherTest.kt` — the red-on-HEAD case
  `aNonHttpSchemeIsReportedAsUnusable`; its spec-reference KDoc widened.
- `core/src/main/java/com/naviveylin/core/mapsource/RepositoryFailure.kt` — `MalformedUrl` KDoc widened to the
  HTTP-URL meaning; no enum/value change, so no `when` site is affected.
- Specs: the delta lands in this change; the durable spec is synced at archive
  (`openspec/specs/map-download-infrastructure/spec.md`).
- Guidelines: `guidelines/Design.md` §9 already owns the `HttpURLConnection` rule; the change adds no rule, so
  no guideline edit. `guidelines/Logging.md` §2 is unchanged (the rejected-scheme line stays coordinate-free
  and names the URL, as the existing failure line already does).

**Verification** — the three scenarios are exercised by `HttpUrlFetcherTest` unit cases; one revert-check
mutates the scheme guard; the forced both-flavor gate runs once. Details in `design.md`, steps in `tasks.md`.
