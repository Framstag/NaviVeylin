# Review verdict — round 7 (archive gate)

Reviewer: read-only subagent, run `61608b44-25d0-4d55-977a-4d30d455506d` (revived as
`8340474f-ec91-47ca-a64c-96c4fa6a8baa` for the truncated reprint). No gate, no suite, no network; quotes green
results from the evidence by path. The reviewer wrote nothing — its sandbox denies writes — so its verdict is
filed here by the parent.

## VERDICT: FAIL — LOOP-BLOCKING: 1

### Resolved from earlier rounds

1. **B11 → RESOLVED.** `evidence/gate-full.txt:13-22` states 860 files / 6449 tests / 0 failures / 0 errors with
   the split. Re-counted with the file's own command (`-newermt '2026-11-24 11:30'`): 860 files; totals
   `tests="…"`→6449, `failures="…"`→0, `errors="…"`→0 (same without the time filter, so the window is not
   load-bearing). Direct per-module: auto 99/979, core 70/678, osmscout-client-java 23/112, architecture 12/38;
   app 656 files (328 mobile + 328 automotive, counted) and 4642 tests = 6449 − 1807 (derived, = 2321+2321).
   `tasks.md:145-148` and `design.md:251` state the same. All five modules are `failures="0" errors="0"` (global
   sum is 0/0).
2. **N8 → RESOLVED.** `evidence/gate-sensors.txt:67-70` matches measurement: `git rev-list --count
   77973d53..82305af6` = 264; `…77973d53..HEAD` = 268 at `5e8ebbd6` (269 now, see NOTE 2); `git diff --stat
   82305af6..HEAD -- tools/loop/tests/loop-cases.sh` is empty, and `1171f5e4`, `52eaddc3` (the #1048 loop edits)
   are ancestors of `82305af6`, so "none of them touched loop-cases.sh" holds for the commits after it.
3. **Six verdicts → RESOLVED.** All six files exist in `evidence/` (`review-verdict.md`, `-2`, `-3`, `-4`, `-5`,
   `-6`) and each claim in `tasks.md:134-142` matches the file's trailer: v1 FAIL/4 (`review-verdict.md:134-135`),
   v2 PASS/0 (`review-verdict-2.md:51-52`), v3 FAIL/3 (`review-verdict-3.md:10`), v4 FAIL/3 with N6/N7 + all seven
   CORRECT (`review-verdict-4.md:11,48-77`), v5 FAIL/4 = B7–B10 (`review-verdict-5.md:10,39`), v6 FAIL/1 = B11 +
   N8 (`review-verdict-6.md:11,47,65`). One wording error, see NOTE 1.

### Blocking

**B12 — the range counts no longer describe the range.** HEAD advanced mid-review to `c76b38f9` ("test: keep the
source-switch case's registry off the network (#1048)") — `git log --oneline --reverse d885082d..HEAD` lists 12
commits, not 11. `proposal.md:251` "eleven commits (`git rev-list --count d885082d..HEAD`)" — that command
returned 11 at `5e8ebbd6` and returns 12 now. `proposal.md:259`'s surface line was verified exact at `5e8ebbd6`
(`git diff --numstat d885082d..HEAD` = 10 files / 271 insertions / 12 deletions, per-file splits match
`proposal.md:235-245`); the range now carries one more commit and one more file, so the line and `proposal.md:8`'s
"ten files" no longer describe the range. Smallest fix: re-pin the range counts (or name the excluded commit) and
confirm the new commit's own suite run, since it edits a compiled test file (`tasks.md:3` said "One task edits a
compiled test file"). The reviewer did not re-run numstat after the tree moved.

### NOTEs

- **NOTE 1 (P2).** `tasks.md:136-137` said round 3's "three BLOCKING items [were] against causes 5 and 6".
  `review-verdict-3.md:26-28` B1 targets `tasks.md` §7.2 (cause 7's section), and `review-verdict-3.md:20` records
  cause 5 as CORRECT (NOTE N1). Fix: "against causes 5, 6 and 7".
- **NOTE 2 (P2).** `evidence/gate-sensors.txt:70` "restate 268" — 269 at `c76b38f9`. Same class as B12.
- **NOTE 3 (P2).** `evidence/` holds 17 files, not the 20 the round-7 brief named; no change record claims a file
  count, so harmless.
- **NOTE 4.** The live `app/build/test-results/testMobileDebugUnitTest` was rewritten by a fresh run during this
  review (`MapManagerSourceSwitchPlanTest`, 12:32) — a re-count of B11 must read the gate run's own archived XMLs,
  not the live tree.
- **NOTE 5.** No contradiction found against the seven causes; round 6's CORRECTs and B7–B10's resolutions hold in
  the files as read. Tooling limits: the reviewer's shell was stopped after B12's measurements, so B12's numstat
  was not re-measured by it and the line numbers inside the verdict files are from its reads.

## The parent's answer to each item

- **B12 → corrected.** `proposal.md:251` now says "twelve commits"; `proposal.md:8` now says "eleven files"; the
  surface line now reads "11 files, 276 insertions, 14 deletions … 3 test files" and counts with
  `git diff --numstat d885082d..HEAD`, measured at `c76b38f9`: 11 files, 276 insertions, 14 deletions. The new
  commit's own suite run is quoted in `evidence/gate-source-switch.txt` (9 tests, 0 failures, 0 errors in each
  `:app` flavor). The eighth cause is now stated in the record: `proposal.md` §8, `design.md` D8 and verification
  row 9, `tasks.md` §8.
- **NOTE 1 → corrected.** `tasks.md` now reads "against causes 5, 6 and 7".
- **NOTE 2 → corrected.** `evidence/gate-sensors.txt` now states 269 at `c76b38f9`.
- **NOTE 3 → no action.** No record claims an evidence-file count.
- **NOTE 4 → recorded.** `evidence/gate-full.txt` now states that a re-count must read the run's archived XMLs, not
  the live tree, and names the source-switch run that rewrote it.
- **NOTE 5 → no action.** The eight causes' CORRECTs stand.
