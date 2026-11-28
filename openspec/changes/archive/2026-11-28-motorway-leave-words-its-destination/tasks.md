# Tasks

## 1. The gate and the composition (`:core`)

- [x] 1.1 Admit `MOTORWAY_LEAVE` in `ManoeuvreLabels.carriesDestination`; a turn and a name change keep wording
  nothing (`core/src/main/java/com/naviveylin/core/ManoeuvreLabels.kt`). Verify: `core: ManeuverLabelTest`
  "a plain turn does not show a destination record it carries" stays green.
- [x] 1.2 A manoeuvre with no composition target and a sign leads onto `towards <destination>`; one whose record
  repeats the target adds no clause; `description` words the leave-only-sign case as `<short> towards <dest>`.
  Verify: the three added `core: ManeuverLabelTest` cases, green in German and English.

## 2. The surfaces pin the parity

- [x] 2.1 Overlay: second line "towards Essen-Zentrum", first line the manoeuvre. Verify: `app:
  NextTurnOverlayTest` "a motorway leave's second line carries the signposted destination".
- [x] 2.2 Car hint: secondary text carries the destination. Verify: `app: CarHintContentTest`
  "aMotorwayLeavesSecondaryTextCarriesTheSignpostedDestination".
- [x] 2.3 Car panel target street: "towards Essen-Zentrum". Verify: `auto: NavigationTemplateMapperTest`
  "a motorway leave's road carries the destination".
- [x] 2.4 Voice: the leave's sentence names the destination; the plain turn's stays unspoken. Verify:
  `app: NavigationEngineVoiceGuidanceTest#aMotorwayLeaveNamesItsDestination` (inverted from the scenario this
  change removes) and `#aPlainTurnKeepsItsDestinationUnspoken`.

## 3. The measurement on the owner's routes

- [x] 3.1 Route-hint dump over both routes, both directions (NRW): the leave's `leadsOnto` and `carhint` carry
  its destination. Measured 2026-11-28:
  `step=282 km=14.1 kind=MOTORWAY_LEAVE destination='Dortmund-Schüren' leadsOnto='towards Dortmund-Schüren'
  carhint='towards Dortmund-Schüren'` (`phoenix-fwd`) and `step=950 km=41.3 … destination='Essen-Zentrum'
  leadsOnto='towards Essen-Zentrum'` (`essen-fwd`); a leave with no record keeps `B 54 (B 236)` /
  `Mallinckrodtstraße (K 16)`.
- [x] 3.2 Guidance replay over the recorded fixtures: `summary 'phoenix-fwd' expected=16 announced=14 matched=14
  destination_missing=0 wording_differs=0 unannounced=2 unexpected=0`, with the live announcement
  `kind=MOTORWAY_LEAVE destination=Dortmund-Schüren leadsOnto=towards Dortmund-Schüren` at 14.1 km.

## 4. Documents

- [x] 4.1 `guidelines/UIPhone.md` — the notification and car-hint rule names enter, change and leave. (The car
  panel's target-street rule is owned by the spec `auto/navigation-view`, which no guideline restates, so there
  is no second sentence to change.)
