# Tasks — fix-feature-list-coverage-claim

## 1. Red proof on the base commit

- [x] 1.1 Verify the claim in the tree: `guidelines/FeatureList.md:143` (coverage row), `:116-117` (its case
      name), `:109-112` (the argument) against the tool's actual refusal
      (`tools/gen-feature-list.sh:703,717`, case `tools/gen-feature-list-selftest.sh:409`) and against the
      requirement that decides the direction
      (`openspec/specs/feature-list-generation/spec.md:115-121`).
      — confirmed 2026-10-21: the row and the case name state a refusal the tool does not perform; the tool's
      refusal is over `$required`, the highlights whose spec set is shipped.
- [x] 1.2 Measure the set the old row claims is refused and the tool never looks at:
      `jq` over `tools/feature-list/specs.json` and `tools/feature-list/highlights.json`.
      — 2026-10-21T11:09:12Z: **39** user-visible shipped specs named by no highlight (the item measured 38 on
      2026-10-13; the set grew with an archived spec), 19 highlights;
      `evidence/measurement-unreached-specs.txt`.
- [x] 1.3 Record the red case — the structural probe, since this delta touches no compiled file:
      `bash evidence/red-probe.sh --root <tree at 44b673f4>` (a `git archive` extraction, so the review diff is
      not the input).
      — 2026-10-21T11:08:52Z: exit **1**, `stated cases: 39, without a carrying case: 7`, each named;
      `evidence/probe-red-on-base.txt`.

## 2. The fix — the prose side only

- [x] 2.1 §6: the coverage row refuses `a selling point that no entry reaches`; the `source marker` row is left
      as it is.
      — `guidelines/FeatureList.md`, gate table row 2.
- [x] 2.2 §4: the `userVisible` bullet says what the shortlist decides and that the run fails only on a named
      selling point; the coverage case leaves §4's case list (it is named in §1, where the rule lives).
      — `guidelines/FeatureList.md` §4, bullet 3 and the case list below it.
- [x] 2.3 §1: delete the second `Cases:` block — the three `density:` names and the density report have no
      check behind them in the tool or in the spec; the length sentence above keeps the cases that exist.
      — `guidelines/FeatureList.md` §1.
- [x] 2.4 §5: the case list names `catalogue: a dual-surface capability tags both surfaces`,
      `catalogue: the car-only area is rendered under its own heading` and
      `catalogue: a dual-surface capability appears once`, the cases that exist there.
- [x] 2.5 §6: `gates: the keyless bullet is named` → `gates: the keyless entry is named`, the name
      `tools/gen-feature-list-selftest.sh:378` prints.
- [x] 2.6 `.pi/skills/feature-list/SKILL.md` §3 and §4 follow the same two statements (a catalogue section
      covers the shortlisted capabilities of its area; the failure list says "a selling point no entry
      reaches").
- [x] 2.7 No tool, no spec, no compiled file changes — confirmed by `git diff --stat`: two prose files.

## 3. Green — the probe and the suites that own the surviving names

- [x] 3.1 The probe on the changed tree.
      — 2026-10-21T11:09:36Z: exit **0**, `stated cases: 35, without a carrying case: 0` (re-taken after the
      last prose edit); `evidence/probe-after-fix.txt`. Re-taken after the round-2 correction below:
      2026-10-21T11:13:18Z, exit **0**, `stated cases: 35, without a carrying case: 0`;
      `evidence/probe-after-correction.txt`. The count is unchanged because the deleted sentence's own
      backticked `areas.json` had been counted as a case name and the real case replaces it.
- [x] 3.2 `bash tools/gen-feature-list-selftest.sh`.
      — 2026-10-21T11:09:36Z: **105 passed, 0 failed**, exit 0;
      `evidence/gen-feature-list-selftest-after-fix.txt`. The suite is untouched by this change, so this run
      confirms the names the prose now states are the names it prints.
- [x] 3.3 `bash tools/check-doc-routes.sh` — the Documentation Map still resolves after the edits.
      — 2026-10-21T11:09:48Z: `all 89 section references resolve`, exit 0;
      `evidence/doc-routes-after-fix.txt`.
- [x] 3.4 `bash tools/gen-feature-list.sh --check-classification` — the measurement of 1.2 stays reproducible
      and the classification is untouched.
      — 2026-10-21T11:09:10Z: 176 specs read, 176 classified, 0 unclassified, 0 stale, 19 highlights, exit 0;
      `evidence/classification-after-fix.txt`.

## 4. Gate shape — why no Gradle run

- [x] 4.1 State the scope-block justification (`guidelines/Build.md` §2, "Scope — what the changed files owe"):
      the delta is two Markdown files and this change's artifacts; it touches no compiled file, no Gradle input,
      no test source and no data feed. The structural probe (1.3/3.1) is the change's red/green pair and the two
      tool gates plus the classification gate are its structural checks, so the run's Gradle gate is not owed —
      and it is not claimed.
      — recorded 2026-10-21. **Exclusions named:** `./gradlew test -PforceTests --no-build-cache` was not run;
      nothing it compiles is touched by this delta. No device, no emulator, no model call.

## 5. Archive

- [x] 5.1 `openspec validate fix-feature-list-coverage-claim --strict` is green.
      — 2026-10-21, the run before the archive: `Change 'fix-feature-list-coverage-claim' is valid`, the
      `skip_specs` INFO line accepted; exit 0.
- [x] 5.2 The change directory is moved under `openspec/changes/archive/2026-10-21-…` with `skip_specs: true`
      and the evidence inside it; no capability spec changes, so no delta is merged.
      — 2026-10-21: `openspec archive fix-feature-list-coverage-claim --yes` → "archived as
      '2026-10-21-fix-feature-list-coverage-claim'"; `openspec/changes/` now holds only `archive/`.

## 6. Round 2 — the reviewer's single blocking correction

- [x] 6.1 §5 no longer claims that "one capability one area is a property of `areas.json`, not a check that could
      fail": `tools/gen-feature-list-selftest.sh:328` is that check (`catalogue: a dual-surface capability appears
      once`, a count of the rendered entry). The claim is deleted and the case is named in the list instead.
      The same sentence in the archived `design.md` (D2's car-placement row) is replaced by the three case names
      with their line numbers.
- [x] 6.2 Structural re-check after the edit, prose only, no build owed (`guidelines/Build.md` §2 scope block):
      `bash tools/check-doc-routes.sh` → all 89 section references resolve, exit 0, 2026-10-21T11:13:18Z
      (`evidence/doc-routes-after-correction.txt`); `bash tools/gen-feature-list-selftest.sh` → 105 passed,
      0 failed, exit 0, 2026-10-21T11:13:33Z (`evidence/gen-feature-list-selftest-after-correction.txt`); the
      probe → exit 0, `stated cases: 35, without a carrying case: 0`, 2026-10-21T11:13:18Z
      (`evidence/probe-after-correction.txt`).
      `./gradlew test -PforceTests --no-build-cache` again not run and not claimed — the delta is still two
      Markdown files and this change's artifacts.
