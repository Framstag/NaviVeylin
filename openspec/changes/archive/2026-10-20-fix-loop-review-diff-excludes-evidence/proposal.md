# Proposal — fix-loop-review-diff-excludes-evidence

## Why

Every review round is handed a diff of the whole tree an item changed, and most of that tree is not the change:
it is the evidence the change archives beside its code. Item #126's round-3 diff was **435 KB and 9 935 changed
lines**, of which **9 364 (94.3 %) were evidence artefacts** —
`evidence/gate-reports/app-mobile.html` 3 143 lines, `app-automotive.html` 3 143, `auto.html` 1 042,
`core.html` 781, `osmscout-client-java.html` 195, `evidence/revert-check-mutated.xml` 400,
`evidence/red-buildsrc-scan.xml` 187 — while the change itself was **112 lines** of Kotlin
(`HardcodedStringScanner.kt` 62, its test 50). Three review rounds are therefore roughly 300 000 tokens of
HTML and XML for a change a reader holds in one screen. The repository carries the same shape: **609 tracked
evidence files** under `openspec/changes/archive/*/evidence/`, 550 of them html/xml/log/txt, 23 MB.

Root cause, and the one measurement that settles it: `run.sh` builds the round's reading surface with a diff
of everything between the run's base and the item's HEAD, with no pathspec at all —

```sh
# tools/loop/run.sh, before this change (two sites: the round's first diff and each later round's)
git -C "$worktree" diff "$base"..HEAD > "$review_diff"
```

— so anything a worker committed, generated output included, enters the reviewer's context with the same
weight as the hunk it is being asked to judge. Re-measured on the run's own copy of that file, the shape is the
same: 10 883 lines and 446 092 bytes, of which 9 574 are inside an `evidence/` tree
(`evidence/red-round3-diff-size.txt`, which also records the 609 tracked evidence files and the 23 MB of
`openspec/changes/archive`). The run already knows the evidence by another route: the
change's own `evidence/` directory is verified on the file system (`change_evidence=…` and `carried=`) and
handed to the reviewer as `EVIDENCE_DIR`. Nothing needs the raw bytes in the diff.

Failing case and its red run on the unpatched script (evidence, not assertion):

- case `run#reviewDiffExcludesTheEvidenceArchive` (`tools/loop-cases.sh`) has the stub worker write a code file,
  a note a reviewer must read that is **not** under an evidence tree, and a 300-row
  `openspec/changes/archive/…/evidence/gate-report.html`; it runs one item with `--review on` and reads the
  produced `review-1.diff`.
- With the pathspec removed from `write_review_diff` (one line) the case fails with
  `the generated gate report is not part of the review's reading surface: 'gate-report.html' should not appear
  in: diff --git a/Demo.kt …` (`evidence/case-red-unpatched.log`, exit 1).
- With the pathspec in place the same case passes (`evidence/case-green.log`, exit 0), and it still asserts
  `Demo.kt`, its hunk and `notes-for-the-reviewer.txt` are in the diff.

Owning requirement, and the gap the defect exposes: `agent-issue-loop` — "Each item is worked by a fresh
process, reviewed by a different one". Its scenario *The reviewer is not the writer* says who reads, and
nothing said **what** a round is handed; that silence is where a generated archive became a review artefact.
This change ADDs the missing requirement, "A review round reads the change, not the tree it sits in"; no
existing requirement's text moves.

One fix follows from this root cause, and no second fix is defensible: the reading surface is built in one
place, by one function, and the only thing wrong with it is what the pathspecs let through. The alternatives
the evidence eliminates are in `design.md`.

## What Changes

- `run.sh` gains `write_review_diff <base> <file>`, the one place a review diff is written (both round sites
  use it), which applies the project's exclusion pathspec and falls back to the plain diff when the project
  configures none.
- `loop.conf` gains the recorded key `LOOP_REVIEW_EXCLUDE_PATHSPEC`, `":(exclude,glob)**/evidence/**"` here, so
  which directories hold generated output stays a fact of the project's change records and not of the phase
  (D16, the same rule that keeps `LOOP_GATE_CMD` and `LOOP_WORKER_SKILLS` out of the engine).
- `prompts/reviewer.md` gains one line: the diff excludes the generated evidence trees, by pathspec, and the
  evidence reaches the reviewer by path through `{{EVIDENCE_DIR}}` — so the reviewer neither greps the diff for
  it nor reports its absence as a missing artefact. The pathspec is a `{{REVIEW_EXCLUDE}}` placeholder, rendered
  from the same configuration the diff is built with.
- `.pi/skills/fix-loop/SKILL.md` gains the subsection "What a reviewer receives" under what the wrapper owns.
- `tools/loop-cases.sh` gains the case `run#reviewDiffExcludesTheEvidenceArchive`.

Additive: no flag, exit status, state field, verdict or worktree path changes, and the reviewer's eight-item
contract is untouched — only the artifact it is asked to read. Rollback: revert `tools/loop/run.sh`,
`tools/loop/loop.conf`, `tools/loop/prompts/reviewer.md`, `tools/loop-cases.sh` and
`.pi/skills/fix-loop/SKILL.md`; a round is then handed the whole diff again, with nothing else changed.

## Capabilities

### New Capabilities

None.

### Added Requirements

- `agent-issue-loop`: one new requirement, "A review round reads the change, not the tree it sits in", with
  three scenarios — the round's diff excludes the configured generated trees and keeps everything else, the
  exclusion is a recorded value rather than a rule of the phase, and the reviewer is told the evidence arrives
  by path so an absence is not an omission. Each is exercised by the single case
  `run#reviewDiffExcludesTheEvidenceArchive`. No existing requirement is modified, deleted or reordered.

## Impact

- `tools/loop/run.sh` — one new function `write_review_diff` and the two calls that replace the two inline
  `git diff` invocations; the review loop, the round budget, the verdict reading and the reviewer-wrote check
  are untouched.
- `tools/loop/loop.conf` — one recorded key, `LOOP_REVIEW_EXCLUDE_PATHSPEC`, documented with its measurement.
- `tools/loop/prompts/reviewer.md` — one bullet extended by two clauses and a placeholder.
- `tools/loop-cases.sh` — the new shell case, added to `LOOP_SHELL_CASES` and to the dispatcher of
  `loop_run_shell_case`.
- `.pi/skills/fix-loop/SKILL.md` — one new subsection.
- `openspec/specs/agent-issue-loop/spec.md` — gains the ADDED requirement at archive time; this change's delta
  is the only place it is written now.
- No app module, manifest, resource, stylesheet or native file is touched; no phone/car variant is affected, so
  the parity requirement of `guidelines/UI.md` §1 does not apply.
- Guidelines affected: none. `guidelines/Build.md` §2 owns the iteration protocol the loop runs and says
  nothing about what a review round reads; `AGENTS.md` "Backlog maintenance" names the loop's entry point, its
  skills and its gate only. The loop's own operating document is the skill, which is updated here.

## Scope

Tooling only — the backlog loop that closes defect-shaped items. It is not a feature of the app, of the Android
Auto integration or of the phone UI.
