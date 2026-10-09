# Proposal — fix-highlight-longest-run

## Why

Root cause: in `tools/measure-highlight.py`'s row scan (`find_casing`, HEAD `:72-73`) the per-row state
`run`/`first`/`last` only ever describes the **last** colour run the scan ended on — on a row that ends
on a match `run == last - first + 1`, and on a row that ends on a gap `first`/`last` still hold that last
run's extent while `run` is 0 — so the row test
`if run >= MIN_RUN_PX or (last - first + 1) >= MIN_RUN_PX:` compares `MIN_RUN_PX` against the row's
**rightmost** run for both terms, and a row whose longest run is not its rightmost one is dropped.

Evidence:    `tools/measure-highlight-selftest.sh` case `longest-run-not-last` (fixture: one row with a
41 px casing run followed by a 5 px one) fails on HEAD — exit=1, `FAIL longest-run-not-last — measured
output does not carry "bbox": [10, 50, 5, 5]: {"image": "…", "highlight": null, "image_size": [200, 20],
"band_bottom": 20}` with `exit=2`; case `longest-run-per-row` fails too (`bbox": [60, 100, 6, 6]`,
`highlight_px": 41` instead of `[10, 100, 5, 6]` / `82`). The retained run:
`openspec/changes/fix-highlight-longest-run/evidence/red-on-HEAD.txt`, 2026-10-09T20:33:07Z, HEAD
`095e6ac`, `measure-highlight selftest: 5 passed, 2 failed`, harness exit 1. The same shape on a 1080×2400
canvas (a 41 px casing run with a 5 px one right of it, `--margin 126`) reads `{"…", "highlight": null,
"…"}`, **exit 2** on HEAD and `bbox=[300, 340, 900, 900] px=41` / `verdict: inside`, exit 0 after the fix;
`evidence/synthetic-samples-after-fix.txt` (2026-10-09T20:33:38Z) carries the **post-fix** run only, and the
HEAD side of that sample as it ran on the reviewer's round-1 re-check, not as a retained run here.

Repro:       bash tools/measure-highlight-selftest.sh

Spec:        `highlight-measurement` / A row qualifies by its longest colour run   Guideline:
`guidelines/Build.md` section 11 (Measuring a phone UI finding) — it names the script as the verdict and states
the precondition, but says nothing about how a row is qualified, so no guideline text contradicts the fix.

Found 2026-10-09 by `bugfix-loop` (iteration 12) while gating `TODO.md` §147, and filed as §169 with the
synthetic rows above; the device-side impact stays inference, which is why the proof is host-side.

## What Changes

- **`tools/measure-highlight.py`**: the row scan keeps the **longest** run it sees in that row (and that
  run's extent) rather than the run it ends on, and `find_casing` qualifies, counts and extends the box
  with that run. `MIN_RUN_PX` is untouched — the threshold's stated job ("low enough to see a short
  analysed leg, high enough to reject isolated label pixels") is unchanged, and §169 explicitly forbids
  raising it.
- **The row rule is stated where the code is** (`find_casing`'s docstring and the `MIN_RUN_PX` comment),
  and `.pi/skills/pixel-check/SKILL.md` names it in its dense-run bullet.
- **`tools/measure-highlight-selftest.sh`** (new): a device-free harness that builds its PNG fixtures with
  the `python3` standard library (`zlib` + `struct`, no ImageMagick, no device, no git), prints what each
  case measured and exits 0/1 — the `tools/declared-cases-selftest.sh` / `tools/gate-timings-selftest.sh`
  precedent, which `AGENTS.md` requires for a tool a skill uses. It is the red-on-HEAD proof and stays as
  the guard for the row rule.
- **`guidelines/Build.md` section 11** names that harness as the device-free cover for the detector (the older
  `.pi/skills/pixel-check/selftest.sh`, ImageMagick, stays).
- **Two stale `§143` pointers in the same skill are re-pointed at `§147`** — §147's own "Doc drift from the
  id change" bullet asks the change that repairs the detector to do it, and the two lines (`:45`, `:105` —
  the pre-fix numbering §147 records)
  quote the no-route observation, which §147 owns (§143 is the route-cost entry).
- **No change to the verdict/exit-code contract** (0 inside / 1 clipped / 2 no highlight), so its three
  existing pins — the module docstring (`:25-26`), `pixel-check/SKILL.md` (`:96`) and `Build.md` section 11
  (`:1416`) — are updated by nothing and a fourth copy is not added.

## Capabilities

### New Capabilities

- `highlight-measurement`: the repository's highlight detector — the rule by which a screenshot row is
  qualified as carrying the route's analysed-segment highlight. It owns the row rule only; the verdict and
  exit-code contract keeps its existing homes (module docstring, `pixel-check` skill, `Build.md` section 11).
  No existing capability states this: `openspec/specs/` mentions neither `measure-highlight` nor
  `pixel-check`, and `build-test-gate` owns the Gradle verification gate, not the phone-UI measurement
  script (checked with `grep -rn 'measure-highlight\|pixel-check\|screenshot' openspec/specs/` — no hits).

### Modified Capabilities

(none)

## Impact

Affected files (one production file plus its harness; no Gradle-visible source):

- `tools/measure-highlight.py` — `find_casing` row scan + its docstring, the `MIN_RUN_PX` and casing
  comments (26 insertions, 11 deletions).
- `tools/measure-highlight-selftest.sh` — new, 7 cases.
- `.pi/skills/pixel-check/SKILL.md` — the dense-run bullet names the row rule and the new harness.
- `guidelines/Build.md` section 11 item 2 — names the new harness as the device-free cover.
- `openspec/specs/highlight-measurement/spec.md` — new capability (archive-time merge of the delta).

Blast radius — every caller of the detector (found with
`grep -rn 'MIN_RUN_PX\|measure-highlight' tools/ .pi/skills/ guidelines/ openspec/specs/ openspec/config.yaml`):
the `pixel-check` workflow (`.pi/skills/pixel-check/SKILL.md`, which drives the on-device recipe and
records the verdict in `tasks.md`), `guidelines/Build.md` sections 2 and 11 (the look-then-measure rule),
`openspec/config.yaml`'s design guidance ("name the measurement that will settle it on device … the
`tools/measure-highlight.py` verdict") and `.pi/skills/pixel-check/selftest.sh`. None of them depends on
the rightmost run, and the arithmetic of the change is bounded:

- **No row is lost, and `px` never falls.** A row the old rule qualified had a rightmost run ≥
  `MIN_RUN_PX`; its longest run is ≥ that, so it still qualifies, and the count now adds a value ≥ the
  one it added before.
- **The right edge can move right, not only left.** A newly counted row's longest run can lie further
  right than the old box reached, which extends `x1` and can turn `inside` into a right-`CLIPPED` —
  **unmeasured**: reviewer round 1's fixture (400×200, `--band-bottom 200 --margin 60`; row y=70 a 9 px run
  x=70..78, row y=100 a 41 px run x=301..341 plus a 5 px run x=380..384) is the only evidence (HEAD
  `bbox=[70, 78, 70, 70] px=9` / `inside` exit 0 → fixed `bbox=[70, 341, 70, 100] px=50` / `CLIPPED at
  right` exit 1) and it is not retained in this change.
- **A newly counted row can raise `CLIPPED` where the old rule read `inside`** — *arithmetic, not measured*
  (no harness case asserts it). A row that was dropped may lie nearer a margin than the old box; that is
  the fix, not a regression — the row was highlight evidence the detector ignored, and §169 records the
  false-`CLIPPED` half of the same defect.
- The two synthetic samples in the repository: the §169 row (`bbox=[300, 340, 900, 900] px=41`, exit 0 after
  the fix, the HEAD side reproduced by the reviewer's round-1 re-run) and §147's no-route frame (18 px over
  two rows — each of its rows has one 8 px and one 10 px run, both already over the floor — re-measured on
  the **fixed** tool only; the file carries no HEAD run of it).

No app code, no Kotlin/Java, no JNI, no manifest, no flavor, no dependency, no native change: both
distribution flavors are untouched, and the change is additive and reversible (restore the old
`find_casing` body and delete the harness).

Rollback: `git revert` of the change commit — the detector and its documentation revert together; nothing
else reads the detector's internal row rule.
