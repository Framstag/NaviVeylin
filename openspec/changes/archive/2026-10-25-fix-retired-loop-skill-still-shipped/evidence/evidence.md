# Evidence — fix-retired-loop-skill-still-shipped

Base measured against: `d6deb7e4372c44d127b5d177d58ff22c7cc33531` (the run's base).

## Phase A — the condition, the surface, the red case

- The skill exists on the base and nothing loads it: `git ls-tree -r --name-only d6deb7e4 .pi/skills/fix-loop`
  lists three files; `LOOP_WORKER_SKILLS` (`tools/loop/loop.conf` at the base) is
  `openspec-propose openspec-apply-change build-app run-tests revert-check`, and the child's prompt is rendered
  from `tools/loop/prompts/*.md` (`tools/loop/lib.sh`, `loop_render`). Full log: `surface-head.log`.
- Surface on the base: **12 files name `fix-loop`** outside the archived changes — 3 of them the skill itself, 9
  outside it — plus 2 consequence sites (`tools/check-portability.sh`'s value set, `tools/loop/gate.sh`'s
  resource-tree comment) and 1 new case. Command and full list: `surface-head.log`.
- Red test on the base, `tools/loop/worktree.sh` restored from `d6deb7e4` and `tools/loop/evidence-check.sh` moved
  aside (`red-on-head.log`):

```
$ bash tools/loop-selftest.sh --case worktree#seedsAllFourInputs
  the engine still seeds a retired, versioned skill into the worktree
FAIL worktree#seedsAllFourInputs
loop-selftest: 1 of 1 cases failed
exit=1

$ bash tools/loop-selftest.sh --case evidence#scansWhatTheProfileNames
  a clean tree whose configuration names its own scan root passes: bash: …/tools/loop/evidence-check.sh: Datei oder Verzeichnis nicht gefunden: expected '0', got '127'
FAIL evidence#scansWhatTheProfileNames
loop-selftest: 1 of 1 cases failed
exit=1
```

- Green after the change: `green-cases.log`
  (`PASS worktree#seedsAllFourInputs`, `PASS evidence#scansWhatTheProfileNames`).
- The document half: `bash tools/check-doc-routes.sh` reports a document that names a skill the tree does not carry,
  so the `AGENTS.md` row and the skill had to move in one change rather than one after the other.

## Phase D — the gate

`./tools/loop/gate.sh --base d6deb7e4…` — the project's gate, over the change's own pathspec. `--base` is the run's
base commit, not the profile's default `merge-base HEAD origin/main` (`d846c74`): that range also holds the paths
the run's earlier items left in the branch — of which the carried `evidence/measure.log` measures this run's own
`LOOP-MEASURE base=d6deb7e4372c44d127b5d177d58ff22c7cc33531 paths=17 insertions=117 deletions=415` — and their
compiled files would owe a Gradle run this delta does not touch. The pathspec rule the base exists for is
"verification follows the changed files" (`guidelines/Build.md` §2). (An earlier revision of this artifact defended
the same choice with "the 192 paths" of a `measure.sh` run against `merge-base HEAD origin/main`; that run is not
carried here, and the reason it argued for stands on the line above rather than on its number.)

`gate.log`, one line from it (the aggregate; no single tally carries the sum):

- The gate's summary line is the *last* line of `evidence/gate.log` itself, verbatim with its suite list: it names
  every suite that ran with its own wall clock, and the sum of their cases. No single tally carries that sum, so
  what this artifact quotes below is each suite's own tally.
- Six tallies, every one with zero failed cases:
  - `tools_loop-selftest.sh_--from-git_d6deb7e4372c44d127b5d177d58ff22c7cc33531.xml` — `tests="232" failures="0"`,
    `time="1390"` (the loop owns `tools/loop/**`, so the full suite is owed: 232 of 232 cases, no file-set
    narrowing)
  - `bash_tools_loop-mutations.sh_--check.xml` — `tests="170" failures="0"`
  - `bash_tools_loop_evidence-check.sh_--selftest.xml` — `tests="0" failures="0"` (the gate's tally writer reads
    the suite's own summary line, and this suite prints `ok …` lines and `evidence-selftest: PASS` instead; the `6`
    an earlier revision of this artifact quoted is the neighbouring `tools_check-openspec-hygiene-selftest.sh` tally's)
  - `tools_issue-tooling-selftest.sh.xml` — `tests="121" failures="0"`
  - `tools_check-portability-selftest.sh.xml` — `tests="29" failures="0"`
  - `tools_check-portability.sh.xml`, `tools_check-doc-routes*.xml`, `tools_check-openspec-hygiene*.xml`,
    `tools_mutate*.xml` — the structural checks, each `failures="0"`
- No Gradle suite: no compiled path in the pathspec (`LOOP_GATE_COMPILED_PATTERNS` matched nothing), which is the
  scope rule for a delta that touches no compiled file.

## Phase F — pointers and documents

`bash tools/loop/evidence-check.sh
openspec/changes/archive/2026-10-25-fix-retired-loop-skill-still-shipped --root .` — the round-2 run, whose log is
`evidence/evidence-check.log` (the round-1 run named the same change before it was archived and passed too):

```
ok   C1 §references: 1 checked, 0 exempted as archived pointers
ok   C2 test tallies: 5 pairs confirmed, 0 exempted (not retained), 0 unparsable, 0 drift
ok   C3 /tmp pointers: none cited
ok   C4 mutations: no REVERT-CHECK MUTATION marker under 1 configured path(s): app/src
ok   C5 file:line: 357 resolved and in range, 0 unresolved or ambiguous
----
evidence-check: PASS
```

The three numbers above are the carried log's own. An earlier revision of this artifact quoted a block it
attributed to the same command — `0 checked`, `1 pairs confirmed`, `238 resolved and in range` — which is not the
output the log carries: the check recurses into every markdown artifact of the change, so its counts move as this
file and `tasks.md` grow, and the quote had been taken from a different run.

`measurements-moved.log` — every number the skill carried at the base, checked for presence in
`guidelines/Backlog.md`: 55 of 58 tokens present on the first pass, and the three the pass reported are present
too (two span a line break, one carries markdown emphasis) — the re-check is appended to that log.

- `bash tools/check-doc-routes.sh` — `all 154 section references resolve, all 4 skill references resolve`, exit 0.
- `bash tools/check-portability.sh` — `OK — 49 engines and 2 templates name no project value (108 value(s) read
  from the project)`, exit 0.
- `git grep -n -e '\.pi/skills/fix-loop' -- . ':(exclude)openspec/changes/archive'
  ':(exclude)tools/loop-cases.sh'` — no match, exit 1. This is the widened form of the item's own grep (`grep -rn
  '.pi/skills/fix-loop' AGENTS.md guidelines/ openspec/specs/`), which the round-1 review showed blind to
  `openspec/config.yaml`; the two excluded paths name the retired path on purpose (the archived changes are
  history, and `tools/loop-cases.sh` plants a versioned `fix-loop` skill in its throwaway project so
  `worktree#seedsAllFourInputs` can assert the engine does not seed it). The command's own output is carried in
  `task-verifications.log`.
- `test ! -d .pi/skills/fix-loop` — true.
- `openspec validate --changes --strict` — the open set is empty after the archive ("No items found to validate."),
  and the change's delta is in the capability spec and in its archived `specs/` (the round-1 run's
  `✓ change/…` is not a check the archived tree can repeat). Both runs are carried in `task-verifications.log`.
- `bash tools/check-openspec-hygiene.sh` — consistent, exit 0.

## Round 2 — the round-1 review's two blocking findings, and its notes

The round-1 review is `review-failed-round-1.md` (`LOOP-BLOCKING: 2`, `VERDICT: FAIL`). Both findings are answered
in the tree this artifact ships; the notes it raised are corrected here rather than argued.

### 1. `openspec/config.yaml` named the deleted skill

Its `context` block's defect-shaped-items clause read "`.pi/skills/fix-loop` owns the budgeted loop …" while this
same change deletes that directory — a live citation to a skill the tree no longer carries, in the one document
both the item's own grep and `tools/check-doc-routes.sh` were blind to (that check reads `AGENTS.md` and
`guidelines/*.md`; the `openspec/config.yaml` clause the change *did* fix is the `operations.apply` guidance, one
screen below). The clause now names the loop's entry and its contract:

```
Defect-shaped items: `tools/loop/run.sh` is the entry to the budgeted loop that closes bugs and defect-shaped
improvements (…); its contract is the spec `agent-issue-loop`. An item may only enter that loop when exactly one
fix follows from a proven root cause; otherwise it is skipped, not guessed at.
```

Task 2.1 carries that clause and the widened verify — the whole tracked tree minus the two paths that name the
retired path on purpose — and that command's empty output (exit 1) is in `task-verifications.log`. With it, the
proposal's own sentence about `AGENTS.md`, `guidelines/Backlog.md` and `openspec/config.yaml` is true of the tree
this change leaves.

### 2. The new invariant carried no revert-check

`tools/loop-mutations.sh` gains one record, following that file's own rule of one record per invariant:

- `255.1` on `tools/loop/evidence-check.sh` — the scan root read from the project's configuration. The mutation
  writes the literal the retired skill carried (`app/src`) in place of the configuration read, and
  `evidence#scansWhatTheProfileNames` is the case that must fail: the marker that case plants under the root its
  own configuration names is then not reported. Transcript, `evidence/loop-mutations-255.1.log`:

```
$ bash tools/loop-mutations.sh --only 255.1 --out openspec/changes/archive/2026-10-25-fix-retired-loop-skill-still-shipped/evidence/loop-mutations-255.1.log
RED    255.1  evidence#scansWhatTheProfileNames   (the scan root read from the project configuration — the engine looks at a literal of its own instead (issue #255))
restore 255.1 evidence#scansWhatTheProfileNames green again

red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0
```

The harness reports `selected 1 of 171 records` — the set is one record larger than the 170 the round-1 gate's
`--check` tally counted — and its `--check` step, which applies every record's expression to a copy of the file it
names without running a case, carried this record green in the round-2 gate (`evidence/gate-round2.log`). Task 3.4
names the record, the case and the transcript.

### 3. The notes

- The three numbers the Phase F quote carried, and the Phase D tally attributed to the evidence-check self-test,
  are corrected above from the logs this record carries.
- Task 1.2's `gate.sh --plan` listing and task 1.3's `check-portability.sh --values` listing are carried in
  `task-verifications.log` instead of standing as named commands with no output.
- The base choice's rationale no longer quotes the uncarried "192 paths" run.

### The round-2 gate, and what it did not read

`./tools/loop/gate.sh --base d6deb7e4372c44d127b5d177d58ff22c7cc33531`, run once for this round: its complete
output is `evidence/gate-round2.log`, whose last line is the `LOOP-GATE:` tally naming every suite that ran with
its wall clock. The pathspec is the round-1 one plus `tools/loop-mutations.sh`, which adds that file's own
self-test to the suites the gate owes — no compiled path is in it, so no Gradle run is owed.

This section and the round-2 measure block below it are written *after* that gate ran, so the gate's `*.md` steps
read this file one revision earlier — the disclosure the round-1 review asked for. The round-2
`evidence-check.log` above is the check taken once this file's text was complete; the lines appended below carry
no `file:line` reference and no tally pair, so its counts hold for them too.
