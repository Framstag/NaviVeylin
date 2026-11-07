# The corrections, applied — 2026-11-06T08:45Z

The review of round 1 returned one `BLOCKING` finding and four `NOTE`s. Every one of them is a deletion or a
rewording; nothing in the gate, the profile, the reach table or the red case's own mechanism changed.

## The blocking finding: a project value in an engine template

`guidelines/Backlog.md` §4 I13 and I15 — a project fact exists in one file, and a phase names no skill, command or path
of its own. Round 1 wrote this project's case file, case name and command into `tools/loop/prompts/worker.md`, an engine
template, and the case and the requirement cemented it. Three deletions:

1. `tools/loop/prompts/worker.md:215` — the paragraph now sends the child to the plan's first line
   (`{{GATE_CMD}} --plan --base {{BASE}}`) and names no case, no case file and no suite command of this project. It
   still says what makes the step owed (a diff that writes a case, or narrows the first-line `# files:` declaration)
   and why the child and not the gate is what saves the attempt (issue #792).
2. `gate#theDeclarationCaseIsOwedFirst` — the assertion on the prompt's case name is deleted; the negative assertion
   that replaces it is the portability rule itself: the prompt SHALL name no case of this project, because a foreign
   project does not have that case. The pointer to the plan's first line is asserted positively, so the reworded
   requirement still has its case.
3. The requirement's child-step clause and the scenario's `AND` line now say the child's step sends the child to the
   plan's first line and names no project's case, instead of ordering it to run this project's case.

The archived delta and `openspec/specs/agent-issue-loop/spec.md` are byte-identical (`diff` of the two blocks, empty).

## The notes

- Requirement body: 489 characters, inside the 500-character `ADDED` limit `AGENTS.md` records (was 816).
- `proposal.md` — the `grep -c '^#### Scenario'` count is **323**, which is what the archived tree prints (the delta's
  own scenario is the 323rd), not 322; the "the engine still names no project fact" sentence is now true of the
  scripts *and* of the template.
- `design.md` — the case's own seconds are cited from the tally that carries them (`time="2"` in
  `evidence/green-on-change.xml`), not from a log that has no per-case line; the prompt hunk is **eight** lines, not
  four; and the gate's aggregate `tests=` sum does not grow, because the step's tally is
  `tests="0" failures="0" time="10"` (the case reports a `PASS` line, not a suite child) — only the tally directory
  gains that file.
- `tasks.md` — task 1.2's `2026-11-06T08:11:59Z`, which no log carried, is deleted; tasks 1.1 and 2.3 name the corrected
  assertions.

## What was re-measured after the correction

- `bash tools/loop-selftest.sh --case 'gate#theDeclarationCaseIsOwedFirst'` on the base `560edf23`, with the corrected
  case copied into a copy of that tree: `1 of 1 cases failed`, `2026-11-06T08:45:09Z` (`evidence/red-on-HEAD.log`,
  `evidence/red-on-HEAD.xml`) — the same plan, the build first and the declaration case not at all.
- The same command on the change: `PASS`, status 0, `2026-11-06T08:45:18Z`–`08:45:20Z` (`evidence/green-on-change.log`,
  `evidence/green-on-change.xml`).
- `--case 'gate#*'` — all 24 cases passed; `driver#everyCaseDeclaresTheFilesItCanReach` — `PASS`;
  `tools/check-portability.sh` — `OK — 56 engines and 7 templates name no project value (116 value(s) read from the
  project)`; `bash tools/loop-mutations.sh --check` — all 246 records apply. `2026-11-06T08:45:18Z`–`08:45:48Z`
  (`evidence/focused-cases.log`).
- Mutation `84.1` targets `tools/loop/gate.sh`, which this correction does not touch, so its `RED`/restore record
  (`evidence/mutation-84.1.log`, `2026-11-06T08:12:37Z`–`08:12:39Z`) stands as it was measured.
