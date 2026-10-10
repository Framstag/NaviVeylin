# Proposal

## Why

The car favourites path does not scale, and part of the reason is a platform limit the app never
consults. The all-favourites `ListTemplate` presents every group as a section and every favourite as a
row in one list; the Car App Library contract for that template says "The number of items in the
`ItemList` should be smaller or equal than the limit provided by
`ConstraintManager#CONTENT_LIMIT_TYPE_LIST`. **The host will ignore any items over that limit**", and the
host chooses that number (the AAOS emulator answers 6). Two groups of five favourites already lose rows
with no signal, and nothing in this repository reads the limit today. The phone, by contrast, has
browsed favourites as a group grid that opens a group's detail list since `group-grid-display`, so the
car's flat list is the outlier, not the model. Owner request, 2026-10-10: with many groups and many
favourites the car list "does not scale — there must be an intermediate dialog for groups".

## What Changes

- **A group level on the car's all-favourites path.** "All favorites" opens a list with one row per
  group, then a group opens its own favourites. The group level is unconditional: the hierarchy does
  not depend on how much the store holds, so one scenario describes it on every host. Group rows are
  ordered by the stored group order (`AutoFavoritesProvider.groupOrder`) and show the shared
  `plurals/favorite_count` wording the phone group card uses; a group's rows keep the stored favourite
  order; both levels stay read-only and selecting a favourite starts the destination-picker flow
  exactly as today.
- **"Starred favorites" keeps its own top-level entry** and its current shape: one flat list, no group
  headers, in the stored starred order. It gains only the row capacity below.
- **Per-screen row capacity instead of silent truncation.** A car list screen asks the host for its
  limit and presents at most that many rows, ending with a clickable `More` row that opens the next
  page. This applies to the group list, a group's favourites and today's starred list. Every item stays
  reachable and nothing disappears without a signal. On a host below car API 2 the limit cannot be
  queried (`ConstraintManager` is `@RequiresCarApi(2)` while `app/src/main/AndroidManifest.xml:122`
  declares `minCarApiLevel = 1`); whether that falls back to a conservative fixed cap or to no cap is a
  design decision, as is the exact `More`-row wording and whether a page screen titles itself with its
  position.
- **Empty groups stay hidden** on the car, as today: no car affordance can move a favourite into one.
  The phone grid keeps showing them, and `UI.md` §1/§6 records the deviation with its reason.
- **Guideline and spec corrections that this change owes.** `guidelines/UI.md` §3 currently claims
  "`ListTemplate` scrolls and the host **pages** when the list exceeds one page", which contradicts the
  library's own build contract quoted above; the section is corrected to the measured behaviour.
  `openspec/specs/auto-favorites/spec.md` still names `PlaceListTemplate`, which this screen
  deliberately does not use (a place list requires a distance span on every row), and its
  grouped-by-category requirement carries a "colour indicator" scenario that no `ListTemplate` row can
  satisfy and the screen never implemented.
- **The host limit is measured, not assumed.** The real `CONTENT_LIMIT_TYPE_LIST` value on the AAOS
  emulator and on a projection host, and whether section headers count toward it, is evidence this
  change produces before it fixes a threshold.

## Capabilities

### New Capabilities

- `car-list-row-limit`: how a car list screen discovers the host's per-screen row capacity, never
  presents more rows than the host will display, and keeps every item beyond that capacity reachable
  through a `More` row — the contract the favourites group list, a group's rows, the starred list and
  future car result lists share.

### Modified Capabilities

- `auto-favorites`: the all-favourites mode gains a group level (a group row opens that group's
  favourites, read-only, in the stored order); the requirement's `PlaceListTemplate` wording is
  corrected to `ListTemplate`; the "colour indicator" scenario is restated or dropped to match what a
  car row can render.
- `fav-management-ui`: the "Favorites management stays on the phone" requirement's browsing clause
  ("Phone and car surfaces SHALL keep their existing labels and hierarchy for browsing") is restated
  so the hierarchy it protects includes the group level. The rule that the car offers no favourites-
  management action is unchanged.

## Impact

Affected modules and files:

- `:auto` — `auto/src/main/java/com/naviveylin/auto/FavoritesScreen.kt` (group level, the group's
  favourites screen, the `More` row), `FavoritesScreenMapper.kt` and a new pager/group mapper for the
  pure, testable parts, `FavoritesScreenObservations.kt` where the second level needs the same state,
  and the screen-stack pushes through `armScreenPush` with `enableBackNavigation()` for the way back.
  The map menu entries (`MapTemplateFactory.buildMenuContent`) keep their wording; "All favorites" now
  means the entry to the browsing path.
- `auto/src/main/res/values/strings.xml` and `auto/src/main/res/values-de/strings.xml` — the new group
  and `More` wording; the count reuses the `plurals/favorite_count` wording the phone group card
  already uses, so the two surfaces stay at parity by construction.
- Tests — `auto/src/test/java/com/naviveylin/auto/FavoritesScreenTest.kt`, `FavoritesScreenMapperTest.kt`,
  `FavoritesScreenObservationsTest.kt`, plus new cases for the group level, the paging row and the
  capacity cap. Each delta scenario needs one case per surface it names, or an explicit
  device-gated note.
- Android components: `androidx.car.app.constraints.ConstraintManager` (already on the classpath via
  `androidx.car.app:app`), the `ScreenManager` stack for the group and page screens, and the
  `ListTemplate`/`ItemList` templates. No manifest change, no new dependency, no new Gradle
  configuration. No native/JNI change: no submodule patch and no bridge-module override.
- Guidelines: `guidelines/UI.md` §1 and §6 (parity rows and the deliberate-deviation reasoning),
  §3 (the corrected platform claim), and `guidelines/Design.md` §8 for the cross-variant wiring of the
  new screens.
- On-device evidence: `device-check`/`pixel-check` on the AAOS emulator and a projection host — the
  measured content limit, the group level rendering, and the `More` row reaching the next page. The
  stationary emulator can settle all three; no driving-dependent property is claimed.
- Scope: car only, shared by Android Auto and Android Automotive OS through one `:auto` screen. The
  phone grid, group detail sheet and reordering are untouched.
- Additive, not breaking: the store, its order channels and the persisted JSON are unchanged, and only
  the car browsing path gains a level. Rollback is a revert of the commit — no data migration and no
  native artefact.
