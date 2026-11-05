# Proposal

## Why

A place's phone number and website are the two attributes a user acts on immediately, and the phone details
dialog already receives both: the native description carries `Contact/Phone` and `Contact/Website` entries
(`DescriptionService.cpp`, `ContactDescriptionProcessor`). The dialog renders them as inert text, so the user has
to read a number off the screen and retype it, or retype a URL by eye. Nothing native and nothing in the JNI
bridge has to change — the values already arrive, they are only not actionable.

## What Changes

- The phone details dialog renders the values of the two known `Contact` label keys as tappable links: a website
  opens the browser, a phone number opens the dialer prefilled. Every other entry keeps rendering as text.
- A value carrying several `;`-separated parts is split — each part becomes its own link, or stays plain text.
- Website targets: `http` and `https` only. A value with no scheme is given `https://` first. Any other scheme
  (`javascript:`, `file:`, `data:`, `intent:`, `content:`) renders as plain text and starts no intent.
- Phone targets: digits plus a leading `+`, opened with `ACTION_DIAL` (never `ACTION_CALL`, which would need the
  `CALL_PHONE` permission). A value under three digits renders as plain text.
- A tap that no installed app can handle shows a transient hint instead of silently doing nothing.
- The Android Auto details screen is unchanged: its contact rows stay non-actionable, and the delta records the
  platform constraint that forces it.

**Not changing**: the native description service, the JNI bridge, the `DescriptionEntry` model, the car details
screen, the manifest (no `<queries>` entry — the app never resolves a handler, it tries the launch and handles the
failure), any permission, and the label or value wording (labels stay the native English keys; their translation
is filed separately as issue #767).

## Capabilities

### New Capabilities

None. The behaviour belongs to the dialog that renders it, not to a new capability.

### Modified Capabilities

- `enhanced-details-sheet`: two requirements added — a contact value resolves to a target and becomes a link, and
  an unhandled link shows a hint. The shipped "Structured description display" requirement keeps holding (the rows
  are still rendered as label/value rows); this change adds what a tap on two of them does.
- `auto-destination-details`: one requirement added — the car's contact rows stay non-actionable. The shipped
  requirement that lists every description attribute as a labeled row is not modified: phone and website are still
  listed, only not actionable.

## Impact

**Modules and files**

- `core/src/main/java/com/naviveylin/core/details/ContactLinks.kt` (new): the pure classifier —
  `ContactLink(kind, display, target)` and the split/sanitize rules. No Android type, no resource, no wording.
- `core/src/main/java/com/naviveylin/core/details/DetailsResolver.kt`: unchanged in behaviour; the classifier is a
  sibling so the resolver's `displayEntries` contract stays as it is.
- `app/src/main/java/com/naviveylin/ui/map/LocationDetailsSheet.kt`: the `Contact` rows render each part as a link
  or as text; the dialog root gains a `SnackbarHost` for the failure hint, and the composable gains one defaulted
  parameter `onLaunch: (ContactLink) -> Boolean` so the failure path is injectable in a test.
- `app/src/main/java/com/naviveylin/ui/attribution/OsmAttributionOverlay.kt`: the `openUrl` helper gains a
  `tel:`/browser sibling returning whether a handler took the intent (`internal`, same module, reused as the
  production default for `onLaunch`).
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-de/strings.xml`: one hint string each.
- `app/src/test/java/com/naviveylin/ui/map/LocationDetailsDialogComposeTest.kt`: the link, intent, hint and
  inert-entry cases.
- `core/src/test/java/com/naviveylin/core/details/ContactLinksTest.kt` (new): the split and allow-list cases.
- `auto/src/test/java/com/naviveylin/auto/DetailsScreenTest.kt`: the car case asserting the contact rows stay plain.

**Guidelines referenced**: `guidelines/UIPhone.md` §5 "Phone details dialog structure" (the layout rule gains the
link rule); `guidelines/UI.md` §1 "Cross-variant UI parity (general rule)" — "a documented deviation names its
platform constraint" — with the record row in `guidelines/UI.why.md` §1; `guidelines/Design.md` §4 (threading —
the classifier is a pure function on the composition thread, no dispatcher); `guidelines/Logging.md` §2 (no new
log line: a refused value is rendered, not logged, so the coordinate rule and the disclosure are untouched).

**Classification**: additive. **Rollback**: revert the commit — the specs return to their shipped text, the dialog
renders the values as text again, and the two new files and one string pair disappear. No data, no persistence
format and no native interface is involved, so there is no migration to unwind.

surface: 3 requirements and 10 scenarios added, 0 requirement bodies rewritten — 9 scenarios in the
`enhanced-details-sheet` delta and 1 in the `auto-destination-details` delta, counted by
`grep -c '^#### Scenario:' openspec/changes/contact-links-in-details/specs/enhanced-details-sheet/spec.md` (9) and
`grep -c '^#### Scenario:' openspec/changes/contact-links-in-details/specs/auto-destination-details/spec.md` (1).
