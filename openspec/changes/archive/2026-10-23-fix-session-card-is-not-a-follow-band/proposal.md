# fix-session-card-is-not-a-follow-band

## Why

`#97` claimed that with the route-planning session card open during navigation the published bottom inset still
described the routing-status card. Measured on 2026-10-22, that is false: the card's own measured height **is** the
published bottom inset (`MapCanvasScreen.kt:2533-2536` writes `overlayBottomInset`, `:2362-2366` publishes it under
`isNavigating`) and the status card's writer (`:2313`) is suppressed by `!state.showRoutePanel` (`:2302`) — one
writer per inset. `#97` was closed as mis-diagnosed with that measurement (`status/retired`).

What the measurement also showed is a wording gap, not a behaviour gap. `smooth-follow` says the regions the
phone's own chrome covers are measured from "the chrome the surface **currently composes**", and its bottom-band
scenarios name the routing-status card and the next-turn card. A `RoutePanel` card composed in FREE_DRIVE is chrome
the surface currently composes, yet it is **not** a band the follow anchor resolves against: the FREE_DRIVE probe
resolves the bottom preset to the raw `fy = 0.9`. A reader of `smooth-follow` alone cannot tell whether that is
intended, and the sentence it carries reads as if it were not.

## What changes

One `ADDED` requirement, "A band the follow anchor reads is one the surface composes while following", with two
scenarios: the session card is not a follow band (and why — the session owns the camera, per
`route-planning-session`, "Session holds the camera while active"), and, for the contrast, the same card **is** the
band while navigating, so the refuted reading of `#97` cannot be re-derived from this spec.

No code. The behaviour the delta states is the shipped one; the change only makes it readable and states the rule
in the terms a later reader needs.

## Evidence

- `evidence/probe97.md` and `evidence/probe97-junit.xml` — the probe that settled `#97` (case
  `probeThePublishedBottomInsetWhileTheSessionCardIsOpenDuringNavigation`, `tests=2 failures=0`,
  2026-10-22T11:27:24Z). Copied out of `.pi/issue-loop/items/97/`, which is machine-local and not versioned, so the
  numbers a closure cites survive the run.
- Measured numbers: navigating with the card open `fy=0.5181818182` (card 378 px) · card closed
  `fy=0.7707070707070708` (status card 128 px) · FREE_DRIVE with the card open `fy=0.9` unchanged.
- Code read: `MapCanvasScreen.kt:2302`, `:2313`, `:2362-2366`, `:2533-2536`; `MapCanvasViewModel.kt:3383-3384`
  (opening the session clears `followMode`).
- Spec read: `openspec/specs/smooth-follow/spec.md` ("Vehicle position anchor in follow mode", its inset sentence
  and the three band scenarios), `openspec/specs/route-planning-session/spec.md` ("Session holds the camera while
  active", scenario "the map stops following while the session is active").

## Owning capability and guideline section

`AGENTS.md` → "how the map is drawn" names `MapRendering.md` §3, §6, §7 for follow mode, bearing and anchors, and
§§ for the phone UI are `UI.md` §7. The owning capability is `smooth-follow`; the neighbour whose rule the delta
cites is `route-planning-session`.

## Scope

`surface: 0 cases added, 0 cases rewritten, 0 fixtures realigned` — documentation-only delta. The spec's own
convention carries case-less scenarios (13 `- **Case**:` lines against 51 scenarios), and the behaviour this delta
states is already asserted by the shipped suite indirectly; no new case is claimed.
