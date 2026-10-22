# Proposal — the gate's suite map covers every step the pipeline's checks run

## Why

Measured in `.pi/worktrees/loop` on `ee95d71` (evidence: `.pi/issue-loop/items/242/red-on-HEAD.log`,
`2026-10-22T04:41:39Z`, and `<change>/evidence/red-on-HEAD.log`):

- `tools/loop/loop.conf:128` records `LOOP_GATE_SUITES` as 21 `<glob>|<command>` pairs that name **8** distinct
  shell commands. `.github/workflows/build.yml` runs **31** shell commands under its `Check …` steps
  (`build.yml:148`–`316`). Comparing the two lists with the pipeline parsed as "a step named `Check …`, and the
  `bash <path> …` lines it runs" leaves **23 of the 31** named by no pair (`comm -13 map pipeline`, command in
  this change's `evidence/map-vs-pipeline.txt`). Among the missing: `tools/build-inputs.sh --check`,
  `tools/check-todo-references.sh`, `tools/resolve-stale-selftest.sh`,
  `tools/check-compose-sandbox-rule.sh`, `tools/mutate-selftest.sh`, `tools/check-portability.sh`,
  `tools/check-action-runtimes.sh`, `tools/check-metadata.sh` and eight of the ten mutation `--list` lines.
- `tools/loop/gate.sh:95` answers `LOOP-GATE: nothing owed — no path this profile gates changed since <base>`
  and exits 0 for a path no glob names — so a delta that touches one of those checks is *green in the run* no
  matter what the pipeline says about it. That is exactly the shape of 2026-10-21: `ed56ed2` (the merge of
  #229) passed the run's gate and broke `tools/check-portability-selftest.sh`, and the pipeline found the red
  `main`.
- **Root cause, one sentence: the profile's map is a hand-kept subset of the commands the pipeline's check
  steps run — eight of thirty-one — and the gate reads a path no glob names as owing nothing and exits 0, so a
  landing the pipeline refuses can be green in the run.**
- The red cases were added to `tools/loop-cases.sh` before the profile was changed, and each failed on HEAD
  (exit 1, `.pi/issue-loop/items/242/red-on-HEAD.log`, `2026-10-22T04:41:39Z`):
  `gate#theMapCoversEveryPipelineStep` — "the profile records no `LOOP_GATE_PIPELINE`, so the map is pinned to
  nothing and nobody can tell"; and `gate#aPipelineCheckIsOwedByItsPaths` — the plan for a delta touching
  `tools/build-inputs.sh` answered `nothing owed` instead of the check. The suite is a shell gate, so the
  evidence is the suite's own per-case line and its exit status; there is no JUnit XML to quote.
- The capability that owns it is **`agent-issue-loop`** — the capability whose requirement *The worker is handed
  one command that answers what the change owes* states that the checks come from the same call the gate makes,
  so the profile owns them. The rule the mechanism serves is **`guidelines/Build.md` §2, "Agent iteration
  protocol" — "verification follows the changed files"**, whose command table entry in **§3 (the gate row)**
  promises "the shell suite that covers the touched paths". What is missing is the map's *coverage*: the rule
  names a suite per path, and 23 of the pipeline's commands have no path that reaches them.
- The owner's recorded decision on this item (`.pi/issue-loop/items/242/work-package.md`, `ITEM_DECISION`,
  `2026-10-22T03:51:52Z`) is **option 1**: complete the profile's map rather than run every pipeline step for
  every item, and keep the build gate compiled-path-only. This change is that option, and the residual that
  decision accepted — a new pipeline step escapes until someone adds it — is answered by one cheap alarm: a
  case that compares the map with the pipeline's check steps, so the escape is a red loop suite rather than a
  red `main`.

surface: 2 cases added, 0 rewritten, 0 fixtures realigned, 2 mutation records added, 1 profile map replaced
(21 → 55 pairs) — `grep -rl 'LOOP_GATE' --include='*.sh' --include='*.md' --include='*.conf' --include='*.yml'
--include='*.json' . | grep -v '^./openspec/changes/archive' | grep -v '^./.pi/'` → 11 files, of which the map
itself (`loop.conf`), its two readers (`gate.sh`, `measure.sh`), the case runner (`loop-cases.sh`), the
falsification harness (`loop-mutations.sh`), the portability check (it reads `LOOP_GATE_CMD` as a value) and
`guidelines/Build.md` §3 assert the behaviour; `PORTING.md` and `guidelines/Backlog.md` document
`LOOP_GATE_CMD` only and are untouched.

## What Changes

- **One new requirement in `agent-issue-loop`**: the profile SHALL name the pipeline its map is pinned to, and
  the map SHALL carry every command the pipeline's check steps run, so a landing the pipeline would refuse is
  refused by the run. A profile that names no pipeline SHALL record that as an empty value rather than leave
  the key out, so "the map is unpinned" is a state a reader can see.
- **`tools/loop/loop.conf`** records `LOOP_GATE_PIPELINE=".github/workflows/build.yml"` and its
  `LOOP_GATE_SUITES` value grows from 21 pairs naming 8 commands to 55 pairs naming all 31, grouped by the step
  each pair belongs to: the path maps to *the whole command set of the step whose tools read it*, never to
  another step's.
- **`tools/loop-cases.sh`** gains the two cases: the pin (the map against the pipeline's `Check …` steps) and
  the behaviour (a delta on a path the pipeline checks owes that step's commands).
- **`tools/loop-mutations.sh`** gains one record per invariant (40.1, 40.2), so reverting a pair makes the named
  case fail.
- **`guidelines/Build.md` §3** states the bound the map now carries: every command of the pipeline's check
  steps is reachable from a path in the profile's map, and the map is pinned by a case.

The change is additive: no engine reads a new key it did not read before (`LOOP_GATE_PIPELINE` is read by the
pin case only), no suite's behaviour changes, and the compiled-path gate is untouched. Rollback is the revert
of the four files; nothing else depends on the extra pairs.

## Impact

- Affected files: `tools/loop/loop.conf`, `tools/loop-cases.sh`, `tools/loop-mutations.sh`,
  `guidelines/Build.md`, the delta in `openspec/specs/agent-issue-loop/spec.md`.
- Affected modules: none — no Kotlin, Java or C++ file is touched, so `LOOP_GATE_COMPILED_CMD` is not owed by
  this change and the Gradle gate does not run.
- Specifications changed: `agent-issue-loop` (one ADDED requirement; no existing requirement is modified).
- Cost per item: a change touching `openspec/**` or a note now owes the whole "Check documentation routes" step
  (two scripts and one mutation list) and `tools/check-todo-references.sh`; all three are greps, measured at
  0.14 s and 0.50 s over this tree on 2026-10-22. A workflow delta owes the loop's suite, because the loop's
  suite is where the map is pinned against it.
- Breaking: none. A profile that records no `LOOP_GATE_PIPELINE` at all is refused by the loop's own suite; the
  foreign-project fixture is not run by any suite, so it keeps its three keys.
