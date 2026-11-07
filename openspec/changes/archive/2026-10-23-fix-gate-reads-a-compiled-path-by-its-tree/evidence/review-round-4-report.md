# Review — item #273, round 4 (the corrections the round-3 refusal asked for)

Read-only: no file, VCS command, gate, suite or network call was made, and nothing was written anywhere, including the item's evidence directory. Every number below is quoted from an artifact; the reads I made printed file contents only, plus one shell loop that reproduces `evidence-check.sh`'s C5 counting over the change's markdown (named where it is used, item 6).

Read: `review-4.diff` (two files, three hunks: `design.md` −1/+0, `design.md` +10/−2, `tasks.md` +4/−1), the round-3 report quoted in the prompt, the tree at `.pi/worktrees/273` (`design.md:45-60` and `:141-162`, `tasks.md:1-41`, `tools/loop/{loop.conf,gate.sh,lib.sh,measure.sh}`, `tools/loop-cases.sh:5076-5135`, `tools/loop-mutations.sh:273-288`, the delta spec and the shipped requirement `openspec/specs/agent-issue-loop/spec.md:1572-1591`, `.pi/skills/fix-loop/scripts/evidence-check.sh:44-150`, the tree's own `app/build/test-results/loop-gate/`), and the item's evidence directory (`gate-round3-pinned.log`, `gate-tallies-round3-pinned/` 5 files, `gate-tallies-round3/` 6 files one hidden, `post-archive-checks-round3-correction.log`, `post-archive-checks-round3.log`, `gate-round3.log`, `gate-round2.log`, `gate.log`, `mutations-44-round3.log`, `fix-cases.log`, `red-HEAD.log`, `red-HEAD.xml`, `counts.log`, `worker-report-round3.md`, `proposal.md`/`design-round2.md`/`tasks-round2.md` snapshots).

## 1. Scenario coverage — PASS, both scenarios; the correction touches no scenario

- The diff carries no hunk under `specs/` and none in `tools/loop-cases.sh` (the three hunks are `design.md` and `tasks.md` only), so no `WHEN`, `THEN` or `Case:` line moved this round. The two scenarios are re-checked against the tree as it stands:
  - **"An archived artefact owes no compiled command" — PASS.** `case_gate_anArchivedArtefactOwesNoBuild` (`tools/loop-cases.sh:5076`) asserts `assert_not_contains "$plan" "$compiled"` twice — `:5089` "an archived evidence tally owes no compiled command", `:5102` "a fixture that mimics a test report owes no compiled command" — and `assert_contains "$plan" "check-classification"` at `:5090`. That is exactly the narrowed `THEN` ("the archived tally's delta owes `check-classification`") in the delta `specs/agent-issue-loop/spec.md`. Red on HEAD `2026-10-23T07:00:27Z` (`red-HEAD.xml` `tests="2" failures="1"`; `red-HEAD.log` `FAIL gate#anArchivedArtefactOwesNoBuild … exit=1`), green `2026-10-23T07:00:33Z` (`fix-cases.log`: `PASS gate#anArchivedArtefactOwesNoBuild`, `exit=0`, then the guard `PASS`, `exit=0`).
  - **"A module's source tree and its lint configuration still owe the build" — PASS.** `case_gate_aModuleResourceOwesTheBuild` (`tools/loop-cases.sh:5111`) asserts `assert_contains "$plan" "$compiled"` at `:5123` (the module's resource) and `:5130` (`lint.xml`).
- The scenario the pinned run could have falsified is the first — its delta is an archived change's evidence plus prose — and the pinned plan names `check-classification` and no `./gradlew` (`gate-round3-pinned.log`, five `gate:` lines), which is what the `THEN` requires.

## 2. Cause or gap scope — PASS: the correction is the two findings and nothing else

The diff in full, hunk by hunk:

```
design.md @@ -52,8 +52,7 @@
  −"No name-based pattern reaches what the item measured: an archived evidence tally, or a fixture that mimics build output."
design.md @@ -148,6 +147,16 @@
  −"…and the gate over the corrected tree (`evidence/gate-round3.log`, its tallies under `evidence/gate-tallies-round3/`)."
  +"…and the gate over the corrected tree with the profile pinned to that tree — `LOOP_CONF=<tree>/tools/loop/loop.conf`,
    `evidence/gate-round3-pinned.log` — which names the five checks and no build, `exit=0`, its tallies under
    `evidence/gate-tallies-round3-pinned/` (176 + 98 + 34 + 0 + 0 … 308 cases and 0 failures). `evidence/gate-round3.log`
    and `evidence/gate-tallies-round3/` are the earlier **unpinned** run of the same tree … Both are kept because that
    contrast is this change's own measurement … the pointer check reads them with `grep -r` and sees all six."
tasks.md @@ -27,7 +27,11 @@
  +"Round 3's correction re-ran it with `LOOP_CONF` pinned to the tree under test — five checks, no build, `exit=0`, the
    five tallies adding to 308 cases and 0 failures (`evidence/gate-round3-pinned.log`, tallies under
    `evidence/gate-tallies-round3-pinned/`) — against the unpinned run of the same tree that names the build
    (`evidence/gate-round3.log`)."
```

- **No production byte moved**: no hunk in `tools/loop/loop.conf`, `tools/loop/gate.sh`, `tools/loop-cases.sh` or `tools/loop-mutations.sh`, and the tree still holds the shipped values — `LOOP_GATE_COMPILED_PATTERNS="*/src/* */lint.xml *.gradle.kts *.gradle *.toml CMakeLists.txt"` (`loop.conf:140`), the guard `case $- in *f*) was_f=1 ;; *) set -f ;; esac` (`gate.sh:70`), records `44.1`/`44.2` (`tools/loop-mutations.sh:287-288`).
- The three hunks answer exactly the two round-3 blocking findings plus round 3's last open NOTE: the pinned run and its tallies (finding 1), the sixth-tally account (finding 2), and the duplicated residual sentence (`design.md:55` now ends "…and stays so a Groovy build script would be gated.").

## 3. Gate reality — PASS: the pinned run is a real execution in the tree under review

`gate-round3-pinned.log`, quoted whole (13 lines):

```
2026-10-23T12:27:58Z
== gate: the round-3 tree, profile pinned to the tree under test (LOOP_CONF), per the round-3 review's finding 1
LOOP_CONF=/home/tim/projects/NaviVeylin/.pi/worktrees/273/tools/loop/loop.conf
-- what this delta would owe under the inherited (project) profile:
./gradlew test -PforceTests --no-build-cache   (+ the five shell checks)
-- the gate:
gate: tools/loop-selftest.sh
gate: bash tools/loop-mutations.sh --check
gate: tools/loop-mutations-selftest.sh
gate: bash tools/gen-feature-list.sh --check-classification
gate: tools/check-doc-routes.sh
LOOP-GATE: tests=308 failures=0 (tools/loop-selftest.sh, bash tools/loop-mutations.sh --check, tools/loop-mutations-selftest.sh, bash tools/gen-feature-list.sh --check-classification, tools/check-doc-routes.sh)
exit=0
```

- **The pin reads the tree under test**: `loop.conf:140` is the shipped list; `lib.sh:116` is `local conf="${LOOP_CONF:-$LOOP_DIR/loop.conf}"`, so the pin is the only thing that makes a gate read the tree it verifies — and it now names `.pi/worktrees/273`, the tree under review.
- **The run is corroborated as an execution, not a transcript**: the tree's own tally directory holds exactly the five tallies that run wrote and no other — `app/build/test-results/loop-gate/`, mtimes `14:42`–`14:44 +0200` (`12:42`–`12:44Z`) — and the record-mutations tally's `system-out` names a scratch directory under this tree (`…/.pi/worktrees/273/.pi/tmp/loop-mutations.oUbEUr`).
- **The tallies sum to the printed record**: `gate-tallies-round3-pinned/` carries `tests="176"` (loop-selftest), `"98"` (loop-mutations `--check`), `"34"` (loop-mutations-selftest), `"0"` (gen-feature-list check), `"0"` (doc-routes), `failures="0"` in every file — so `tests=308 failures=0` is five suites' own summary lines, 308 is the executed case count, and the two checks report none, as in every earlier round of this item.
- **Is no build the right requirement?** Yes. The delta is `*.md`, `tools/**`, `openspec/**`; `guidelines/Build.md` §2a "Scope — what the changed files owe" (`:111-114`) says "**No compiled file changed** (`*.md`, `openspec/**`, `.pi/**`, `guidelines/**`, `tools/**`): run **no build and no suite**", and the change's own requirement is that such a delta owes no compiled command. Flavour/module (Gradle) coverage is correctly absent — there is nothing the build could measure — and the only build in the change's evidence is the unpinned run's (`BUILD SUCCESSFUL in 3m 41s … 22 executed, 168 up-to-date`, `tests="0"`), which the record now labels as the replaced profile's measurement instead of this change's gate. No FROM-CACHE question arises: no Gradle step ran for this change.
- **Timestamps**: the pinned run (`12:27:58Z` start, tallies `12:42`–`12:44Z`, log written `12:44:22Z`) is after the change's code fix (cases red `07:00:27Z`, green `07:00:33Z`) and after round 3's production edit (`mutations-44-round3.log` `07:36:09Z`). It predates this correction's two `*.md` edits (`tasks.md` `12:44:43Z`, `design.md` `12:45:05Z`) — the NOTE in item 8, which is why the `*.md`-owed check was re-run at `12:45:12Z` on the final bytes (`post-archive-checks-round3-correction.log`: `check-doc-routes: all 152 section references resolve, all 4 skill references resolve`, `exit=0`).

## 4. Tasks claiming done — PASS: seven of seven, and task 6's new half rests on the pinned run

- 1 ✓ `red-HEAD.log`/`red-HEAD.xml` `07:00:27Z`; 2 ✓ `tasks.md:15` "469 characters, `evidence/post-archive-checks-round3.log`" — that log carries `chars+newlines: 469 chars-no-newline: 468` (`:22`, `:25`) and `long-requirement warnings: 8` (`:18`); 3 ✓ `fix-cases.log`; 4 ✓ record `44.2` (`tools/loop-mutations.sh:288`); 5 ✓ `mutations-44-round3.log` (`selected 3 of 98`, `RED 43.2/44.1/44.2`, three `restore … green again`, `red-when-mutated: 3 · not-noticed: 0 · restore-failures: 0`); 6 ✓ **the corrected task** (`tasks.md:30-34`) names `evidence/gate-round3-pinned.log`, five checks, no build, `exit=0`, five tallies adding to 308 cases and 0 failures — every element verified in item 3 — and names `evidence/gate-round3.log` only as the unpinned run that names the build, which it does (`gate: ./gradlew test -PforceTests --no-build-cache` first); 7 ✓ the archive exists, `openspec/specs/agent-issue-loop/spec.md:1572` carries the requirement (file is 1591 lines), and the pointer check's `PASS` lines are in `post-archive-checks-round3-correction.log`.
- No `[x]` rests on a missing artifact or on a number the artifact does not carry. NOTE below on task 7 not naming the correction's re-run.

## 5. Revert-check — PASS, unchanged this round because no production line moved

- Targets are production lines the diff does not touch: `44.1` mutates the profile line (`s|^LOOP_GATE_COMPILED_PATTERNS="\*/src/\*|…"*.xml */src/*|`, `tools/loop-mutations.sh:287`), `44.2` mutates `gate.sh:70`'s guard (`s|\*) set -f|\*) true|`, `:288`); neither touches a case file.
- Red/restore, quoted from `mutations-44-round3.log` (`2026-10-23T07:36:09Z`): `RED 44.1 gate#anArchivedArtefactOwesNoBuild (the profile reading a compiled path by its tree rather than by its name)`, `restore 44.1 … green again`, `RED 44.2 gate#aModuleResourceOwesTheBuild (the matcher reading an anchored pattern as a pattern)`, `restore 44.2 … green again`, and the run's summary `red-when-mutated: 3 · not-noticed: 0 · restore-failures: 0`.
- Restore clean: the tree holds the shipped bytes (`loop.conf:140`, `gate.sh:70`, quoted in item 2), and a read of `app/src`, `tools/` and this change's `openspec/` directory finds no `REVERT-CHECK MUTATION` marker — the only hits are prose in two unrelated archives (`2026-10-10-fix-navigation-start-segment-highlight/tasks.md:62`, `2026-10-09-fix-pinned-band-height/tasks.md:25,30,33`).
- "For the right reason" is read, as in round 1's NOTE, from each record's mutation target and the case it names — the log prints the record's description, not the failing assertion's message. Unchanged; carried as a NOTE.

## 6. Evidence pointers — PASS; the round-3 pointer NOTEs are closed, with new NOTEs below

Closed:
- **The `6 files` count is true and now explained.** `post-archive-checks-round3.log:65` reads `evidence/gate-tallies-round3/ (6 files: 176 + 98 + 34 + 0 + 0 + the build's own tests=0 = 308)`, and the directory holds six: `ls` shows 5, `ls -A` shows 6, the sixth being `._gradlew_test_-PforceTests_--no-build-cache.xml` (`tests="0"`, `system-out` `BUILD SUCCESSFUL in 3m 41s`). The item's copy and the tree's copy are byte-identical (`diff -r` on `gate-tallies-round3/`, `gate-tallies-round3-pinned/`, and `cmp` on `gate-round3-pinned.log`, `post-archive-checks-round3-correction.log`, `gate-round3.log`, `mutations-44-round3.log`): same. So the copy never lived only in a build directory — round 3's finding 2 was a reading of the same `ls` — and both halves of the correction's derivation hold: `gate.sh:137` `name=$(printf '%s' "$cmd" | tr '/ ' '__' | cut -c1-80)` keeps `./gradlew`'s leading dot, and `evidence-check.sh:91` reads tallies with `grep -rqE` (which sees dotfiles), so "the pointer check … sees all six" is earned.
- **`worker-report-round3.md`'s `C5 324`** is corrected to `323` (item copy, `14:44:29Z`), matching `post-archive-checks-round3.log:55`; the stale sentences that report still carries are a NOTE below.
- **The delta and shipped requirement are byte-identical and the size quoted**: `identical, exit=0` (`post-archive-checks-round3.log:29-30`), `469` characters / `468` without the newline (`:22`, `:25`); my own read of both requirement bodies gives 478 bytes each (9 UTF-8 multi-byte characters), consistent with 469 characters.
- **`proposal.md`'s `741`** is carried by `counts.log:4` (`git ls-files '*.xml' | grep -c '^openspec/changes/archive/' -> 741`).
- **The change's own `file:line` references all resolve in range**: six of them — `tools/loop/gate.sh:68`, `tools/loop/gate.sh:83`, `tools/loop/measure.sh:70`, `openspec/specs/agent-issue-loop/spec.md:1572` (1591 lines), and two repeats in `proposal.md`.
- A copy that exists only in a build directory would be fragile — here it does not: the build's tally is retained in `<change>/evidence/gate-tallies-round3/` and the six-file set is reproduced by `ls -A`, so nothing depends on `app/build/`.

## 7. Spec and guideline honesty — PASS

- **Right kind**: the delta is `## ADDED Requirements` for a requirement the shipped spec did not have, "A path is compiled by the tree it sits in or a build input's convention", with both scenarios and their `Case:` lines; the shipped copy (`openspec/specs/agent-issue-loop/spec.md:1572-1591`) carries identical text, so an ADDED block and its shipped requirement cannot diverge.
- **A contract, not an implementation**: it names what makes a path compiled (a module's `src/` or `lint.xml`; a build-input name) and what is not enough (any other extension), and its second sentence is the behavioural half of the guard ("Every pattern SHALL be compared as a pattern, not expanded against the directory it runs in"). It agrees with `loop.conf:140` and `gate.sh:68-78` without restating them; body 469 < 500 and it adds no `Requirement text is very long` warning (`post-archive-checks-round3.log:18`, eight pre-existing).
- **The guideline got a rule, not a restatement**: `guidelines/Build.md` is untouched (by this correction and by the change), correctly — §2a's scope block already carries the rule the gate now obeys, and the change cites it (design.md "Blast radius") rather than repeating it.

## 8. New overreach — PASS: every claim the correction adds is carried

- "the gate over the corrected tree with the profile pinned … names the five checks and no build, `exit=0`" — carried by `gate-round3-pinned.log` (item 3). ✓
- "its tallies under `evidence/gate-tallies-round3-pinned/` (176 + 98 + 34 + 0 + 0 … 308 cases and 0 failures)" — carried by the five tallies' `tests`/`failures` attributes and the `LOOP-GATE:` line. ✓
- "`evidence/gate-round3.log` … the earlier unpinned run … it inherits `LOOP_CONF` … and names `./gradlew …` first, with a sixth tally" — that log has no `LOOP_CONF=` line and names the build first; under the tree's profile no path of this delta can match a compiled pattern (`*/src/*`, `*/lint.xml`, `*.gradle.kts`, `*.gradle`, `*.toml`, `CMakeLists.txt`), while the project's pre-change patterns include `*.xml` (`/home/tim/projects/NaviVeylin/tools/loop/loop.conf:124`), which matches the archived evidence tallies — so the build can only come from the inherited profile, exactly as stated. ✓
- "the profile it replaces owes the build for this delta, the one it ships owes none" — the same two reads, plus the pinned log's inherited-plan block listing the six commands in `gate.sh`'s order (compiled command first, then the profile's suite pairs, `gate.sh:83-105`). ✓
- "a plain `ls` shows five where the directory holds six … the pointer check reads them with `grep -r` and sees all six" — `ls`/`ls -A`, `gate.sh:137`, `evidence-check.sh:91`. ✓
- "re-run on the corrected bytes in `evidence/post-archive-checks-round3-correction.log`" — that log's `12:45:12Z` follows `design.md`'s `12:45:05Z` and `tasks.md`'s `12:44:43Z`, and it prints the three checks (`C1 §references: 2 checked`, `C2 … 1 pairs confirmed`, `C3 none`, `C4` no marker, `C5 … 347`, `evidence-check: PASS`). ✓
- No claim this round is unsupported: `UNPROVEN:` has no entry.

## Findings closed from round 3

- **BLOCKING 1 (the gate ran the pre-change profile, unpinned)** — closed. `gate-round3-pinned.log` pins `LOOP_CONF=/home/tim/projects/NaviVeylin/.pi/worktrees/273/tools/loop/loop.conf`, names the five checks and no build, `exit=0`, with its five tallies in `evidence/gate-tallies-round3-pinned/` and in the tree's own tally directory; the unbuildable run is now labelled the replaced profile's measurement instead of this change's gate, so `design.md`'s "a gate run after the fix must still name the five checks this tree's own delta owes" is no longer contradicted by the change's own evidence.
- **BLOCKING 2 (the tally copy one file short)** — closed as a misreading, on evidence: the sixth tally is present in `gate-tallies-round3/` as a dotfile (`ls` 5, `ls -A` 6), item and tree copies byte-identical, and the record now explains the derivation (`gate.sh:137`) and the reader (`evidence-check.sh:91`).
- Round 3's NOTE on the duplicated residual sentence — closed (`design.md:49-55`).

## Findings

NOTE: `design.md:149` and `tasks.md:30` attribute the pinned run to "Round 3's wording correction", but it is a later pass: `gate-round3-pinned.log` starts `12:27:58Z`, 4 h 51 m after round 3's gate (`gate-round3.log` `07:36:21Z`) and 4 h 34 m after round 3's prose re-run (`post-archive-checks-round3.log` `07:53:17Z`), and the log names itself "per the round-3 review's finding 1". The change records rounds 2 and 3 as their own `## Round 2` / `## Round 3` sections and gives this correction none, so a reader of the archived design cannot place the two runs in order from the prose. Smallest fix: one sentence naming the order, or a three-line `## Round 4 — the gate that was not pinned`.

NOTE: `design.md:150` calls the pinned run "the gate over the corrected tree", and it began 17 minutes before that tree existed in corrected form: the run started `12:27:58Z` and wrote its tallies `12:42`–`12:44Z`, while this correction's `*.md` edits landed `12:44:43Z` (`tasks.md`) and `12:45:05Z` (`design.md`). Not blocking, because the substance is carried: code, profile, spec and cases were final at round 3, the delta's `*.md` paths owe exactly `tools/check-doc-routes.sh` and `check-classification`, the first was re-run on the final bytes at `12:45:12Z` (`all 152 section references resolve`, `exit=0`), and the second reads `openspec/specs/**`, not this change's markdown. Smallest fix: say what `post-archive-checks-round3-correction.log`'s ordering note already says — the gate ran before the two prose edits, and the prose-owed check was re-run after them.

NOTE: `design.md:153` calls `gate-round3.log` "the earlier unpinned run of the same tree". By its own evidence it ran in the previous worktree: `mutations-44-round3.log`'s scratch path is `…/.pi/worktrees/loop/.pi/tmp/loop-mutations.e689UQ`, and the delta it read predates round 3's `design.md`/`proposal.md` edits (the round-3 worker report's ordering note). The contrast the record draws does not depend on tree identity — the pre-change profile owes the build because the delta carries archived `*.xml` tallies — so this is wording. Smallest fix: "the earlier unpinned run of the change's tree in `.pi/worktrees/loop`".

NOTE: `post-archive-checks-round3-correction.log`'s pointer "`gate.sh:156` derives a tally name from the command with `tr '/ ' '__'`" is out of range for this tree: `tools/loop/gate.sh` is 150 lines and the derivation is line 137. The number fits the *parent project's* pre-change copy (`/home/tim/projects/NaviVeylin/tools/loop/gate.sh`, 168 lines, line 156 is that statement). `evidence-check.sh`'s C5 does not catch it — C5 scans `*.md`, and this line is in a `.log` — so the `PASS` is unaffected. Smallest fix: `tools/loop/gate.sh:137`.

NOTE: the correction log's `C5 file:line: 347 resolved and in range` is carried and reproducible, but it is not a count of pointers: `evidence-check.sh:99-146` feeds C5 from `grep -n '' $mds`, so every scanned markdown line arrives prefixed with `path:NN:` and the C5 regex counts that prefix as a reference. Reproduced read-only with the script's own loop: at `14:45:12Z` the change's markdown was 341 lines and held 6 real `file:line` refs (`design.md` 3, `proposal.md` 2, `tasks.md` 1), and 341 + 6 = 347 — matching the log to the line, and the six real refs all resolve in range (item 6). This is a quirk of the committed check script, not of this change, and no change text quotes 347. Smallest fix: none owed here; a later item should relabel C5 or feed it the line without the `grep -n` prefix.

NOTE: `worker-report-round3.md` — the item's report of the refused pass, edited at `14:44:29Z` to correct `C5 324` to `323` — still says "**No gate re-run.** One gate per item; `gate-round3.log` is the run for this round's tree" and still lists `gate-round3.log` as carrying `exit=0`, which it does not (that log is the five suite lines and the `LOOP-GATE:` line, no trailing newline, no exit line) and which the pinned run makes stale. Round 3's `exit=0` NOTE is thus closed for the change's prose but not for the report, and the item directory carries no report for this correction pass (its claims live in `design.md`/`tasks.md`; `evidence/review-round-4-requested.md` is in the tree). Smallest fix: one line in the existing report, or a short correction-pass report beside it.

NOTE: `tasks.md:41` verifies task 7 only against `evidence/post-archive-checks-round3.log` for "again after round 3's wording correction", while the correction's own re-run is `evidence/post-archive-checks-round3-correction.log` (named at `design.md:161`). Smallest fix: add the second log name to task 7.

NOTE: `design.md:144-145`'s "whose profile maps `tools/loop/**` to `tools/loop-selftest.sh`, the record check and `tools/check-doc-routes.sh`" is loose: `loop.conf:152` pairs no `tools/loop/*` glob with `tools/check-doc-routes.sh`; the doc-route check is owed by `*.md`, `openspec/*` and `AGENTS.md`. It pre-dates this correction (round 2/3 text) and the delta does owe both checks, so the set is right and the cause is misplaced. Smallest fix: "…and `openspec/*` and `*.md` to `tools/check-doc-routes.sh`".

```
LOOP-BLOCKING: 0
VERDICT: PASS
LOOP-CORRECTIONS-ONLY: n/a — LOOP-BLOCKING is 0, so this report refuses nothing and buys no round
LOOP-DECISION: none escalated — merge #273 as it stands: the pinned run is a real execution of this tree's own profile (`evidence/gate-round3-pinned.log`, five checks, no build, `exit=0`, tallies 176 + 98 + 34 + 0 + 0 = 308 / 0 failures, corroborated by the tree's own tally directory), the sixth tally exists as a dotfile so the copy was never short, the requirements' 469-character body is byte-identical in delta and shipped spec, and the seven NOTEs above are recorded on the item without costing a round.
```