# Proposal — the run publishes the base it landed on

## Why

**Root cause.** The run's only push is one call site, `push_branch "fix/issue-$issue-$slug"`
(`tools/loop/run.sh:2802`), and a landing is a fast-forward of the **project tree's local base**
(`tools/loop/worktree.sh:772`). Nothing publishes the branch the landing went into, so a run started with
`--push yes` leaves `main` local while `origin/main` stands where a person's last push left it.

**What that costs (issue #1018).** Measured 2026-11-22 08:25 on the project tree: `git status -sb` read
`## main...origin/main [ahead 29]`, `origin/main` was `e4132813`, and `git push --dry-run origin main` printed
`e4132813..3a39d92a main -> main` — six hours and 29 commits CI's `Build Debug APK` never compiled. The
`broken-main` sensor reads the CI job of that remote commit, not the tree, so it filed #1011 at 22:03 on a failure of
`e4132813` and re-read it as today's condition at 23:5x, while the two cases it named pass on `3a39d92a`; the same
shape produced #986 before it. Two facts make the drift silent rather than loud: the fifth guard of
`tools/sensors/guard.sh` refuses when `HEAD` is not the remote base's commit, and `tools/loop/report.sh` reads no
remote at all (`origin` appears nowhere in it), so no line of a run says which commits the remote is missing.

**The single mechanism.** The flag publishes one ref — the item's branch, which the run has just merged and
released — and never the base that branch landed on. One function, `push_branch`
(`tools/loop/leftovers.sh:414`), is what publishes; the fix gives it the base as a second caller in the run's
publishing block, so `--push yes` means what the flag promises, and a base publish that fails is one named line and
no retry (`publish-failed`, `tools/loop/events.tsv:104`).

**The red test.** `worktree#publishesTheBaseItLandedOn` (`tools/loop/tests/loop-cases.sh:4503`) drives a run with
`--push yes` over a bare remote whose base stands at the commit the run starts from. On the base commit it fails:
`a run asked to publish leaves the remote's base carrying its last landing, not where a person left it: expected
'1149c77…', got 'ca04c78…'` — `loop-selftest: 1 of 1 cases failed`, exit 1 (`evidence/red-on-base.log`,
2026-11-22T15:08Z). It is a shell case and leaves no JUnit XML; the number the run cites is that log.

**Owning capability, and what owns the edit.** `agent-issue-loop`, "The publish publishes what still exists"
(`openspec/specs/agent-issue-loop/spec.md:2248`) — the requirement the delta modifies, repeating both of its
shipped scenarios verbatim and adding one. `AGENTS.md`'s row for the loop's procedure routes a `tools/loop/**`
change to `guidelines/Backlog.md`, whose bodies are `guidelines/Backlog.recipes.md`; that text states what the suite
owes and what a leg bounds, and states nothing about which refs a run publishes, so no guideline section carries
this rule and the delta owes no guideline edit (the search that shows it: `tools/look.sh 'The publish publishes what still
exists'` — 3 matches, 1 live in the spec and 2 in archived changes).

surface: 1 case added, 1 case's assertion rewritten, 1 engine call site added, 1 requirement modified (2 scenarios repeated, 1 added) — `tools/look.sh 'push_branch'` (18 matches, 9 files); `tools/look.sh 'The publish publishes what still exists'` (3 matches, 3 files)

## What Changes

- `tools/loop/run.sh` — the run's publishing block gains `push_branch "$(loop_base_ref)"`, so the base an item
  landed on leaves the run with the item's branch.
- `tools/loop/leftovers.sh` — the paragraph above `push_branch` states that both refs leave through that one
  function, and what a failed publish of either says.
- `tools/loop/tests/loop-cases.sh` — `worktree#publishesTheBaseItLandedOn`: a bare remote whose base stands at the
  run's starting commit, the landed base it must carry afterwards, and a remote whose own hook refuses the push. And
  `worktree#noPushWithoutTheFlag`'s call-site count, 1 → 2, still guarded by the flag.
- `openspec/specs/agent-issue-loop/spec.md` — "The publish publishes what still exists" restated: the base a
  landing was merged into is published through the same push, with one scenario and one case.
