# Tasks — the filing skills carry the type axis

- [x] 1. Grow the case's file list to the four skills a filing session leaves through, and add the clause that the
      two filing skills' documented call line carries both flags. Take the red proof on HEAD, with both skills at the
      base commit's text: `bash tools/issues/tests/issue-tooling-selftest.sh` — exit 1,
      `.pi/skills/file-finding/SKILL.md no longer names --type, so a filing that follows it cannot carry the type
      axis`, `issue-tooling-selftest: 1 of 188 cases failed` (`evidence/red-on-base.log`).
- [x] 2. Name `--type` and `--parent` on the heredoc call at `.pi/skills/file-finding/SKILL.md:41` and on the per-child
      call at `.pi/skills/reshape-issue/SKILL.md:94`, with one sentence below each saying what the two flags write and
      where the type's name comes from. Verified by the same case (`evidence/green.log`, `all 188 cases passed`).
- [x] 3. Falsify the call-line clause on each file: strip the flags from the call line, leave the sentence below it,
      and expect the suite's own tally and the message naming the file
      (`evidence/mutation-red.log` for `reshape-issue`, `evidence/mutation-red-file-finding.log` for `file-finding`);
      restore the file byte for byte and run the case again (`evidence/mutation-restored.log`).
- [x] 4. Structural check of the three files this change edits, in place of a build (it compiles nothing):
      `bash -n tools/issues/tests/issue-tooling-cases.sh`, and `tools/check-doc-values.sh` with
      `tools/check-doc-routes.sh` over the tree the two skills sit in (`evidence/structural-checks.log`).
- [x] 5. Name the delta: the modified requirement in `specs/issue-backlog/spec.md` carries the four files, the
      call-line clause and the one scenario, whose case is the red test.
- [x] 6. Take the measuring command's block for this change and keep it beside the evidence:
      `./tools/loop/measure.sh --change fix-the-filing-skills-carry-the-type-axis` (`evidence/measure.log`).
- [x] 7. Complete the change before the gate — the pointers above re-read against the edited files, the evidence
      copied into `evidence/`, `bash tools/loop/evidence-check.sh <the change directory> --root .` clean
      (`evidence/evidence-check.log`), and the archive written with the delta it merges (`openspec archive --yes`).
      The gate is run once after that archive, by this change child, and its tally is quoted in the item's report
      rather than claimed here.
