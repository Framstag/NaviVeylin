# fix-a-child-carries-its-own-route

## Why

Every child of a run is started with the project's whole context: the agent discovers `AGENTS.md` in the
project root it is started in and turns it into a `project_context` section of its system prompt, which is
re-read on every turn of a session whose work is one item.

- **The condition, verified in the tree today.** `tools/pi-child-events.sh:46` appends the only words this
  project's adapter adds to the configured command, and `tools/loop/run.sh:1025` starts that command with
  `cd "$LOOP_ROOT"` — the project root, which is where the agent's context-file discovery happens. On the base
  commit the adapter's argv was `mode=(--mode json)` alone (`git show <base>:tools/pi-child-events.sh`), so the
  child was started with `AGENTS.md` in its system prompt, 25 766 characters of it (`wc -c AGENTS.md`).
- **Root cause, one mechanism.** The adapter starts the child with the agent's default resource discovery, so the
  project's context file becomes a system-prompt section charged on every turn, while the phase prompt already
  carries — and now must carry — the little routing that child needs.
- **Measured.** On the stream of one item
  (`openspec/changes/fix-a-child-carries-its-own-route/evidence/sections-probe.py`, log beside it):
  `section project_context 25624 chars`, `total system prompt 32924 chars`, `project_context share … 77.8 % … 9151.4
  tokens at 2.8 chars/token`. Against the archive's own baseline (`python3 tools/loop-spend.py`:
  `LOOP-SPEND-BASELINE tokens=17235 charged=354875389`) that section is 9 151 of the 17 235 tokens a turn costs —
  a little over half of what every turn of every child re-reads, for routing a bounded child does not use.
- **Red test.** `adapter#theChildIsStartedWithoutContextFiles` and
  `prompt#thePhasePromptsCarryTheirOwnRoute`, both added by this change, both failing on the base commit:
  `loop-selftest: 2 of 2 cases failed` for the adapter group and `loop-selftest: 1 of 1 cases failed` for the
  prompt case (`evidence/red-on-head-adapter.log`, `evidence/red-on-head-prompt.log`, tallies
  `evidence/red-on-head-adapter.xml` and `evidence/red-on-head-prompt.xml`, 2026-10-26T23:32:26Z and
  2026-10-26T23:32:32Z).
- **Owning requirement.** `openspec/specs/agent-issue-loop/spec.md`, "Each item is worked by a fresh process,
  reviewed by a different one" — its scenario "A process runs where the project's own policy applies" — extended
  by the requirement this change adds ("A child carries its own route, and not the project's whole document").
- **Owning guideline section.** `guidelines/Backlog.md`, its section "The loop", which states what a run is and what makes
  it survivable; it gains the rule that a child is started without the project's context files and carries the
  routing it needs in its own prompt.
- **Surface, before the plan was fixed.** `surface: 2 cases added, 1 rewritten, 2 mutation records added, 2 prompt
  skeletons edited, 0 fixtures — grep -n 'theChildIsStartedWithoutContextFiles\|thePhasePromptsCarryTheirOwnRoute'
  tools/loop-cases.sh tools/loop-mutations.sh; grep -n 'adapter#modeFlagIsItsOwnArgument' tools/loop-cases.sh`.
  The rewritten case is `adapter#modeFlagIsItsOwnArgument`, whose assertion is the child's whole argv; the case
  that pins the other half of the risk — that the child still starts in the project, where the tool policy is
  resolved — is `run#childStartsInTheProjectRoot`, which this change leaves green.

## What changes

1. `tools/pi-child-events.sh` — `context=(--no-context-files)`, appended beside the mode words and before the
   prompt, so the child is started without the project's context files. It stays in the adapter because it is a
   fact about this project's agent, and no phase under `tools/loop/**` may name one
   (`run#engineNamesNoProjectFact`, `tools/check-portability.sh`).
2. `tools/loop/prompts/worker.md`, `reviewer.md` — the route the child now lacks: `AGENTS.md` as the document map,
   `grep -n '^## '` for a document's section list, `guidelines/` for the rules, `openspec/specs/` for the
   capabilities and the change's `evidence/` for citations. 1 812 characters together, against the 25 624 the
   section cost — and read once instead of on every turn.
3. `tools/loop-cases.sh` — two cases added (`adapter#theChildIsStartedWithoutContextFiles`,
   `prompt#thePhasePromptsCarryTheirOwnRoute`) and one rewritten (`adapter#modeFlagIsItsOwnArgument`, whose
   assertion is the child's whole argv).
4. `tools/loop-mutations.sh` — records `66.1` (the context switch) and `66.2` (the route's path), one per invariant.
5. `guidelines/Backlog.md`, its section "The loop" — the rule, the measurement, and the seam that must not move.

## Impact

No engine decision changes: the run still starts every child in the project root, and the gate, the report, the
state and the labels are untouched. A ported project keeps whatever its own configured command does — the flag
lives in this project's adapter, not in a phase — and its prompt skeleton carries the route either way. The
`tools/pi-child-events.sh` and `tools/loop/prompts/**` pathspec owes the loop's whole self-test (243 cases), the
record check and the portability check, which is the gate this change ran once.
