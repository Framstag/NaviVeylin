# Proposal

## Why

`DoSearchLocations` bounds the free-text search per source and, when an admin region is resolved, drops the
hits that lie outside that region's extent. Both rules are right on their own; together they shorten the
answer: the budget counts **collected** hits (`OSMScoutClient.cpp:4899-4901` at the pre-change tip
`5188f2138` of the submodule) and the geographic filter runs **after** the walk (`:5098-5117`), so a scoped
search that drops 30 of its 60 collected hits returns 30 candidates for a limit of 60 even though in-scope
hits may still lie behind the walk's stopping point (TODO §110, issue #19, found 2026-10-01 while
implementing `fix-cross-database-search-scope`).

The item waited on a native measurement of the walk's cost, assuming that counting only kept hits buys
unbounded index traversal. That assumption does not hold: `TextSearchIndex::Search` materializes a query's
complete hit set in one call, before the budget is consulted at all
(`libosmscout/src/osmscout/db/TextSearchIndex.cpp:134-170`), so the budget bounds the per-hit object loads that
follow it, not the traversal — which is why the measurement did not have to be taken (design: "Why this
option").

## What Changes

- The per-source budget counts the candidates the search **keeps**: a hit the active scope refuses does not
  spend it, so the walk continues until `limit` in-scope hits are collected or the index's hit set is
  exhausted — option 1 of the three candidates the item recorded.
- The scope's extent is derived **before** the database walk instead of on the way through it, because the
  budget has to know the scope while it counts and the database that resolved the region is not necessarily
  the first one walked.
- The rule is a pure, host-tested helper (`naviveylin::FreeTextBudget` in `search_scope.h`, next to
  `IsInsideGeoBox`), so the counting is decidable without a database or an index.
- The debug line that reports how much the scope filtered moves to the collection and keeps its wording and
  its coordinate-free shape.

Not in scope: the dedup against structured results (it still runs after the walk, so a scoped search can
still return fewer than `limit` candidates when the same objects were also found structurally — the delta's
requirement says "kept", not "published"); and the name-based region path, which derives no extent at all
today and is unchanged by this fix.

## Capabilities

### New Capabilities

None — this change corrects the counting rule of an existing requirement.

### Modified Capabilities

- `search-free-text`: the candidate-budget requirement gains the counting rule (kept candidates, not scanned
  hits).

## Impact

| Where | What changes |
|---|---|
| `app/src/main/cpp/libosmscout/libosmscout-client-java/src/search_scope.h` (submodule) | `FreeTextBudget`: the counting rule as a pure helper |
| `app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp` (submodule) | extent derived before the walk, budget replaces the size check, filter applied at collection |
| `app/src/main/cpp/libosmscout/Tests/src/SearchScopeTest.cpp` (submodule) | three cases for the counting rule |
| gitlink `app/src/main/cpp/libosmscout` | bump after the submodule commit on `naviveylin-local` |

**Native/JNI form: submodule patch, minimal and upstreamable** (`guidelines/Design.md` §5) — no new JNI
method, no Java-side change, no local override in `:osmscout-client-java`, and the JNI surface is unchanged.

Surface count: 1 requirement delta, 3 headless host cases, 1 helper and 1 call site —
`grep -rn "FreeTextBudget\|freeTextLimitReached" app/src/main/cpp/libosmscout/libosmscout-client-java/src
app/src/main/cpp/libosmscout/Tests/src` before the change finds `freeTextLimitReached` at two call sites and
no helper.

Rollback: revert the submodule commit and the gitlink bump. No persisted state, no API shape, no Java class
is involved.

Specs changed by this proposal: `search-free-text` (delta in this change).

Guidelines: `guidelines/Design.md` §5 (one submodule commit plus the pin bump). No guideline text changes:
the counting rule belongs to the capability, and the reason the walk stays affordable is a code comment next
to the budget.
