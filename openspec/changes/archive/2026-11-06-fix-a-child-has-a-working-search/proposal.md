# fix-a-child-has-a-working-search

surface: 2 cases added, 3 assertions in existing cases rewritten, 6 mutation records added, 1 adapter edited, 1 wrapper
extended with one flag, 4 prompt skeletons edited, `AGENTS.md` and 6 guideline headers and 2 `openspec/config.yaml`
guidance entries re-recipe'd, 2 guideline paragraphs, 2 `AGENTS.md` routing rows re-priced —
`grep -c "^case_adapter_" tools/loop-cases.sh` (5 adapter cases: 1 added), `grep -c "look.sh '\^## '" tools/loop/prompts/*.md`
(4 skeletons), `grep -rln "grep -n '\^## '" AGENTS.md guidelines/*.md openspec/config.yaml` (0 files, was 10).

## Why

A child the loop starts had no search tool it could use on 2026-11-06, and the two things it was told instead are one
it cannot run and one its own policy refuses.

- **The condition, verified in the tree today.** Every phase prompt named the shell recipe for a document's section
  list, `grep -n '^## '` (`tools/loop/prompts/worker.md:43`, `worker-gate.md:35`, `worker-tail.md:36`,
  `reviewer.md:18` at the base commit), the same recipe stood in `AGENTS.md` and in six guideline headers — the document
  the prompts send a child to read — and the policy the same adapter loads with every child refuses exactly that call:
  `node tools/pi-shell-refusal.mjs --command "grep -n '^## ' guidelines/Build.md"` → `REFUSED … exit 3`
  (`evidence/policy-refuses-the-prompt-recipe.log`). The refusal's own answer names `tools/look.sh` first and
  `ctx_grep` second, and `tools/look.sh` had no way to answer one document: its walk covered the whole tree, and the
  only seam that scoped it (`LOOK_ROOT`) is the cases' and not a child's (`tools/look.sh:22`, base commit).
- **Root cause, one mechanism.** A child's tree search was never a call that runs: the documents it is handed name a
  shell command its own policy refuses, and the tool the refusal names second does not start on this host.
- **Measured, on this host and on this run archive.**
  `python3 evidence/ctx-grep-census.py` → `CTX-GREP-CENSUS items=252 items_failed=125 calls=482 failed=282`: 282 of 482
  `ctx_grep` calls answered `zsh:1: command not found: rg`, in 125 of 252 item directories
  (`evidence/ctx-grep-census.log`). The binary is present — `command -v rg` → `~/.pi/agent/bin/rg`, ripgrep 15.2.0 —
  and the same call fails in an interactive session whose `PATH` carries that directory, while `zsh` and `bash` started
  from that session both find `rg` (`evidence/ctx-grep-does-not-resolve-rg.log`). So what the measurement shows is
  that `ctx_grep`'s own shell does not carry the caller's `PATH`, which is why prepending it cannot be the fix and why
  the wrapper is.
- **Red test.** Two cases added and three assertions rewritten, failing on the base commit `5b30ed79`
  (`evidence/red-on-head.log`, `evidence/red-on-head-look.log`, `evidence/red-on-head-adapter.log`,
  `evidence/red-on-head-prompt.log`, 2026-11-06T16:42:50Z): `look#aPathScopesTheWalkToIt` (exit 2, `look: unknown
  argument --path`), `adapter#theChildIsStartedWithItsToolDirectoryOnPath` (the child's `PATH` began `/usr/bin`), and
  `prompt#thePhasePromptsCarryTheirOwnRoute` (the wrapper's call was absent). `evidence/red-on-head.xml` carries the
  tally.
- **Owning requirement.** `openspec/specs/agent-issue-loop/spec.md`, the requirement this change adds, "A child is
  started with a tree search that runs".
- **Owning guideline sections.** `guidelines/BuildGate.md` §2a, "Rule: answer a tree question with one call" (the
  call, and the `--path` scope that answers one document), and `guidelines/Backlog.recipes.md` §7a, "What a child is
  started with, and what it therefore re-reads" (the adapter's environment, the documents' recipe and the census).

## What changes

1. `tools/look.sh` — `--path <path>` scopes the one walk to a file or a directory of the root, so a document's
   section list is one call (`tools/look.sh '^## ' --path guidelines/Build.md`); a scope the root does not hold, or one
   carrying `..`, is a refusal (exit 2) and not an empty answer, and the answer's first line names what was walked.
2. `tools/loop/prompts/worker.md`, `worker-gate.md`, `worker-tail.md`, `reviewer.md` — the section-list recipe is
   that call, and the worker's surface count is `tools/look.sh '<the rule>'` instead of `grep -l`.
3. `AGENTS.md`, the six guideline headers that carried the same recipe (`Backlog`, `BacklogTooling`, `UI`, `UICar`,
   `UIPhone`, `UIPhoneMap`) and the two `openspec/config.yaml` guidance entries — the recipe a child is *routed* to is
   the same call, because a prompt that points into a document naming the refused shell form has fixed nothing.
4. `tools/pi-child-events.sh` — the directory this project's agent keeps its tools in is prepended to the child's
   `PATH`, once, and only when it exists: `PI_CODING_AGENT_DIR/bin` when the agent names its own directory, else
   `$HOME/.pi/agent/bin` (guarded, so an adapter started without `HOME` still runs).
5. `tools/loop-cases.sh` — `look#aPathScopesTheWalkToIt` and `adapter#theChildIsStartedWithItsToolDirectoryOnPath`
   added (the second asserting the prefix, the inherited path behind it, a directory that is not there, and no second
   copy); `prompt#thePhasePromptsCarryTheirOwnRoute` rewritten to name the call, refuse the old one and check the
   document map and every guideline header; `prompt#workerNamesTheChangeSurfaceBeforeThePlan` rewritten where it
   asserted the `grep -l` the same policy refuses.
6. `tools/loop-mutations.sh` — records 87.1–87.6, one per invariant: the `PATH` prepend, the `--path` scope, the
   first line naming the walk, the refusal that keeps a scope inside the tree, and the two documents' recipe.
7. `guidelines/BuildGate.md` §2a and `guidelines/Backlog.recipes.md` §7a — the call, the flag, the census and the
   measurement; the two `AGENTS.md` rows that price `BuildGate.md` §2a carry the section's new size (6.2 kB, was 6.0).

## Impact

What a child is handed changes; what the run decides does not. The engine stays a caller: the directory is a fact
about this project's agent and lives in this project's adapter, which a ported project deletes, and the wrapper is the
project's own tool. No compiled file is touched: the delta is one flag, one adapter line, four prompt skeletons, the
documents' recipe, two cases, three rewritten assertions, six mutation records and two guideline paragraphs, so the
build gate is replaced by the structural checks and the loop's own suite. `ctx_grep`'s failure is an upstream report
and is left named, not worked around: the child's search is the wrapper, which runs wherever `grep` does.

The change was landed from a project tree another session was working in, and the peer's in-flight `#804` hunks had
been written into `tools/loop-cases.sh` before this change's commit: the commit carries this change's hunks only, and
the peer's stay in the working tree (`evidence/loop-suite.log` was taken on a clean worktree of the commit, which is
how that was checked).
