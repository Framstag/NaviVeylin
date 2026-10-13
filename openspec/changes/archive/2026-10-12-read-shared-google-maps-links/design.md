# Design

## Context

See `proposal.md` — Why for the motivation and the measured evidence. This design only fixes how the work is done.

Current state that shapes the approach:

- `DeepLinkParser` (`core`) is pure Kotlin over `android.content.Intent`/`Uri`, shared by the phone wrapper
  `share/SharedLocationParser.kt` (which adds the network short-link resolver and the `EXTRA_SUBJECT` label) and by
  the car session `auto/NavigationSession.kt:615`. Any rule placed there changes both surfaces.
- The phone dispatch is `SharedLocationHandler` (`@Singleton` StateFlow, consume-once) → `MapCanvasViewModel.processSharedLocation`
  (`MapCanvasViewModel.kt:3004`) → `showCandidatesFor` (`MapCanvasViewModel.kt:2947`), which is also the long-press path.
- The map screen already owns a snackbar band (`MapCanvasScreen.kt:2574`, `showSnackbar`/`clearSnackbar` on the
  ViewModel, spec `map-canvas-screen`) and `values/` + `values-de/` string resources exist (`i18n-l10n`).
- The car has no candidate/details template: a coordinate starts navigation directly (`auto-deep-links`,
  "Deep link starts navigation").
- Constraints from `guidelines/Design.md` §3/§4 (state and threading), §9 (search/data), `guidelines/UI.md` §6a and
  §10 (phone search surface, snackbar band), `guidelines/Logging.md` (no coordinate in any log line).

## Goals / Non-Goals

**Goals:**

- One link-reading rule, in one place, with the phone and the car both obtaining their outcome from it.
- A recognized map link resolves to: coordinate → place name → unusable. Never a search query.
- The phone keeps its existing dispatch shape (handler → ViewModel → candidate/search flow); only the decision
  inputs change.
- The car reaches the same destination: a `maps.app.goo.gl` share link is resolved off the host thread and navigates,
  instead of the session geocoding a URL that cannot match.

**Non-Goals:**

- Car-side candidate ranking or a car details surface: the car starts navigation, by platform design.
- A car-side *hint* surface beyond the existing `reportError` notice: the car templates have no snackbar slot.
- Outgoing share, file share (`EXTRA_STREAM`, GPX/KML), Google Play Services or a network map lookup for a name.
- Any change to how the *car host* discovers the app (the `DeepLinkActivity` filter keeps its category) or to the
  phone's short-link timing (5 s timeout stays).

## Decisions

### D1: The "a URL is never a query" rule lives in the shared `core` parser

`DeepLinkParser` is the single place that decides what a shared payload means; it stops producing a query whose
text is an `http(s)` URL.

- **Why**: one truth for both surfaces. A URL is never a location name, and geocoding one can only fail — the car's
  current `"No matching location found for \"https://…\""` path proves it.
- **Alternatives**: (a) filter in the phone wrapper only — leaves the car geocoding URLs and creates two rules for
  the same payload; (b) a `isPhone`/`allowRawUrlQuery` parameter — a flag that every caller must remember, and the
  car would keep the wrong behavior by default; (c) keep the behavior and only fix URL *coverage* — a link that
  still cannot be read keeps landing in the search field, which is the reported defect.
- **Risk**: touches the working car flow. Mitigation: `DeepLinkLogMessageTest` and the `:auto` suite re-run; the
  spec delta for `auto-deep-links` states the new car outcome.

### D2: A three-valued parse outcome instead of `DeepLinkDestination?`

The parser returns a sealed outcome — `Destination(lat, lon, query)`, `UnusableLink` (a recognized map link that
yields neither coordinate nor name), `None` (nothing recognized) — so each caller can act differently without
re-inspecting strings.

- **Why**: the hint (D6) and the car error are only correct if "recognized link, nothing usable" is distinguishable
  from "plain text, not a link". Reconstructing that distinction in two callers duplicates the host knowledge the
  parser already has.
- **Alternatives**: (a) keep `DeepLinkDestination?` and let each caller test the text against a URL pattern —
  duplicated host logic, and a plain `https://example.com` share would be misread as a map link; (b) add an
  `unusable: Boolean` field to `DeepLinkDestination` — an invalid-state-carrying data class (coordinates plus
  "unusable"); (c) throw — a shared payload is ordinary input, not an error condition.
- **Alternatives rejected by the spec**: returning the URL as the query text is what `share/location-receiving` and
  `auto-deep-links` no longer permit.
- **Migration**: `NavigationSession`, `SharedLocationParser` and the parser tests switch from `null`-checks to
  `when` over the outcome. `DeepLinkDestination` stays the `Destination` payload, so its coordinate-free logging
  seam (`deepLinkLogMessage`, `sharedLocationParsedMessage`) is unchanged.

### D3: One ordered coordinate ladder, extended with the current Google share shapes

`parseMapsUrl` grows into an ordered extraction — `data=!3d/!4d` place pin → `@<lat>,<lon>,<zoom>z` viewport →
`ll`/`q`/`daddr`/`destination` parameters → `/place/<lat>,<lon>` path — plus a widened host set (`google.com`,
`google.<country>`, `goo.gl/maps`, existing `goo.gl`, `maps.apple.com`, Waze, HERE, OSM) with tracking parameters
(`g_st`, `entry`, `g_ep`) ignored.

- **Why**: the ladder is the priority order the user asked for (place pin beats viewport center); one function with
  a fixed order is testable per shape. Google Maps shares today are `maps.app.goo.gl` links resolving to
  `/maps/place/<Name>/@…!3d…!4d…`, which no current branch reads.
- **Alternatives**: (a) one parser object per vendor (`GoogleMapsUrlParser`, `WazeUrlParser`, …) — more structure
  than four branches justify, and the ordering between vendor branches still has to be defined; (b) one regex sweep
  over the whole URL string looking for any coordinate pair — would pick the *last* coordinate in the URL (a
  tracking or `data` parameter) with no priority rule; (c) fetch the page and read its `<meta>`/HTML — network,
  latency and parsing weight for data the URL already carries.
- **Risk**: Google changes URL shapes again. Mitigation: one test per shape, and the unusable path degrades to the
  hint instead of a search.

### D4: Label = `EXTRA_SUBJECT`, else the link's place name; the name ranks candidates, never filters

`SharedLocationParser` keeps `EXTRA_SUBJECT` as the label and falls back to the place name read from the link
(`/place/<Name>/`, percent- and `+`-decoded). `showCandidatesFor` gets the name as an extra input and stable-partitions
the candidate list: labels containing the name first, database ranking preserved otherwise. The name hint is not used
when it equals the coordinate pair.

- **Why**: the sharer's own label is the most trustworthy hint; the link name covers a share without a subject. A
  partition cannot lose a valid object, while a filter can (the database label may be localized or abbreviated, e.g.
  "Eiffelturm" for a link named "Eiffel Tower").
- **Alternatives**: (a) filter the list to matching candidates — an empty list for a renamed place, and the share
  then looks broken; (b) score by edit distance and sort by score — hides the database's own quality ranking behind
  a fuzzy metric that is hard to explain in a spec; (c) drop the hint and rely on the database ranking — this is the
  current behavior and the reason a coordinate-only link is hard to resolve.
- **Threading**: normalization and partitioning are pure in-memory work over an already-loaded list, on the same
  `defaultDispatcher` context that produced the candidates (`MapCanvasViewModel` candidate lookup), so no extra hop.
- **Risk**: substring matching can rank an unrelated candidate first when the name is a single short word.
  Mitigation: the picker still shows the full ranked list; the ranking is documented as a ranking, not a selection.
- **Guideline conflict to resolve in the same change**: `guidelines/Design.md` §9 requires that "name matching is the
  map library's job … never re-implements matching per surface". Ordering an already-matched candidate list by a
  share-provided name hint is not matching — nothing is filtered, dropped or rewritten, and a name the library does
  not match keeps its position. §9 gains one sentence saying so, so the next reader does not have to re-derive the
  distinction from this design.

### D5: A single candidate opens the details sheet directly

`showCandidatesFor` gains `openDetailsOnSingleCandidate` (set by the share flow, not by long-press): one candidate →
details sheet for that object, more than one → picker as today, none → details on the raw coordinate as today.

- **Why**: the user's requirement is "details when it is unambiguous"; a one-row picker asks a question with one
  answer. Long-press keeps its current behavior because there the user picked a point deliberately and may want the
  candidate list as confirmation.
- **Alternatives**: (a) always show the picker when candidates exist — current behavior, and a needless second tap
  for the common single-object case; (b) treat "unambiguous" as a distance bound (candidate object center within
  ~15 m of the shared coordinate) — requires a distance threshold and object-center precision per object type,
  which the database does not guarantee; (c) always open details for the top-ranked candidate — silently decides
  what the user meant when several objects exist.
- **Risk**: the single candidate is the wrong object. Mitigation: the details sheet shows the object and its
  position, and the candidate picker remains one tap away by long-pressing the same point.

### D6: The unusable link produces the existing snackbar, with a new localized string

The phone ViewModel calls the existing `showSnackbar` with a new resource (`shared_link_unreadable`, added to
`values/strings.xml` and `values-de/strings.xml`); the car uses `reportError` (`SurfaceOrigin.CAR`).

- **Why**: the band already exists, is composed in the correct layer (spec `map-canvas-screen`) and is testable
  through `snackbarMessages`; it is non-blocking, which matches "no search run, subtle hint".
- **Alternatives**: (a) `Toast` — outside the Compose layer and outside the existing test seam; (b) a dialog —
  interrupts for a payload the user can ignore, and needs its own dismiss handling; (c) log only — the user gets no
  feedback at all when a share silently does nothing, which is the behavior the reported defect already has.
- **Parity note**: the phone hint is a snackbar string, the car error is a host-reported message — surfaces differ
  because the car has no snackbar slot, but both say the link could not be read.
- **Logging**: both outcomes log shape only (`shape=unusable`), never the URL or a coordinate (`guidelines/Logging.md`).

### D7: Short-link resolution becomes a shared `:core` seam, used by the phone and the car

The `HttpURLConnection` redirect follow, its 5 s timeout and the URL-decoding of the target move from
`SharedLocationParser.resolveShortLink` into a `:core` resolver (`ShortLinkResolver`, a `suspend fun resolve(url: String?): String?`
over `Dispatchers.IO`). The pure parser keeps its injected `(String) -> String?` resolver parameter, so it stays
synchronous and JVM-testable; `SharedLocationParser` passes the shared implementation, and the car session calls it
inside its existing coroutine before the second parse.

- **Why**: resolution is network and must not enter a pure function. `guidelines/Design.md` §1 requires shared logic
  in `:core` and §639 forbids a second copy of it — a resolver in `:auto` would be exactly that copy, and `:auto`
  cannot depend on `:app` (wrong layering direction), so `:core` is the only layer both surfaces may call. The parser
  itself stays synchronous because it is called from the car's host path.
- **Alternatives**: (a) resolve inside the parser — makes a pure function Android/network-bound and blocks the car
  host thread; (b) duplicate a resolver in `:auto` — two implementations of the timeout, the redirect policy and the
  percent-decoding, which is the drift `Design.md` §12 warns about; (c) have the car parse the short link through the
  phone app — the car session has no phone app to call and no process guarantee; (d) resolve only after a first parse
  fails — doubles URL handling and makes the outcome order-dependent.
- **Threading**: intent parse is main-thread string work (existing); resolution suspends on `Dispatchers.IO` from both
  callers — the phone wrapper's existing `withContext(Dispatchers.IO)` and the car session's `scope.launch` plus
  `withContext(Dispatchers.IO)` (its geocode lookup already leaves the host thread); handler write/collect stays on the
  main-thread `StateFlow` (existing); candidate lookup stays on `defaultDispatcher` (existing). The new `:core`
  resolver is a suspend function with no dispatcher of its own, so it cannot run on a caller's thread by accident. No
  native work and no host mutation happens on the resolution path, so §8's guarded host seam is untouched.
- **The chain's shape is part of the decision**: the resolver walks at most 5 hops with the existing 5 s timeouts, so
  a stalling remote bounds the wait at ~25 s off the caller's thread (a real Google chain answers in well under a
  second). Walking by hand is what makes that bound visible: with `instanceFollowRedirects = true` the number of
  requests is invisible *and* the answer is wrong (see the class note in `ShortLinkResolver`).

### D8: Widen the manifest `VIEW` hosts, keep the car category

`DeepLinkActivity`'s `VIEW` filter gains `google.com`, `goo.gl`, `maps.google.de`, `www.google.de` (host wildcards
cannot span a country TLD, so the set is enumerated); the `androidx.car.app.category.NAVIGATION` category stays.

- **Why**: measured on the emulator, `adb shell cmd package query-activities -a android.intent.action.SEND -c android.intent.category.DEFAULT -t text/plain`
  lists `DeepLinkActivity`, while a `BOGUS` category matches nothing — an extra category in a filter does not block a
  plain share, so the category is safe to keep. The host list matters only for "open with" (`ACTION_VIEW`).
- **Alternatives**: (a) leave the hosts as they are — a `.de` link can then only arrive through a share, not through
  "open with"; (b) add a `<data android:pathPrefix="/maps">` — makes the filter fragile against URL shape changes
  that the parser is designed to absorb; (c) filter only on `google.com` — drops the country hosts most German users
  produce.
- **Risk**: a host list is a maintenance surface. Mitigation: the share path needs no host at all (it matches
  `text/plain`), so a link from an unlisted host still arrives.

## Risks / Trade-offs

- [The shared rule changes car behavior] → `auto-deep-links` delta states the new outcome; `:auto` suite re-run;
  the car's previous behavior was a guaranteed failure (geocoding a URL).
- [`UnusableLink` makes a `youtube.com` share show "link could not be read"] → acceptable: a link shared into a
  navigation app is expected to be a location; the previous behavior searched for the URL text.
- [Ranking by name can order a wrong candidate first] → still a list, not a selection; the hint is a preference, not
  a filter (D4).
- [Single candidate opens the wrong object] → details sheet is dismissible and the picker is reachable by
  long-press (D5).
- [Short-link resolution needs network] → 5 s timeout on both surfaces; an offline share of a short link degrades to
  the hint on the phone and to the guarded `reportError` on the car, which is now explicit feedback rather than a
  silent search or a misleading "no matching location" (D6, D7).
- [The car resolves a link while the host path is mid-mutation] → resolution is a suspending call on `Dispatchers.IO`
  and every host-facing send stays behind the existing gate; the car case is verified on the emulator in task 7.5.
- [Manifest host enumeration drifts] → the share path is host-independent, so a missing host costs only the
  "open with" affordance (D8).

## Migration Plan

Additive. No data migration, no persistence change, no native/JNI or submodule change. Rollback: revert the parser
outcome type and the URL ladder, the `:core` resolver extraction and the car resolver call, the wrapper label
fallback, the activity's subject forwarding, the ViewModel ranking/single-candidate branch, the string resources and
the manifest hosts — the previous "URL as query" behavior returns, and both spec deltas must be reverted with it.
Per-surface order: the `:core` resolver seam and the parser outcome + tests first (both surfaces still compile against
a `when` that treats `UnusableLink` as `None` until the callers adopt it), then the phone caller, then the car caller
(which is also the point where `:auto` gains the resolver dependency on `:core`), then the manifest hosts.

## Verification

- Unit tests carry the spec scenarios: `DeepLinkParserTest` (one case per URL shape, priority, tracking parameters,
  unusable link, text-plus-link), `ShortLinkResolverTest` (one case per redirect policy: target reached, no
  redirect, relative `Location`, non-terminating chain, transport failure, plus the real share link's chain and
  what the parser must read out of it), `SharedLocationParserTest`
  (label source, unusable verdict), `MapCanvasViewModelSharedLocationTest` (ranking order, single candidate, hint on
  unusable, search untouched), `DeepLinkPrivacyTest` and `DeepLinkLogMessageTest` (coordinate-free log lines),
  `NavigationSession` deep-link cases (resolved short link navigates, unusable link reports an error instead of
  geocoding).
- `openspec/changes/read-shared-google-maps-links/tasks.md` names the single test case per delta scenario.
- New invariant (D1/D2): "a URL never reaches the query field." Revert-check: make the parser return the raw URL as
  the query again, the named case must fail, then restore and re-run forced green.
- On-device measurement (emulator): `adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "<link>"`
  with a real `maps.app.goo.gl` link → logcat under tag `NaviVeylin` shows the coordinate-free
  `Shared location parsed:` line, the search panel stays closed, and the candidate/details flow opens; screenshot of
  the snackbar measured with the `pixel-check` skill. Limitation to record: the synthetic intent reproduces the
  shared payload, not a tap in Google Maps' own share sheet — that tap stays a manual check.
- Compose assertion for the visible order: the ranked first candidate is the top row of the picker
  (`CandidatePickerSheetComposeTest`), so the ranking is proven in pixels, not only in state.
- Car measurement (emulator with the Android Auto host, or the desktop head unit): send the synthetic share intent
  `adb shell am start -a android.intent.action.VIEW -d "https://maps.app.goo.gl/<real-link>"` and read `adb logcat -s NaviVeylin`
  for the coordinate-free deep-link line plus the navigation start; the case that must NOT appear is a geocode of the
  link. Record the host thread being unblocked (the screen stays responsive while the link resolves) and state
  explicitly if the head unit is unavailable — then the resolver path is proven by the `:auto` unit case plus the
  phone-side device run only.

## Open Questions

None.
