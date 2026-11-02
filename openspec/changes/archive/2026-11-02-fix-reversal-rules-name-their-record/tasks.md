# Tasks

Reference: `proposal.md` for the five rule sites and the measurements, `design.md` for the fix's shape, and
`evidence/red-on-HEAD.xml` for the red case. No spec delta: the item's `Fix` is additive with no new claim and no
case, and its `Rejected alternative` is the rule form this change does not take.

## 1. Red evidence

- [x] 1.1 Run `bash evidence/reversal-pointer-probe.sh --out evidence/red-on-HEAD.xml` on base `e190d872` and record
      `evidence/red-on-HEAD.log`. Verify: the tally reads `tests=5 failures=5`, and each of the five rule blocks is
      named in the log as carrying no `Design.why.md` pointer.

## 2. The three rules `DesignCrossVariant.md` carries

- [x] 2.1 Add the pointer sentence inside section 8a's "Every car guidance surface reads the step model" block. Verify:
      `bash evidence/reversal-pointer-probe.sh` names that anchor as one of the passing sites.
- [x] 2.2 Add the pointer sentence inside section 8a's host-constraint bullet ("Prefer host-rendered panels"). Verify: the
      same probe run names that anchor.
- [x] 2.3 Add the pointer sentence inside section 8b's "Rule: phone and AAOS are separate build flavors under one
      applicationId". Verify: the same probe run names that anchor, and `tools/check-doc-routes.sh` still exits 0 (no
      rule name added, the reference resolves).

## 3. The two rules `MapFollow.md` carries

- [x] 3.1 Add the pointer sentence to section 7's opening statement ("Bearing smoothing lives in `LocationService`
      (`BearingFilter`), NOT in the ViewModel"). Verify: the probe passes that anchor.
- [x] 3.2 Add the pointer sentence inside section 8's "Rule: Render target". Verify: the probe passes that anchor and
      `tools/check-doc-routes.sh` still exits 0.

## 4. Structural check instead of a build

- [x] 4.1 Run the gate the pathspec owes — `./tools/loop/gate.sh` — once. Verify: every documentation suite passes
      and its tally is recorded; no Gradle build runs, because no compiled path changed.
- [x] 4.2 Copy every cited XML into `evidence/` and run
      `bash tools/loop/evidence-check.sh openspec/changes/fix-reversal-rules-name-their-record --root .`. Verify:
      the checker reports no dangling pointer, and its output is archived as `evidence/evidence-check.log`.

## 5. Archive

- [x] 5.1 Archive the change and confirm the directory exists under `openspec/changes/archive/`. Verify: the
      archived copy carries `proposal.md`, `design.md`, `tasks.md` and `evidence/`, and no capability spec changed
      (the delta adds none).

## 6. Correction round 1 (reviewer refused the archived claim)

- [x] 6.1 Narrow the archive's claim to the five sites the delta edits. Verify: `proposal.md`'s title, its root-cause
      sentence and `design.md`'s root-cause section name these as five of the nine reversals
      `sed -n '7,71p' guidelines/Design.why.md | grep -c '^- '` counts, and `proposal.md`/`design.md` carry a residual
      table for the four left unpointed (`guidelines/MapRendering.md:95`, `:101`, `guidelines/UIPhoneMap.md:124`,
      `guidelines/UICar.md:106`). No new claim, case or assertion.
- [x] 6.2 Archive the evidence checker's output: `evidence/evidence-check.log`, from
      `bash tools/loop/evidence-check.sh openspec/changes/archive/2026-11-02-fix-reversal-rules-name-their-record --root .`.
      Verify: the file ends `evidence-check: PASS`, with `C1 §references:` and `C2 test tallies:` confirmed at
      the counts the file records, and no `FAIL`. (The counts rise as evidence is added, so this task pins the
      verdict and the checker's own output, not a number.)
- [x] 6.3 Archive the producer of the two section figures the archive quotes: `evidence/section-sizes.log`,
      `tools/check-doc-routes.sh --sizes`. Verify: the log carries `§8a 10540 bytes` and `§8b 3038 bytes`, the
      two numbers `design.md`'s blast radius states.

## 7. Correction round 2 (the probe's own root, found when the round was re-run)

- [x] 7.1 Make the probe's default root the tree that holds `guidelines/`, so the archived change's own path resolves
      without a flag. Verify: `evidence/probe-default-root.log` carries three runs — the default root from the archived
      path (`tests=5 failures=0`), the same script with `--root .pi/scratch/730-base` over a detached checkout of base
      `e190d872` (`tests=5 failures=5`, the red case), and `--root .` (`tests=5 failures=0`).
- [x] 7.2 Re-run the evidence checker over the corrected directory and archive it. Verify:
      `evidence/evidence-check.log` ends `evidence-check: PASS` and carries no `FAIL`.
- [x] 7.3 Apply the eight NOTEs review round 2 returned (no blocking finding): the archived checker counts in 6.2,
      `UIPhoneMap.md:124` for the `§124` typo, "three pointer sentences" for "three added lines", the sibling-site
      clause in `proposal.md`'s residual paragraph, the host-rendered-panel pointer's subject in
      `DesignCrossVariant.md` §8a, and the probe's two XMLs re-run from this worktree instead of the loop's. Verify:
      `evidence/after-change.xml` and `evidence/red-on-HEAD.xml` name this worktree and `.pi/scratch/730-base`, the
      tallies are unchanged (`5/0` green, `5/5` red), and every check below stays green.
