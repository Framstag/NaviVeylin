## Context

See `proposal.md` — motivation. Current layout in `MapCanvasScreen.kt`:

```
Box(fillMaxSize)
  └─ Scaffold(snackbarHost) { scaffoldPadding →
       Box(fillMaxSize.padding(scaffoldPadding))  ← pushes Canvas down
         ├─ Canvas(fillMaxSize)  → map clipped below status bar
         ├─ Column(statusBarsPadding, TopEnd)  → double-padded menu button
         └─ ... overlays
```

`enableEdgeToEdge()` in `MainActivity` makes window draw behind system bars. But `scaffoldPadding` (which includes top status bar inset) is applied to the Box wrapping Canvas. Overlay Column then adds `statusBarsPadding()` on top → double padding = visible gap.

## Goals / Non-Goals

**Goals:**
- Map Canvas renders full screen behind system bars (edge-to-edge)
- Menu button, search, favorites, zoom controls sit below status bar with correct single inset
- Snackbar still visible at bottom

**Non-Goals:**
- No behavior changes to map rendering, gestures, or overlays
- No changes to ViewModel, native code, or data layer

## Decisions

1. **Remove Scaffold, use plain Box** — Scaffold only provided SnackbarHost. Replace with manual `SnackbarHost` composable inside the Box. This eliminates `scaffoldPadding` entirely.

2. **Canvas fills full screen** — `Modifier.fillMaxSize()` with no padding. Map renders behind status bar.

3. **Overlay Column uses `statusBarsPadding()` only** — Single inset from system window. No double padding. Menu button sits directly below status bar.

4. **SnackbarHost at BottomCenter** — Manual positioning with `Modifier.align(Alignment.BottomCenter)`. No bottom inset needed — snackbar draws over map content.

**Alternatives considered:**
- Keep Scaffold but don't apply `scaffoldPadding` to Canvas — still need to handle snackbar separately. Removing Scaffold is cleaner.
- Apply `WindowInsets` directly — more verbose, same result as `statusBarsPadding()`.

## Risks / Trade-offs

- **[Low] Overlay buttons too close to status bar** — `statusBarsPadding()` adds correct inset. Existing `padding(end=8.dp, top=4.dp)` provides visual margin.
- **[Low] Snackbar overlaps map** — Same behavior as before. Snackbar is transient.
- **[Low] Canvas size changes** — `onSizeChanged` callback already reports actual pixel size. ViewModel uses this for projection calculations. Full-screen canvas means larger bitmap renders — no functional change.
