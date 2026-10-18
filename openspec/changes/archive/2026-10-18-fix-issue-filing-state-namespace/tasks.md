# Tasks

## 1. Reproduce the defect (red on HEAD)

- [x] 1.1 Write `create#refusesTwoValuesOfOneNamespace` in `tools/issue-tooling-cases.sh` — the reproduction
  from issue #179 plus the same contradiction through a second namespace, and the single-route filings that
  must keep working. Parent spec: `issue-backlog` → "Labels carry the item's kind, area, state and
  provenance" → scenario `create#refusesTwoValuesOfOneNamespace`. Verify: the case drives the real `create.sh`
  against the stub `gh` and asserts exit 2, a reason naming both values, and an empty `GH_STUB_RECORD` log.
- [x] 1.2 Record the red-on-HEAD run: `bash tools/issue-tooling-selftest.sh --case
  create#refusesTwoValuesOfOneNamespace` → `FAIL create#refusesTwoValuesOfOneNamespace` (expected `2`, got
  `0`), saved as `evidence/red-on-head.log`. Verify: the file exists and quotes the failure.

## 2. Implement the fix

- [x] 2.1 Add the one-value-per-namespace refusal to `tools/issues/create.sh`, after the vocabulary gate and
  before the dedup/fingerprint work: scan the merged `labels` for two labels sharing the prefix before the
  separator and `issues_die` (exit 2) naming both. Parent spec: `issue-backlog` → "Labels carry the item's
  kind, area, state and provenance". Verify: `create#refusesTwoValuesOfOneNamespace` passes.
- [x] 2.2 Keep the state named only through `--status`, or only through the labels, working. Parent spec: same
  requirement. Verify: the same case's last two filings reach `gh issue create` (the log counts 2).
- [x] 2.3 Do **not** change `tools/issues/fixtures/gh`: the real `gh` accepts a duplicate-namespace label set
  (measured during triage), so a stricter stub would be wrong. Parent spec: same requirement. Verify: the case
  asserts the refusal through the empty write log, not through the stub refusing.

## 3. Falsify the new invariant

- [x] 3.1 Add `tools/create-mutations.json` with one mutation (label `1.1`) whose search is the guard's
  predicate in `tools/issues/create.sh` and whose case is `create#refusesTwoValuesOfOneNamespace`. Parent
  spec: `issue-backlog` → "Labels carry the item's kind, area, state and provenance". Verify: `bash
  tools/mutate.sh --mutations tools/create-mutations.json` reports the case red with the mutation in place and
  green after the restore.
- [x] 3.2 Register the set's `--list` in the "Check issue tooling" step of `.github/workflows/build.yml`, so a
  set that no longer parses fails CI instead of rotting. Verify: `bash tools/mutate.sh --mutations
  tools/create-mutations.json --list` exits 0 and names label `1.1`.

## 4. Verify no regression

- [x] 4.1 Run the owning self-test in full: `bash tools/issue-tooling-selftest.sh` → all 77 cases pass.
  Parent spec: `issue-backlog` (the whole capability). Exclusion: no Gradle and no device — this change touches
  no compiled file, renders nothing and logs no coordinate, so no build or suite outside these offline shell
  self-tests is affected (`guidelines/Build.md` §2, the scope block).
- [x] 4.2 Run the two other self-tests that drive `create.sh`: `bash tools/sensor-selftest.sh` and `bash
  tools/check-portability-selftest.sh` → both pass. Verify: they file findings through the real `create.sh`
  and no case turns red on the new guard.

## 5. Land and close

- [x] 5.1 Run `openspec validate fix-issue-filing-state-namespace --type change` → exit 0. Verify: the
  command's exit status.
- [x] 5.2 Archive the change with the `openspec-archive-change` skill into
  `openspec/changes/archive/<YYYY-MM-DD>-fix-issue-filing-state-namespace/` (move, not copy). Verify: the
  archive directory exists and the change dir is gone.
- [x] 5.3 One commit: `fix: fix-issue-filing-state-namespace (#179)`, adding every touched file. Verify:
  `git status --porcelain` is clean afterwards (bar the untracked `Agentisches-Coding-NaviVeylin.md`).
- [x] 5.4 Mark issue #179 `status/fixed-by` with a comment citing the archived change, the selftest and the
  `file:line` that now makes the defect impossible — via `tools/issues/update.sh`, since #179 carries no
  `agent/eligible` and `close.sh` would refuse (`missing-eligibility-label`). The closure itself is owed to the
  owner. Verify: `tools/issues/update.sh 179 --status fixed-by --comment-file …` exits 0 and the item carries
  one `status/*` label.
