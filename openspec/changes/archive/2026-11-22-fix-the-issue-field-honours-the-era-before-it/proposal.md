# Proposal — the issue-field rule honours the era before it

## Why

**Root cause.** `tools/check-openspec-hygiene.sh:124-135` reads the item a change answers from the `x-issue:` field
of the change's own `.openspec.yaml` (`:125`), or from a `fix-issue-<N>-` directory name for the era before the field
existed (`:129-130`). A directory from that era whose name carries no number satisfies neither, and `:135` refuses
it — so the trunk is red for a declaration that only that change's own author can make (issue #999). The class
header (`:108-113`) states the field and the name and states no era, so the omission is in the rule, not in the five
directories that meet it.

**Evidence, verified against the tree (issue #999).** The item's own trunk reported **26** such directories. This
run's tree, base `f826cd7a5e4952fc0e5331a32dcea0cb67baa99a`, holds **6**, and the base checker over it
(`evidence/check-before.log`, the base file `git show f826cd7a:tools/check-openspec-hygiene.sh` run with `--root`
over this worktree) prints 6 `::error` lines of the form
`no 'x-issue:' field and no 'fix-issue-<N>-' name` and exits **1** (2026-11-22T20:13:27Z). Five of the six are
directories written before the field was required — `a-case-name-resolves-to-one-harness`,
`run-event-stream-is-typed`, `settings-taxonomy-and-surface`, `the-bounds-spend-and-budget-are-typed`,
`the-print-sites-are-converted`, every one of them `created: 2026-11-14`; the sixth
(`fix-the-checkpoint-splits-the-items-chain`) carries no `.openspec.yaml` at all, so it states no era and this change
leaves it refused. Every other open change declares its item: `#971`, `#1010`, `#988`, `#946`, `#1002`, `#945`,
`#914`, `#999`, `#882`, `#961`, and two `x-issue: none`. The item's 26 and this tree's 5 are one condition read at
two open sets.

**The red test.** `artifact-hygiene#aChangeWrittenBeforeTheFieldIsReported` is the case this change writes
(`tools/tests/check-openspec-hygiene-selftest.sh:436-476`). Run against the base checker with this change's case file
beside it (`evidence/red-on-base.sh`) it reports `1 of 12 cases failed`, `tests=12 failures=1`, at
2026-11-22T20:14:11Z (`evidence/red-on-base.xml`); the one-case reading is `1 of 1 cases failed` at
2026-11-22T20:14:10Z (`evidence/red-on-base-single.log`, `.xml`): the base refuses a change `created: 2026-11-14`
instead of reporting the era it was written in. The whole-suite base reading beside it is what says the eleven other
cases assert what the base already does — the two fixtures this change realigns included.

**The single fix.** Read the change's own `created:` (`tools/check-openspec-hygiene.sh:126`) and, when it states a
day before `2026-11-19` — the day the rule landed, `c7de199f` — report the directory instead of refusing it
(`:131-133`), with the date the check read named in the output (`:155-157`). The boundary is one constant (`:45-48`).
The refusal keeps every change written on or after that day, and every change that states no `created:` at all,
because a directory that states no era cannot be read as belonging to one.

**The requirement the defect contradicts.** `openspec/specs/documentation-ownership/spec.md:792`, "An open change
names the issue it is for": its scenario "A change that names no issue is refused" states the refusal without
exception, and a tree whose open set predates the field is the state it has no answer for.

**The owning guideline section.** None. `tools/look.sh 'x-issue' --path guidelines` reports `0 matches in 0 files`,
and so does the same call with `--path docs`: the rule's normative home is the capability requirement above, which
is where this change's delta lands. `AGENTS.md` "OpenSpec Workflow" lists the artifact classes CI gates and does not
name this one — a pre-existing omission, reported as a `NOTE:` rather than repaired here.

surface: 2 files changed, 1 case added, 2 fixtures realigned — `tools/look.sh "no 'x-issue:' field"` reports 189
matches in 39 files, and exactly two of
them are the tree's own — the check and its case file; the rest are archived changes' evidence and open changes'
logs, which quote the message rather than assert the rule. So the delta is two files and one capability spec, and
the estimate is set against that count, not against the first file the plan names.

## What Changes

One clause of one class: a directory written before 2026-11-19 is reported, not refused. Nothing
else about the class moves — the field is still read from the change's own file, `none` is still a declaration,
`fix-issue-<N>-` still supplies the number, and two open changes naming one issue are still refused.

Additive: a tree that passes today passes after it, and the only trees whose verdict changes are the ones the refusal
was red on for a declaration nobody could write. No breaking change, no migration.

**Rollback.** `git revert` of the one commit restores the unconditional refusal and the single case is restored with
it; nothing outside the two files below and the delta was written.

## Impact

- `tools/check-openspec-hygiene.sh` — the era constant (`:45-48`), the `created:` read (`:126`), the era branch
  (`:131-133`), the era's report line (`:155-157`), and the class's own header comment (`:108-113`).
- `tools/tests/check-openspec-hygiene-selftest.sh` — `artifact-hygiene#aChangeWrittenBeforeTheFieldIsReported`
  (`:436-476`), and the two `created:` fixtures of `#aChangeNamingNoIssueIsRefused` (`:357`, `:377`) moved to
  `2026-11-20`, because a fixture dated before the boundary would pass that case for the era's reason instead of the
  field's.
- `openspec/specs/documentation-ownership/spec.md` — the requirement "An open change names the issue it is for",
  MODIFIED: its body gains the era, and the new scenario names the case above.
- No module, no Gradle configuration, no manifest and no resource: the delta touches no compiled file, so its
  verification is the structural check of the files it edits (the two commands the gate plans), not a build.

**Previous specifications changed.** `documentation-ownership`, "An open change names the issue it is for"
(MODIFIED). No other capability spec is touched.

**Guidelines affected.** None — no `guidelines/` document states the issue-field rule, so no guideline section
carries an edit this diff owes.
