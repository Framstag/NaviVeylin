## 1. NavigationStateOverlay — Road label & stat labels

- [x] 1.1 Build road display string from `ref` + `name` only, omitting `typeName` (spec: nav-hud-visuals — Road name excludes type)
- [x] 1.2 Replace stat label `Text` composables ("ETA", "Dist", "Speed") with `Icon` composables (`Icons.Default.Schedule`, `Icons.Default.Place`, `Icons.Default.Speed`) (spec: nav-hud-visuals — ETA is primary stat with compact labels)

## 2. NavigationStateOverlay — Stop button

- [x] 2.1 Replace `FilledTonalButton` with text "Stop" with `IconButton` using `Icons.Default.Close` (spec: nav-hud-visuals — Stop button uses icon only)
- [x] 2.2 Remove fixed 72dp width constraint; let icon size determine width (spec: nav-hud-visuals — Stop button uses icon only)

## 3. NextTurnOverlay — Instruction text size

- [x] 3.1 Change `instruction.description` `Text` style from `bodySmall` to `bodyLarge` (spec: nav-hud-visuals — Navigation instruction text is larger)

## 4. NavigationStateOverlay — Remaining time

- [x] 4.1 Add remaining time column between ETA and Distance (spec: nav-hud-visuals — Remaining time until arrival)
- [x] 4.2 Add `formatRemainingTime()` helper computing `etaMillis - System.currentTimeMillis()` (spec: nav-hud-visuals — Remaining time until arrival)

## 5. Verify

- [x] 5.1 Build debug APK and confirm compilation succeeds
- [x] 5.2 Run unit tests and confirm all pass
