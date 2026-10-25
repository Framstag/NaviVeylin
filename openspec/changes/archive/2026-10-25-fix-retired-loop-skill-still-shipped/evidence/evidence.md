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
base commit, not the profile's default `merge-base HEAD origin/main` (`d846c74`): that range also holds the 192
paths the run's earlier items left in the branch, whose compiled files would owe a Gradle run this delta does not
touch. The pathspec rule the base exists for is "verification follows the changed files" (`guidelines/Build.md` §2).

`gate.log`, one line from it (the aggregate; no single tally carries the sum):

- The gate's summary line, quoted from `gate.log`: `LOOP-GATE: tests=558 errors=0`. No single tally carries
  that sum, so the pair this artifact quotes is the loop suite's own, below.
- Six tallies, every one with zero failed cases:
  - `tools_loop-selftest.sh_--from-git_d6deb7e4372c44d127b5d177d58ff22c7cc33531.xml` — `tests="232" failures="0"`,
    `time="1390"` (the loop owns `tools/loop/**`, so the full suite is owed: 232 of 232 cases, no file-set
    narrowing)
  - `bash_tools_loop-mutations.sh_--check.xml` — `tests="170" failures="0"`
  - `bash_tools_loop_evidence-check.sh_--selftest.xml` — `tests="6" failures="0"`
  - `tools_issue-tooling-selftest.sh.xml` — `tests="121" failures="0"`
  - `tools_check-portability-selftest.sh.xml` — `tests="29" failures="0"`
  - `tools_check-portability.sh.xml`, `tools_check-doc-routes*.xml`, `tools_check-openspec-hygiene*.xml`,
    `tools_mutate*.xml` — the structural checks, each `failures="0"`
- No Gradle suite: no compiled path in the pathspec (`LOOP_GATE_COMPILED_PATTERNS` matched nothing), which is the
  scope rule for a delta that touches no compiled file.

## Phase F — pointers and documents

`bash tools/loop/evidence-check.sh openspec/changes/2026-10-25-fix-retired-loop-skill-still-shipped --root .`:

```
ok   C1 §references: 0 checked, 0 exempted as archived pointers
ok   C2 test tallies: 1 pairs confirmed, 0 exempted (not retained), 0 unparsable, 0 drift
ok   C3 /tmp pointers: none cited
ok   C4 mutations: no REVERT-CHECK MUTATION marker under 1 configured path(s): app/src
ok   C5 file:line: 238 resolved and in range, 0 unresolved or ambiguous
evidence-check: PASS
```

`measurements-moved.log` — every number the skill carried at the base, checked for presence in
`guidelines/Backlog.md`: 55 of 58 tokens present on the first pass, and the three the pass reported are present
too (two span a line break, one carries markdown emphasis) — the re-check is appended to that log.

- `bash tools/check-doc-routes.sh` — `all 154 section references resolve, all 4 skill references resolve`, exit 0.
- `bash tools/check-portability.sh` — `OK — 49 engines and 2 templates name no project value (108 value(s) read
  from the project)`, exit 0.
- `grep -rn '\.pi/skills/fix-loop' AGENTS.md guidelines/ openspec/specs/` — no match.
- `test ! -d .pi/skills/fix-loop` — true.
- `openspec validate --changes --strict` — `✓ change/2026-10-25-fix-retired-loop-skill-still-shipped`.
- `bash tools/check-openspec-hygiene.sh` — consistent, exit 0.
