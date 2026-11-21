# Review round 1 — findings answered

Reviewer report: `review-round-1-report.md` (VERDICT: FAIL, 4 should-fix, 6 nit). Every finding is answered here; two
of them changed code, the rest changed what the change claims.

1. **should-fix — the case does not catch a new session fact.** *Applied, in code.* `vars` is now read out of
   `tools/loop/gate.sh`'s own `item_session_vars=(…)` declaration rather than copied
   (`tools/loop/tests/loop-cases.sh:6934-6941`), and a second check asserts that every `LOOP_*` name
   `tools/pi-child-events.sh` exports is in that list — so a fact added to the adapter without the list is named
   before any step runs, and a fact added to the list is asserted. A tree that declares no list (HEAD) falls back to
   the adapter's exports plus the switch, so the case still fails *on the leaked facts* there rather than on the
   missing declaration (`evidence/red-on-HEAD-980-case.log`).
2. **should-fix — the two `base-red.log` citations were swapped.** *Applied.* `proposal.md`: `:9525` is the adapter
   failure and `:9041` the gate failure. Verified against the log.
3. **should-fix — "865–944 s per base, five base shas".** *Applied.* The log holds **six** measurements of the leg —
   907, 869, 944, 859, 865 and 509 s — so the sentence now reads "all six of them, 859–944 s each, the newest 509 s"
   in `proposal.md`, `design.md`, `tools/loop/gate.sh`, `tools/loop/tests/loop-cases.sh` and
   `tools/loop/tests/loop-mutations.sh`. The scope sentence is corrected too: only a gate whose delta owes
   `tools/loop/**` runs the leg and carries the subtraction.
4. **should-fix — task 3.2 claimed a red run no evidence recorded.** *Applied.* `evidence/mutation-63.1.log` is the
   record's own run: red on `the step ran on after its command exited`, restore, green.
5. **should-fix — task 4.1 under-reported its run.** *Applied.* It names both reds of the first run: the stale `63.1`
   (`mutation#everyDeclaredRecordApplies`, this change's own doing and the reason for task 3.2) and the
   `run#childUsageIsRecordedPerItem` flake, with the flake attribution on the second alone.
6. **nit — the canary assertion doubled as the session assertion.** *Applied.* The step prints `RAN=1` first and the
   non-vacuity assertion is on that line, so a red run says the fixture ran and names the fact that leaked.
7. **nit — scenario 1's THEN has two halves and named one case.** *Applied.* The `Case` line now also names
   `gate#aThirdFullRunInOneSessionIsRefused`, which proves the gate keeps reading `LOOP_CHILD_EVENTS` itself.
8. **nit (prose) — the class was defined more widely than the seven.** *Applied.* `gate.sh`, `design.md` and the case's
   comment now state the class as closed: what the adapter exports for one child, the item's event stream, and the
   switch. *Deferred, as the reviewer says:* `LOOP_STATE_FILE`/`LOOP_WORKTREE` stay in a step's environment, and
   dropping them is a second change — `design.md` says what it would have to measure.
9. **nit — typo.** *Applied*: `tools/loop/tests/loop-mutations.sh:1530`.
10. **nit — the `--check` file set named two of three files.** *Applied.* `tasks.md` now says what the 28/28 covers
    (the two files whose launch lines the change touches) and points at the full 440/440 for the rest.

Gate after the review round, over the whole set: 519 of 519 green (`evidence/full-suite-review.log`, 564.84 s), which
also covers `63.1`'s and `980.1`'s applicability (`mutation#everyDeclaredRecordApplies`).
