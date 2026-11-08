# Tasks

Reference: `proposal.md` for scope and the red run, `design.md` for why,
`specs/highlight-measurement/spec.md` for the contract.

## 1. The red cases

Requirement: "A verdict requires the same-moment frame state", scenarios "A frame with no same-moment state is
refused", "Diagnostics without the analysed-segment line are refused", "A band that ends at the canvas bottom is no
card measured" and "A band the two moments disagree on is refused".

- [x] 1.1 `tools/measure-highlight-selftest.sh`: write a `segment focus:` state fixture per case, pass `--state`
      to the 7 cases that state the row rule, add `glyph-pair.png` (two 9 px rows, 18 px — issue #90's shape) and
      the four refusal cases `no-state`, `no-focus-line`, `no-card`, `band-disagrees`.
      Verify: on the base tool `b9ff0171` the harness reports `measure-highlight selftest: 0 passed, 11 failed`,
      exit 1 (2026-11-08T11:34:56Z) — `evidence/red-on-HEAD.log`, tally `tests=11 failures=11 errors=0` in
      `evidence/red-on-HEAD.xml`; the four new cases name the refusal they expect and get a box instead
      (`no-state`: `verdict: inside`), the seven old ones die on
      `measure-highlight.py: error: unrecognized arguments: --state`.
- [x] 1.2 The plan is ready before production work: `tools/check-change-readiness.sh
      openspec/changes/fix-highlight-verdict-needs-analysed-segment` → `LOOP-CHANGE-READY: ok` (2026-11-08).

## 2. The detector

- [x] 2.1 `tools/measure-highlight.py`: `--state` (the same-moment logcat capture), `FOCUS_BAND` +
      `segment_focus_band()` reading the last `MapCanvasVM: segment focus: … band=[0,N]` line, `refused_reason()`
      deciding `no-state` / `no-focus-line` / `no-card` / `band-disagrees` before the pixels are read, the refusal
      printed as `verdict: REFUSED (<reason>)` with `"highlight": null` and `"refused"` in `--json`, exit 2, and
      the docstring's usage and exit contract.
      Verify: `bash tools/measure-highlight-selftest.sh` → `measure-highlight selftest: 11 passed, 0 failed`,
      exit 0 (2026-11-08T11:36:37Z, re-run on the final file; `evidence/green-on-change.log`,
      `evidence/green-on-change.xml`).
- [x] 2.2 The green is the detour, not the harness: the same harness run beside the base tool (copied with
      `git show b9ff0171:tools/measure-highlight.py` into the item's scratch) reports `0 passed, 11 failed`
      (`evidence/revert-check-base-tool.log`). Issue #90's own command now refuses: `python3
      tools/measure-highlight.py glyph-147.png --band-bottom 2400 --margin 126` → `verdict: REFUSED (no-state)`,
      exit 2; with a state line whose band is the canvas bottom → `REFUSED (no-card)`, exit 2
      (2026-11-08T11:35:40Z, `evidence/repro-147-after.log`).

## 3. The other surfaces

- [x] 3.1 `.pi/skills/pixel-check/selftest.sh`: the same state fixture and `--state` on its 3 checks.
      Verify: `bash .pi/skills/pixel-check/selftest.sh` → `ok inside -> inside=true`, `ok behind-card ->
      inside=false`, `ok dot -> no highlight`, `selftest FAIL=0`, exit 0 (`evidence/skill-selftest-on-change.log`).
- [x] 3.2 The sites that name the command and the rule: `.pi/skills/pixel-check/SKILL.md` (recipe, output block,
      `--state` bullet, the precondition paragraph, rule 1, the surface table), `guidelines/Build.md` §1
      "Looking at a screenshot" (the recipe line), `guidelines/BuildEvidence.md` section 11 rule 2 (the precondition
      paragraph), `guidelines/BuildEvidence.why.md` section 11 (the measured example). Each now states the refusal the
      detector makes; the §147 record keeps its `TODO.md` pointer and gains the issue number.
      Verify: the structural check of this delta — it compiles nothing — is `bash -n
      tools/measure-highlight-selftest.sh` and `bash -n .pi/skills/pixel-check/selftest.sh`, both silent, plus the
      two harnesses above (the doc checks that read these files, `tools/check-doc-routes.sh`,
      `tools/check-todo-references.sh`, `tools/check-openspec-hygiene.sh` and `tools/check-rule-routes.sh`, are the
      gate's selection — `LOOP-MEASURE-CHECK` lines of `./tools/loop/measure.sh --change
      fix-highlight-verdict-needs-analysed-segment` — and are run by the gate child, not here).

## 4. Verification

- [x] 4.1 `tools/check-change-readiness.sh openspec/changes/fix-highlight-verdict-needs-analysed-segment` →
      `LOOP-CHANGE-READY: ok`; `./tools/loop/measure.sh --change
      fix-highlight-verdict-needs-analysed-segment` reports `scenarios=4 cases=4 unnamed=0`.
- [x] 4.2 The gate is not this child's: the run defers it to the child that follows the diff review, and its
      selection is the `LOOP-MEASURE-CHECK` lines above. That child ran it once and it passed — 54 tests, no
      failure, no error — recorded in `evidence/gate-output.log` with the ten per-step XMLs under
      `evidence/gate-tallies/` (2026-11-08).
