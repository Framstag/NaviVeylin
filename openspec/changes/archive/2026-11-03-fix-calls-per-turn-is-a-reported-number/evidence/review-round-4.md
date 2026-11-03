# Review round 4 — fix-calls-per-turn-is-a-reported-number (issue #757)

Read-only closure check of round 3's four findings, on the committed change: `HEAD` = `064b7254` ("fix: a run
reports the shape it spent its budget in (#757)") on `5e2de33e`, working tree clean, `git show HEAD` the whole
delta (`9 files, +260 −28`). Nothing was run — no self-test, no `tools/loop/gate.sh`, no mutation run; the logs
under `evidence/` are the author's. One file written: this one.

## Review
- Correct: round 3's finding 1 is closed. `evidence/evidence-check.log` is fresh and reads `FAIL (10)` at `:16`,
  nine `FAIL C1` lines at `:1-9` and one `FAIL C2` line at `:10`; the only lines in the change that carry a
  section-sign citation of a guideline subsection are `evidence/review-round-1.md:136-137` and
  `evidence/review-round-2.md:102,103,120,122,131,132,133` — nine lines, all in the two retained reviewer
  reports — and `grep -rn '§' --include='*.md' .` returns nothing from `proposal.md`, `tasks.md`, `design.md`
  or `specs/agent-issue-loop/spec.md`. The prose now says ten findings in two classes, raised by the reports
  (`tasks.md:83-84`) and "none by an artifact of the delta" (`proposal.md:70-72`), which is what the log shows.
- Correct: round 3's finding 2 is closed at all four sites — `tools/loop/loop.conf:129`,
  `guidelines/Backlog.recipes.md:318`, `design.md:44` and `proposal.md:15` all read p75 1.22 over the same
  series; `grep -rn '1\.21'` over `tools/loop/`, `guidelines/Backlog.recipes.md`, `.pi/skills/loop-spend/`,
  `PORTING.md` and the change finds the old figure only in the reviewer reports' own history
  (`review-round-1.md:103`, `review-round-2.md:39,142-143`, `review-round-3.md:39,84-89`) and in `tasks.md:74`'s
  account of the correction.
- Correct: round 3's finding 3 is closed. `tools/loop-cases.sh:8416` now reads "Seventeen columns, so eighteen
  separators"; the assertions under it (`:8420`, `:8422`, `:8424`) require 18 pipes and say "seventeen columns";
  the header (`tools/loop/state.sh:726`) has 17 cells ending `over | calls`, the rule row (`:727`) and the
  empty-state row (`:728`, whose jq expression's own pipe is not a cell) carry the same 18 separators, and
  `table_cell 17` is the ratio (`loop-cases.sh:8628-8629`, `:8654`).
- Correct: round 3's finding 4 is closed as far as the logs' own stamps go — `evidence/gate.log:1556` names
  `HEAD 064b7254` at `2026-11-03T22:06:08Z` and the gate's stamps run `21:52:14Z`–`22:06:08Z`, against a commit
  at `21:51:38Z`; `evidence/mutations.log:3-15` runs `21:51:40Z`–`21:51:57Z`, after that commit, and closes
  `red-when-mutated: 4 · not-noticed: 0 · restore-failures: 0`; `evidence/red-on-head.log` is the FAIL
  (`expected '6', got 'null'`, `1 of 1 cases failed`); the tallies read `tests="310" failures="0"` over
  `selected 310 of 310` (`gate-tally-loop-selftest.xml`, `gate.log:4`) and 231/0
  (`gate-tally-mutations-check.xml`).
- Finding: P1 — `tasks.md:77` records round 3 as `VERDICT: PASS`; `evidence/review-round-3.md:110` reads
  `VERDICT: NOTES`, that report raised one P1 and three P2s (`:94`), and it called the claim written ahead of
  the act (`:47`). A task marked done on a verification that did not pass. Smallest fix: at `tasks.md:77` write
  `VERDICT: NOTES` and name what stands — or point the line at `review-round-4.md`.
- Finding: P2 — `proposal.md:71` reads "every one of them raised / tby the two verbatim review reports": `tby`
  is a typo for `by`, introduced by the round-3 repair. Smallest fix: `tby` → `by`.
- Finding: P2 — `tasks.md:63` says "the `LOOP-GATE:` line names the commit it measured"; the line
  (`gate.log:1610`) names `--from-git 47ec83e5ee8c…`, which `tools/loop/gate.sh:135` computes as
  `git merge-base HEAD origin/main` — the base the selection was taken from, not the measured `HEAD`. The
  measured commit appears only incidentally, in the issue-tooling step's own line (`gate.log:1556`). Round 3
  said the same line names no revision at all (`review-round-3.md:94`); the accurate statement is the one
  between them, and `mutations.log` still names no revision — only its internal times place it after the
  commit. Smallest fix: "names the base it selected since, and the step at `:1556` names the measured `HEAD`".
- Finding: P2 (carried from rounds 2 and 3, unchanged) — `evidence/red-on-head.log` is five lines with no
  revision and no time, so the parent it failed on is asserted only by `proposal.md:22` and `tasks.md` task 1,
  not by the artifact. Smallest fix: one header line naming `5e2de33e` and the run's time.
- Finding: P2 (carried, unchanged) — `tasks.md:96` says the change is archived and the delta merged, while the
  directory still sits under `openspec/changes/` and
  `grep -c 'shape a run spent its budget in' openspec/specs/agent-issue-loop/spec.md` is `0`.
- Merge verdict: **OK with notes** — no engine line and no case assertion is wrong, all four round-3 findings
  are substantively closed, and one record line (`tasks.md:77`) plus one typo (`proposal.md:71`) state the
  wrong thing about a check.

## 1. Round 3's four findings, one by one

| round-3 finding | state | evidence |
|---|---|---|
| **P1** — the "none in an artifact of this change" claim was false, and the retained check log was stale | **closed** | `evidence-check.log:1-10` is 9 + 1 findings, `:16` `FAIL (10)`; the nine C1 lines resolve to `review-round-1.md:136-137` and `review-round-2.md:102,103,120,122,131,132,133`; no section-sign citation survives in `proposal.md`, `tasks.md`, `design.md`, `specs/`; `tasks.md:83-84` and `proposal.md:70-72` state the two classes |
| **P2** — p75 quoted 1.21 against the archive's 1.22 | **closed** | `loop.conf:129`, `Backlog.recipes.md:318`, `design.md:44`, `proposal.md:15` all 1.22 |
| **P2** — "Sixteen columns, so seventeen separators" above an 18-pipe assertion | **closed** | `loop-cases.sh:8416` "Seventeen columns, so eighteen separators"; `:8420` asserts 18; header `state.sh:726` has 17 cells |
| **P2** — `mutations.log` recorded before the commit | **closed (as stamps)** | `mutations.log:3-15` `21:51:40Z`–`21:51:57Z` against the commit at `21:51:38Z`; `gate.log:1556` names `HEAD 064b7254` at `22:06:08Z`; neither log names a revision in its own frame — see the P2 on `tasks.md:63` |

## 2. The check log against the tree it describes

Ten findings, and the source of each: the nine C1 lines are the section-sign pointers in the two retained
reviewer reports, and the one C2 line is a report's quotation of the gate's summed figure, which no per-suite
JUnit XML carries — the tallies that do exist are `310`/`0` (loop suite) and `231`/`0` (record check). Both
classes are the check reading reviewer prose as a claim, which is why they are reported and not repaired here.
The log was taken before this file existed: its C5 line counts 800 resolved citations, and this report adds
more, so a re-run over the archived tree will move that count, not the verdict.

## 3. Does any claim in `tasks.md` outrun its evidence

Yes, three: `:77` (round 3 recorded as PASS against `VERDICT: NOTES`), `:63` (the gate's summary line named as
carrying the measured commit, where it carries the selection base), and `:96` (archived and merged, where
neither happened). Task 13's ten-findings claim (`:83-92`) does not outrun the log.

## 4. What I did not verify

- The self-test, the gate and the mutation set were not re-run, per this round's remit; `gate.log:4`'s
  `selected 310 of 310` and the new case's PASS are relied on as the author left them.
- The C5 count (`evidence-check.log:14`) was not traced line by line; the check itself treats it as report-only.
- The "3 909 of them a second read" half of `proposal.md:16-17` stands unverified, as in rounds 2 and 3.

VERDICT: NOTES
