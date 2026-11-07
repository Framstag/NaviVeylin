# Design — fix-a-child-carries-its-own-route

## Root cause

One mechanism: the adapter this project's `LOOP_AGENT_CMD` names starts the child with the agent's default
resource discovery, and the child is started in the project root, so the `AGENTS.md` the agent finds there is
folded into its system prompt as a `project_context` section (`tools/pi-child-events.sh:35` for the mode word,
`tools/pi-child-events.sh:46` for the context switch this change adds, `tools/loop/run.sh:1025` for the start
directory). That section is part of every turn's baseline, so it is charged once per turn of a child whose whole
work is one item — measured at 25 624 of 32 924 characters of system prompt and 9 151 of the 17 235 tokens a turn
costs (`evidence/sections-probe.log`, `evidence/loop-spend.log`).

## The fix

Two lines of production change and two prompt sections:

1. `tools/pi-child-events.sh` starts the child with `--no-context-files` (`context=(--no-context-files)`, appended
   beside the mode words, before the prompt). It is the agent's own switch for exactly the discovery that produces
   the section, and it is a fact about this project's agent, so it lives in the adapter and no engine script names
   it (`run#engineNamesNoProjectFact`, `tools/check-portability.sh`).
2. `tools/loop/prompts/worker.md` and `reviewer.md` state the route the child now lacks: `AGENTS.md` is the
   project's document map, a guideline's sections are listed by `grep -n '^## '`, `guidelines/` holds the rules,
   `openspec/specs/` the capabilities, and the change's `evidence/` its citations. The worker prompt grows from
   15 159 to 16 422 characters, the reviewer's from 7 636 to 8 185 — 1 812 characters together, against the
   25 624 the section cost, and the two prompts are read once whereas the section was re-read on every turn.

## The risk, and what proves it did not happen

The child is started in the project **on purpose**: the project's own tool policy is resolved at the directory the
process starts in. A flag that moved the start directory — or that replaced the system prompt, changed the trust, or
narrowed the tools — would take the policy with it, so:

- `adapter#theChildIsStartedWithoutContextFiles` asserts the context switch is there **and** that no word of
  `-a`, `--approve`, `-na`, `--no-approve`, `--system-prompt`, `--append-system-prompt`, `-t`, `--tools`, `-ns` or
  `--no-skills` rides along.
- `run#childStartsInTheProjectRoot` (unchanged, still green) asserts every child's recorded `cwd` is the project
  root, which is the seam the policy is resolved on.
- `prompt#thePhasePromptsCarryTheirOwnRoute` asserts every repository path the two skeletons name resolves in the
  project root, so a child that has no `AGENTS.md` in its prompt is not sent to a file that is not there. The
  literal half of the risk — a path the policy *refuses* is still refused — is not asserted here: that policy is
  this machine's tool allowlist, not a file in the tree, so an offline case cannot reproduce a refusal. What it can
  do is pin the seam the refusal is resolved at (the start directory) and the absence of any flag that would move
  it, which is what the two cases above do.

## Blast radius

`tools/pi-child-events.sh`, the two prompt skeletons, two cases (`tools/loop-cases.sh`), two mutation records
(`tools/loop-mutations.sh`), the guideline rule (`guidelines/Backlog.md`, section "The loop") and this change. Nothing under
`tools/loop/*.sh` names the new flag, so the engine's portability is untouched; a project that has no adapter — the
porting recipe deletes it — keeps whatever its own configured command does, and the prompt skeleton it uses carries
the route either way.

## Rollback

Remove `context=(--no-context-files)` from the adapter: the argv goes back to `--mode json <prompt>`, the child gets
the project's context files again, and the two prompts keep a routing section that costs 1 812 characters and is
harmless. Nothing else reads the flag.

## Alternatives the evidence eliminated

- **A per-phase `--system-prompt <path>`.** It replaces the agent's whole system prompt rather than one section of
  it, and the prompt it would be given is the phase prompt the run already passes as the first message — so it buys
  the same saving by removing the agent's own instructions, which the run depends on (the tool discipline, the
  event-mode contract) and the adapter can prove nothing about.
- **`-t`/`--tools` to shrink the tool list.** The `tools` section is 3 120 of the 32 924 characters — a different
  axis (a child that may not call a tool it needs), not this defect, and it would be a second change.
- **Trimming `AGENTS.md` itself.** It is the supervising session's map and its rows are what a project-level reader
  routes with; the cost here is that a *child* re-reads it, not that it is large (proposal §Why measures 25 766
  characters against 25 624 in one system prompt). Shrinking the document to fit a child's budget would remove
  routing from the reader it was written for.
