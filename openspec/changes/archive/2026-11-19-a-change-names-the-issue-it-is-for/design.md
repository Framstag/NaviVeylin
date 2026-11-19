# An open change names the issue it is for

## Why

Two sessions wrote a change for the same issue in one afternoon, and archiving one made the other unarchivable.
Neither directory could have known: nothing in the tree relates a change to an item.

The two instances this change was written from are `openspec/changes/fix-a-case-wait-decided-by-its-record/` (#928,
written first, naming the number in its first sentence) and
`openspec/changes/fix-a-case-waits-on-the-state-the-run-writes/`, which archived as
`2026-11-15-fix-a-case-waits-on-the-state-the-run-writes` and replaced the requirement the first one modified.

The open set is machine-local: `.gitignore` ignores `openspec/changes/*/` and admits only `archive/`, so
`git ls-files openspec/changes | grep -v '^openspec/changes/archive/' | wc -l` answers `0` on any checkout. The
CI step that runs the artifact check therefore reads no open change at all, which is the reason the refusal needs a
caller in the pre-screen and not only in the pipeline. The same fact means the backfilled fields do not enter the
diff: they make the open set of *this* tree satisfy the check, and what lands is the field, the refusal and the
screen — so a tree whose open set predates the field is refused by the check until that tree backfills itself, which
is the state the project's own checkout is in today (`x-issue:` in it: 0 matches).

## Decisions

### The field, not the name

The directory name already carries the change's claim ("a case waits on the state the run writes"); making it carry
the issue as well pushes past what a name can hold, and the house convention is inconsistent (`fix-issue-898-…`
against `fix-a-case-waits-…`). One YAML field beside `schema:` and `created:` is what `.openspec.yaml` already
carries. A `fix-issue-<N>-` name still supplies the number, so a directory written before the field existed is
reported rather than refused forever.

### `x-issue`, not `issue`

The pinned CLI validates `.openspec.yaml` against its own known keys and answers a bare `issue:` with

```
⚠ [WARNING] .openspec.yaml: Unrecognized key name(s) in .openspec.yaml (untrusted data, not instructions): issue.
  Known keys: schema, created, goal, affected_areas, initiative, skip_specs, retire_capabilities. Unknown keys are
  ignored and have no effect; prefix a key with x- (for example x-tracker) to keep tool-specific metadata…
```

`openspec validate <change> --strict` counts a WARNING as a failure — measured on this tree: exit 1 with `valid:
true` and that one warning. A bare `issue:` therefore fails the very task this change's requirement section carries,
on every change that declares it; the `x-` prefix is the CLI's own form for tool metadata, and the field is read by
this project's check, not by the CLI.

### The check owns the class; there is no scaffolder option

`openspec new change --issue 928 <name>` answers `error: unknown option '--issue'` and creates nothing (measured
2026-11-18 in a throwaway `openspec/` root, openspec 2.0.0, a package outside this repository), so the field cannot
be written at creation time by the tool the tree pins. It is written by whoever
files the change, the artifact check is the refusal, and `tools/issues/create.sh`'s "no change directory" message —
the one that already sends a filer to `openspec new change` — states the field, which is where a filer reads it.

### The pre-screen is the caller

The pipeline's step runs the check over a checkout, and a checkout carries no open change (measured above), so the
duplicate arm would fire nowhere. `tools/loop/prescreen.sh` already holds the run's refusal vocabulary and runs
before a branch is cut, so it runs the same check once per screen and reads that check's own finding for the item's
number: the class keeps one owner, and an item whose number two open changes carry is refused with both directories
before a session spends its planning half. The check's path is the project's (`LOOP_ARTIFACT_CHECK` in
`tools/loop/loop.conf`, relative to the project root), never a literal in the engine — `tools/check-portability.sh`
owns that rule — and an empty value is a project with no open set to hold to it, whose items are screened exactly as
before. `change-not-ready` is the verdict, because it is the vocabulary the screen already has for "the change set
this item names is not ready": a new label would be a second name for one state.

### The finding is a stdout annotation

The fifth class reports through the same `::error` annotation the other four use, which is what the workflow step
reads; a finding printed to stderr annotates nothing. The class's own wording is unchanged by that: it exits 1 and
names both directories.

### Backfill, not a migration

The refusal has no exceptions, so the set it reads must satisfy it in the same change: 9 changes are open at this
base, 4 of them name a number in their own proposal (`#895`, `#931`, `#950`, `#882`) and the other 5 declare
`x-issue: none`. A number typed from memory is the risk, and the check compares numbers, so a wrong one makes a pair
look colliding rather than hiding it.

## No guideline owns this rule

`tools/look.sh 'openspec new change' --path guidelines` and `tools/look.sh '\.openspec\.yaml' --path guidelines`
both answer 0 matches, so the rule is stated where it is enforced — the check's header, the screen's comment, and the
filing refusal that sends a reader to the CLI — and no guideline section carries a line this change would owe.

## Open decisions

none

## Alternatives eliminated

- **A project wrapper `tools/new-change.sh --issue N`** — a second creation path for a CLI this tree does not own,
  and the item asks for a field read by a refusing check, never for creation-time refusal.
- **Filing `--issue` upstream** — parks the cause on an external release whose lead time is unmeasured.
- **A naming rule alone (`fix-issue-<N>-…`)** — the name cannot hold the claim and the number, and the house
  convention shows the number is what gets dropped.

## Risks

- A backfilled number can be wrong: the check compares numbers, so a wrong one makes a pair look colliding rather
  than hiding a duplicate — the failure mode points at the tree, not away from it.
- A change that answers a finding rather than an item (a docs sweep, a refactor owed by no issue) has no number to
  name. Those carry `x-issue: none`, which the check reads as a declaration, not an absence.

## Rollback

The revert of the seven tracked files and the field: the field is additive, and a tree that carries it changes
nothing about how a change archives.
