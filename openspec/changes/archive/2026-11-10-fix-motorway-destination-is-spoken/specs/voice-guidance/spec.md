# Spec Delta

## ADDED Requirements

### Requirement: The motorway manoeuvre names its signposted destination

When the manoeuvre ahead is a motorway enter or change whose step carries a signposted destination,
the spoken instruction SHALL name that destination beside the manoeuvre, resolved from the app's own
localized resources. The value SHALL be the manoeuvre's own `destination` record, spoken only through
the `ManoeuvreLabels.carriesDestination` gate the screens use. A manoeuvre that carries no destination
record, and one that is not a motorway enter or change, SHALL keep exactly today's sentence.

#### Scenario: A motorway change's spoken instruction names the destination

- **WHEN** the manoeuvre ahead is a motorway change whose step carries the signposted destination
  "München" and the native listener reports the sequence for that manoeuvre
- **THEN** the spoken sentence SHALL name the manoeuvre the agent emitted and the destination
  ("After 300 meters leave the motorway to the right towards München")
- **AND** the destination SHALL be the manoeuvre's own `destination` value, never parsed out of the
  sample sequence or out of the sentence
- **Case**: `app: NavigationEngineVoiceGuidanceTest#aMotorwayChangeNamesItsDestination`

#### Scenario: A motorway manoeuvre without a destination record keeps today's sentence

- **WHEN** the step ahead enters or changes a motorway and carries no destination record
- **THEN** the spoken sentence SHALL be exactly the sentence it produces today, with no placeholder
  and no empty clause
- **Case**: `app: VoiceGuidanceTest#aSentenceWithoutADestinationKeepsTodaysWording`

#### Scenario: A destination record on a manoeuvre that is not a motorway enter or change stays unspoken

- **WHEN** the step ahead is a motorway leave whose step carries a destination record, and the
  native listener reports the same samples a motorway change emits
- **THEN** the spoken sentence SHALL NOT gain a destination clause
- **Case**: `app: NavigationEngineVoiceGuidanceTest#aMotorwayLeaveKeepsItsDestinationUnspoken`
