# Design — the description vocabulary crosses the boundary as values only

## The shape of the defect

| where | what it does today |
|---|---|
| `DescriptionService.cpp:142-…` | publishes 84 English constants (64 `LABEL_KEY_*`, 12 `SECTION_NAME_*`, 8 `SUBSECTION_NAME_*`), 74 distinct values; localizes none of them |
| `JavaScout/.../DescriptionEntry.java` | carries `sectionKey`, `subsectionKey`, `labelKey`, `value` across JNI |
| `LocationDetailsSheet.kt:295`, `:318`, `:343` | renders the three key fields as text, with no resource in between |
| `DetailsResolver.kt:31,46,53,62,79,106` / `ContactLinks.kt:41-47,63` | match the same fields by raw value (`SECTION_CONTACT`/`LABEL_PHONE`/`LABEL_WEBSITE` in `ContactLinks`, `"General"`/`"Name"`, `"Location"`/`"IsIn"` in `DetailsResolver`; `ContactLinks.resolve` is where the two link-bearing labels are decided) |

The constant *name* never crosses the bridge: Kotlin sees `"Phone"`, not `LABEL_KEY_CONTACT_PHONE`. So the only
identifier a mapping can key on is the value — and the raw-value matching in `:core` must keep working on the same
value, which is why the mapping is a *display* step at the render site and not a rewrite of the entry.

## The single fix

A table in the module that renders the row — `:app`, `ui/map/DescriptionLabels.kt` — keyed on the trimmed raw value,
resolving to `R.string.description_label_<slug>`, consumed by a composable that falls back to the key:

```kotlin
@StringRes fun res(key: String?): Int? = when (key?.trim()) { "Contact" -> R.string.description_label_contact … else -> null }
@Composable fun descriptionLabel(key: String?): String
```

Three call sites change; the label row's `":"` stays in Kotlin, because a separator is not a word. The subsection's
ordinal index is **not** concatenated beside the translated word: `UI.md` §10 requires positional format arguments
("a translator can reorder"), so the word and the number live in one resource, `description_subsection_index`
(`%1$s %2$d`), read as `stringResource(R.string.description_subsection_index, descriptionLabel(key), index)`. The
ordinal stays data from the entry; only its position in the sentence becomes the translator's.

**Why a `when`, not a lookup by name.** A `when` is checked at compile time: every branch names a resource that
exists, so a renamed or deleted string is a build failure rather than a blank row at runtime.

## Alternatives the evidence eliminated

- **A shared Kotlin map in `:core`.** `:core` owns no wording (spec `i18n-l10n` — "Shared module owns no wording";
  `UI.md` §10), and the car would then have to either take a `:core` literal or duplicate the table — the exact
  duplicate-resource-home the parity requirement forbids. The shared module also composes both surfaces' text from
  their callers' resources today (`DetailsResolver.resolveTitle(input, genericTitle)`), so a wording map there would
  be the single exception to a rule the module is built around.
- **Localize on the native side** — set a description language on the client builder so `DescriptionService` emits
  German labels. Impossible: the description language localizes *values* (opening hours, lengths); the labels are
  `static const std::string` members of the processors, and `DescriptionService.h` declares no localizer. The
  proposal's evidence is the constant table itself; there is nothing to switch on.
- **`resources.getIdentifier("description_label_" + key, "string", pkg)`.** No compile-time check, so a renamed key
  degrades to a blank row instead of a failure. The compile-checked `when` costs the same 74 lines and fails loudly.
- **Translate the car's rows in the same change.** The car renders its own row titles from the same value
  (`auto/.../DetailsScreen.kt:690`), but `:auto` keeps its own `res/` and the item scopes the car out as the parity
  follow-up. Doing both here would put two surfaces' resource sets and two cases in one delta and buy the phone
  nothing.
- **Reword the English keys as well** (`LanesForward` → `Lanes forward`). The defect is the missing localization, not
  the camelCase; rewording would move 6 existing assertions and the car's title text for no German gain, and it is
  a wording decision the item does not authorise.

## Blast radius

| file | change |
|---|---|
| `app/src/main/java/com/naviveylin/ui/map/DescriptionLabels.kt` | new: 74-branch table + composable |
| `app/src/main/java/com/naviveylin/ui/map/LocationDetailsSheet.kt` | 4 `descriptionLabel(...)` calls at 3 render positions |
| `app/src/main/res/values/strings.xml` | 74 `description_label_*` entries (English value = the key) plus `description_subsection_index` (`%1$s %2$d`) |
| `app/src/main/res/values-de/strings.xml` | the same 74 keys in German, plus the same format string |
| `app/src/test/.../ui/map/LocationDetailsDialogComposeTest.kt` | 3 cases added |
| `guidelines/UIPhone.md` §5 | the "labels stay the native English keys" sentence replaced |
| `guidelines/UI.why.md` §1 | one parity row for the description rows |

Not touched: `:core`, the native tree, `:auto`, the build. The hardcoded-string scan (`checkHardcodedStrings`) sees
no new literal in a text position: the `when` branches are match keys, the `":"` separator is the symbol exemption
the row already relied on, and the subsection template substitutes a resource value only.

## Threading and lifecycle

None. `stringResource` is a `@Composable` read of the composition's `Resources`, on the main thread, inside the
dialog's existing composition; no state, no dispatcher, no coroutine, no lifecycle owner is added. `res()` is pure
and allocation-free apart from `trim()`.

## Verification

- **Host test, the defect**: `LocationDetailsDialogComposeTest.germanDeviceTranslatesDescriptionSectionsAndLabels` —
  red on HEAD (`evidence/TEST-red-head-LocationDetailsDialogComposeTest.xml`, `tests=24 failures=1`), green after
  (`evidence/TEST-green-gate-mobile-LocationDetailsDialogComposeTest.xml` and its automotive sibling,
  `tests=25 failures=0` each, `evidence/gate-summary.txt`).
- **Host test, the fallback**: `anUnmappedDescriptionKeyKeepsTheNativeKey`.
- **Host test, resource parity**: `GermanTranslationCompletenessTest` (`tests=2 failures=0`,
  `evidence/TEST-green-GermanTranslationCompletenessTest.xml`).
- **Host test, the English path**: `englishDeviceKeepsTheNativeDescriptionWords` asserts the `Contact` header and
  the `Phone:` and `Website:` rows under the default locale — the three clauses the English scenario states. The
  class's existing `Phone:` / `Website:` / `Name:` / `General` assertions stay unchanged and green, because the
  English value of each key is the key.
- **Structural check, the guideline**: `bash tools/check-doc-routes.sh` still exits 0 after the §5 edit (the section
  is routed from `AGENTS.md`, its text is not a routing row).
- **Predicted, not measured**: the dialog of any `contact:*` object on a `de` device reads `Kontakt` / `Telefon:` /
  `Webseite:`. No device run is carried and none is claimed — the host test reads the same `Resources` the device
  reads, so the prediction is the composition of two verified facts, not evidence of its own.

## Open decisions

none
