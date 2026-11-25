# Proposal — a filing skill names the type axis on the call it shows

## Why

**Root cause.** The two skills a filing session is routed to document exactly one `tools/issues/create.sh` call
each, and neither call names the tracker's own classification: `.pi/skills/file-finding/SKILL.md:41` reads
`tools/issues/create.sh <<'FINDING'` and `.pi/skills/reshape-issue/SKILL.md:94` reads
`tools/issues/create.sh ...   # once per child, with its cause and its case`. `tools/issues/create.sh` accepts both
flags (`--type <name>`, `--parent <number>`), and the case that guards the axis,
`docs#theFilingSkillsNameTheTypeAxis` (`tools/issues/tests/issue-tooling-cases.sh:3400`), reads
`.pi/skills/create-issue/SKILL.md` and `.pi/skills/update-issue/SKILL.md` only — so the two skills a finding and a
split actually leave through were green by not being read.

**The failure this allows.** A session that copies the call line a skill shows files a finding with no issue type
and no parent link, and nothing turns red. That is the shape issue #911 paid for: a split of an epic that looks
complete in the shaping inbox and reads as an empty epic on the tracker, because the tracker groups a container's
children by type and an untyped child does not appear under it. The measurement the item carries still holds in this
tree — `grep -c -- '--type'` and `grep -c -- '--parent'` are `0` for both files at the base commit `6a7107042e2f`,
and both files call `tools/issues/create.sh`.

**What already stands.** The flags exist and are described by the two skills that were already read
(`.pi/skills/create-issue/SKILL.md:100-101` shows `--type Epic` and `--type Task --parent 906`); the axis is the
tracker's own field, not a label, and `guidelines/Backlog.md` §3 "What the tracker holds" owns it; the case is
token-free, offline and reads the tree, so it is the cheap structural check a skill delta owes.

**The gap.** Neither skill names the flags, and no case reads them — so the pointer that was missing can be removed
again from the two skills a session actually copies without a red run.

## What changes

1. `.pi/skills/file-finding/SKILL.md` — the documented `create.sh` call carries `--type` and `--parent` beside its
   other flags, and the paragraph under it says why they are not body fields and which of the profile's role names
   is the container's.
2. `.pi/skills/reshape-issue/SKILL.md` — the same, on the call a split files each child through.
3. `tools/issues/tests/issue-tooling-cases.sh` — `docs#theFilingSkillsNameTheTypeAxis` reads all four filing skills
   and, for the two that show a `create.sh` call a session copies, asserts the flags on that line rather than
   anywhere in the prose.
4. `openspec/specs/issue-backlog/spec.md` — the requirement "The skill a filer is routed to names the type axis"
   names all four files and the call line, so the delta and the case agree.

## Impact

- One requirement modified in `issue-backlog`; no new capability.
- Four prose lines gained in the two skills, one case widened, one delta spec.
- No compiled file, so no build: the delta's evidence is the case, run through
  `tools/issues/tests/issue-tooling-selftest.sh --case docs#theFilingSkillsNameTheTypeAxis`, red on the base and
  green here, plus the doc checks the pathspec owes.
- Rollback: revert the diff; nothing reads the new prose but the case, and the case is where the loss becomes loud
  again.
- The class-to-type map the item mentions (`roles.typeForClass`, change `shape-idea`) is not in this tree — no
  `tools/tooling.json` role carries it and no open change declares it — so neither skill names it and neither points
  at a key that does not exist.

surface: 2 documented call lines changed and 2 paragraphs added across 2 skills, 1 case widened from a 2-file list
  to 4 files plus a call-line loop, 1 shipped requirement MODIFIED in 1 delta file —
  `git diff --stat 6a7107042e2f; grep -n 'create\.sh' .pi/skills/file-finding/SKILL.md .pi/skills/reshape-issue/SKILL.md`
