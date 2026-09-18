## Why

Any map object whose name contains `ß` (German `Straße`) is unreachable by a query that spells it `ss`, because libosmscout's transliterating `StringMatcher` uppercases text and pattern *before* transliterating, while the character map maps `ß` to lowercase `ss`: the transliterated name ends in `...ssE` and the transliterated pattern in `...SSE`, so the fallback `find()` never matches.

Verified on device (NRW map, emulator): `Erbstollenstrasse`, `Aktienstrasse`, `Fismerstrasse` → "Keine Ergebnisse gefunden", while `Erbstollen` (byte-identical prefix) finds `Erbstollenstraße` (Rüdinghausen/Witten) and `Gunnemannshof` finds `Günnemannshof` (umlaut folding works, `Ü`→`U` is case-correct). The NRW index stores the OSM spelling (`straße` 65 848×, `strasse` 22×).

Google Contacts transliterates `ß`→`ss` when it syncs a contact (already recorded in commit `0cfb074`), so a contact whose address is a `ß` street can never be resolved: the address-book resolver issues the correct candidates, gets only region/postal-area partial-match fallbacks, and its street-evidence gate correctly rejects them — ending in "Kein Ort für diese Adresse gefunden" even though the street exists and a manual search with the `ß` spelling finds it. The same defect hit the phone search box and the Android Auto search for every `ß` street in the map.

## What Changes

- Make transliterated name matching case-consistent in libosmscout's `StringMatcherTransliterate`: perform the comparison on case-normalized transliterated forms, so a transliteration that introduces cross-case output (`ß`→`ss`) still matches. `ß`/`ss` becomes symmetric (`ss` query ↔ `ß` name and the reverse); umlaut folding (`ü`→`u`, `ö`→`o`, `ä`→`a`) keeps its current behavior.
- Add the missing native test coverage for transliterated *matching* (upstream currently tests `UTF8Transliterate` output only; no test exercises `StringMatcherTransliterate::Match`), including the `ß`/`ss` and `ü`/`u` cases.
- No change to the address-book resolver, its candidate chain, or its street-evidence gate: those behaved exactly as specified (device log: every candidate returned only street-less fallbacks). No change to map data, index format, or the free-text (MARISA) path, whose lookup normalizes to lowercase on both sides and is therefore unaffected.
- Additive, not breaking: matching only becomes more permissive; queries that matched before keep matching, and no query that previously returned results loses them. Queries whose spelling previously produced an empty result set now return the object. No database, file format, or JNI signature change; the Android ABI surface is untouched.

### Previous specifications changed

- `location-search` — the structured (location index) result contract gains the transliteration-consistency requirement; the existing "Suggestions-while-type", "Search scoped by current admin region" and "Fully qualified query with postal code still matches" requirements are unchanged but now actually hold for `ß` streets.
- `address-book-search` — the "Address resolution search" requirement gains the `ß`↔`ss` case (contact street field as written by Google Contacts).
- `auto-search` — the car search template gains the phone-parity statement for transliterated matching.

### Scope

General search-backend fix: phone search dialog, route-panel search, Android Auto search, and the address-book resolution all call the same matching code. Not AA-only, not phone-only. Android Auto behavior needs no platform-specific deviation, so parity is stated as "identical matching, no deviation".

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `location-search`: structured search SHALL match a name spelled with `ss` against an index name spelled with `ß` (and the reverse), and SHALL remain case-insensitive; queries containing `ß` words SHALL NOT return an empty result set.
- `address-book-search`: a contact postal address whose street field carries Google Contacts' `ss` transliteration SHALL resolve to the `ß`-spelled street in the location index instead of reporting "not found".
- `auto-search`: the car search template SHALL match transliterated names exactly as the phone search does (same backend, no deviation).

## Impact

Affected files and modules:

- **libosmscout submodule — submodule patch, minimal and upstreamable** (`app/src/main/cpp/libosmscout/`, branch `naviveylin-local`, pushed to `Framstag/libosmscout`; main repo gitlink bumped afterwards):
  - `libosmscout/src/osmscout/util/StringMatcher.cpp` — case-consistent transliterated comparison in `StringMatcherTransliterate::Match` / its constructor.
  - `libosmscout/Tests/src/` — new transliterated-matching test (`ß`/`ss`, `ü`/`u`, exact vs partial match quality), registered in the parent project's test build.
  - No `Android/` directory change; libosmscout stays Android-free outside `Android/` (CI gate `Check libosmscout Android-free outside Android/`).
- **App / JNI bridge**: no change. The bridge's choice of `StringMatcherTransliterateFactory` plus `partialMatch=true` (`libosmscout-client-java/src/OSMScoutClient.cpp`, form and string search) is correct as-is; no Kotlin change is needed because `AddressBookResolver.accept` / `AddressRanker.hasStreetEvidence` already folds `ß`→`ss` on the result label (`app/src/main/java/com/naviveylin/data/AddressParser.kt`), so evidence passes as soon as the entry is returned.
- **Build**: `:osmscout-client-java` and `:app` native builds are unaffected by the source change but must be rebuilt for all three ABIs (arm64-v8a, armeabi-v7a, x86_64) once the submodule moves.
- **Guidelines**: `guidelines/Design.md` (native boundary / submodule-patch conventions, submodule SHA bump rule) and `guidelines/Build.md` (native build and test verification via the `build-app` / `run-tests` skills). No `guidelines/UI.md` or `guidelines/MapRendering.md` change (no UI or render-pipeline behavior).
- **User-visible data**: none stored; no migration. The visible difference is that previously empty searches for `ss`-spelled `ß` names now return results.

Rollback path: revert the libosmscout submodule commit and the gitlink bump in the main repo (two commits, no data migration, no stored state involved). Reverting restores the previous, more restrictive matching exactly.

Verification (detailed in `design.md` / `tasks.md`): a native unit test for transliterated matching (fails on the current code), plus an on-device re-run of the recorded failing cases — manual search for `Erbstollenstrasse` / `Aktienstrasse` / `Fismerstrasse`, and the address-book resolution of the affected contact — on phone and in the Android Auto search.
