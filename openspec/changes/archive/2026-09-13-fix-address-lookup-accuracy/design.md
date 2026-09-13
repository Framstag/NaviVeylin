# Design — fix address lookup accuracy

## D1: Where to make full-address queries resolve

**Options**
- **A (chosen): App-side parser + structured form search, plus native partial-match enablement.** Free text is parsed into `(street, houseNo, plz, city)` by a shared `AddressParser`; address-like queries additionally run `searchLocationByForm`, whose house-level results rank first. Native side sets `partialMatch=true` so a failed full chain returns the street/region candidate instead of nothing.
- B: Native-only (`partialMatch=true` + token-stripping for postal codes). Simpler, but surplus tokens beyond the PLZ (region/country spellings) still zero out results, and there is no house-level pass — street-level only. Leaves the phone/car/contacts chains dependent on native quirks.
- Rationale: both failure modes are covered independently — parser+form gives the house-level hit ("not found" fix), native partialMatch gives the fallback ("house not in index" fix). Native change is upstreamable and benefits all libosmscout consumers.

## D2: Free-text demotion for address-like queries

**Options**
- **A (chosen): Stable-post-merge ordering — within equal native rank, structured (`idx`) entries sort before free-text (`txt`) entries in `DoSearchLocations`.** Free-text remains available for non-address queries (e.g. "cafe central") and for queries with no structured hit, but a structured street/address can never sit below a bus-stop free-text hit of the same native rank.
- B: Drop free-text results entirely for address-tokenized queries. Loses valid POI suggestions while typing ("Erbstollenstra" may want a related POI), and the tokenizer heuristic is brittle.
- Rationale: demotion preserves the existing "POI found while typing" scenario; only the relative order changes. Implemented in the JNI by sorting the merged result vector with a source-aware comparator after the native rank.

## D3: City-only fallback in contact resolution

**Options**
- **A (chosen): Gate it.** `AddressBookResolver` auto-selects only results with street-token (or postal-code) evidence; otherwise resolution reports not-found. The phone search list stays permissive (user picks knowingly).
- B: Keep city-only result as a low-ranked option. Silent wrong-pin risk (bus stops km away) is exactly the reported bug.
- Rationale: contract resolution auto-pins one result; a km-away pin is worse than no pin. Phone search is a list, so permissiveness costs nothing.

## D4: Postal-code-in-street parsing

**Options**
- **A (chosen): `AddressParser` strips a leading/trailing 5-digit postal code from the street string before house-number extraction**, and the separate PLZ field wins when present.
- B: Only honor the postal-code field, ignore PLZ inside street. Fails contacts whose only PLZ sits in the street field.
- Rationale: A covers both layouts; house-number regexes stay unchanged.

## D5: Threading / lifecycle

- `AddressParser` is a pure function; `StructuredAddressSearch` runs form search on the same background dispatcher already used for `searchLocations` (no new threads).
- `MapCanvasViewModel.mergeSearchResults` stays on `defaultDispatcher`; `AddressBookResolver.resolveAddress` stays synchronous-under-the-hood as today.
- No new Hilt bindings; parser/helper classes are plain `@Inject`-less objects or instantiated in the existing modules.

## D6: Native verification

- Host repro against `maps/repository/public/europe/germany/nordrhein-westfalen/arnsberg-regbez/v27` with `LocationLookup`/`LocationLookupForm` demos before/after, plus submodule unit-test addition for the PLZ-token case.
- ABI build matrix via CMake (arm64-v8a, armeabi-v7a, x86_64) in CI.

## Risk assessment

- `partialMatch=true` returns more candidates on all searches; the JNI limit + stable source ordering keep lists bounded and sane. Regression risk contained to ordering, not removal of results.
- Submodule bump changes all APKs consuming the library; verified with the existing CI gate (Android-free purity check not affected — only client-java JNI files change).
