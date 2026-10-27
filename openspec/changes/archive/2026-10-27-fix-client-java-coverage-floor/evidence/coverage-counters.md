# The module's instruction counters, before and after

Both columns are the `INSTRUCTION` counter of one class in the module's own JaCoCo report, read with
`python3 evidence/class-counters.py <report.xml>`:

- **before** — `evidence/jacoco-report-base.xml`, produced on a copy of the base commit whose test sources
  are the base's own (`./gradlew --offline :osmscout-client-java:test
  :osmscout-client-java:jacocoTestReport` in that copy; the copy lived outside the worktree and is not part
  of the change). It reproduces the base tree's own reading, `covered=1567 missed=2643`.
- **after** — `evidence/jacoco-report-change.xml`, the report of the change's own run
  (`evidence/green-on-change.xml` names the probe's reading of it).

| class | before | after |
|---|---|---|
| AvailableMapEntry | 83/141 = 58.9 % | 127/141 = 90.1 % |
| BasemapManager | 883/1532 = 57.6 % | 1189/1532 = 77.6 % |
| BasemapManager$ActiveDownload | 41/42 = 97.6 % | 41/42 = 97.6 % |
| BasemapManager$BasemapArchive | 75/115 = 65.2 % | 85/115 = 73.9 % |
| BasemapManager$BasemapInfo | 0/97 = 0.0 % | 97/97 = 100.0 % |
| CurrentRoadInfo | 0/96 = 0.0 % | 96/96 = 100.0 % |
| DescriptionEntry | 0/3 = 0.0 % | 3/3 = 100.0 % |
| FavoriteLocation | 0/25 = 0.0 % | 25/25 = 100.0 % |
| FavoriteLocationGroup | 0/29 = 0.0 % | 29/29 = 100.0 % |
| InstalledMaps | 0/72 = 0.0 % | 70/72 = 97.2 % |
| LaneTurn | 0/171 = 0.0 % | 171/171 = 100.0 % |
| LocationEntry | 6/6 = 100.0 % | 6/6 = 100.0 % |
| MapDownloadListener | 1/3 = 33.3 % | 3/3 = 100.0 % |
| MapDownloadManager | 109/487 = 22.4 % | 157/487 = 32.2 % |
| MapDownloadManager$ActiveDownload | 16/42 = 38.1 % | 16/42 = 38.1 % |
| MapProvider | 18/25 = 72.0 % | 25/25 = 100.0 % |
| NavigationController | 0/3 = 0.0 % | 3/3 = 100.0 % |
| NavigationListener | 0/11 = 0.0 % | 11/11 = 100.0 % |
| NavigationPosition | 0/50 = 0.0 % | 49/50 = 98.0 % |
| NavigationState | 0/33 = 0.0 % | 33/33 = 100.0 % |
| ObjectDescription | 0/59 = 0.0 % | 59/59 = 100.0 % |
| OSMScoutClient | 0/229 = 0.0 % | 0/229 = 0.0 % |
| OSMScoutClientBuilder | 0/97 = 0.0 % | 0/97 = 0.0 % |
| PoiCategories | 0/361 = 0.0 % | 361/361 = 100.0 % |
| PoiEntry | 0/3 = 0.0 % | 3/3 = 100.0 % |
| RoadInfo | 0/39 = 0.0 % | 39/39 = 100.0 % |
| RouteCallback | 0/4 = 0.0 % | 0/4 = 0.0 % |
| RouteEntry | 0/3 = 0.0 % | 3/3 = 100.0 % |
| RouteInstruction | 114/160 = 71.2 % | 151/160 = 94.4 % |
| RoutingProfile | 48/48 = 100.0 % | 48/48 = 100.0 % |
| StarredFavoriteLocation | 0/12 = 0.0 % | 12/12 = 100.0 % |
| TrackPoint | 0/24 = 0.0 % | 24/24 = 100.0 % |
| TurnType | 152/167 = 91.0 % | 167/167 = 100.0 % |
| Vehicle | 21/21 = 100.0 % | 21/21 = 100.0 % |
| **module** | **1567/4210 = 37.2 %** | **3124/4210 = 74.2 %** |

The four classes the change does not move are the ones no host case reaches: `OSMScoutClient` (229),
`OSMScoutClientBuilder` (97) and `RouteCallback` (4) load the native library in a static initializer, and
`MapDownloadManager` (330 uncovered) plus `MapDownloadManager$ActiveDownload` (26) are the worker whose
first fallible step is a native call — 686 instructions, 16.3 % of the module, and the report shows every
other class between 73.9 % and 100 %.

Derived from the six local overrides' rows only (`BasemapManager` 1532 + `MapDownloadManager` 487 +
`AvailableMapEntry` 141 + `OSMScoutClient` 229 + `OSMScoutClientBuilder` 97 + `InstalledMaps` 72 = 2558
instructions, 1075 covered): the module's own files alone read **42.0 %** on the base tree, so narrowing the
report to them would not have reached the same floor.
