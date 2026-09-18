## Context

libosmscout's location search matches query tokens against index names through a `StringMatcher` created by a factory. The app's JNI bridge requests the transliterating variant for both search entry points and enables partial matching (`libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp`, string search and form search).

That matcher uppercases the text and the pattern *before* transliterating (`libosmscout/src/osmscout/util/StringMatcher.cpp`), while the character map declares `ß`'s uppercase form as `ß` itself and its transliteration target as the lower-case `"ss"` (`libosmscout/src/osmscout/util/utf8helper_charmap.cpp`, `charmap_c3`). Result:

```
name  "Erbstollenstraße"  -> upper -> "ERBSTOLLENSTRAßE"
                          -> translit -> "ERBSTOLLENSTRA" + "ss" + "E"   <- lower case
query "Erbstollenstrasse" -> upper -> "ERBSTOLLENSTRASSE"
                          -> translit -> "ERBSTOLLENSTRASSE"            <- upper case
find(pattern) in transliterated text -> not found -> no match
```

Umlauts are unaffected because their transliteration targets are case-correct (`Ü`→`U`, `ü`→`u`), so uppercasing both sides stays consistent.

Measured constraints (emulator `emulator-5554`, NRW map database, search dialog in Places mode):

| query | observed result |
|---|---|
| `Erbstollen` | finds `Erbstollenstraße` (Rüdinghausen/Witten) |
| `Erbstollenstrasse`, `Aktienstrasse`, `Fismerstrasse` | no results |
| `Gunnemannshof` | finds `Günnemannshof` (diacritic folding works) |
| `Erbstollenstrasse 10 58454 Witten` | only `boundary_administrative` fallbacks (Annen, Düren, Rüdinghausen, Stockum, Witten, Witten-Mitte) |

The NRW `location.idx` name pool contains `Erbstollenstraße` (in the Rüdinghausen street list) and spells `straße` 65 848 times against `strasse` 22 times, i.e. the index stores the OSM spelling and carries no searchable `ss` variant. The free-text (MARISA) path is *not* affected: it normalizes its lookup key to lower case before transliterating (`TextSearchIndex::Search`, `UTF8NormForLookup` + `UTF8Transliterate`), matching how the keys were written at import.

The address-book resolver therefore behaves correctly and still fails: it issues the right candidates, receives only region/postal-area partial-match fallbacks, and its street-evidence gate rejects them (device log: `19/21/10/1 results, 0 with street evidence`), ending in "Kein Ort für diese Adresse gefunden". Google Contacts is the source of the `ss` spelling (commit `0cfb074` records that sync transliterates `ß`→`ss`).

Related OpenSpec state: `fix-contact-address-resolution` (in progress) owns the contact-side chain and its street-evidence contract, and its on-device task 5.1 is exactly this failing check; `address-book-search` states "Address found" without the `ß`/`ss` case. No spec currently constrains transliterated matching.

## Goals / Non-Goals

**Goals:**

- A query SHALL match an index name modulo transliteration **and** letter case, in both directions (`ss`-query ↔ `ß`-name and `ß`-query ↔ `ss`-name), identically on phone, in the route panel, in the address-book resolution, and in the Android Auto search — one backend, no per-variant deviation.
- Keep the existing diacritic folding behavior byte-identical for names without `ß`.
- Keep the `match` vs `partialMatch` quality classification intact: ranking and the partial-match fallback depend on it.
- Fix it where the defect lives, with a minimal, upstreamable library patch; the app keeps calling the same JNI API with the same arguments.
- Close the test gap that let this ship: a native unit test that fails on the current code.

**Non-Goals:**

- Re-importing or regenerating map databases, or changing the index format or the import pipeline (`--textIndexVariant`).
- Touching the address-book candidate chain, the street-evidence gate, or `AddressParser` normalization — all verified correct on device.
- Changing JNI method signatures or the JNI bridge's matcher/partial-match choices.
- Fixing unrelated transliteration quirks (e.g. `ẞ` U+1E9E, which translates to itself); no index name in the affected data uses them.
- Any UI, rendering, or guideline-behavior change.

## Decisions

### D1 — Where the fix lives

| option | assessment |
|---|---|
| (a) libosmscout library patch in `StringMatcher.cpp` — **chosen** | The defect is a wrong comparison inside the library. One file, a few lines, no API change, upstreamable. Fixes every caller at once (search box, route panel, AA, contacts). |
| (b) Custom matcher factory in the JNI bridge (`OSMScoutClient.cpp`) | Also inside the submodule, but a larger, bridge-shaped patch that duplicates library behavior; the bridge is supposed to mirror upstream APIs (`guidelines/Design.md` §5). Rejected. |
| (c) Kotlin query-variant expansion (emit `ß`-spelled candidates) | Guesswork: the app cannot know whether the index spells a name with `ß` or `ss`, so it must expand every candidate, doubling native queries and complicating the evidence gate. Leaves the search box, the route panel, and the AA search broken. Rejected. |
| (d) Re-import maps with transliterated names | Changes DB content and the index variant for all maps, doubles index content (or loses the original spelling used for display), and does not fix existing installs without a re-download. Rejected. |

Chosen: (a). The app and the JNI bridge are untouched.

### D2 — Comparison mechanism inside the matcher

Options: (i) case-fold the *transliterated* forms before `find()` — **chosen**; (ii) lower-case the transliterated forms instead of upper-casing them; (iii) change the character map so `ß` translates to `SS` — impossible with one table serving both cases, since a lower-case input must still produce `ss`; (iv) add a second matcher that normalizes the whole string through `UTF8NormForLookup` (as the MARISA path does) — correct in principle but a bigger behavioral change for the index-based path, where the current match/partial-match semantics and ignore-token handling expect the existing matcher.

(i) and (ii) are equivalent for matching; (i) keeps the existing upper-case convention. Required properties of the chosen implementation:

- keep the current fast path (direct `find()` of the upper-cased pattern in the upper-cased text) so unaffected queries take the same path as today;
- only when that fails, compare case-normalized transliterated forms (`UTF8StringToUpper(UTF8Transliterate(...))` on both sides, or the equivalent helper) so `ß`→`ss` and `SS` align;
- derive `match` vs `partialMatch` from the position/length relation of the **case-normalized transliterated** strings, preserving today's classification semantics;
- keep the empty-transliteration guard (a pattern with no transliteration output must still return `noMatch` rather than matching everything).

### D3 — Test placement

Options: (i) extend the existing `Tests/src/StringUtilsTest.cpp` — **chosen**; (ii) add `Tests/src/StringMatcherTest.cpp` and register it in `Tests/CMakeLists.txt` and `Tests/meson.build`.

`StringUtilsTest` already links libosmscout and already tests the transliteration table (including `ß`→`ss`), so a matching test case belongs there and needs no build-system edit — the smallest change that closes the gap. (ii) is cleaner by name but adds two build files to the patch for no behavioral gain. The test SHALL cover, at minimum: `ss`-query matches `ß`-name as a full match; `ß`-query matches `ß`-name; `ü`-query-without-diacritic matches `ü`-name (regression guard); prefix of a `ß` name matches as `match`/`partialMatch` consistent with today; a name/query pair without any transliteration difference still matches.

### D4 — App-side mitigation

None. The street-evidence gate (`AddressBookResolver.accept` / `AddressRanker.hasStreetEvidence`) already folds `ß`→`ss` on the result label, so it accepts the entry the moment native returns it; loosening it would re-introduce the free-text false positives the spec forbids. No Kotlin change is therefore part of this change; the missing coverage is at the native matcher, which the Robolectric fakes cannot reach (they construct labels directly and never exercise native matching) — the reason the existing `AddressBookResolverTest` case for a `ß` street stayed green while the device failed.

### D5 — Guideline alignment

`guidelines/Design.md` §5 says behavior issues should be fixed in Kotlin rather than by changing native semantics, and §12 says to change shared/native behavior only as a last resort. That clause targets JNI *contracts* (keep them mirrored so submodule syncs stay clean). This change is a library bug fix behind an unchanged contract, and every Kotlin-only option was rejected in D1/D4, so it satisfies "last resort". To keep the guideline and the change consistent, §5 gains one clarifying sentence: transliteration/matching defects inside libosmscout are fixed upstream as minimal patches, not worked around in Kotlin.

### D6 — Delivery and build

- Patch in the submodule (`app/src/main/cpp/libosmscout/`, branch `naviveylin-local`), committed and pushed to `Framstag/libosmscout`; the main repo's gitlink SHA is bumped in a separate commit afterwards.
- No Gradle, manifest, resource, or asset change. `:osmscout-client-java` and `:app` are rebuilt for all three ABIs (arm64-v8a, armeabi-v7a, x86_64); both dist flavors.
- No database regeneration, no migration, no stored-state change; the map database on the device stays as it is.
- libosmscout stays Android-free outside `Android/`: the patch touches only `libosmscout/src/osmscout/util/` and `Tests/src/`, so the CI gate `Check libosmscout Android-free outside Android/` is unaffected.

## Risks / Trade-offs

- **Broader matching surfaces new results.** Queries that today return nothing (or only region fallbacks) will return the matching object. This is the intent. Residual risk: a query that matched one entry can now additionally match a differently spelled entry, shifting ranking; the result limit and existing ranking (house > street > POI, exact > candidate) absorb this, and the address-book gate still requires street evidence.
- **Case normalization also touches exotic characters.** An audit of the character map (`awk` over `utf8helper_charmap.cpp`) finds 70 rows whose uppercase form is the character itself while its transliteration target is lower case — ß plus symbols and IPA letters (`ª`, `×`, `ɸ`, `ȸ`, `ʐ`, ...). Names containing those now also match their ASCII spelling, the same class of defect this change fixes (matching only widens; no previously matching query stops matching). How often those rows appear in real place names was **not** established: a raw byte-sequence grep over `location.idx` counts binary coincidences, not name characters, so it is not usable as evidence. Practical impact is expected to be limited (the change is a superset of the old behaviour), and ß is the row this change exists for (65 848 `straße` occurrences in the NRW name pool).
- **Both spellings present in one database** (an `ss` name and a `ß` name for different objects) could now return two entries for one query. Rare in the affected data and covered by the disambiguation display already specified in `location-search`; acceptable.
- **The `match`/`partialMatch` reclassification could change ranking subtly** if the quality is derived from the wrong string length. Mitigated by deriving it from the case-normalized transliterated pair (D2) and by asserting both qualities in the native test.
- **Upstream sync cost.** The patch must survive future submodule syncs; keeping it small and pushing it upstream (or at least to `naviveylin-local`) limits that. If upstream declines, the patch stays in `naviveylin-local` and every sync must re-check it — recorded as the main maintenance cost of option (a) over the app-side options.
- **Behavior change in a shared library.** Any other consumer of the transliterating matcher gains the same permissiveness. Intended (it is the documented transliteration behavior); the matcher is only used for search matching, not for storage or hashing.
- **Rollback** is a revert of the submodule commit plus the gitlink bump; no data migration and no persisted state are involved.
- **Known limitation (not fixed):** `ẞ` (U+1E9E) translates to itself, so a query spelling that character is not folded. No affected index name uses it; the character map is upstream data we do not rewrite in this change.

## Threading model and lifecycle

No new component, thread, dispatcher, or lifecycle hook.

- Matching stays inside the existing native search execution: the JNI bridge runs search jobs through `DBThread::RunSynchronousJob`; the matcher change adds no work outside that job.
- Kotlin call sites are unchanged: `AddressBookResolver.resolveAddress` and the search providers keep calling the native search from their existing background dispatchers; the debounce, cancellation, and timeouts defined in `location-search` and `guidelines/Design.md` §4 stay as they are.
- No native call is added on the main thread; no new StateFlow, ViewModel, or `DisposableEffect`.
- The patched code is stateless and allocation-only-per-comparison, with the same allocation profile as today (one extra transliterated copy on the fallback path only).

## Verification

Native (submodule):

1. New test case in `Tests/src/StringUtilsTest.cpp`: run `ctest -R StringUtilsTest` (CMake) and the same target under Meson. It SHALL fail before the patch and pass after; the revert-check (restore the old comparison, confirm red, restore the patch, confirm green) is the evidence that the test actually covers the defect.
2. Full native test suite of the submodule for the touched tree (Catch2 via `ctest`), to prove no ranking/parsing regression in `LocationService` tests (`SearchForLocationByStringTest`, `SearchForLocationByFormTest`, `LocationServiceTest`).

App (via the `build-app` / `run-tests` skills, `guidelines/Build.md`):

1. `run-tests`: `:app` unit tests green (including the existing `AddressParserTest`, `AddressBookResolverTest`, search and ViewModel suites); record test-result counts.
2. `build-app`: `./gradlew :app:assembleMobileDebug` and `:app:assembleAutomotiveDebug`, all three ABIs, no build warnings.
3. `./gradlew :osmscout-client-java:jacocoTestReport` unchanged (no Java bridge change).

On device (emulator with the NRW map, plus a head unit or the `:auto` harness):

1. Search dialog, Places mode — the recorded matrix SHALL flip: `Erbstollenstrasse`, `Aktienstrasse`, `Fismerstrasse` return the street; `Erbstollen`, `Gunnemannshof`, `Erlenbruch` return the same results as before; `Erbstollenstrasse 10 58454 Witten` returns the street/house entry, not only region entries.
2. Address-book resolution of the recorded failing contact resolves to the street/house object and opens the details view; `adb logcat -s AddressBookResolver NaviVeylin` shows at least one candidate with a non-zero street-evidenced count (the evidence the in-progress `fix-contact-address-resolution` task 5.1 needs).
3. Route-panel start/destination search finds the `ss`-spelled street.
4. Android Auto search template (emulator/head unit): the `ss`-spelled query returns the same entry, with the same label and region hierarchy as the phone.
