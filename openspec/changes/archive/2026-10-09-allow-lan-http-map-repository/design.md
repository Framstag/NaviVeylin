# Design — allow-lan-http-map-repository

## Context

See `proposal.md` — Why. Three constraints shape everything below.

- **Android gives no per-host cleartext switch.** `network_security_config.xml` is static at build time and its
  `<domain-config>` entries match host names and IP literals — there is no CIDR form and no runtime host list. A
  user-supplied base URL therefore cannot be allow-listed individually; the choice is blanket-or-nothing.
- **`android:usesCleartextTraffic` and `android:networkSecurityConfig` do not compose.** Once the config resource
  is referenced, the manifest attribute is ignored on API 24+, so the existing debug allowance at
  `app/src/debug/AndroidManifest.xml:14` becomes dead the moment the new file is wired in.
- **The two seams that make this testable already exist.** `HttpUrlFetcher` has an `internal var ioDispatcher`
  seam, and the `buildSrc` scanners (`HardcodedStringGate`, `CoordinateLogScanner`) are pure text functions with
  their own tests — precedent for a policy gate that needs no device.

## Goals / Non-Goals

**Goals**

- A Play-installed build reaches a plain-HTTP repository on a local network, in both flavours, with the same
  observable behaviour as the debug build has today.
- The permission is visible in the repository, falsifiable by a build gate, and not contradicted by a stale
  comment or a dead attribute.
- A denial — if the policy is ever tightened again — names the reason in the user's terms.
- An `http` source is visibly unencrypted without adding a step to any flow.
- A base URL survives being typed on a phone: the field asks for URL input, shows the expected shape, and a value
  that still arrives padded is repaired rather than rejected.

**Non-Goals**

- Any integrity guarantee for cleartext downloads. The size and CRC-32 the download verifies against
  (`DatabaseMetadata`, `FileVerifier`) arrive on the same connection, so a man-in-the-middle substitutes data and
  checksum together. Recorded as a `TODO.md` entry, not solved here.
- HTTPS for the repository, client-side CA trust for self-signed certificates, and certificate pinning. Those are
  a different change with a different user story (a user who *has* TLS on the host).
- Narrowing the allowance to a host class. Not expressible by the platform (see Context).
- The repository update check, and anything about the karry.cz path.

## Decisions

### D1 — the permission lives in a network security config, not the manifest attribute

`app/src/main/res/xml/network_security_config.xml` (new) holds
`<base-config cleartextTrafficPermitted="true">` with `<trust-anchors><certificates src="system"/></trust-anchors>`,
and `app/src/main/AndroidManifest.xml` points at it with `android:networkSecurityConfig`.

*Alternatives.* (a) `android:usesCleartextTraffic="true"` in the main manifest — one line, but blunter: it is the
attribute the debug overlay already uses, it says nothing about trust anchors, and it silently stops applying if
any future config file is added. (b) a `<domain-config>` list — only reaches hosts known at build time, so it
cannot serve a user's own URL. (c) a raw-socket HTTP client that bypasses `NetworkSecurityPolicy` for a
user-approved host — the only way to be narrow, at the price of writing an HTTP client and deliberately stepping
around a platform protection. (d) HTTPS plus user-CA trust or pinning — keeps cleartext off, but does not serve a
host that has no TLS at all, which is the case the proposal exists for.

*Why the config file.* It is the mechanism that actually governs policy, it states the trust anchors it relies on,
it leaves room for a later `domain-config` exception or `<debug-overrides>` without a second migration, and — the
decisive part — the gate can read it as text. Its consequence is the blanket allowance, which is the only
expression the platform offers.

*What it removes.* `android:usesCleartextTraffic` from `app/src/debug/AndroidManifest.xml` goes, together with the
comment above it claiming "release ... expects HTTPS". Leaving either would leave the repository asserting
something untrue.

### D2 — one pure predicate decides "unencrypted", and both UI sites read it

A host-testable helper in `:core`, next to `RepositoryUrlPlanner`, answers whether a base URL is cleartext
(`http` scheme, case-insensitive, after the same trimming `normaliseBaseUrl` does). The ViewModel exposes it on
the state it already builds (`MapManagerUiState`: the draft and the active source), and the composable renders the
notice from that flag.

*Alternatives.* Parsing in the composable — untestable without Compose, and it duplicates the rule across the
draft field and the source row. Persisting a boolean in `SettingsStorage` — becomes false the moment the URL is
edited, and adds a settings field for a value derived from another field.

*Consequence.* Every scenario of the notice requirement is asserted in a JVM test (the predicate) plus one Compose
assertion (the notice is present for `http` and absent for `https`), with no network and no device.

### D3 — the fetcher probes the policy instead of parsing the platform's message

`HttpUrlFetcher` gains a second `internal var` seam beside `ioDispatcher`:
`cleartextPermitted: (String) -> Boolean`, defaulting to
`NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted(host)`. Before connecting, a request whose host is
present and not permitted returns `RepositoryFailure.CleartextBlocked` (new case in `:core`) without attempting a
connection. A URL whose host cannot be extracted skips the probe and falls through to the normal path.

*Alternatives.* Matching the thrown exception's text ("Cleartext HTTP traffic to … not permitted") — brittle
across platform versions and locale, and it makes the classification a side effect of a string. Trusting the
generic `TransportFailed` — leaves the user with Android's sentence, which is what the change exists to replace.

*Why a seam.* The default reads `android.security.NetworkSecurityPolicy`, so a JVM test can only drive it through
an injected lambda; the pattern is the one `ioDispatcher` already uses in this class. `MapManagerScreen.describe()`
gains one branch and a new string `source_test_cleartext_blocked`, phrased as an instruction
("… is unencrypted; this build permits it only for a source you chose" style copy to be finalised in the task).

*Kept as a fallback.* If the probe says permitted but Android still refuses, the existing `TransportFailed` path
still reports the request — the classification is an improvement, not a replacement.

### D4 — the gate is a `buildSrc` text scanner, wired beside the existing ones

`buildSrc/src/main/kotlin/com/naviveylin/build/transport/` gains a scanner and a Gradle task asserting two
things: the shipping config permits cleartext, and the main manifest references that config. Its pure core is unit
tested in `buildSrc` like `CoordinateLogScanner`.

*Alternatives.* Asserting the *merged* release manifest
(`app/build/intermediates/merged_manifests/mobileRelease/.../AndroidManifest.xml`) — proves the output and not just
the sources, but requires an assemble before it can run, and the merged path is an AGP internal layout that this
project already reads only for inspection. Chosen deliberately: scanner for the fast, always-on gate; the merged
artifact is verified once by hand in the change's device/evidence task.

*Falsification.* The gate owes one revert-check: mutate `cleartextTrafficPermitted="false"`, the named scanner case
must fail, restore, then re-run the affected suite green forced (`-PforceTests --no-build-cache`) per
`guidelines/Build.md` §2/§4.

### D5 — URL entry is treated as data entry, and a spoiled URL is repaired rather than rejected

Three things land together, because each is only a partial defence on its own:

1. `RepositoryUrlPlanner.normaliseBaseUrl` (`core/.../RepositoryUrlPlanner.kt:43`) removes *all* whitespace before
trimming trailing slashes, not just at the ends. It is the single place every request URL and the persisted
`MapSource` identity (`MapSource.kt:40`) pass through, so repairing there fixes both the request and the identity in
one move.
2. The field declares URL input (`KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false,
imeAction = ImeAction.Done)`) next to the existing `singleLine = true`, so the IME stops inserting a space after a
period and stops auto-capitalising — the cause of `10.0. 2. 2:30123` on a hand-typed IP address.
3. The field shows the accepted format beside it (`placeholder` or `supportingText` with `http://host:port`), since
the label "Repository URL" (`strings.xml:199`) states no shape at all.

*Alternatives.* Declaring URL input alone — fixes typing but not a paste, and not a third-party IME that ignores
the hint. Stripping whitespace alone — repairs the value silently while the user still sees a field that fights
them. Validating and refusing a padded URL — punishes the user for the keyboard's behaviour.

**Why the IME cannot be the whole answer.** `KeyboardType.Uri` is a request to the input method, not a guarantee:
`MapManagerViewModel.updateRepositoryUrlDraft` (`MapManagerViewModel.kt:563-570`) writes whatever arrives to
`SettingsStorage` on every keystroke, so normalisation is the boundary that holds. The two are complementary — the
keyboard prevents most of it, normalisation makes the rest harmless.

### D6 — an unparseable URL gets its own failure case

`RepositoryFailure.MalformedUrl` (new, beside `CleartextBlocked`) is returned when `URI.create` rejects the URL, and
`MapManagerScreen.describe()` renders it as the URL plus the expected form. The check happens in `openConnection`, at
the point that already wraps the parse in a `try`.

*Alternatives.* Leave it as `TransportFailed` — today's behaviour, which tells the user
`Illegal character in authority at index 13`, a sentence about our parser rather than about their URL. Validate the
scheme against an allow-list — a different behaviour (refusing a source) that the specs do not ask for and that
would need its own justification; D5's normalisation plus this classification covers the observed problem without
narrowing what a user may point the app at.

### D7 — the lint warning is suppressed with its reason, in the file that suppresses lint today

`app/lint.xml` gains `<issue id="InsecureBaseConfiguration" severity="ignore"/>` with a comment naming this change
and the reason, next to the existing `HardcodedText` entry.

*Alternatives.* Leave the warning — `app/build.gradle.kts:334` sets `abortOnError = true` but not
`warningsAsErrors`, so the build would pass and the apply rule "the build has no warnings" would be quietly
broken. Turn off lint for release — loses the i18n gate this file exists for. Both rejected: the allowance is
deliberate, and a suppression with a stated reason is honest about it.

## Risks / Trade-offs

- **A Play pre-launch security scan notes cleartext** → the allowance covers only the user-typed repository URL;
  no credentials, no personal data and no telemetry traverse it. Recorded in `guidelines/Regulatory.md` §6 so the
  decision is not re-litigated on the next scan.
- **Map data can be substituted in transit, checksum included** → not mitigated here; the user chose the source
  and the notice says it is unencrypted. `TODO.md` entry; a signed repository or a strong hash in `db.json` is the
  fix, and both are upstream-format questions.
- **The gate proves sources, not the merged artifact** → the change's device/evidence task inspects the merged
  release manifest once, and the scanner also asserts the manifest reference that makes the config apply.
- **Lint issue ids can be renamed across AGP versions** → a stale suppression is invisible rather than harmful;
  the comment in `lint.xml` names the intent so a rename is greppable.
- **Removing the debug attribute could surprise a developer who reads it as the mechanism** → the gate makes the
  shipping config the single answer, and the debug manifest's comment is rewritten to point at it.
- **Blanket allowance reaches any host, including a mistyped one** → the notice is the only user-visible
  consequence, which is the accepted trade of D1.
- **An input method may ignore the URL-input hint** → normalisation (D5) is the boundary that holds regardless;
  the keyboard type reduces how often it matters.
- **Silent whitespace removal could mask a genuinely wrong URL** (a user who meant to type a path with a space) →
  a raw space is never valid in a URL — it is `%20` — so removal cannot turn a usable URL into a wrong one, and a
  URL that is wrong for another reason is still reported by the test.

## Migration Plan

Nothing stored changes: no settings field, no file format, no directory layout, so there is nothing to migrate and
no data loss path. Deployment is the next release build of both tracks. Rollback is reverting the change's
commits — the platform default returns, an `http` source stops being testable in a release build again, and no
installed map becomes unusable (maps already downloaded from an http source stay registered and rendered; only a
re-download would fail).

## Verification

- **Unit (JVM)** — the scheme predicate and the whitespace-normalised base URL (`:core`), the fetcher's denial and
  unusable-URL classifications through both seams (permitted / denied / unparseable / unextractable host), the
  scanner's pure function (`buildSrc`), and Compose assertions that the notice appears for `http` and not for
  `https` and that the URL field declares URL input beside its format hint.
- **Revert-check** — the gate's named mutation (D4).
- **On-device (measurement)** — `installMobileRelease` needs `app/release.keystore` (gitignored, machine-local); on
  a machine without it, the evidence is the merged `mobileRelease` manifest plus the automated checks, and that
  limitation is stated rather than implied. With a signed release build and a reachable host: the source test
  reports success with the region and leaf counts, and `adb logcat -s NaviVeylin` carries no
  `repository request failed` line for that URL. The notice is measured through a UI dump's text, not by eye.
- **Not measurable on a stationary emulator** — nothing here needs motion; the only device-gated part is whether a
  *release-signed* build reaches a real LAN host, which a machine without the release keystore cannot show.

## Open Questions

- The exact wording of the notice, of the URL field's format example, and of the cleartext-denial and
  unusable-URL strings, and whether they are translated in the same change (the `values-de` entries) — a copy
  decision, not a behaviour one; the tasks own it.
