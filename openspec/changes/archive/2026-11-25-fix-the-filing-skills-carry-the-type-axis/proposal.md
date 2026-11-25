# The filing skills that were green by not being read carry the type axis

## Why

`tools/issues/create.sh` writes two facts that are not labels — the issue type (`--type`) and the parent link
(`--parent`, `tools/issues/create.sh:79-80`) — and the tracker groups a container's children by them
(`guidelines/Backlog.md` §3, "What the tracker holds", `:87-98`). Two of the skills a filing session leaves through
named neither flag: the heredoc form at `.pi/skills/file-finding/SKILL.md:41` and the per-child call at
`.pi/skills/reshape-issue/SKILL.md:94`. The case that pins the axis read only `create-issue` and `update-issue` —
its list named those two (`tools/issues/tests/issue-tooling-cases.sh:3402`) — so the two skills that file children
were green by not being
read — the shape #911 paid for, nine children that read as an empty epic.

**Root cause, in one sentence:** the case that pins the type axis enumerated the two skills that already carried it,
so the two skills that file children lost the pointer without a red run.

**The red test:** `docs#theFilingSkillsNameTheTypeAxis`, taken with the case's file list grown to these four and both
skills at the base commit's text, red on HEAD (`evidence/red-on-base.log`, 2026-11-25T04:15:43Z, exit 1):

```
  .pi/skills/file-finding/SKILL.md no longer names --type, so a filing that follows it cannot carry the type axis
FAIL docs#theFilingSkillsNameTheTypeAxis
issue-tooling-selftest: 1 of 188 cases failed (HEAD 6a710704, tools 0b00d74fcd37, 2026-11-25T04:15:43Z)
```

A shell suite has no JUnit XML of its own (`.pi/skills/revert-check/SKILL.md`, "When the gate is a shell self-test,
not Gradle"), so the tally is the suite's own line. The case's second clause — that the documented call line itself
carries both flags, not merely the prose below it — is falsified on its own by
`evidence/mutation-red.log` (the same tally, with the call's flags removed and the sentence left in place).

**The owning requirement:** `issue-backlog`, "The skill a filer is routed to names the type axis". Its text already
says *every* skill the entry document names; its scenario enumerated only the two that carried the flags. The delta
MODIFIES that requirement, so the scenario names the four files the case reads.

**The guideline section:** `guidelines/Backlog.md` §3 "What the tracker holds" — the rule that the tracker's own
classification is an axis of its own and that a container is never work. The change edits no guideline: the rule is
stated, and what was missing is the pointer in the skills and the list in the case.

**Surface, counted before the plan** — `rg -l 'issues/create\.sh' .pi/skills` names 7 skills, 5 of which document a
call; `rg -c -- '--type' .pi/skills/*/SKILL.md` reported 0 on the base commit for `file-finding`, `reshape-issue`,
`process-failure-log`, `openspec-proof-gap-audit` and `portability-check`/`run-tests` (the last two mention the
writer without documenting a call); `rg -l 'theFilingSkillsNameTheTypeAxis' openspec/specs tools` names 1 case
reading 2 files. So: **2 skills edited, 1 case's file list grown from 2 to 4 and one assertion added, 0 cases added, 0 fixtures
realigned**.

## What changes

surface: 2 skills edited, 1 case's file list grown from 2 to 4 files with one assertion added, 0 cases added, 0
fixtures realigned — `rg -l 'issues/create\.sh' .pi/skills` (7 skills, 5 documenting a call) and
`tools/issues/tests/issue-tooling-selftest.sh --list | wc -l` on the base commit.

- `.pi/skills/file-finding/SKILL.md` — the heredoc call carries the two flags on its own line
  (`--type Bug --parent 906`, the type its example finding is), and the
  paragraph below it says what the two flags write, why they are not labels, and where the type's name comes from.
- `.pi/skills/reshape-issue/SKILL.md` — the per-child call carries the same two flags, with the sentence that says
  what a child filed without them looks like on the tracker.
- `tools/issues/tests/issue-tooling-cases.sh` — `case_docs_theFilingSkillsNameTheTypeAxis` reads four files instead
  of two, and asserts that each of the two filing skills' documented call line carries both flags: the presence
  grep it already had is satisfied by the sentence *below* the call, so the call line needs its own clause or the
  guard is prose-shaped.

## Alternatives eliminated

- **Let the case discover the callers** (`rg -l 'issues/create\.sh' .pi/skills` inside the case). Refused: the
  pattern that finds a caller also matches the skill that *mentions* the writer in prose, so the case would fail on
  a file that documents no call, with no message naming what is missing. The enumerated list is what the
  requirement states, and it is what the suite can report a file name from.
- **Point both skills at `roles.typeForClass`.** Refused: no such key exists in `tools/tooling.json` today
  (`rg -n typeForClass` — no matches); change `shape-idea` adds it. A pointer to a key the tree does not carry is
  the defect this item is about. The skills point at `roles.containerType`, which exists (`tools/tooling.json`), and
  at `create-issue`, which names the flags and the series they file.
- **Repair the prose only, as #911's own repair did.** Refused: the mechanism is the case's file list, so a prose
  repair leaves the two files free to lose the flags again in the next edit — the red proof above is that the case
  notices only what it reads.
