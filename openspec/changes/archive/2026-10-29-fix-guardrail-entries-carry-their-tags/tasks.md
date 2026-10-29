# Tasks — fix-guardrail-entries-carry-their-tags

Every task names its own verification. No compiled file is touched, so no Gradle task appears: the text probe
and the documentation checks stand in for the build (`guidelines/Build.md` §2, the scope block). Parent
requirement: `documentation-ownership`, *A recorded guardrail is stated in the section that owns it*
(`openspec/specs/documentation-ownership/spec.md:390-406`); the red case is
`guardrail-tag#eachCarriedEntryCarriesItsRecordTag`.

## 1. The red case on HEAD

- [x] 1.1 Write `evidence/red-probe.sh`: per entry, locate the owning section by its heading text and require
      the entry's `§40.NN` tag inside it, text only — no build, no device, no network. Verify: on the unedited
      tree it prints `tests=3 failures=3`, names §40.38, §40.46 and §40.55, and exits 1; captured as
      `evidence/red-HEAD.txt` with the run time in its header, and as the JUnit XML `evidence/red-on-head.xml`
      (`tests="3" failures="3"`) so the quoted tally is carried by an XML.

## 2. Tag the entry whose rule is already stated

- [x] 2.1 Append `(§40.46)` to the concurrent-writer clause of `guidelines/Build.md:158-160` (§2a "Agent iteration
      protocol"). Verify: the probe's §40.46 line changes from `MISSING` to `STATED`.
- [x] 2.2 Append `(§40.55)` to the stylesheet-verification bullet, `guidelines/MapRendering.md:899` (§15a).
      Verify: the probe's §40.55 line changes from `MISSING` to `STATED`.

## 3. Carry the entry whose rule is missing

- [x] 3.1 Add one bullet to `guidelines/Build.md` §4d "Attributing a red run": a flake of unknown origin gets an
      entry with its rerun evidence, never a silent retry, tagged `(§40.38)`, naming `:591`'s "this tree keeps
      no flake list" as the reason and pointing at §6d for the `FavoritesSheetReorderComposeTest` example.
      Verify: the probe's §40.38 line changes from `MISSING` to `STATED`, and the added bullet states the tag
      and the rule on adjacent lines (`sed -n` of the section reads both).

## 4. Green and falsified

- [x] 4.1 Re-run the probe on the fixed tree. Verify: `tests=3 failures=0` and the
      `PASS guardrail-tag#eachCarriedEntryCarriesItsRecordTag` line, captured as `evidence/green-after.txt`
      with its run time in the header and as `evidence/green-after.xml` (`tests="3" failures="0"`).
- [x] 4.2 Falsify the probe with exactly one mutation (`guidelines/Build.md` §4 "One mutation per revert
      check"): delete the `(§40.55)` tag from `guidelines/MapRendering.md`. Verify: the probe fails and names
      §40.55; restore the tag and the probe passes again — recorded verbatim in `evidence/revert-mutation.txt`.

## 5. Structural checks (in place of the build)

- [x] 5.1 Run the documentation checks the pathspec owes: `tools/check-doc-routes.sh`,
      `tools/check-todo-references.sh`, `tools/check-openspec-hygiene.sh`,
      `tools/gen-feature-list.sh --check-classification`, and `openspec validate
      fix-guardrail-entries-carry-their-tags --strict`. Verify: each exits 0 and the outputs are recorded in
      `evidence/checks.txt`.

## 6. The gate

- [x] 6.1 Run `./tools/loop/gate.sh` once from the change root. Verify: `LOOP-GATE` green with its tally block,
      `evidence/gate.log` and `evidence/gate-tallies/` hold the run, and every failure count is 0.

## 7. Evidence and archive

- [x] 7.1 Copy every cited log into `openspec/changes/fix-guardrail-entries-carry-their-tags/evidence/`.
      Verify: each path cited by the proposal, this file and the report exists under `evidence/`.
- [x] 7.2 Archive the change and re-point the evidence check at the archived path:
      `bash tools/loop/evidence-check.sh openspec/changes/archive/<date>-fix-guardrail-entries-carry-their-tags
      --root .`. Verify: the directory exists under `openspec/changes/archive/`, the delta's `skip_specs`
      leaves `openspec/specs/` untouched, and the evidence check reports no dangling pointer.
