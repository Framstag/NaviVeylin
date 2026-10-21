# NaviVeylin — Features

## Routing & Turn-by-Turn

_Phone and car_

- **Degenerate route geometry degrades safely** — Degenerate route geometry degrades safely; Route fit is suppressed while driving; Route overview fits the route bounding box; Calculate route; Cancel route calculation; Clear route; Route button on location details sheet; Route panel with start and destination fields; Session overlay dismissal ends the session;.
  <!-- cap: route-map-overview#Degenerate route geometry degrades safely -->
  <!-- cap: route-map-overview#Route fit is suppressed while driving -->
  <!-- cap: route-map-overview#Route overview fits the route bounding box -->
  <!-- cap: route-panel-ui#Calculate route -->
  <!-- cap: route-panel-ui#Cancel route calculation -->
  <!-- cap: route-panel-ui#Clear route -->
  <!-- cap: route-panel-ui#Route button on location details sheet -->
  <!-- cap: route-panel-ui#Route panel with start and destination fields -->
  <!-- cap: route-panel-ui#Session overlay dismissal ends the session -->
  <!-- cap: route-panel-ui#Start Navigation button in route panel -->
  <!-- cap: route-panel-ui#Stop navigation hides route from map -->
  <!-- cap: route-panel-ui#Swap button position -->
  <!-- cap: route-panel-ui#Swap start and destination -->
  <!-- cap: route-panel-ui#Turn-by-turn instruction list -->
  <!-- cap: route-panel-ui#Vehicle selector -->
  <!-- cap: route-summary-dialog#Active navigation mode with step highlighting -->
  <!-- cap: route-summary-dialog#Route statistics displayed -->
  <!-- cap: route-summary-dialog#Route summary dialog shown after calculation -->
  <!-- cap: route-summary-dialog#Scrollable step list -->
  <!-- cap: route-summary-dialog#Start Navigation button -->
  <!-- cap: route-summary-dialog#Stop Navigation button -->
  <!-- cap: route-summary-dialog#Stop navigation from summary dialog hides route -->
- **No regression for subsequent instructions** — No regression for subsequent instructions; Show first instruction on route start; State flag for immediate instruction; ETA is primary stat with compact labels; Navigation instruction text is larger; Remaining time until arrival; Road name excludes type; Stop button uses icon only; Current speed displayed; ETA.
  <!-- cap: immediate-turn-instruction#No regression for subsequent instructions -->
  <!-- cap: immediate-turn-instruction#Show first instruction on route start -->
  <!-- cap: immediate-turn-instruction#State flag for immediate instruction -->
  <!-- cap: nav-hud-visuals#ETA is primary stat with compact labels -->
  <!-- cap: nav-hud-visuals#Navigation instruction text is larger -->
  <!-- cap: nav-hud-visuals#Remaining time until arrival -->
  <!-- cap: nav-hud-visuals#Road name excludes type -->
  <!-- cap: nav-hud-visuals#Stop button uses icon only -->
  <!-- cap: navigation-state-display#Current speed displayed -->
  <!-- cap: navigation-state-display#ETA and remaining distance displayed -->
  <!-- cap: navigation-state-display#Max allowed speed displayed -->
  <!-- cap: navigation-state-display#Navigation state overlay hides on stop -->
  <!-- cap: next-turn-overlay#"Next next" hint -->
  <!-- cap: next-turn-overlay#Distance display -->
  <!-- cap: next-turn-overlay#Driver-seat readable font sizes -->
  <!-- cap: next-turn-overlay#Left-aligned layout -->
  <!-- cap: next-turn-overlay#Next turn overlay visible during navigation -->
  <!-- cap: next-turn-overlay#Next-next turn smaller -->
  <!-- cap: next-turn-overlay#No gap when lanes absent -->
  <!-- cap: next-turn-overlay#Text wrapping -->
  <!-- cap: next-turn-overlay#Turn type icon -->
- **Arrow mapping** — Arrow mapping; Lane arrows in next-turn overlay; Lane data flow; Lane hints toggle.
  <!-- cap: lane-guidance#Arrow mapping -->
  <!-- cap: lane-guidance#Lane arrows in next-turn overlay -->
  <!-- cap: lane-guidance#Lane data flow -->
  <!-- cap: lane-guidance#Lane hints toggle -->
- **Off route red tint on status** — Off-route red tint on status card; Failed reroute calculation is surfaced during navigation; Panel-driven route clearing unchanged; Phone and Android Auto parity for route geometry state; Route stays drawn during reroute calculation; Off-route state is exposed in navigation state; Reroute calculation progress is.
  <!-- cap: off-route-indicator#Off-route red tint on status card -->
  <!-- cap: reroute-route-visibility#Failed reroute calculation is surfaced during navigation -->
  <!-- cap: reroute-route-visibility#Panel-driven route clearing unchanged -->
  <!-- cap: reroute-route-visibility#Phone and Android Auto parity for route geometry state -->
  <!-- cap: reroute-route-visibility#Route stays drawn during reroute calculation -->
  <!-- cap: rerouting-visual-feedback#Off-route state is exposed in navigation state -->
  <!-- cap: rerouting-visual-feedback#Reroute calculation progress is exposed -->
  <!-- cap: rerouting-visual-feedback#Rerouting state is exposed in navigation state -->
  <!-- cap: rerouting-visual-feedback#Solid red tint on status card during rerouting -->

## Search & Destinations

_Phone and car_

- **A repeated query text still runs** — A repeated query text still runs the search; Admin region follows user movement; Auto-focused search input; Clear text button; Convenience entries on empty query; Follow mode deactivated on search result selection; Free-text matches in search suggestions; Full formatted address resolution; Map centers on selected.
  <!-- cap: location-search#A repeated query text still runs the search -->
  <!-- cap: location-search#Admin region follows user movement -->
  <!-- cap: location-search#Auto-focused search input -->
  <!-- cap: location-search#Clear text button -->
  <!-- cap: location-search#Convenience entries on empty query -->
  <!-- cap: location-search#Follow mode deactivated on search result selection -->
  <!-- cap: location-search#Free-text matches in search suggestions -->
  <!-- cap: location-search#Full formatted address resolution -->
  <!-- cap: location-search#Map centers on selected location -->
  <!-- cap: location-search#Marker at selected location -->
  <!-- cap: location-search#Result distance display -->
  <!-- cap: location-search#Result item display -->
  <!-- cap: location-search#Search button on map screen -->
  <!-- cap: location-search#Search resilient to inconsistent map data -->
  <!-- cap: location-search#Search results reusable for route location picking -->
  <!-- cap: location-search#Search scope region name shown in search panel -->
  <!-- cap: location-search#Search scoped by current admin region -->
  <!-- cap: location-search#Search suggestions follow the newest query text -->
  <!-- cap: location-search#Structured matches above free-text for address queries -->
  <!-- cap: location-search#Suggestions-while-type -->
  <!-- cap: location-search#Transliteration-consistent name matching -->
  <!-- cap: precise-location-results#Disambiguation fields on duplicate results -->
  <!-- cap: precise-location-results#Disambiguation fields use existing data only -->
  <!-- cap: precise-location-results#Duplicate-label detection -->
  <!-- cap: precise-location-results#Single-result items unchanged -->
  <!-- cap: search-dialog#Contacts mode -->
  <!-- cap: search-dialog#Full-screen dialog behavior -->
  <!-- cap: search-dialog#Leaving Places mode abandons its search -->
  <!-- cap: search-dialog#POIs mode search flow -->
  <!-- cap: search-dialog#Places mode suggestions -->
  <!-- cap: search-dialog#Search mode switch -->
  <!-- cap: search-dialog#Unified search dialog entry points -->
  <!-- cap: search-free-text#Free-text results merged with structured results -->
  <!-- cap: search-free-text#Free-text results without garbage entries -->
  <!-- cap: search-free-text#Free-text search excludes basemap -->
  <!-- cap: search-free-text#Free-text search over text index -->
  <!-- cap: search-history#Duplicate entries are collapsed when the history is loaded -->
  <!-- cap: search-history#History capped at 50 entries -->
  <!-- cap: search-history#History entry content -->
  <!-- cap: search-history#History entry recorded on result selection -->
  <!-- cap: search-history#History persists across restarts -->
  <!-- cap: search-history#History selection replays the search -->
  <!-- cap: search-history#History view lists entries youngest first -->
  <!-- cap: search-history#No duplicate entry for the same search text -->
  <!-- cap: search-history#Select from history entry on empty search box -->
  <!-- cap: search-result-ranking#Candidate set larger than displayed list -->
  <!-- cap: search-result-ranking#Coordinate result answers a coordinate query exactly -->
  <!-- cap: search-result-ranking#Degradation without per-attribute quality -->
  <!-- cap: search-result-ranking#Distance reference for ordering and display -->
  <!-- cap: search-result-ranking#Perfect match classification uses native per-attribute quality -->
  <!-- cap: search-result-ranking#Perfect-match marking and cross-surface parity -->
  <!-- cap: search-result-ranking#Query attributes classified as criteria or context -->
  <!-- cap: search-result-ranking#Result ordering by tier then distance -->
- **A POI search keeps its own** — A POI search keeps its own slot and is abandoned when its view is left; Category and radius selection; Details via single click; Embedded result map fit never clips a result; No category preselected and no preloaded results; POI results list; POI results map embedded in the search sheet; POI search accessible.
  <!-- cap: poi-search#A POI search keeps its own slot and is abandoned when its view is left -->
  <!-- cap: poi-search#Category and radius selection -->
  <!-- cap: poi-search#Details via single click -->
  <!-- cap: poi-search#Embedded result map fit never clips a result -->
  <!-- cap: poi-search#No category preselected and no preloaded results -->
  <!-- cap: poi-search#POI results list -->
  <!-- cap: poi-search#POI results map embedded in the search sheet -->
  <!-- cap: poi-search#POI search accessible from the map menu -->
  <!-- cap: poi-search#POI search covers all loaded maps with deterministic ordering -->
  <!-- cap: poi-search#Search radius up to 100 km -->
  <!-- cap: poi-search#Selection changes the maps -->
  <!-- cap: poi-search#Selective action closes both dialogs -->
  <!-- cap: poi-search#Viewport restored when POI search closes -->
- **Decision is remembered** — Decision is remembered; First-use rationale dialog before permission request; Permission state drives feature visibility; Requested permission can be granted or denied; Address book entry in the phone map menu; Address book entry on the Android Auto car screen; Address resolution search; Details view for the.
  <!-- cap: address-book-permission#Decision is remembered -->
  <!-- cap: address-book-permission#First-use rationale dialog before permission request -->
  <!-- cap: address-book-permission#Permission state drives feature visibility -->
  <!-- cap: address-book-permission#Requested permission can be granted or denied -->
  <!-- cap: address-book-search#Address book entry in the phone map menu -->
  <!-- cap: address-book-search#Address book entry on the Android Auto car screen -->
  <!-- cap: address-book-search#Address resolution search -->
  <!-- cap: address-book-search#Details view for the resolved object -->
  <!-- cap: address-book-search#Searchable list of persons with addresses -->
  <!-- cap: address-book-search#Selecting a person with multiple addresses asks for the address -->

## Places & Objects on the Map

_Phone_

- **Address entry when house number present** — Address entry when house number present; Area as list entry; Coordinates display; Current position on details mini map; Favorite management in sheet; Full-screen details dialog; Navigate to button in details sheet; Structured description display; Title shows name or address; Close candidate picker without.
  <!-- cap: enhanced-details-sheet#Address entry when house number present -->
  <!-- cap: enhanced-details-sheet#Area as list entry -->
  <!-- cap: enhanced-details-sheet#Coordinates display -->
  <!-- cap: enhanced-details-sheet#Current position on details mini map -->
  <!-- cap: enhanced-details-sheet#Favorite management in sheet -->
  <!-- cap: enhanced-details-sheet#Full-screen details dialog -->
  <!-- cap: enhanced-details-sheet#Navigate to button in details sheet -->
  <!-- cap: enhanced-details-sheet#Structured description display -->
  <!-- cap: enhanced-details-sheet#Title shows name or address -->
  <!-- cap: long-press-candidate-picker#Close candidate picker without selection -->
  <!-- cap: long-press-candidate-picker#Long press shows candidate list -->
  <!-- cap: long-press-candidate-picker#User selects a candidate -->
  <!-- cap: long-press-details#Candidate ranking algorithm -->
  <!-- cap: long-press-details#DescriptionService integration via JNI -->
  <!-- cap: long-press-details#Object lookup by coordinate -->
  <!-- cap: mini-map#Independent interactive viewport -->
  <!-- cap: mini-map#Multiple object markers -->
  <!-- cap: mini-map#North-aligned orientation -->
  <!-- cap: mini-map#Object marker -->
  <!-- cap: mini-map#Optional current-position marker -->
  <!-- cap: mini-map#Panning -->
  <!-- cap: mini-map#Reusable embeddable widget -->
  <!-- cap: mini-map#Zoom controls -->

## Favorites & Saved Places

_Phone and car_

- **Group card renders with color shading** — Group card renders with color shading; Group color can be set via card menu; Back gesture from group detail returns to main grid first; Coordinate entry accepts either decimal separator; Drag end commits the visible order; no-op and aborted drags write nothing; Favorite item has star toggle button; Favorite row.
  <!-- cap: fav-group-color#Group card renders with color shading -->
  <!-- cap: fav-group-color#Group color can be set via card menu -->
  <!-- cap: fav-management-ui#Back gesture from group detail returns to main grid first -->
  <!-- cap: fav-management-ui#Coordinate entry accepts either decimal separator -->
  <!-- cap: fav-management-ui#Drag end commits the visible order; no-op and aborted drags write nothing -->
  <!-- cap: fav-management-ui#Favorite item has star toggle button -->
  <!-- cap: fav-management-ui#Favorite row offers a "Move to group" action -->
  <!-- cap: fav-management-ui#Favorites management stays on the phone -->
  <!-- cap: fav-management-ui#Favorites sheet displays groups as expandable sections -->
  <!-- cap: fav-management-ui#Favorites sheet has "Add current map location" option -->
  <!-- cap: fav-management-ui#Favorites sheet resets to main screen on open -->
  <!-- cap: fav-management-ui#Favorites sheet supports favorite CRUD within groups -->
  <!-- cap: fav-management-ui#Favorites sheet supports group CRUD -->
  <!-- cap: fav-management-ui#Full-screen favorites sheet opens from map screen -->
  <!-- cap: fav-management-ui#Group card menu has "Set Color" option -->
  <!-- cap: fav-management-ui#Group detail list supports drag-and-drop reordering -->
  <!-- cap: fav-management-ui#Reorder commits are serialised -->
  <!-- cap: fav-management-ui#Reorder gesture only applies to favorite rows -->
  <!-- cap: fav-management-ui#Row actions remain available -->
  <!-- cap: fav-management-ui#Search results are not reorderable -->
  <!-- cap: fav-management-ui#Starred chip bar at top of main view -->
  <!-- cap: fav-markers#All saved favorites render as map markers -->
  <!-- cap: fav-markers#Favorite markers update reactively -->
  <!-- cap: fav-markers#Favorite markers use the `_favorite` type -->
  <!-- cap: fav-ordering#A favorite can be moved from one group into another -->
  <!-- cap: fav-ordering#Favorite position within a group is user-defined -->
  <!-- cap: fav-ordering#Move semantics for invalid or out-of-range targets -->
  <!-- cap: fav-ordering#Move-to-group semantics for invalid or refused targets -->
  <!-- cap: fav-ordering#Order is scoped to a group -->
  <!-- cap: fav-ordering#Order is stable across other operations -->
  <!-- cap: fav-ordering#Reordering persists with a single write per committed move -->
  <!-- cap: fav-search#Empty state for no matches -->
  <!-- cap: fav-search#Real-time filter by name -->
  <!-- cap: fav-search#Search across all groups -->
  <!-- cap: fav-search#Search bar on favorites sheet -->
  <!-- cap: fav-star#Favorite can be starred/unstarred -->
  <!-- cap: fav-star#Starred favorites show filled star icon -->
  <!-- cap: fav-star#Starring enters the favorite at the end of the starred order -->
  <!-- cap: fav-starred-chip-bar#Chip bar follows the stored starred order -->
  <!-- cap: fav-starred-chip-bar#Chip bar supports drag-and-drop reordering -->
  <!-- cap: fav-starred-chip-bar#Chip click opens route panel -->
  <!-- cap: fav-starred-chip-bar#Starred favorites shown in chip bar -->
  <!-- cap: favorite-search#Deduplication of identical objects -->
  <!-- cap: favorite-search#Favorite hit selection -->
  <!-- cap: favorite-search#Favorite hits prioritized and marked -->
  <!-- cap: favorite-search#Favorites searchable by name -->
  <!-- cap: favorite-search#Phone and Android Auto parity -->
  <!-- cap: group-grid-display#Back navigation from group detail -->
  <!-- cap: group-grid-display#Click group card to view favorites -->
  <!-- cap: group-grid-display#Group card has action menu -->
  <!-- cap: group-grid-display#Group card tap and action menu survive the drag affordance -->
  <!-- cap: group-grid-display#Group drag writes nothing when it commits nothing -->
  <!-- cap: group-grid-display#Group grid supports drag-and-drop reordering -->
  <!-- cap: group-grid-display#Group reorder commits are serialised -->
  <!-- cap: group-grid-display#Groups displayed in grid -->
  <!-- cap: group-ordering#An order change is observable on its own -->
  <!-- cap: group-ordering#Group order is stable across other operations -->
  <!-- cap: group-ordering#Group order is what every group-listing surface renders -->
  <!-- cap: group-ordering#Group order survives a restart -->
  <!-- cap: group-ordering#Group position is user-defined -->
  <!-- cap: group-ordering#Group reorder commits are serialised -->
  <!-- cap: group-ordering#Group reorder semantics for invalid or out-of-range targets -->
  <!-- cap: group-ordering#Reordering groups persists with a single write per committed move -->
  <!-- cap: group-rename#Rename group via menu -->
  <!-- cap: group-rename#Rename persists across restarts -->
  <!-- cap: group-rename#Rename validates uniqueness -->

## Offline Maps & Data

_Phone and car_

- **Cancel basemap download** — Cancel basemap download; Delete basemap; Download and extract basemap archive; Select basemap variant; Update basemap atomically; Indicate basemap status in map view; Provide basemap download/update control; Show basemap in installed maps list; App hibernation prevention; Wake lock acquired during download;.
  <!-- cap: basemap-download#Cancel basemap download -->
  <!-- cap: basemap-download#Delete basemap -->
  <!-- cap: basemap-download#Download and extract basemap archive -->
  <!-- cap: basemap-download#Select basemap variant -->
  <!-- cap: basemap-download#Update basemap atomically -->
  <!-- cap: basemap-ui#Indicate basemap status in map view -->
  <!-- cap: basemap-ui#Provide basemap download/update control -->
  <!-- cap: basemap-ui#Show basemap in installed maps list -->
  <!-- cap: download-wake-lock#App hibernation prevention -->
  <!-- cap: download-wake-lock#Wake lock acquired during download -->
  <!-- cap: map-download-ui#Active downloads section -->
  <!-- cap: map-download-ui#Basemap section in map manager screen -->
  <!-- cap: map-download-ui#Cancel a download -->
  <!-- cap: map-download-ui#Delete an installed map -->
  <!-- cap: map-download-ui#Delete and refresh errors surfaced -->
  <!-- cap: map-download-ui#Download a map -->
  <!-- cap: map-download-ui#Download button shows progress inline -->
  <!-- cap: map-download-ui#Download errors shown with explicit OK -->
  <!-- cap: map-download-ui#Error banner placement -->
  <!-- cap: map-download-ui#Installed maps name their source -->
  <!-- cap: map-download-ui#Installed maps stay on top after refresh -->
  <!-- cap: map-download-ui#Installed maps visible without refresh -->
  <!-- cap: map-download-ui#Loading indicator placement -->
  <!-- cap: map-download-ui#Provider selection and refresh -->
  <!-- cap: map-download-ui#Search/filter available maps -->
  <!-- cap: map-download-ui#Section ordering -->
  <!-- cap: map-download-ui#Unified map tree view -->

## Map Appearance

_Phone and car_

- **Ambient light sensor option** — Ambient light sensor option; Android Auto template chrome is host-owned and host-themed; Automatic mode follows environment dimming; Dark presentation applies to UI controls; Dark presentation applies to map rendering; Manual dark mode control; Three-state dark mode preference; All bundled styles are selectable;.
  <!-- cap: dark-mode#Ambient light sensor option -->
  <!-- cap: dark-mode#Android Auto template chrome is host-owned and host-themed -->
  <!-- cap: dark-mode#Automatic mode follows environment dimming -->
  <!-- cap: dark-mode#Dark presentation applies to UI controls -->
  <!-- cap: dark-mode#Dark presentation applies to map rendering -->
  <!-- cap: dark-mode#Manual dark mode control -->
  <!-- cap: dark-mode#Three-state dark mode preference -->
  <!-- cap: map-styles#All bundled styles are selectable -->
  <!-- cap: map-styles#Android Auto settings entry -->
  <!-- cap: map-styles#Car surface follows the shared preference without a control -->
  <!-- cap: map-styles#Failed style switch keeps previous style -->
  <!-- cap: map-styles#Icon-versus-symbol preference is a persisted phone setting -->
  <!-- cap: map-styles#Persisted style applied on start -->
  <!-- cap: map-styles#Phone control for the preference -->
  <!-- cap: map-styles#Phone settings entry -->
  <!-- cap: map-styles#Preference decides which rendering is drawn when both exist -->
  <!-- cap: map-styles#Preference is applied at start and on change -->
  <!-- cap: map-styles#Style display name is file name without postfix -->
  <!-- cap: map-styles#Style load failure is visible on both surfaces -->
  <!-- cap: map-styles#Style selection is persisted -->
  <!-- cap: render-mode-switch#Dead direct-render remnants removed -->
  <!-- cap: render-mode-switch#Mode switch re-renders from scratch -->
  <!-- cap: render-mode-switch#Render pipeline honors the selected mode -->
  <!-- cap: render-mode-switch#User-selectable render mode -->

## Map Interaction & Controls

_Phone_

- **Render timing metrics** — Render timing metrics; Zoom placeholder from scaled buffer; Animation continues until native render completes; Eased zoom animation on discrete zoom input; Geographic anchor stays fixed during zoom animation; Retracking on rapid zoom input; The render pin lives only as long as the walk; Viewport records final.
  <!-- cap: adaptive-zoom#Render timing metrics -->
  <!-- cap: adaptive-zoom#Zoom placeholder from scaled buffer -->
  <!-- cap: smooth-zoom#Animation continues until native render completes -->
  <!-- cap: smooth-zoom#Eased zoom animation on discrete zoom input -->
  <!-- cap: smooth-zoom#Geographic anchor stays fixed during zoom animation -->
  <!-- cap: smooth-zoom#Retracking on rapid zoom input -->
  <!-- cap: smooth-zoom#The render pin lives only as long as the walk -->
  <!-- cap: smooth-zoom#Viewport records final magnification -->
  <!-- cap: smooth-zoom#Zoom animation composes with follow mode -->
  <!-- cap: smooth-zoom#Zoom animation never exposes uncovered map area -->
  <!-- cap: zoom-transition-scaling#Native render replaces placeholder at exact target magnification -->
  <!-- cap: zoom-transition-scaling#Placeholder origin matches geographic anchor -->
  <!-- cap: zoom-transition-scaling#Zoom placeholder uses continuous scale factor -->

## Driving, Position & Speed

_Phone and car_

- **Fix availability and quality tiers** — Fix availability and quality tiers; One definition for every fix-quality consumer; Quality is re-evaluated without a new fix; Re-evaluation stays off the main dispatcher while the quality is unchanged; Centered indicator cluster; Minimum readable size for speed text; Minimum readable size for the max-speed sign;.
  <!-- cap: gps-fix-quality#Fix availability and quality tiers -->
  <!-- cap: gps-fix-quality#One definition for every fix-quality consumer -->
  <!-- cap: gps-fix-quality#Quality is re-evaluated without a new fix -->
  <!-- cap: gps-fix-quality#Re-evaluation stays off the main dispatcher while the quality is unchanged -->
  <!-- cap: map-speed-widget#Centered indicator cluster -->
  <!-- cap: map-speed-widget#Minimum readable size for speed text -->
  <!-- cap: map-speed-widget#Minimum readable size for the max-speed sign -->
  <!-- cap: map-speed-widget#Overspeed warning color -->
  <!-- cap: map-speed-widget#Speed badge uses the standard overlay card container -->
  <!-- cap: map-speed-widget#Speed widget shown during navigation -->
  <!-- cap: map-speed-widget#Speed widget shown in follow mode -->
  <!-- cap: map-speed-widget#Stable widget layout -->
  <!-- cap: map-speed-widget#Widget placement on map -->
  <!-- cap: road-lookup-bearing#Road lookup returns name, ref, type, and max speed -->
  <!-- cap: road-lookup-bearing#Road lookup uses vehicle bearing to disambiguate -->
  <!-- cap: smooth-follow#A frame from the previous orientation yields a zero offset instead of an error -->
  <!-- cap: smooth-follow#Anchor-centered follow framing -->
  <!-- cap: smooth-follow#Correction easing -->
  <!-- cap: smooth-follow#Display center extrapolation -->
  <!-- cap: smooth-follow#Display-only prediction -->
  <!-- cap: smooth-follow#Extrapolation loop gating -->
  <!-- cap: smooth-follow#Follow frame refresh before the overrun margin is exhausted -->
  <!-- cap: smooth-follow#Non-negative overrun margin -->
  <!-- cap: smooth-follow#Prediction state update -->
  <!-- cap: smooth-follow#Single resolved anchor across render, blit and marker -->
  <!-- cap: smooth-follow#Stop decision against a current base -->
  <!-- cap: smooth-follow#Vehicle position anchor in follow mode -->

## Sharing & Handoff

_Phone and car_

- **Car stop navigation reflected on phone** — Car stop navigation reflected on phone; Car-only navigation start; Connect mid-navigation shows active route; Phone stop navigation reflected on car; Session lifecycle cleanup; Deep link parsed into a destination; Deep link starts navigation; Deep-link activity forwards to phone app; Deep-link entry point.
  <!-- cap: auto-cross-device-sync#Car stop navigation reflected on phone -->
  <!-- cap: auto-cross-device-sync#Car-only navigation start -->
  <!-- cap: auto-cross-device-sync#Connect mid-navigation shows active route -->
  <!-- cap: auto-cross-device-sync#Phone stop navigation reflected on car -->
  <!-- cap: auto-cross-device-sync#Session lifecycle cleanup -->
  <!-- cap: auto-deep-links#Deep link parsed into a destination -->
  <!-- cap: auto-deep-links#Deep link starts navigation -->
  <!-- cap: auto-deep-links#Deep-link activity forwards to phone app -->
  <!-- cap: auto-deep-links#Deep-link entry point declared -->
  <!-- cap: share/location-receiving#A map link with no coordinate falls back to its place name -->
  <!-- cap: share/location-receiving#An unreadable shared map link is reported, never searched -->
  <!-- cap: share/location-receiving#Map centers on shared location -->
  <!-- cap: share/location-receiving#Shared address triggers search -->
  <!-- cap: share/location-receiving#Shared coordinate disambiguated through candidate picker -->
  <!-- cap: share/location-receiving#Shared location received while app running -->
  <!-- cap: share/location-receiving#Shared map link is read for its coordinate -->

## In Your Car

_Car_

- **AAOS launch entry point** — AAOS launch entry point; Android Auto projection declarations intact in the mobile build; Automotive hardware feature declaration; Automotive template host metadata; Distribution as separate bundle; A live car session is observable in the process; The phone is informed, not disabled; The signal never claims a.
  <!-- cap: android-automotive-os#AAOS launch entry point -->
  <!-- cap: android-automotive-os#Android Auto projection declarations intact in the mobile build -->
  <!-- cap: android-automotive-os#Automotive hardware feature declaration -->
  <!-- cap: android-automotive-os#Automotive template host metadata -->
  <!-- cap: android-automotive-os#Distribution as separate bundle -->
  <!-- cap: car-session-presence#A live car session is observable in the process -->
  <!-- cap: car-session-presence#The phone is informed, not disabled -->
  <!-- cap: car-session-presence#The signal never claims a session that is gone -->
- **Candidate picker on car map selection** — Candidate picker on car map selection; Anchor selection survives immediate dismissal; Anchor settings reflect the persisted value on re-visibility; Compass rose follows the resolved surface presentation; Compass rose north pointer direction; Content box acts as the app menu; Settings dialog reachable while.
  <!-- cap: auto-map-destination-picker#Candidate picker on car map selection -->
  <!-- cap: auto-map-layout#Anchor selection survives immediate dismissal -->
  <!-- cap: auto-map-layout#Anchor settings reflect the persisted value on re-visibility -->
  <!-- cap: auto-map-layout#Compass rose follows the resolved surface presentation -->
  <!-- cap: auto-map-layout#Compass rose north pointer direction -->
  <!-- cap: auto-map-layout#Content box acts as the app menu -->
  <!-- cap: auto-map-layout#Settings dialog reachable while driving -->
  <!-- cap: auto-map-layout#Speed-limit indicator during navigation -->
  <!-- cap: auto-map-layout#Visualisation controls -->
  <!-- cap: auto-navigation-hints#Car hint content -->
  <!-- cap: auto-navigation-hints#End navigation from the car hint -->
  <!-- cap: auto-navigation-hints#Hint teardown without host connection -->
  <!-- cap: auto-navigation-hints#No car surface for free driving -->
  <!-- cap: auto-navigation-hints#Notification importance per surface -->
  <!-- cap: auto-navigation-hints#Trip metadata for cluster and heads-up display -->
  <!-- cap: auto-navigation-hints#Trip publishing cadence -->
  <!-- cap: auto-navigation-hints#Turn hints in the car rail widget -->
  <!-- cap: auto-navigation-hints#Turn-by-turn notification contract while navigating -->
  <!-- cap: auto/navigation-view#A roundabout exit is identified by its ordinal -->
  <!-- cap: auto/navigation-view#Current and next step in host instruction panel -->
  <!-- cap: auto/navigation-view#Current street name shown during navigation -->
  <!-- cap: auto/navigation-view#Host-rendered lane guidance -->
  <!-- cap: auto/navigation-view#Leave navigation at any time -->
  <!-- cap: auto/navigation-view#Manual map panning during navigation -->
  <!-- cap: auto/navigation-view#Navigation ends when the car session ends after arrival -->
  <!-- cap: auto/navigation-view#Next turn maneuver in host instruction panel -->
  <!-- cap: auto/navigation-view#Reroute keeps the navigation view live under the notice -->
  <!-- cap: auto/navigation-view#Route description screen -->
  <!-- cap: auto/navigation-view#Route line on navigation map -->
  <!-- cap: auto/navigation-view#Routing anchor applies when the setting changes during navigation -->
  <!-- cap: auto/navigation-view#Smooth follow-mode scrolling during navigation -->
  <!-- cap: auto/navigation-view#Speed-driven auto-zoom during navigation -->
  <!-- cap: auto/navigation-view#Time and distance to destination -->
  <!-- cap: auto/navigation-view#Vehicle anchor during navigation -->
- **Coordinate row uses the locale stable** — Coordinate row uses the locale-stable format; Destination identity retained during navigation; Details actions visually marked; Details screen shown before navigation starts; Favorite management on details screen; Navigation starts from the details screen; AA place list follows the stored group order; AA place.
  <!-- cap: auto-destination-details#Coordinate row uses the locale-stable format -->
  <!-- cap: auto-destination-details#Destination identity retained during navigation -->
  <!-- cap: auto-destination-details#Details actions visually marked -->
  <!-- cap: auto-destination-details#Details screen shown before navigation starts -->
  <!-- cap: auto-destination-details#Favorite management on details screen -->
  <!-- cap: auto-destination-details#Navigation starts from the details screen -->
  <!-- cap: auto-favorites#AA place list follows the stored group order -->
  <!-- cap: auto-favorites#AA place list reflects the stored favorite order -->
  <!-- cap: auto-favorites#Favorite add/remove from details screen -->
  <!-- cap: auto-favorites#Favorite displays name and address -->
  <!-- cap: auto-favorites#Favorite selection triggers destination picker -->
  <!-- cap: auto-favorites#Favorites grouped by category -->
  <!-- cap: auto-favorites#ListTemplate displays favorites -->
  <!-- cap: auto-favorites#Starred favorites render as one ordered list -->
  <!-- cap: auto-favorites#The all-favorites mode lists groups first -->
  <!-- cap: auto-favorites#The favourites list distinguishes a loading store from an empty one -->
  <!-- cap: auto-search#Car and phone region scoping parity -->
  <!-- cap: auto-search#Car region scope follows the car's movement -->
  <!-- cap: auto-search#Favorite hits in search results -->
  <!-- cap: auto-search#Full formatted address resolution on car screen -->
  <!-- cap: auto-search#Search result limit -->
  <!-- cap: auto-search#Search result selection triggers destination picker -->
  <!-- cap: auto-search#Search results displayed as list -->
  <!-- cap: auto-search#Search scoped by the car position's admin region -->
  <!-- cap: auto-search#Search-as-you-type with debounce -->
  <!-- cap: auto-search#SearchTemplate displayed on car screen -->
  <!-- cap: auto-search#The car search screen stops its own search when it leaves its started period -->
  <!-- cap: auto-search#Transliterated name matching parity with phone search -->
  <!-- cap: auto-search-suggestions#Mode rows on empty query -->
  <!-- cap: auto-search-suggestions#No-results state keeps mode rows -->
  <!-- cap: auto-search-suggestions#Recent searches on empty query -->
  <!-- cap: auto-search-suggestions#Typing replaces suggestions -->

## Device Fit & Accessibility

_Phone and car_

- **A failure from native code is** — A failure from native code is worded by the surface; All user-facing text is translatable; Count-dependent strings use plurals; Diagnostics UI text is translated; English is the default locale; German is fully supported; Locale-aware number formatting; Native-generated navigation text is localized at the.
  <!-- cap: i18n-l10n#A failure from native code is worded by the surface -->
  <!-- cap: i18n-l10n#All user-facing text is translatable -->
  <!-- cap: i18n-l10n#Count-dependent strings use plurals -->
  <!-- cap: i18n-l10n#Diagnostics UI text is translated -->
  <!-- cap: i18n-l10n#English is the default locale -->
  <!-- cap: i18n-l10n#German is fully supported -->
  <!-- cap: i18n-l10n#Locale-aware number formatting -->
  <!-- cap: i18n-l10n#Native-generated navigation text is localized at the frontend -->
  <!-- cap: i18n-l10n#POI category names are localized at the frontend -->
  <!-- cap: i18n-l10n#Parameterized strings use format arguments -->
  <!-- cap: i18n-l10n#Phone and Auto label parity -->
  <!-- cap: i18n-l10n#RTL readiness -->
  <!-- cap: i18n-l10n#Units are localized resources -->
  <!-- cap: turn-instruction-localization#All native instruction kinds are covered -->
  <!-- cap: turn-instruction-localization#Fallback to native text -->
  <!-- cap: turn-instruction-localization#Instruction text is rebuilt from structured fields -->
  <!-- cap: turn-instruction-localization#Shared resources for phone and Auto -->

## Settings, Data & Legal

_Phone and car_

- **About dialog can be dismissed** — About dialog can be dismissed; About dialog displays app description; About dialog displays app name and version; About dialog displays author name; About dialog displays copyright year; About dialog is reachable from map screen menu; About dialog provides link to OSM data licence; About dialog provides link to.
  <!-- cap: about-dialog#About dialog can be dismissed -->
  <!-- cap: about-dialog#About dialog displays app description -->
  <!-- cap: about-dialog#About dialog displays app name and version -->
  <!-- cap: about-dialog#About dialog displays author name -->
  <!-- cap: about-dialog#About dialog displays copyright year -->
  <!-- cap: about-dialog#About dialog is reachable from map screen menu -->
  <!-- cap: about-dialog#About dialog provides link to OSM data licence -->
  <!-- cap: about-dialog#About dialog provides link to project source and licenses -->
  <!-- cap: about-dialog#About dialog provides the bundled dependency license list -->
  <!-- cap: about-dialog#About dialog shows the application's own license with full text -->
  <!-- cap: about-dialog#About dialog states what the app does with location and diagnostics -->
  <!-- cap: about-dialog#License list is not surfaced in the car app -->
  <!-- cap: osm-attribution#Attribution links to OSM copyright page -->
  <!-- cap: osm-attribution#Attribution may collapse but licence info stays reachable -->
  <!-- cap: osm-attribution#Attribution shown in Android Auto variant -->
  <!-- cap: osm-attribution#Attribution shown on all distribution flavors -->
  <!-- cap: osm-attribution#Map displays OSM attribution notice -->

