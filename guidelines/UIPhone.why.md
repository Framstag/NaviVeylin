# UIPhone — why (rationale)

The rationale layer of `guidelines/UIPhone.md`: the measurements a bound was arrived at, the folds that
produced a rule, the rejected alternatives and the history. Read beside the section its `## §N` anchor
names; it is not a document a route names.

## §12 The empty-group and management deviations

A group holding no favorites is listed on the phone's grid (that is how a freshly created group appears,
and the phone is where it can be filled) and hidden on the car's group level, because no car affordance can
move a favorite into a group, so a car row for it would be a dead end (spec `auto-favorites` — A group
holding no favorites is not listed). The phone half is asserted by `FavoritesViewModelTest` (`addGroup
creates group and shows snackbar`), the car half by `FavoritesScreenTest.anEmptyGroupIsNotListedOnTheCar`.
The group order and the starred order need one more thing than the favorite order to be shared at all: the
car reads their sequence from the provider's **order channels** (`AutoFavoritesProvider.groupOrder`,
`AutoFavoritesProvider.starredOrder`), not from the group map's iteration order, because a `StateFlow`
drops an emission equal to its current value while `Map` equality ignores order.

## §18 Why the car shows a hint and not a shade notice

Car hosts never show turn-by-turn navigation notifications in their Notification Center, by design, so the
notification reaches the car as a turn-by-turn hint in the rail widget. While free driving the app is not
the active navigation app, so the host suppresses turn hints and `NavigationManager.updateTrip` rejects
trip updates — a platform constraint, not a defect. On Android Automotive OS the notification needs a car
channel of at least `IMPORTANCE_DEFAULT`, because the platform does not represent low-importance
foreground-service notifications at all.
