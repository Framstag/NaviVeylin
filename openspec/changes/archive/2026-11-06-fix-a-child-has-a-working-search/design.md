# Design — fix-a-child-has-a-working-search

## Root cause

A child's tree search was never a call that runs. Three things say how:

1. every phase prompt named `grep -n '^## '` for a document's section list — and so did `AGENTS.md` and six guideline
   headers, which is the document the prompts route a child into — while the policy the adapter loads with every child
   refuses exactly that command (`REFUSED`, exit 3): the documents taught a call the child's own policy blocks;
2. the refusal's second answer is `ctx_grep`, which answered `zsh:1: command not found: rg` on 282 of 482 calls over
   this run archive: the shell it spawns does not carry the caller's `PATH`, while `rg` sits in the agent's own
   directory;
3. and the wrapper that does run, `tools/look.sh`, covered the whole tree and nothing smaller, so the one question a
   prompt needs — one document's headings — had no call at all.

## The single fix

Give the child one search call that runs, and say it everywhere a child is routed: `tools/look.sh` gains `--path` (one
file or one directory, never outside the tree), every phase prompt, `AGENTS.md` and the guideline headers name that
call, and the adapter prepends the agent's own tool directory to the child's `PATH` so a run started from a context
without it does not leave a worker without `rg`.

## Blast radius

One project tool (`tools/look.sh`), one adapter (`tools/pi-child-events.sh`), four prompt skeletons, `AGENTS.md`, six
guideline headers, two config guidance entries, two guideline paragraphs, two cases added, three assertions
rewritten, six mutation records. No engine script, no recorded config value, no compiled file.

## Alternatives the evidence eliminated

- **Record the directory as a `loop.conf` key and export it in the engine.** Refused: `tools/loop/**` may name no
  project fact (`design D16`, `tools/check-portability.sh`, `run#engineNamesNoProjectFact`), and the directory is a
  fact about this project's agent, which the adapter is exactly the place for.
- **Prepend `PATH` and stop there** (the item's own first proposal). Not sufficient, and the measurement says why:
  the same `ctx_grep` call fails in an interactive session whose `PATH` already carries the directory
  (`evidence/ctx-grep-does-not-resolve-rg.log`). The prepend is kept because it is the half that fixes a child's
  *own* subprocesses and costs one line, but it cannot be the fix, and a change that claimed it was would have
  shipped a red case that never went green.
- **Repair `tools/look.sh`'s bound, or add a `--context`-style flag, instead of `--path`.** Refused: the bound is
  `look#aResultOverTheBoundIsCutAndNamed`'s invariant and unchanged; `--context N` widens one match and does not
  narrow the walk, which is the axis a section list needs.
- **Rewrite the prompts to name `ctx_grep`.** Refused by the measurement above: on this host it does not run, and a
  prompt is the one place a child cannot tell a broken tool from an empty tree.
- **Fix the prompts and leave `AGENTS.md` and the guideline headers alone.** Refused after review: the prompts route
  the child into `AGENTS.md` for its section list, so the same refused recipe stood one document further on — the
  change's own evidence shows the command is refused, and leaving it in the map would have made this change's new
  sentence in `guidelines/BuildGate.md` §2a contradict the map on the same question.

## Rollback

Revert the eight files and the case/mutation records. Nothing recorded, nothing packaged, nothing persisted changes
shape: the flag is additive, the adapter's prepend is guarded by `[ -d … ]` and idempotent, and the prompts and headers
are prose.

## What this change does not do

It does not report the `ctx_grep` fault upstream, and it does not remove `ctx_grep` from the refusal's answer: on a
host where the tool resolves, it is a good second answer. The item's report and the upstream half are named in
`guidelines/Backlog.recipes.md` §7a so the failure stays visible instead of becoming folklore.
