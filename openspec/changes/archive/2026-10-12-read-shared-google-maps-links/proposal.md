# Proposal

## Why

A Google Maps link shared into NaviVeylin does not become a destination. `DeepLinkParser.parse` tries the URI, then
`EXTRA_TEXT` and `EXTRA_QUERY` — and `EXTRA_TEXT` is handed to `fromFreeText` without ever being tried as a URL
(`core/src/main/java/com/naviveylin/core/DeepLinkParser.kt:52-61`). A share payload that is only a link therefore
reaches `SharedLocationRequest(query = …)` and `MapCanvasViewModel` opens the search panel with the raw URL in the
text field (`MapCanvasViewModel.kt:3026-3034`). Measured on the connected emulator, `DeepLinkActivity` is a
resolving share target for a plain `ACTION_SEND`/`text/plain` intent, so this is the path a Google Maps share takes.

Even where the link is read, only the legacy parameter shapes are understood: `parseMapsUrl` looks at `ll`, `q`,
`daddr` and `destination` (`DeepLinkParser.kt:175-205`), while Google Maps today shares short links
(`maps.app.goo.gl/…`) that resolve to `/maps/place/<Name>/@<lat>,<lon>,<zoom>z/data=!4m…!3d<lat>!4d<lon>…`. The
coordinate in that shape is never found, and an unresolvable short link degrades to the raw link as query
(`DeepLinkParser.kt:98-108`). Two smaller gaps sit on the same path: `DeepLinkActivity` forwards action, data and
two text extras but not `EXTRA_SUBJECT` (`DeepLinkActivity.kt:16-21`), which is where a share carries the place
name; and the declared `VIEW` hosts (`AndroidManifest.xml:87-91`) miss `google.com`, the country hosts and
`goo.gl/maps`, so "open with" is never offered for those links.

The user-visible goal: a shared Google Maps link is read, its coordinate is the primary value, and a link that
carries no coordinate never ends up in the search field. The car reads the same links: a Google Maps share link that
reaches the car session resolves to the destination it names and starts navigation.

## What Changes

- `DeepLinkParser` reads the current Google Maps URL shapes — resolved `/maps/place/…` links with `!3d`/`!4d` in
  the `data` parameter, `/@<lat>,<lon>,<zoom>z`, `/maps/search/?query=…`, `/maps/dir/?destination=…`,
  `goo.gl/maps/…` and `google.<tld>/maps/…` hosts, ignoring tracking parameters (`g_st`, `entry`, `g_ep`) — and
  extracts the place name from `/place/<Name>/` as a label hint when no `EXTRA_SUBJECT` is present.
- **Behavior change**: a recognized map link that yields neither a coordinate nor a name is no longer returned as a
  free-text query. The parse reports "not usable" and the caller surfaces a non-blocking hint instead of running a
  search for a URL. This rule lives in the shared parser, so the car path changes with it.
- Short-link resolution moves from the phone wrapper to a shared `:core` seam, and the car session uses it: a
  `maps.app.goo.gl` link is resolved off the host thread and parsed into the destination it points at, so the car
  navigates to a Google Maps share link instead of geocoding its URL. The phone keeps resolving on `Dispatchers.IO`
  as today.
- A shared URL embedded in surrounding text (`"<Name>\nhttps://…"`) is split into text and link before parsing
  instead of being passed on as one query string.
- `DeepLinkActivity` forwards `EXTRA_SUBJECT`, so the shared place name survives the phone → activity hop and can
  serve as the label hint.
- The phone coordinate flow ranks the candidate list by the link's place name (matching candidates first, existing
  ranking preserved for the rest) and opens the details sheet directly when exactly one candidate is found. No
  candidate still opens the details sheet on the raw coordinate; more than one still shows the picker.
- The manifest `VIEW` deep-link hosts are widened to `google.com`, the `google.<tld>` country hosts and
  `goo.gl/maps`, keeping the existing `androidx.car.app.category.NAVIGATION` category (measured: an extra category
  in a filter does not stop a plain share from matching).
- New user-facing hint string in `values/strings.xml` and `values-de/strings.xml`.
- Additive, no `**BREAKING**` API change: no public Kotlin/Java signature is removed, no native/JNI code and no
  submodule patch is involved. Rollback: revert the parser, the shared resolver extraction and the car resolver call,
  the activity forwarding, the ranking in `MapCanvasViewModel`, the string resources and the manifest hosts; the
  previous "URL as query" behavior returns. No data migration.

This is **not** a `fix-*` fix-loop change: it is not one root cause with one resulting fix. The URL coverage gap,
the dropped subject extra, the manifest host list and the query-fallback policy are four independently fixable
defects plus the ranking decision, so the entry is not loop-eligible and is planned as a normal change.

## Capabilities

### New Capabilities

None. The behavior belongs to the existing phone share flow and the existing car deep-link flow.

### Modified Capabilities

- `share/location-receiving`: the "Shared address triggers search" requirement changes — its "Unparseable shared
  text" scenario no longer covers a recognized map URL, and a new requirement states that a shared map link is
  read for a coordinate first, a place name second, and that an unreadable link produces a hint instead of a search.
  New requirement for the candidate list: name-ranked candidates and "exactly one candidate opens the details
  sheet".
- `auto-deep-links`: the "Deep link parsed into a destination" requirement changes — its "Short link does not
  resolve" scenario no longer yields the raw link text as query (the session surfaces an error instead), and its
  "Short link resolves to a map target" scenario becomes reachable on the car, which now resolves the short link off
  the host thread.

## Impact

**Code**

- `core/src/main/java/com/naviveylin/core/DeepLinkParser.kt` — URL shapes, place-name extraction, "URL is never a
  query", text-plus-link splitting; `core/src/test/java/com/naviveylin/core/DeepLinkParserTest.kt`.
- `core/src/main/java/com/naviveylin/core/ShortLinkResolver.kt` (new) — the redirect-following resolver extracted from
  the phone wrapper as a shared seam, with a JVM-testable contract and a fake for tests.
- `app/src/main/java/com/naviveylin/share/SharedLocationParser.kt` — label hint from `EXTRA_SUBJECT` or the link,
  carry "not usable" through; `app/src/test/java/com/naviveylin/share/SharedLocationParserTest.kt`.
- `app/src/main/java/com/naviveylin/DeepLinkActivity.kt` — forward `EXTRA_SUBJECT`;
  `app/src/test/java/com/naviveylin/DeepLinkPrivacyTest.kt`.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — `processSharedLocation` / `showCandidatesFor`:
  name ranking, single-candidate details, hint via the existing `showSnackbar`;
  `app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelSharedLocationTest.kt`.
- `auto/src/main/java/com/naviveylin/auto/NavigationSession.kt` — `handleDeepLink` resolves a short link through the
  shared seam on a background dispatcher and reports "link not readable" through `reportError` instead of geocoding a
  URL; `auto/src/test/java/com/naviveylin/auto/`.
- `app/src/main/AndroidManifest.xml` — `VIEW` host list of `DeepLinkActivity`.
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-de/strings.xml` — the hint string.

**Specs**

- `openspec/specs/share/location-receiving/spec.md` — modified (delta in this change).
- `openspec/specs/auto-deep-links/spec.md` — modified (delta in this change).

**Guidelines / docs**

- `guidelines/Design.md` §9 "Data, persistence & search" — the share/deep-link → search-and-details routing.
- `guidelines/UI.md` §6a "Phone search surface" and §10 (snackbar band) — the hint and the search panel must no
  longer be filled from a link.
- `guidelines/Logging.md` — the new parse outcomes stay coordinate-free, following the existing
  `sharedLocationParsedMessage` / `deepLinkLogMessage` seam.

**Dependencies / platform**

- No new dependency. Short-link resolution keeps using the already-present `HttpURLConnection` redirect follow, now
  owned by `:core` so the phone wrapper and the car session share one implementation (no Google Play Services, no
  Maps SDK).
- Android Auto: the resolver call happens off the host thread and outside the guarded host-mutation seam
  (`guidelines/Design.md` §8), and the existing `DeepLinkActivity` filter keeps its category, so the car host still
  discovers the app as a navigation target.
