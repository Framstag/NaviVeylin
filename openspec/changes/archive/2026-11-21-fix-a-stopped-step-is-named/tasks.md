# Tasks

## 1. The case that reads the silence (spec `agent-issue-loop`, "A step that is stopped is named while it is stopped")

- [x] 1.1 `tools/loop/tests/loop-cases.sh`: the case `gate#aStoppedStepIsNamedBeforeItsBound` — a real `gate.sh` in
      a throwaway repository whose one suite stops itself (`kill -STOP $$`) while the subshell the gate started
      around it stays in `wait`, a log read for the line while the gate is still inside the step (six seconds into a
      `LOOP_GATE_STEP_MAX_SECONDS=10` bound, so a line printed before the bound is what is read), then the gate waited
      for so the bound ends the step's group and nothing stopped is left behind. Declared in `LOOP_SHELL_CASES` and
      in the dispatch row, with `# files: tools/loop/gate.sh tools/loop/loop.conf tools/pi-child-events.sh
      tools/tooling.json`.
      — red before the fix: `FAIL gate#aStoppedStepIsNamedBeforeItsBound` / `the gate names a step that is stopped
      while it is stopped, not at its bound: … the step overran its bound of 10s and was ended …`
      (`red-on-HEAD.log`, the run taken before the fix, with the suite still stopping its whole group before the case
      was narrowed), tally `tests=1 failures=1 errors=0` (`red-on-HEAD.xml`).
- [x] 1.2 `tools/loop/gate.sh:707-751`: the sample (`ps -A -o pgid=,stat=` once a second for the whole wave at
      `:712-716`, `T`/`t`/`Z` kept by `awk`), the `st_stopped` slot fact (`:653`, `:667`), and the one arm
      (`:742-751`) that prints the line — with the rule above it: the incident, why the bound stays the only
      authority, and why the sample is not per look (issue #259) and not per slot.
      — green after it: `PASS gate#aStoppedStepIsNamedBeforeItsBound` (`green-case.log`).
- [x] 1.3 `tools/loop/tests/loop-mutations.sh`: record `948.1` disarms the arm (`case "$stopped_groups" in` →
      `case "" in`); the case reads the silence again.
      — `--only '948.1'` → `RED 948.1`, restore `green again`, `red-when-mutated: 1 · not-noticed: 0 ·
      restore-failures: 0` (`mutation-948-1.log`, red at 2026-11-21T02:07:40Z); `--check --files 'tools/loop/gate.sh
      tools/loop/tests/loop-mutations.sh'` → `all 24 cases passed — every selected record applies to the file it
      names` (`mutations-check.log`).
- [x] 1.4 `openspec/changes/fix-a-stopped-step-is-named/specs/agent-issue-loop/spec.md`: the ADDED requirement and
      its one scenario, naming the case. — `openspec validate fix-a-stopped-step-is-named --strict` →
      `Change 'fix-a-stopped-step-is-named' is valid`.
- [x] 1.5 `guidelines/Backlog.recipes.md:963` (section 7b): one paragraph beside the bound's own at `:950` — the
      incident, the line's shape, and the reason the bound is not moved.
      — read back with `tools/look.sh 'A step that is stopped rather than running is named' --path guidelines`
      → `1 match in 1 file` (`structural-checks.log`); the line's own text wraps across two lines of the
      paragraph, so the phrase `the step is stopped, not running` is not the pattern that reads it back.

## 2. Verification

- [x] 2.1 The new case, before and after the fix (1.1, 1.2).
- [x] 2.2 Every gate case, because the poll is the arm each of them runs through: `--group gate` →
      `all 43 cases passed` (`gate-group.log`).
- [x] 2.3 The driver cases the new case reaches — its `# files:` declaration and the dispatcher's own row:
      `--group driver` → `all 38 cases passed`, `driver#everyCaseDeclaresTheFilesItCanReach` among them
      (`driver-cases.log`).
- [x] 2.4 The structural checks, since no compiled file is touched (`guidelines/Build.md` §2, the scope block):
      `bash -n` over the three edited shell files, `openspec validate … --strict`, `tools/check-doc-routes.sh`,
      `tools/check-doc-values.sh` — each exits `0` (`structural-checks.log`). No Gradle build and no Android suite
      is owed, and none was run.
- [x] 2.5 The full self-test of `tools/loop/**` is the gate's own selection and is not run here: this item's gate is
      a fresh child's (`LOOP-DIFF-READY`), and the change is left unarchived for it.
