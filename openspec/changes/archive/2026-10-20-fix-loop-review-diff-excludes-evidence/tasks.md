# Tasks — fix-loop-review-diff-excludes-evidence

Parent specification for every task below: `agent-issue-loop`, requirement "A review round reads the change,
not the tree it sits in" (this change's ADDED delta). The change touches no compiled file — shell, a prompt
skeleton, the configuration and the skill — so the structural checks of the files it edits plus the loop's own
self-test stand in for a build (`guidelines/Build.md` §2, the scope block).

## 1. The case that reproduces the reading surface

- [x] 1.1 Add the shell case `run#reviewDiffExcludesTheEvidenceArchive` to `tools/loop-cases.sh` — registered in
      `LOOP_SHELL_CASES` and in the dispatcher of `loop_run_shell_case` — with a stub worker that writes
      `Demo.kt`, a `notes-for-the-reviewer.txt` outside any evidence tree, and a 300-row
      `openspec/changes/archive/2026-01-01-fix-demo/evidence/gate-report.html`; the case runs one item with
      `--review on` and asserts that the produced `review-1.diff` carries `Demo.kt`, its hunk and the `.txt`,
      carries neither `gate-report.html` nor `gate row 0`, and that `reviewer-prompt-1.md` names the evidence
      directory and the exclusion its diff was built with. Parent spec: `agent-issue-loop`, scenarios "The
      round's diff carries the change and not the generated evidence" and "The reviewer is told where the
      evidence went". **Verify**: on the unpatched `write_review_diff` the case fails at `the generated gate
      report is not part of the review's reading surface: 'gate-report.html' should not appear in: diff --git
      a/Demo.kt …`, recorded in `evidence/case-red-unpatched.log`.
- [x] 1.2 Record the red and the restore: copy `tools/loop/run.sh`, mutate the one line that applies the
      pathspec out of `write_review_diff`, run the case (exit 1), copy the file back, run it again (exit 0).
      **Verify**: `evidence/case-red-unpatched.log` (`FAIL`, exit 1) and `evidence/case-green.log` (`PASS`,
      exit 0), with `git diff` of `run.sh` back to the fix afterwards. This pair is the falsification the
      change owes: the exclusion is the only difference between the two runs.
- [x] 1.3 Record the measurement the change exists for, from the real artefact: the round-3 diff item #126's
      run wrote, split into its `diff --git` blocks. Parent spec: `agent-issue-loop`, requirement "A review
      round reads the change, not the tree it sits in", first clause. **Verify**:
      `evidence/red-round3-diff-size.txt` — 10 883 lines / 446 092 bytes, 9 574 of them (88.5 %) inside
      `evidence/`, and the tree at rest: 609 tracked evidence files, 23 MB of `openspec/changes/archive`.

## 2. The fix

- [x] 2.1 Add `write_review_diff <base> <file>` to `tools/loop/run.sh` — the one place a review diff is written,
      applying `LOOP_REVIEW_EXCLUDE_PATHSPEC` as `git diff <base>..HEAD -- . "<spec>"` and falling back to the
      plain diff when the project configures none — and replace both inline `git diff` invocations with it (the
      first round's `review-<round>.diff` and each later round's). Parent spec: `agent-issue-loop`, scenarios
      "The round's diff carries the change and not the generated evidence" and "The exclusion is a recorded
      value, and an empty one is no exclusion". **Verify**: `bash -n tools/loop/run.sh` clean, and
      `evidence/case-green.log` green — the round's diff carries the change and not the evidence.
- [x] 2.2 Record the exclusion in `tools/loop/loop.conf` as `LOOP_REVIEW_EXCLUDE_PATHSPEC=":(exclude,glob)**/evidence/**"`,
      documented like the other recorded values, with the measurement that justifies it. Parent spec:
      `agent-issue-loop`, scenario "The exclusion is a recorded value, and an empty one is no exclusion".
      **Verify**: the key's presence is what `write_review_diff` reads and what the rendered prompt carries —
      asserted by the case (`**/evidence/**` in `reviewer-prompt-1.md`); `bash -n tools/loop/loop.conf` clean.
- [x] 2.3 Extend the diff bullet in `tools/loop/prompts/reviewer.md`: the diff excludes the generated evidence
      trees by pathspec, `{{REVIEW_EXCLUDE}}`, and the evidence reaches the reviewer by path through
      `{{EVIDENCE_DIR}}`, so it neither greps the diff for it nor reports its absence as a missing artefact;
      render `REVIEW_EXCLUDE` from the same configuration value the diff is built with. Parent spec:
      `agent-issue-loop`, scenario "The reviewer is told where the evidence went". **Verify**: the case asserts
      the rendered prompt carries both the item's evidence directory and `**/evidence/**`, and
      `prompt#reviewerCarriesTheEightItems` still passes — the eight items and both machine-read fields are
      untouched.
- [x] 2.4 Keep the loop portable: no path of this project's change records in a phase or a prompt skeleton. The
      pathspec is the configuration's (`loop.conf`), and the prompt names it through a placeholder. Parent spec:
      `agent-issue-loop`, requirement "The run's mechanics are the project's configuration, not its code".
      **Verify**: `evidence/case-engine-name-check.log` — `run#engineNamesNoProjectFact` PASS — and
      `tools/check-portability.sh` OK (42 engines and 2 templates name no project value).

## 3. The operating document

- [x] 3.1 Add the subsection "What a reviewer receives" to `.pi/skills/fix-loop/SKILL.md`, under what the wrapper
      owns: the round's diff is the change, the exclusion is the project's recorded pathspec, it is positional
      rather than by extension, and the evidence arrives by path. Parent spec: `agent-issue-loop`, all three
      scenarios. **Verify** (structural, no build):
      `sed -n '/^### What a reviewer receives/,/^## The eligibility gate/p' .pi/skills/fix-loop/SKILL.md` names
      the key, the directory-not-extension rule and `EVIDENCE_DIR`.
- [x] 3.2 Guideline update: none owed. `guidelines/Build.md` §2 owns the iteration protocol the loop runs and
      says nothing about what a review round reads; `AGENTS.md` "Backlog maintenance" names the loop's entry
      point, its gate and its skills only. **Verify** (structural): `git status --short guidelines/ AGENTS.md`
      is empty.

## 4. The gate for `tools/loop/**`

- [x] 4.1 Run the two cases this change can move, and only those — the orchestrator runs the whole suite once,
      on the merged tree (`guidelines/Build.md` §2, "iterate with focused suites and gate once"). **Verify**:
      `bash tools/loop-selftest.sh --case 'run#reviewDiffExcludesTheEvidenceArchive'` → `PASS`
      (`evidence/case-green.log`), `bash tools/loop-selftest.sh --case 'run#engineNamesNoProjectFact'` → `PASS`
      (`evidence/case-engine-name-check.log`), both offline, no token, no device.
- [x] 4.2 Confirm the change itself needs no build: no Kotlin, Java, C++ or resource file is touched.
      **Verify** (structural): the committed files are `tools/loop/run.sh`, `tools/loop/loop.conf`,
      `tools/loop/prompts/reviewer.md`, `tools/loop-cases.sh` and `.pi/skills/fix-loop/SKILL.md` — the five a
      `git show --stat` of the commit lists; this record itself is open WIP under an ignored `openspec/changes/`
      and is versioned when the loop archives it (`.gitignore`, "only archived changes are versioned").
- [x] 4.3 `openspec validate fix-loop-review-diff-excludes-evidence` and `openspec doctor` clean. **Verify**:
      `evidence/validate-doctor.log`.
