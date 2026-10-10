## Why

The loop's eligibility gate was written as a bug filter, but the six conditions it applies never test
"is this a bug": conditions 3 (exactly one fix), 5 (one capability, bounded files) and 6 (host-decidable)
do all the discriminating, and they are class-blind. A `class: improvement` entry that satisfies all six
is nevertheless rejected on its label alone — `TODO.md` §164 (a non-HTTP base URL reaches the user as a
`TransportFailed(cause=… cannot be cast to HttpURLConnection)`) and §120 (a user style switch costs a
second stylesheet load) both pass every condition today.

The open run's own state shows the filter is not even doing work: **0 closed, 32 skipped**, and not one of
the 32 skips was rejected because of its class — the queue was built from bugs, so the class check never
fired. At the same time the loop's run *filed* §166, §167, §170 and §171 as `class: improvement`: the loop
manufactures improvement-shaped residue it is forbidden to close.

The second problem is craft. `SKILL.md` is 479 lines / 30.5 KB of equal-weight prose in which:

- one rule (the cited XML must be copied out before the next run) is stated **five** times;
- the six gate conditions appear **twice** — embedded in the triage child's JS contract string *and* as the
  Phase B table — so an edit to one silently diverges from the other;
- a report template's `## Bug-fix loop — <date>` heading reads as a document section;
- the phases, which are what a reader executes, come *after* Roles, progress tracking and the worktree
  recipe;
- every rule carries its provenance inline (`§138 took 65 min`, `the round-1 FAIL`, `5 of 9 gated`), which
  makes the rules harder to follow than the measurements that justify them;
- 12 of the 15 pitfalls restate a rule already in the body.

## What Changes

- **Rename** the skill `bugfix-loop` → `fix-loop`. Every artifact the loop produces already says *fix*
  (`fix-<slug>` changes, `fix:` commits, `fixed-by` status, `fix-*` vocabulary); only the skill name and one
  flag say "bug".
- **Gate: class leaves the reject set, stays in the rank set.** `class: bug|improvement` both enter the
  queue; features do not. `triage-todo` already orders bugs → improvements → features, so the loop drains
  bugs first within the same budget.
- **Condition 4 becomes class-parameterised.** A bug asserts what the code does *wrong*; an improvement
  asserts the bound, count or absence the delta requires and the code does not yet meet. Both are
  falsifiable WHEN/THEN statements, false on HEAD, proven by the same red-case machinery Phase A already
  has (the doc-vs-code divergence branch).
- **New improvement-only condition 7** — the delta is owned by an existing capability (or adds one small
  requirement), and its red case asserts a number, a count or an absence rather than "better". This is what
  keeps open-ended improvements out (§77 "needs its own boundary analysis", §76 import-side, §163 signature
  format), and it is checked at the gate, not during analysis.
- **Two new skip verdicts**: `not-spec-shaped`, `needs-target`.
- **Budget unit renamed**: `--bugs N` → `--items N`, state field `bugLimit` → `itemLimit`. The state
  directory `.pi/bugfix-loop/` keeps its name so the open run is not orphaned.
- **`SKILL.md` restructured to execution order**, with cross-cutting invariants (evidence pointers, one
  writer per tree, budget authority) each stated once and referenced by name, the gate conditions in one
  canonical place, provenance moved to a measured-costs appendix, the report template no longer a `##`
  heading, and the pitfalls deduplicated. Target ≤ ~320 lines, no rule dropped.
- **References updated**: `AGENTS.md` (backlog-maintenance table, agent-iteration-loop section) and
  `openspec/config.yaml` (context paragraph, `operations.apply.guidance` entry).

**Additive, not breaking.** The loop closes a wider class under an unchanged gate; no product code, no
runtime behaviour, no user-visible surface changes. **Rollback**: revert the rename and restore the previous
gate wording — the two are independent, so the gate change can be reverted alone.

**Scope**: repository tooling (the agent harness and its documented process). Neither the phone nor the
Android Auto variant is affected.

## Capabilities

No spec-level behaviour change. This is tooling and documentation: the agent loop's rules, one skill
document, two scripts, and the two documents that reference them by name. Nothing a user of the app
observes changes, and no product capability gains or loses a requirement.

`skip_specs: true` is set in `.openspec.yaml`.

The change *applies* the principle of `openspec/specs/documentation-ownership/spec.md` — "A rule has one
normative home" — to a skill document, which that capability does not currently cover (it governs
`AGENTS.md` and `guidelines/`). Recording that as a requirement is deliberately **not** part of this change;
see `design.md` for why, and for the follow-up it would need.

No existing specification is changed.

## Impact

- `.pi/skills/bugfix-loop/` → `.pi/skills/fix-loop/` — the skill directory (4 files: `SKILL.md` and
  `scripts/{loop-state,evidence-check,selftest}.sh`)
- `.pi/skills/fix-loop/scripts/loop-state.sh` — `--bugs` → `--items`, `bugLimit` → `itemLimit`, banner text;
  `selftest.sh` follows
- `.pi/skills/fix-loop/scripts/evidence-check.sh` — a comment referencing the skill path
- `AGENTS.md` — the backlog-maintenance skill table and the agent-iteration-loop section name
  `.pi/skills/bugfix-loop/scripts/loop-state.sh` today
- `openspec/config.yaml` — the `context` paragraph ("Bug fixes: `.pi/skills/bugfix-loop` owns …") and one
  `operations.apply.guidance` entry
- `.gitignore` — unchanged; the `!.pi/skills/*/` negation admits the renamed directory, and only
  `openspec-*`-prefixed skills need a line of their own

**Working-tree note (must be handled by the tasks, not discovered during apply).** The skill's tracked copy
lives on branch `bugfix-loop-2026-10-09` (worktree `.pi/worktrees/loop`); on `main` it exists only as an
untracked duplicate. A rename cannot be applied to a tree that does not track the file, so this change lands
the renamed skill **on `main`** as its first task, which is also what the skill's own worktree rule asks for
("edit the repo copy, copy it into the worktree"). The loop branch's copy is brought forward at merge time;
the skill's `TODO.md` there is untouched by this change.
