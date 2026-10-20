# Tasks — fix-loop-progress-reporting

Parent specification for every task below: `agent-issue-loop`, requirement "A run reports the wait it cannot
get from the child" (this change's ADDED delta). The change touches no compiled file, so the structural checks
of the files it edits stand in for a build (`guidelines/Build.md` §2, the scope block).

## 1. The case that reproduces the silence

- [x] 1.1 Add the shell case `run#childReportsWhileItWaits` to `tools/loop-cases.sh` — registered in
      `LOOP_SHELL_CASES` and in the dispatcher of `loop_run_shell_case` — and give it a stub child held on a
      fifo, so whatever the run prints it cannot have learned from the child, the case asserting
      `loop: worker-prompt started`, `loop: worker-prompt running`, `changed file(s)`, then
      `loop: worker-prompt finished in` and `exit 0` after the fifo is released. Parent spec: `agent-issue-loop`,
      scenario "The wait is announced when the child starts". **Verify**: on the pre-fix `run_child` the case
      fails — `the wait is announced when the child starts: 'loop: worker-prompt started' not found in: … pick
      #21` — recorded in `evidence/case-red-unpatched.log`.
- [x] 1.2 Record the red-on-unpatched run and the restore: copy `tools/loop/run.sh`,
      `git checkout 1fa8077~1 -- tools/loop/run.sh`, run the case (exit 1), copy the file back, unstage and run
      the case again (exit 0). **Verify**: `evidence/case-red-unpatched.log` (`FAIL run#childReportsWhileItWaits`,
      exit 1) and `evidence/case-green-after-restore.log` (`PASS`, exit 0), with `git diff` empty afterwards.
      This pair is the falsification the change owes: the new behaviour is the only difference between the two
      runs.

## 2. The fix

- [x] 2.1 Print the wait from `run_child` in `tools/loop/run.sh`: a `started` line before the child is launched,
      a `finished` line after it is reaped carrying the exit status and the log size, and a watchdog subshell
      that prints a `running` line every `LOOP_PROGRESS_SECONDS` (default 30, an empty, non-numeric or zero value
      falling back to it) with the elapsed time, the report file's size, the worktree's changed-file count and
      its commits since `$base`, every number read from the file system and git. Parent spec: `agent-issue-loop`,
      scenarios "A running child is reported on a cadence, with numbers read from the tree" and "The child's end
      is reported with its own exit status". **Verify**: `bash -n tools/loop/run.sh` clean, and case
      `run#childReportsWhileItWaits` green (`evidence/case-green.log`) — its `running` line is printed while the
      child is blocked on the fifo.
- [x] 2.2 Add `fmt_elapsed` (`37s`, `4m12s`) so the elapsed time is readable in both lines and no arithmetic is
      repeated at the call sites. Parent spec: `agent-issue-loop`, scenario "A running child is reported on a
      cadence, with numbers read from the tree". **Verify**: its two shapes appear in the case output
      (`evidence/case-green.log` exercises the seconds branch; the review and gate phases of the suite exercise
      the minutes branch).
- [x] 2.3 Keep the watchdog from outliving its child: a stop file next to the report file, `: > "$stop"` before
      `wait "$hb"`, and the stop file removed afterwards, so no `running` line can appear in the next phase's
      output. Parent spec: `agent-issue-loop`, scenario "The child's end is reported with its own exit status".
      **Verify**: in `evidence/case-green.log` the `finished` line is the last line of the case's console.

## 3. The evidence that eliminates the alternatives

- [x] 3.1 Write and run `evidence/probe-print-mode-bytes.sh` — a stand-in child that holds its 16-byte answer
      until exit, sampled every second on a redirected file and on a pipe. Parent spec: `agent-issue-loop`,
      requirement "A run reports the wait it cannot get from the child", first clause ("from what the run can
      observe itself"). **Verify**: `evidence/probe-output.txt` shows `0 B` at `t=1s…4s` and `16 B` at exit on
      both, which is what eliminates streaming the child's progress and tailing its report file (`design.md`).

## 4. The operating document

- [x] 4.1 Add the section "What a run prints while it works" to `.pi/skills/fix-loop/SKILL.md`: the three lines
      with an example, that `LOOP_PROGRESS_SECONDS` overrides the cadence, and which number is expected to stay
      at 0 until the child exits. Parent spec: `agent-issue-loop`, scenario "A running child is reported on a
      cadence, with numbers read from the tree". **Verify** (structural, no build):
      `sed -n '/^## What a run prints while it works/,/^## Report/p' .pi/skills/fix-loop/SKILL.md` names all three
      lines and `LOOP_PROGRESS_SECONDS`.
- [x] 4.2 Guideline update: none owed. `guidelines/Build.md` §2 owns the iteration protocol the loop runs and does
      not describe the loop's console output, and `AGENTS.md` "Backlog maintenance" names the loop's entry point
      only. **Verify** (structural): `git status --short guidelines/ AGENTS.md` is empty.

## 5. The whole suite

- [x] 5.1 Run the loop's whole self-test offline — `bash tools/loop-selftest.sh` — after the fix, because the
      edited `run_child` is on every phase's path (`run#commitsWithoutAnAmbientIdentity`,
      `run#childStartsInTheProjectRoot`, `run#leftoverBranchIsRecordedNotDropped` and every other case that starts
      a child). Parent spec: `agent-issue-loop`, all three scenarios. **Verify**: `evidence/full-suite.log` —
      final line `loop-selftest: all 86 cases passed (JDK 27, offline, no token, no sandbox)` — no network, no
      token, no device. A first attempt was killed by the host, not by the script: one case died with `Getötet`
      (`run#mayCloseNoLeavesTheIssueOpen`, 0.9 GB of RAM free on the machine,
      `evidence/full-suite-first-run-killed.log`), it passed in isolation, and the clean re-run above passed all
      86.
- [x] 5.2 Confirm the change itself needs no build: no Kotlin, Java, C++ or resource file is touched.
      **Verify** (structural): `git show --stat 1fa8077` lists exactly three files —
      `.pi/skills/fix-loop/SKILL.md`, `tools/loop-cases.sh`, `tools/loop/run.sh`
      (`evidence/commit-1fa8077-stat.txt`) — and this record adds only `openspec/**`.

## 6. This record

- [x] 6.1 Write the delta in `specs/agent-issue-loop/spec.md`, one ADDED requirement and three scenarios, each
      naming `run#childReportsWhileItWaits`. **Verify**: `openspec validate fix-loop-progress-reporting --strict`
      reports the change valid with no warning.
- [x] 6.2 Run `openspec doctor` and confirm the artifact rules (`- [x] N.M` markers, quoted guidance in
      `openspec/config.yaml`). **Verify**: doctor exits 0 with the root ok and no reference error.
