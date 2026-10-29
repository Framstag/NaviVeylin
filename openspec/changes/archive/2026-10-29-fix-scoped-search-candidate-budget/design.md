# Design

## Root cause

One mechanism: the per-source free-text budget is spent on hits the scope then discards. The walk stops when
the *collected* count reaches the limit
(`app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp:4899-4901` at the pre-change tip
`5188f2138`, tested at `:5027`, `:5031` and `:5054`), and only afterwards does the geographic filter drop the
hits outside the resolved region's extent (`:5098-5117`). The two rules are individually correct — the budget
keeps the text index from crowding structured results out, the filter keeps an Iceland map from answering a
Dortmund scope — so the defect is their order and their accounting, not either rule.

## The fix

1. **`naviveylin::FreeTextBudget`** (`search_scope.h`, next to `IsInsideGeoBox` and `GeoBoxContains`). Holds the
   limit and the extent, offers `Admit(lat, lon)` per hit and answers `Reached()`; only a hit it keeps
   increments the count the limit is compared against, a refused hit increments `Scanned()`/`Dropped()` alone.
   Pure and dependency-free, like its neighbours, so the counting is decidable on the host.
2. **The extent is derived before the walk** (`OSMScoutClient.cpp`, inside the existing
   `dbThread->RunSynchronousJob`, before the `for (const auto &db : databases)` loop). It used to be derived
   lazily, the first time the resolving database was reached, which is why a filter at collection time would
   have seen an unset extent for every database walked before it. The scope vector the resolving database's
   structured search uses is resolved in the same block and reused, so `ResolveSearchScope` runs once, not
   twice.
3. **The collection loop offers each hit to the budget** and pushes only what it keeps; the size check is gone
   and the post-walk filter with it (an entry in `freeTextEntries` is in scope by construction, which is also
   what the per-entry JNI verdict relies on). The debug line that names the count of refused hits moves next to
   the collection, keeps its wording ("search scope: dropped N of M free-text hit(s) outside the scope"), and
   stays debug-gated and coordinate-free.

## Why this option, and why it needs no device measurement

The item left three options and recommended this one "with the native measurement taken first". The
measurement is not owed, because the cost it was meant to bound does not exist in this tree:

- `osmscout::TextSearchIndex::Search` (`libosmscout/src/osmscout/db/TextSearchIndex.cpp:134-170`) walks the
  MARISA tries with `predictive_search` until it returns false and materializes **every** hit of the query in
  the `ResultsMap` it is handed. It takes no limit, so the budget never shortened the index traversal — the
  map is complete before the first `freeTextLimitReached()` call.
- What the budget bounds is the per-hit `BuildFreeTextEntry` object load. Under the old rule the hits in the
  first `limit` positions were loaded — including the ones the filter then dropped. Under this rule the loads
  continue behind that boundary until `limit` hits were **kept**, so the added work is at most the object loads
  of the out-of-scope hits in the query's hit set, a set the call already holds in memory.
- Option 2 (over-collect by a bounded factor, trim after filtering) was eliminated on the same fact: the
  factor would bound object loads, not traversal, and no measurement can pick it — a selective scope still
  leaves the list short, which is the defect.
- Option 3 (state "up to `limit`" as the contract) was eliminated by the decision recorded on the item: the
  owner chose to make "a scoped search returns `limit` candidates when the index holds them" true.

Not claimed: the dedup against structured results still runs after the walk, so a query whose objects are also
found structurally can still publish fewer than `limit` free-text candidates. That is a second, unrelated
budget question and the requirement says "kept", not "published".

## Blast radius

- Two submodule source files plus the submodule's own host test; the gitlink bump. The derivation of the box
  itself is the host unit `naviveylin::DeriveScopeExtent` (`search_scope_extent.h`, landed by the submodule
  commit below this one — issue #20); this change only moves *when* it is called and qualifies the call.
- `#ifdef OSMSCOUT_HAVE_LIB_MARISA` is the boundary of the changed block (the free-text pass does not exist
  without the text index), and both configurations are compile-checked — see the tasks.
- The Java side, the JNI signature, `LocationEntry`, the app-side ranker and the per-entry "in scope" verdict
  are untouched: the entries that reach them are the same shape, there are simply more of them in a scoped
  search.
- The log line's denominator changes meaning slightly: it is now every hit the walk reached, before the
  structured-result dedup, where it used to count the post-dedup entries. Counts only, no coordinate, and the
  sentence reads the same.

## Verification

- Red/green on the host, with the HEAD rule expressed as a one-line mutation of the helper (`Reached()` reading
  the scanned count): `Tests/src/SearchScopeTest.cpp` "A scoped free-text walk collects the requested
  candidates, not fewer" fails `kept == 30` with `0 == 30` under the mutation and passes after it.
- Both `#ifdef` configurations of the translation unit compile (clang 18 from NDK 27, the arm64 Debug include
  set, `-fsyntax-only -Wall -Wextra`), with the same two pre-existing `InMemorySettingsStorage` destructor
  warnings as the pre-change tip.
- The JVM gate is unaffected in content (no Kotlin changed) but is run because the change touches compiled
  paths: `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache`.
