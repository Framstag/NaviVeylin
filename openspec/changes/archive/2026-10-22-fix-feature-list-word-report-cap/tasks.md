## 1. The gate collects an area's candidate words instead of printing each one

- [x] 1.1 `tools/feature-list/gate.awk` gains `note_word()` and `flush_words()`, and `flush_entry`'s
      `printf "unsourced word: …"` becomes `note_word(w, substr(pending, 1, 120))`; the `/^#/` arm calls
      `flush_words()` at a `## ` heading and records the heading as the current area; `END` closes the last area
      after `flush_entry()`. Verification: `awk -f tools/feature-list/gate.awk` over a two-area catalogue with one
      unsourced word in each prints one summary line per area and one `unsourced word:` line per area.
- [x] 1.2 The `NR == FNR` arm counts, per word, how many specs of the project contain it (`specfreq`), counting a
      word once per spec, and `BEGIN` defaults `maxwords` to 10 when the caller passes none.
      Verification: `grep -n 'specfreq' tools/feature-list/gate.awk` and 1.1's probe, which must show the word of a
      spec the entry does not cite ranked behind a word no spec contains.
- [x] 1.3 The gate's header comment states the bounded, ranked report, so the file still explains itself when read
      on its own. Verification: `sed -n '1,30p' tools/feature-list/gate.awk` names the cap, the ranking and the
      suppressed count.

## 2. The tool declares the cap and passes it to both call sites

- [x] 2.1 `tools/gen-feature-list.sh` declares `max_words=10` beside `maxpara` (`:80`) with the requirement it
      serves, and both `gate.awk` invocations (`:715`, `:856`) pass `-v maxwords="$max_words"`.
      Verification: `grep -n 'max_words\|maxwords' tools/gen-feature-list.sh` shows the constant and both call sites;
      a run reports the catalogue line as before.
- [x] 2.2 The comment above the catalogue gate (`:712-714`) says the report is bounded and ranked.
      Verification: `sed -n '710,718p' tools/gen-feature-list.sh`.

## 3. The self-test asserts the bound, the count and the ranking

- [x] 3.0 The four cases were written and measured red **before** the fix: `tests=119 failures=4` on the run's base
      commit, `evidence/red-on-HEAD.xml` and `evidence/red-on-HEAD.log`.
- [x] 3.1 `tools/gen-feature-list-selftest.sh` reads the cap from the tool's own `max_words` and asserts it is
      declared (case `gates: the word report's cap is declared by the tool`).
- [x] 3.2 It appends the cap plus three absent words to one area's entry and asserts exactly the cap's worth of
      `unsourced word:` lines (`gates: the word report shows no more than its cap in one area`) and the summary line
      (`gates: the word report counts the candidates it did not show`).
- [x] 3.3 It appends one word a fixture spec contains and one no spec contains and asserts the rarest is named first
      (`gates: the word report names the rarest word first`).
- [x] 3.4 Verification: `bash tools/gen-feature-list-selftest.sh` — `all 119 cases passed`, including the five
      existing gate cases unchanged (`gates: the unsourced word is named`,
      `gates: the report does not stand in for the figure gate`, `gates: a fully sourced bullet is not reported`,
      `gates: an unsourced word is reported and the run stays green`, `gates: a well-sourced section passes`).

## 4. Falsify the new bound once, and restore

- [x] 4.1 Remove the cap from `flush_words` (show every candidate), run the focused case
      `gates: the word report shows no more than its cap in one area` — it must fail — then restore the line and
      re-run the suite green. Evidence: `evidence/mutation-no-cap.log`.

## 5. The guideline says what the report now is

- [x] 5.1 `guidelines/FeatureList.md` §6: the gates table's `words` row, the paragraph on why the report is not a
      gate, and the case list. Verification: `sed -n '150,190p' guidelines/FeatureList.md` states the cap, the
      ranking and the count, and names the new cases.

## 6. Checks this delta owes — no compiled file, so no Gradle task

- [x] 6.1 `bash tools/check-doc-routes.sh` (a guideline changed) — log in `evidence/check-doc-routes.log`.
- [x] 6.2 `bash tools/check-portability.sh` (a `tools/` file changed) — log in `evidence/check-portability.log`.
- [x] 6.3 `openspec validate --all` and `openspec validate fix-feature-list-word-report-cap --strict` — log in
      `evidence/openspec-validate.log`.
- [x] 6.4 `./tools/loop/gate.sh`, run once for the item, quoted into the report and the evidence.

## 7. Evidence and archive

- [x] 7.1 Copy every cited log and XML into `openspec/changes/fix-feature-list-word-report-cap/evidence/`.
- [x] 7.2 `openspec archive fix-feature-list-word-report-cap`; confirm the archive directory exists and the delta
      merged into `openspec/specs/feature-list-generation/spec.md`.
