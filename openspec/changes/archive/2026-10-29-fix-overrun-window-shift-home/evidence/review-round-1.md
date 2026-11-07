# Review round 1 — independent, read-only (`delegate`, 2026-10-29)

Verdict: **FAIL** — one blocking item, three prose counts, two missing evidence carriers.

- **A** PASS — both deltas carry their requirement verbatim from the shipped spec
  (`openspec/specs/smooth-follow/spec.md:402-436`, `openspec/specs/auto-smooth-follow/spec.md:227-249`),
  changing only the helper name; the named cases exist (`FollowBlitCallSiteTest.kt:76`, `:117`,
  `MapCanvasViewModelVehicleAnchorTest.kt:603`; the auto scenarios by `AutoMapRendererBlitTest`'s
  resolved-anchor, browse-center and marker-rides-content cases).
- **B** PASS — `OverrunWindow.kt` is byte-identical to the removed `FollowPrediction.displayOffsetPx`
  (base `87cc95de:380`): same parameter order, `anchorX/anchorY = 0.5`, rotation, margin
  `coerceAtLeast(0.0)`, `DisplayOffset` fields; only the receiver, the name and reflowed KDoc differ.
- **C** PASS — no live `displayOffsetPx` in `*.kt`; the six live `shiftPx` sites take the right anchor
  (follow = resolved, pan/coverage/browse = the `0.5/0.5` default).
- **D** PASS with notes — no dead code, no stale import; `guidelines/MapRendering.md` fully repointed.
  Noted: the two shipped spec lines still name the old helper, which the deltas replace at archive, so
  `tasks.md`'s "no live mention in `*.md`" was overstated.
- **E** PARTIAL — red-on-HEAD, green guard, revert-check mutation and restore, and the gate all match their
  files. **Blocking:** `evidence/evidence.md` listed shell steps the gate profile never selected
  (`tools/loop-mutations.sh --check`, `tools/check-portability.sh`, the issue tooling cases,
  `gen-feature-list --check-classification`) and omitted two that ran. Also: "eleven geometry cases" (the
  tree has ten), "12 files rewritten" against the same paragraph's enumeration of 17, and no carried output
  for the three focused runs and the four hand-run checks.

Corrections applied in response (round 2 re-read them):

1. `evidence/evidence.md` §4 now names the six steps `shell-gate.log` records and gives the four hand-run
   checks their own section with `evidence/checks.txt` behind them.
2. "eleven" → ten cases in `proposal.md`, `design.md`, `tasks.md`; the surface sentence now counts the 16
   Kotlin files it enumerates.
3. The focused runs carry their XMLs beside the evidence file; `tasks.md` 5.1 states what the two remaining
   `*.md` mentions are.
