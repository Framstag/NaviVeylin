<!-- cachekey: 9c5666df8664b4949e1278765ce28d20946d670923b1d256e5aee66bf52be160 area: car-only -->
- **The same app in your car** — Android Automotive OS head units and Android Auto projection from your phone, one app in separate bundles: on the car the launcher opens the car experience, your phone is unchanged. The phone stays usable while the car session is live and shows navigation is presented on the car screen.
  <!-- cap: android-automotive-os#AAOS launch entry point -->
  <!-- cap: android-automotive-os#Android Auto projection declarations intact in the mobile build -->
  <!-- cap: android-automotive-os#Automotive hardware feature declaration -->
  <!-- cap: android-automotive-os#Automotive template host metadata -->
  <!-- cap: android-automotive-os#Distribution as separate bundle -->
  <!-- cap: car-session-presence#A live car session is observable in the process -->
  <!-- cap: car-session-presence#The phone is informed, not disabled -->
  <!-- cap: car-session-presence#The signal never claims a session that is gone -->

- **Guidance on the car display** — The next turn, the street you are on and the lane to take sit in the host's own instruction panel; the speed limit, the remaining time and distance, the route list, the pan, zoom and compass controls, and a rail-widget hint that ends navigation are all there.
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
  <!-- cap: auto/navigation-view#Current and next step in host instruction panel -->
  <!-- cap: auto/navigation-view#Current street name shown during navigation -->
  <!-- cap: auto/navigation-view#Host-rendered lane guidance -->
  <!-- cap: auto/navigation-view#Leave navigation at any time -->
  <!-- cap: auto/navigation-view#Manual map panning during navigation -->
  <!-- cap: auto/navigation-view#Navigation ends when the car session ends after arrival -->
  <!-- cap: auto/navigation-view#Next turn maneuver in host instruction panel -->
  <!-- cap: auto/navigation-view#Route description screen -->
  <!-- cap: auto/navigation-view#Route line on navigation map -->
  <!-- cap: auto/navigation-view#Routing anchor applies when the setting changes during navigation -->
  <!-- cap: auto/navigation-view#Smooth follow-mode scrolling during navigation -->
  <!-- cap: auto/navigation-view#Speed-driven auto-zoom during navigation -->
  <!-- cap: auto/navigation-view#Time and distance to destination -->
  <!-- cap: auto/navigation-view#Vehicle anchor during navigation -->

- **Find a destination from the car** — Type a name or an address and the results list it with its distance and a heart when it is a favorite; tap one and a details screen shows the address, the coordinates and a map preview before navigation starts. Favorite groups hold the order you set, and suggestions fill an empty field.
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
  <!-- cap: auto-favorites#PlaceListTemplate displays favorites -->
  <!-- cap: auto-search#Favorite hits in search results -->
  <!-- cap: auto-search#Full formatted address resolution on car screen -->
  <!-- cap: auto-search#Search result limit -->
  <!-- cap: auto-search#Search result selection triggers destination picker -->
  <!-- cap: auto-search#Search results displayed as list -->
  <!-- cap: auto-search#Search-as-you-type with debounce -->
  <!-- cap: auto-search#SearchTemplate displayed on car screen -->
  <!-- cap: auto-search#Transliterated name matching parity with phone search -->
  <!-- cap: auto-search-suggestions#Mode rows on empty query -->
  <!-- cap: auto-search-suggestions#No-results state keeps mode rows -->
  <!-- cap: auto-search-suggestions#Recent searches on empty query -->
  <!-- cap: auto-search-suggestions#Typing replaces suggestions -->
