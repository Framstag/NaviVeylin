# Spec Delta — map-source-selection

## ADDED Requirements

### Requirement: An unencrypted repository source is marked as such

When the repository source's base URL uses the `http` scheme, the map manager SHALL mark that source as unencrypted, at the source together with the URL it applies to. The mark SHALL be informational only: it SHALL NOT gate selection, testing or downloading, and SHALL NOT ask the user to confirm anything.

#### Scenario: An http base URL is marked unencrypted

- **WHEN** the repository source's base URL scheme is `http`
- **THEN** the map manager shows a notice saying the source is unencrypted
- **AND** the notice names the URL it applies to
- **AND** testing, selecting and downloading that source remain available without further interaction

#### Scenario: An https base URL carries no notice

- **WHEN** the repository source's base URL scheme is `https`
- **THEN** no unencrypted notice is shown for it

#### Scenario: Marking does not replace the test outcome

- **WHEN** the user runs the source test on an `http` base URL
- **THEN** the test's own result is still reported
- **AND** the unencrypted notice is shown independently of the result

### Requirement: A repository base URL is normalised before it is used

The app SHALL remove whitespace from a repository base URL before using it to build a request URL, before validating it, and before persisting it as the source's identity, so a URL an input method padded is still usable and two spellings of one URL name one source.

#### Scenario: A padded URL behaves as the unpadded one

- **WHEN** the user enters a base URL that contains whitespace
- **THEN** the test and the requests use that URL without the whitespace
- **AND** the outcome is the same as for the same URL entered without whitespace

#### Scenario: Whitespace does not fork the stored source

- **WHEN** a base URL containing whitespace is selected as the source
- **THEN** the stored source identity equals that of the same URL without whitespace
- **AND** restarting the app shows that same source as active

### Requirement: The repository URL field is presented as URL input with its format shown

The field for the repository base URL SHALL show the format it expects (scheme, host, optional port) and SHALL be presented as URL input, so the platform's text input does not apply prose conventions — a space inserted after a period, or an auto-capitalised first letter.

#### Scenario: The expected format is shown with the field

- **WHEN** the map manager shows the repository URL field
- **THEN** an example of the accepted format is visible beside it

#### Scenario: URL text is not subject to prose conventions

- **WHEN** the repository URL field is focused and text is entered
- **THEN** the field declares URL input, so no space is inserted after a period and no letter is auto-capitalised
