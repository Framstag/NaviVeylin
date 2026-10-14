# Design — fix-ui-reroute-numbers

## Context

See `proposal.md` — Why for the root cause, the red case and the authority of the code side. The two facts
that shape the approach:

- The bullet is the guideline's **only** statement of the reroute policy's numbers
  (`grep -n "reroute" guidelines/UI.md` → `:870` is the only policy bullet; `guidelines/Design.md` states none
  of these numbers), so the fix is one edit at one place, not a sweep.
- The bullet's numbers are derivable from the code's constants (`NavigationEngine.FAST_PATH_DISTANCE_M`,
  `MIN_OFF_ROUTE_DURATION_MS`, `REROUTE_COOLDOWN_MS`, `MAX_REROUTE_ACCURACY`, `TUNNEL_REROUTE_GUARD_MS`), which
  makes a conformance case cheaper and stricter than a reader's memory — the shape
  `FavAutoZoomClampRangeTest` established for a spec that restated a code-derived number.

## Goals / Non-Goals

**Goals:**

- The bullet states the policy the single engine applies (30 m / 4 s / 25 s / 100 m / 30 s).
- A policy move fails a named case instead of leaving the guideline stale again.

**Non-Goals:**

- No engine, gate or spec change: the shipped requirement already states the new numbers.
- Not a general "guideline numbers must equal code constants" harness: this change guards the one bullet whose
  numbers are code-derived and restated. The other numbers in the document (§1's parity table, §8's 48 dp and
  sp minimums) are spec'd, not constant-derived, and stay out.
- No change to the bullet's historical clause about the car's former path (accurate, see proposal.md), and no
  addition of the spec's ~9 s end-to-end figure.
- No edit to the "Keeping this document honest" section. It names the §1 parity table and the §2 glyph table as
  the sections a spec change updates; the §8 bullet had no such pointer, and this change's guard case is what
  closes that hole for this bullet (see Risks).

## Decisions

**D1 — The guideline is the wrong side; the code and the spec are right.** The evidence eliminates every other
reading: the spec states 4 s (`openspec/specs/reroute-trigger/spec.md:10`) and 30 m (`:25`), the engine applies exactly those
(`NavigationEngine.kt:1141,1143`, gate built at `:206-211`), and the archived change records the move
(`openspec/changes/archive/2026-10-12-tune-reroute-trigger-latency/proposal.md:27,29`). A stale spec would show the old numbers
in `openspec/specs/reroute-trigger/spec.md`; it does not.

**D2 — The fix is the two numbers, not a reference.** *Alternative eliminated:* delete the numbers and point at
spec `reroute-trigger` under the `documentation-ownership` rule "A rule has one normative home"
(`openspec/specs/documentation-ownership/spec.md:136`). Eliminated because `UI.md`'s own honesty section
(`:1266`) declares this document the condensed knowledge base, and §8 already carries a code-derived number
with its guard named (`:839-845`, the density rule and `CompassNeedleStrokeTest`) — the reference-only form
would leave the guideline's reader ("the timing is the same whichever surface displays the session") without
the timing. *Alternative eliminated:* change the spec or
the constants to match the guideline — nothing supports it; the 30 m / 4 s pair is the deliberate outcome of the
archived tuning change.

**D3 — The guard case lives in `com.naviveylin.navigation` and runs without Robolectric.**
`ReroutePolicyGuidelineParityTest` sits where the constants it reads are declared, next to
`RerouteConfirmationGateTest` (which already builds the gate from `NavigationEngine`'s constants). It reads
`File("../guidelines/UI.md")` — an `:app` unit test's working directory is the module directory, the
`FavAutoZoomClampRangeTest` convention — and needs no runner: the five constants are `const val`s (inlined) and
the rest is a file read. *Alternative eliminated:* placing it in `com.naviveylin.ui.map`, where the two
precedents live — they guard spec files in their own capability's area, while this case's code side is the
navigation engine.

**D4 — The case matches each number by its label, not by position.** The bullet deliberately keeps the car's
retired `25 m / 15 s / 10 s`-interval numbers as history, so a "the bullet states exactly five quantities"
assertion (the precedent's shape) would be wrong here; a positional read would compare the policy against
history. Each number is matched as `<value> <unit> <label>` (`30 m fast path`, `4 s confirmation floor`, `25 s
cooldown`, `100 m accuracy guard`, `30 s tunnel guard`), and a missing label fails naming the label and the
bullet rather than defaulting. Consequence: the words `fast path`, `confirmation`, `cooldown`,
`accuracy guard` and `tunnel guard` are the case's contract with the bullet — rewording the bullet's labels is
a deliberate test edit, and the failure message says so.

**D5 — The bullet names the constants and the guard case** (`NavigationEngine`'s five constants;
`ReroutePolicyGuidelineParityTest`). This is what makes the guideline carry a rule ("this paragraph is the
engine's policy, and this case holds it") instead of a bare restatement, in the style §8 already uses for the
density rule and its case. It adds no new number, so the case reads the same five labels.

**D6 — `skip_specs: true`.** No requirement text changes: `reroute-trigger` already states the corrected
numbers, and the delta is a guideline bullet plus a test source. Precedent with the same reasoning:
`archive/2026-10-10-fix-shipped-spec-classification` (`.openspec.yaml` `skip_specs: true`; its Capabilities
section reads "no requirement text changes, so this change declares `skip_specs: true`").

**D7 — Gate shape.** The delta adds a test source and edits a guideline: the scope block
(`guidelines/Build.md` §2) owes the new case plus the affected cases through the module filter, in **both**
`:app` flavors (the file is shared source, so both compile and run it), not the suite. Exclusions named in
`tasks.md`.

## Risks / Trade-offs

- **The case is brittle to the bullet's wording** → the labels are the assertion's contract; a reworded bullet
  fails with the label and the bullet text in the message, which is the intended prompt to update both sides
  in one change (D4).
- **The guideline's honesty section still enumerates only §1/§2** → the guard case now covers this bullet; whether
  the section should name §8's policy bullet (or every code-derived number) is a documentation-process question
  with its own blast radius. Filed as `TODO.md` §200, not decided here.
- **"Confirmation window" becomes "confirmation floor"** (D4's labels) → the spec's term for the 4 s, and the
  gate's own window is a different number (60 s); a reader who keeps the old word next to
  `REROUTE_CONFIRM_WINDOW_MS` would map it to the wrong constant.
- **A green focused run says nothing about the suite** → the gate task states the scope and names the exclusions
  instead of implying a suite run.

## Migration Plan

Additive, no migration: one guideline bullet and one new test class. Rollback: restore `:872`'s two numbers
(`50 m fast path, 10 s confirmation window`) and delete the test file; nothing else in the tree depends on
either.
