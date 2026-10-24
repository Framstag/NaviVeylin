# Tasks

Ordered. Each task names the verification that closes it; nothing is ticked on a cached or partial run.

- [x] 1. Root cause and the red test (Phase A). Verified against the tree: `run.sh:926` sets `diff_base="$base"`
   once for the whole review loop, `run.sh:931` builds every round's diff from it, the corrective extension at
   `run.sh:990-992` changes nothing about what the next round reads, and `prompts/reviewer.md` said nothing
   about a third round's input. Root cause: the round a corrective refusal buys is built and prompted exactly
   like the round before it.
   Verification: `run#aCorrectiveRoundIsHandedTheCorrections` on the base engine — `FAIL`, `EXIT=1`
   (`evidence/red-HEAD.log`, 2026-10-22T07:49:59Z, and `evidence/red-HEAD.xml`, `tests="1" failures="1"`).

- [x] 2. `tools/loop/run.sh` — `diff_base` as a loop variable (`$base` for every round, the refusing round's
   own HEAD for the round the extension buys), both `write_review_diff` call sites taking it, so the
   correction round reads `git diff <refused>..HEAD`.
   Verification: the case's round-3 assertions — `+the correction` present, `+the first pass` absent as a
   change, while the round-2 diff still carries `the first pass` from the base
   (`evidence/focused-cases-green.log`).

- [x] 3. `tools/loop/run.sh` — `review_correction_scope` renders the section the correction round's prompt
   carries (the range, the refusing report's path, the report quoted), passed through
   `render_reviewer_prompt` as `{{CORRECTION_SCOPE}}` at both call sites.
   Verification: the case asserts the round-3 prompt carries `the demo scenario names no case`,
   `reviewer-report-2.md` and `the correction round alone`; the round-1 prompt carries none of it.

- [x] 4. `tools/loop/prompts/reviewer.md` — the `{{CORRECTION_SCOPE}}` placeholder after the inputs, and the
   paragraph that said a subtractive finding "needs no third review" replaced by what a corrective refusal
   buys and what that round grades.
   Verification: `run#aCorrectiveRoundIsHandedTheCorrections` renders the prompt through the placeholder, and
   `prompt#reviewerCarriesTheEightItems` still passes (the contract's eight items and the two machine-read
   lines are untouched).

- [x] 5. `guidelines/Backlog.md` §7 "The loop" — one paragraph: what the round is handed, why the range is
   what makes it affordable, and that the reviewer process is what the decision kept.
   Verification: `tools/check-doc-routes.sh` (the gate's suite for `guidelines/**`).

- [x] 6. `tools/loop-cases.sh` — `run#aCorrectiveRoundIsHandedTheCorrections`: two refusals (the second
   corrective), then a pass. Its two correction passes are told apart by a gitignored marker under `build/`,
   so neither the marker nor the earlier line reaches an assertion by accident.
   Verification: `PASS run#aCorrectiveRoundIsHandedTheCorrections`, `EXIT=0`
   (`evidence/focused-cases-green.log`), and the whole suite — `tools/loop-selftest.sh` in the gate.

- [x] 7. `tools/loop-mutations.sh` — `40.1` (the correction round's diff base back to `$base`), `40.2` (the
   refusing report dropped from the prompt) and `40.3` (the section substituted after the paths it names),
   one per half of the invariant plus its order.
   Verification: `bash tools/loop-mutations.sh --only '40.1 40.2 40.3'` → `RED 40.1`, `RED 40.2`, `RED 40.3`,
   each `restore … green again`, `red-when-mutated: 3 · not-noticed: 0 · restore-failures: 0`, and a diff
   fingerprint of the mutated files identical before and after (`evidence/mutation-40.log`). `40.3`'s right
   reason, quoted from the case: `every placeholder in the correction section is substituted: '{{' should not
   appear in: # Reviewer — the fixed contract` (`evidence/mutation-40.3-reason.log`, 2026-10-22T08:15:15Z).

- [x] 8. The round-1 review's blocking finding (`LOOP-BLOCKING: 1`): `loop_render` (`lib.sh:409`) substitutes
   one pair at a time, so `{{CORRECTION_SCOPE}}` rendered **last** left the section's own `{{DIFF_PATH}}` and
   `{{CHANGE_ROOT}}` literal. Fixed by making that pair the first one (`run.sh:686-690`), and the case now
   asserts what nothing asserted before: the round-3 prompt names `review-3.diff` and carries no `{{`, and the
   round-1 prompt names `review-1.diff` and carries no `{{` either.
   Verification: the four assertions added at `tools/loop-cases.sh:3379-3387` fail on the pre-fix engine for
   the right reason (`evidence/red-placeholder.log`, 2026-10-22T08:10:43Z, `FAIL`, `EXIT=1`) and pass after the
   fix (`evidence/focused-cases-green.log`, 08:11:03Z, `PASS`, `EXIT=0`); mutation `40.3` keeps the order
   falsifiable.

- [x] 9. The project's gate, once, exactly as the run configures it: `./tools/loop/gate.sh`, whose owed suites
   for this pathspec are `tools/loop-selftest.sh`, `tools/loop-mutations-selftest.sh` and
   `tools/check-doc-routes.sh` (`gate.sh --plan`).
   Verification: `LOOP-GATE: tests=477 failures=0` — `EXIT=0`, `started: 2026-10-22T08:17:49Z`,
   `finished: 2026-10-22T08:34:02Z` (`evidence/gate-round2.log`), the per-suite tallies copied out of the build
   directory into `evidence/gate-tallies/` (`tools_loop-selftest.sh.xml` `tests="156" failures="0"`,
   `tools_loop-mutations-selftest.sh.xml` `tests="34" failures="0"`). `tools_check-doc-routes.sh` ran and its
   tally is `tests="0"`, because that suite prints no `all N cases passed` line for the gate to count — its
   green is its exit status, which is in the same log.

- [x] 10. Archive: the change sits under `openspec/changes/archive/<date>-fix-the-round-a-corrective-refusal-buys/`,
   the delta merged into `openspec/specs/agent-issue-loop/spec.md`, every cited log and XML in its `evidence/`.
   Verification: archived as `openspec/changes/archive/2026-10-22-fix-the-round-a-corrective-refusal-buys/` with its
   `evidence/` (`openspec archive fix-the-round-a-corrective-refusal-buys --yes`); the requirement
   "The round a corrective refusal buys is handed the corrections" and its two scenarios are at
   `openspec/specs/agent-issue-loop/spec.md:1398-1421`; `openspec validate agent-issue-loop --type spec` — only
   the pre-existing "requirement text is very long" warnings; `openspec doctor` — root ok;
   `tools/check-doc-routes.sh` after the archive — `all 120 section references resolve, all 5 skill references
   resolve`, `EXIT=0`.