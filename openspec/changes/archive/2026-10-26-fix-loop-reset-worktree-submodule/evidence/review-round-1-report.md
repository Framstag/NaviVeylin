## Review

I read the four inputs the contract names: item #310 (`work-package.md`), the tree under review `/home/tim/projects/NaviVeylin/.pi/worktrees/loop` at `f9110e9f` (branch `fix/issue-310-reset-worktree-cannot-discard-a-dirty-su`, clean working tree), the diff `/home/tim/projects/NaviVeylin/.pi/issue-loop/items/310/review-1.diff` (11 files, evidence trees excluded by the prompt's glob), and the evidence directory `openspec/changes/archive/2026-10-26-fix-loop-reset-worktree-submodule/evidence` (41 artefacts). I ran no gate, no suite, no version control, no tracker call, no network. Numbers below are read from the tree and the evidence, never reproduced by execution.

Context I had to establish first, because two of the eight checks read the record: `.pi/issue-loop/items/310/worker-report.md` is a *timeout* record — `LOOP-TIMEOUT: the child exceeded 2700 s`, then `LOOP-ENDED`/`LOOP-LEFTOVERS` lines only. The round was ended after the work had landed and been gated; no `LOOP-VERDICT`/`LOOP-GATE` report exists. `leftover-from-killed-run.patch` is the same six-file change that is in the diff (byte-identical for `PORTING.md`, `openspec/specs/agent-issue-loop/spec.md`, `tools/loop-cases.sh`, `tools/loop-mutations.sh`, `tools/loop/loop.conf`, and the same `run.sh` hunk), so the killed round's leftovers are not extra work hiding behind the diff.

### 1. Scenario coverage

Delta: `openspec/changes/archive/2026-10-26-fix-loop-reset-worktree-submodule/specs/agent-issue-loop/spec.md:9-18` (two ADDED scenarios; the same block is at `openspec/specs/agent-issue-loop/spec.md:2341-2357`).

- **Scenario "A submodule left on another commit is checked out at the recorded one" — PASS.** Case `run#resetWorktreeSyncsTheSubmoduleTheResetCannotDiscard` (`tools/loop-cases.sh:5348`, declared `:188`, dispatched `:472`). The THEN's three clauses are each an assertion: `assert_eq "" "$(git -C "$wt" status --porcelain)" "the repair leaves a tree the next start accepts"` (`:5361`), `assert_eq "$SUB_SECOND" "$(git -C "$wt/app/src/main/cpp/libosmscout" rev-parse HEAD)"` (`:5362-5363`), `assert_eq "0" "$status" "so the run starts instead of refusing the same tree again"` (`:5364`) plus `assert_contains "$out" "nothing eligible"` (`:5365`). Red on the base: `evidence/red-on-head.xml` — `the repair leaves a tree the next start accepts: expected '', got ' M app/src/main/cpp/libosmscout' (2026-10-26T07:26:29Z)`, tally `tests="1" failures="1"`. Green: `evidence/green-after-fix.log:23-24` `PASS run#resetWorktreeSyncsTheSubmoduleTheResetCannotDiscard` / `exit=0` (its worktree branch stamp is `loop-20261026-072641`, i.e. 07:26:41Z) and `evidence/focused-reset-cases.log:60-61` `PASS …` / `loop-selftest: all 3 cases passed … — selection: --case run#resetWorktree*` (branch `loop-20261026-072800`, 07:28:00Z).
- **Scenario "The repair names dirt it cannot discard" — PASS.** Case `run#resetWorktreeNamesTheDirtItCannotDiscard` (`tools/loop-cases.sh:5371`, declared `:189`, dispatched `:473`). THEN clauses as assertions: `assert_eq "5" "$status"` (`:5386`), `assert_contains "$out" "cannot discard"` (`:5387`), `assert_contains "$out" "app/src/main/cpp/libosmscout"` (`:5388`), `assert_not_contains "$out" "is clean again"` (`:5389`), `assert_not_contains "$out" "pre-screen:"` (`:5390`). Red on the base `run.sh`: `evidence/red-on-head-names.xml` — `the repair refuses a tree it cannot make clean: expected '5', got '0' (2026-10-26T07:28:28Z, run.sh from the base 5e8d739b)`, tally `tests="1" failures="1"`. Green: `evidence/focused-reset-cases.log:58` `PASS run#resetWorktreeNamesTheDirtItCannotDiscard` with the same `all 3 cases passed` line at `:61`.
- No scenario rests on a different case's claim; `evidence/measure.log:193` `LOOP-MEASURE-CHANGE fix-loop-reset-worktree-submodule scenarios=2 cases=2 unnamed=0` agrees.

### 2. Cause or gap scope

The item's root cause (work package, *Observed*): `--reset-worktree` runs only `git reset --hard` and `git clean -fd`, so a submodule whose worktree HEAD differs from the recorded gitlink stays dirty. The diff implements exactly that, in that branch, and nothing else in the engine:

```
+    reset_submodule_path=$(loop_conf_value LOOP_SUBMODULE_PATH)
+    if [ -n "$reset_submodule_path" ] && [ -f "$worktree/.gitmodules" ]; then
+      git -C "$worktree" submodule update --init --checkout --force "$reset_submodule_path" >&2 ||
+        loop_note "reset-worktree: the submodule could not be checked out — the tree may still be dirty"
+    fi
+    reset_after=$(git -C "$worktree" status --porcelain)
+    if [ -n "$reset_after" ]; then
+      printf 'reset-worktree: the reset cannot discard what is left in %s:\n' "$worktree"
+      printf '%s\n' "$reset_after" | sed 's/^/  /'
+      loop_refuse "the kept worktree is still not clean after --reset-worktree — nothing was started; the paths above are removed by hand"
+    fi
```
(`tools/loop/run.sh:513-526`.) The rest of the diff is the three files the work package names plus the two comment realignments the item asks for (`tools/loop/loop.conf:271-273`, `PORTING.md:56`): two cases and their fixture (`tools/loop-cases.sh:5313-5391`), two mutation records (`tools/loop-mutations.sh:134-135`), one ADDED requirement, and the archive. No compiled file, no unrelated refactor, no second behaviour beyond the repair's own truthfulness (the "cannot discard" refusal), which is the same false claim the item reports and is argued in `design.md:36-58`. The item's second candidate (*stop naming `--reset-worktree` for dirt it cannot discard*) is not taken and the rejection is recorded (`design.md:38-42`, `proposal.md:105-111`). **PASS.**

### 3. Gate reality

- `evidence/gate-round2.log:1433` carries `LOOP-GATE: tests=601 failures=0 errors=0 (./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache 249s, tools/loop-selftest.sh --from-git 6e8b2889… 405s, bash tools/loop-mutations.sh --check 2s, tools/loop-mutations-selftest.sh 229s, … )`, 23 suites. `evidence/gate-summary.xml` restates it with `tests="601" failures="0" errors="0"`.
- Real execution for *this change*, not a tally borrowed from elsewhere: the run is dated 08:07Z (log mtime 09:07 local, +0100; the run's own commit is `f9110e9f` at 08:39:49Z), i.e. after the mutation records were requoted (`evidence/focused-declared-record-applies.log`, `evidence/focused-mutations-selftest.log`, 07:50Z). Round 1's red run is kept, not overwritten: `evidence/gate-round1-red.log:1479` `LOOP-GATE: tests=540 failures=6 errors=0 … — failing: tools/loop-selftest.sh --from-git … tools/loop-mutations-selftest.sh`, matching `tasks.md:47-51` and the quoted `'all 173 cases passed' not found` (`gate-round1-red.log:555`).
- Not cached: `grep -c FROM-CACHE evidence/gate-round2.log` is 0; the Gradle step ran with `--no-build-cache`.
- The tally is internally consistent: summing `tests=` across the 23 `evidence/gate-*.xml` (excluding `gate-summary.xml`) gives exactly 601, failures 0, errors 0 in every one (236 loop-selftest, 175 loop-mutations `--check`, 121 issue-tooling, 34 loop-mutations-selftest, 29 portability-selftest, 6 openspec-hygiene-selftest, 0 for the rest).
- Flavour/module coverage is the right requirement: the delta touches no compiled file, so no Gradle/flavour suite is owed (`AGENTS.md`, "Verification follows the changed files"); the gate ran the whole profile anyway. The Gradle step's tally is `tests="0"` (`evidence/gate-gradlew_test_-PforceTests_-PjvmTestsOnly_--no-build-cache.xml`: `BUILD SUCCESSFUL … 17 executed, 148 up-to-date`) — the same shape as the sibling archived change's tally, and it is why the 601 is entirely shell cases. Nothing in the change claims compiled coverage. **PASS.**

### 4. Tasks claiming done

Twelve `[x]` items (`tasks.md`). Every claimed artefact exists and says what the item says, except two pointers:

- 1.1 fixture reaches the dirt — `evidence/red-on-head.log:33` fails at the status assertion, not at `fail "the fixture did not leave the submodule on another commit"`. ✔
- 1.2 / 1.3 — red tallies `evidence/red-on-head.xml` (`tests="1" failures="1"`), `evidence/red-on-head-names.xml` (`tests="1" failures="1"`, `run.sh from the base 5e8d739b`); greens at `green-after-fix.log:23` and `focused-reset-cases.log:58,60,61`. ✔
- 2.1 — `focused-reset-cases.log:61` `all 3 cases passed`, including the untouched `run#resetWorktreeDiscardsWhatItNames` (`:59`). ✔
- 2.2 — `evidence/gate-tools_check-portability.sh.xml` green. ✔
- 3.1 / 3.2 — `evidence/mutation-13.8.log`, `evidence/mutation-13.9.log`, each `RED …` then `restore … green again`. ✔ (with the grade in check 5)
- 3.3 — `evidence/gate-bash_tools_loop-mutations.sh_--check.xml`: `tests="175" failures="0"`, `loop-mutations: all 175 cases passed — every selected record applies to the file it names`. ✔
- 4.2 — `evidence/evidence-check.log` `evidence-check: PASS` (C2 `4 pairs confirmed, 0 drift`; C4 no mutation marker; C5 `256 resolved and in range`). ✔
- 5.1 / 5.2 — the `LOOP-GATE` line and the kept red first run are in the evidence (check 3). ✔

`NOTE:` `tasks.md:37` (4.1) claims verification by `openspec validate fix-loop-reset-worktree-submodule --strict`, but no artefact in `evidence/` carries that command's output (the sibling archived change keeps `validate.log`; this one does not), and after the archive step the id is no longer an open change, so the command cannot be re-run as written. The delta itself is present and correct (check 7), so this is a pointer to correct, not a claim to re-establish.

`NOTE:` `tasks.md:44` (5.1) says the gate line "is quoted in the item's report". The item's report (`.pi/issue-loop/items/310/worker-report.md`) carries only `LOOP-TIMEOUT`/`LOOP-ENDED`/`LOOP-LEFTOVERS`; the round was ended at 2700 s before it was written. The line is carried by `evidence/gate-round2.log:1433` and `evidence/gate-summary.xml`, which is what the run reads, so point the sentence at those. Related, same cause: the prompt's phase F also asks for the cited XMLs to be copied into the item directory, and that copy is absent — a round-level consequence of the timeout, not a defect of the change, but the run should not read 5.1's pointer as satisfied at item level.

### 5. Revert-check

- Both mutations target the production invariant, in the production file: `tools/loop-mutations.sh:134` turns `if [ -n "$reset_submodule_path" ] && [ -f "$worktree/.gitmodules" ]; then` (`run.sh:514`) into `if false` — the checkout never runs; `:135` turns `if [ -n "$reset_after" ]; then` (`run.sh:521`) into `if false` — the repair skips its own verification.
- Each reddens the case its record names: `evidence/mutation-13.8.log` `RED 13.8 run#resetWorktreeSyncsTheSubmoduleTheResetCannotDiscard (the repair syncing the submodule its own verbs cannot reach)`; `evidence/mutation-13.9.log` `RED 13.9 run#resetWorktreeNamesTheDirtItCannotDiscard (…)`; both end `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0`.
- Restore is clean: the tree under review is clean (`git status --porcelain` empty, so `tools/loop/run.sh` at HEAD carries no marker and differs from HEAD by nothing), the block itself is the unmutated production text (`run.sh:513-526`), and `evidence/evidence-check.log:4` reports `C4 mutations: no REVERT-CHECK MUTATION marker under 1 configured path(s): app/src`.

`NOTE:` neither mutation log quotes the *failure text* or a timestamp — `tools/loop-mutations.sh:795` prints only `RED <label> <case> (<description>)` — so "fails for the right reason" is evidenced at the level of the named case (and by the harness's `not-noticed: 0`), not by a message the way this check asks. The message for the same code path with the same invariant absent is quoted in `evidence/red-on-head.xml` (`expected '', got ' M app/src/main/cpp/libosmscout'`, 07:26:29Z). Nothing here is false; it is thinner than the check's wording.

### 6. Evidence pointers

Reproducible from the record: `601/0/0` and 23 suites (`gate-round2.log:1433`, `gate-summary.xml`, and the 23 tallies), `971 s` / `23 suites` (`measure.log:192` `LOOP-MEASURE-GATE seconds=971 suites=23`), `175` declared records (`gate-bash_tools_loop-mutations.sh_--check.xml`), the two `tests="1" failures="1"` red tallies (`red-on-head.xml`, `red-on-head-names.xml`; C2 `4 pairs confirmed`), `scenarios=2 cases=2 unnamed=0` (`measure.log:193`), `256` `file:line` citations (`evidence-check.log:5`).

`NOTE:` the surface counts at `proposal.md:60` are wrong, and the log they cite cannot have produced them. Measured on the change's own base `6e8b2889` and on `5e8d739b`: `grep -c 'reset-worktree' tools/loop-cases.sh` is **10**, and at HEAD **12** — the record says "11 mentions at the base, 13 after". `grep -n 'reset-worktree' tools/loop-mutations.sh` prints **nothing** at either endpoint (the file's records spell the case name `resetWorktree`; a `reset-worktree` match would need a line the file never had), yet `evidence/surface-head.log` prints a match at line 133; and that log's `grep -c '^case_run_'` prints **87** where both endpoints give 86. Two of the five logged values agree only with a half-written tree (11 and 87 go together), and the fifth cannot be produced by the command written above it. Nothing in the contract, a case or an assertion depends on these numbers: the correction is subtractive — delete the parenthetical counts or restate them as 10 → 12 and drop the `loop-mutations.sh` half.

`NOTE:` `measure.log:196` `LOOP-MEASURE-SURFACE surface: … 1 requirement added with 2 scenarios, 2` is truncated at the last digit, so the proposal's `2 comments realigned` rests on a line the tool cut.

`NOTE:` `design.md:14-28` quotes a four-command probe transcript (`$ git -C "$wt" reset -q --hard && git -C "$wt" clean -qfd`, `$ git -C "$wt" status --porcelain`, ` M app/src/main/cpp/libosmscout`, `$ git -C "$wt" submodule update --init --checkout app/src/main/cpp/libosmscout`, empty), and `proposal.md:20-32` names `evidence/red-on-head.log` as its carrier. That log carries the case's failing assertion, not the probe. The substance is verified by the red case, so this is a pointer correction only.

`NOTE:` `design.md:50-52` `--checkout` "leaves a module's untracked files in place (measured: ` M … ` survives it)" has no log either; case 2's green run does exercise it (the module is at the recorded commit, the checkout runs, the status stays dirty and the repair refuses), but no artefact prints the probe.

`NOTE:` `proposal.md:82` "Body: 303 characters against the validator's 500" is correct — I measured the requirement body at 303 characters — but no command in the record produced that number.

`NOTE:` `tasks.md:39-40` (4.2) cites `bash tools/loop/evidence-check.sh openspec/changes/fix-loop-reset-worktree-submodule --root .`, the pre-archive path; after the archive step that directory is `openspec/changes/archive/2026-10-26-…`, and `evidence-check.log` predates the archive. The sibling archived change carries a post-archive run (`evidence-check-archived.log`); this one does not. The cited `file:line` and tally pointers all still resolve, so this is a path to correct.

### 7. Spec and guideline honesty

- The delta is the right kind: **ADDED**. No shipped requirement's text changes (the existing refusal requirement and all its scenarios stay verbatim, `openspec/specs/agent-issue-loop/spec.md:548-573`), so no `MODIFIED` block was owed. The archived delta (`…/specs/agent-issue-loop/spec.md:3-18`) is identical to the block now appended at `openspec/specs/agent-issue-loop/spec.md:2341-2357` — same requirement, same two scenarios, same two case names, nothing dropped.
- It states a contract, not an implementation: `SHALL leave a kept worktree that the next start accepts`, `SHALL be checked out at that gitlink`, `SHALL stop the run naming it`. Body 303 characters, under the validator's 500; scenarios 2, cases 2, each scenario naming exactly one case.
- No guideline rule was owed and none is now inaccurate: `guidelines/Backlog.md:29` makes the spec `agent-issue-loop` the owner of "its refusals", and `Backlog.md:516`'s sentence ("the tree is still dirty and nothing is discarded without `--reset-worktree`") is still true after the change. The two comments that described the key's reach were realigned (`loop.conf:271-273`, `PORTING.md:56`) rather than restated.

`NOTE:` neither the ADDED requirement nor the existing scenario states the boundary this diff introduces: on the `--reset-worktree` sub-path the run now ends with exit 5 and — unlike the unflagged refusal — writes no blocked record, no escalation and no report. The case pins the exit status and that nothing is started (`loop-cases.sh:5386,5390`), but a reader of `spec.md:568-572` ("records the refusal as a blocked item under it, escalates it with the branch as evidence and prints its report") can read that clause as applying here too. One sentence in the delta naming the exit status and the absence of a record would close it.

### 8. New overreach

`UNPROVEN: "the gate ran them once"` (`proposal.md:101`) — `tasks.md:47-51` records two runs of the same gate (round 1 `tests=540 failures=6`, then the 601 run), and both logs are in the evidence directory.

`UNPROVEN: "no compiled file, so no Gradle suite is owed by these paths and the gate ran only the shell suites the profile maps for tools/loop/**"` (`design.md:64`) — the gate ran the whole profile: 23 steps, including `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache 249s` (`gate-round2.log:1433`). What it ran is the profile, not only the loop's suites; the conclusion (nothing compiled is owed by *this* delta) still holds on `AGENTS.md`'s scope rule, but the clause describes a run that did not happen.

`UNPROVEN: "11 mentions at the base, 13 after"` / `"one record before, three after"` (`proposal.md:60`) — measured 10 → 12 and 0 → 0 for the command as written (check 6).

`UNPROVEN: "Measured 2026-10-26 in a throwaway repository whose configured submodule path is a real module of two commits:"` with the four-command transcript (`design.md:14-28`, repeated `proposal.md:20-32`) — no artefact carries that transcript.

`UNPROVEN: "(measured: ` M app/src/main/cpp/libosmscout` survives it)"` (`design.md:51`) — no artefact prints that probe; case 2's green run exercises the behaviour without printing it.

## Merge verdict

Every one of the eight checks is answered from the evidence, and the change does what the item's root cause names: the repair checks out the configured submodule at the recorded gitlink, and it stops with the paths named instead of announcing a clean tree it cannot deliver. Both scenarios are red on the base and green after the fix, each on its own named case; the gate is a real 601-case execution of the fixed tree with the red first run kept; the tree under review is clean, so both mutations restored.

`NOTE:` (all subtractive, none of which a case, a scenario or the gate depends on) — the surface counts at `proposal.md:60` and the two logged values behind them; the `loop-mutations.sh` grep line in `surface-head.log`; the truncated `LOOP-MEASURE-SURFACE` line; the design's probe transcript and the `--checkout` "measured" remark; the unproduced "303 characters"; `tasks.md:37`'s validate pointer and `tasks.md:39-40`'s pre-archive path; `tasks.md:44`'s "quoted in the item's report", which is not (the round timed out before the report was written); `proposal.md:101` "ran them once"; `design.md:64` "only the shell suites"; and the unstated exit-5/no-record boundary in the spec.

LOOP-BLOCKING: 0
VERDICT: PASS
