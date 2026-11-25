# Design — a filing skill names the type axis on the call it shows

## Root cause

The pointer the tracker needs lives in the skill's documented call, and the two skills a finding or a split leaves
through carry a call that names neither flag (`.pi/skills/file-finding/SKILL.md:41`,
`.pi/skills/reshape-issue/SKILL.md:94`). The case that would notice —
`docs#theFilingSkillsNameTheTypeAxis`, `tools/issues/tests/issue-tooling-cases.sh:3400` — iterates a two-element
list, `create-issue` and `update-issue`, so it could not read them. One mechanism: the filing surface grew by two
skills and the case's list and their call lines never followed.

## The single fix

1. Both call lines name `--type <the tracker's type>` and `--parent <the container's number>` beside the flags they
   already carry, and one paragraph under each says the two are the tracker's own classification and not body
   fields, and that `roles.containerType` in `tools/tooling.json` is the name this project treats as the container.
2. The case's list becomes the four filing skills, and a second loop asserts the two flags on the documented
   `create.sh` call of `file-finding` and `reshape-issue` — `grep -Eq 'create\.sh .*--type [^ ]+ .*--parent [^ ]+'`.
3. The delta restates the requirement with the four files and the call line, so the spec and the case name one rule.

Blast radius: two skill documents, one case in the token-free suite, one requirement in `issue-backlog`. No
compiled file, no test of a compiled surface, nothing the app links.

## Why the call line and not only the paragraph

A session that files copies the line the skill shows; a mention in the prose around it is not what it types. The
presence loop alone was satisfiable by the paragraph the fix adds, so the case would have stayed green with the call
line reverted — which is the red this change's evidence carries (`red-on-call-line.log`: the paragraph intact, the
call reverted, `FAIL docs#theFilingSkillsNameTheTypeAxis`).

## Alternatives the evidence eliminated

- **Point the skills at the class-to-type map.** `roles.typeForClass` does not exist in `tools/tooling.json` in this
  tree and no open change declares it (`grep` over `openspec/` finds no occurrence), so a skill naming it would
  point at a key that does not exist; the item's own premise is that change `shape-idea` adds it, and this item must
  not depend on a change that has not landed.
- **Widen the case's list only.** Then the base's two skills still carry no flags and the suite goes red without a
  fix — a case that fails on the tree it is committed to is not a fix.
- **A new case for the two skills.** One case name per requirement is the rule here, and the existing case already
  owns this axis; a second case would give the same rule two owners.

## Rollback

Revert the commit: the two skills lose four prose lines, the case returns to its two-file list, and the delta is
withdrawn with the change. Nothing reads the added prose but the case.

## Verification

`bash tools/issues/tests/issue-tooling-selftest.sh --case docs#theFilingSkillsNameTheTypeAxis` — red with the base's
two skills (`evidence/red-on-base.log`, `.xml`), red with only the call line reverted
(`evidence/red-on-call-line.log`), green with this tree (`evidence/green-on-fix.log`). The pathspec's other checks
are the gate's, not this change's.
