# Design — contact links in the phone details dialog

## Context

The values are already present. `ContactDescriptionProcessor`
(`app/src/main/cpp/libosmscout/libosmscout/src/osmscout/description/DescriptionService.cpp`) emits two entries for
a POI that carries the tags, and they cross the JNI bridge as plain strings:

```
DescriptionEntry { sectionKey = "Contact", labelKey = "Phone"   | "Website", value = <raw OSM tag> }
```

The constant *name* never leaves native (`LABEL_KEY_CONTACT_PHONE`, `LABEL_KEY_CONTACT_WEBSIZE` — an upstream typo
in the name only; its value is `Website`), so the frontend classifies on the value it receives. Matching a
description entry by its section and label keys is the seam this codebase already uses
(`DetailsResolver` on `Location`/`Address`/`IsIn`, `CandidatePickerSheet` on `General`/`Name`), so no native or JNI
change is proposed.

The phone dialog renders the entries at `LocationDetailsSheet.kt` (the label/value `Row`, the section header above
it). The car builds the same rows in `DetailsScreen.kt` (`buildAttributeList`, one `Row` per entry).

## Decisions

### D1 — Where the classification lives: `:core`, as a pure classifier

**Options**

1. A classifier in `:core` beside `DetailsResolver`, returning `List<ContactLink>` per entry.
2. A helper inside `:app`'s `ui/map` package, next to the dialog.
3. A typed field on `DescriptionEntry` (`linkKind`), crossing the JNI bridge from native.

**Choice**: 1.

**Rationale**: the rules are pure string work with no Android type, so they belong where they can be unit-tested
without Robolectric and without a Compose sandbox — `:core` already owns the sibling rules (`DetailsResolver`,
`formatCoordinatePair`). Option 2 would leave the rules reachable only from `:app`, where the module's
Robolectric suite pays for every case. Option 3 repeats work: the section/label keys are already the classifier
seam three call sites use, and it would cost a submodule patch (`DescriptionService.h`, `DescriptionEntry.java`,
the JNI set-block) plus a gitlink bump for two labels. `:core` may not own wording (`i18n-l10n` — Shared module
owns no wording), so the classifier carries targets and display text only; the hint's wording stays in `:app`.

**Risk**: the classifier keys on values (English strings). A native label rename would silently stop the rows being
linkable — mitigated by the cases naming both keys, so a rename turns them red.

### D2 — Which values become targets: a strict allow-list, with `https://` added for a bare host

**Options**

1. Allow `http`/`https` only; prefix `https://` to a value that parses as a bare host; refuse every other scheme.
2. Allow any scheme `Uri.parse` accepts, letting the platform refuse what it cannot open.
3. Render everything as a link and rely on the intent throwing.

**Choice**: 1.

**Rationale**: these strings are OSM tag data — untrusted input rendered on a map. `javascript:`, `data:`, `file:`,
`content:` and `intent:` are not things a navigation app should hand to a resolver, and option 2 turns a hostile tag
into a launch attempt. Option 3 additionally hides the refusal behind an exception the user cannot see. The
`https://` prefix is required because OSM's `website` tag is routinely written without a scheme.

**Risk**: a legitimate `ftp:` or `mailto:` value renders as text. Accepted — the map is a phone app, and neither has
a target this feature claims.

### D3 — The phone intent: `ACTION_DIAL`, and a normalised `tel:` URI

**Options**

1. `ACTION_DIAL` with `tel:` and the value reduced to digits and one leading `+`.
2. `ACTION_CALL` — places the call immediately.
3. `ACTION_VIEW` with `tel:`.

**Choice**: 1.

**Rationale**: `ACTION_CALL` requires the `CALL_PHONE` runtime permission and places a call from a tap on a row —
a permission this app does not ask for, and an action that is unrecoverable if mis-tapped. `ACTION_DIAL` prefills
the dialer and leaves the decision with the user. `ACTION_VIEW` with a `tel:` URI resolves to the same dialer but is
not the documented contract for it. Normalisation is required because OSM numbers carry spaces, hyphens, slashes
and parentheses (`030 / 12 34 56`).

**Risk**: a number with an extension or a `;ext=` suffix loses it. Accepted; the dialer shows the number before any
call, so the user sees what was kept.

### D4 — The failure path: attempt the launch, report when nothing handles it

**Options**

1. Start the intent, catch the failure, return `false`, show a hint.
2. Query handlers first (`resolveActivity` / `queryIntentActivities`) and render a link only when one exists.
3. Attempt the launch and stay silent when it fails.

**Choice**: 1.

**Rationale**: option 2 reads cleanest until package visibility on API 30+: an app that queries intents for a
browser or a dialer must declare them in `<queries>`, which is manifest surface this change otherwise does not
touch, and — worse — a query answered before the user taps can be stale by the time it is tapped. Option 3 is the
`openUrl` behaviour in `OsmAttributionOverlay.kt` today, and it is indistinguishable from a bug on a Wi-Fi tablet
that ships no dialer. The rule (`UI.md` §1) already prefers a deviation that is visible over one that is silent.

**Risk**: one string and one failure branch on a path that almost never runs. Accepted; the branch is the only thing
that makes the feature testable.

### D5 — Where the hint appears: the dialog's own `SnackbarHost`

**Options**

1. `SnackbarHost` in a `Box` around the dialog's `Column`, with a local `SnackbarHostState`.
2. An inline hint line under the tapped row, inside the description `Column`.
3. A hoisted snackbar on `MapCanvasScreen` (it already owns a `SnackbarHostState`).

**Choice**: 1.

**Rationale**: the description `Column` is the scrollable region and holds the action buttons, so an inline line
would scroll with the content and would require restructuring the label/value `Row` into a column per row.
Option 3 pushes a new parameter through a call site that has no interest in the failure. The snackbar is the
Material 3 idiom for a transient message about an action and is fixed at the dialog's bottom regardless of scroll
position.

**Risk**: a coroutine scope and one wrapper. Bounded; the scope is `rememberCoroutineScope`, so it dies with the
dialog.

### D6 — The testability seam: an injected launcher

**Options**

1. One defaulted parameter `onLaunch: (ContactLink) -> Boolean`, defaulting to the real intent launch.
2. Call `context.startActivity` from the composable and test only the happy path.
3. Inspect the platform's activity stack in the test to infer failure.

**Choice**: 1.

**Rationale**: measured constraint — Robolectric's `ShadowInstrumentation` records an intent for an unregistered
action and does not throw, so a unit test can never produce "no app handles this" through the real call, and
option 2 would leave the hint path unproven while reading as covered (the case would go green on a device-less
JVM). Option 3 has no failure to observe. One default argument keeps the single production call site
(`MapCanvasScreenModalBands.kt`) unchanged and lets the tests pass both `true` and `false`.

**Risk**: a test-only seam in production API. Accepted; it is a `(ContactLink) -> Boolean`, not a strategy object,
and the default is the real behaviour.

### D7 — Multiple values in one tag: split on `;`

**Options**

1. Split on `;`, trimming each part, and resolve each part independently.
2. Link the whole value (a URI containing `;` is legal and resolves to a broken address).
3. Link only the first part and render the rest as text.

**Choice**: 1.

**Rationale**: OSM's `website` and `phone` tags are both multi-valued by convention (`;`), and a row is the natural
place to show what the tag actually says. Option 2 produces a target no server recognises; option 3 hides data the
user can see in the same row.

**Risk**: a URI that legitimately contains an encoded `;` would be split. Accepted — `%3B` is the conventional
encoding and stays intact.

### D8 — The car: rows stay plain, the deviation is recorded

**Options**

1. Car unchanged; the deviation and its platform constraint recorded in the `auto-destination-details` delta.
2. Attempt the same links on the car.
3. Hide the contact rows on the car.

**Choice**: 1.

**Rationale**: a Car App Library template cannot start an activity outside the car app — option 2 is not
implementable, and a `Row` marked clickable would either be ignored or be a lie. Option 3 would drop attributes the
car spec requires to be listed completely. The car spec therefore gains a requirement saying the rows are text and
no intent is started, which is the one thing a future implementer could get wrong here.

**Risk**: none to the car; the risk is a reader assuming shared resolver parity means shared interaction, which the
requirement's wording forecloses.

### D9 — The affordance: a clickable `Text`

**Options**

1. A `Text` with `Modifier.clickable`, `TextDecoration.Underline`, `colorScheme.primary` and a 48 dp minimum touch
   target, one per target.
2. An `AnnotatedString` with `LinkAnnotation.Url` per value.
3. A `TextButton` per value.

**Choice**: 1.

**Rationale**: the value can carry several independent targets (D7), and one clickable `Text` per target keeps each
target's bounds and its own semantics node, which is what a test asserts against. `LinkAnnotation` exists in this
Compose BOM but its interaction listener is experimental, and the annotation would give one node per row rather than
per target. A `TextButton` would impose button padding and elevation where a list row wants a line of text.

**Risk**: a nested clickable inside the scrollable column can swallow a scroll gesture started on the link. Small at
row height; accepted, and the touch target is bounded to the value's own height plus padding.

## Threading and lifecycle

The classifier is a pure function running on the composition thread; the entry list is a handful of items already in
memory, so no dispatcher, no `LaunchedEffect` and no `remember` beyond what the dialog has. The launch runs on the
main thread — `startActivity` requires it — and the snackbar is shown from a `rememberCoroutineScope()` tied to the
composition, so it is cancelled when the dialog leaves. Nothing is written, nothing is persisted, and no state
survives the dialog.

## Verification

- `ContactLinksTest` (`:core`, plain JVM): every allow-list refusal, the `https://` prefix, the phone normalisation,
  the split, and the inert non-contact entry.
- `LocationDetailsDialogComposeTest` (Robolectric): the intent for a website (`ACTION_VIEW` + data), the intent for a
  phone (`ACTION_DIAL` + `tel:`), the hint on an injected `false`, no hint on `true`, and the inert `General` entry.
- `DetailsScreenTest` (car): the contact row's title and text, and that no intent was started.
- Device: on a phone with a browser and a dialer, tap a `Website` row and a `Phone` row and observe the browser and
  the dialer open on the right target; on a device with no dialer, observe the hint. Recorded as the device step of
  the tasks.

## Risks

- **Untrusted input** is the only security-relevant surface here; the allow-list of D2 is the boundary, and its
  cases are the proof.
- **Label renames in native** would silently unlink the rows (D1) — mitigated by the cases naming the keys.
- **A `tel:` intent on a device with telephony but no dialer UI** throws `ActivityNotFoundException` like any other;
  the failure path handles it.

## Open decisions

none
