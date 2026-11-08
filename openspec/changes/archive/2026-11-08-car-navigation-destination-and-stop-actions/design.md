# Design — two actions in the strip the zoom buttons already use

## Where they go

The navigation template has two action surfaces: `actionStrip` (built at the base in `NavigationScreen.kt:575-577`
as two zoom actions) and `mapActionStrip` (`:574` at the base, the route description). The four actions are
composed by the pure factory `NavigationScreenActions.navigationActionStrip`, so the strip's shape is host-testable;
`NavigationScreen.buildTemplate` passes the two resolved titles and the four callbacks. The car's place for the
two actions is the action strip — the owner decision put them there: `mapActionStrip` is already spoken for (the
route description), and the header carries the trip text, so the strip is the only surface with room.

## Order

The order is the owner decision's — zoom first (the most frequent action while driving), then *change
destination*, and the destructive *end navigation* last. The
two new actions are `Action.Builder().setTitle(String)` + `setOnClickListener` — the shape
`NavigationScreenActions.zoomInAction` already uses, no new API — and each title is a resource (spec: i18n-l10n;
the i18n build gate refuses a literal at `.setTitle(`), resolved by the screen.

## Ending the session

`onNavigationEnded()` is what the session's own state collector already calls when `isNavigating` goes false, and
`showRootScreen()` is already bound. The action therefore does not invent a path: it calls
`navigationViewModel.stopNavigation()` — the same call `NavigationScreen`'s back callback makes
(`NavigationScreen.navigationBackBehavior`) — and the collector's `onNavigationEnded()` + `Pop to RootScreen`
follows.

## Reversal

Remove the two `addAction` lines from `navigationActionStrip` and the two builders; the strip is byte-identical to
the base's, and `NavigationScreen` builds its inline two-zoom strip again.

## Guideline

`guidelines/UICar.md` §3 "Car-host template constraints (platform knowledge)" owns the car-host template
constraints and owes no edit here: its no-custom-titles rule is scoped to `ListTemplate.Builder.addAction`, and the
navigation `actionStrip` already carries titled actions (zoom). The two new titles are strings in
`values/strings.xml` and `values-de/strings.xml` because the i18n gate refuses a literal at `.setTitle(`.

## What this does not decide

Whether the destination change should reuse the car's `SearchScreen` (the `suchen` row of the root list) or offer a
short list of favourites first. Both are a decision for the change, not for this item; the search surface is what
the root list already reaches, so that is the default until someone measures the alternative.

## Open decisions

none
