# Proposal — fix-retired-loop-skill-still-shipped

## Why

One defect, verified on the run's base `d6deb7e4`, issue #255: **`fix-loop` is a skill no session loads, and it is
still shipped, still seeded into every loop worktree, and still cited as the home of five measurements and of the
evidence check.**

**Root cause (one).** The skill stopped being an entry point on 2026-10-22 — the loop is entered through
`tools/loop/run.sh`, the run's contract is the spec `agent-issue-loop`, and the child's contract is
`tools/loop/prompts/worker.md` (`LOOP_WORKER_SKILLS` does not name it, `tools/loop/loop.conf:139`) — but nothing
was migrated out of it: `tools/loop/worktree.sh:96` kept copying it into every worktree, `worker.md:170` kept
running the copy of its script, and `loop.conf:43`, `prescreen.sh:15` and `guidelines/Backlog.md:29` kept pointing a
reader at it. A directory that is versioned *and* copied into the worktree is a second copy of a file the checkout
already carries, which is why the copy read as harmless while the references made it authoritative.

**Verified, not quoted from the tracker.** Measured 2026-10-25 on this base, the surface of the name
(`evidence/surface-head.log`):

```
$ git grep -ln "fix-loop" HEAD -- . | grep -v '^HEAD:openspec/changes/archive/'
HEAD:.pi/skills/fix-loop/REFERENCE.md
HEAD:.pi/skills/fix-loop/SKILL.md
HEAD:.pi/skills/fix-loop/scripts/evidence-check.sh
HEAD:AGENTS.md
HEAD:guidelines/Backlog.md
HEAD:openspec/config.yaml
HEAD:tools/loop-cases.sh
HEAD:tools/loop/loop.conf
HEAD:tools/loop/prescreen.sh
HEAD:tools/loop/prompts/worker.md
HEAD:tools/loop/worktree.sh
HEAD:tools/portability-cases.sh
--- count (excluding the archived changes, which are history): 12
```

surface: 12 files name the skill on HEAD — 3 of them the skill itself, 9 outside it, plus 2 consequence sites
(the portability check's value set and `tools/loop/gate.sh`'s resource-tree comment) and 1 new case — from
`git grep -ln "fix-loop" HEAD -- . | grep -v '^HEAD:openspec/changes/archive/'` (12 files, listed below) and
`git grep -n fix-loop HEAD -- tools/` for the two consequence sites. The archived changes are history
and are not rewritten, except for the one the item names (its own record of the 2026-10-22 reduction).

**Red test — the smallest case that exercises the defect, failing on HEAD.** Two cases of the loop's own suite,
both red on this base (`evidence/red-on-head.log`, taken 2026-10-25 with `tools/loop/worktree.sh` restored from
`HEAD` and `tools/loop/evidence-check.sh` moved aside):

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

Both are green after the change (`evidence/green-cases.log`). The document half of the red case is the check that
owns it: `tools/check-doc-routes.sh` reports a document naming a skill the tree no longer carries, so the
`AGENTS.md` row could not simply be deleted and left the skill in place, and could not keep the row after the skill
went — the two halves are one change.

## What changes

- `tools/loop/evidence-check.sh` — the check moves here from the skill. Its two project paths are now the project's
  configuration (`LOOP_EVIDENCE_SCAN_PATHS`, `LOOP_EVIDENCE_XML_DIR` in `tools/loop/loop.conf`), read and never
  sourced, with `--scan`/`--xml-dir` overrides for a tree that is not this one; a configuration that names no scan
  root is refused instead of scanning nothing. It is a tracked file, so a worktree carries it from the checkout —
  the seed step it needed before exists for generated files only.
- `tools/loop/gate.sh` — one comment loses the resource-tree literal it quoted: the value is now declared as a
  project value, so the portability check reports it in that engine like anywhere else (the pattern itself stays in
  the profile, and the comment cites it there).
- `tools/check-portability.sh` — the two new configuration keys join the declared value set, the same way
  `LOOP_WORKTREE_SCRATCH_PATHS` did for issue #210.
- `tools/loop/loop.conf` — the two keys, one gate pair for the moved check's own self-test, and the citation of the
  measured costs repointed at `guidelines/Backlog.md`, section 7.
- `tools/loop/worktree.sh` — the seed copies `openspec-*` only.
- `tools/loop/prompts/worker.md`, `tools/loop/prescreen.sh` — the paths and citations they carry.
- `tools/loop-cases.sh` — `worktree#seedsAllFourInputs` asserts what is seeded and what is no longer, and the new
  case `evidence#scansWhatTheProfileNames` exercises the moved check against a configuration of its own.
- `tools/portability-cases.sh` — the foreign-tree scaffolding drops the directory the engine no longer copies.
- `AGENTS.md`, `guidelines/Backlog.md`, `openspec/config.yaml` — no document names the skill. The measurements the
  skill carried are in `guidelines/Backlog.md`, section 7, where the loop's costs and its floor belong.
- `.pi/skills/fix-loop/` — deleted. Its rules were already stated once, in the spec `agent-issue-loop` and
  `tools/loop/prompts/worker.md`; this change removes the copies, not the rules.
- The archived change `2026-10-22-loop-harness-bugs-and-features` — its record of the reduction is amended once
  more, to say the skill is gone and where its content went.

## Spec

Delta to `openspec/specs/agent-issue-loop/spec.md`:

- `ADDED` **The evidence check is the loop's own tool, and the paths it reads are the project's** — the tool is
  tracked in the loop's directory and named by the worker prompt, and the roots it reads are configuration, with
  the scenario `evidence#scansWhatTheProfileNames`.

No requirement about the seeding changes textually: *One working tree per run* says the tree is "seeded with what
the project's build and test need", which is still true of the generated skills, the submodule and `local.prop*`.
`documentation-ownership` owns the removal itself — *A rule has one normative home* and *A document that names a
skill resolves to a skill that exists* — and neither needs new text, because the change makes the tree conform to
them instead of restating them.

## Impact

`tools/loop/evidence-check.sh` (new), `tools/loop/loop.conf`, `tools/loop/gate.sh`, `tools/loop/worktree.sh`,
`tools/loop/prompts/worker.md`, `tools/loop/prescreen.sh`, `tools/check-portability.sh`, `tools/loop-cases.sh`,
`tools/portability-cases.sh`, `AGENTS.md`, `guidelines/Backlog.md`, `openspec/config.yaml`, one archived change's
record, and the deletion of `.pi/skills/fix-loop/`. No compiled file, so no Gradle build — the gate is the shell
suites the pathspec owes. Rollback: revert the branch; a worktree seeded by an older run is untouched by the change,
and the deleted measurements are recoverable from the base commit.

## Open

The skill's eleven "field notes" are dropped rather than moved: each is a pointer to a rule the spec or the worker
prompt states, and the two that carry a number (the 15-minute skip, the starved host's 3m47s-versus-30-minutes gate)
have that number in the measurements now in `guidelines/Backlog.md`, section 7. The "what changed when the loop became a
script" table is dropped as provenance: the item records that the history lives in
`openspec/changes/archive/2026-10-17-migrate-todo-to-github-issues/report.md`.
