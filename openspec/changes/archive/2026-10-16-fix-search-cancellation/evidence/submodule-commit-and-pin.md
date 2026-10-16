# Task 2.10 — the submodule commit and the gitlink bump

Landed 2026-10-15 while the worktree still carried another builder's uncommitted routing work; the pin
followed that work's own commit later the same day, so the final state has the submodule tree clean.

```
submodule   dff960e34  "routing: give a failed route calculation a reason, and attach an endpoint…"  (not this change)
           9725fe9a6  "client-java: carry the search request lifecycle in every pass"   (this change, follow-up)
           c8e340a87  "client-java: cancel a search by generation, one slot per surface"  (this change)
           e303556e2  the pushed tip (#4's label word-wrapping work)
parent      3cc7a5d    "submodule: routing failures carry a reason and an attachable endpoint"  (gitlink -> dff960e34)
           3485275    "submodule: carry the search request lifecycle in every pass"
           04dce74    "submodule: libosmscout carries the search request lifecycle"
           a13be71    #4's docs commit
```

Submodule 3 commits ahead of `origin/naviveylin-local`, parent 12 ahead of `upstream/main`, everything local:
the push is the operator's call (this change did not push).

## The first commit was incomplete, and how that was caught

the `label-word-wrapping` session (`NaviVeylin#4`) asked whether two added `generation` lines still uncommitted
in the shared file were mine. They were — five hunks were, in fact. The splitter classified hunks by marker
*words* in the changed lines, so it dropped every hunk that carries only a search **signature** line:

| dropped hunk | consequence had it stayed dropped |
|---|---|
| `DoSearchLocations` forward declaration (the committed file still read `jboolean cancel);`) | redeclaration mismatch — the commit did not compile |
| `searchPOIsByTypes` definition's `jlong generation` | the JNI entry point kept the old 5-argument signature while the Java override declared 6 |
| `param.SetBreaker(breaker);` in the structured form pass | task 2.7's form-pass abort was absent from the commit |
| the two free-text per-database polls | task 2.7's free-text abort was absent from the commit |

The second splitter pass checks the routing patterns *first* (the routing work edits the same
`calculateRouteWithObjects…` functions) and then looks for search code or a search signature line. It reports
31 leftover hunks as **mine=5, theirs=26, neither=0**, and after applying those five the committed search lines
are line-for-line identical to the worktree's: `jlong generation` 9/9, `SetBreaker(breaker)` 4/4,
`searchBreakers` 15/15, `search: slot=` 2/2, `SlotOf` 2/2. The remaining uncommitted diff of that file is
**100 % routing** (26 hunks, `mine=0`), which also means the routing change can now stage the file whole.

## The commit's own content — my seven files, nobody else's

```
JavaScout/…/SearchOverlay.java                      16 +-
Tests/CMakeLists.txt                                7 +
Tests/meson.build                                   9 +
Tests/src/SearchBreakerTest.cpp                   164 +++++
libosmscout-client-java/java/…/OSMScoutClient.java  91 ++--
libosmscout-client-java/src/OSMScoutClient.cpp    272 ++++++----
libosmscout-client-java/src/search_breaker.h      192 +++++
7 files changed, 676 insertions(+), 75 deletions(-)
```

## How a shared file was committed without sweeping the other work

`libosmscout-client-java/src/OSMScoutClient.cpp` holds both this change's search work and the other
builder's routing work, so `git add <path>` could not be used. The diff of that one file (76 hunks with
context) was split by content markers (`build/tmp/split_search_hunks.py`): **15 hunks mine, 30 theirs, 1
mixed** — the include block, which added their `#include "route_failure_kind.h"` and my
`#include "search_breaker.h"` together. That hunk was rewritten for staging to add only mine, the 16 hunks
were applied to the index with `git apply --cached`, and the staged blob was verified before committing:

| marker | in the staged blob |
|---|---|
| `search_breaker.h`, `SearchBreakerAdapter`, `searchBreakers`, `beginSearch`, `cancelSearch`, `search: slot=`, `SlotOf` | present |
| `route_failure_kind`, `nearestRoad`, `reachedPct`, `CalculateRouteWithObjects`, `KIND_` | **absent** |
| `RoutePosition` | 11 — the same count as in `HEAD`, i.e. pre-existing, none added by this staging |

Their nine dirty entries (`RouteCallback.java`, the routing services, `route_failure_kind.h`, the routing
hunks of the shared file) are **still uncommitted** after the commit, as intended.

## Verification, and the two clauses that cannot hold in this worktree

- `git submodule status` shows the new SHA from the gitlink, no `+` ✔.
- The app builds against the new pin: `:app` search suites (phone places 6/0, phone POI 3/0, fake 4/0)
  `BUILD SUCCESSFUL` after the bump ✔.
- The patch applied cleanly **to the index** — i.e. against the committed baseline without the other
  builder's hunks — which is the contextual-consistency proof that my hunks are self-contained ✔.
- *Not* verified as such, but now covered: a clean-checkout compile of the commit. At the time this was
  written the worktree build compiled the union (their C++ plus mine); after the follow-up commit the committed
  **search** lines equal the worktree's exactly (the table above), and when the routing half was committed the
  worktree became identical to the committed state — so the last successful `ninja -C build
  osmscout_client_java` ran on exactly the content that is now pinned. The routing hunks the first commit
  excluded are not this change's.
- No Gradle run was made for the follow-up: it touches only the C++ translation unit (the JNI entry points and
  the walks) and leaves the Java surface unchanged, and the tree's owner has asked for no Gradle builds while
  their clean gate runs.
- "The submodule tree is clean" **now holds in full**: `git -C app/src/main/cpp/libosmscout status --porcelain`
  is empty, `git submodule status` shows the pinned SHA with no `+`, and the parent's
  `app/src/main/cpp/libosmscout` entry is unmodified. It took the routing work's own commit to get there, which
  is why this change left that work alone rather than sweeping it.
