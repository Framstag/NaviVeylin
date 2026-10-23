# Tasks — fix-session-card-is-not-a-follow-band

- [x] 1 Verify the item's premise against the tree: the card's height is the published bottom inset while
  navigating (`MapCanvasScreen.kt:2533-2536`, `:2362-2366`, status card suppressed at `:2302`) — premise false,
  `#97` closed as mis-diagnosed with the probe (`evidence/probe97.md`, `evidence/probe97-junit.xml`).
- [x] 2 Name the remaining gap: the FREE_DRIVE probe resolves `fy = 0.9` because the card is not a follow band, and
  `smooth-follow` does not say so.
- [x] 3 Copy the machine-local probe evidence into `evidence/`, so the numbers a closure cites are versioned.
- [x] 4 Delta: one `ADDED` requirement with two scenarios (the card is not a follow band in free driving; it is the
  band while navigating), citing `route-planning-session`'s camera lease rather than restating it.
- [x] 5 Structural checks (documentation-only delta: no build, no suite):
  `openspec validate fix-session-card-is-not-a-follow-band --strict` — valid;
  `openspec validate smooth-follow --type spec --strict` — no new `>500 characters` warning (8 before, 8 after);
  `bash tools/check-doc-routes.sh` — all 152 section references and all 4 skill references resolve.
- [x] 6 No case claimed: the spec's own convention carries case-less scenarios (13 `- **Case**:` lines against 51
  scenarios), and no shipped case asserts the bottom band while the session card is open — the navigating half was
  measured by the throwaway probe, whose copy is in `evidence/`.
