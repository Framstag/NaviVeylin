## 1. The red case

- [x] 1.1 Reproduce on the base commit: `bash tools/gen-feature-list.sh --release 2026-10-10-1` against a copy of
      `tools/feature-list/` (so the tree's cache and snapshots are untouched) exits 1 with seven catalogue
      paragraphs at 321–322 characters. Verification: `evidence/real-run-on-HEAD.log`, exit status and the seven
      `entry paragraph is …` lines.
- [x] 1.2 Write the cases in `tools/gen-feature-list-selftest.sh` — `within_bound`, an assertion over a written
      document measured as `gate.awk` measures it; a 399-character requirement name of nine-character words (its
      320th character is a space, so a clip that forgets the lead-in overshoots by exactly two); a baseline run for
      the note; and a 319-character requirement added in the second run to `delta`, a user-visible capability no
      highlight names, so the note gate sees it and the catalogue gate cannot fire in front of it. That run also
      drops a requirement of `beta`, so the removal list is not empty and the case does not stand on the `pair.awk`
      defect. Verification: `bash tools/gen-feature-list-selftest.sh` red on HEAD — `tests=108 failures=2`, the
      catalogue case failing with `entry paragraph is 322 characters` and the note case at the note gate with
      `entry keyword is 32 words, more than six` (`evidence/red-on-HEAD.log`).
- [x] 1.3 Measure the site the catalogue gate hid: with the catalogue clip fixed (`clip($max - 5)`) and the note
      sites untouched, a real run writes the catalogue and then exits 1 with 99 `entry keyword is N words, more than
      six` lines, release notes unwritten. Verification: `evidence/real-run-after-catalogue-fix.log`, exit status
      and the 99 lines.

## 2. The fix

- [x] 2.1 `render_mechanical` composes its paragraph to the bound the gate measures: the names are clipped with the
      lead-in and the full stop reserved (`clip($max - 5)`, spec `feature-list-generation` — "A paragraph stays
      short"). Verification: case `length: a mechanical catalogue entry is composed inside the bound` passes; the
      real run's longest catalogue paragraph is 317 characters.
- [x] 2.2 `entry_keyword` and `entry_paragraph` shape one requirement name into the entry shape, and both note
      branches use them (`changed` and `removed`, spec `feature-list-generation` — "Every entry leads with a keyword
      and a paragraph"). `entry_paragraph` reserves the wrapper the note entry prints around the name — `— ` and the
      full stop, five bytes, the same reserve as the clip (`$max - 5`) — and not the full stop alone. Verification:
      `length: a mechanical release-note entry is composed inside the bound` passes with a 319-character name, and
      fails at `entry paragraph is 322 characters … (line 11)` when the helper reserves `$max - 1`
      (`evidence/red-on-half-fixed.log`); the real run's note carries 144 entries, longest paragraph 81, no keyword
      above six tokens.
- [x] 2.3 Confirm the real run writes both documents and stays byte-identical on a rerun. Verification: two
      identical runs, exit 0 each (`evidence/real-run-after-fix.log`, `evidence/real-run-after-fix-2nd.log`), and
      the documents compared equal.
- [x] 2.4 Update `guidelines/FeatureList.md` §1 with the two case names and the sentence that the bounds hold for
      the entries composed without a model. Verification: `bash tools/check-doc-routes.sh` — every routed section
      still resolves.

## 3. Verification

- [x] 3.1 Revert-check, in this order: restore `tools/gen-feature-list.sh` from the base commit in place and run
      the suite — exactly the two cases fail (`evidence/red-on-HEAD.log`, 106 passed, 2 failed); restore the fix as
      reviewed, whose helper reserves `$max - 1` — the note case fails
      (`evidence/red-on-half-fixed.log`, 107 passed, 1 failed); restore the corrected fix and rerun — green
      (`evidence/green.log`, 108 passed, 0 failed, taken after both mutations).
- [x] 3.2 Name the scope: the delta touches no compiled file, so no Gradle task and no unit-test module runs; the
      structural check is `bash tools/gen-feature-list-selftest.sh` (108 cases) plus the real run above, as
      `guidelines/Build.md` §2's scope block asks, with `bash tools/check-doc-routes.sh`,
      `bash tools/check-portability.sh` and `openspec validate --all` recorded beside them. Verification: this
      change's evidence carries the suite logs, both real runs and those three checks.
- [x] 3.3 Record the adjacent defect this change neither touches nor claims — `tools/feature-list/pair.awk` treats
      the first line of its second file as a removal when the removal list is empty, so an additions-only release
      prints a capability as `— no longer available.` Verification:
      `evidence/pair-awk-empty-removed.log` (an empty removal list and one added name produce `x#Some  -`), noted in
      the proposal's Impact for a separate item.
- [x] 3.4 Archive the change and copy the cited logs into `<change>/evidence/`. Verification: the directory exists
      under `openspec/changes/archive/` and the delta is merged into
      `openspec/specs/feature-list-generation/spec.md`.
