# A motorway leave words its signposted destination

## Why

`MOTORWAY_LEAVE` is the one motorway manoeuvre that names nothing when its step carries a signposted
destination. The gate is `ManoeuvreLabels.carriesDestination` (`core/src/main/java/com/naviveylin/core/ManoeuvreLabels.kt`),
which admits only `MOTORWAY_ENTER` and `MOTORWAY_CHANGE` — a decision taken by `motorway-destination-hint`
(design D5), when the record was only known to reach enter/change steps.

Two measured routes show what that costs (owner report 2026-11-28, driven in Android Auto; routes
51.5726589 7.4649125 → 51.4918664 7.5045441 and 51.5726589 7.4649125 → 51.4533448 6.9939603, NRW map data):

- `phoenix-fwd`, the route's whole motorway phase is one step: `MOTORWAY_LEAVE` at 14.1 km carrying
  `destination='Dortmund-Schüren'` and no street name. Its second line, its car hint and its spoken sentence
  carry nothing — the only guidance the driver gets at that exit is "Leave motorway".
- `essen-fwd` / `essen-rev`, the leave carries `Essen-Zentrum` / nothing and words it alike.

The record is present, published and read: `destination_missing=0` on every announced manoeuvre of both routes
(the route-hint dump, `tools/route-hints.sh`). Only the wording gate refuses it.

## What Changes

- `ManoeuvreLabels.carriesDestination` admits `MOTORWAY_LEAVE` beside enter and change. Nothing else about the
  gate changes: a plain turn or name change whose way carries the tag still words nothing (design D5 stays).
- `ManoeuvreLabels.leadsOnto` gains the case where the manoeuvre has **no** thing to lead onto but does carry a
  sign: a motorway exit whose step names no street leads onto `towards <destination>`, because a bare place name
  is not what a second line or a car target street says.
- `ManoeuvreLabels.description` words that case as `<short> towards <destination>` ("Leave motorway towards
  Dortmund-Schüren") instead of the `onto` composition, which would read "onto towards".
- A destination that merely repeats the thing the manoeuvre leads onto adds no clause: a way tagged
  `destination=A 45` reads "A 45", never "A 45 towards A 45". This is the second defect the same measurement
  found (`essen-rev`, 30.0 km) and it is fixed in the same function.
- No resource is added; the clause reuses `nav_towards`, as the enter/change path already does.

Surface reach: the phone overlay's second line, the car hint's secondary text, the car instruction panel's target
street and the spoken sentence all read one of these functions, so all four gain the clause together — that
parity is what the specs below pin.

Not changed: the census (`motorway_dest` already counts whatever `carriesDestination` admits), the native step
model, the route list rows, and any wording for a manoeuvre that is not a motorway manoeuvre.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `turn-instruction-localization` — the wording rule gains the leave, the "nothing but the sign" composition and
  the repeat guard.
- `next-turn-overlay` — the second line gains the leave's destination.
- `auto-navigation-hints` — the car hint's secondary text gains the leave's destination.
- `auto/navigation-view` — the car's target street gains the leave's destination.
- `voice-guidance` — the spoken sentence gains the leave's destination, and its "stays unspoken" scenario moves
  from the leave to a plain turn, which is the manoeuvre the gate still excludes.

## Impact

- `core/src/main/java/com/naviveylin/core/ManoeuvreLabels.kt` — `carriesDestination`, `leadsOnto`,
  `description`, one private `signDestination` helper.
- Cases: `core: ManeuverLabelTest` (three added), `app: CarHintContentTest`, `app: NextTurnOverlayTest`,
  `app: NavigationEngineVoiceGuidanceTest` (one inverted, one added for the plain turn),
  `auto: NavigationTemplateMapperTest` (one added).
- Guidelines: `guidelines/UIPhone.md` §(notification content) and `guidelines/UICar.md` name "enter or change"
  in the same rule; both sentences follow the requirement.
- Nothing in the native tree, no resource, no schema, no persisted state.

Reversal: the four-word gate plus the two composition branches — a revert is one file and the case set.

## Measurement the change was decided on

`tools/route-hints.sh --record` / `--replay` over the owner's two routes (NRW): 4 fixtures, replay
`destination_missing=0 wording_differs=0` before the change, so the defect is the gate, not the record; and the
route dump shows the leave's `destination=` values (`Dortmund-Schüren`, `Essen-Zentrum`) on the step itself.
