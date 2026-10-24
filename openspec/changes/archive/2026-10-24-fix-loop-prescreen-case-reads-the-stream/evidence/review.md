# The independent review — a fresh, read-only process that did not write the change, 2026-10-24
#
# Run: a `delegate` child of this session, told to follow tools/loop/prompts/reviewer.md and nothing else —
# read-only: no file written, no git command, no gate, no suite, no network, no tracker. It read the diff
# (`git diff c4c727fb -- tools/loop-cases.sh`), the change artifacts, the shipped requirement and this evidence
# directory. Verdict: LOOP-BLOCKING 0, VERDICT PASS; the eleven findings it raised are all NOTES, and the
# change answers them where they were subtractive (the proposal, the design, the case comment and this index)
# or ships the missing artefact (this file, structural-checks.log, green-repeat.log).
#
# Its own caveats, kept: the red reading it saw is one invocation and the delay of that invocation is not
# pinned to one mechanism; the added scenario's 30 s bound has no assertion of its own; and
# `gate#theSuitesOutputReachesTheConsoleWhileItRuns` reads at a fixed instant of the same shape while staying
# green (named as out of scope in the proposal, and filed as item #296).

## 1. Scenario coverage
| delta scenario | verdict | asserting case · assertion · timestamp |
|---|---|---|
| The header names the next step and the live view | **PASS** | `run#headerNamesTheLiveView` — `PASS run#headerNamesTheLiveView` at `gate.log:371`; body asserts `".pi/issue-loop"`, `"pre-screen"`, `"not instant"` (`tools/loop-cases.sh:3245-3247`); gate.log mtime 11:49:15 |
| The pre-screen is announced and reported before it returns | **PASS** | `run#prescreenAnnouncesItselfBeforeItReturns` — `PASS …` in `green-after.log` (mtime 11:25:32) and `gate.log`; asserts the phase line, `started`, `assert_matches '^loop: pre-screen · [0-9]+(m[0-9]{2})?s · started — .*prescreen\.log$'`, first record present, second absent, both records kept, exit `7` |
| **A phase line is read from the stream, not at a fixed instant** (added) | **PASS**, with NOTE | the same case: the wait body is lines `3290-3296` (`for _ in $(seq 600)`, `sleep 0.05`, break on `*'pre-screen: first record'*`), and the *no-softened-guard* half is exercised twice: `mutation-34.1.log` (`RED 34.1 run#prescreenAnnouncesItselfBeforeItReturns`, mtime 11:25:32) and `replay-probe.log` (same mtime) quoting `and the line it has not printed yet does not: the text is streamed, not replayed: 'pre-screen: second record' should not appear in: …` with both records in the one reading |
| Every boundary outside a child prints one line in one format | **PASS** | `run#everyPhaseBoundaryIsPrinted` — `gate.log:373` |
NOTE: the added scenario's first half ("it waits, within a bound") is exercised only *structurally* — the wait is the case's own body — plus the red→green pair on the same case (`red-before.log` FAIL → `green-after.log` PASS). No log records the elapsed at which the line arrived in the green run, so "the wait, not luck, made it green" is inferred from the pair rather than measured; and no case asserts the **30 s bound** or the path the design invents for a run that dies before the pre-screen.
NOTE: the new scenario's WHEN is general ("a case reads the console of a run that is still working") while the tree keeps one case of exactly that shape at a fixed instant — `case_gate_theSuitesOutputReachesTheConsoleWhileItRuns`, `tools/loop-cases.sh:2711` still `sleep 1.5`. The proposal names it and says it is out of scope because it is not red; the scenario's THEN ("a line of the phase it guards") does scope it to phase lines, so the wording holds — but the sibling case will flake by the identical mechanism and nothing here records that as owed work.
## 2. Cause or gap scope
The diff implements the named root cause and nothing else — the case's reading and its comment:
```
-  sleep 1.5
-  out=$(cat "$log")
+  out=""
+  for _ in $(seq 600); do
+    out=$(cat "$log")
+    case "$out" in *'pre-screen: first record'*) break ;; esac
+    sleep 0.05
+  done
```
No engine file, no `tools/loop/run.sh`, no mutation record, no profile, no `guidelines/**`. Production invariant untouched: `tools/loop/run.sh:464` still reads `bash "$LOOP_DIR/prescreen.sh" … 2>&1 | tee "$prescreen_log"`.
**The item's crux — "the case was the defect, not the engine" — is supported.** Three independent pieces: (a) `timing.log` measures the phase line at `628 ms` / `741 ms` / `760 ms` (header `101 ms`) on `ea194c38`, `e12cf971`, `c4c727fb` — and a *capture-and-replay* engine prints after the stand-in's 3 s hold, so a line already present at 0.63 s is proof the engine streams; (b) `red-before.log`'s console holds the header and `run: the pre-screen runs next and is not instant …` but no phase line, i.e. the read happened before the call was reached; (c) `replay-probe.log` shows what the opposite defect looks like — and it is a different message (`the text is streamed, not replayed`), so the red was not that shape. The fix is the right one under either cost that delays the line, which is why the residual imprecision in item 6 costs no round.
## 3. Gate reality
`gate.log` (mtime 11:49:15, after the case's 11:21:57):
- `loop-selftest: selected 208 of 208 cases (files: tools/loop-cases.sh)` — the delta touches the suite's core file, so the whole set is the right requirement (Build.md §2 scope block: nothing compiled, no Gradle suite owed; `proposal.md` "Impact" states it).
- `208` `PASS` lines, `0` `FAIL`, `0` occurrences of `from-cache`/`up-to-date`.
- `loop-selftest: all 208 cases passed (JDK 27, offline, no token, no sandbox) — selection: --from-git c4c727fb`
- `LOOP-GATE: tests=208 failures=0 errors=0 (tools/loop-selftest.sh --from-git c4c727fb 1332s)`
- Both of the item's reds green here: `PASS run#prescreenAnnouncesItselfBeforeItReturns` and `PASS run#aWriterTheRunCanPlaceBlocksTheItem`.
Quoted, not reproduced. Coverage verdict: correct requirement, real execution, 1332 s of it.
## 4. Tasks claiming done
NOTES only:
- NOTE: `4.4` is `[x]` ("Archive the change and confirm the scenario merged into `openspec/specs/agent-issue-loop/spec.md`") but the change is still at `openspec/changes/fix-loop-prescreen-case-reads-the-stream/` and the shipped requirement (`openspec/specs/agent-issue-loop/spec.md:1212`) carries its three original scenarios and not the added one. The tick is premature by the run's own order (archive after PASS); it is recorded here because the reviewer cannot confirm the merge, and item 6's "carried by the archived text too" is unanswerable for that reason.
- NOTE: `4.2`'s `Verify: evidence/review.md` does not resolve — `evidence.md` indexes `review.md` and the file is absent from the directory. This report is that file's content; the pointer is an ordering artefact.
- NOTE: `4.1`'s "and the mutation `--check` pass" is not in `gate.log`: the log carries the *cases* that assert the obligation (`PASS gate#anEnginePathOwesTheRecordCheck`, `PASS mutation#everyDeclaredRecordApplies`), and the `LOOP-GATE:` line names only the suite. The mutation harness did run — `mutation-34.1.log`, not the gate.
- NOTE: `2.1`'s `bash -n` parse, `3.1`'s `openspec validate … --strict` and `3.2`'s `tools/check-doc-routes.sh` are ticked with no log in `evidence/`, and `evidence.md` does not index one.
- `1.1`–`1.3`, `2.2`–`2.4` are backed by the files they name.
## 5. Revert-check
Mutation record at `tools/loop-mutations.sh:228`:
```
'34.1@-@tools/loop/run.sh@run#prescreenAnnouncesItselfBeforeItReturns@the stream that reaches the terminal while the pre-screen runs (issue #191)@s#\| tee "\$prescreen_log"#\| cat > "\$prescreen_log"#'
```
Targets the production invariant (the stream to the terminal), not the case. `mutation-34.1.log`, mtime 11:25:32:
```
RED    34.1  run#prescreenAnnouncesItselfBeforeItReturns   (the stream that reaches the terminal while the pre-screen runs (issue #191))
restore 34.1 run#prescreenAnnouncesItselfBeforeItReturns green again
red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0
```
Restore is clean in this tree: `tools/loop/run.sh:464` carries `| tee "$prescreen_log"`, no mutation marker anywhere in the diff or the file. (I ran no `git` — the contract forbids version control here — so "the production file's diff is empty" is evidenced by the file's own intact line, not by `git diff`.)
NOTE: `mutation-34.1.log` quotes no assertion message, so the *right reason* for 34.1 is inferred from the substitution: the console never receives `pre-screen: first record`, the 30 s wait expires, and `the pre-screen's own first line reaches the console while the call runs` fails. The replay shape's right reason *is* quoted verbatim (item 1). The absence is the harness's output shape, not an omission the change could easily fix.
## 6. Evidence pointers
Carried and correct: `4.140 s` ↔ `compile-cost.log` `… 4,140 total`; `0.628/0.741/0.760 s` and `header 101 ms` ↔ `timing.log`; `208`, `failures=0`, `1332 s` ↔ `gate.log`; the replay message ↔ `replay-probe.log`; the `RED 34.1` line ↔ `mutation-34.1.log`; `1 of 1 cases failed` ↔ `red-before.log`.
NOTES:
- NOTE: `design.md` writes "measured 0.63 s on a quiet host and 1.3 s with a suite running beside it" — `timing.log`'s own preamble says the opposite: "while a gate suite of the neighbouring session ran beside it — so these are readings from a **loaded** host, not from a quiet one." Only the label is wrong; the three numbers are quoted exactly.
- NOTE: `proposal.md`'s "on a loaded host … the phase line landed at 1.03 s and 1.30 s" has no artefact behind it, and `evidence.md` indexes no file that holds it.
- NOTE: `proposal.md`'s "Running the case alone therefore read an **empty** console, which is the invocation the item's own evidence was taken with" describes an invocation the change does not ship: `red-before.log`'s console is header-bearing (`agent-issue-loop: items=1 …` plus the `run: the pre-screen runs next …` line), not empty. The two mechanisms (cold `javac` 4.140 s vs. a loaded preamble) cover two different invocations, and neither log pins the delay of the run that actually went red.
- NOTE: `evidence/green-repeat.log` (mtime 11:51:09, three `PASS` runs stamped `2026-10-24T09:50:42Z`, `09:50:54Z`, `09:51:09Z`) is in the evidence directory and is **not** in `evidence.md`'s table — an unindexed artefact, and the only file here that carries an in-file timestamp.
- NOTE: `evidence.md`'s table indexes `review.md`, which does not exist yet (see item 4).
- NOTE: `red-before.log` and `green-after.log` carry no command line, no revision and no in-file time, so "red on the base `c4c727fb`" rests on the mtime window and on the tree, not on the log. A copy that exists only as a 49-byte `PASS` line is thin.
## 7. Spec and guideline honesty
MODIFIED is the right kind: the delta adds one scenario and repeats the shipped requirement's body and its other three scenarios verbatim (checked against `openspec/specs/agent-issue-loop/spec.md:1212-1263` — header view, pre-screen, every-boundary all identical; nothing renamed, nothing dropped, so the strict validator's omission rule is met). The added scenario states a **contract about how a case reads** ("it waits, within a bound, for the line the phase itself prints"), not an implementation, and its second AND states the limit on that licence — that is the right shape for the requirement whose `**Case**:` line owns the phenomenon. Body unchanged, so the 500-character rule applies to nothing new.
NOTE: `design.md`'s justification for editing no guideline is a keyword grep ("`grep -n 'phase line' guidelines/*.md` prints nothing"). The nearest owning rule is `guidelines/Build.md` §6e "Wall-clock waits and the conversion measured" — "a test that must wait instead drives the component's seam … and awaits observable state". The change *complies* with §6e's principle while using a poll rather than a seam, and §6e's mechanism (`checkNoWallClockWaits`) is wired only into Gradle test-bearing modules, so no edit is owed — but the design should say that, not that no guideline states it.
## 8. New overreach
- UNPROVEN: "on a loaded host (a gate suite running beside it) the phase line landed at 1.03 s and 1.30 s, against the 1.5 s sample; the margin is under half a second, and the red the item reports under the suite's own load held the header only" (`proposal.md`) — the numbers have no artefact.
- UNPROVEN: "Running the case alone therefore read an **empty** console, which is the invocation the item's own evidence was taken with" (`proposal.md`) — contradicted by the console `red-before.log` shows.
- UNPROVEN: "`run#aWriterTheRunCanPlaceBlocksTheItem` … measured: red on `e12cf971`, green on `c4c727fb`" — `evidence.md` states both readings were not shipped; the green half is backed by this gate (`PASS run#aWriterTheRunCanPlaceBlocksTheItem`), the red half is not.
- UNPROVEN: "a run that refuses before the pre-screen still ends well inside it — and then fails on the phase assertion, with the console the run did print in the message" (`design.md`) — no log of that path.
- UNPROVEN: "the state write and the decision count are a JVM each" (`tools/loop-cases.sh`, comment above the case; `proposal.md`) — no measurement; only the `javac` half of the window is timed.
- UNPROVEN: "it is not red (the gate's own start-up is git and `jq`, with no JVM in front of it)" (`proposal.md`) — the not-red half is in `gate.log:293`; the *reason* has no measurement.
- UNPROVEN: "The mutation record `34.1` is what keeps that second half honest" (the case's new comment) — 34.1 keeps the **silent** half honest; the replay ("second") half is kept by a hand probe that is not a record in `tools/loop-mutations.sh` and so is not re-run by the gate. `design.md`'s table states this correctly; the comment does not.
## Verdict
No finding here contradicts the change's contract, leaves a scenario unexercised by the case that names it, or makes the gate or the red evidence missing or false — every blocking-grade claim (engine prints, case read too early, gate green for this tree, guard not softened) is backed by a log, and the two evidence imprecisions (the quiet/loaded mislabel, the unshipped 1.03/1.30 s) are strictly subtractive corrections of surrounding prose. Everything raised is a NOTE.
```
LOOP-BLOCKING: 0
VERDICT: PASS
```
