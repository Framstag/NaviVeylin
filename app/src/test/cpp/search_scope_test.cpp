// Host unit test for the sibling-region search scope decision (see
// app/src/main/cpp/libosmscout/libosmscout-client-java/src/search_scope.h).
//
// Pure helpers: no database or file I/O, no libosmscout dependency. Run on
// the host:
//
//   g++ -std=c++17 -I app/src/main/cpp/libosmscout/libosmscout-client-java/src \
//       app/src/test/cpp/search_scope_test.cpp \
//       -o /tmp/search_scope_test && /tmp/search_scope_test
//
// Covers spec (location-search) "Search scoped by current admin region":
// depth normalization to the admin_level scale, the cap boundary (parent at
// the cap expands, coarser does not), and the invariant that an unknown
// level never expands.

#include <cassert>
#include <cstddef>
#include <cstdint>
#include <iostream>

#include "search_scope.h"

using naviveylin::kMaxSearchRegionLevel;
using naviveylin::NormalizeDepthToAdminLevel;
using naviveylin::ShouldExpandScope;

void TestDepthNormalization()
{
  assert(NormalizeDepthToAdminLevel(0) == 0);  // defensive
  assert(NormalizeDepthToAdminLevel(1) == 0);  // root
  assert(NormalizeDepthToAdminLevel(2) == 2);  // country
  assert(NormalizeDepthToAdminLevel(3) == 4);  // state
  assert(NormalizeDepthToAdminLevel(4) == 6);  // county
  assert(NormalizeDepthToAdminLevel(5) == 8);  // city
  assert(NormalizeDepthToAdminLevel(6) == 10); // suburb
  std::cout << "ok depth normalization\n";
}

void TestCapBoundary()
{
  // Parent at the cap (Regierungsbezirk/district) expands.
  assert(ShouldExpandScope(5, kMaxSearchRegionLevel));
  // Finer parents (county, city, suburb) expand.
  assert(ShouldExpandScope(6, kMaxSearchRegionLevel));
  assert(ShouldExpandScope(8, kMaxSearchRegionLevel));
  assert(ShouldExpandScope(10, kMaxSearchRegionLevel));
  // Coarser parents (state, country) do not expand.
  assert(!ShouldExpandScope(4, kMaxSearchRegionLevel));
  assert(!ShouldExpandScope(2, kMaxSearchRegionLevel));
  std::cout << "ok cap boundary\n";
}

void TestUnknownLevelNeverExpands()
{
  // Level 0 (unknown or the artificial root region) never expands.
  assert(!ShouldExpandScope(0, kMaxSearchRegionLevel));
  std::cout << "ok unknown level never expands\n";
}

int main()
{
  TestDepthNormalization();
  TestCapBoundary();
  TestUnknownLevelNeverExpands();
  std::cout << "ALL TESTS PASSED\n";
  return 0;
}
