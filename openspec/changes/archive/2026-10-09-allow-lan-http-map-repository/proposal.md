# Proposal — allow-lan-http-map-repository

## Why

A Play-installed build cannot use a self-hosted repository served over plain HTTP:
`app/src/debug/AndroidManifest.xml:14` sets `android:usesCleartextTraffic="true"` for the debug build only, so on a
release build the platform default blocks the request and the source test fails with the raw platform text
`http://<host>/names.json could not be reached (Cleartext HTTP traffic to <host> not permitted)`.

That is not only a testing inconvenience. Upstream's reference server for this protocol is plain-HTTP **by its own
spec** — the submodule's `openspec/specs/web-server/spec.md` serves the repository "over plain HTTP without any
authentication", and `Documentation/MapRepository.md:686` documents `curl http://localhost:8080/names.json`. The
canonical self-hosted map source is therefore unreachable from the shipped app, which contradicts the shipped
feature: the map source selector exists to let a user point the app at their own repository
(`add-mapgen-map-source`, archived 2026-10-09 — the change whose debug-only allowance this one supersedes).

## What Changes

- **Shipped builds permit cleartext repository transport.** `app/src/main/res/xml/network_security_config.xml`
  (new) declares `<base-config cleartextTrafficPermitted="true">` with the system trust anchors, and
  `app/src/main/AndroidManifest.xml` references it through `android:networkSecurityConfig`. Both flavours
  (mobile, automotive) inherit `src/main/res`, so the two distribution tracks behave the same.
- **The debug allowance becomes dead config and is removed.** Once `android:networkSecurityConfig` is set,
  `android:usesCleartextTraffic` is ignored, so `app/src/debug/AndroidManifest.xml` loses the attribute and the
  header comment that says "release ... expects HTTPS" — a comment this change makes false.
- **An unencrypted source is marked inline.** When the repository source's base URL is an `http://` URL, the map
  manager shows a non-interactive notice next to it ("unencrypted — trusted local hosts only"). No dialog, no
  gate, no behaviour change beyond the label. This is honesty, not defence: the downloaded data's integrity
  check is a declared size and CRC-32 that travel the same connection, so cleartext buys reachability only.
- **A denied cleartext request reports an actionable reason** instead of the platform's own sentence: the fetcher
  asks `NetworkSecurityPolicy.isCleartextTrafficPermitted(host)` before connecting and classifies a denial as its
  own `RepositoryFailure`, so a future regression of the policy says what to do rather than repeating Android.
- **A base URL is typed as a URL, and normalised.** The field is presented as URL input with the accepted format
  shown beside it, because today it is plain text input with no hint at all (`MapManagerScreen.kt:466`): the
  platform's text input inserts a space after a period, so typing an IP address by hand can produce
  `10.0. 2. 2:30123`. Whitespace is removed from a base URL before it is used, validated or persisted, since
  `normaliseBaseUrl` (`RepositoryUrlPlanner.kt:43`) trims only the ends today — a pasted URL, or any input method
  that pads, otherwise reaches settings and the source identity as typed.
- **An unparseable base URL is reported as an unusable URL**, not as a connection failure. Today the parse throws
  and surfaces as `… could not be reached (Illegal character in authority at index …)`; the change gives it its
  own `RepositoryFailure` case with the URL and the expected form.
- **A build gate asserts the shipping policy.** The permission is an invariant a later manifest or resource edit
  can silently remove, so a scanner (precedent: `HardcodedStringGate`, `CoordinateLogScanner` in `buildSrc`) plus
  its unit test fail the build when the shipping configuration stops permitting cleartext.
- **The lint consequence is recorded, not silent.** Lint reports `InsecureBaseConfiguration` for a base-config
  that permits cleartext, and `app/build.gradle.kts:334` runs lint with `abortOnError = true` and
  `checkReleaseBuilds = true`. The issue is suppressed in `app/lint.xml` with the reason stated in the file,
  because the allowance is deliberate.
- **Not in this change**: any integrity protection for cleartext downloads (repository signing, a strong hash
  instead of CRC-32, or an out-of-band checksum), HTTPS-only transport with user-CA trust or certificate
  pinning, and the repository update check. The first two become a `TODO.md` entry; the rest stay open.

## Capabilities

### New Capabilities

None — the transport policy, the failure taxonomy and the URL entry rule are properties of the existing map
download path and the existing source selector, so all belong to capabilities this project already has.

### Modified Capabilities

- `map-download-infrastructure`: a new requirement — the shipped build permits cleartext HTTP for repository
  transport, `https://` is unaffected, and a request the policy denies is reported as a denial rather than as a
  generic transport failure, as is a base URL that cannot be parsed at all. The capability currently owns the network seam (`HttpURLConnection for HTTP`) and
  is the only place that can own the app-wide policy and the failure taxonomy.
- `map-source-selection`: new requirements — a repository base URL whose scheme is `http` is presented as
  unencrypted; a base URL has its whitespace removed before it is used or persisted; and the URL field states the
  expected format and is presented as URL input. The capability already owns "Base URL of the repository source is
  validated before use", which is the requirement that reports the test's outcome and that the three additions'
  copy and field belong beside.

## Impact

**Modules and files**

- `app/src/main/res/xml/network_security_config.xml` (new), `app/src/main/AndroidManifest.xml`
  (`android:networkSecurityConfig`), `app/src/debug/AndroidManifest.xml` (attribute and comment removed),
  `app/lint.xml` (`InsecureBaseConfiguration` suppression with its reason).
- `app/src/main/java/com/naviveylin/data/HttpUrlFetcher.kt` (policy probe, denial and unusable-URL
  classification), `core/src/main/java/com/naviveylin/core/mapsource/RepositoryFailure.kt` (two new failure cases),
  `core/src/main/java/com/naviveylin/core/mapsource/RepositoryUrlPlanner.kt` (whitespace-normalised base URL),
  `app/src/main/java/com/naviveylin/ui/mapmanager/MapManagerScreen.kt` (`describe()` branches, the notice, the URL
  field's keyboard options and its format hint), `app/src/main/res/values/strings.xml` and
  `values-de/strings.xml` (notice, format hint, actionable denial and unusable-URL text).
- `buildSrc/src/main/kotlin/com/naviveylin/build/transport/` (new scanner) plus its test; the gate is wired into
  the app build's check lifecycle next to the existing scanner gates.
- Tests: app unit test for the fetcher's classification seams, a `:core` test for the URL normalisation and the
  cleartext predicate, Compose assertions for the notice and the URL field in
  `MapManagerSourceSelectorComposeTest.kt`, a `buildSrc` test for the scanner, and the gate's revert-check.

**Specs** — the two modified capabilities above. No spec is deleted or renamed.

**Guidelines** — `guidelines/Regulatory.md` §6 gains one line stating that cleartext repository transport is a
deliberate decision that leaves the data-safety declaration unchanged; `guidelines/Design.md` §2 is the recipe a
new resource file and manifest attribute follow; `guidelines/UI.md` §6a (phone search surface) is *not* affected —
the source selector is a settings surface, not the search surface. `guidelines/Build.md` §2/§4 own the gate's
result evaluation and the revert-check discipline this change owes.

**Scope** — a general app property, not an Android Auto one: the network policy is declared once in `src/main/res`
and both flavours inherit it, while the notice lives in the phone-only map manager screen, so there is no car
counterpart to keep in parity.

**Change class and rollback** — *additive*: release builds gain reachability, nothing that worked before stops
working, and no stored data or format changes. Rollback is reverting the change's commits; removing
`networkSecurityConfig` restores the previous, stricter platform default, and the notice and gate go with it. The
only data consequence of a rollback is that an http source stops being testable in a release build again.

**Native/JNI** — none. No submodule patch, no bridge-module override, no `:osmscout-client-java` change: the
download path already reaches the network through `HttpURLConnection` and this change alters only the manifest
policy and the failure classification around it.
