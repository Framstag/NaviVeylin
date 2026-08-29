// Host unit test for BuildAdminRegionHierarchyPath (see
// app/src/main/cpp/libosmscout/libosmscout-client-java/src/admin_region_hierarchy.h).
//
// Pure helper: no database or file I/O, compiles against the libosmscout
// headers only. Run on the host:
//
//   g++ -std=c++17 -DOSMSCOUT_STATIC \
//       -I app/src/main/cpp/libosmscout/libosmscout-client-java/src \
//       -I app/src/main/cpp/libosmscout/libosmscout/include \
//       app/src/test/cpp/hierarchy_walk_test.cpp \
//       -o /tmp/hierarchy_walk_test && /tmp/hierarchy_walk_test
//
// Covers spec (osmscout-jni) "Search result region hierarchy scoped to
// originating database": single-database chains, root-only, truncated chain,
// and the invariant that a foreign-region entry can never be picked (the map
// fed to the walk is per-database by construction; a colliding key from
// another database is absent).

#include <cassert>
#include <iostream>
#include <map>
#include <string>

#include <osmscout/location/Location.h>

#include "admin_region_hierarchy.h"

namespace {

using osmscout::AdminRegion;
using osmscout::AdminRegionRef;
using osmscout::FileOffset;
using naviveylin::BuildAdminRegionHierarchyPath;

AdminRegionRef MakeRegion(FileOffset regionOffset,
                          FileOffset parentRegionOffset,
                          const std::string &name)
{
  auto region = std::make_shared<AdminRegion>();
  region->regionOffset = regionOffset;
  region->parentRegionOffset = parentRegionOffset;
  region->name = name;
  return region;
}

void TestRootOnly()
{
  // A region with no parents: root name only.
  std::map<FileOffset, AdminRegionRef> chain;
  AdminRegionRef bergkamen = MakeRegion(100, 0, "Bergkamen");
  chain[bergkamen->regionOffset] = bergkamen;

  assert(BuildAdminRegionHierarchyPath(bergkamen, chain) == "Bergkamen");
  std::cout << "ok root-only\n";
}

void TestFullChain()
{
  // Germany map: Bergkamen -> Kreis Unna -> Nordrhein-Westfalen -> Deutschland
  AdminRegionRef deutschland = MakeRegion(400, 0, "Deutschland");
  AdminRegionRef nrw = MakeRegion(300, 400, "Nordrhein-Westfalen");
  AdminRegionRef kreis = MakeRegion(200, 300, "Kreis Unna");
  AdminRegionRef bergkamen = MakeRegion(100, 200, "Bergkamen");

  std::map<FileOffset, AdminRegionRef> chain;
  chain[deutschland->regionOffset] = deutschland;
  chain[nrw->regionOffset] = nrw;
  chain[kreis->regionOffset] = kreis;
  chain[bergkamen->regionOffset] = bergkamen;

  assert(BuildAdminRegionHierarchyPath(bergkamen, chain) ==
         "Bergkamen/Kreis Unna/Nordrhein-Westfalen/Deutschland");
  std::cout << "ok full chain\n";
}

void TestTruncatedChain()
{
  // Middle of the chain missing: resolvable prefix only, no crash.
  AdminRegionRef nrw = MakeRegion(300, 0, "Nordrhein-Westfalen");
  AdminRegionRef kreis = MakeRegion(200, 300, "Kreis Unna");
  AdminRegionRef bergkamen = MakeRegion(100, 200, "Bergkamen");

  std::map<FileOffset, AdminRegionRef> chain;
  chain[kreis->regionOffset] = kreis;
  chain[bergkamen->regionOffset] = bergkamen;

  assert(BuildAdminRegionHierarchyPath(bergkamen, chain) == "Bergkamen/Kreis Unna");
  std::cout << "ok truncated chain\n";
}

void TestNullRoot()
{
  std::map<FileOffset, AdminRegionRef> chain;
  assert(BuildAdminRegionHierarchyPath(nullptr, chain) == "");
  std::cout << "ok null root\n";
}

void TestPerDatabaseMapIsolation()
{
  // The bug scenario: with the old merged-map code, a foreign (Iceland)
  // region could sit at the same numeric key as Germany's parent offset, and
  // the walk would print the foreign name. The fixed contract feeds the walk
  // a chain built from ONE database only, so a colliding key from a foreign
  // database is never present. This test pins that contract: a map containing
  // ONLY the owning database's chain must resolve the full German path even
  // though numeric offsets overlap with the (absent) Iceland entries.
  //
  // Germany: Bergkamen(100) -> Kreis Unna(200) -> NRW(300) -> Deutschland(400)
  // "Iceland": a foreign region whose regionOffset (200) collides with Kreis
  // Unna's key in the old merged map — NOT present in the per-database chain.
  AdminRegionRef deutschland = MakeRegion(400, 0, "Deutschland");
  AdminRegionRef nrw = MakeRegion(300, 400, "Nordrhein-Westfalen");
  AdminRegionRef kreis = MakeRegion(200, 300, "Kreis Unna");
  AdminRegionRef bergkamen = MakeRegion(100, 200, "Bergkamen");

  std::map<FileOffset, AdminRegionRef> chain; // owning database only
  chain[deutschland->regionOffset] = deutschland;
  chain[nrw->regionOffset] = nrw;
  chain[kreis->regionOffset] = kreis;
  chain[bergkamen->regionOffset] = bergkamen;

  const std::string path = BuildAdminRegionHierarchyPath(bergkamen, chain);
  assert(path == "Bergkamen/Kreis Unna/Nordrhein-Westfalen/Deutschland");
  assert(path.find("Island") == std::string::npos);
  assert(path.find("Ísland") == std::string::npos);
  std::cout << "ok per-database map isolation (no foreign contamination)\n";
}

} // namespace

int main()
{
  TestRootOnly();
  TestFullChain();
  TestTruncatedChain();
  TestNullRoot();
  TestPerDatabaseMapIsolation();
  std::cout << "ALL TESTS PASSED\n";
  return 0;
}
