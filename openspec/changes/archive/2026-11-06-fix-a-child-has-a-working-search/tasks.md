# Tasks — fix-a-child-has-a-working-search

No compiled file is touched: the delta is one flag on a project tool, one adapter line, four prompt skeletons, the
document-and-header recipe, two cases, three rewritten assertions, six mutation records and two guideline paragraphs.
The build gate is therefore replaced by the structural checks of the files edited (`tools/loop-selftest.sh`,
`tools/loop-mutations.sh --check`, `tools/check-portability.sh`, `tools/check-doc-routes.sh`,
`tools/check-openspec-hygiene.sh`, `openspec validate --strict`) and by the loop suite itself, which is the gate for
`tools/loop/**`.

- [x] 1. Write the red cases and run them on the base commit `5b30ed79`.
  Verification: `bash tools/loop-selftest.sh --case 'look#aPathScopesTheWalkToIt'` → `FAIL` (`look: unknown argument
  --path`); `--case 'adapter#theChildIsStartedWithItsToolDirectoryOnPath'` → `FAIL` (the child's `PATH` began
  `/usr/bin`); `--case 'prompt#thePhasePromptsCarryTheirOwnRoute'` → `FAIL`. Logs:
  `evidence/red-on-head-{look,adapter,prompt}.log`, tally `evidence/red-on-head.xml`, 2026-11-06T16:42:50Z.

- [x] 2. Measure both halves of the item's claim instead of adopting it.
  Verification: the census, re-taken here rather than quoted from the item —
  `python3 evidence/ctx-grep-census.py` → `CTX-GREP-CENSUS items=252 items_failed=125 calls=482 failed=282`
  (`evidence/ctx-grep-census.log`); `node tools/pi-shell-refusal.mjs --command "grep -n '^## ' guidelines/Build.md"`
  → `REFUSED … exit 3`, and the same call on the wrapper's scoped form → `ALLOWED … exit 0`
  (`evidence/policy-refuses-the-prompt-recipe.log`); the session's environment resolves `rg` in `zsh` and `bash` while
  `ctx_grep` answers `command not found` (`evidence/ctx-grep-does-not-resolve-rg.log`), which is what rules out
  "prepend `PATH` and stop" and puts the prepend in the change as one of three parts.

- [x] 3. Scope the wrapper: `tools/look.sh --path <path>`, refusing a scope the root does not hold and one carrying
  `..`.
  Verification: `bash tools/loop-selftest.sh --case 'look#*'` → `all 3 cases passed`;
  `bash evidence/look-path-probe.sh` → `--path ../outside` and `--path guidelines/../outside` both
  `look: --path carries .., … exit=2`, and `--path guidelines/Build.md` → `LOOK: 42 matches in 1 files …`
  (`evidence/look-path-probe.log`).

- [x] 4. Export the agent's tool directory for the child (`tools/pi-child-events.sh`), guarded and idempotent.
  Verification: `bash tools/loop-selftest.sh --case 'adapter#*'` → `all 5 cases passed` (the case asserts the prefix,
  the inherited path behind it, that a directory which does not exist changes nothing, and that a `PATH` already
  carrying it gains no second copy).

- [x] 5. Name the call in every phase prompt (`worker.md`, `worker-gate.md`, `worker-tail.md`, `reviewer.md`), in
  `AGENTS.md`, in the six guideline headers that carried the same recipe, and in the two `openspec/config.yaml`
  guidance entries.
  Verification: `bash tools/loop-selftest.sh --case 'prompt#thePhasePromptsCarryTheirOwnRoute'` → `PASS`, which now
  also asserts the map and every guideline header carry no refused recipe; `--case
  'prompt#workerNamesTheChangeSurfaceBeforeThePlan'` → `PASS`, its assertion rewritten to the wrapper; `grep -rln
  "grep -n '\^## '" AGENTS.md guidelines/*.md openspec/config.yaml` → no file.

- [x] 6. One mutation record per invariant (`tools/loop-mutations.sh`, 87.1–87.6).
  Verification: `bash tools/loop-mutations.sh --only "87.1 87.2 87.3 87.4 87.5 87.6"` → `RED` six times,
  `restore … green again` six times, `red-when-mutated: 6 · not-noticed: 0 · restore-failures: 0`; `bash
  tools/loop-mutations.sh --check` → `all 255 cases passed` (`evidence/mutations.log`).

- [x] 7. State the rule where it is owned: `guidelines/BuildGate.md` §2a (the call and its `--path` scope) and
  `guidelines/Backlog.recipes.md` §7a (what a child is started with, the census behind it, and the part this tree
  cannot fix).
  Verification: `bash tools/check-doc-routes.sh` → `all 329 section references resolve` (the two `AGENTS.md` rows that
  price §2a carry its new size), `bash tools/check-openspec-hygiene.sh` → consistent, `bash
  tools/check-portability.sh` → `OK — 56 engines and 7 templates name no project value`.

- [x] 8. Run the loop's own suite, which is the gate for `tools/loop/**`, over the whole set — on a clean worktree of
  the commit, because the project tree carries another session's in-flight edits and a suite run there would not
  describe this commit.
  Verification: `git worktree add .pi/worktrees/796-verify <commit>` → `bash tools/loop-selftest.sh` →
  `loop-selftest: all 332 cases passed (JDK 27, offline, no token, no sandbox) — 416.16 s` (`evidence/loop-suite.log`,
  2026-11-06T17:22:05Z→17:29:02Z); the structural checks are in `evidence/structural-checks.log`.

- [x] 9. Answer the independent read with its findings addressed: the peer's `#804` hunks removed from this commit
  (they stay in the working tree), the suite re-taken from the commit itself, the "no search tool" overstatement
  corrected to "no `rg`", `AGENTS.md` and the guideline headers re-recipe'd, the header line and the escape refusal
  given their own assertions and records, `$HOME` guarded, and the census made reproducible.
