# Spec Delta — routing-failure-reporting

## ADDED Requirements

### Requirement: A database list that is still filling is reported as not loaded yet

A route calculation that finds no database instance SHALL report that the routing data is not loaded yet when
an open has been asked for and its instance list is not populated, distinct from the reason a database that
cannot be used reports. Its wording SHALL ask for a retry, and no surface SHALL offer a map-manager action for
it.

#### Scenario: A calculation that outruns the open reports that

- **WHEN** a calculation starts after the client asked for a database open and before the instance list is populated
- **THEN** it SHALL report the not-loaded-yet reason
- **AND** the reason SHALL NOT be the one a database that cannot be used reports
- **AND** its wording SHALL ask the user to try again
- **Case:** `RoutingFailureWordingTest.notYetLoadedRoutingDataIsNotUnusableRoutingData`

#### Scenario: No action is offered for it

- **WHEN** the phone or the car words that reason
- **THEN** the phone SHALL NOT offer the map manager
- **AND** the car SHALL show the same label and SHALL NOT state that the action belongs to the phone
- **Case:** `RoutePanelViewModelFailureReasonTest.aCalculationThatOutrunsTheDatabaseOpenOffersNoAction`,
  `RoutingFailureNoticeTest.theCarLabelEqualsThePhoneLabelForEveryReason`

#### Scenario: Unusable routing data keeps its own reason

- **WHEN** the routing service cannot open the loaded databases
- **THEN** the failure SHALL report the unusable-routing-data reason, which offers the map manager
- **Case:** `RoutingFailureWordingTest.unusableRoutingDataOffersTheMapManager`
