# NaviVeylin — Release Notes

## 2026-10-10-1

### Routing & Turn-by-Turn

- **Car hint content** — Car hint content.
  <!-- cap: auto-navigation-hints#Car hint content -->
- **A roundabout exit is identified by** — A roundabout exit is identified by its ordinal.
  <!-- cap: auto/navigation-view#A roundabout exit is identified by its ordinal -->
- **Current and next step in host** — Current and next step in host instruction panel.
  <!-- cap: auto/navigation-view#Current and next step in host instruction panel -->
- **Navigation ends when the car session** — Navigation ends when the car session ends after arrival.
  <!-- cap: auto/navigation-view#Navigation ends when the car session ends after arrival -->
- **Next turn maneuver in host instruction** — Next turn maneuver in host instruction panel.
  <!-- cap: auto/navigation-view#Next turn maneuver in host instruction panel -->
- **Reroute keeps the navigation view live** — Reroute keeps the navigation view live under the notice.
  <!-- cap: auto/navigation-view#Reroute keeps the navigation view live under the notice -->
- **Route description screen** — Route description screen.
  <!-- cap: auto/navigation-view#Route description screen -->
- **Current road info shown in free** — Current road info shown in free driving (no active route).
  <!-- cap: current-road-info#Current road info shown in free driving (no active route) -->
- **One free driving road speed status** — One free-driving road/speed status feeds every surface and the notification.
  <!-- cap: current-road-info#One free-driving road/speed status feeds every surface and the notification -->
- **Free driving content** — Free-driving content.
  <!-- cap: navigation-ongoing-notification#Free-driving content -->
- **Free driving content comes from the** — Free-driving content comes from the free-driving status.
  <!-- cap: navigation-ongoing-notification#Free-driving content comes from the free-driving status -->
- **Process survival during background driving** — Process survival during background driving.
  <!-- cap: navigation-ongoing-notification#Process survival during background driving -->
- **Unknown free driving values leave no** — Unknown free-driving values leave no empty fragment.
  <!-- cap: navigation-ongoing-notification#Unknown free-driving values leave no empty fragment -->
- **Route description list** — Route description list.
  <!-- cap: navigation-status-details#Route description list -->
- **Route progress rings on the routing** — Route progress rings on the routing status row.
  <!-- cap: navigation-status-details#Route progress rings on the routing status row -->
- **Turn type icon** — Turn type icon.
  <!-- cap: next-turn-overlay#Turn type icon -->
- **Reroute calculation progress is exposed** — Reroute calculation progress is exposed.
  <!-- cap: rerouting-visual-feedback#Reroute calculation progress is exposed -->
- **Solid red tint on status card** — Solid red tint on status card during rerouting.
  <!-- cap: rerouting-visual-feedback#Solid red tint on status card during rerouting -->
- **Analysed segment is highlighted on the** — Analysed segment is highlighted on the map.
  <!-- cap: route-analysis#Analysed segment is highlighted on the map -->
- **Analysed step is locatable on the** — Analysed step is locatable on the map.
  <!-- cap: route-analysis#Analysed step is locatable on the map -->
- **An analysis is created only by** — An analysis is created only by the selector that owns it.
  <!-- cap: route-analysis#An analysis is created only by the selector that owns it -->
- **A step s values are its** — A step's values are its own, never a neighbour's.
  <!-- cap: route-analysis#A step's values are its own, never a neighbour's -->
- **Step values describe the step s** — Step values describe the step's own leg.
  <!-- cap: route-analysis#Step values describe the step's own leg -->
- **A superseded calculation never clears the** — A superseded calculation never clears the live calculation's state.
  <!-- cap: route-calculation-feedback#A superseded calculation never clears the live calculation's state -->
- **Cancelling a calculation aborts it and** — Cancelling a calculation aborts it and releases its resources.
  <!-- cap: route-calculation-feedback#Cancelling a calculation aborts it and releases its resources -->
- **Car wait notice while a route** — Car wait notice while a route is being calculated.
  <!-- cap: route-calculation-feedback#Car wait notice while a route is being calculated -->
- **In flight route calculation is part** — In-flight route calculation is part of the shared navigation state.
  <!-- cap: route-calculation-feedback#In-flight route calculation is part of the shared navigation state -->
- **Route calculation is measured without coordinates** — Route calculation is measured without coordinates.
  <!-- cap: route-calculation-feedback#Route calculation is measured without coordinates -->
- **Route calculation progress is exposed as** — Route calculation progress is exposed as a percentage.
  <!-- cap: route-calculation-feedback#Route calculation progress is exposed as a percentage -->
- **The car notice never outlives its** — The car notice never outlives its calculation.
  <!-- cap: route-calculation-feedback#The car notice never outlives its calculation -->
- **Calculate route** — Calculate route.
  <!-- cap: route-panel-ui#Calculate route -->
- **Vehicle selector** — Vehicle selector.
  <!-- cap: route-panel-ui#Vehicle selector -->
- **Planning card content and its pinned** — Planning card content and its pinned actions.
  <!-- cap: route-planning-session#Planning card content and its pinned actions -->
- **Session lifetime and its only exits** — Session lifetime and its only exits.
  <!-- cap: route-planning-session#Session lifetime and its only exits -->
- **A failed route calculation reports a** — A failed route calculation reports a reason.
  <!-- cap: routing-failure-reporting#A failed route calculation reports a reason -->
- **A reason that concerns one end** — A reason that concerns one end of the route identifies that end.
  <!-- cap: routing-failure-reporting#A reason that concerns one end of the route identifies that end -->
- **A reason whose cause the user** — A reason whose cause the user can act on offers the action.
  <!-- cap: routing-failure-reporting#A reason whose cause the user can act on offers the action -->
- **Each reason is worded by the** — Each reason is worded by the surface.
  <!-- cap: routing-failure-reporting#Each reason is worded by the surface -->
- **A missing or unusable maximum speed** — A missing or unusable maximum speed falls back to the built-in profile.
  <!-- cap: routing-max-speed#A missing or unusable maximum speed falls back to the built-in profile -->
- **A per vehicle maximum speed is** — A per-vehicle maximum speed is a persisted setting.
  <!-- cap: routing-max-speed#A per-vehicle maximum speed is a persisted setting -->
- **A speed change does not restart** — A speed change does not restart a calculation.
  <!-- cap: routing-max-speed#A speed change does not restart a calculation -->
- **Every calculation path carries the vehicle** — Every calculation path carries the vehicle's maximum speed.
  <!-- cap: routing-max-speed#Every calculation path carries the vehicle's maximum speed -->
- **The setting is shared by the** — The setting is shared by the phone and the car.
  <!-- cap: routing-max-speed#The setting is shared by the phone and the car -->
- **Vehicle semantics of the maximum speed** — Vehicle semantics of the maximum speed.
  <!-- cap: routing-max-speed#Vehicle semantics of the maximum speed -->

### Search & Destinations

- **Car and phone region scoping parity** — Car and phone region scoping parity.
  <!-- cap: auto-search#Car and phone region scoping parity -->
- **Car region scope follows the car** — Car region scope follows the car's movement.
  <!-- cap: auto-search#Car region scope follows the car's movement -->
- **Search as you type with debounce** — Search-as-you-type with debounce.
  <!-- cap: auto-search#Search-as-you-type with debounce -->
- **Search scoped by the car position** — Search scoped by the car position's admin region.
  <!-- cap: auto-search#Search scoped by the car position's admin region -->
- **The car search screen stops its** — The car search screen stops its own search when it leaves its started period.
  <!-- cap: auto-search#The car search screen stops its own search when it leaves its started period -->
- **Transliterated name matching parity with phone** — Transliterated name matching parity with phone search.
  <!-- cap: auto-search#Transliterated name matching parity with phone search -->
- **A repeated query text still runs** — A repeated query text still runs the search.
  <!-- cap: location-search#A repeated query text still runs the search -->
- **Search suggestions follow the newest query** — Search suggestions follow the newest query text.
  <!-- cap: location-search#Search suggestions follow the newest query text -->
- **A POI search keeps its own** — A POI search keeps its own slot and is abandoned when its view is left.
  <!-- cap: poi-search#A POI search keeps its own slot and is abandoned when its view is left -->
- **Embedded result map fit never clips** — Embedded result map fit never clips a result.
  <!-- cap: poi-search#Embedded result map fit never clips a result -->
- **Contacts mode** — Contacts mode.
  <!-- cap: search-dialog#Contacts mode -->
- **Leaving Places mode abandons its search** — Leaving Places mode abandons its search.
  <!-- cap: search-dialog#Leaving Places mode abandons its search -->

### Favorites & Saved Places

- **AA place list follows the stored** — AA place list follows the stored group order.
  <!-- cap: auto-favorites#AA place list follows the stored group order -->
- **Favorites grouped by category** — Favorites grouped by category.
  <!-- cap: auto-favorites#Favorites grouped by category -->
- **ListTemplate displays favorites** — ListTemplate displays favorites.
  <!-- cap: auto-favorites#ListTemplate displays favorites -->
- **Starred favorites render as one ordered** — Starred favorites render as one ordered list.
  <!-- cap: auto-favorites#Starred favorites render as one ordered list -->
- **The all favorites mode lists groups** — The all-favorites mode lists groups first.
  <!-- cap: auto-favorites#The all-favorites mode lists groups first -->
- **The favourites list distinguishes a loading** — The favourites list distinguishes a loading store from an empty one.
  <!-- cap: auto-favorites#The favourites list distinguishes a loading store from an empty one -->
- **Bounding box zoom calculation** — Bounding box zoom calculation.
  <!-- cap: fav-auto-zoom#Bounding box zoom calculation -->
- **Coordinate entry accepts either decimal separator** — Coordinate entry accepts either decimal separator.
  <!-- cap: fav-management-ui#Coordinate entry accepts either decimal separator -->
- **Favorite row offers a Move to** — Favorite row offers a "Move to group" action.
  <!-- cap: fav-management-ui#Favorite row offers a "Move to group" action -->
- **Favorites management stays on the phone** — Favorites management stays on the phone.
  <!-- cap: fav-management-ui#Favorites management stays on the phone -->
- **A favorite can be moved from** — A favorite can be moved from one group into another.
  <!-- cap: fav-ordering#A favorite can be moved from one group into another -->
- **Favorite position within a group is** — Favorite position within a group is user-defined.
  <!-- cap: fav-ordering#Favorite position within a group is user-defined -->
- **Move to group semantics for invalid** — Move-to-group semantics for invalid or refused targets.
  <!-- cap: fav-ordering#Move-to-group semantics for invalid or refused targets -->
- **Reordering persists with a single write** — Reordering persists with a single write per committed move.
  <!-- cap: fav-ordering#Reordering persists with a single write per committed move -->
- **Chip bar follows the stored starred** — Chip bar follows the stored starred order.
  <!-- cap: fav-starred-chip-bar#Chip bar follows the stored starred order -->
- **Chip bar supports drag and drop** — Chip bar supports drag-and-drop reordering.
  <!-- cap: fav-starred-chip-bar#Chip bar supports drag-and-drop reordering -->
- **Starred favorites show filled star icon** — Starred favorites show filled star icon.
  <!-- cap: fav-star#Starred favorites show filled star icon -->
- **Starring enters the favorite at the** — Starring enters the favorite at the end of the starred order.
  <!-- cap: fav-star#Starring enters the favorite at the end of the starred order -->
- **Move semantics for invalid or out** — Move semantics for invalid or out-of-range targets.
  <!-- cap: starred-ordering#Move semantics for invalid or out-of-range targets -->
- **Order is recovered from a file** — Order is recovered from a file that carries none.
  <!-- cap: starred-ordering#Order is recovered from a file that carries none -->
- **Other operations leave the starred order** — Other operations leave the starred order alone.
  <!-- cap: starred-ordering#Other operations leave the starred order alone -->
- **Reordering persists with a single write** — Reordering persists with a single write per committed move.
  <!-- cap: starred-ordering#Reordering persists with a single write per committed move -->
- **Starred order is user defined and** — Starred order is user-defined and spans all groups.
  <!-- cap: starred-ordering#Starred order is user-defined and spans all groups -->
- **Starring and unstarring change the starred** — Starring and unstarring change the starred order.
  <!-- cap: starred-ordering#Starring and unstarring change the starred order -->

### Offline Maps & Data

- **Download and extract basemap archive** — Download and extract basemap archive.
  <!-- cap: basemap-download#Download and extract basemap archive -->
- **Provide basemap download update control** — Provide basemap download/update control.
  <!-- cap: basemap-ui#Provide basemap download/update control -->
- **Basemap section in map manager screen** — Basemap section in map manager screen.
  <!-- cap: map-download-ui#Basemap section in map manager screen -->
- **Installed maps name their source** — Installed maps name their source.
  <!-- cap: map-download-ui#Installed maps name their source -->
- **Provider selection and refresh** — Provider selection and refresh.
  <!-- cap: map-download-ui#Provider selection and refresh -->
- **Section ordering** — Section ordering.
  <!-- cap: map-download-ui#Section ordering -->
- **An unencrypted repository source is marked** — An unencrypted repository source is marked as such.
  <!-- cap: map-source-selection#An unencrypted repository source is marked as such -->
- **A repository base URL is normalised** — A repository base URL is normalised before it is used.
  <!-- cap: map-source-selection#A repository base URL is normalised before it is used -->
- **A source switch unregisters reloads and** — A source switch unregisters, reloads and records what it deletes.
  <!-- cap: map-source-selection#A source switch unregisters, reloads and records what it deletes -->
- **Base URL of the repository source** — Base URL of the repository source is validated before use.
  <!-- cap: map-source-selection#Base URL of the repository source is validated before use -->
- **Installed map data records the source** — Installed map data records the source it came from.
  <!-- cap: map-source-selection#Installed map data records the source it came from -->
- **Map source is a registered persisted** — Map source is a registered, persisted choice.
  <!-- cap: map-source-selection#Map source is a registered, persisted choice -->
- **Switching source is gated by a** — Switching source is gated by a confirmation naming what will be deleted.
  <!-- cap: map-source-selection#Switching source is gated by a confirmation naming what will be deleted -->
- **The repository URL field is presented** — The repository URL field is presented as URL input with its format shown.
  <!-- cap: map-source-selection#The repository URL field is presented as URL input with its format shown -->
- **An installed repository database is compared** — An installed repository database is compared with its own version slot.
  <!-- cap: map-updates#An installed repository database is compared with its own version slot -->
- **An installed update is rendered without** — An installed update is rendered without an app restart.
  <!-- cap: map-updates#An installed update is rendered without an app restart -->
- **An update replaces the installation only** — An update replaces the installation only when it is complete.
  <!-- cap: map-updates#An update replaces the installation only when it is complete -->
- **Data left by an interrupted update** — Data left by an interrupted update is recognised, not mistaken for a map.
  <!-- cap: map-updates#Data left by an interrupted update is recognised, not mistaken for a map -->
- **Each compared item carries one update** — Each compared item carries one update state.
  <!-- cap: map-updates#Each compared item carries one update state -->
- **Leftover staging data is removed on** — Leftover staging data is removed on the app-start path only.
  <!-- cap: map-updates#Leftover staging data is removed on the app-start path only -->
- **The app checks for newer repository** — The app checks for newer repository map data once a day.
  <!-- cap: map-updates#The app checks for newer repository map data once a day -->
- **The check covers the active repository** — The check covers the active repository source only.
  <!-- cap: map-updates#The check covers the active repository source only -->
- **The check result survives a restart** — The check result survives a restart.
  <!-- cap: map-updates#The check result survives a restart -->
- **The updates section belongs to the** — The updates section belongs to the repository source.
  <!-- cap: map-updates#The updates section belongs to the repository source -->
- **The updates section lists what can** — The updates section lists what can be updated.
  <!-- cap: map-updates#The updates section lists what can be updated -->
- **The updates surface is the phone** — The updates surface is the phone's.
  <!-- cap: map-updates#The updates surface is the phone's -->
- **The user can run the check** — The user can run the check on demand.
  <!-- cap: map-updates#The user can run the check on demand -->
- **Updating every listed item is a** — Updating every listed item is a queue.
  <!-- cap: map-updates#Updating every listed item is a queue -->

### Map Appearance

- **Car surface follows the shared preference** — Car surface follows the shared preference without a control.
  <!-- cap: map-styles#Car surface follows the shared preference without a control -->
- **Icon versus symbol preference is a** — Icon-versus-symbol preference is a persisted phone setting.
  <!-- cap: map-styles#Icon-versus-symbol preference is a persisted phone setting -->
- **Phone control for the preference** — Phone control for the preference.
  <!-- cap: map-styles#Phone control for the preference -->
- **Preference decides which rendering is drawn** — Preference decides which rendering is drawn when both exist.
  <!-- cap: map-styles#Preference decides which rendering is drawn when both exist -->
- **Preference is applied at start and** — Preference is applied at start and on change.
  <!-- cap: map-styles#Preference is applied at start and on change -->
- **Style load failure is visible on** — Style load failure is visible on both surfaces.
  <!-- cap: map-styles#Style load failure is visible on both surfaces -->

### Map Interaction & Controls

- **Auto zoom speed source per surface** — Auto-zoom speed source per surface.
  <!-- cap: auto-speed-zoom#Auto-zoom speed source per surface -->
- **Entry transition is bounded in time** — Entry transition is bounded in time and render requests.
  <!-- cap: auto-speed-zoom#Entry transition is bounded in time and render requests -->
- **Compass colors follow the resolved day** — Compass colors follow the resolved day/night presentation.
  <!-- cap: compass-button#Compass colors follow the resolved day/night presentation -->
- **Compass needle stroke is density independent** — Compass needle stroke is density-independent.
  <!-- cap: compass-button#Compass needle stroke is density-independent -->
- **A slider change persists immediately** — A slider change persists immediately.
  <!-- cap: location-options-ui#A slider change persists immediately -->
- **Sheet reachable during navigation** — Sheet reachable during navigation.
  <!-- cap: location-options-ui#Sheet reachable during navigation -->
- **The car exposes no maximum speed** — The car exposes no maximum speed control.
  <!-- cap: location-options-ui#The car exposes no maximum speed control -->
- **Vehicle maximum speed sliders in the** — Vehicle maximum speed sliders in the options sheet.
  <!-- cap: location-options-ui#Vehicle maximum speed sliders in the options sheet -->
- **Browse re center** — Browse re-center.
  <!-- cap: map-modes#Browse re-center -->
- **The render pin lives only as** — The render pin lives only as long as the walk.
  <!-- cap: smooth-zoom#The render pin lives only as long as the walk -->
- **Zoom animation never exposes uncovered map** — Zoom animation never exposes uncovered map area.
  <!-- cap: smooth-zoom#Zoom animation never exposes uncovered map area -->

### Driving, Position & Speed

- **Correction easing** — Correction easing.
  <!-- cap: auto-smooth-follow#Correction easing -->
- **Shared follow bounds on the car** — Shared follow bounds on the car surface.
  <!-- cap: auto-smooth-follow#Shared follow bounds on the car surface -->
- **A frame from the previous orientation** — A frame from the previous orientation yields a zero offset instead of an error.
  <!-- cap: smooth-follow#A frame from the previous orientation yields a zero offset instead of an error -->
- **Correction easing** — Correction easing.
  <!-- cap: smooth-follow#Correction easing -->
- **Follow frame refresh before the overrun** — Follow frame refresh before the overrun margin is exhausted.
  <!-- cap: smooth-follow#Follow frame refresh before the overrun margin is exhausted -->
- **Non negative overrun margin** — Non-negative overrun margin.
  <!-- cap: smooth-follow#Non-negative overrun margin -->
- **Single resolved anchor across render blit** — Single resolved anchor across render, blit and marker.
  <!-- cap: smooth-follow#Single resolved anchor across render, blit and marker -->
- **Stop decision against a current base** — Stop decision against a current base.
  <!-- cap: smooth-follow#Stop decision against a current base -->
- **Vehicle position anchor in follow mode** — Vehicle position anchor in follow mode.
  <!-- cap: smooth-follow#Vehicle position anchor in follow mode -->

### Sharing & Handoff

- **Deep link activity forwards to phone** — Deep-link activity forwards to phone app.
  <!-- cap: auto-deep-links#Deep-link activity forwards to phone app -->
- **Deep link parsed into a destination** — Deep link parsed into a destination.
  <!-- cap: auto-deep-links#Deep link parsed into a destination -->
- **A map link with no coordinate** — A map link with no coordinate falls back to its place name.
  <!-- cap: share/location-receiving#A map link with no coordinate falls back to its place name -->
- **An unreadable shared map link is** — An unreadable shared map link is reported, never searched.
  <!-- cap: share/location-receiving#An unreadable shared map link is reported, never searched -->
- **Shared address triggers search** — Shared address triggers search.
  <!-- cap: share/location-receiving#Shared address triggers search -->
- **Shared coordinate disambiguated through candidate picker** — Shared coordinate disambiguated through candidate picker.
  <!-- cap: share/location-receiving#Shared coordinate disambiguated through candidate picker -->
- **Shared map link is read for** — Shared map link is read for its coordinate.
  <!-- cap: share/location-receiving#Shared map link is read for its coordinate -->

### Device Fit & Accessibility

- **A failure from native code is** — A failure from native code is worded by the surface.
  <!-- cap: i18n-l10n#A failure from native code is worded by the surface -->
- **RTL readiness** — RTL readiness.
  <!-- cap: i18n-l10n#RTL readiness -->
- **All native instruction kinds are covered** — All native instruction kinds are covered.
  <!-- cap: turn-instruction-localization#All native instruction kinds are covered -->

