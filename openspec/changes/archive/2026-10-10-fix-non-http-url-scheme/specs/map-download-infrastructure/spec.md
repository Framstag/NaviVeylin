# Spec Delta — map-download-infrastructure

## ADDED Requirements

### Requirement: A base URL with a non-HTTP scheme is reported as an unusable URL

The system SHALL report a repository base URL whose scheme is neither `http` nor `https` as an unusable URL, naming the URL and the expected form, instead of reporting a transport or connection failure.

#### Scenario: A non-HTTP scheme is named as unusable

- **WHEN** the source test or a fetch is given a base URL that parses with a scheme other than `http` or `https`
- **THEN** the reported failure identifies the URL as unusable
- **AND** it names the URL and the expected form
- **AND** it is not reported as a transport or connection failure

## MODIFIED Requirements

### Requirement: A base URL that cannot be parsed is reported as an unusable URL

The system SHALL report a repository base URL it cannot parse as an unusable URL, naming the URL and the expected form, instead of reporting a transport or connection failure.

#### Scenario: An unparseable URL is named as unusable

- **WHEN** the source test or a fetch is given a base URL that cannot be parsed
- **THEN** the reported failure identifies the URL as unusable
- **AND** it names the URL and the expected form
- **AND** it is not reported as a transport or connection failure

#### Scenario: A parseable URL is never reported as unusable

- **WHEN** the base URL parses with an `http` or `https` scheme
- **THEN** no unusable-URL failure is reported, whatever the request's outcome
