# voice-guidance delta

## ADDED Requirements

### Requirement: The motorway manoeuvre names its signposted destination

While a navigation session is active and the announced step is a motorway enter or change whose record carries a
signposted destination, the engine SHALL append the localized destination clause to the composed spoken instruction.
A sequence that carries no recognised instruction SHALL stay silent, and a step without the record SHALL speak the
sentence the base speaks.

#### Scenario: A motorway manoeuvre speaks its destination

- **WHEN** the announced next step is a motorway change carrying the signposted destination "München" and the native
  voice sequence for its turn arrives
- **THEN** the spoken sentence names the manoeuvre and the destination, as `towards München` in the device's wording
- **Case**: `NavigationEngineVoiceGuidanceTest#motorwayInstructionNamesTheSignpostedDestination`

#### Scenario: The clause never makes an unspoken sequence speak

- **WHEN** a destination is passed beside a sample sequence that carries no recognised instruction
- **THEN** nothing is spoken, and a sequence with a recognised instruction gains exactly the one destination clause
- **Case**: `VoiceGuidanceTest#aDestinationClauseNeedsAnInstruction`
