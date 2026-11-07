# Tasks

## 1. The counting rule as a pure helper

- [x] 1.1 Add `naviveylin::FreeTextBudget` to
  `app/src/main/cpp/libosmscout/libosmscout-client-java/src/search_scope.h`: it holds the limit and the
  extent, `Admit(lat, lon)` decides a hit and counts only the kept ones, `Reached()` compares the limit against
  the kept count, and `Scanned()`/`Dropped()` carry the numbers the debug line reports. Verify: the header stays
  dependency-free (no libosmscout include) and compiles in the host test build below.
- [x] 1.2 Add three cases to `app/src/main/cpp/libosmscout/Tests/src/SearchScopeTest.cpp`: the scoped walk
  (60 hits, 30 outside the extent, limit 30), the unset extent (every hit spends the budget) and the refused hit
  (it does not spend it) — spec `search-free-text`, "Free-text candidate budget counts kept candidates".
  Verify: `Tests/src/SearchScopeTest.cpp` passes 12 cases / 69 assertions.

## 2. The red case

- [x] 2.1 Falsify the new rule before trusting it: mutate `Reached()` to read the scanned count (the HEAD rule)
  and run the same suite. Verify: 12 cases, 10 passed, 2 failed — "A scoped free-text walk collects the
  requested candidates, not fewer" fails on `kept == 30` with `0 == 30`, "A refused hit does not spend the
  budget" fails on `REQUIRE_FALSE(budget.Reached())`; restore the helper and the suite is green again.

## 3. Apply the rule in the walk

- [x] 3.1 Derive the scope's extent before the database walk and reuse the resolved scope vector for the
  resolving database's structured search (`OSMScoutClient.cpp`, inside `dbThread->RunSynchronousJob` before the
  database loop). Verify: the file compiles in both `#ifdef OSMSCOUT_HAVE_LIB_MARISA` configurations (3.4) and
  `ResolveSearchScope` has one call site, not two.
- [x] 3.2 Replace the `freeTextLimitReached()` lambda with the budget object and offer every hit of the
  collection loop to `Admit()`; push only what it keeps. Verify: `grep -n "freeTextLimitReached"
  OSMScoutClient.cpp` finds nothing, and the loop's two stopping checks read `freeTextBudget.Reached()`.
- [x] 3.3 Delete the post-walk scope filter and move its debug line to the collection, keeping the wording and
  the debug gate. Verify: no `remove_if` over `freeTextEntries` for the extent remains
  (`grep -n "IsInsideGeoBox" OSMScoutClient.cpp` shows the per-entry verdict call sites only).
- [x] 3.4 Compile-check the translation unit in both configurations with the project's own compiler and include
  set (NDK 27 clang 18, arm64 Debug, `-fsyntax-only -Wall -Wextra`), the pre-change tip `5188f2138` as the
  baseline: this tree `exit=0 warnings=2`, this tree with `#undef OSMSCOUT_HAVE_LIB_MARISA` `exit=0
  warnings=2`, the pre-change tip `exit=0 warnings=2` — the two warnings are the pre-existing
  `InMemorySettingsStorage` destructor warning, so the change adds none.

## 4. Submodule commit and gitlink bump

- [x] 4.1 Commit the submodule work on `naviveylin-local` (three files: `search_scope.h`,
  `OSMScoutClient.cpp`, `Tests/src/SearchScopeTest.cpp`). Verify: `git -C app/src/main/cpp/libosmscout status
  --porcelain` is empty afterwards and the commit lists only those paths.
- [x] 4.2 Bump the gitlink in the main repository. Verify: the pin names the new submodule commit and pushing the
  branch pushes the submodule commit first.

## 5. Gate and evidence

- [x] 5.1 Run the gate once: `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache` (in the worktree, with
  `ANDROID_HOME`/`ANDROID_SDK_ROOT`/`ANDROID_NDK_HOME` in the environment instead of a machine-local
  `local.properties`). Verify: `BUILD SUCCESSFUL in 8m 16s`, `166 actionable tasks: 163 executed`, and the
  per-class XML tallies `:app` mobile **2138/0/0**, `:app` automotive **2138/0/0**, `:auto` **887/0/0**,
  `:core` **643/0/0**, `:osmscout-client-java` **112/0/0**.
- [x] 5.2 Run the structural checks the change's paths trip: `tools/check-openspec-hygiene.sh`,
  `tools/gen-feature-list.sh --check-classification`, `tools/check-doc-routes.sh` and
  `tools/check-todo-references.sh`. Verify: each exits 0 — this change touches `openspec/**` and `*.md` and no
  Kotlin, Java or Gradle file.
- [x] 5.3 Copy the cited evidence into the change's `evidence/` directory and keep the run's own record beside
  it. Verify: every `file:line`, case name and tally quoted above resolves in the tree at the recorded commit.
