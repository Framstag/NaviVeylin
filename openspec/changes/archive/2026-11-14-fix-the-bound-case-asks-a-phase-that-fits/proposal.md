# A bounded answer's case asks a phase whose state leaves room for the head it asserts

## Why

Issue **#915**. `phase#aResultOverTheBoundIsCutAndNamed` (`tools/loop/tests/loop-cases.sh:17617`) fails on this tree's
base `a1404a6c` with `'PKG-LINE-001' not found in … [PHASE-BOUND: cut at 4000 characters — the phase state is printed
first, so a cut takes the work package's tail; …]`, and the number it is cut at is the project's own:
`LOOP_TOOL_RESULT_MAX_CHARS=4000` at `tools/loop/loop.conf:450` (`tools/look.sh 'LOOP_TOOL_RESULT_MAX_CHARS' --path
tools/loop/loop.conf` → 1 match, `:450`). Every engine gate therefore re-pays a red it cannot attribute to any delta.

The arithmetic is the case's own, taken from the failing answer (`evidence/window.txt` carries the commands):

- the phase A checklist is the prompt's own section, 2 630 bytes — the same at `419d482c` and at HEAD
  (`git show <rev>:tools/loop/prompts/worker.md | awk …`), so no prompt growth accounts for the red;
- the notice the cut prints is 262 bytes, so the window the bound leaves is `4000 − 262 − 2 = 3736` bytes;
- the body's head reaches the package's opening delimiter at 3 696 bytes, so the window ends 4 bytes *inside*
  `<<<ISSUE_TEXT`; the delimiter's line plus `PKG-LINE-001` need 27 bytes more than the window has;
- the state printed before the package — the phase line, the item line, the checklist's path and section, the tree,
  the gate, the two `PATHS:` lines and the package line — is what spends the rest.

**Root cause, in one sentence.** `tools/loop/phase.sh` prints the phase's checklist before the work package, and this
case asks the package's head of phase A, whose checklist alone is 2 630 of the 4 000 characters the project records —
so the kept window ends inside the package's delimiter and the line the case asserts could not be there whatever the
fixture's package said.

**The red case** is the case itself, unchanged on that base (`evidence/red-on-HEAD.log`, `evidence/red-on-HEAD.xml`,
tests=1 failures=1, 2026-11-14T18:23:56Z), and it is green once the fixture asks a phase whose state leaves room for
the head (`evidence/red-case-green.log`, `evidence/red-case-green.xml`, tests=1 failures=0, 2026-11-14T18:25:03Z).

**The owning capability** is `agent-issue-loop`; the rule it states is added to
`openspec/specs/agent-issue-loop/spec.md` as "A bounded phase answer keeps the state and the package's head". The
owning guideline section is `guidelines/Backlog.recipes.md`, its section "The gate and its suite" (the heading the document
carries for the loop's suite and the gate), whose paragraph this change
adds — the loop's suite is where a case about a bound belongs, and no section yet stated how a case must choose the
fixture it asserts a bound with.

**Surface**, counted before the plan was fixed, one line per file: `tools/look.sh 'PHASE-BOUND'` → 4 matches in 2 files
(`tools/loop/phase.sh` 1, `tools/loop/tests/loop-cases.sh` 3) and `tools/look.sh 'PKG-LINE-001' --path tools` → 1 match
in 1 file (`tools/loop/tests/loop-cases.sh`).

surface: 0 cases added, 1 rewritten, 0 fixtures realigned — `tools/look.sh 'PHASE-BOUND'` (4 matches in 2 files) and `tools/look.sh 'PKG-LINE-001' --path tools` (1 match in 1 file)

No line of `tools/loop/phase.sh` changes. `tools/look.sh 'phase' --path tools/loop/tests/loop-mutations.sh` → 0
matches: no mutation record covers this case, so none is realigned and none is added — the case's own fixture is the
mutation.

**What this change does not do.** A phase A answer at this bound keeps no package line at all, so a child that asks
`phase.sh A` still has to read the package itself — the crowding the case had been red about. Answering it means
choosing which part of a cut answer gives way, the checklist or the package's head, and that is a trade-off this item
does not own (see `design.md`, "Alternatives the evidence eliminated").
