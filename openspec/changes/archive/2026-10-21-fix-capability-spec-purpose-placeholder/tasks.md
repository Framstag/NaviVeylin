## 1. Record the defect

- [x] 1.1 Save the probe as `evidence/red-probe.sh` — one `awk` pass over every `spec.md` found under
      `openspec/specs/` at any depth, reading a `## Purpose` section and printing any offending spec and
      line, exiting non-zero when there is one — and record the diagnosis that preceded it, `grep -rln
      'TBD - created by archiving' openspec/specs/*/spec.md`. Verification: the diagnosis prints
      `openspec/specs/navigation-ongoing-notification/spec.md` and `openspec/specs/render-mode-switch/spec.md`
      on base `44b673f`, exit 1; its output is `evidence/red-probe.txt` (2026-10-21T11:22:13Z), and the
      shipped probe's red half is the mutation in `evidence/revert-check.txt`. No JUnit XML: the delta
      touches no compiled file.
- [x] 1.2 Record the validator's part of the mechanism: `openspec validate <id> --type spec` exits 0 while
      printing `⚠ [WARNING] overview: Purpose section is still a placeholder`. Verification: the two
      warnings and both exit codes are captured in `evidence/openspec-validate-red.txt`.

## 2. Write what each capability is for

- [x] 2.1 Replace `openspec/specs/navigation-ongoing-notification/spec.md:4` with one sentence naming what
      the capability is for. Verification: `openspec validate navigation-ongoing-notification --type spec`
      no longer prints an `overview` warning for that spec (`evidence/openspec-validate-green.txt`).
- [x] 2.2 The same for `openspec/specs/render-mode-switch/spec.md:4`. Verification: the same command for
      `render-mode-switch` prints no `overview` warning, and `bash evidence/red-probe.sh` now prints only the
      second path until 2.2 lands, then nothing (exit 0, `evidence/red-probe-green.txt`).

## 3. Make the mechanism report itself

- [x] 3.1 Add the check to the CI step `Check OpenSpec artifact hygiene` in `.github/workflows/build.yml`,
      accumulating into the step's existing `fail` variable, and correct the `AGENTS.md` "OpenSpec Workflow"
      count from two CI-gated artifact rules to three, with the new rule as a bullet. Verification: the
      step's body run locally over the tree prints `Every capability spec states its Purpose.` and
      `OpenSpec artifacts are consistent.` and exits 0 (`evidence/hygiene-step.txt`, which runs the shipped
      block extracted verbatim from the workflow by `evidence/hygiene-step.sh`).
- [x] 3.2 Revert-check the new invariant: put `TBD - created by archiving change render-mode-switch. Update
      Purpose after archive.` back under that spec's `## Purpose`, run the step, and require it to name
      `openspec/specs/render-mode-switch/spec.md` and exit 1; restore the sentence and require exit 0.
      Verification: both halves are `evidence/revert-check.txt`. No Gradle re-run: the mutation is a
      Markdown line, so the green half is the same check, not `./gradlew test`.

## 4. State the requirement

- [x] 4.1 `specs/documentation-ownership/spec.md` carries the ADDED requirement *A capability spec states
      what the capability is for*, with its three scenarios, each naming the case that exercises it.
      Verification: every scenario line names a case (`grep -c '^- \*\*AND\*\* the case' …` = 3 in both
      the delta and the merged requirement), and `openspec validate --all` exits 0 over the merged spec
      (`evidence/openspec-validate-green.txt`). The change-scoped validator
      (`openspec validate fix-capability-spec-purpose-placeholder --type change`) was run before the
      archive and is not re-runnable afterwards — the CLI's change list is empty once a change is archived,
      so the post-archive subject is the merged capability spec.

## 5. Verify and archive

- [x] 5.1 Trace every scenario to a case and every task to a requirement: scenario 1 →
      `artifact-hygiene#theArchivePlaceholderIsReported` (tasks 1.1, 3.1, 3.2); scenario 2 →
      `artifact-hygiene#aWrittenPurposePasses` (tasks 2.1, 2.2, 3.1); scenario 3 →
      `artifact-hygiene#aNestedCapabilitySpecIsReported` (tasks 6.1, 6.2). Verification: no scenario without a
      case and no task without a scenario, read back from these two files.
- [x] 5.2 Run the change's structural checks: `bash evidence/red-probe.sh` (exit 0), the CI step's body
      (exit 0), `openspec validate --all`, `openspec doctor`. No Gradle gate is owed and none is run — the
      delta touches no compiled file (`guidelines/Build.md` §2, its scope block); that exclusion is stated
      in the report. Verification: `evidence/` holds one file per command with its exit code.
- [x] 5.3 Archive the change and confirm the requirement merged into
      `openspec/specs/documentation-ownership/spec.md` while the two `## Purpose` sentences were *not*
      overwritten. Verification: the archive directory exists under `openspec/changes/archive/`, `grep -n
      "A capability spec states what the capability is for" openspec/specs/documentation-ownership/spec.md`
      matches once, and `bash evidence/red-probe.sh` still exits 0.
- [x] 5.4 Copy every cited result into this change's `evidence/` directory. Verification: each file named
      above exists there and in `.pi/issue-loop/items/11/`.

## 6. Correct the round-1 review

- [x] 6.1 The pass covers every capability spec, not only the top level: the `awk` is fed by
      `find openspec/specs -name spec.md`, so the 7 nested specs (`auto/*`, `share/*`) are read too. The
      requirement's sentence, the `AGENTS.md` bullet and `design.md` say so. Verification:
      `evidence/nested-spec-coverage.txt` — `ls openspec/specs/*/spec.md | wc -l` = 169 against
      `find openspec/specs -name spec.md | wc -l` = 176, and the 7 nested paths are listed.
- [x] 6.2 Revert-check the nested case: with the placeholder under
      `openspec/specs/share/location-receiving/spec.md`'s `## Purpose`, the old flat glob prints nothing
      (the round-1 defect) while the shipped step names `openspec/specs/share/location-receiving/spec.md:5`
      and exits 1; restored, the step prints `Every capability spec states its Purpose.` and exits 0.
      Verification: both halves are `evidence/revert-check-nested.txt`. The case
      `artifact-hygiene#aNestedCapabilitySpecIsReported` is scenario 3 of the requirement.
- [x] 6.3 The scenario-2 THEN is now produced by the shipped step: it prints `Every capability spec states
      its Purpose.` before `OpenSpec artifacts are consistent.`, instead of that sentence existing only in
      the unshipped probe. Verification: `evidence/hygiene-step.txt` runs the workflow's own `run: |` block
      through `evidence/hygiene-step.sh` and shows both lines with exit 0.
- [x] 6.4 Remove the four claims the review marked UNPROVEN, or give each an artifact: the 170 warnings are
      counted in `evidence/openspec-validate-green.txt`; the `highlight-measurement` hand repair now cites
      commit `b3dfc78`; `design.md` no longer claims the two specs are unchanged by every archived change;
      and `design.md` "Verification" now points at `hygiene-step.txt` for the shipped check's own output.
      Verification: `grep -n 'unchanged by every change archived since'` finds nothing, and each remaining
      number appears in an evidence file.
