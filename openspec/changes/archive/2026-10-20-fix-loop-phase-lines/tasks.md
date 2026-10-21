# Tasks — fix-loop-phase-lines

Parent specification for every task below: `agent-issue-loop`, requirement "The run names its phases and the
live view into it" (this change's ADDED delta). The change touches no compiled file, so the structural checks of
the files it edits stand in for a build (`guidelines/Build.md` §2, the scope block) — the gate that owns
`tools/loop/**` is `tools/loop-selftest.sh`, run offline.

## 1. The cases that reproduce the silence

- [x] 1.1 Add `run#headerNamesTheLiveView` to `tools/loop-cases.sh` — registered in `LOOP_SHELL_CASES` and in
      the dispatcher of `loop_run_shell_case` — asserting the header line carries `state.sh show`, the state
      directory, and that the pre-screen runs next and is not instant. Parent spec: scenario "The header names
      the next step and the live view". **Verify**: red on the base —
      `evidence/red-phase-lines.log` (`the header names the command that shows the run's progress: 'state.sh
      show' not found in: agent-issue-loop: items=1 …`, exit 1).
- [x] 1.2 Add `run#prescreenAnnouncesItselfBeforeItReturns`: a copied `tools` tree whose `prescreen.sh` is a
      stand-in that prints one line, holds 3 s and prints a second, the run started against it, the console read
      1.5 s in (the first line must be there and the second must not — that is what separates streaming from
      capture-and-replay), the kept `<state dir>/prescreen.log` read after the run, and a second stand-in that
      fails with 7 so the run's own status stays the pre-screen's. Parent spec: scenario "The pre-screen is
      announced and reported before it returns". **Verify**: red on the base —
      `the phase is announced on the console: 'loop: pre-screen' not found in: … dry-run=0 …` at 1.5 s, i.e. the
      console carried the header only (`evidence/red-phase-lines.log`, exit 1), and the rewritten case red on
      the base for its own reasons (`evidence/red-phase-lines-round2.log`, exit 1).
- [x] 1.3 Add `run#everyPhaseBoundaryIsPrinted`, asserting the shape of each boundary line —
      `loop: <phase> · <elapsed> · <what>` with an elapsed field — one line per crossing (the pre-screen's two,
      the other four one each), and that no phase line reaches the state file or the report's text. Parent spec:
      scenario "Every boundary outside a child prints one line in one format". **Verify**: red on the base —
      `the pre-screen's boundary: 'loop: pre-screen · ' not found in: …` (`evidence/red-phase-lines.log`,
      exit 1), and the rewritten case red on the base at
      `the pre-screen is announced and reported: expected '2', got '0'`
      (`evidence/red-phase-lines-round2.log`, exit 1).

## 2. The helper the phases share

- [x] 2.1 Add `progress <phase> <what>` and move `fmt_elapsed` from `tools/loop/run.sh` into `tools/loop/lib.sh`,
      with `LOOP_PROGRESS_STARTED` as the run's own age. Parent spec: scenario "Every boundary outside a child
      prints one line in one format". **Verify**: `bash -n tools/loop/lib.sh tools/loop/run.sh` clean, and
      `bash -c 'source tools/loop/lib.sh; progress gate "read — 0 test-results XML"'` prints
      `loop: gate · 0s · read — 0 test-results XML` (`evidence/helper-line.txt`).

## 3. The header hint

- [x] 3.1 Print the line after the header naming the pre-screen as the next step and that it is not instant, the
      state directory and `bash tools/loop/state.sh show`. Parent spec: scenario "The header names the next step
      and the live view". **Verify**: `run#headerNamesTheLiveView` green (`evidence/phase-lines-cases.log`).

## 4. The streamed pre-screen

- [x] 4.1 Replace the command substitution and its replay with one pipeline through `tee` into
      `<state dir>/prescreen.log`, `pipefail` set inside the subshell so the pipeline's status is the
      pre-screen's own, and a `progress` line before and after it. The case then requires three things: the
      text on the console while the call runs (streamed, not replayed), the same bytes in the kept file, and the
      run's exit status to be the pre-screen's own. Parent spec: scenario "The pre-screen is announced and
      reported before it returns". **Verify**: `run#prescreenAnnouncesItselfBeforeItReturns` green
      (`evidence/phase-lines-cases.log`), and each half falsified by its own mutation — 33.1
      (`| tee "$prescreen_log"` → `| cat > "$prescreen_log"`) fails on the console's missing line, 33.2
      (`| tee /dev/null`) fails on the kept file's missing text (`evidence/mutations-round2.txt`); the exit
      status keeps its earlier falsification (`evidence/mutation-status-capture.txt`, which also records that
      the same case passes when only `pipefail` is removed, so the capture is what it guards).
- [x] 4.2 Read `stage_dir` once, before the pre-screen that writes into it, and drop the later assignment.
      **Verify**: `grep -n 'stage_dir=' tools/loop/run.sh` names one assignment, before the pre-screen block.

## 5. The five boundary lines

- [x] 5.1 Print one `progress` line at each boundary: `pre-screen` (start, and end with the exit status),
      `worktree` (ready), `item` (picked, naming the report file), `gate` (the `test-results` XML count) and
      `commit` (`end-item` returned, with HEAD and the base). Parent spec: scenario "Every boundary outside a
      child prints one line in one format". **Verify**: `run#everyPhaseBoundaryIsPrinted` green
      (`evidence/phase-lines-cases.log`), including the elapsed field and the one-line count asserted for each
      boundary, and falsified by 33.3 (the elapsed field removed from `progress` → `expected '2', got '0'`) and
      33.4 (the line written into the state file as a field → `'loop: ' should not appear in: {… "progressNote":
      "loop: gate" …}`) in `evidence/mutations-round2.txt`.

## 6. The operating document

- [x] 6.1 Extend `.pi/skills/fix-loop/SKILL.md`, section "What a run prints while it works", with the phase
      lines and the header hint beside the three child lines, and the rule that no progress line is read back.
      Parent spec: requirement "The run names its phases and the live view into it", first clause. **Verify**
      (structural, no build): `sed -n '/^## What a run prints while it works/,/^## Report/p'
      .pi/skills/fix-loop/SKILL.md` names `progress`, the header hint and `state.sh show`.

## 7. Verification — the scope block, no compiled file is touched

- [x] 7.1 `bash -n` on `tools/loop/lib.sh`, `tools/loop/run.sh`, `tools/loop-cases.sh`;
      `openspec validate agent-issue-loop --type spec --strict`; `openspec doctor`. **Verify**:
      `evidence/validation.txt`.
- [x] 7.2 The offline shell suite (`tools/loop-selftest.sh`, the gate that owns `tools/loop/**`) run **once**
      over the final tree, after every assertion this change makes existed. **Verify**: `evidence/selftest.txt` —
      **130 of 131 cases pass**, the three new ones among them, and the one red
      (`worktree#keepsByDefaultAndRemovesOnRequest`) is pre-existing and independent: the same failure and the
      same message with the base engine, and `worktree.sh` is not in this change
      (`evidence/PRE-EXISTING-RED.txt`, issue #215).
- [x] 7.3 No Gradle, no device: the delta touches no Kotlin, Java, C++, manifest or resource file, so
      `guidelines/Build.md` §2's scope block names these structural checks in place of
      `./gradlew test -PforceTests --no-build-cache`. Exclusion stated: the JVM gate is not run for a delta no
      JVM module compiles.
- [x] 7.4 Archive the change and confirm the delta merged into `openspec/specs/agent-issue-loop/spec.md`.
      **Verify**: `openspec/changes/archive/<dated name>` exists and
      `grep -n 'The run names its phases and the live view into it' openspec/specs/agent-issue-loop/spec.md`
      resolves.

## 8. What the operator sees

- [x] 8.1 The same fixture item worked by the engine at the base and by this change, so the console change is
      read rather than described (probe: the case helpers, a throwaway project, the stub `gh` and `pi`, identical
      everywhere but the engine). **Verify**: `evidence/console-before-after.txt` — before: header, two `state:`
      lines, then the pre-screen's whole output in one burst after it returns; after: the header hint, the
      pre-screen's start and finish around its own streamed text, then `worktree`, `item`, `worker-prompt`,
      `commit` and `gate`.

## 9. What the first review found, and what closed it

- [x] 9.1 The pre-screen case exercised the run's own progress line but not the pre-screen's text: a
      capture-and-replay engine would have passed it, and `<state dir>/prescreen.log` was asserted nowhere.
      **Verify**: the case now holds a stand-in pre-screen (one line, 3 s, a second line), reads the console at
      1.5 s and requires the first line and not the second, then reads the kept file and requires both
      (`evidence/phase-lines-cases.log`), red on the base for its own reasons
      (`evidence/red-phase-lines-round2.log`) and red under 33.1 and 33.2 (`evidence/mutations-round2.txt`).
- [x] 9.2 The boundary case asserted five substrings, so the elapsed field, the one-line count and the
      "never read back" clause were unexercised. **Verify**: it now asserts `loop: <phase> · <elapsed> · <what>`
      per boundary, the count per crossing, and the absence of a `loop: ` line from the state file and the
      report (`evidence/phase-lines-cases.log`), red on the base (`evidence/red-phase-lines-round2.log`) and red
      under 33.3 and 33.4 (`evidence/mutations-round2.txt`).
- [x] 9.3 The review's measurement notes: the requirement's `53 s` moved out of the scenario's WHEN into this
      change's prose, the `40–65 min` figures now point at
      `.pi/skills/fix-loop/REFERENCE.md` "Measured costs" rather than `state.sh`, and the stale draft directory
      `openspec/changes/fix-loop-phase-progress/` and the four superseded transcripts in the item's evidence
      directory were removed. **Verify**: `openspec validate agent-issue-loop --strict` no longer reports the
      500-character requirement warning (`evidence/validation.txt`), the two pointers resolve
      (`grep -n 'Measured costs' .pi/skills/fix-loop/REFERENCE.md`), and neither draft path exists.
