## ADDED Requirements

### Requirement: Contact values are actionable
The phone details dialog SHALL render the value of a `Contact/Phone` or `Contact/Website` description entry as a tappable link when the value resolves to a target: a website SHALL open in a browser, a phone number SHALL open the dialer prefilled. The label and value text SHALL stay verbatim. A value that resolves to no target SHALL render as plain text, and no entry outside the `Contact` section SHALL become tappable.

#### Scenario: A website value opens the browser
- **WHEN** the object description carries a `Contact` entry labelled `Website` with the value `https://cafe.de`
- **AND** the user taps that value
- **THEN** the dialog SHALL start an `ACTION_VIEW` intent for `https://cafe.de`
- **AND** the details dialog SHALL stay open
- **Case**: `LocationDetailsDialogComposeTest#websiteValueStartsTheBrowser`

#### Scenario: A phone value opens the dialer prefilled
- **WHEN** the object description carries a `Contact` entry labelled `Phone` with the value `+49 30 1234567`
- **AND** the user taps that value
- **THEN** the dialog SHALL start an `ACTION_DIAL` intent whose data is `tel:+49301234567`
- **AND** no call SHALL be placed and no telephony permission SHALL be required
- **Case**: `LocationDetailsDialogComposeTest#phoneValueOpensTheDialer`

#### Scenario: A website value without a scheme is linked over https
- **WHEN** the `Website` value is `www.cafe.de`
- **THEN** the resolved target SHALL be `https://www.cafe.de`
- **Case**: `ContactLinksTest#aBareHostGetsHttpsAndIsLinked`

#### Scenario: A non-web scheme is refused
- **WHEN** the `Website` value is `javascript:alert(1)`, `file:///etc/passwd`, `data:text/html,x`, `intent://x` or `content://x`
- **THEN** the value SHALL resolve to no target, so the row stays plain text
- **Case**: `ContactLinksTest#aNonWebSchemeResolvesToNoTarget`

#### Scenario: A phone value too short to dial is refused
- **WHEN** the `Phone` value carries fewer than three digits (e.g. `12`)
- **THEN** the value SHALL resolve to no target, so the row stays plain text
- **Case**: `ContactLinksTest#aPhoneValueBelowThreeDigitsResolvesToNoTarget`

#### Scenario: A multi-valued tag splits into one target per part
- **WHEN** the `Website` value is `https://a.de; https://b.de`
- **THEN** the value SHALL resolve to two targets, one per part
- **AND** their targets SHALL be `https://a.de` and `https://b.de`, in that order
- **Case**: `ContactLinksTest#aMultiValuedTagSplitsIntoOneTargetPerPart`

#### Scenario: An entry outside the Contact section stays inert
- **WHEN** the description carries a `General` entry labelled `Name` with the value `https://cafe.de`
- **THEN** that value SHALL render as plain text
- **AND** tapping it SHALL start no intent
- **Case**: `LocationDetailsDialogComposeTest#aNonContactEntryStaysInert`

### Requirement: An unhandled contact link shows a hint
When the launch reports that no installed app handles a contact link's intent, the dialog SHALL show a transient hint taken from a localized string resource, SHALL keep the details dialog open, and SHALL NOT retry the launch. A link whose launch was handled SHALL show no hint.

#### Scenario: A contact link no app can handle shows the hint
- **WHEN** the user taps a contact link and no installed app handles its intent
- **THEN** the dialog SHALL show the hint
- **AND** the details dialog SHALL stay open
- **Case**: `LocationDetailsDialogComposeTest#anUnhandledLinkShowsTheHint`

#### Scenario: A handled contact link shows no hint
- **WHEN** the user taps a contact link and an installed app handles its intent
- **THEN** no hint SHALL be shown
- **Case**: `LocationDetailsDialogComposeTest#aHandledLinkShowsNoHint`
