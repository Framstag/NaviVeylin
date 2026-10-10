# Spec Delta — map-download-infrastructure

## ADDED Requirements

### Requirement: Shipped builds permit cleartext repository transport

Shipped NaviVeylin builds SHALL permit cleartext HTTP for map repository transport, so a libosmscout mapgen repository served over plain HTTP on a local network is reachable after installation. The permission SHALL be app-wide, SHALL be identical in both distribution flavours, and SHALL NOT alter how an `https://` source is fetched.

#### Scenario: Plain-HTTP source is reachable in a shipped build

- **WHEN** a Play-installed (release) build tests or downloads from a repository base URL whose scheme is `http`
- **AND** the host answers
- **THEN** the request reaches the host
- **AND** no cleartext refusal is raised

#### Scenario: HTTPS source is unaffected

- **WHEN** a repository base URL's scheme is `https`
- **THEN** the request is made with the platform's normal TLS validation
- **AND** the cleartext permission changes neither the request nor its validation

#### Scenario: Both flavours permit it

- **WHEN** the same plain-HTTP source is used from the mobile build and from the automotive build
- **THEN** both reach the host
- **AND** the two flavours' transport policy is identical

### Requirement: A denied cleartext request reports the denial itself

The system SHALL report a repository request that the platform's cleartext policy denies as an unencrypted-transport refusal, naming the requested URL, instead of reproducing the platform's exception text or reporting a generic connection failure.

#### Scenario: Denied request names the reason

- **WHEN** the platform's cleartext policy denies a repository request
- **THEN** the reported failure identifies the refusal as one of unencrypted transport
- **AND** it names the URL that was requested
- **AND** it does not reproduce the platform's own exception sentence

#### Scenario: Permitted request is not reported as a denial

- **WHEN** the platform's cleartext policy permits the request
- **THEN** a failure, if any, is reported as its own kind and not as a cleartext refusal

### Requirement: A base URL that cannot be parsed is reported as an unusable URL

The system SHALL report a repository base URL it cannot parse as an unusable URL, naming the URL and the expected form, instead of reporting a transport or connection failure.

#### Scenario: An unparseable URL is named as unusable

- **WHEN** the source test or a fetch is given a base URL that cannot be parsed
- **THEN** the reported failure identifies the URL as unusable
- **AND** it names the URL and the expected form
- **AND** it is not reported as a transport or connection failure

#### Scenario: A parseable URL is never reported as unusable

- **WHEN** the base URL can be parsed
- **THEN** no unusable-URL failure is reported, whatever the request's outcome
