# Tasks — fix-highlight-longest-run

## 1. Red proof: the harness fails on HEAD

- [x] 1.1 Add `tools/measure-highlight-selftest.sh`: a device-free harness that builds its PNG fixtures
      itself with the `python3` standard library (`zlib` + `struct`, no ImageMagick, no device, no git — the
      `tools/declared-cases-selftest.sh` / `tools/gate-timings-selftest.sh` precedent, required by
      `AGENTS.md`'s "the tools a skill uses belong in the repo, with a self-test that runs without a
      device"), prints what every case measured and exits 0/1; verify `bash tools/measure-highlight-selftest.sh`
      runs on HEAD and prints its per-case measurements.
      — ran 2026-10-09T20:33:07Z: the harness ran against `tools/measure-highlight.py` at HEAD `095e6ac`
      (restored from git for the run, the harness being the change's new file) and printed all seven cases
      with their JSON and exit codes. The `mktemp` fixture-directory paths inside the retained runs (the `"image"` field of the tool's JSON) are
      harness's own per-run fixture directory, embedded in the tool's JSON `image` field — regenerated on
      every run, not a pointer to a cited number (the number is on the same line).
- [x] 1.2 Record the red run of the case that reproduces §169's row pattern (a 41 px casing run followed by a
      5 px one in the same row): `longest-run-not-last` must fail on HEAD, and `longest-run-per-row` with it
      (each qualifying row must contribute its own longest run); retain the run verbatim in the change.
      — ran 2026-10-09T20:33:07Z: harness exit **1**, `measure-highlight selftest: 5 passed, 2 failed`;
      `FAIL longest-run-not-last — measured output does not carry "bbox": [10, 50, 5, 5]` with the tool's
      measured `{"…", "highlight": null, "image_size": [200, 20], "band_bottom": 20}` and `exit=2`; and
      `FAIL longest-run-per-row` with `"bbox": [60, 100, 6, 6]`, `"highlight_px": 41` and `exit=0` instead of
      `[10, 100, 5, 6]` / `82` — i.e. HEAD drops the row whose longest run is not its rightmost one, exactly
      as §169 records. Retained: `evidence/red-on-HEAD.txt` (2026-10-09T20:33:07Z, HEAD `095e6ac`).

## 2. The fix

- [x] 2.1 `tools/measure-highlight.py` `find_casing`: qualify a row, count it and extend the box with that
      row's **longest** casing-coloured run instead of the run the scan ends on (track `best`/`best_first`/
      `best_last`, close the final run after the `x` loop, `if best >= MIN_RUN_PX:`); keep `MIN_RUN_PX`
      untouched (spec: `highlight-measurement` — A row qualifies by its longest colour run).
      — ran 2026-10-09T20:33:10Z / re-run 2026-10-09T20:34:21Z: `bash tools/measure-highlight-selftest.sh`
      → `measure-highlight selftest: 7 passed, 0 failed`, harness exit 0 (`evidence/green-after-fix.txt`,
      `evidence/revert-check-mutation.txt`); `git diff --stat tools/measure-highlight.py` → 1 file changed,
      26 insertions(+), 11 deletions(−).
- [x] 2.2 State the row rule where the code is: `find_casing`'s docstring, the `MIN_RUN_PX` comment, and the
      detector bullet in `.pi/skills/pixel-check/SKILL.md` (spec: `highlight-measurement` — A row qualifies by
      its longest colour run); verify with `grep -n 'longest' tools/measure-highlight.py
      .pi/skills/pixel-check/SKILL.md`.
      — ran 2026-10-09: `tools/measure-highlight.py:48-53` (the `MIN_RUN_PX` comment) and `:58-64` (the
      `find_casing` docstring), `.pi/skills/pixel-check/SKILL.md:101-104` all state the longest-run rule.
- [x] 2.4 Re-point the two stale `§143` pointers in `.pi/skills/pixel-check/SKILL.md` (`:45`, `:105` — the
      pre-fix numbering `TODO.md` §147 records) at
      `§147`, which owns the no-route observation they quote — `§147`'s own "Doc drift from the id change"
      bullet asks the change that repairs the detector to do it (spec: none — pointer repair); verify
      `grep -n '§143' .pi/skills/pixel-check/SKILL.md` → no match.
      — ran 2026-10-09: both lines now read `TODO.md` §147; `grep -n '§143' .pi/skills/pixel-check/SKILL.md`
      → no match (the surviving `§143` citation in `guidelines/Build.md:672` is the route-cost entry's own and
      is correct).
- [x] 2.3 `guidelines/Build.md` section 11 item 2: name `bash tools/measure-highlight-selftest.sh` as the detector's
      device-free cover (the older `.pi/skills/pixel-check/selftest.sh` stays named); verify
      `bash tools/check-doc-routes.sh` → `check-doc-routes: all 71 section references resolve`, exit 0.
      — ran 2026-10-09T20:35:49Z: `check-doc-routes: all 71 section references resolve`, exit **0**
      (`evidence/gate-shape.txt`).

## 3. Gate shape (no Gradle-visible source changed)

- [x] 3.1 Decide the gate shape and state the justification: this change touches `tools/`, the OpenSpec
      change artifacts, `.pi/skills/pixel-check/SKILL.md` and one `guidelines/Build.md` section 11 sentence — no
      `app/src`, no `core/src`, no `auto/src`, no `:osmscout-client-java`, no Gradle-visible test source and
      no build file — so shape (a) applies: the changed tool's harness green plus `tools/check-doc-routes.sh`
      for the guideline pointer; `./gradlew test -PforceTests --no-build-cache` is **not** run because it
      would execute exactly the same compiled suites as at HEAD (`git diff --name-only` lists only the files
      in this change), and the loop forbids running a gate to "refresh" evidence.
      — ran 2026-10-09T20:35:49Z: `git diff --name-only HEAD~1..HEAD` → `.pi/skills/pixel-check/SKILL.md`,
      `guidelines/Build.md`, `tools/measure-highlight-selftest.sh`, `tools/measure-highlight.py`, so no
      Gradle-visible input changed; harness exit 0 (7/7, `evidence/green-after-fix.txt`) and
      `check-doc-routes.sh` exit 0 (`evidence/gate-shape.txt`).
- [x] 3.2 The older detector harness stays green after the fix (it covers inside / behind card / no highlight
      with ImageMagick); verify `bash .pi/skills/pixel-check/selftest.sh`.
      — ran 2026-10-09T20:34:44Z (re-run 2026-10-09T20:35:49Z): `ok inside -> inside=true` / `ok behind-card
      -> inside=false` / `ok dot -> no highlight` / `selftest FAIL=0`, exit **0**
      (`evidence/skill-selftest-after-fix.txt`, `evidence/gate-shape.txt`).
- [x] 3.3 Re-measure the two 1080×2400 synthetic samples the repository knows about and retain the runs the
      file carries: the **post-fix** runs of the §169 row pattern (`bbox=[300, 340, 900, 900] px=41`,
      `verdict: inside`, exit 0 — its HEAD side, no highlight / exit 2, is the reviewer's round-1 re-check, not
      a retained run) and of §147's no-route frame (`bbox=[619, 628, 413, 734] px=18`, `verdict: inside`, exit
      0, measured on the fixed tool only — its rows hold a single 8 px and 10 px run, and §147 stays on hold
      for its own decision).
      — ran 2026-10-09T20:33:38Z: those quotes and their regenerable `convert` fixture commands are in
      `evidence/synthetic-samples-after-fix.txt`, whose header states that it carries post-fix runs only.

## 4. Revert-check (falsification of the invariant)

- [x] 4.1 Mutate the production invariant once — make `find_casing` keep only the run the row ends on
      (HEAD's rightmost-run rule) — and verify the named case fails for the right reason; restore, confirm no
      `REVERT-CHECK MUTATION` marker survives and that `git diff` of the tool equals the fix (spec:
      `highlight-measurement` — A row qualifies by its longest colour run).
      — ran 2026-10-09T20:34:12Z (mutation) and 2026-10-09T20:34:21Z (restored): with the mutation the harness
      exit is **1**, `measure-highlight selftest: 5 passed, 2 failed`, and the named case failed exactly as on
      HEAD — `FAIL longest-run-not-last … {"…", "highlight": null, …}` with `exit=2` (the row's longest run is
      41 px, the run it ends on is 5 px), with `longest-run-per-row` also red (`"bbox": [60, 100, 6, 6]`,
      `"highlight_px": 41`); restored (`grep -rn 'REVERT-CHECK MUTATION' tools/ .pi/skills/pixel-check/` → no
      match), re-ran `bash tools/measure-highlight-selftest.sh` → `7 passed, 0 failed`, exit 0, and
      `git diff --stat tools/measure-highlight.py` → 26 insertions(+), 11 deletions(−), the fix. Retained:
      `evidence/revert-check-mutation.txt` (both the mutated and the restored run).

## 5. Scenario coverage

- [x] 5.1 `longest-run-not-last` — a row with a long run followed by a shorter one still qualifies, and the
      box is the long run's extent (`"bbox": [10, 50, 5, 5]`, `"highlight_px": 41`, `"inside": true`, exit 0)
      (spec: `highlight-measurement` — The longest run is not the rightmost one).
      — ran 2026-10-09T20:33:10Z: PASS, `evidence/green-after-fix.txt`.
- [x] 5.2 `longest-run-is-last` — the mirror order keeps working (`"bbox": [100, 140, 5, 5]`,
      `"highlight_px": 41`, exit 0) (spec: `highlight-measurement` — The longest run is the rightmost one).
      — ran 2026-10-09T20:33:10Z: PASS, `evidence/green-after-fix.txt`.
- [x] 5.3 `longest-run-per-row` — two rows whose longest runs lie on different sides contribute both, box
      `[10, 100, 5, 6]`, count `82` (spec: `highlight-measurement` — Each qualifying row contributes its own
      longest run).
      — ran 2026-10-09T20:33:10Z: PASS, `evidence/green-after-fix.txt`.
- [x] 5.4 `single-run` — one run per row is measured as before (`"bbox": [10, 50, 5, 5]`, `41`, exit 0)
      (spec: `highlight-measurement` — One run per row is measured as before).
      — ran 2026-10-09T20:33:10Z: PASS, `evidence/green-after-fix.txt`.
- [x] 5.5 `short-only` — a single run below `MIN_RUN_PX` never qualifies (`"highlight": null`, exit 2) (spec:
      `highlight-measurement` — A single short run never qualifies a row).
      — ran 2026-10-09T20:33:10Z: PASS, `evidence/green-after-fix.txt`.
- [x] 5.6 `two-short-runs` — two short runs far apart in a row neither sum to the floor nor qualify by the
      row's extent (`"highlight": null`, exit 2) (spec: `highlight-measurement` — Short runs are neither
      summed nor measured by the row's extent).
      — ran 2026-10-09T20:33:10Z: PASS, `evidence/green-after-fix.txt`; green on HEAD too (it is a guard
      against the two eliminated fixes, not part of the red pair).
- [x] 5.7 `behind-card` — the band verdict and its exit code are unchanged (`"clipped": ["bottom"]`,
      `"inside": false`, exit 1); its contract keeps its existing homes (module docstring `:25-26`,
      `pixel-check/SKILL.md` `:96`, `Build.md` section 11) and is deliberately **not** restated in the new spec, so
      the change adds no fourth copy (spec: none — non-regression case).
      — ran 2026-10-09T20:33:10Z: PASS, `evidence/green-after-fix.txt`.
- [x] 5.8 Check the rest of the repository for the old rightmost-run expectation and update every consumer
      the fix moves: `grep -rn 'MIN_RUN_PX\|highlight' tools/ .pi/skills/pixel-check/ guidelines/
      openspec/specs/` → the only statements of the row rule are the two places updated in task 2.2; no
      Gradle test, spec or script asserts the rightmost-run behaviour.
      — ran 2026-10-09: `grep` hits outside the edited files are the verdict/exit-code pins (script
      docstring, `SKILL.md` `:96`, `Build.md` section 11) and descriptive uses of "highlight"; nothing else encodes
      the row rule.

## 6. Follow-ups (pending, not this change)

- [ ] 6.1 Device measurement (pending): re-run the `pixel-check` recipe on a phone frame that shows an
      analysed segment with a casing-coloured glyph or label to its right, and record the verdict before and
      after this change. §169 states the device-side impact is inference; the fix is host-decidable, so this
      task stays open.
- [ ] 6.2 `TODO.md` §147 (pending decision): its operating point (a stroke floor vs a precondition refusal)
      remains open and is not decided here; the fix was measured not to move its frame (`px=18`, exit 0,
      `evidence/synthetic-samples-after-fix.txt`).

## 7. Bookkeeping

- [x] 7.1 `TODO.md` §169 close-out, with the run's removal allowance: the whole `## 169. …` section (heading,
      metadata line, body) deleted rather than marked `fixed-by`, `§169` removed from the
      `- **verification** — bug:` Clusters row (now `§131 §147 §148`), and one sentence appended to §147's
      entry recording that this change landed the longest-run row rule and re-pointed `pixel-check/SKILL.md`
      `:45`/`:105` from §143 to §147 (§147's status unchanged, its false positive left open); then the
      structure checks: `##` count == `**id:**` count, every numbered heading followed by its metadata line, no
      id collision, no dangling `§169` outside §147's own body.
      — ran 2026-10-09: `grep -c '^## [0-9]'` = 90, `grep -c '^\*\*id:\*\* '` = 90, no duplicate id, every
      heading followed by its metadata line, Clusters row `§131 §147 §148`; the only surviving `§169` strings are
      §147's historical "Adjacent finding" bullet and the sentence appended beside it, which names this
      change's archived record.
- [x] 7.2 Commit the iteration as `fix: highlight-longest-run (§169) — archive with its evidence` (phase F),
      archive and `TODO.md` in the same commit; hash reported by the orchestrator's phase-F step.
      — ran 2026-10-09 (phase F): the archive directory, the six evidence files, `TODO.md` and this task's
      checkbox travel in that single commit on the loop branch; nothing pushed.
