# Proposal

## Why

The phone's log and diagnostic lines format their numbers through the JVM's **default locale** — Kotlin's
`"%.1f".format(x)` is `String.format(Locale.getDefault(), …)` — so a German install prints decimal commas,
and the comma is the line's field separator: `GPS fix acc=5,0 … speed=30,0 follow=false`,
`browse re-center off=400,0px … mag=15,00`, `follow t=… off=3,4,-1,4 clamped=false`. Every recipe that greps
such a line (`guidelines/Regulatory.md` §9, `guidelines/Build.md` §10) reads a German run differently from an
English one.

The car half landed on 2026-10-03 (`fix-car-follow-reengage-render`, spec `auto-diagnostics`); this is the
phone half, the residue issue #44 was kept open for. It had no phone home, so it was parked as "needs
decision": `i18n-l10n` names `Locale.ROOT` as the *forbidden* constant for numeric formatting, while the car
sibling uses exactly that constant. The decision is `design.md` D1.

## What Changes

- **One helper owns the constant.** `app/src/main/java/com/naviveylin/ui/map/DiagnosticLines.kt` gains
  `internal fun diagnosticNumber(format, value)`, which formats through `Locale.US` — the fixed locale
  `:app` and `:core` already use for machine-parseable text (`DiagnosticsLog`'s timestamp pair,
  `CoordinateFormat`, `NavigationEngine`) and not the one `i18n-l10n` forbids.
- **The four phone sites go through it**: the `follow` line (`MapCanvasScreen.kt`), the GPS-fix line, the
  auto-zoom commit/hold lines and the browse re-center line (`MapCanvasViewModel.kt`). The re-center line is
  the one that also reaches the file-backed diagnostics the diagnostics view and the share sheet show.
- **The `follow` line becomes a builder.** `internal fun followDiagnosticLine(fixTime, speedKmH,
  smoothedBearing, dbg, offset)` — the shape the car change chose — because no test mounts
  `MapCanvasScreen`: the frame-loop `LaunchedEffect` needs a rendered bitmap, a render viewport and a
  non-empty canvas, so an inline concatenation there is assertable by nothing.
- **Nothing else moves.** Field names, field order and the `-` placeholders of every line are unchanged, so
  the recipes' greps and the field list in `guidelines/Regulatory.md` §9 stay valid; a *display* number
  (the `%.1f MB` strings in `ui/mapmanager/`) keeps the device locale, which is what `i18n-l10n` requires of
  display text.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `i18n-l10n`: gains one requirement — *Log and diagnostic numbers are locale-independent* — beside the
  number-formatting requirement it belongs to: a log line is evidence, a display number is text, and the two
  take opposite locales. The car's own line stays owned by `auto-diagnostics`; this requirement names it
  rather than restating it.

## Impact

- `app`: `ui/map/DiagnosticLines.kt` (new), `ui/map/MapCanvasScreen.kt`, `ui/map/MapCanvasViewModel.kt`.
- `app` tests: `ui/map/DiagnosticLineLocaleTest.kt` (new), `ui/map/MapCanvasViewModelBrowseReCenterTest.kt`,
  `test/DefaultLocaleRule.kt` (new).
- `guidelines/Regulatory.md` §9: the provenance sentence of the follow recipe names both changes.
- No native, manifest, dependency, resource or DI change; no behaviour change beyond the separators of a log
  line, so a rollback is the helper's constant alone.
