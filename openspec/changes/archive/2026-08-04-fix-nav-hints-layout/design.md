## Context

See proposal.md for motivation. Current layout:

- **NextTurnOverlay** at `Alignment.TopStart` with `fillMaxWidth()` + `padding(horizontal = 16.dp)` — spans full screen, covers top-right buttons
- **NavigationStateOverlay** at `Alignment.BottomCenter` with `fillMaxWidth()` + `padding(horizontal = 16.dp)` — not edge-to-edge
- Top-right button column at `Alignment.TopEnd` with `padding(end = 8.dp)`

## Goals / Non-Goals

**Goals:**
- Constrain NextTurnOverlay width to avoid overlapping top-right buttons
- Remove left margin on NextTurnOverlay (flush to left edge)
- Make NavigationStateOverlay truly full width (no horizontal padding)

**Non-Goals:**
- No changes to button layout, positioning, or sizing
- No changes to map canvas or gesture handling
- No behavioral changes to navigation logic

## Decisions

**Decision 1: Width constraint via `widthIn(max=...)` on NextTurnOverlay**
- Approach: Compute available width as `screenWidth - buttonColumnWidth - gap`, apply `Modifier.widthIn(max = availableWidth)` on the outer Card
- Alternative: Use `Modifier.requiredWidthIn()` — rejected because it would clip the card's shadow
- Alternative: Wrap in a `Box` with fixed max width — adds unnecessary nesting

**Decision 2: Remove left padding on NextTurnOverlay**
- Change `padding(horizontal = 16.dp, vertical = 8.dp)` to `padding(start = 0.dp, end = 16.dp, vertical = 8.dp)` on the Card
- Keep right padding for visual breathing room from the buttons

**Decision 3: Full-width NavigationStateOverlay**
- Remove `padding(horizontal = 16.dp)` from the outer Box modifier, keep only `top = 8.dp`
- No bottom padding — panel sits flush to bottom display edge
- Card inside already has `fillMaxWidth()` so it will span edge-to-edge

## Risks / Trade-offs

- [Button column width varies] → Use a fixed estimate (~56dp for icon buttons + padding) rather than measuring at runtime. Slight over-estimate is safe (hints just get slightly narrower)
- [Landscape vs portrait] → Same approach works for both orientations since the button column is always at TopEnd
